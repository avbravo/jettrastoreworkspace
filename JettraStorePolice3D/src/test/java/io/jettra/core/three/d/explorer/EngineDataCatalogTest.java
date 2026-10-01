package io.jettra.core.three.d.explorer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EngineDataCatalogTest {

    @Test
    @DisplayName("Debe listar engines soportados para example_factura_db")
    public void testSupportedEnginesFacturaDb() {
        EngineDataCatalog catalog = new EngineDataCatalog();
        List<String> engines = catalog.getSupportedEngines("example_factura_db");

        assertTrue(engines.contains("DOCUMENT"), "Debe soportar Document Engine");
        assertTrue(engines.contains("GRAPH"), "Debe soportar Graph Engine");
        assertTrue(engines.contains("VECTOR"), "Debe soportar Vector Engine");
        assertTrue(engines.contains("JAVA_RECORD"), "Debe soportar Java Record Engine");
        assertTrue(engines.contains("KEYVALUE"), "Debe soportar KeyValue Engine");
        assertTrue(engines.contains("TIMESERIES"), "Debe soportar TimeSeries Engine");
        assertTrue(engines.contains("GEOSPATIAL"), "Debe soportar Geospatial Engine");
        assertTrue(engines.contains("COLUMNAR"), "Debe soportar Columnar Engine");
    }

    @Test
    @DisplayName("Debe paginar registros correctamente en Document Engine bucket facturas")
    public void testPaginatedRecords() {
        EngineDataCatalog catalog = new EngineDataCatalog();
        List<EngineRecord> page0 = catalog.getPaginatedRecords("example_factura_db", "DOCUMENT", "facturas", 0, 5);
        assertEquals(5, page0.size(), "La página 0 con tamaño 5 debe tener 5 elementos");
        assertEquals("FAC-2026-00001", page0.get(0).getId());

        List<EngineRecord> page1 = catalog.getPaginatedRecords("example_factura_db", "DOCUMENT", "facturas", 1, 5);
        assertEquals(5, page1.size());
        assertEquals("FAC-2026-00006", page1.get(0).getId());
    }

    @Test
    @DisplayName("Debe contener registros de embeddings y grafos multimodelo")
    public void testMultimodelRecords() {
        EngineDataCatalog catalog = new EngineDataCatalog();
        List<EngineRecord> graphRecs = catalog.getPaginatedRecords("example_factura_db", "GRAPH", "red_comercial", 0, 10);
        assertFalse(graphRecs.isEmpty());
        assertTrue(graphRecs.get(0).getDetails().contains("GraphEdge"));

        List<EngineRecord> vecRecs = catalog.getPaginatedRecords("example_factura_db", "VECTOR", "factura_embeddings", 0, 10);
        assertFalse(vecRecs.isEmpty());
        assertTrue(vecRecs.get(0).getDetails().contains("VectorEmbedding"));
    }
}
