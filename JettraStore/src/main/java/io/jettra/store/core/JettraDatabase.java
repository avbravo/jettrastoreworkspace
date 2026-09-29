package io.jettra.store.core;

import io.jettra.store.engine.models.*;
import io.jettra.store.engine.panama.NativeMemTable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JettraDatabase {
    private final String databaseName;
    private final JettraStoreConfig config;
    private final NativeMemTable memTable;

    private final Map<String, DocumentEngine> documentEngines = new ConcurrentHashMap<>();
    private final Map<String, VectorEngine> vectorEngines = new ConcurrentHashMap<>();
    private final Map<String, GraphEngine> graphEngines = new ConcurrentHashMap<>();
    private final Map<String, TimeSeriesEngine> timeSeriesEngines = new ConcurrentHashMap<>();
    private final Map<String, KeyValueEngine> keyValueEngines = new ConcurrentHashMap<>();
    private final Map<String, GeospatialEngine> geospatialEngines = new ConcurrentHashMap<>();
    private final Map<String, ColumnarEngine> columnarEngines = new ConcurrentHashMap<>();

    public JettraDatabase(String databaseName, JettraStoreConfig config) {
        this.databaseName = databaseName;
        this.config = config;
        long memTableBytes = config.getMemTableSizeMb() * 1024L * 1024L;
        this.memTable = new NativeMemTable(memTableBytes);
    }

    public DocumentEngine getDocumentEngine(String name) {
        return documentEngines.computeIfAbsent(name, DocumentEngine::new);
    }

    public VectorEngine getVectorEngine(String name, int dimensions) {
        return vectorEngines.computeIfAbsent(name, k -> new VectorEngine(k, dimensions));
    }

    public GraphEngine getGraphEngine(String name) {
        return graphEngines.computeIfAbsent(name, GraphEngine::new);
    }

    public TimeSeriesEngine getTimeSeriesEngine(String name) {
        return timeSeriesEngines.computeIfAbsent(name, TimeSeriesEngine::new);
    }

    public KeyValueEngine getKeyValueEngine(String name) {
        return keyValueEngines.computeIfAbsent(name, KeyValueEngine::new);
    }

    public GeospatialEngine getGeospatialEngine(String name) {
        return geospatialEngines.computeIfAbsent(name, GeospatialEngine::new);
    }

    public ColumnarEngine getColumnarEngine(String name) {
        return columnarEngines.computeIfAbsent(name, ColumnarEngine::new);
    }

    public void flushMemTable() throws IOException {
        Path target = Path.of(config.getStoragePath(), databaseName + "_sstable" + config.getFileExtension());
        Files.createDirectories(target.getParent());
        memTable.flushToJettraFile(target);
    }

    public java.util.Set<String> getDocumentEngineNames() { return java.util.Collections.unmodifiableSet(documentEngines.keySet()); }
    public java.util.Set<String> getVectorEngineNames() { return java.util.Collections.unmodifiableSet(vectorEngines.keySet()); }
    public java.util.Set<String> getGraphEngineNames() { return java.util.Collections.unmodifiableSet(graphEngines.keySet()); }
    public java.util.Set<String> getTimeSeriesEngineNames() { return java.util.Collections.unmodifiableSet(timeSeriesEngines.keySet()); }
    public java.util.Set<String> getKeyValueEngineNames() { return java.util.Collections.unmodifiableSet(keyValueEngines.keySet()); }
    public java.util.Set<String> getGeospatialEngineNames() { return java.util.Collections.unmodifiableSet(geospatialEngines.keySet()); }
    public java.util.Set<String> getColumnarEngineNames() { return java.util.Collections.unmodifiableSet(columnarEngines.keySet()); }

    public java.util.Set<String> getAllCollectionNames() {
        java.util.Set<String> all = new java.util.TreeSet<>();
        all.addAll(documentEngines.keySet());
        all.addAll(vectorEngines.keySet());
        all.addAll(graphEngines.keySet());
        all.addAll(timeSeriesEngines.keySet());
        all.addAll(keyValueEngines.keySet());
        all.addAll(geospatialEngines.keySet());
        all.addAll(columnarEngines.keySet());
        return all;
    }

    public boolean dropCollection(String name) {
        boolean removed = false;
        if (documentEngines.remove(name) != null) removed = true;
        if (vectorEngines.remove(name) != null) removed = true;
        if (graphEngines.remove(name) != null) removed = true;
        if (timeSeriesEngines.remove(name) != null) removed = true;
        if (keyValueEngines.remove(name) != null) removed = true;
        if (geospatialEngines.remove(name) != null) removed = true;
        if (columnarEngines.remove(name) != null) removed = true;
        return removed;
    }

    public String getDatabaseName() { return databaseName; }
    public NativeMemTable getMemTable() { return memTable; }
    public JettraStoreConfig getConfig() { return config; }
}
