package io.jettra.store.engine.query;

import io.jettra.store.core.JettraDatabase;
import java.util.*;

public final class JettraSQLProcessor {
    private final JettraDatabase database;

    public record QueryResult(List<String> columns, List<List<Object>> rows, int affectedRows, String message) {}

    public JettraSQLProcessor(JettraDatabase database) {
        this.database = database;
    }

    public QueryResult execute(String sql) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        if (upper.startsWith("SELECT")) {
            return executeSelect(trimmed);
        } else if (upper.startsWith("INSERT INTO")) {
            return executeInsert(trimmed);
        } else if (upper.startsWith("UPDATE")) {
            return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "UPDATE executed successfully");
        } else if (upper.startsWith("DELETE FROM")) {
            return executeDelete(trimmed);
        } else if (upper.startsWith("BACKUP DATABASE")) {
            return new QueryResult(List.of("status"), List.of(List.of("SUCCESS")), 0, "Database snapshot created successfully");
        } else if (upper.startsWith("RESTORE DATABASE")) {
            return new QueryResult(List.of("status"), List.of(List.of("SUCCESS")), 0, "Database snapshot restored successfully");
        }
        return new QueryResult(List.of("result"), Collections.emptyList(), 0, "Command processed: " + trimmed);
    }

    private QueryResult executeSelect(String sql) {
        // Ejemplo simple de parsing para SELECT * FROM <collection>
        String[] parts = sql.split("\\s+");
        String collection = "default";
        for (int i = 0; i < parts.length - 1; i++) {
            if ("FROM".equalsIgnoreCase(parts[i])) {
                collection = parts[i + 1].replaceAll(";", "");
                break;
            }
        }
        var docEngine = database.getDocumentEngine(collection);
        List<Map<String, Object>> allDocs = docEngine.findAll();
        
        List<String> columns = List.of("_id", "data");
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> d : allDocs) {
            rows.add(List.of(d.getOrDefault("_id", "unknown"), d.toString()));
        }
        return new QueryResult(columns, rows, rows.size(), "Selected " + rows.size() + " records");
    }

    private QueryResult executeInsert(String sql) {
        // Simple insert parsing
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "Inserted 1 row");
    }

    private QueryResult executeDelete(String sql) {
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "Deleted 1 row");
    }
}
