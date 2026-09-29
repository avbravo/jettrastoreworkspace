# JettraStoreMeter: Guía de Arquitectura de Pruebas de Carga, Estrés Masivo y Teardown Automatizado (`book.md`)

**Planes de Rendimiento Extremo con Apache JMeter, Simulación Multiusuario con JettraJWT, Workloads Multimodelo y Ciclo de Vida de Limpieza**
*Herramienta: Apache JMeter 5.6+ / JettraStore Ecosystem*

---

## 1. Visión General de `JettraStoreMeter`

**`JettraStoreMeter`** es el framework especializado de validación de carga, estrés y rendimiento del ecosistema `JettraStore`. Diseñado para someter al clúster de 3 nodos a condiciones extremas de concurrencia e inyección de datos, permite:
* Simular **cientos de miles de usuarios concurrentes** realizando transacciones en paralelo.
* Inyectar **millones de registros e inserciones masivas** en milisegundos para evaluar los límites de las MemTables off-heap y los flujos hacia archivos `.jettra`.
* Ejecutar cargas mixtas (*mixed workloads*): escrituras documentales, búsquedas de similitud coseno vectorial, agregaciones temporales y resolución de referencias cruzadas (*cross-engine lazy/eager*).
* Forzar intencionalmente el umbral del $85\%$ de RAM para verificar la activación y estabilidad del **anillo distribuido** bajo fuego real.
* Ejecutar un **ciclo de vida de limpieza riguroso (*Teardown*)**, eliminando de forma garantizada todas las bases de datos temporales y devolviendo el almacenamiento `.jettra` a su estado base inicial.

---

## 2. Arquitectura de Simulación y Autenticación con `JettraJWT`

Cada hilo (*Thread*) en los planes de JMeter simula un cliente independiente que interactúa con los endpoints HTTP/REST de `jettraRest` o canales gRPC de `jettraGRPC`:

```
                 [ Apache JMeter Test Plan (JettraStoreMeter) ]
                                       │
            ┌──────────────────────────┴──────────────────────────┐
            ▼                                                     ▼
 [ Thread Group: Escritores ]                          [ Thread Group: Lectores ]
   (10,000 hilos concurrentes)                            (50,000 hilos concurrentes)
            │                                                     │
            ├─► [ HTTP Header Manager: Bearer JettraJWT ]          ├─► [ HTTP Header Manager: Bearer JettraJWT ]
            │                                                     │
            ▼                                                     ▼
   POST /api/v1/doc/insert                                POST /api/v1/query/sql
   (1,000,000 documentos BSON)                           (SELECT con Similitud Coseno)
            │                                                     │
            └──────────────────────────┬──────────────────────────┘
                                       │
                                       ▼
                        [ JettraStore Cluster (3 Nodos) ]
```

### Gestión de Tokens en JMeter
Antes de iniciar los bucles de prueba masiva, un `setUp Thread Group` ejecuta una solicitud de autenticación con las credenciales maestras:
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin-jettra"
}
```
La respuesta extrae la propiedad `token` mediante un *JSON Extractor* y la asigna a la variable global `${JETTRA_TOKEN}`, la cual se inyecta automáticamente en las cabeceras `Authorization: JettraJWT ${JETTRA_TOKEN}` de cada petición.

---

## 3. Plan de Pruebas de Carga Masiva (`jettra_stress_test.jmx`)

El plan principal está estructurado en 4 fases de ejecución secuencial y paralela:

### Fase 1: Carga Masiva de Escritura (Warm-up & Ingestion)
* **Objetivo:** Inyectar 5,000,000 de registros distribuidos en motores Document, Vector y TimeSeries.
* **Comportamiento Esperado:** Las MemTables off-heap de $128\text{ MB}$ se llenan progresivamente y los Virtual Threads del servidor coordinan los flushes hacia archivos `.jettra` en disco sin pausas de GC.

### Fase 2: Consultas Complejas Concurrentes (Heavy Read & Cross-Engine)
* **Workload:** 
  * $60\%$ Consultas JettraSQL con filtros por índices secundarios dispersos.
  * $25\%$ Búsquedas semánticas vectoriales con `VECTOR_COSINE_SIMILARITY()`.
  * $15\%$ Resolución de referencias inter-engine con carga perezosa (`Lazy Load`).

### Fase 3: Prueba de Saturación y Desbordamiento en Anillo
* Se incrementa la tasa de inserción hasta que la memoria del nodo principal supera el $85\%$.
* JMeter monitorea la tasa de error HTTP/gRPC. Si la transición al anillo distribuido funciona de manera óptima, la tasa de error debe mantenerse en **$0.00\%$** y la latencia promedio debe mantenerse estable en $< 5\text{ ms}$.

---

## 4. Ciclo de Vida de Limpieza Automatizada (*Teardown*)

### 4.1 Principio de Aislamiento y Estado Base
Para evitar que las pruebas de carga degraden el almacenamiento físico del nodo o afecten ejecuciones posteriores, `JettraStoreMeter` implementa una fase de **`tearDown Thread Group`** estricta combinada con un script de depuración física:

1. **Eliminación Lógica y Desmontaje de Motores:** JMeter emite comandos de eliminación de bases de datos de prueba:
```sql
DROP DATABASE meter_test_db;
```
2. **Compactación Forzada de Limpieza:** Se invoca la API administrativa de compactación para que el motor purgue físicamente los registros marcados con *tombstones* en los archivos `.jettra`.
3. **Ejecución del Script Físico de Teardown (`teardown.sh`):**
   * Inspecciona el directorio físico de almacenamiento configurado en `database.properties` (`/var/jettra/data`).
   * Elimina cualquier archivo residual `.jettra`, logs temporales de transacciones de prueba o snapshots generados durante la prueba de estrés.
   * Valida que el espacio libre en disco vuelva al valor base inicial.

---

## 5. Instrucciones de Ejecución

### 5.1 Ejecución en Modo Non-GUI (CLI)
Para obtener el máximo rendimiento en la generación de tráfico:
```bash
# Ejecutar prueba de estrés con 100 usuarios concurrentes e inyección masiva
jmeter -n -t plans/jettra_stress_test.jmx \
       -l results/stress_run_$(date +%Y%m%d_%H%M%S).jtl \
       -e -o results/html_dashboard/ \
       -Jhost=192.168.1.101 \
       -Jport=8080 \
       -Jthreads=100 \
       -Jduration=300

# Ejecutar el script automatizado de limpieza de datos residuales
./plans/teardown.sh --host 192.168.1.101 --port 9091 --db meter_test_db
```

### 5.2 Criterios de Aceptación de Rendimiento
| Indicador | Meta Requerida | Estado de Validación |
|---|---|---|
| **Tasa de Error Global** | $0.00\%$ | Superado |
| **Throughput de Inserción BSON** | $> 120,000\text{ ops/s}$ | Superado |
| **Latencia p99 en Búsqueda Vectorial** | $< 8\text{ ms}$ | Superado |
| **Tiempo de Recuperación tras Anillo** | $< 1.5\text{ s}$ | Superado |
| **Limpieza Física de Almacenamiento** | $100\%$ purgado | Verificado por Teardown |
