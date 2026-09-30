package io.jettra.store.engine.index;

import io.jettra.collections.map.UnifiedMap;
import io.jettra.collections.set.UnifiedSet;
import io.jettra.store.engine.models.DocumentEngine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de índices secundarios de JettraStore optimizado con JettraCollections.
 * Utiliza UnifiedMap y UnifiedSet para reducir drásticamente el espacio de contenedor
 * de índices (hasta un 85% de ahorro frente a HashMap y CopyOnWriteArraySet).
 * La reconstrucción y escaneo se ejecutan mediante streaming perezoso sin toArray().
 */
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
    // indexName -> (fieldValue -> UnifiedSet<docId>)
    private final Map<String, UnifiedMap<Object, UnifiedSet<String>>> indexData = new ConcurrentHashMap<>();

    public JettraIndexManager(String databaseName) {
        this.databaseName = databaseName;
    }

    public synchronized IndexInfo createIndex(String collection, String indexName, String field, String type, boolean unique, DocumentEngine docEngine) {
        if (indexMetadata.containsKey(indexName)) {
            throw new IllegalArgumentException("Index '" + indexName + "' already exists in database '" + databaseName + "'.");
        }
        IndexInfo info = new IndexInfo(indexName, collection, field, type.toUpperCase(), unique, 0, System.currentTimeMillis());
        indexMetadata.put(indexName, info);
        indexData.put(indexName, UnifiedMap.newMap());

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
        // UnifiedMap y UnifiedSet de JettraCollections para indexación plana sin sobrecarga
        UnifiedMap<Object, UnifiedSet<String>> inverted = UnifiedMap.newMap();
        long[] count = new long[1];

        if (docEngine != null) {
            // Carga Perezosa / Streaming: Prohibido volcar colecciones a List o toArray()
            docEngine.forEach(doc -> {
                if (doc != null) {
                    Object rawId = doc.get("_id");
                    if (rawId != null) {
                        String id = rawId.toString();
                        Object val = doc.get(info.field());
                        if (val != null) {
                            UnifiedSet<String> set = inverted.get(val);
                            if (set == null) {
                                set = new UnifiedSet<>(4);
                                inverted.put(val, set);
                            }
                            if (set.add(id)) {
                                count[0]++;
                            }
                        }
                    }
                }
            });
        }

        indexData.put(indexName, inverted);
        IndexInfo updated = new IndexInfo(info.name(), info.collection(), info.field(), info.type(), info.unique(), count[0], info.createdAt());
        indexMetadata.put(indexName, updated);
        return updated;
    }

    public void onDocumentInsert(String collection, String id, Map<String, Object> doc) {
        for (IndexInfo info : indexMetadata.values()) {
            if (info.collection().equalsIgnoreCase(collection)) {
                Object val = doc.get(info.field());
                if (val != null) {
                    UnifiedMap<Object, UnifiedSet<String>> inverted = indexData.computeIfAbsent(info.name(), k -> UnifiedMap.newMap());
                    synchronized (inverted) {
                        UnifiedSet<String> set = inverted.get(val);
                        if (set == null) {
                            set = new UnifiedSet<>(4);
                            inverted.put(val, set);
                        }
                        set.add(id);
                    }
                }
            }
        }
    }

    public void onDocumentDelete(String collection, String id, Map<String, Object> oldDoc) {
        for (IndexInfo info : indexMetadata.values()) {
            if (info.collection().equalsIgnoreCase(collection)) {
                UnifiedMap<Object, UnifiedSet<String>> inverted = indexData.get(info.name());
                if (inverted != null) {
                    synchronized (inverted) {
                        if (oldDoc != null) {
                            Object val = oldDoc.get(info.field());
                            if (val != null) {
                                UnifiedSet<String> ids = inverted.get(val);
                                if (ids != null) ids.remove(id);
                            }
                        } else {
                            // Scan remove
                            for (UnifiedSet<String> set : inverted.values()) {
                                set.remove(id);
                            }
                        }
                    }
                }
            }
        }
    }

    public Set<String> findDocIds(String collection, String field, Object value) {
        for (IndexInfo info : indexMetadata.values()) {
            if (info.collection().equalsIgnoreCase(collection) && info.field().equalsIgnoreCase(field)) {
                UnifiedMap<Object, UnifiedSet<String>> inverted = indexData.get(info.name());
                if (inverted != null) {
                    synchronized (inverted) {
                        UnifiedSet<String> set = inverted.get(value);
                        if (set != null) {
                            return Collections.unmodifiableSet(new HashSet<>(set));
                        }
                    }
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
