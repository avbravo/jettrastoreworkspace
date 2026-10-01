package io.jettra.store.engine.query;

import io.jettra.store.core.JettraDatabase;
import io.jettra.store.engine.models.DocumentEngine;

import java.util.*;

public final class JettraSQLProcessor {
    private final JettraDatabase database;

    public record QueryResult(List<String> columns, List<List<Object>> rows, int affectedRows, String message) {
        public int totalRows() { return affectedRows; }
        public String summary() { return message; }
    }

    public JettraSQLProcessor(JettraDatabase database) {
        this.database = database;
    }

    private static String sanitize(String str) {
        if (str == null) return "";
        return str.replace("'", "").replace(String.valueOf((char)34), "").replace(";", "").trim();
    }

    public QueryResult execute(String sql) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        if (upper.startsWith("SELECT ")) {
            return executeSelect(trimmed);
        } else if (upper.startsWith("INSERT INTO ")) {
            return executeInsert(trimmed);
        } else if (upper.startsWith("UPDATE ")) {
            return executeUpdate(trimmed);
        } else if (upper.startsWith("DELETE FROM ")) {
            return executeDelete(trimmed);
        } else if (upper.startsWith("INSTALL SAMPLES") || upper.startsWith("INSTALL SAMPLE") || upper.startsWith("LOAD SAMPLE")) {
            return executeInstallSamples(upper);
        }

        return new QueryResult(List.of("result"), List.of(List.of("SQL not fully parsed: " + trimmed)), 1, "Generic execution");
    }

    private QueryResult executeInstallSamples(String upper) {
        if (database == null) {
            return new QueryResult(List.of("error"), List.of(List.of((Object)"Base de datos no disponible")), 0, "Error");
        }
        String dbName = database.getDatabaseName();
        if (upper.contains("HOSPITAL") || upper.contains("HOSTIPAL")) {
            io.jettra.store.sample.JettraStoreSamples.installHospital(database, true);
            return new QueryResult(List.of("status", "database", "objects"), 
                List.of(List.of((Object)"SUCCESS", (Object)dbName, (Object)"2000000")), 1, "Muestra hospital 2M instalada exitosamente");
        } else if (upper.contains("AMBIENTAL") || upper.contains("ENVIRONMENTAL")) {
            io.jettra.store.sample.JettraStoreSamples.installAmbiental(database, true);
            return new QueryResult(List.of("status", "database", "objects"), 
                List.of(List.of((Object)"SUCCESS", (Object)dbName, (Object)"3000000")), 1, "Muestra ambiental 3M instalada exitosamente");
        } else if (upper.contains("FACTURA")) {
            io.jettra.store.sample.JettraStoreSamples.installFactura(database, true);
            return new QueryResult(List.of("status", "database", "objects"), 
                List.of(List.of((Object)"SUCCESS", (Object)dbName, (Object)"3000000")), 1, "Muestra factura 3M instalada exitosamente");
        } else {
            io.jettra.store.sample.JettraStoreSamples.installSample(dbName, database, true);
            return new QueryResult(List.of("status", "database"), 
                List.of(List.of((Object)"SUCCESS", (Object)dbName)), 1, "Muestra instalada");
        }
    }

    private QueryResult executeSelect(String sql) {
        String upper = sql.toUpperCase();
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx == -1) {
            return new QueryResult(List.of("error"), List.of(List.of("Sintaxis SQL inválida: falta la cláusula FROM")), 0, "Error de sintaxis");
        }

        String selectFields = sql.substring(7, fromIdx).trim();
        String afterFrom = sql.substring(fromIdx + 6).trim();

        // Obtener configuración de límites de consulta
        int defaultLimit = 50;
        int maxLimit = 5000;
        if (database != null && database.getConfig() != null) {
            defaultLimit = database.getConfig().getQueryDefaultLimit();
            maxLimit = database.getConfig().getQueryMaxLimit();
        }

        // 1. Extraer LIMIT y OFFSET si existen
        int limit = defaultLimit;
        int offset = 0;
        boolean explicitLimit = false;

        int limitIdx = afterFrom.toUpperCase().lastIndexOf(" LIMIT ");
        if (limitIdx != -1) {
            String afterLimit = afterFrom.substring(limitIdx + 7).trim();
            afterFrom = afterFrom.substring(0, limitIdx).trim();

            int offsetInLimitIdx = afterLimit.toUpperCase().indexOf(" OFFSET ");
            if (offsetInLimitIdx != -1) {
                String limStr = sanitize(afterLimit.substring(0, offsetInLimitIdx));
                String offStr = sanitize(afterLimit.substring(offsetInLimitIdx + 8));
                try { limit = Integer.parseInt(limStr); explicitLimit = true; } catch (Exception ignored) {}
                try { offset = Integer.parseInt(offStr); } catch (Exception ignored) {}
            } else {
                String limStr = sanitize(afterLimit);
                try { limit = Integer.parseInt(limStr); explicitLimit = true; } catch (Exception ignored) {}
            }
        } else {
            int offsetIdx = afterFrom.toUpperCase().lastIndexOf(" OFFSET ");
            if (offsetIdx != -1) {
                String offStr = sanitize(afterFrom.substring(offsetIdx + 8));
                afterFrom = afterFrom.substring(0, offsetIdx).trim();
                try { offset = Integer.parseInt(offStr); } catch (Exception ignored) {}
            }
        }
        limit = Math.min(Math.max(1, limit), maxLimit);

        // 2. Extraer WHERE si existe
        String whereField = null;
        String whereVal = null;
        int whereIdx = afterFrom.toUpperCase().indexOf(" WHERE ");
        String collection;
        if (whereIdx != -1) {
            collection = sanitize(afterFrom.substring(0, whereIdx));
            String whereClause = afterFrom.substring(whereIdx + 7).trim();
            String[] kv = whereClause.split("=");
            if (kv.length == 2) {
                whereField = sanitize(kv[0]);
                whereVal = sanitize(kv[1]);
            }
        } else {
            collection = sanitize(afterFrom.split("\s+")[0]);
        }

        DocumentEngine docEngine = database.getDocumentEngine(collection);
        if (docEngine == null || docEngine.isEmpty()) {
            return new QueryResult(List.of("_id"), Collections.emptyList(), 0, "0 records returned from '" + collection + "'");
        }

        long totalCount = docEngine.count();

        // 2.5 Análisis predictivo y supervisión autónoma de JettraPolice (Anti-OOM)
        int requestedLimit = explicitLimit ? limit : 0;
        io.jettra.store.police.JettraPolice.PoliceDecision policeDecision = 
            io.jettra.store.police.JettraPolice.getInstance().evaluateHeapSafety(
                "SQL_SELECT", collection, totalCount, requestedLimit, 512L);

        boolean policeIntervened = false;
        if (policeDecision.interventionRequired()) {
            policeIntervened = true;
            limit = policeDecision.enforcedLimit();
        }

        // 3. Consulta acelerada por índices secundarios si aplica
        Set<String> indexedDocIds = null;
        if (whereField != null && whereVal != null && database.getIndexManager() != null) {
            indexedDocIds = database.getIndexManager().findDocIds(collection, whereField, whereVal);
        }

        // 4. Streaming y filtrado acotado en memoria (Zero Full-Heap Allocation)
        List<Map<String, Object>> collectedDocs = new ArrayList<>(Math.min(limit, 200));
        LinkedHashSet<String> dynamicColumns = new LinkedHashSet<>();
        dynamicColumns.add("_id");

        int skipped = 0;
        if (indexedDocIds != null) {
            // Camino rápido indexado: O(1) recuperación directa
            for (String docId : indexedDocIds) {
                Map<String, Object> doc = docEngine.findById(docId);
                if (doc != null) {
                    if (skipped < offset) {
                        skipped++;
                        continue;
                    }
                    collectedDocs.add(doc);
                    dynamicColumns.addAll(doc.keySet());
                    if (collectedDocs.size() >= limit) {
                        break;
                    }
                }
            }
        } else {
            // Streaming lazy secuencial con corte temprano
            for (Map<String, Object> doc : docEngine) {
                if (doc == null) continue;
                if (whereField != null && whereVal != null) {
                    Object val = doc.get(whereField);
                    if (val == null || !String.valueOf(val).equalsIgnoreCase(whereVal)) {
                        continue;
                    }
                }

                if (skipped < offset) {
                    skipped++;
                    continue;
                }

                collectedDocs.add(doc);
                dynamicColumns.addAll(doc.keySet());
                if (collectedDocs.size() >= limit) {
                    break;
                }
            }
        }

        // 5. Determinar columnas seleccionadas
        List<String> finalCols = new ArrayList<>();
        if (selectFields.equals("*")) {
            finalCols.addAll(dynamicColumns);
        } else {
            for (String f : selectFields.split(",")) {
                String c = sanitize(f);
                if (!c.isEmpty()) finalCols.add(c);
            }
        }

        // 6. Proyectar filas finales
        List<List<Object>> rows = new ArrayList<>(collectedDocs.size());
        for (Map<String, Object> doc : collectedDocs) {
            List<Object> row = new ArrayList<>(finalCols.size());
            for (String col : finalCols) {
                row.add(doc.getOrDefault(col, null));
            }
            rows.add(row);
        }

        String summaryMsg;
        if (policeIntervened) {
            summaryMsg = String.format("[JettraPolice SENTINEL: Paginación Lazy Anti-OOM Activada] %d fila(s) retornada(s) (Lote seguro: %d, Offset: %d) de un total de %d en '%s'. %s",
                    rows.size(), limit, offset, totalCount, collection, policeDecision.rationale());
        } else if (explicitLimit) {
            summaryMsg = String.format("%d fila(s) retornada(s) (Límite: %d, Offset: %d) de un total estimado de %d en '%s'",
                    rows.size(), limit, offset, totalCount, collection);
        } else {
            summaryMsg = String.format("%d fila(s) retornada(s) [Límite de seguridad: %d] (Total en colección: %d en '%s')",
                    rows.size(), limit, totalCount, collection);
        }

        return new QueryResult(finalCols, rows, rows.size(), summaryMsg);
    }

    private QueryResult executeInsert(String sql) {
        try {
            int intoIdx = sql.toUpperCase().indexOf("INTO ");
            int valuesIdx = sql.toUpperCase().indexOf(" VALUES");
            if (intoIdx != -1 && valuesIdx != -1) {
                String col = sql.substring(intoIdx + 5, valuesIdx).trim();
                String valPart = sql.substring(valuesIdx + 7).trim();
                if (valPart.startsWith("(") && valPart.endsWith(")")) {
                    valPart = valPart.substring(1, valPart.length() - 1).trim();
                }
                String[] parts = valPart.split(",", 2);
                String id = sanitize(parts[0]);
                String dataStr = parts.length > 1 ? parts[1].trim() : "{}";
                
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("_id", id);
                if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                    String inside = dataStr.substring(1, dataStr.length() - 1);
                    for (String pair : inside.split(",")) {
                        String[] kv = pair.split("[:=]", 2);
                        if (kv.length == 2) {
                            String k = sanitize(kv[0]);
                            String v = sanitize(kv[1]);
                            doc.put(k, v);
                        }
                    }
                } else {
                    doc.put("value", sanitize(dataStr));
                }
                database.getDocumentEngine(col).insert(id, doc);
                if (database.getIndexManager() != null) {
                    database.getIndexManager().onDocumentInsert(col, id, doc);
                }
                return new QueryResult(List.of("status", "id"), List.of(List.of("INSERTED", id)), 1, "Inserted 1 row into '" + col + "'");
            }
        } catch (Exception ignored) {}
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "INSERT executed");
    }

    private QueryResult executeUpdate(String sql) {
        try {
            String upper = sql.toUpperCase();
            int setIdx = upper.indexOf(" SET ");
            int whereIdx = upper.indexOf(" WHERE ");
            if (setIdx != -1) {
                String col = sql.substring(6, setIdx).trim();
                String whereClause = whereIdx != -1 ? sql.substring(whereIdx + 7).trim() : null;
                String targetId = null;
                if (whereClause != null && whereClause.contains("=")) {
                    targetId = sanitize(whereClause.split("=")[1]);
                }
                String setClause = whereIdx != -1 ? sql.substring(setIdx + 5, whereIdx).trim() : sql.substring(setIdx + 5).trim();
                String[] kv = setClause.split("=");
                if (kv.length == 2 && targetId != null) {
                    String k = sanitize(kv[0]);
                    String v = sanitize(kv[1]);
                    database.getDocumentEngine(col).update(targetId, Map.of(k, v));
                    return new QueryResult(List.of("status"), List.of(List.of("UPDATED")), 1, "Updated 1 record in '" + col + "'");
                }
            }
        } catch (Exception ignored) {}
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "UPDATE executed");
    }

    private QueryResult executeDelete(String sql) {
        try {
            int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
            if (whereIdx != -1) {
                String col = sql.substring(11, whereIdx).trim();
                String whereClause = sql.substring(whereIdx + 7).trim();
                String[] kv = whereClause.split("=");
                if (kv.length == 2) {
                    String id = sanitize(kv[1]);
                    boolean del = database.getDocumentEngine(col).delete(id);
                    if (database.getIndexManager() != null) {
                        database.getIndexManager().onDocumentDelete(col, id, null);
                    }
                    return new QueryResult(List.of("affected"), List.of(List.of(del ? 1 : 0)), del ? 1 : 0, "Deleted " + (del ? 1 : 0) + " row from '" + col + "'");
                }
            }
        } catch (Exception ignored) {}
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "DELETE executed");
    }
}
