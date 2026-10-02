package io.jettra.core.three.d.explorer;

/**
 * Representa una versión histórica inmutable de un registro dentro de un engine de JettraStore
 * para soporte de auditoría, control de cambios y restauración de versiones previas.
 */
public record RecordVersion(
    int version,
    String summary,
    String details,
    String timestamp,
    String operationNote
) {}
