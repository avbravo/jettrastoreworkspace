package io.jettra.core.three.d.explorer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Catálogo multimodelo que provee acceso jerárquico (Árbol de Engines -> Buckets -> Registros Paginados)
 * para cualquier base de datos de JettraStore.
 */
public class EngineDataCatalog {
    private static EngineDataCatalog instance;
    public static synchronized EngineDataCatalog getInstance() {
        if (instance == null) {
            instance = new EngineDataCatalog();
        }
        return instance;
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Map<String, List<EngineBucket>> databaseBuckets = new HashMap<>();

    public EngineDataCatalog() {
        initFacturaDb();
        initHospitalDb();
        initAmbientalDb();
        initMetadataDb();
    }

    private void initFacturaDb() {
        List<EngineBucket> list = new ArrayList<>();
        String db = "example_factura_db";
        String now = LocalDateTime.now().format(FMT);

        // 1. DOCUMENT ENGINE
        EngineBucket bDocFacturas = new EngineBucket("DOCUMENT", "Document Engine (JSON Off-Heap)", "facturas", "Facturas comerciales electrónicas timbradas", 1_000_000L);
        for (int i = 1; i <= 25; i++) {
            String id = String.format("FAC-2026-%05d", i);
            String sum = String.format("Factura Fiscal #%d | Total: $%,.2f | Items: %d", i, (i * 124.50f + 85.0f), (i % 8 + 1));
            String det = String.format("{\n  \"id\": \"%s\",\n  \"emisor\": \"Corp Global SA\",\n  \"receptor\": \"Cliente_%d\",\n  \"subtotal\": %.2f,\n  \"iva\": %.2f,\n  \"total\": %.2f,\n  \"metodoPago\": \"TRANSFERENCIA_SPEI\",\n  \"estado\": \"TIMBRADO_VALIDADO\"\n}",
                id, i, (i * 124.50f), (i * 124.50f * 0.16f), (i * 124.50f * 1.16f));
            bDocFacturas.addRecord(new EngineRecord(id, "DOCUMENT", "facturas", sum, det, now));
        }
        list.add(bDocFacturas);

        EngineBucket bDocClientes = new EngineBucket("DOCUMENT", "Document Engine (JSON Off-Heap)", "clientes", "Registro fiscal y comercial de clientes", 200_000L);
        for (int i = 1; i <= 15; i++) {
            String id = String.format("CLI-%04d", i);
            String sum = String.format("Cliente: Empresa %c%d | RFC/RUC: CL%d9921", (char)('A' + (i % 26)), i, 1000 + i);
            String det = String.format("{\n  \"idCliente\": \"%s\",\n  \"razonSocial\": \"Comercializadora Alfa %d\",\n  \"limiteCredito\": $%,.2f,\n  \"zonaFiscal\": \"Norte-01\",\n  \"activo\": true\n}", id, i, i * 50000.0f);
            bDocClientes.addRecord(new EngineRecord(id, "DOCUMENT", "clientes", sum, det, now));
        }
        list.add(bDocClientes);

        // 2. GRAPH ENGINE
        EngineBucket bGraph = new EngineBucket("GRAPH", "Graph Engine (Redes de Nodos y Aristas)", "red_comercial", "Topología de relaciones Clientes -> Facturas -> Proveedores", 200_000L);
        for (int i = 1; i <= 20; i++) {
            String id = String.format("EDGE-PAGO-%04d", i);
            String sum = String.format("(Cliente_%d) ──[EMITE_PAGO $%,.2f]──> (Factura_%d)", i, i * 450.0f, i);
            String det = String.format("GraphEdge: {\n  \"sourceVertex\": \"VERTEX_CLI_%d\",\n  \"targetVertex\": \"VERTEX_FAC_%d\",\n  \"relationship\": \"EMITE_PAGO\",\n  \"weight\": %.2f,\n  \"directSettlement\": true\n}", i, i, i * 450.0f);
            bGraph.addRecord(new EngineRecord(id, "GRAPH", "red_comercial", sum, det, now));
        }
        list.add(bGraph);

        // 3. VECTOR ENGINE
        EngineBucket bVector = new EngineBucket("VECTOR", "Vector Engine (Indexación Cosine 3D/HD)", "factura_embeddings", "Embeddings multidimensionales para detección de anomalías con IA", 200_000L);
        for (int i = 1; i <= 18; i++) {
            String id = String.format("VEC-EMBED-%04d", i);
            float vx = (float)Math.sin(i * 0.45);
            float vy = (float)Math.cos(i * 0.45);
            float vz = (float)(Math.sin(i * 0.8) * 0.5);
            String sum = String.format("Vector 3D [%.3f, %.3f, %.3f] | Cosine: %.4f | Cluster: Grupo_%d", vx, vy, vz, 0.985f - (i * 0.005f), (i % 4));
            String det = String.format("VectorEmbedding {\n  \"id\": \"%s\",\n  \"dimensions\": 3,\n  \"coordinates\": [%.4f, %.4f, %.4f],\n  \"metric\": \"COSINE\",\n  \"confidenceScore\": %.3f,\n  \"anomalyDetected\": %b\n}",
                id, vx, vy, vz, 0.985f - (i * 0.005f), (i == 7));
            bVector.addRecord(new EngineRecord(id, "VECTOR", "factura_embeddings", sum, det, now));
        }
        list.add(bVector);

        // 4. JAVA RECORD ENGINE
        EngineBucket bRecord = new EngineBucket("JAVA_RECORD", "Java Record Engine (In-Memory Panama Structs)", "factura_records", "Registros inmutables en memoria nativa sin GC", 500_000L);
        for (int i = 1; i <= 15; i++) {
            String id = String.format("REC-STRUCT-%04d", i);
            String sum = String.format("record FacturaItemRecord(folio=%d, itemSku='SKU-%d', precio=%.2f)", 1000 + i, i * 3, i * 15.75f);
            String det = String.format("java.lang.Record: FacturaItemRecord[folio=%d, itemSku=\"SKU-%d\", precio=%.2f, tasaImpuesto=0.16, offHeapOffset=0x%08X]",
                1000 + i, i * 3, i * 15.75f, i * 64);
            bRecord.addRecord(new EngineRecord(id, "JAVA_RECORD", "factura_records", sum, det, now));
        }
        list.add(bRecord);

        // 5. KEYVALUE ENGINE
        EngineBucket bKv = new EngineBucket("KEYVALUE", "KeyValue Engine (Caché Ultrarrápida Off-Heap)", "cache_folios", "Pares Clave-Valor de folios fiscales y tokens en nanosegundos", 300_000L);
        for (int i = 1; i <= 15; i++) {
            String key = String.format("folio_cache:2026:FAC_%05d", i);
            String sum = String.format("Key: '%s' -> Value: 'UUID_TOKEN_%04X' [TTL: 3600s]", key, i * 7919);
            String det = String.format("KeyValueEntry {\n  \"key\": \"%s\",\n  \"value\": \"UUID_HASH_%08X\",\n  \"timeToLiveSeconds\": 3600,\n  \"accessLatencyNs\": 140,\n  \"storageType\": \"DIRECT_PANAMA_MEMORY\"\n}", key, i * 1234567);
            bKv.addRecord(new EngineRecord(key, "KEYVALUE", "cache_folios", sum, det, now));
        }
        list.add(bKv);

        // 6. TIMESERIES ENGINE
        EngineBucket bTs = new EngineBucket("TIMESERIES", "TimeSeries Engine (Métricas e Ingesta Streaming)", "volumen_facturacion", "Métricas agregadas de volumen por segundo y ventana temporal", 50_000L);
        for (int i = 1; i <= 15; i++) {
            String id = String.format("TS-WINDOW-%04d", i);
            String sum = String.format("Ventana T-%dm: %,d facturas emitidas | Tasa: %.1f ops/s", i, 1200 + i * 45, 150.0f + i * 2.5f);
            String det = String.format("TimeSeriesPoint {\n  \"timestamp\": \"%s\",\n  \"bucketInterval\": \"1_MINUTE\",\n  \"count\": %d,\n  \"throughputOps\": %.2f,\n  \"avgLatencyMs\": 0.35\n}", now, 1200 + i * 45, 150.0f + i * 2.5f);
            bTs.addRecord(new EngineRecord(id, "TIMESERIES", "volumen_facturacion", sum, det, now));
        }
        list.add(bTs);

        // 7. GEOSPATIAL ENGINE
        EngineBucket bGeo = new EngineBucket("GEOSPATIAL", "Geospatial Engine (Índices R-Tree 2D/3D)", "tiendas_coordenadas", "Coordenadas geográficas y radios de cobertura de sucursales", 50_000L);
        for (int i = 1; i <= 15; i++) {
            String id = String.format("GEO-SUC-%04d", i);
            double lat = 19.4326 + (i * 0.012);
            double lon = -99.1332 - (i * 0.015);
            String sum = String.format("Sucursal #%d | Point(Lat: %.4f, Lon: %.4f) | Radio: 5.0 km", i, lat, lon);
            String det = String.format("GeoPoint {\n  \"id\": \"%s\",\n  \"sucursal\": \"Sede Comercial %d\",\n  \"latitude\": %.6f,\n  \"longitude\": %.6f,\n  \"altitudeMeters\": 2240,\n  \"coverageRadiusKm\": 5.0\n}", id, i, lat, lon);
            bGeo.addRecord(new EngineRecord(id, "GEOSPATIAL", "tiendas_coordenadas", sum, det, now));
        }
        list.add(bGeo);

        // 8. COLUMNAR ENGINE
        EngineBucket bCol = new EngineBucket("COLUMNAR", "Columnar Engine (Compresión Vectorial Parquet)", "metricas_analiticas", "Columnas numéricas comprimidas para agregaciones SUM, AVG y GROUP BY", 100_000L);
        for (int i = 1; i <= 12; i++) {
            String id = String.format("COL-CHUNK-%04d", i);
            String sum = String.format("Chunk Columnar #%d (10,000 filas) | SUM(total): $%,.2f | AVG: $%.2f", i, i * 450000.0f, 450.0f);
            String det = String.format("ColumnChunk {\n  \"chunkId\": %d,\n  \"rowCount\": 10000,\n  \"columns\": [\"total\", \"iva\", \"descuento\"],\n  \"compression\": \"ZSTD_SIMD\",\n  \"sumTotal\": %.2f,\n  \"avgTotal\": 450.00\n}", i, i * 450000.0f);
            bCol.addRecord(new EngineRecord(id, "COLUMNAR", "metricas_analiticas", sum, det, now));
        }
        list.add(bCol);

        databaseBuckets.put(db, list);
    }

    private void initHospitalDb() {
        List<EngineBucket> list = new ArrayList<>();
        String db = "samples_hostipal_db";
        String now = LocalDateTime.now().format(FMT);

        EngineBucket bPacientes = new EngineBucket("DOCUMENT", "Document Engine (JSON Off-Heap)", "pacientes", "Historias clínicas de pacientes y diagnósticos", 400_000L);
        for (int i = 1; i <= 20; i++) {
            String id = String.format("PAC-%05d", i);
            String sum = String.format("Paciente: %s | Edad: %d | Sala: %s | Prioridad: %s",
                "Paciente_" + (char)('A' + (i % 26)) + i, 20 + (i * 3 % 60), "Piso " + (i % 5 + 1), (i % 3 == 0 ? "ALTA" : "NORMAL"));
            String det = String.format("{\n  \"idPaciente\": \"%s\",\n  \"diagnostico\": \"Afección Cardiovascular Nivel %d\",\n  \"camaAsignada\": \"UCI-%03d\",\n  \"estadoClinico\": \"ESTABLE\",\n  \"medicoTratante\": \"Dr. Gonzalez\"\n}", id, (i % 4 + 1), i);
            bPacientes.addRecord(new EngineRecord(id, "DOCUMENT", "pacientes", sum, det, now));
        }
        list.add(bPacientes);

        EngineBucket bUci = new EngineBucket("KEYVALUE", "KeyValue Engine", "camas_uci", "Caché de ocupación de camas UCI en tiempo real", 200_000L);
        for (int i = 1; i <= 15; i++) {
            String key = String.format("cama:uci:%03d", i);
            String sum = String.format("Cama UCI-%03d -> Ocupada: %b | Paciente: PAC-%05d", i, (i % 2 == 0), i);
            String det = String.format("KeyValueEntry {\n  \"camaId\": \"UCI-%03d\",\n  \"ocupada\": %b,\n  \"oxigenoPct\": 98.5,\n  \"monitoreoCardiaco\": \"ACTIVO\"\n}", i, (i % 2 == 0));
            bUci.addRecord(new EngineRecord(key, "KEYVALUE", "camas_uci", sum, det, now));
        }
        list.add(bUci);

        EngineBucket bVector = new EngineBucket("VECTOR", "Vector Engine", "sintomas_embeddings", "Vectores de similitud de sintomatología clínica", 200_000L);
        for (int i = 1; i <= 15; i++) {
            String id = String.format("VEC-SINT-%04d", i);
            String sum = String.format("Vector Síntoma [%.3f, %.3f, %.3f] -> Correlación Diagnóstica: 97.4%%", (float)Math.sin(i), (float)Math.cos(i), 0.15f * i);
            String det = String.format("SymptomEmbedding {\n  \"id\": \"%s\",\n  \"clusterPatologia\": \"Respiratorio_Tipo_%d\",\n  \"cosineSimilarity\": 0.974\n}", id, (i % 3 + 1));
            bVector.addRecord(new EngineRecord(id, "VECTOR", "sintomas_embeddings", sum, det, now));
        }
        list.add(bVector);

        databaseBuckets.put(db, list);
    }

    private void initAmbientalDb() {
        List<EngineBucket> list = new ArrayList<>();
        String db = "samples_ambiental_db";
        String now = LocalDateTime.now().format(FMT);

        EngineBucket bCo2 = new EngineBucket("TIMESERIES", "TimeSeries Engine", "mediciones_co2", "Mediciones continuas de CO2 en ppm por estación", 800_000L);
        for (int i = 1; i <= 20; i++) {
            String id = String.format("CO2-REC-%05d", i);
            float ppm = 412.5f + (float)(Math.sin(i * 0.5) * 15.0);
            String sum = String.format("Estación Est-%d: %.2f ppm CO2 | Estado: %s", (i % 8 + 1), ppm, (ppm > 425 ? "ALERTA" : "NORMAL"));
            String det = String.format("TimeSeriesData {\n  \"estacionId\": \"EST_%03d\",\n  \"parametro\": \"CO2_PPM\",\n  \"valor\": %.2f,\n  \"unidad\": \"ppm\",\n  \"calibracion\": \"OPTICA_NDIR\"\n}", (i % 8 + 1), ppm);
            bCo2.addRecord(new EngineRecord(id, "TIMESERIES", "mediciones_co2", sum, det, now));
        }
        list.add(bCo2);

        EngineBucket bGeo = new EngineBucket("GEOSPATIAL", "Geospatial Engine", "sensores_gps", "Geolocalización de boyas y estaciones ambientales", 300_000L);
        for (int i = 1; i <= 15; i++) {
            String id = String.format("BOYA-GPS-%04d", i);
            String sum = String.format("Boya Marina #%d | Coord: (%.4f, %.4f) | Océano Pacífico", i, 12.5 + (i * 0.4), -85.2 - (i * 0.5));
            String det = String.format("GeoSensor {\n  \"boyaId\": \"BOYA_%03d\",\n  \"lat\": %.4f,\n  \"lon\": %.4f,\n  \"temperaturaAgua\": 24.5\n}", i, 12.5 + (i * 0.4), -85.2 - (i * 0.5));
            bGeo.addRecord(new EngineRecord(id, "GEOSPATIAL", "sensores_gps", sum, det, now));
        }
        list.add(bGeo);

        databaseBuckets.put(db, list);
    }

    private void initMetadataDb() {
        List<EngineBucket> list = new ArrayList<>();
        String db = "system_metadata_db";
        String now = LocalDateTime.now().format(FMT);

        EngineBucket bSchemas = new EngineBucket("JAVA_RECORD", "Java Record Engine", "schemas", "Esquemas y catálogos de metadatos del clúster", 5_000L);
        String[] schNames = {"FacturaSchemaV2", "PacienteClinicalSchema", "SensorAmbientalSchema", "RaftLogEntryRecord", "PoliceAuditRecord"};
        for (int i = 0; i < schNames.length; i++) {
            String sum = String.format("Esquema Registrado: '%s' | Versión 2.5 | Estado: ACTIVO", schNames[i]);
            String det = String.format("SchemaRecord {\n  \"schemaName\": \"%s\",\n  \"version\": 2.5,\n  \"multimodelBuckets\": 8,\n  \"raftTerm\": 12\n}", schNames[i]);
            bSchemas.addRecord(new EngineRecord("SCH-" + (i + 1), "JAVA_RECORD", "schemas", sum, det, now));
        }
        list.add(bSchemas);

        EngineBucket bPolice = new EngineBucket("DOCUMENT", "Document Engine", "police_audits", "Registros de intervenciones y auditorías de JettraPolice", 20_000L);
        String[] actions = {"PREVENCIÓN OOM HEAP", "AISLAMIENTO NODO OFFLINE", "FLUSH MEMTABLE FORZADO", "INSPECCIÓN PERMISOS USUARIO"};
        for (int i = 0; i < 15; i++) {
            String sum = String.format("Auditoría #%d: %s | Veredicto: SALUDABLE", i + 1, actions[i % actions.length]);
            String det = String.format("AuditEntry {\n  \"auditId\": \"AUD_%04d\",\n  \"agenteK9\": \"k9_alpha\",\n  \"accion\": \"%s\",\n  \"resultado\": \"INTERVENCIÓN EXITOSA\"\n}", i + 1, actions[i % actions.length]);
            bPolice.addRecord(new EngineRecord("AUD-" + (i + 1), "DOCUMENT", "police_audits", sum, det, now));
        }
        list.add(bPolice);

        databaseBuckets.put(db, list);
    }

    public List<EngineBucket> getBucketsForDatabase(String dbId) {
        return databaseBuckets.getOrDefault(dbId, databaseBuckets.get("example_factura_db"));
    }

    public List<String> getSupportedEngines(String dbId) {
        List<EngineBucket> buckets = getBucketsForDatabase(dbId);
        Set<String> engines = new LinkedHashSet<>();
        for (EngineBucket b : buckets) {
            engines.add(b.getEngineType());
        }
        return new ArrayList<>(engines);
    }

    public List<EngineBucket> getBucketsByEngine(String dbId, String engineType) {
        List<EngineBucket> all = getBucketsForDatabase(dbId);
        List<EngineBucket> res = new ArrayList<>();
        for (EngineBucket b : all) {
            if (b.getEngineType().equalsIgnoreCase(engineType)) {
                res.add(b);
            }
        }
        return res;
    }

    public List<EngineRecord> getPaginatedRecords(String dbId, String engineType, String bucketName, int pageIndex, int pageSize) {
        List<EngineBucket> buckets = getBucketsForDatabase(dbId);
        for (EngineBucket b : buckets) {
            if (b.getEngineType().equalsIgnoreCase(engineType) && b.getBucketName().equalsIgnoreCase(bucketName)) {
                List<EngineRecord> list = b.getSampleRecords();
                int start = Math.max(0, pageIndex * pageSize);
                int end = Math.min(list.size(), start + pageSize);
                if (start >= list.size()) {
                    return Collections.emptyList();
                }
                return list.subList(start, end);
            }
        }
        return Collections.emptyList();
    }

    public int getTotalRecordCount(String dbId, String engineType, String bucketName) {
        List<EngineBucket> buckets = getBucketsForDatabase(dbId);
        for (EngineBucket b : buckets) {
            if (b.getEngineType().equalsIgnoreCase(engineType) && b.getBucketName().equalsIgnoreCase(bucketName)) {
                return b.getSampleRecords().size();
            }
        }
        return 0;
    }
}
