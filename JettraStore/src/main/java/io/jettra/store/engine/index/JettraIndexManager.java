package io.jettra.store.engine.index;

import io.jettra.store.engine.models.DocumentEngine;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public final class JettraIndexManager {
    public record IndexInfo(
        String name, 
        String collection, 
        String field, 
        String type, 
        boolean unique, 
        long entriesCount, 
        long createdAt
    ) {}

    private final String databaseName;
    private final Map<String, IndexInfo> indexMetadata = new ConcurrentHashMap<>();
    // indexName -> (fieldValue -> Set<docId>)
    private final Map<String, Map<Object, Set<String>>> indexData = new ConcurrentHashMap<>();

    public JettraIndexManager(String databaseName) {
        this.databaseName = databaseName;
    }

    public synchronized IndexInfo createIndex(String collection, String indexName, String field, String type, boolean unique, DocumentEngine docEngine) {
        if (indexMetadata.containsKey(indexName)) {
            throw new IllegalArgumentException("Index '" + indexName + "' already exists in database '" + databaseName + "'.");
        }
        IndexInfo info = new IndexInfo(indexName, collection, field, type.toUpperCase(), unique, 0, System.currentTimeMillis());
        indexMetadata.put(indexName, info);
        indexData.put(indexName, new ConcurrentHashMap<>());

        if (docEngine != null) {
            rebuildIndex(indexName, docEngine);
        }
        return indexMetadata.get(indexName);
    }

    public synchronized boolean dropIndex(String indexName) {
        indexData.remove(indexName);
        return indexMetadata.remove(indexName) != null;
    }

    public synchronized IndexInfo rebuildIndex(String indexName, DocumentEngine docEngine) {
        IndexInfo info = indexMetadata.get(indexName);
        if (info == null) {
            throw new NoSuchElementException("Index '" + indexName + "' not found.");
        }
        Map<Object, Set<String>> inverted = new ConcurrentHashMap<>();
        long count = 0;

        if (docEngine != null) {
            for (Map<String, Object> doc : docEngine.findAll()) {
                String id = String.valueOf(doc.get("_id"));
                Object val = doc.get(info.field());
                if (val != null) {
                    inverted.computeIfAbsent(val, k -> new CopyOnWriteArraySet<>()).add(id);
                    count++;
                }
            }
        }

        indexData.put(indexName, inverted);
        IndexInfo updated = new IndexInfo(info.name(), info.collection(), info.field(), info.type(), info.unique(), count, info.createdAt());
        indexMetadata.put(indexName, updated);
        return updated;
    }

    public void onDocumentInsert(String collection, String id, Map<String, Object> doc) {
        for (IndexInfo info : indexMetadata.values()) {
            if (info.collection().equalsIgnoreCase(collection)) {
                Object val = doc.get(info.field());
                if (val != null) {
                    Map<Object, Set<String>> inverted = indexData.computeIfAbsent(info.name(), k -> new ConcurrentHashMap<>());
                    inverted.computeIfAbsent(val, k -> new CopyOnWriteArraySet<>()).add(id);
                }
            }
        }
    }

    public void onDocumentDelete(String collection, String id, Map<String, Object> oldDoc) {
        for (IndexInfo info : indexMetadata.values()) {
            if (info.collection().equalsIgnoreCase(collection)) {
                Map<Object, Set<String>> inverted = indexData.get(info.name());
                if (inverted != null) {
                    if (oldDoc != null) {
                        Object val = oldDoc.get(info.field());
                        if (val != null) {
                            Set<String> ids = inverted.get(val);
                            if (ids != null) ids.remove(id);
                        }
                    } else {
                        // Scan remove
                        for (Set<String> set : inverted.values()) {
                            set.remove(id);
                        }
                    }
                }
            }
        }
    }

    public Set<String> findDocIds(String collection, String field, Object value) {
        for (IndexInfo info : indexMetadata.values()) {
            if (info.collection().equalsIgnoreCase(collection) && info.field().equalsIgnoreCase(field)) {
                Map<Object, Set<String>> inverted = indexData.get(info.name());
                if (inverted != null) {
                    Set<String> set = inverted.get(value);
                    if (set != null) return Collections.unmodifiableSet(set);
                }
            }
        }
        return Collections.emptySet();
    }

    public List<IndexInfo> listIndexes(String collection) {
        List<IndexInfo> list = new ArrayList<>();
        for (IndexInfo info : indexMetadata.values()) {
            if (collection == null || collection.isBlank() || info.collection().equalsIgnoreCase(collection)) {
                list.add(info);
            }
        }
        list.sort(Comparator.comparing(IndexInfo::name));
        return list;
    }

    public IndexInfo getIndex(String indexName) {
        return indexMetadata.get(indexName);
    }

    public String getDatabaseName() {
        return databaseName;
    }
}
