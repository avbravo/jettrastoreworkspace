package io.jettra.store.core;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import io.jettra.json.JsonArray;
import io.jettra.store.engine.index.JettraIndexManager;
import io.jettra.store.engine.models.*;
import io.jettra.store.engine.panama.NativeMemTable;
import io.jettra.store.sample.JettraStoreSamples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class JettraDatabase {
    private final String databaseName;
    private final JettraStoreConfig config;
    private final NativeMemTable memTable;
    private final JettraIndexManager indexManager;

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
        this.indexManager = new JettraIndexManager(databaseName);
        long memTableBytes = config.getMemTableSizeMb() * 1024L * 1024L;
        this.memTable = new NativeMemTable(memTableBytes);

        // 1. Cargar estado previo de disco si existe
        boolean loaded = loadFromDisk();

        // 2. Si es una base de datos de muestra y no tiene datos, cargar muestra automáticamente
        if (!loaded && getAllCollectionNames().isEmpty() && JettraStoreSamples.isSampleDatabase(databaseName)) {
            JettraStoreSamples.installSample(databaseName, this);
            try {
                flushMemTable();
            } catch (Exception ignored) {}
        }
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
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        memTable.flushToJettraFile(target);
        saveToDisk();
    }

    public static Path resolveMetaFile(String dbName, JettraStoreConfig config) {
        if (dbName == null) return null;
        if (config != null && config.getStoragePath() != null) {
            Path p = Path.of(config.getStoragePath(), dbName + "_meta.json");
            if (Files.exists(p)) return p;
        }
        if (config != null && config.getConfiguredStoragePath() != null) {
            Path p = Path.of(config.getConfiguredStoragePath(), dbName + "_meta.json");
            if (Files.exists(p)) return p;
        }
        Path p = Path.of("./data/jettra", dbName + "_meta.json");
        if (Files.exists(p)) return p;
        p = Path.of("data/jettra", dbName + "_meta.json");
        if (Files.exists(p)) return p;
        p = Path.of("../data/jettra", dbName + "_meta.json");
        if (Files.exists(p)) return p;
        p = Path.of(System.getProperty("user.home") + "/jettra/data", dbName + "_meta.json");
        if (Files.exists(p)) return p;
        return null;
    }

    public static int getLightweightCollectionCount(String databaseName, JettraStoreConfig config) {
        if (databaseName == null) return 0;
        if (JettraStoreSamples.isSampleDatabase(databaseName)) {
            return JettraStoreSamples.getSampleCollectionCount(databaseName);
        }
        Path metaFile = resolveMetaFile(databaseName, config);
        if (metaFile == null || !Files.exists(metaFile)) {
            return 0;
        }

        try (StreamingJsonScanner s = new StreamingJsonScanner(Files.newBufferedReader(metaFile, StandardCharsets.UTF_8))) {
            s.skipWhitespace();
            if (s.peek() == '{') s.read();
            Set<String> collections = new HashSet<>();
            while (s.peek() != -1 && s.peek() != '}') {
                s.skipWhitespaceAndSeparators();
                String key = s.readQuotedString();
                if (key == null) break;
                s.skipWhitespaceAndSeparators();
                if ("collections".equals(key)) {
                    s.skipWhitespace();
                    if (s.peek() == '[') {
                        s.read();
                        while (s.peek() != -1 && s.peek() != ']') {
                            s.skipWhitespaceAndSeparators();
                            String colName = s.readQuotedString();
                            if (colName != null) collections.add(colName);
                        }
                        return collections.size();
                    }
                } else if ("documentEngines".equals(key) || "keyValueEngines".equals(key) ||
                           "timeSeriesEngines".equals(key) || "geospatialEngines".equals(key) ||
                           "vectorEngines".equals(key) || "graphEngines".equals(key) ||
                           "columnarEngines".equals(key)) {
                    s.skipWhitespace();
                    if (s.peek() == '{') {
                        s.read();
                        while (s.peek() != -1 && s.peek() != '}') {
                            s.skipWhitespaceAndSeparators();
                            String itemKey = s.readQuotedString();
                            if (itemKey == null) break;
                            collections.add(itemKey);
                            s.skipValue();
                        }
                        s.skipWhitespace();
                        if (s.peek() == '}') s.read();
                    } else {
                        s.skipValue();
                    }
                } else {
                    s.skipValue();
                }
            }
            return collections.size();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean saveToDisk() {
        Path metaFile = Path.of(config.getStoragePath(), databaseName + "_meta.json");
        if (metaFile.getParent() != null) {
            try {
                Files.createDirectories(metaFile.getParent());
            } catch (IOException e) {
                return false;
            }
        }

        JettraJson json = new JettraJson();
        try (BufferedWriter writer = Files.newBufferedWriter(metaFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writer.write("{\n");
            writer.write("  \"databaseName\": \"" + databaseName + "\",\n");
            writer.write("  \"savedAt\": " + System.currentTimeMillis() + ",\n");

            // Metadatos de colecciones ligeras al inicio
            List<String> allCols = new ArrayList<>(getAllCollectionNames());
            writer.write("  \"collections\": [");
            for (int i = 0; i < allCols.size(); i++) {
                writer.write("\"" + JettraJson.escapeString(allCols.get(i)) + "\"" + (i < allCols.size() - 1 ? ", " : ""));
            }
            writer.write("],\n");

            // 1. Documentos
            writer.write("  \"documentEngines\": {\n");
            boolean firstCol = true;
            for (var entry : documentEngines.entrySet()) {
                if (!firstCol) writer.write(",\n");
                firstCol = false;
                writer.write("    \"" + JettraJson.escapeString(entry.getKey()) + "\": [\n");
                boolean firstDoc = true;
                for (var doc : entry.getValue().findAll()) {
                    if (!firstDoc) writer.write(",\n");
                    firstDoc = false;
                    JsonObject d = new JsonObject();
                    for (var kv : doc.entrySet()) {
                        if (kv.getValue() instanceof Number n) {
                            d.addProperty(kv.getKey(), n);
                        } else if (kv.getValue() instanceof Boolean b) {
                            d.addProperty(kv.getKey(), b);
                        } else {
                            d.addProperty(kv.getKey(), String.valueOf(kv.getValue()));
                        }
                    }
                    writer.write("      " + json.toJson(d));
                }
                writer.write("\n    ]");
            }
            writer.write("\n  },\n");

            // 2. Key-Value
            writer.write("  \"keyValueEngines\": {\n");
            boolean firstKV = true;
            for (var entry : keyValueEngines.entrySet()) {
                if (!firstKV) writer.write(",\n");
                firstKV = false;
                writer.write("    \"" + JettraJson.escapeString(entry.getKey()) + "\": {\n");
                boolean firstPair = true;
                for (var pair : entry.getValue().getAll().entrySet()) {
                    if (!firstPair) writer.write(",\n");
                    firstPair = false;
                    writer.write("      \"" + JettraJson.escapeString(pair.getKey()) + "\": \"" +
                            JettraJson.escapeString(new String(pair.getValue(), StandardCharsets.UTF_8)) + "\"");
                }
                writer.write("\n    }");
            }
            writer.write("\n  },\n");

            // 3. TimeSeries
            writer.write("  \"timeSeriesEngines\": {\n");
            boolean firstTS = true;
            for (var entry : timeSeriesEngines.entrySet()) {
                if (!firstTS) writer.write(",\n");
                firstTS = false;
                writer.write("    \"" + JettraJson.escapeString(entry.getKey()) + "\": {\n");
                boolean firstP = true;
                for (var p : entry.getValue().getAll().entrySet()) {
                    if (!firstP) writer.write(",\n");
                    firstP = false;
                    writer.write("      \"" + p.getKey() + "\": " + p.getValue());
                }
                writer.write("\n    }");
            }
            writer.write("\n  },\n");

            // 4. Geospatial
            writer.write("  \"geospatialEngines\": {\n");
            boolean firstGeo = true;
            for (var entry : geospatialEngines.entrySet()) {
                if (!firstGeo) writer.write(",\n");
                firstGeo = false;
                writer.write("    \"" + JettraJson.escapeString(entry.getKey()) + "\": [\n");
                boolean firstPt = true;
                for (var p : entry.getValue().getAllPoints().values()) {
                    if (!firstPt) writer.write(",\n");
                    firstPt = false;
                    writer.write("      {\"id\": \"" + JettraJson.escapeString(p.id()) +
                            "\", \"lat\": " + p.latitude() + ", \"lon\": " + p.longitude() + "}");
                }
                writer.write("\n    ]");
            }
            writer.write("\n  },\n");

            // 5. Vectors
            writer.write("  \"vectorEngines\": {\n");
            boolean firstVec = true;
            for (var entry : vectorEngines.entrySet()) {
                if (!firstVec) writer.write(",\n");
                firstVec = false;
                writer.write("    \"" + JettraJson.escapeString(entry.getKey()) + "\": {\n");
                writer.write("      \"dimensions\": " + entry.getValue().getDimensions() + ",\n");
                writer.write("      \"vectors\": {\n");
                boolean firstVData = true;
                for (var pair : entry.getValue().getAllVectors().entrySet()) {
                    if (!firstVData) writer.write(",\n");
                    firstVData = false;
                    writer.write("        \"" + JettraJson.escapeString(pair.getKey()) + "\": [");
                    float[] fa = pair.getValue();
                    for (int idx = 0; idx < fa.length; idx++) {
                        writer.write(String.valueOf(fa[idx]) + (idx < fa.length - 1 ? ", " : ""));
                    }
                    writer.write("]");
                }
                writer.write("\n      }\n    }");
            }
            writer.write("\n  },\n");

            // 6. Graphs
            writer.write("  \"graphEngines\": {\n");
            boolean firstGr = true;
            for (var entry : graphEngines.entrySet()) {
                if (!firstGr) writer.write(",\n");
                firstGr = false;
                writer.write("    \"" + JettraJson.escapeString(entry.getKey()) + "\": {\n");
                writer.write("      \"vertices\": [");
                var verts = new ArrayList<>(entry.getValue().getVertices());
                for (int i = 0; i < verts.size(); i++) {
                    writer.write("\"" + JettraJson.escapeString(verts.get(i)) + "\"" + (i < verts.size() - 1 ? ", " : ""));
                }
                writer.write("],\n      \"edges\": {\n");
                boolean firstEdge = true;
                for (var edgeEntry : entry.getValue().getAllEdges().entrySet()) {
                    if (!firstEdge) writer.write(",\n");
                    firstEdge = false;
                    writer.write("        \"" + JettraJson.escapeString(edgeEntry.getKey()) + "\": [\n");
                    boolean firstEl = true;
                    for (var edge : edgeEntry.getValue()) {
                        if (!firstEl) writer.write(",\n");
                        firstEl = false;
                        writer.write("          {\"target\": \"" + JettraJson.escapeString(edge.targetVertex()) +
                                "\", \"label\": \"" + JettraJson.escapeString(edge.label()) + "\"}");
                    }
                    writer.write("\n        ]");
                }
                writer.write("\n      }\n    }");
            }
            writer.write("\n  }\n");

            writer.write("}\n");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean loadFromDisk() {
        Path metaFile = resolveMetaFile(databaseName, config);
        if (metaFile == null || !Files.exists(metaFile)) {
            return false;
        }

        JettraJson json = new JettraJson();
        try (StreamingJsonScanner scanner = new StreamingJsonScanner(Files.newBufferedReader(metaFile, StandardCharsets.UTF_8))) {
            scanner.skipWhitespace();
            if (scanner.peek() == '{') scanner.read(); // consume root {

            while (scanner.peek() != -1 && scanner.peek() != '}') {
                scanner.skipWhitespaceAndSeparators();
                String sectionKey = scanner.readQuotedString();
                if (sectionKey == null) break;
                scanner.skipWhitespaceAndSeparators();

                switch (sectionKey) {
                    case "documentEngines" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '{') {
                            scanner.read(); // consume {
                            while (scanner.peek() != -1 && scanner.peek() != '}') {
                                scanner.skipWhitespaceAndSeparators();
                                String colName = scanner.readQuotedString();
                                if (colName == null) break;
                                scanner.skipWhitespaceAndSeparators();
                                if (scanner.peek() == '[') {
                                    scanner.read(); // consume [
                                    var docEng = getDocumentEngine(colName);
                                    Map<String, Map<String, Object>> batch = new HashMap<>(5000);
                                    while (scanner.peek() != -1 && scanner.peek() != ']') {
                                        scanner.skipWhitespaceAndSeparators();
                                        String docStr = scanner.readBalancedObject();
                                        if (docStr != null) {
                                            JsonObject d = json.fromJson(docStr, JsonObject.class);
                                            if (d != null) {
                                                String id = d.has("_id") ? d.getAsString("_id") : UUID.randomUUID().toString();
                                                Map<String, Object> map = new HashMap<>();
                                                for (String k : d.keySet()) {
                                                    map.put(k, d.getAsString(k));
                                                }
                                                batch.put(id, map);
                                                if (batch.size() >= 5000) {
                                                    docEng.insertBatch(batch);
                                                    batch.clear();
                                                }
                                            }
                                        }
                                    }
                                    if (!batch.isEmpty()) {
                                        docEng.insertBatch(batch);
                                        batch.clear();
                                    }
                                    scanner.skipWhitespace();
                                    if (scanner.peek() == ']') scanner.read();
                                } else {
                                    scanner.skipValue();
                                }
                            }
                            scanner.skipWhitespace();
                            if (scanner.peek() == '}') scanner.read();
                        } else {
                            scanner.skipValue();
                        }
                    }
                    case "keyValueEngines" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '{') {
                            scanner.read();
                            while (scanner.peek() != -1 && scanner.peek() != '}') {
                                scanner.skipWhitespaceAndSeparators();
                                String ns = scanner.readQuotedString();
                                if (ns == null) break;
                                scanner.skipWhitespaceAndSeparators();
                                if (scanner.peek() == '{') {
                                    scanner.read();
                                    var kvEng = getKeyValueEngine(ns);
                                    while (scanner.peek() != -1 && scanner.peek() != '}') {
                                        scanner.skipWhitespaceAndSeparators();
                                        String k = scanner.readQuotedString();
                                        if (k == null) break;
                                        scanner.skipWhitespaceAndSeparators();
                                        String v = scanner.readQuotedString();
                                        if (v != null) {
                                            kvEng.put(k, v.getBytes(StandardCharsets.UTF_8));
                                        } else {
                                            scanner.skipValue();
                                        }
                                    }
                                    scanner.skipWhitespace();
                                    if (scanner.peek() == '}') scanner.read();
                                } else {
                                    scanner.skipValue();
                                }
                            }
                            scanner.skipWhitespace();
                            if (scanner.peek() == '}') scanner.read();
                        } else {
                            scanner.skipValue();
                        }
                    }
                    case "timeSeriesEngines" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '{') {
                            scanner.read();
                            while (scanner.peek() != -1 && scanner.peek() != '}') {
                                scanner.skipWhitespaceAndSeparators();
                                String metric = scanner.readQuotedString();
                                if (metric == null) break;
                                scanner.skipWhitespaceAndSeparators();
                                if (scanner.peek() == '{') {
                                    scanner.read();
                                    var tsEng = getTimeSeriesEngine(metric);
                                    while (scanner.peek() != -1 && scanner.peek() != '}') {
                                        scanner.skipWhitespaceAndSeparators();
                                        String tsStr = scanner.readQuotedString();
                                        if (tsStr == null) break;
                                        scanner.skipWhitespaceAndSeparators();
                                        String valStr = scanner.readPrimitiveToken();
                                        if (valStr != null) {
                                            try {
                                                long ts = Long.parseLong(tsStr);
                                                double val = Double.parseDouble(valStr);
                                                tsEng.record(ts, val);
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                    scanner.skipWhitespace();
                                    if (scanner.peek() == '}') scanner.read();
                                } else {
                                    scanner.skipValue();
                                }
                            }
                            scanner.skipWhitespace();
                            if (scanner.peek() == '}') scanner.read();
                        } else {
                            scanner.skipValue();
                        }
                    }
                    case "geospatialEngines" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '{') {
                            scanner.read();
                            while (scanner.peek() != -1 && scanner.peek() != '}') {
                                scanner.skipWhitespaceAndSeparators();
                                String layer = scanner.readQuotedString();
                                if (layer == null) break;
                                scanner.skipWhitespaceAndSeparators();
                                if (scanner.peek() == '[') {
                                    scanner.read();
                                    var geoEng = getGeospatialEngine(layer);
                                    while (scanner.peek() != -1 && scanner.peek() != ']') {
                                        scanner.skipWhitespaceAndSeparators();
                                        String ptStr = scanner.readBalancedObject();
                                        if (ptStr != null) {
                                            JsonObject gp = json.fromJson(ptStr, JsonObject.class);
                                            if (gp != null && gp.has("id") && gp.has("lat") && gp.has("lon")) {
                                                String id = gp.getAsString("id");
                                                double lat = Double.parseDouble(gp.getAsString("lat"));
                                                double lon = Double.parseDouble(gp.getAsString("lon"));
                                                geoEng.insertPoint(id, lat, lon);
                                            }
                                        }
                                    }
                                    scanner.skipWhitespace();
                                    if (scanner.peek() == ']') scanner.read();
                                } else {
                                    scanner.skipValue();
                                }
                            }
                            scanner.skipWhitespace();
                            if (scanner.peek() == '}') scanner.read();
                        } else {
                            scanner.skipValue();
                        }
                    }
                    case "vectorEngines" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '{') {
                            scanner.read();
                            while (scanner.peek() != -1 && scanner.peek() != '}') {
                                scanner.skipWhitespaceAndSeparators();
                                String vName = scanner.readQuotedString();
                                if (vName == null) break;
                                scanner.skipWhitespaceAndSeparators();
                                String vecObjStr = scanner.readBalancedObject();
                                if (vecObjStr != null) {
                                    JsonObject vo = json.fromJson(vecObjStr, JsonObject.class);
                                    if (vo != null) {
                                        int dims = vo.has("dimensions") ? vo.getAsInt("dimensions") : 3;
                                        var vecEng = getVectorEngine(vName, dims);
                                        if (vo.has("vectors")) {
                                            JsonObject vData = vo.getAsJsonObject("vectors");
                                            for (String vid : vData.keySet()) {
                                                JsonArray va = vData.getAsJsonArray(vid);
                                                float[] fa = new float[va.size()];
                                                for (int idx = 0; idx < va.size(); idx++) {
                                                    fa[idx] = Float.parseFloat(va.get(idx).toString());
                                                }
                                                vecEng.index(vid, fa);
                                            }
                                        }
                                    }
                                }
                            }
                            scanner.skipWhitespace();
                            if (scanner.peek() == '}') scanner.read();
                        } else {
                            scanner.skipValue();
                        }
                    }
                    case "graphEngines" -> {
                        scanner.skipWhitespace();
                        if (scanner.peek() == '{') {
                            scanner.read();
                            while (scanner.peek() != -1 && scanner.peek() != '}') {
                                scanner.skipWhitespaceAndSeparators();
                                String gName = scanner.readQuotedString();
                                if (gName == null) break;
                                scanner.skipWhitespaceAndSeparators();
                                String grObjStr = scanner.readBalancedObject();
                                if (grObjStr != null) {
                                    JsonObject go = json.fromJson(grObjStr, JsonObject.class);
                                    if (go != null) {
                                        var gEng = getGraphEngine(gName);
                                        if (go.has("vertices")) {
                                            JsonArray va = go.getAsJsonArray("vertices");
                                            for (int i = 0; i < va.size(); i++) {
                                                gEng.addVertex(va.get(i).toString());
                                            }
                                        }
                                        if (go.has("edges")) {
                                            JsonObject edges = go.getAsJsonObject("edges");
                                            for (String src : edges.keySet()) {
                                                JsonArray ea = edges.getAsJsonArray(src);
                                                for (int i = 0; i < ea.size(); i++) {
                                                    JsonObject eo = ea.getAsJsonObject(i);
                                                    String tgt = eo.getAsString("target");
                                                    String lbl = eo.getAsString("label");
                                                    gEng.addEdge(src, tgt, lbl, Map.of());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            scanner.skipWhitespace();
                            if (scanner.peek() == '}') scanner.read();
                        } else {
                            scanner.skipValue();
                        }
                    }
                    default -> scanner.skipValue();
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static final class StreamingJsonScanner implements AutoCloseable {
        private final Reader reader;
        private int peekChar = -2;

        public StreamingJsonScanner(Reader reader) {
            this.reader = (reader instanceof BufferedReader br) ? br : new BufferedReader(reader, 65536);
        }

        public int peek() throws IOException {
            if (peekChar == -2) {
                peekChar = reader.read();
            }
            return peekChar;
        }

        public int read() throws IOException {
            if (peekChar != -2) {
                int c = peekChar;
                peekChar = -2;
                return c;
            }
            return reader.read();
        }

        public void skipWhitespaceAndSeparators() throws IOException {
            int c;
            while ((c = peek()) != -1) {
                if (Character.isWhitespace(c) || c == ',' || c == ':') {
                    read();
                } else {
                    break;
                }
            }
        }

        public void skipWhitespace() throws IOException {
            int c;
            while ((c = peek()) != -1) {
                if (Character.isWhitespace(c)) {
                    read();
                } else {
                    break;
                }
            }
        }

        public String readQuotedString() throws IOException {
            skipWhitespace();
            int c = peek();
            if (c != '"') return null;
            read(); // consume opening "
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            while ((c = read()) != -1) {
                if (escaped) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 'r') sb.append('\r');
                    else if (c == 't') sb.append('\t');
                    else sb.append((char) c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append((char) c);
                }
            }
            return sb.toString();
        }

        public String readPrimitiveToken() throws IOException {
            skipWhitespace();
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = peek()) != -1) {
                if (Character.isWhitespace(c) || c == ',' || c == '}' || c == ']' || c == ':') {
                    break;
                }
                sb.append((char) read());
            }
            return sb.length() > 0 ? sb.toString() : null;
        }

        public String readBalancedObject() throws IOException {
            skipWhitespace();
            int c = peek();
            if (c != '{') return null;
            read(); // consume '{'
            StringBuilder sb = new StringBuilder(256);
            sb.append('{');
            int depth = 1;
            boolean inString = false;
            boolean escaped = false;
            while ((c = read()) != -1) {
                sb.append((char) c);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                } else {
                    if (c == '"') {
                        inString = true;
                    } else if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            return sb.toString();
                        }
                    }
                }
            }
            return sb.toString();
        }

        public void skipBalanced(char open, char close) throws IOException {
            skipWhitespace();
            int c = peek();
            if (c != open) return;
            read(); // consume open
            int depth = 1;
            boolean inString = false;
            boolean escaped = false;
            while ((c = read()) != -1) {
                if (inString) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') inString = false;
                } else {
                    if (c == '"') inString = true;
                    else if (c == open) depth++;
                    else if (c == close) {
                        depth--;
                        if (depth == 0) return;
                    }
                }
            }
        }

        public void skipValue() throws IOException {
            skipWhitespaceAndSeparators();
            int c = peek();
            if (c == '{') {
                skipBalanced('{', '}');
            } else if (c == '[') {
                skipBalanced('[', ']');
            } else if (c == '"') {
                readQuotedString();
            } else {
                while ((c = peek()) != -1 && c != ',' && c != '}' && c != ']' && !Character.isWhitespace(c)) {
                    read();
                }
            }
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    public void drop() {
        try {
            memTable.close();
        } catch (Exception ignored) {}
        documentEngines.clear();
        vectorEngines.clear();
        graphEngines.clear();
        timeSeriesEngines.clear();
        keyValueEngines.clear();
        geospatialEngines.clear();
        columnarEngines.clear();
        deleteStorageFiles();
    }

    private void deleteStorageFiles() {
        String[] paths = {
            config.getStoragePath(),
            config.getConfiguredStoragePath(),
            "./data/jettra",
            "data/jettra",
            "../data/jettra",
            "/jettra/data"
        };
        for (String pStr : paths) {
            if (pStr == null || pStr.isBlank()) continue;
            try {
                Path dir = Path.of(pStr);
                Files.deleteIfExists(dir.resolve(databaseName + "_meta.json"));
                Files.deleteIfExists(dir.resolve(databaseName + "_sstable" + config.getFileExtension()));
                Files.deleteIfExists(dir.resolve(databaseName + config.getFileExtension()));
                Files.deleteIfExists(dir.resolve(databaseName + "_sstable.jettra"));
                Files.deleteIfExists(dir.resolve(databaseName + ".jettra"));
                Files.deleteIfExists(dir.resolve(databaseName + "_wal.jettra"));
            } catch (Exception ignored) {}
        }
    }

    public String getEngineType(String collectionName) {
        if (documentEngines.containsKey(collectionName)) return "DOCUMENT";
        if (vectorEngines.containsKey(collectionName)) return "VECTOR";
        if (graphEngines.containsKey(collectionName)) return "GRAPH";
        if (timeSeriesEngines.containsKey(collectionName)) return "TIMESERIES";
        if (keyValueEngines.containsKey(collectionName)) return "KEYVALUE";
        if (geospatialEngines.containsKey(collectionName)) return "GEOSPATIAL";
        if (columnarEngines.containsKey(collectionName)) return "COLUMNAR";
        return "DOCUMENT";
    }

    public long getRecordCount(String collectionName) {
        if (documentEngines.containsKey(collectionName)) return documentEngines.get(collectionName).count();
        if (vectorEngines.containsKey(collectionName)) return vectorEngines.get(collectionName).size();
        if (graphEngines.containsKey(collectionName)) return graphEngines.get(collectionName).size();
        if (timeSeriesEngines.containsKey(collectionName)) return timeSeriesEngines.get(collectionName).size();
        if (keyValueEngines.containsKey(collectionName)) return keyValueEngines.get(collectionName).size();
        if (geospatialEngines.containsKey(collectionName)) return geospatialEngines.get(collectionName).size();
        if (columnarEngines.containsKey(collectionName)) return columnarEngines.get(collectionName).size();
        return 0;
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
        if (removed) {
            saveToDisk();
        }
        return removed;
    }

    public String getDatabaseName() { return databaseName; }
    public NativeMemTable getMemTable() { return memTable; }
    public JettraStoreConfig getConfig() { return config; }
    public JettraIndexManager getIndexManager() { return indexManager; }
}
