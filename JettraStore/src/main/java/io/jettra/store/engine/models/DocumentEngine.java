package io.jettra.store.engine.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DocumentEngine {
    private final String collectionName;
    private final Map<String, Map<String, Object>> documents = new ConcurrentHashMap<>();

    public DocumentEngine(String collectionName) {
        this.collectionName = collectionName;
    }

    public void insert(String id, Map<String, Object> document) {
        Map<String, Object> copy = new HashMap<>(document);
        copy.put("_id", id);
        documents.put(id, copy);
    }

    public Map<String, Object> findById(String id) {
        return documents.get(id);
    }

    public List<Map<String, Object>> findAll() {
        return new ArrayList<>(documents.values());
    }

    public void update(String id, Map<String, Object> updates) {
        Map<String, Object> existing = documents.get(id);
        if (existing != null) {
            existing.putAll(updates);
        }
    }

    public boolean delete(String id) {
        return documents.remove(id) != null;
    }

    public long count() {
        return documents.size();
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void insertBatch(Map<String, Map<String, Object>> batch) {
        documents.putAll(batch);
    }
}
