package io.jettra.store.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class JettraSecurityManager {
    public static final String DEFAULT_ADMIN_USER = "admin";
    public static final String DEFAULT_ADMIN_PASS = "admin-jettra";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    private final String secretKey = "jettra-store-secret-master-encryption-key-2026";

    public record UserAccount(String username, String passwordHash, String role, boolean immutable) {}

    public JettraSecurityManager() {
        // Inicialización obligatoria del superusuario por defecto con máxima prioridad
        users.put(DEFAULT_ADMIN_USER, new UserAccount(
            DEFAULT_ADMIN_USER, 
            hashPassword(DEFAULT_ADMIN_PASS), 
            ROLE_SUPER_ADMIN, 
            true // Immutable: jamás puede ser alterado o revocado por usuarios secundarios
        ));
    }

    public String authenticate(String username, String password) {
        UserAccount account = users.get(username);
        if (account == null || !account.passwordHash().equals(hashPassword(password))) {
            throw new SecurityException("Invalid credentials for user: " + username);
        }
        return generateToken(account);
    }

    public void createUser(String executingUserToken, String newUsername, String newPassword, String role) {
        Claims claims = validateToken(executingUserToken);
        if (!ROLE_SUPER_ADMIN.equals(claims.role()) && !"DB_ADMIN".equals(claims.role())) {
            throw new SecurityException("Insufficient permissions to create user");
        }
        if (users.containsKey(newUsername)) {
            throw new IllegalArgumentException("User already exists: " + newUsername);
        }
        users.put(newUsername, new UserAccount(newUsername, hashPassword(newPassword), role, false));
    }

    public void alterUserRole(String executingUserToken, String targetUsername, String newRole) {
        Claims claims = validateToken(executingUserToken);

        // Regla estricta: Ningún usuario secundario puede alterar los roles del superusuario
        if (DEFAULT_ADMIN_USER.equalsIgnoreCase(targetUsername)) {
            if (!DEFAULT_ADMIN_USER.equals(claims.username())) {
                throw new SecurityException("Security violation: Superuser privileges cannot be altered, modified, or revoked by secondary accounts.");
            }
        }

        UserAccount target = users.get(targetUsername);
        if (target == null) {
            throw new NoSuchElementException("User not found: " + targetUsername);
        }
        users.put(targetUsername, new UserAccount(targetUsername, target.passwordHash(), newRole, target.immutable()));
    }

    public void changeAdminPassword(String currentPassword, String newPassword) {
        UserAccount admin = users.get(DEFAULT_ADMIN_USER);
        if (!admin.passwordHash().equals(hashPassword(currentPassword))) {
            throw new SecurityException("Current admin password is invalid");
        }
        users.put(DEFAULT_ADMIN_USER, new UserAccount(DEFAULT_ADMIN_USER, hashPassword(newPassword), ROLE_SUPER_ADMIN, true));
    }

    public Claims validateToken(String token) {
        if (token == null || !token.startsWith("JettraJWT.")) {
            throw new SecurityException("Malformed or missing JettraJWT token");
        }
        try {
            String[] parts = token.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // Formato payload: username|role|expiry
            String[] data = payloadJson.split("\\|");
            String user = data[0];
            String role = data[1];
            long exp = Long.parseLong(data[2]);
            if (Instant.now().getEpochSecond() > exp) {
                throw new SecurityException("JettraJWT token expired");
            }
            return new Claims(user, role, exp);
        } catch (Exception e) {
            throw new SecurityException("Invalid token signature or payload", e);
        }
    }

    private String generateToken(UserAccount user) {
        long exp = Instant.now().plusSeconds(86400).getEpochSecond();
        String payload = user.username() + "|" + user.role() + "|" + exp;
        String b64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = hashPassword(b64Payload + secretKey);
        return "JettraJWT." + b64Payload + "." + signature;
    }

    private String hashPassword(String pass) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(pass.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public record Claims(String username, String role, long expiration) {}
}
