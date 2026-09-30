package io.jettra.store.engine.models;

import io.jettra.collections.map.UnifiedMap;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Motor de documentos de ultra-alto rendimiento optimizado para memoria en Java 25.
 * Basado internamente en JettraCollections (UnifiedMap) para eliminar el 100% de los
 * nodos HashMap$Node/ConcurrentHashMap$Node, y en vistas perezosas (LazyDocumentList / Streams)
 * para evitar sobrecargar el heap con toArray() y listas intermedias.
 */
public final class DocumentEngine implements Iterable<Map<String, Object>> {
    private final String collectionName;
    private final io.jettra.store.core.JettraDatabase database;
    private final UnifiedMap<String, Map<String, Object>> documents = UnifiedMap.newMap(64);
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock rLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock wLock = rwLock.writeLock();

    public DocumentEngine(String collectionName) {
        this(collectionName, null);
    }

    public DocumentEngine(String collectionName, io.jettra.store.core.JettraDatabase database) {
        this.collectionName = collectionName;
        this.database = database;
    }

    public void insert(String id, Map<String, Object> document) {
        wLock.lock();
        try {
            Map<String, Object> copy = UnifiedMap.newMap(Math.max(4, document.size() + 1));
            copy.putAll(document);
            copy.put("_id", id);
            documents.put(id, copy);
            if (database != null && database.getStorageMode().isDiskMemory()) {
                persistToDiskMemory(id, copy);
            }
        } finally {
            wLock.unlock();
        }
    }

    public Map<String, Object> findById(String id) {
        rLock.lock();
        try {
            Map<String, Object> doc = documents.get(id);
            if (doc == null && database != null && database.getStorageMode().isDiskMemory()) {
                doc = loadFromDiskMemory(id);
            }
            return doc;
        } finally {
            rLock.unlock();
        }
    }

    private void persistToDiskMemory(String id, Map<String, Object> doc) {
        try {
            String jsonStr = new io.jettra.json.JettraJson().toJson(doc);
            database.putRecordDiskMemory((byte) 1, collectionName + ":" + id, jsonStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void removeFromDiskMemory(String id) {
        try {
            database.deleteRecordDiskMemory((byte) 1, collectionName + ":" + id);
        } catch (Exception ignored) {}
    }

    private Map<String, Object> loadFromDiskMemory(String id) {
        try {
            byte[] bytes = database.getRecordDiskMemory((byte) 1, collectionName + ":" + id);
            if (bytes != null) {
                String jsonStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                return new io.jettra.json.JettraJson().fromJson(jsonStr, Map.class);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Retorna una vista de lista perezosa (Lazy Load) sin duplicar datos en el heap
     * y sin invocar toArray(). Soporta iteración O(1) de memoria, stream() y size().
     */
    public List<Map<String, Object>> findAll() {
        return new LazyDocumentList();
    }

    /**
     * Stream eficiente sobre los documentos de la colección sin volcar listas a memoria.
     */
    public Stream<Map<String, Object>> stream() {
        return StreamSupport.stream(Spliterators.spliterator(iterator(), count(), Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    @Override
    public Iterator<Map<String, Object>> iterator() {
        rLock.lock();
        try {
            return documents.values().iterator();
        } finally {
            rLock.unlock();
        }
    }

    public Iterable<Map<String, Object>> asIterable() {
        return this;
    }

    @Override
    public void forEach(Consumer<? super Map<String, Object>> action) {
        Objects.requireNonNull(action);
        rLock.lock();
        try {
            for (Map<String, Object> doc : documents.values()) {
                action.accept(doc);
            }
        } finally {
            rLock.unlock();
        }
    }

    /**
     * Procesa documentos por lotes (batching) para control de memoria y liberación proactiva del GC.
     */
    public void forEachBatch(int batchSize, Consumer<List<Map<String, Object>>> batchConsumer) {
        Objects.requireNonNull(batchConsumer);
        int effectiveBatchSize = Math.max(100, batchSize);
        rLock.lock();
        try {
            List<Map<String, Object>> chunk = new ArrayList<>(effectiveBatchSize);
            for (Map<String, Object> doc : documents.values()) {
                chunk.add(doc);
                if (chunk.size() >= effectiveBatchSize) {
                    batchConsumer.accept(chunk);
                    chunk = new ArrayList<>(effectiveBatchSize);
                }
            }
            if (!chunk.isEmpty()) {
                batchConsumer.accept(chunk);
            }
        } finally {
            rLock.unlock();
        }
    }

    public void update(String id, Map<String, Object> updates) {
        wLock.lock();
        try {
            Map<String, Object> existing = documents.get(id);
            if (existing != null) {
                existing.putAll(updates);
                if (database != null && database.getStorageMode().isDiskMemory()) {
                    persistToDiskMemory(id, existing);
                }
            }
        } finally {
            wLock.unlock();
        }
    }

    public boolean delete(String id) {
        wLock.lock();
        try {
            boolean removed = documents.remove(id) != null;
            if (database != null && database.getStorageMode().isDiskMemory()) {
                removeFromDiskMemory(id);
            }
            return removed;
        } finally {
            wLock.unlock();
        }
    }

    public long count() {
        rLock.lock();
        try {
            return documents.size();
        } finally {
            rLock.unlock();
        }
    }

    public boolean isEmpty() {
        rLock.lock();
        try {
            return documents.isEmpty();
        } finally {
            rLock.unlock();
        }
    }

    public void clear() {
        wLock.lock();
        try {
            documents.clear();
        } finally {
            wLock.unlock();
        }
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void insertBatch(Map<String, Map<String, Object>> batch) {
        wLock.lock();
        try {
            for (Map.Entry<String, Map<String, Object>> entry : batch.entrySet()) {
                String id = entry.getKey();
                Map<String, Object> doc = entry.getValue();
                Map<String, Object> copy = UnifiedMap.newMap(Math.max(4, doc.size() + 1));
                copy.putAll(doc);
                copy.put("_id", id);
                documents.put(id, copy);
                if (database != null && database.getStorageMode().isDiskMemory()) {
                    persistToDiskMemory(id, copy);
                }
            }
        } finally {
            wLock.unlock();
        }
    }

    /**
     * Implementación perezosa de List que no duplica colecciones en el Heap
     * y previene OutOfMemoryError por llamadas a toArray().
     */
    public final class LazyDocumentList extends AbstractList<Map<String, Object>> implements RandomAccess {
        @Override
        public int size() {
            return (int) DocumentEngine.this.count();
        }

        @Override
        public boolean isEmpty() {
            return DocumentEngine.this.isEmpty();
        }

        @Override
        public Iterator<Map<String, Object>> iterator() {
            return DocumentEngine.this.iterator();
        }

        @Override
        public Spliterator<Map<String, Object>> spliterator() {
            return DocumentEngine.this.stream().spliterator();
        }

        @Override
        public Stream<Map<String, Object>> stream() {
            return DocumentEngine.this.stream();
        }

        @Override
        public void forEach(Consumer<? super Map<String, Object>> action) {
            DocumentEngine.this.forEach(action);
        }

        @Override
        public void clear() {
            DocumentEngine.this.clear();
        }

        @Override
        public Map<String, Object> get(int index) {
            rLock.lock();
            try {
                if (index < 0 || index >= size()) {
                    throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
                }
                int curr = 0;
                for (Map<String, Object> doc : documents.values()) {
                    if (curr++ == index) {
                        return doc;
                    }
                }
                throw new IndexOutOfBoundsException("Index: " + index);
            } finally {
                rLock.unlock();
            }
        }
    }
}
