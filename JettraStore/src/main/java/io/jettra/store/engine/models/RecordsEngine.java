package io.jettra.store.engine.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RecordsEngine<T extends Record> {
    private final String entityName;
    private final Class<T> recordClass;
    private final Map<String, T> records = new ConcurrentHashMap<>();

    public RecordsEngine(String entityName, Class<T> recordClass) {
        this.entityName = entityName;
        this.recordClass = recordClass;
    }

    public void persist(String id, T instance) {
        records.put(id, instance);
    }

    public T find(String id) {
        return records.get(id);
    }

    public List<T> listAll() {
        return new ArrayList<>(records.values());
    }

    public boolean remove(String id) {
        return records.remove(id) != null;
    }

    public String getEntityName() { return entityName; }
    public Class<T> getRecordClass() { return recordClass; }
    public int size() { return records.size(); }
}
