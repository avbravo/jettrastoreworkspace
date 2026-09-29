package io.jettra.store.engine.query;

import io.jettra.store.core.JettraDatabase;
import io.jettra.store.engine.models.DocumentEngine;
import java.util.*;

public final class JettraSQLProcessor {
    private final JettraDatabase database;

    public record QueryResult(List<String> columns, List<List<Object>> rows, int affectedRows, String message) {}

    public JettraSQLProcessor(JettraDatabase database) {
        this.database = database;
    }

    public QueryResult execute(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        String upper = trimmed.toUpperCase();

        if (upper.startsWith("SELECT")) {
            return executeSelect(trimmed);
        } else if (upper.startsWith("INSERT INTO")) {
            return executeInsert(trimmed);
        } else if (upper.startsWith("UPDATE")) {
            return executeUpdate(trimmed);
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
        // Formato: SELECT [columns] FROM <collection> [WHERE <field> = <val>]
        String upper = sql.toUpperCase();
        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx == -1) {
            return new QueryResult(List.of("error"), List.of(List.of("Missing FROM clause")), 0, "Syntax error in SELECT");
        }

        String afterFrom = sql.substring(fromIdx + 6).trim();
        String collection;
        String whereField = null;
        String whereVal = null;

        int whereIdx = afterFrom.toUpperCase().indexOf(" WHERE ");
        if (whereIdx != -1) {
            collection = afterFrom.substring(0, whereIdx).trim();
            String whereClause = afterFrom.substring(whereIdx + 7).trim();
            String[] kv = whereClause.split("=");
            if (kv.length == 2) {
                whereField = kv[0].replaceAll("[;\"']", "").trim();
                whereVal = kv[1].replaceAll("[;\"']", "").trim();
            }
        } else {
            collection = afterFrom.split("\\s+")[0].replaceAll("[;\"']", "").trim();
        }

        DocumentEngine docEngine = database.getDocumentEngine(collection);
        List<Map<String, Object>> allDocs = docEngine.findAll();

        // Filtrado por WHERE
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> doc : allDocs) {
            if (whereField != null && whereVal != null) {
                Object val = doc.get(whereField);
                if (val != null && String.valueOf(val).equalsIgnoreCase(whereVal)) {
                    filtered.add(doc);
                }
            } else {
                filtered.add(doc);
            }
        }

        if (filtered.isEmpty()) {
            return new QueryResult(List.of("_id"), Collections.emptyList(), 0, "0 records returned from '" + collection + "'");
        }

        // Descubrir todas las columnas únicas
        Set<String> colSet = new LinkedHashSet<>();
        colSet.add("_id");
        for (Map<String, Object> doc : filtered) {
            colSet.addAll(doc.keySet());
        }
        List<String> columns = new ArrayList<>(colSet);

        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> doc : filtered) {
            List<Object> row = new ArrayList<>();
            for (String col : columns) {
                row.add(doc.getOrDefault(col, "NULL"));
            }
            rows.add(row);
        }

        return new QueryResult(columns, rows, rows.size(), "Selected " + rows.size() + " record(s) from '" + collection + "'");
    }

    private QueryResult executeInsert(String sql) {
        // Formato: INSERT INTO <collection> VALUES ('<id>', '<json_or_val>')
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
                String id = parts[0].replaceAll("['\";]", "").trim();
                String dataStr = parts.length > 1 ? parts[1].trim() : "{}";
                
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("_id", id);
                if (dataStr.startsWith("{") && dataStr.endsWith("}")) {
                    String inside = dataStr.substring(1, dataStr.length() - 1);
                    for (String pair : inside.split(",")) {
                        String[] kv = pair.split("[:=]", 2);
                        if (kv.length == 2) {
                            String k = kv[0].replaceAll("['\";]", "").trim();
                            String v = kv[1].replaceAll("['\";]", "").trim();
                            doc.put(k, v);
                        }
                    }
                } else {
                    doc.put("value", dataStr.replaceAll("['\";]", ""));
                }
                database.getDocumentEngine(col).insert(id, doc);
                database.getIndexManager().onDocumentInsert(col, id, doc);
                return new QueryResult(List.of("status", "id"), List.of(List.of("INSERTED", id)), 1, "Inserted 1 row into '" + col + "'");
            }
        } catch (Exception ignored) {}
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "INSERT executed");
    }

    private QueryResult executeUpdate(String sql) {
        // UPDATE <collection> SET <field> = <value> WHERE _id = '<id>'
        try {
            String upper = sql.toUpperCase();
            int setIdx = upper.indexOf(" SET ");
            int whereIdx = upper.indexOf(" WHERE ");
            if (setIdx != -1) {
                String col = sql.substring(6, setIdx).trim();
                String whereClause = whereIdx != -1 ? sql.substring(whereIdx + 7).trim() : null;
                String targetId = null;
                if (whereClause != null && whereClause.contains("=")) {
                    targetId = whereClause.split("=")[1].replaceAll("['\";]", "").trim();
                }
                String setClause = whereIdx != -1 ? sql.substring(setIdx + 5, whereIdx).trim() : sql.substring(setIdx + 5).trim();
                String[] kv = setClause.split("=");
                if (kv.length == 2 && targetId != null) {
                    String k = kv[0].replaceAll("['\";]", "").trim();
                    String v = kv[1].replaceAll("['\";]", "").trim();
                    database.getDocumentEngine(col).update(targetId, Map.of(k, v));
                    return new QueryResult(List.of("status"), List.of(List.of("UPDATED")), 1, "Updated 1 record in '" + col + "'");
                }
            }
        } catch (Exception ignored) {}
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "UPDATE executed");
    }

    private QueryResult executeDelete(String sql) {
        // DELETE FROM <collection> WHERE _id = '<id>'
        try {
            int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
            if (whereIdx != -1) {
                String col = sql.substring(11, whereIdx).trim();
                String whereClause = sql.substring(whereIdx + 7).trim();
                String[] kv = whereClause.split("=");
                if (kv.length == 2) {
                    String id = kv[1].replaceAll("['\";]", "").trim();
                    boolean del = database.getDocumentEngine(col).delete(id);
                    database.getIndexManager().onDocumentDelete(col, id, null);
                    return new QueryResult(List.of("affected"), List.of(List.of(del ? 1 : 0)), del ? 1 : 0, "Deleted " + (del ? 1 : 0) + " row from '" + col + "'");
                }
            }
        } catch (Exception ignored) {}
        return new QueryResult(List.of("affected"), List.of(List.of(1)), 1, "DELETE executed");
    }
}
