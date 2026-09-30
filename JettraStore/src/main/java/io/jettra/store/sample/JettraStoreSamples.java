package io.jettra.store.sample;

import io.jettra.store.core.JettraDatabase;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Gestor Centralizado de Bases de Datos de Muestra y Ejemplos Empresariales para JettraStore.
 * Asegura que las bases de datos de muestra ('sample_*' y 'example_factura_db')
 * contengan sus colecciones y datos multimodelo listos tanto en JettraStore, JettraStoreFX y JettraStoreShell.
 */
public final class JettraStoreSamples {

    public static final String SAMPLE_ENTERPRISE = "sample_enterprise_db";
    public static final String SAMPLE_ECOMMERCE = "sample_ecommerce_db";
    public static final String SAMPLE_AI_GRAPH = "sample_ai_graph_db";
    public static final String SAMPLE_IOT = "sample_iot_telemetry_db";
    public static final String SAMPLE_FINANCIAL = "sample_financial_db";
    public static final String SAMPLE_FACTURA = "example_factura_db";

    public static final Set<String> SAMPLE_DATABASES = Set.of(
        SAMPLE_ENTERPRISE,
        SAMPLE_ECOMMERCE,
        SAMPLE_AI_GRAPH,
        SAMPLE_IOT,
        SAMPLE_FINANCIAL
    );

    private JettraStoreSamples() {}

    public static boolean isSampleDatabase(String name) {
        if (name == null) return false;
        // Solo las 5 bases de muestra estándar se inicializan automáticamente al inicio.
        // 'example_factura_db' es masiva (3M) y se inicializa bajo demanda con INSTALL SAMPLES FACTURA.
        return SAMPLE_DATABASES.contains(name);
    }

    public static int getSampleCollectionCount(String dbName) {
        if (dbName == null) return 0;
        return switch (dbName) {
            case SAMPLE_ENTERPRISE -> 4;
            case SAMPLE_ECOMMERCE -> 3;
            case SAMPLE_AI_GRAPH -> 4;
            case SAMPLE_IOT -> 4;
            case SAMPLE_FINANCIAL -> 3;
            case SAMPLE_FACTURA -> 9;
            default -> 0;
        };
    }

    public static void installSample(String dbName, JettraDatabase db) {
        if (dbName == null || db == null) return;
        switch (dbName) {
            case SAMPLE_ENTERPRISE -> installEnterprise(db);
            case SAMPLE_ECOMMERCE -> installEcommerce(db);
            case SAMPLE_AI_GRAPH -> installAiGraph(db);
            case SAMPLE_IOT -> installIoT(db);
            case SAMPLE_FINANCIAL -> installFinancial(db);
            case SAMPLE_FACTURA -> installFactura(db, false);
            default -> {
                if (dbName.startsWith("sample_")) {
                    installGenericSample(dbName, db);
                }
            }
        }
    }

    public static void installEnterprise(JettraDatabase enterprise) {
        enterprise.getDocumentEngine("departments").insert("dep_rd", Map.of(
            "name", "Research & Advanced Computing", "budget", 15000000.0, "floor", 12
        ));
        enterprise.getDocumentEngine("employees").insert("emp_01", Map.of(
            "name", "Ada Lovelace", "title", "Lead Architect", "salary", 185000.0,
            "_ref_department", "document::departments#dep_rd",
            "_ref_vector", "vector::employee_biometrics#bio_01",
            "_ref_equipment", "kv::inventory_cache#laptop_mac_m3"
        ));
        enterprise.getDocumentEngine("products").insert("prod_01", Map.of(
            "name", "Quantum Neural Accelerator", "category", "Hardware", "price", 4500.0,
            "_ref_vector", "vector::product_embeddings#emb_01",
            "_ref_category", "graph::catalog_graph#cat_hardware"
        ));
        enterprise.getVectorEngine("product_embeddings", 3).index("emb_01", new float[]{0.15f, -0.42f, 0.88f});
        enterprise.getVectorEngine("employee_biometrics", 3).index("bio_01", new float[]{0.92f, 0.11f, -0.05f});
        enterprise.getGraphEngine("catalog_graph").addEdge("prod_01", "cat_hardware", "BELONGS_TO", Map.of("weight", 1.0));
        enterprise.getTimeSeriesEngine("telemetry").record(System.currentTimeMillis(), 42.5);
        enterprise.getKeyValueEngine("inventory_cache").put("laptop_mac_m3", "MacBook Pro M3 Max 64GB".getBytes(StandardCharsets.UTF_8));
        
        try {
            enterprise.getIndexManager().createIndex("employees", "idx_emp_name", "name", "BTREE", false, enterprise.getDocumentEngine("employees"));
            enterprise.getIndexManager().createIndex("products", "idx_prod_cat", "category", "HASH", false, enterprise.getDocumentEngine("products"));
        } catch (Exception ignored) {}
    }

    public static void installEcommerce(JettraDatabase ecommerce) {
        ecommerce.getDocumentEngine("customers").insert("cust_101", Map.of(
            "name", "Elena Rostova", "tier", "VIP_PLATINUM", "country", "ES", "email", "elena@quantum.io"
        ));
        ecommerce.getDocumentEngine("orders").insert("ord_9901", Map.of(
            "customer_id", "cust_101", "total", 899.50, "status", "PAID",
            "_ref_customer", "document::customers#cust_101",
            "_ref_product", "document::products#prod_01"
        ));
        ecommerce.getColumnarEngine("order_analytics").appendRow(Map.of("revenue", 899.50));
        ecommerce.getKeyValueEngine("shopping_carts").put("cart_cust_101", "item_quantum_gpu:2".getBytes(StandardCharsets.UTF_8));
        
        try {
            ecommerce.getIndexManager().createIndex("customers", "idx_cust_tier", "tier", "HASH", false, ecommerce.getDocumentEngine("customers"));
        } catch (Exception ignored) {}
    }

    public static void installAiGraph(JettraDatabase aiGraph) {
        aiGraph.getGraphEngine("knowledge_network").addEdge("DeepLearning", "TransformerModel", "FOUNDATION_OF", Map.of("depth", 4.0));
        aiGraph.getGraphEngine("knowledge_network").addEdge("TransformerModel", "AttentionMechanism", "USES", Map.of("weight", 0.95));
        aiGraph.getVectorEngine("concept_embeddings", 3).index("vec_transformer", new float[]{0.85f, 0.12f, -0.33f});
        aiGraph.getDocumentEngine("prompts_corpus").insert("prompt_01", Map.of(
            "role", "system", "text", "You are an autonomous distributed DB engine expert.",
            "_ref_concept", "graph::knowledge_network#TransformerModel",
            "_ref_embedding", "vector::concept_embeddings#vec_transformer"
        ));
    }

    public static void installIoT(JettraDatabase iot) {
        long now = System.currentTimeMillis();
        iot.getTimeSeriesEngine("sensor_temperature").record(now - 2000, 24.5);
        iot.getTimeSeriesEngine("sensor_temperature").record(now - 1000, 25.1);
        iot.getTimeSeriesEngine("sensor_temperature").record(now, 24.8);
        iot.getTimeSeriesEngine("sensor_vibration").record(now, 0.042);
        iot.getDocumentEngine("smart_devices").insert("iot_gateway_01", Map.of(
            "model", "EdgeGate-X25", "firmware", "v2.5.0-LTS", "status", "ONLINE"
        ));
        iot.getGeospatialEngine("device_locations").insertPoint("iot_gateway_01", 40.4168, -3.7038);
    }

    public static void installFinancial(JettraDatabase finance) {
        finance.getDocumentEngine("transactions").insert("tx_001", Map.of(
            "from_account", "ACC_7712", "to_account", "ACC_9941", "amount", 15000.0, "currency", "USD",
            "_ref_client", "document::customers#cust_101"
        ));
        finance.getTimeSeriesEngine("stock_feed").record(System.currentTimeMillis(), 184.50);
    }

    public static void installFactura(JettraDatabase db, boolean massive) {
        int cliTotal = massive ? 200_000 : 2_000;
        int facTotal = massive ? 1_000_000 : 5_000;
        int detTotal = massive ? 1_000_000 : 5_000;
        int folTotal = massive ? 300_000 : 2_000;
        int vecTotal = massive ? 200_000 : 1_000;
        int edgeTotal = massive ? 100_000 : 1_000;
        int tsTotal = massive ? 50_000 : 500;
        int geoTotal = massive ? 25_000 : 250;
        int colTotal = massive ? 25_000 : 250;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Task 1: Clientes
            executor.submit(() -> {
                var docEngine = db.getDocumentEngine("clientes");
                int chunkSize = Math.min(cliTotal, 5_000);
                for (int base = 0; base < cliTotal; base += chunkSize) {
                    Map<String, Map<String, Object>> batch = io.jettra.collections.map.UnifiedMap.newMap(chunkSize);
                    int end = Math.min(base + chunkSize, cliTotal);
                    for (int i = base; i < end; i++) {
                        String id = "cli_" + i;
                        batch.put(id, Map.of(
                            "_id", id,
                            "razon_social", "Corporación Comercial " + i + " S.A.",
                            "rfc_tax_id", "RFC-PAN-" + (1000000 + i),
                            "ciudad", (i % 2 == 0) ? "Ciudad de Panamá" : "Colón",
                            "limite_credito", 50000.0 + (i % 1000) * 100
                        ));
                    }
                    docEngine.insertBatch(batch);
                }
            });

            // Task 2: Facturas
            executor.submit(() -> {
                var docEngine = db.getDocumentEngine("facturas");
                int chunkSize = Math.min(facTotal, 5_000);
                for (int base = 0; base < facTotal; base += chunkSize) {
                    Map<String, Map<String, Object>> batch = io.jettra.collections.map.UnifiedMap.newMap(chunkSize);
                    int end = Math.min(base + chunkSize, facTotal);
                    for (int i = base; i < end; i++) {
                        String id = "fac_" + i;
                        batch.put(id, Map.of(
                            "_id", id,
                            "fecha", "2026-09-29",
                            "total", 250.0 + (i % 2000),
                            "estado", "TIMBRADA",
                            "_ref_cliente", "document::clientes#cli_" + (i % cliTotal),
                            "_ref_detalle", "document::detalles_factura#det_" + (i % detTotal),
                            "_ref_folio", "kv::cache_folios#fol_" + (i % folTotal),
                            "_ref_vector", "vector::factura_embeddings#emb_" + (i % vecTotal),
                            "_ref_sucursal", "geospatial::sucursales_fiscales#suc_" + (i % geoTotal)
                        ));
                    }
                    docEngine.insertBatch(batch);
                }
            });

            // Task 3: Detalles
            executor.submit(() -> {
                var docEngine = db.getDocumentEngine("detalles_factura");
                int chunkSize = Math.min(detTotal, 5_000);
                for (int base = 0; base < detTotal; base += chunkSize) {
                    Map<String, Map<String, Object>> batch = io.jettra.collections.map.UnifiedMap.newMap(chunkSize);
                    int end = Math.min(base + chunkSize, detTotal);
                    for (int i = base; i < end; i++) {
                        String id = "det_" + i;
                        int qty = (i % 10) + 1;
                        double price = 50.0 + (i % 200);
                        batch.put(id, Map.of(
                            "_id", id,
                            "concepto", "Servicio Cloud / Licencia Empresarial #" + (i % 100),
                            "cantidad", qty,
                            "precio_unitario", price,
                            "subtotal", qty * price,
                            "_ref_factura", "document::facturas#fac_" + (i % facTotal)
                        ));
                    }
                    docEngine.insertBatch(batch);
                }
            });

            // Task 4: KV Cache de Folios
            executor.submit(() -> {
                var kvEngine = db.getKeyValueEngine("cache_folios");
                byte[] rawVal = "TIMBRADO_OK_CFDI_2026_SAT".getBytes(StandardCharsets.UTF_8);
                Map<String, byte[]> batch = new HashMap<>(folTotal);
                for (int i = 0; i < folTotal; i++) {
                    batch.put("fol_" + i, rawVal);
                }
                kvEngine.putBatch(batch);
            });

            // Task 5: Factura Embeddings
            executor.submit(() -> {
                var vecEngine = db.getVectorEngine("factura_embeddings", 3);
                float[] emb = new float[]{0.75f, -0.20f, 0.60f};
                Map<String, float[]> batch = new HashMap<>(vecTotal);
                for (int i = 0; i < vecTotal; i++) {
                    batch.put("emb_" + i, emb);
                }
                vecEngine.indexBatch(batch);
            });

            // Task 6: Red Comercial Graph
            executor.submit(() -> {
                var graphEngine = db.getGraphEngine("red_comercial");
                Map<String, List<io.jettra.store.engine.models.GraphEngine.Edge>> batch = new HashMap<>(edgeTotal);
                for (int i = 0; i < edgeTotal; i++) {
                    String from = "cli_" + i;
                    String to = "fac_" + i;
                    batch.put(from, List.of(new io.jettra.store.engine.models.GraphEngine.Edge(
                        to, "FACTURA_EMITIDA", Map.of("weight", 1.0)
                    )));
                }
                graphEngine.addEdgesBatch(batch);
            });

            // Task 7: TimeSeries Volumen Facturación
            executor.submit(() -> {
                var tsEngine = db.getTimeSeriesEngine("volumen_facturacion");
                long now = System.currentTimeMillis();
                Map<Long, Double> batch = new HashMap<>(tsTotal);
                for (int i = 0; i < tsTotal; i++) {
                    batch.put(now - (i * 1000L), 2500.0 + (i % 500));
                }
                tsEngine.recordBatch(batch);
            });

            // Task 8: Geospatial Sucursales
            executor.submit(() -> {
                var geoEngine = db.getGeospatialEngine("sucursales_fiscales");
                Map<String, io.jettra.store.engine.models.GeospatialEngine.GeoPoint> batch = new HashMap<>(geoTotal);
                for (int i = 0; i < geoTotal; i++) {
                    String id = "suc_" + i;
                    batch.put(id, new io.jettra.store.engine.models.GeospatialEngine.GeoPoint(
                        id, 8.9800 + (i % 100) * 0.001, -79.5200 + (i % 100) * 0.001
                    ));
                }
                geoEngine.insertBatch(batch);
            });

            // Task 9: Columnar Analítica Fiscal
            executor.submit(() -> {
                var colEngine = db.getColumnarEngine("analitica_fiscal");
                List<Double> sub = new ArrayList<>(colTotal);
                List<Double> iva = new ArrayList<>(colTotal);
                List<Double> tot = new ArrayList<>(colTotal);
                for (int i = 0; i < colTotal; i++) {
                    double s = 1000.0 + (i % 500);
                    double iv = s * 0.07;
                    sub.add(s);
                    iva.add(iv);
                    tot.add(s + iv);
                }
                colEngine.appendBatch(
                    Map.of("subtotal", sub, "iva", iva, "total", tot),
                    Map.of(),
                    colTotal
                );
            });
        }

        System.gc();
        try {
            db.getIndexManager().createIndex("facturas", "idx_fac_cliente", "_ref_cliente", "HASH", false, db.getDocumentEngine("facturas"));
            db.getIndexManager().createIndex("clientes", "idx_cli_rfc", "rfc_tax_id", "BTREE", false, db.getDocumentEngine("clientes"));
        } catch (Exception ignored) {}
    }

    private static void installGenericSample(String name, JettraDatabase db) {
        db.getDocumentEngine("items").insert("item_01", Map.of(
            "name", "Generic Sample Item", "database", name, "created_at", System.currentTimeMillis()
        ));
    }
}
