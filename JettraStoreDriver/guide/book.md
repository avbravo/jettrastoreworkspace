# JettraStoreDriver: Manual de Referencia y Arquitectura del Conector Java 25+ (`book.md`)

**Conector Cliente de Ultra-Baja Latencia con Virtual Threads, jettra collection, Seguridad JettraJWT, Backup/Restore y Soporte Multimodelo**
*Versión de Plataforma: Java 25 LTS / Jettra Driver 1.0*

---

## 1. Visión General del Conector `JettraStoreDriver`

**`JettraStoreDriver`** es el conector cliente oficial de alta fidelidad desarrollado para interactuar con clústeres distribuidos `JettraStore`. A diferencia de los drivers de bases de datos convencionales basados en pools de hilos pesados del sistema operativo y serialización genérica en el montículo, `JettraStoreDriver` aprovecha al máximo:
* **Java 25 Virtual Threads:** Cada consulta asíncrona o flujo reactivo se ejecuta sobre Virtual Threads ligeros, eliminando la sobrecarga de cambio de contexto de la CPU.
* **Integración con `jettra collection`:** Cero uso de tipos de envoltura (`Long`, `Integer`, `Double`). Los conjuntos de resultados operan sobre arrays primitivos contiguos y buffers directos, eliminando la presión de recolección de basura (*GC pressure*).
* **Transporte Nativo `jettraGRPC`:** Multiplexación de conexiones binarias sobre HTTP/2 y sockets no bloqueantes.
* **Autenticación Obligatoria con `JettraJWT`:** Negociación y renovación transparente de tokens criptográficos.

---

## 2. Configuración y Conexión Segura

### 2.1 Dependencia Maven
```xml
<dependency>
    <groupId>io.jettra</groupId>
    <artifactId>jettrastoredriver</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2.2 Inicialización del Cliente con `JettraClientConfig`
```java
package io.jettra.driver.example;

import io.jettra.driver.JettraClient;
import io.jettra.driver.config.JettraClientConfig;
import java.time.Duration;

public class ConnectionExample {
    public static void main(String[] args) {
        JettraClientConfig config = JettraClientConfig.builder()
            // Clúster de 3 nodos para failover automático
            .addClusterNode("192.168.1.101", 9091)
            .addClusterNode("192.168.1.102", 9091)
            .addClusterNode("192.168.1.103", 9091)
            // Credenciales administrativas por defecto
            .credentials("admin", "admin-jettra")
            .connectionTimeout(Duration.ofMillis(500))
            .enableVirtualThreads(true)
            .build();

        try (JettraClient client = JettraClient.connect(config)) {
            System.out.println("Conectado exitosamente. Token JettraJWT activo.");
        }
    }
}
```

---

## 3. Optimización con `jettra collection` (Zero-Boxing)

Al recuperar conjuntos de datos numéricos o agregaciones analíticas, el driver utiliza las colecciones nativas de alto rendimiento:

```java
import io.jettra.collection.primitive.JettraLongLongHashMap;
import io.jettra.collection.primitive.JettraDoubleArrayList;

// Lectura de millones de métricas sin instanciar objetos Double ni Long
JettraDoubleArrayList prices = client.getDatabase("analytics")
    .getColumnarEngine("market_data")
    .selectDoubleColumn("closing_price");

double sum = 0.0;
for (int i = 0; i < prices.size(); i++) {
    sum += prices.get(i); // Acceso directo por índice primitivo, cero unboxing
}
```

---

## 4. Consultas con JettraSQL y JettraQueryLanguage (LQL)

### 4.1 Ejecución de JettraSQL con Agrupaciones (`GROUP BY`) y Ordenación (`ORDER BY`)
```java
String sql = """
    SELECT department, COUNT(*) AS total_employees, AVG(salary) AS avg_salary
    FROM employees
    WHERE active = true
    GROUP BY department
    HAVING avg_salary > 65000.00
    ORDER BY avg_salary DESC
    """;

JettraResultSet rs = client.sql(sql).execute();
while (rs.next()) {
    System.out.printf("Dept: %s | Total: %d | Avg: %.2f%n",
        rs.getString("department"), rs.getLong("total_employees"), rs.getDouble("avg_salary"));
}
```

### 4.2 Fluent LQL con Índices Secundarios Dispersos
```java
JettraResults<Order> orders = client.from("orders", Order.class)
    .useIndex("idx_customer_sparse") // Fuerza el uso del sparse index en .jettra
    .filter(o -> o.customerId() == 5421L)
    .fetchMode(FetchMode.LAZY)
    .execute();
```

---

## 5. APIs Programáticas de Backup y Restore

El driver expone métodos de primer nivel para automatizar copias de seguridad y recuperaciones desde código Java:

```java
package io.jettra.driver.example;

import io.jettra.driver.JettraClient;
import io.jettra.driver.admin.BackupOptions;
import io.jettra.driver.admin.BackupResult;
import io.jettra.driver.admin.RestoreResult;

public class BackupRestoreExample {
    public static void main(String[] args) {
        try (JettraClient client = JettraClient.connect("localhost", 9091, "admin", "admin-jettra")) {
            
            // 1. Disparar Backup en Caliente (Hot Backup)
            BackupOptions options = BackupOptions.builder()
                .targetDirectory("/backup/daily_snapshots")
                .compress(true)
                .includeSecondaryIndexes(true)
                .build();

            BackupResult backupRes = client.admin().backupDatabase("corporate_db", options);
            System.out.printf("Backup completado en %d ms. Archivo: %s%n", 
                backupRes.durationMs(), backupRes.snapshotPath());

            // 2. Disparar Restauración de Base de Datos
            RestoreResult restoreRes = client.admin().restoreDatabase(
                "corporate_db", 
                backupRes.snapshotPath()
            );
            
            if (restoreRes.isSuccess()) {
                System.out.println("Base de datos restaurada y sincronizada en el clúster.");
            }
        }
    }
}
```

---

## 6. Ejemplos Completos por Motor Soportado

### 6.1 Motor de Documentos
```java
client.getDatabase("store").getDocumentEngine("catalog")
    .insert(new JettraDocument().put("_id", "item_9").put("name", "UltraServer 25"));
```

### 6.2 Motor Vectorial (Embeddings)
```java
client.getDatabase("store").getVectorEngine("products_ai")
    .index("item_9", new float[]{0.15f, -0.82f, 0.44f, ...});
```

### 6.3 Motor de Grafos
```java
client.getDatabase("store").getGraphEngine("relations")
    .createEdge("item_9", "category_server", "BELONGS_TO", Map.of("weight", 1.0));
```

### 6.4 Motor de Series Temporales
```java
client.getDatabase("store").getTimeSeriesEngine("telemetry")
    .record(System.currentTimeMillis(), "cpu_load", 38.4);
```

### 6.5 Motor Key-Value
```java
client.getDatabase("store").getKeyValueEngine("session_cache")
    .put("sess_abc123", "active".getBytes());
```

### 6.6 Motor Geoespacial
```java
client.getDatabase("store").getGeospatialEngine("warehouses")
    .insertPoint("wh_panama", 8.9824, -79.5199);
```

### 6.7 Motor Pure Object / Records
```java
public record DeviceTelemetry(String serial, double battery, long timestamp) {}
client.getDatabase("store").getRecordsEngine("devices", DeviceTelemetry.class)
    .persist(new DeviceTelemetry("SN-882", 98.5, System.currentTimeMillis()));
```

### 6.8 Motor Columnar
```java
client.getDatabase("store").getColumnarEngine("financial_olap")
    .appendRow(Map.of("revenue", 125000.0, "quarter", "Q3"));
```
