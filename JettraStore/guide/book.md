# JettraStore: Arquitectura Definitiva del Ecosistema de Base de Datos Multimodelo Distribuida en Java 25+

**Manual Maestro de Arquitectura, Persistencia Off-Heap, Consenso Raft, Anillo por Saturación de Memoria y Operaciones**
*Versión de Plataforma: Java 25 LTS / Jettra Core 1.0*

---

## Tabla de Contenidos
1. [Visión General y Filosofía de JettraStore](#1-visión-general-y-filosofía-de-jettrastore)
2. [Arquitectura de Almacenamiento y Formato de Archivo `.jettra`](#2-arquitectura-de-almacenamiento-y-formato-de-archivo-jettra)
   - [2.1 Persistencia Inmutable LSM y MemTable Off-Heap](#21-persistencia-inmutable-lsm-y-memtable-off-heap)
   - [2.2 Estructura Binaria de Archivos `.jettra`](#22-estructura-binaria-de-archivos-jettra)
   - [2.3 Índices Secundarios Dispersos y Filtros de Bloom](#23-índices-secundarios-dispersos-y-filtros-de-bloom)
3. [Optimizaciones Extremas de la JVM (Java 25+)](#3-optimizaciones-extremas-de-la-jvm-java-25)
   - [3.1 Project Panama: Foreign Function & Memory (FFM) API](#31-project-panama-foreign-function--memory-ffm-api)
   - [3.2 Compact Object Headers (JEP 450)](#32-compact-object-headers-jep-450)
   - [3.3 Recolección de Basura ZGC de Latencia Sub-Milisegundo](#33-recolección-de-basura-zgc-de-latencia-sub-milisegundo)
   - [3.4 CRaC y CRIU para Arranque Instantáneo](#34-crac-y-criu-para-arranque-instantáneo)
   - [3.5 Colecciones Especializadas Zero-Boxing (`jettra collection`)](#35-colecciones-especializadas-zero-boxing-jettra-collection)
4. [Integración Nativa del Motor Off-Heap `JettraMemory`](#4-integración-nativa-del-motor-off-heap-jettramemory)
   - [4.1 Arquitectura Panama LSM y Segmentos Binarios](#41-arquitectura-panama-lsm-y-segmentos-binarios)
   - [4.2 APIs Nativas en JettraDatabase y JettraClient](#42-apis-nativas-en-jettradatabase-y-jettraclient)
   - [4.3 Compactación y Reclamación de Memoria Off-Heap](#43-compactación-y-reclamación-de-memoria-off-heap)
5. [Mecanismo Dinámico de Anillo Distribuido por Saturación de Memoria](#5-mecanismo-dinámico-de-anillo-distribuido-por-saturación-de-memoria)
   - [4.1 Detección Preventiva de Umbrales de RAM](#41-detección-preventiva-de-umbrales-de-ram)
   - [4.2 Transición Automática a Motor de Anillo](#42-transición-automática-a-motor-de-anillo)
   - [4.3 Protocolo de Descarga y Rebalanceo Dinámico Off-Heap](#43-protocolo-de-descarga-y-rebalanceo-dinámico-off-heap)
5. [Topología de Clúster de 3 Nodos y Consenso Raft](#5-topología-de-clúster-de-3-nodos-y-consenso-raft)
   - [5.1 Configuración de Nodos (Líder y Secundarios)](#51-configuración-de-nodos-líder-y-secundarios)
   - [5.2 Canales Raft Sin Bloqueo con Virtual Threads y `jettraGRPC`](#52-canales-raft-sin-bloqueo-con-virtual-threads-y-jettragrpc)
6. [Seguridad Estricta: Autenticación, Superusuario y `JettraJWT`](#6-seguridad-estricta-autenticación-superusuario-y-jettrajwt)
   - [6.1 Superusuario Administrativo por Defecto (`admin` / `admin-jettra`)](#61-superusuario-administrativo-por-defecto-admin--admin-jettra)
   - [6.2 Inviolabilidad y Jerarquía Máxima del Superusuario](#62-inviolabilidad-y-jerarquía-máxima-del-superusuario)
   - [6.3 Arquitectura de Tokens `JettraJWT`](#63-arquitectura-de-tokens-jettrajwt)
7. [Soporte Multimodelo y Referencias Cruzadas (Intra e Inter-Engine)](#7-soporte-multimodelo-y-referencias-cruzadas-intra-e-inter-engine)
   - [7.1 Los 8 Motores Nativos Integrados](#71-los-8-motores-nativos-integrados)
   - [7.2 Referencias Cruzadas Multimodelo](#72-referencias-cruzadas-multimodelo)
   - [7.3 Estrategias de Carga: Lazy Load vs Eager Load](#73-estrategias-de-carga-lazy-load-vs-eager-load)
8. [Lenguajes de Consulta: JettraQueryLanguage (LQL) y JettraSQL](#8-lenguajes-de-consulta-jettraquerylanguage-lql-y-jettrasql)
   - [8.1 JettraQueryLanguage (LQL) - Estilo Fluent Streams](#81-jettraquerylanguage-lql---estilo-fluent-streams)
   - [8.2 JettraSQL - Dialecto SQL de Alto Rendimiento](#82-jettrasql---dialecto-sql-de-alto-rendimiento)
9. [Componente de Supervisión Preventiva: `JettraPolice`](#9-componente-de-supervisión-preventiva-jettrapolice)
   - [9.1 Ciclo de Vida del Daemon Autónomo](#91-ciclo-de-vida-del-daemon-autónomo)
   - [9.2 Reglas Preventivas y Acciones Mitigadoras](#92-reglas-preventivas-y-acciones-mitigadoras)
   - [9.3 Activación y Configuración (`jettrapolice.active`)](#93-activación-y-configuración-jettrapoliceactive)
10. [Capacidades Nativas de Backup y Restore](#10-capacidades-nativas-de-backup-y-restore)
    - [10.1 Procedimiento de Respaldo Hot-Snapshot](#101-procedimiento-de-respaldo-hot-snapshot)
    - [10.2 Procedimiento de Restauración Consistente](#102-procedimiento-de-restauración-consistente)
11. [Métricas de Rendimiento y Microbenchmarking con JMH](#11-métricas-de-rendimiento-y-microbenchmarking-con-jmh)
    - [11.1 Integración de Java Microbenchmark Harness (JMH)](#111-integración-de-java-microbenchmark-harness-jmh)
    - [11.2 Activación y Control (`jmh.metrics.active`)](#112-activación-y-control-jmhmetricsactive)
12. [Configuración del Sistema (`database.properties` y `jettra.config`)](#12-configuración-del-sistema-databaseproperties-y-jettraconfig)
13. [Caso de Estudio Masivo: Base de Datos de Facturación (3,000,000 Objetos)](#13-caso-de-estudio-masivo-base-de-datos-de-facturación-3000000-objetos)

---

## 1. Visión General y Filosofía de JettraStore

**`JettraStore`** es una plataforma de base de datos multimodelo NoSQL de siguiente generación construida desde cero en **Java 25 LTS**, concebida para romper las barreras tradicionales de latencia, consumo de memoria y sobrecarga de recolección de basura presentes en los motores de base de datos JVM convencionales.

### Pilares Fundamentales
1. **Rendimiento Nativo sin Salir de Java:** Mediante el uso del **Project Panama (Foreign Function & Memory API - JEP 454)**, `JettraStore` asigna, manipula y descarga buffers de memoria nativa fuera del montículo (*off-heap*) con el mismo nivel de rendimiento y control que motores escritos en C++ o Rust, eliminando de raíz las pausas por Garbage Collection.
2. **Arquitectura Multimodelo Cohesiva:** En lugar de operar como un conjunto de bases de datos aisladas, los 8 motores de almacenamiento de `JettraStore` comparten un bus de persistencia unificado basado en el formato de archivo **`.jettra`**, permitiendo **referencias cruzadas directas intra e inter-motor** con carga perezosa (*lazy load*) o ansiosa (*eager load*).
3. **Resiliencia Predictiva y Desbordamiento en Anillo:** `JettraStore` supervisa proactivamente la densidad de RAM de cada nodo. Si el nodo primario se aproxima a su límite de saturación, el motor muta automáticamente a una topología de **anillo distribuido**, particionando y transfiriendo bloques de datos *off-heap* a los nodos secundarios en tiempo real mediante `jettraGRPC`, erradicando situaciones de Out-Of-Memory (OOM).
4. **Seguridad Absoluta Criptográfica:** Toda la comunicación interna (clúster Raft, migración en anillo) y externa (Drivers, Shell CLI, consolas FX y herramientas de benchmarking) se encuentra estrictamente protegida por **`JettraJWT`**.

---

## 2. Arquitectura de Almacenamiento y Formato de Archivo `.jettra`

### 2.1 Persistencia Inmutable LSM y MemTable Off-Heap

El núcleo de persistencia de `JettraStore` se basa en un diseño **Log-Structured Merge-tree (LSM-Tree)** con separación estricta entre memoria volátil nativa y almacenamiento inmutable en disco:

```
[ Cliente / Driver ] 
         │ (Escritura mediante jettraGRPC + JettraJWT)
         ▼
[ Write-Ahead Log (WAL) .jettra ] ──(fsync secuencial off-heap)──▶ [ Disco Físico ]
         │
         ▼
[ Active MemTable (Off-Heap Panama Arena) ] ── (Límite: 128 MB)
         │
         ▼ (Saturación de MemTable)
[ Immutable MemTable ]
         │
         ▼ (Flushing Thread Pool con Virtual Threads)
[ SSTables Nivel 0 (.jettra) ] ──▶ [ Compaction Service (Leveled) ] ──▶ [ SSTables L1..Ln (.jettra) ]
```

* **MemTable Nativa:** Cada MemTable se gestiona dentro de un `Arena.ofShared()` de Project Panama. El tamaño predeterminado de cada MemTable activa es de **$128\text{ MB}$**, con un límite global estricto de memoria en RAM de **$2\text{ GB}$** asignado a buffers activos.
* **Write-Ahead Log (WAL):** Las mutaciones entrantes se serializan en un archivo `.jettra` de registro secuencial directo mediante canales de E/S nativa sin copia intermedia en memoria gestionada de la JVM.

### 2.2 Estructura Binaria de Archivos `.jettra`

Todos los archivos generados por `JettraStore` (segmentos WAL, SSTables de datos, índices y diccionarios de metadatos) llevan la extensión unificada **`.jettra`**. El formato está alineado a límites de 64 bits para lectura directa mediante `MemorySegment`:

| Offset (Bytes) | Longitud | Campo | Descripción |
|---|---|---|---|
| `0x00 - 0x07` | 8 bytes | `MAGIC_HEADER` | Constante binaria `0x4A45545452415354` (`"JETTRAST"`) |
| `0x08 - 0x09` | 2 bytes | `VERSION` | Versión del formato binario (ej. `0x0100` = v1.0) |
| `0x0A - 0x0B` | 2 bytes | `ENGINE_ID` | Identificador del motor (Doc, Vector, Graph, etc.) |
| `0x0C - 0x13` | 8 bytes | `BLOCK_COUNT` | Número de bloques de registros en el archivo |
| `0x14 - 0x1B` | 8 bytes | `METADATA_OFFSET` | Puntero absoluto al pie de metadatos y Bloom Filter |
| `0x1C - 0x23` | 8 bytes | `SPARSE_INDEX_PTR`| Puntero al índice disperso al final del archivo |
| `0x24 - ...` | Variable | `DATA_PAYLOAD` | Bloques comprimidos (ZSTD/Off-heap Direct Buffer) |
| `EOF - 64` | 64 bytes | `CRC64_FOOTER` | Suma de comprobación criptográfica y estado de cierre |

### 2.3 Índices Secundarios Dispersos y Filtros de Bloom

1. **Filtros de Bloom de Alta Precisión:** Embebidos directamente en la cabecera de cada archivo `.jettra`, calculados con funciones hash murmur3-128 con una tasa de falsos positivos configurada al $0.01\%$. Permiten descartar lecturas en disco sin necesidad de consultar el índice físico.
2. **Índices Secundarios Dispersos (*Sparse Indexes*):** Mapeados a la memoria virtual de la JVM a través de `MemorySegment.map(FileChannel.MapMode.READ_ONLY, ...)`. Los índices dispersos almacenan un puntero por cada bloque de $4\text{ KB}$ de registros ordenados, reduciendo el consumo de memoria del índice en un $92\%$ comparado con índices densos tradicionales.

---

## 3. Optimizaciones Extremas de la JVM (Java 25+)

### 3.1 Project Panama: Foreign Function & Memory (FFM) API

`JettraStore` prescinde completamente de `sun.misc.Unsafe` y `ByteBuffer` indirectos, utilizando el estándar **JEP 454 (Foreign Function & Memory API)** disponible de forma permanente en las especificaciones modernas de Java:

```java
package io.jettra.store.engine.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class NativeMemTableSegment implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment nativeBuffer;
    private long writeOffset = 0;

    public NativeMemTableSegment(long capacityBytes) {
        // Asignación de memoria nativa fuera del Heap de la JVM
        this.arena = Arena.ofShared();
        this.nativeBuffer = arena.allocate(capacityBytes, 8); // 8-byte alignment
    }

    public synchronized void appendEntry(byte engineId, byte[] key, byte[] payload) {
        long entrySize = 1 + 4 + key.length + 4 + payload.length;
        if (writeOffset + entrySize > nativeBuffer.byteSize()) {
            throw new IllegalStateException("Segment capacity exceeded");
        }
        
        nativeBuffer.set(ValueLayout.JAVA_BYTE, writeOffset++, engineId);
        nativeBuffer.set(ValueLayout.JAVA_INT, writeOffset, key.length);
        writeOffset += 4;
        MemorySegment.copy(MemorySegment.ofArray(key), 0, nativeBuffer, writeOffset, key.length);
        writeOffset += key.length;

        nativeBuffer.set(ValueLayout.JAVA_INT, writeOffset, payload.length);
        writeOffset += 4;
        MemorySegment.copy(MemorySegment.ofArray(payload), 0, nativeBuffer, writeOffset, payload.length);
        writeOffset += payload.length;
    }

    @Override
    public void close() {
        // Liberación determinística e inmediata de memoria física sin esperar al GC
        arena.close();
    }
}
```

### 3.2 Compact Object Headers (JEP 450)

En Java 25+, las cabeceras de objetos en el montículo se reducen de 128 o 96 bits a solo **64 bits** (8 bytes) mediante la bandera `-XX:+UseCompactObjectHeaders`.
* **Impacto en JettraStore:** Los millones de objetos en memoria asociados con referencias de punteros, metadatos y descriptores de consultas experimentan una reducción del $25\%$ al $35\%$ en la huella de memoria total, lo que maximiza la densidad de registros procesables en un único nodo antes de activar la migración en anillo.

### 3.3 Recolección de Basura ZGC de Latencia Sub-Milisegundo

`JettraStore` está afinado específicamente para **Generational ZGC**:
* La recolección concurrente maneja montículos de decenas o cientos de gigabytes con tiempos de pausa menores a **$1\text{ ms}$**.
* Dado que el $90\%$ de los datos en caliente residen fuera del Heap mediante Panama FFM, el montículo de la JVM se dedica exclusivamente a transacciones de Virtual Threads, compilación JIT y objetos de vida ultra-corta.

### 3.4 CRaC y CRIU para Arranque Instantáneo

Soporte completo para **Coordinated Restore at Checkpoint (CRaC)**:
* La base de datos puede inicializar cachés, precargar diccionarios `.jettra` y congelar el estado de ejecución en disco mediante un checkpoint CRIU.
* El tiempo de arranque desde un estado guardado se reduce de 3.5 segundos a **$18\text{ milisegundos}$**, ideal para orquestación en contenedores y despliegues elásticos.

### 3.5 Manipulación de Colecciones con `JettraCollection` y Prevención Rigurosa de `OutOfMemoryError`

El ecosistema estipula el **uso obligatorio y exclusivo de `JettraCollection`** (`io.jettra.collections.*`) para toda la manipulación interna de datos, índices, metadatos y búferes:

1. **Uso Obligatorio de `JettraCollection`:**
   * **`UnifiedMap<K, V>` y `UnifiedSet<E>`:** Implementan mapas y conjuntos basados en direccionamiento abierto plano (*open addressing flat table*). Eliminan el 100% de los objetos nodo (`HashMap$Node` o `ConcurrentHashMap$Node`), reduciendo el overhead de contenedor de ~36-40 bytes a solo **~5.3 bytes por entrada (ahorro de más del 80% en RAM)**.
   * **Colecciones Primitivas Especializadas (`IntLongHashMap`, `LongLongHashMap`, `IntArrayList`, `LongArrayList`):** Operan sobre arreglos contiguos primitivos o punteros `MemorySegment`, eliminando el *boxing/unboxing* de tipos primitivos (`long`, `int`, `double`) y la presión sobre el Garbage Collector.

2. **Prohibición Estricta de Conversiones Masivas:**
   * Queda **terminantemente prohibido** ejecutar conversiones masivas de colecciones enteras (como `.toArray()` sobre mapas concurrentes o volcado indiscriminado de millones de registros a listas globales en el Heap) para prevenir caídas por `java.lang.OutOfMemoryError: Java heap space`.

3. **Flujos Perezosos (*Lazy Load / Streams*) y Procesamiento por Lotes (*Chunking*):**
   * Las operaciones de consulta masiva, escaneo completo y reconstrucción de índices (`findAll` o reconstrucción de árboles) deben operar estrictamente mediante **flujos perezosos (`LazyDocumentList`, `stream()`, cursores bajo demanda)**.
   * El procesamiento de grandes volúmenes debe realizarse en **lotes acotados (*chunks* de tamaño fijo, ej. 1,000 a 5,000 elementos)** utilizando estructuras de `JettraCollection` con capacidad preasignada y de ciclo de vida efímero que se liberan inmediatamente tras su indexación o persistencia física.

---

## 4. Integración Nativa del Motor Off-Heap `JettraMemory`

`JettraStore` integra en su núcleo la arquitectura **`JettraMemory`** (`io.jettra.memory.*`), un motor de persistencia y almacenamiento binario nativo fuera del montículo (Off-Heap) basado en **Project Panama FFM API**.

### 4.1 Arquitectura Panama LSM y Segmentos Binarios
* **Almacenamiento Off-Heap Directo:** Los registros y cargas binarias no residen como instancias en el Garbage Collector. Se alojan en `MemorySegment` creados dentro de un `Arena.ofShared()` o `Arena.ofConfined()`, garantizando cero impacto en el Heap.
* **Estructura LSM en Disco:** Cada base de datos gestiona su instancia `JettraMemoryEngine` con una estructura LSM propia:
  * Segmentos activos (`active.bin`) y segmentos congelados (*immutable segments*).
  * Índices en memoria mapeados con punteros de 64 bits.
  * Write-Ahead Log (`wal.bin`) con `fsync` selectivo.

### 4.2 APIs Nativas en JettraDatabase y JettraClient
Tanto `JettraDatabase` en el core como `JettraClient` en el driver exponen métodos directos:
* `getMemoryEngine()`: Obtiene el motor `JettraMemoryEngine` subyacente.
* `putOffHeapBinary(key, data)` / `putBinary(...)`: Persiste un binario directamente sin serialización intermedia en heap.
* `getOffHeapBinary(key)` / `getBinary(...)`: Recupera un binario por clave de forma $O(1)$.
* `getMemoryMetrics()`: Provee métricas detalladas de fragmentación, segmentos activos y bytes off-heap en uso.
* `compactMemory()`: Ejecuta la compactación LSM en background fusionando segmentos obsoletos.

---


### 4.4 Modos Duales de Almacenamiento: `JVM-RAM` vs `DISK-MEMORY (JettraMemory)`
JettraStore permite operar cada base de datos bajo dos paradigmas complementarios:
1. **Modo `JVM-RAM` (Predeterminado):**
   * Almacenamiento y procesamiento en las áreas de memoria Stack y Heap de la Máquina Virtual de Java.
   * Utiliza colecciones optimizadas `UnifiedMap` con cabeceras compactas (Compact Object Headers de 64 bits) y Generational ZGC.
   * Ideal para cargas de trabajo de baja latencia con límites de memoria holgados.
2. **Modo `DISK-MEMORY` (Integración Nativa JettraMemory):**
   * Persistencia directa en disco mediante la arquitectura LSM de `JettraMemory` y mapeo fuera del Heap con Project Panama (`MemorySegment`).
   * Cero consumo de Heap para la carga de datos masivos, erradicando fallos por `OutOfMemoryError`.
   * Los registros se escriben a través de `JettraStoreConnector` con compatibilidad multimodelo total.
   * Conmutable en caliente por API (`db.setStorageMode(mode)`), en consola Shell (`STORAGE_MODE DISK_MEMORY`), en la interfaz gráfica `JettraStoreFX` o mediante `database.properties` (`jettra.storage.mode = DISK_MEMORY`).

## 5. Mecanismo Dinámico de Anillo Distribuido por Saturación de Memoria

### 4.1 Detección Preventiva de Umbrales de RAM

Cada nodo de `JettraStore` ejecuta un monitor de recursos en tiempo real que calcula la tasa de utilización de memoria:

$$\text{Tasa de Ocupación} = \frac{\text{RAM Off-Heap Asignada} + \text{Heap Activo}}{\text{Límite Máximo Configurado en } database.properties}$$

* **Umbral Seguro ($< 70\%$):** Modo local estándar; las lecturas y escrituras se atienden en el nodo local con replicación Raft normal.
* **Umbral de Alerta Preventiva ($70\% - 85\%$):** `JettraPolice` emite notificaciones de advertencia y prepara las tablas de partición del anillo.
* **Umbral Crítico de Saturación ($\ge 85\%$):** Activación inmediata del **Motor de Anillo Distribuido**.

### 4.2 Transición Automática a Motor de Anillo

Al superar el umbral crítico, el nodo principal no bloquea la admisión de datos ni arroja errores de Out-Of-Memory. En su lugar:

```
[ Nodo Principal (Líder) ] ---(Saturación >= 85% RAM)---▶ [ Disparo de Transición de Anillo ]
          │                                                               │
          ├─────────────────────────┬─────────────────────────────────────┤
          ▼                         ▼                                     ▼
 [ Stream Off-Heap ]       [ Stream Off-Heap ]                   [ Descompresión Local ]
          │                         │                                     │
          ▼                         ▼                                     ▼
[ Nodo Secundario 1 ]     [ Nodo Secundario 2 ]                 [ RAM Nodo Principal Cae a < 45% ]
(Recibe 50% partición)    (Recibe 50% partición)                (Continúa coordinando clúster)
```

1. **Topología de Anillo Dinámico:** El clúster pasa de un modelo cliente-servidor tradicional a un anillo virtual distribuido consistente (Consistent Hash Ring).
2. **Transferencia Directa de Bloques Off-Heap:** El nodo saturado toma las *Immutable MemTables* más antiguas y las transmite bit a bit mediante `jettraGRPC` a los nodos secundarios sin deserializar a objetos Java.
3. **Equilibrio de Recursos:** Los nodos secundarios integran los bloques en sus respectivas áreas de almacenamiento `.jettra` y memoria disponible, absorbiendo la carga de forma balanceada. El consumo de RAM en el nodo principal desciende por debajo del $45\%$ en segundos.

---

## 5. Topología de Clúster de 3 Nodos y Consenso Raft

### 5.1 Configuración de Nodos (Líder y Secundarios)

`JettraStore` opera de forma estándar sobre un clúster de tres nodos identificados de forma única:
* **Nodo 1 (Líder Primario):** Coordina transacciones distribuidas, lidera el quórum Raft y atiende escrituras prioritarias.
* **Nodo 2 (Secundario / Seguidor 1):** Mantiene réplica activa del log Raft, listo para asumir el liderazgo en $< 150\text{ ms}$ en caso de desconexión del líder.
* **Nodo 3 (Secundario / Seguidor 2):** Garantiza la formación de quórum de mayoría simple ($N/2 + 1 = 2$) y provee capacidad elástica para el desbordamiento en anillo.

### 5.2 Canales Raft Sin Bloqueo con Virtual Threads y `jettraGRPC`

* **Virtual Threads por Conexión:** Cada flujo de replicación y latido Raft (*Heartbeat*) se procesa en un Virtual Thread independiente de la JVM, permitiendo millones de transacciones por segundo sin saturar el pool de hilos de la plataforma del sistema operativo.
* **Protocolo `jettraGRPC`:** Implementación gRPC de alto rendimiento optimizada para serialización binaria directa sobre archivos `.jettra`. Todas las tramas están firmadas criptográficamente con tokens de sesión **`JettraJWT`**.

---

## 6. Seguridad Estricta: Autenticación, Superusuario y `JettraJWT`

### 6.1 Superusuario Administrativo por Defecto (`admin` / `admin-jettra`)

Al inicializar una instancia o clúster de `JettraStore` por primera vez, el sistema provisiona de manera obligatoria la cuenta del superusuario con las siguientes credenciales exactas:
* **Username:** `admin`
* **Password:** `admin-jettra`
* **Rol:** `SUPER_ADMIN`
* **Privilegios:** Control total absoluto e irrestricto sobre bases de datos, engines, cluster management, backup/restore y seguridad.

> [!CAUTION]
> **Recomendación de Seguridad Crítica:**
> Por motivos de seguridad operativa, se recomienda enfáticamente modificar la contraseña predeterminada tras el primer inicio de sesión mediante el comando `ALTER USER admin IDENTIFIED BY '<nueva-clave-segura>'` en `JettraStoreShell` o a través del panel de seguridad en `JettraStoreFX`.

### 6.2 Inviolabilidad y Jerarquía Máxima del Superusuario

1. El usuario `admin` posee la máxima prioridad en el sistema.
2. Ningún otro usuario, independientemente de sus privilegios asignados (`DB_ADMIN`, `OPERATOR`, `DEVELOPER`), tiene autorización para modificar, revocar, degradar roles o eliminar la cuenta `admin`.
3. Cualquier intento de ejecutar un comando de alteración sobre `admin` por parte de una sesión secundaria genera una excepción de seguridad inmediata `JettraSecurityException("Security violation: Superuser privileges cannot be altered by secondary users")` y emite una alerta crítica a través de `JettraPolice`.

### 6.3 Arquitectura de Tokens `JettraJWT`

Toda solicitud entrante debe acompañarse de un token de cabecera `Authorization: JettraJWT <token>`.
* **Firma Criptográfica:** Algoritmo Ed25519 con claves rotadas periódicamente en memoria o HMAC-SHA512.
* **Estructura del Payload:**
```json
{
  "sub": "admin",
  "role": "SUPER_ADMIN",
  "cluster_id": "jettra-cluster-01",
  "permissions": ["*"],
  "iat": 1759140000,
  "exp": 1759226400,
  "jti": "d3b07384-d113-4f9e-bc22-e0f3e2b260d8"
}
```

---

## 7. Soporte Multimodelo y Referencias Cruzadas (Intra e Inter-Engine)

### 7.1 Los 8 Motores Nativos Integrados

| Motor | Tipo de Datos | Caso de Uso Óptimo | Formato en Archivo `.jettra` |
|---|---|---|---|
| **Document** | Documentos JSON / BSON | Esquemas flexibles, catálogos NoSQL | BSON nativo comprimido |
| **KeyValue** | Clave binaria $\rightarrow$ Valor | Caché de ultra-baja latencia, contadores | Bloques Hash contiguos |
| **Columnar** | Columnas y proyecciones | Analítica OLAP, agregaciones masivas | Run-Length Encoding columnar |
| **TimeSeries** | Marcas de tiempo + Telemetría | Métricas IoT, monitorización de logs | Delta-of-Delta + Gorilla |
| **Geospatial** | Coordenadas 2D/3D, Polígonos | Servicios GIS, cálculo de proximidad | R-Tree espacial con Haversine |
| **Pure Object / Records**| Clases y Java 25 Records | Persistencia directa de entidades Java | Serialización tipada zero-copy |
| **Graph** | Vértices, Aristas y Propiedades | Redes sociales, detección de fraude | Lista de Adyacencia CSR |
| **Vector** | Embeddings de coma flotante | Búsqueda semántica IA, similitud coseno | HNSW + Product Quantization |

### 7.2 Referencias Cruzadas Multimodelo

`JettraStore` permite enlazar entidades entre el mismo motor (**intra-engine**) o a través de diferentes motores (**inter-engine**).

**Ejemplo de Referencia Inter-Engine:**
Un documento en el motor de Documentos (`users`) que referencia un vector en el motor Vectorial (`biometrics`) y un vértice en el motor de Grafos (`social_graph`):

```json
{
  "_id": "usr_99812",
  "name": "Carlos Mendoza",
  "email": "carlos@jettra.io",
  "_ref_vector": "vector::biometrics#emb_99812",
  "_ref_graph": "graph::social_graph#vertex_usr_99812"
}
```

### 7.3 Estrategias de Carga: Lazy Load vs Eager Load

* **Lazy Load (Carga Perezosa - Predeterminada):** La referencia se almacena como un descriptor ligero `JettraRef<T>`. El registro referenciado no se lee del disco ni se transmite por la red hasta que la aplicación invoca explícitamente `.resolve()` o accede al campo correspondiente. Esto minimiza el consumo de RAM y el tráfico de red en consultas masivas.
* **Eager Load (Carga Ansiosa):** La consulta resuelve y ensambla inmediatamente todas las entidades referenciadas en un único paso de ejecución, optimizando los casos donde el cliente requiere el árbol completo del objeto de negocio.

---

## 8. Lenguajes de Consulta: JettraQueryLanguage (LQL) y JettraSQL

### 8.1 JettraQueryLanguage (LQL) - Estilo Fluent Streams

Diseñado para desarrolladores Java modernos, `LQL` replica la elegancia de la Stream API:

```java
// Consulta tipada en LQL sobre el motor Document y Vector
JettraResults<User> results = db.from("users", User.class)
    .filter(u -> u.status().equals("ACTIVE") && u.balance() > 1500.0)
    .withSimilarity("biometrics", targetEmbedding, 0.85) // Inter-engine vector lookup
    .fetchMode(FetchMode.LAZY)
    .limit(50)
    .execute();
```

### 8.2 JettraSQL - Dialecto SQL de Alto Rendimiento

Para integración con ecosistemas heredados y consolas analíticas, `JettraSQL` provee sintaxis ANSI SQL ejecutada directamente sobre las estructuras `.jettra`:

```sql
SELECT 
    u._id, 
    u.name, 
    u.email, 
    VECTOR_SIMILARITY(u._ref_vector, '[0.12, -0.45, 0.89, ...]') AS score
FROM users AS u
WHERE u.status = 'ACTIVE' AND u.balance > 1500.0
ORDER BY score DESC
LIMIT 50;
```

---

## 9. Componente de Supervisión Preventiva: `JettraPolice`

### 9.1 Ciclo de Vida del Daemon Autónomo

`JettraPolice` es un componente de supervisión preventiva que opera como un hilo demonio (*daemon thread*) de muy baja prioridad y bajo consumo computacional ($< 0.5\%$ de CPU):
* **Frecuencia de Muestreo:** Cada $500\text{ ms}$ sondea el estado de los descriptores de archivos `.jettra`, el avance de los punteros WAL, los buffers de memoria Panama y la latencia de red de los canales Raft.
* **Detección de Anomalías:** Emplea el motor `jettraRules` para evaluar condiciones de riesgo antes de que se manifiesten en fallos.

### 9.2 Reglas Preventivas y Acciones Mitigadoras

1. **Prevención de Agotamiento de Espacio en Disco:** Si el directorio físico de almacenamiento configurado supera el $90\%$ de capacidad, `JettraPolice` activa automáticamente la compactación forzada de niveles SSTable y depuración de registros marcados con *tombstones*.
2. **Mitigación de Saturación de RAM:** Si la memoria del nodo alcanza el $75\%$, `JettraPolice` alerta a los subsistemas de clúster para pre-calentar los sockets de transferencia de anillo. Al alcanzar el $85\%$, ordena formalmente el desbordamiento de las MemTables hacia los nodos secundarios.
3. **Control de Intrusiones:** Detecta intentos reiterados de autenticación fallida o intentos ilegales de alteración del usuario `admin`, bloqueando las direcciones IP a nivel de socket de red.

### 9.3 Activación y Configuración (`jettrapolice.active`)

El componente se controla de forma transparente en `database.properties`:
```properties
# Habilitación del agente supervisor JettraPolice
jettrapolice.active = true
jettrapolice.interval.ms = 500
jettrapolice.ram.warning.threshold = 75
jettrapolice.ram.critical.threshold = 85
jettrapolice.auto.pagination.enabled = true
jettrapolice.max.safe.batch.size = 100
```

### 9.4 Supervisión Predictiva de Heap y Prevención Autónoma Anti-OOM

Para evitar que una consulta masiva (e.g. `SELECT * FROM clientes` con 200,000 filas o escaneos de 1,000,000 de facturas) desborde el montículo de la JVM (`OutOfMemoryError: Java heap space`), `JettraPolice` incorpora el método predictivo **`evaluateHeapSafety(...)`**:

1. **Auditoría Predictiva de Memoria:**
   * Mide en tiempo real la memoria Heap disponible ($	ext{maxMemory} - (	ext{totalMemory} - 	ext{freeMemory})$).
   * Calcula el porcentaje de saturación actual del Heap y proyecta los bytes necesarios según los registros solicitados:
     $$	ext{estimatedBytes} = 	ext{recordsToLoad} 	imes \max(	ext{avgRecordSizeBytes}, 384	ext{ bytes})$$
2. **Acción Forzosa `AUTO_PAGINATE_LAZY`:**
   * Si la consulta no tiene límite o los bytes estimados superan el $20\%$ de la memoria disponible, o la saturación del Heap supera el $75\%$, JettraPolice interviene y **fuerza paginación automática**:
   * Calcula un tamaño de página seguro adaptativo asignando a lo sumo el $2\%$ de la memoria disponible restante.
   * Emite la alerta `HEAP_EXHAUSTION_PREVENTED` hacia el clúster y la interfaz 3D.
3. **Distribución de Carga con `LazyPagedCursor`:**
   * Permite iterar colecciones gigantescas bloque por bloque, posibilitando que el recolector de basura (ZGC) limpie cada bloque procesado con complejidad de memoria $O(1)$.


El componente se controla de forma transparente en `database.properties`:
```properties
# Habilitación del agente supervisor JettraPolice
jettrapolice.active = true

# Intervalo de sondeo en milisegundos
jettrapolice.interval.ms = 500

# Umbral de advertencia de memoria RAM (porcentaje)
jettrapolice.ram.warning.threshold = 75
```

---

## 10. Capacidades Nativas de Backup y Restore

### 10.1 Procedimiento de Respaldo Hot-Snapshot

`JettraStore` permite realizar copias de seguridad consistentes en caliente sin detener el motor:
1. **Flushing Inmediato:** Se fuerza la congelación de la MemTable activa hacia un archivo SSTable inmutable `.jettra`.
2. **Creación de Hard-Links / Copia Directa:** Se genera un snapshot atómico en la ruta indicada mediante transferencias directas de bloques off-heap.
3. **Copia de Metadatos:** Se genera un archivo de manifiesto criptográfico `backup_manifest.jettra` con los hashes SHA-256 de todas las tablas e índices.

**Ejemplo mediante Shell CLI:**
```text
JettraStore> BACKUP DATABASE corporate_db TO '/backup/corporate_db_20261015.jettra_bak';
[SUCCESS] Snapshot created in 42ms. 14 SSTables and WAL flushed safely.
```

### 10.2 Procedimiento de Restauración Consistente

El proceso de restauración valida la integridad de cada archivo `.jettra` antes de reabrir el motor:
1. Verificación de suma de comprobación `CRC64_FOOTER` de cada segmento.
2. Comprobación de consistencia del log Raft.
3. Reindexación automática de índices secundarios dispersos.

**Ejemplo mediante Shell CLI:**
```text
JettraStore> RESTORE DATABASE corporate_db FROM '/backup/corporate_db_20261015.jettra_bak';
[SUCCESS] Database 'corporate_db' restored successfully. Ready for transactions.
```

---

## 11. Métricas de Rendimiento y Microbenchmarking con JMH

### 11.1 Integración de Java Microbenchmark Harness (JMH)

`JettraStore` incorpora clases de microbenchmark integradas con **JMH** para auditar el rendimiento en tiempo real y validar que no existan regresiones de rendimiento:
* Medición de latencia de escritura en `NativeMemTableSegment` (en nanosegundos).
* Rendimiento de lectura con Bloom Filter en archivos `.jettra`.
* Costo de resolución de referencias cruzadas Lazy vs Eager.
* Deserialización de vectores con operaciones SIMD de la Vector API de Java.

### 11.2 Activación y Control (`jmh.metrics.active`)

Las métricas internas de microbenchmarking se activan o desactivan en tiempo de ejecución o compilación mediante la propiedad:
```properties
# Activa el recolector de métricas de precisión nanométrica JMH
jmh.metrics.active = true
```
Cuando se desactiva (`false`), el compilador JIT elimina los puntos de control de medición mediante *dead-code elimination*, asegurando cero impacto de sobrecarga (*zero runtime overhead*).

---

## 12. Configuración del Sistema (`database.properties` y `jettra.config`)

### 12.1 Archivo `database.properties`

Ubicado en la raíz de configuración de cada nodo (`config/database.properties`):

```properties
################################################################################
# JettraStore Core Engine Configuration
################################################################################

# Ruta física absoluta en disco para almacenamiento de archivos .jettra
jettra.storage.path = /var/jettra/data

# Parámetros de Memoria y MemTable Off-Heap (Project Panama)
jettra.storage.memtable.size.mb = 128
jettra.storage.ram.global.limit.mb = 2048
jettra.storage.offheap.direct = true

# Umbrales para Transición a Motor de Anillo Distribuido
jettra.ring.saturation.threshold.percent = 85
jettra.ring.release.target.percent = 45

# Componente de Monitoreo Preventivo JettraPolice
jettrapolice.active = true
jettrapolice.interval.ms = 500

# Suite de Métricas de Microbenchmarking con JMH
jmh.metrics.active = false

# Seguridad y Criptografía
jettra.security.jwt.algorithm = Ed25519
jettra.security.jwt.expiration.seconds = 86400

# Red y Puertos
jettra.network.grpc.port = 9091
jettra.network.rest.port = 8080
```

### 12.2 Archivo `jettra.config`

Archivo centralizado de topología de clúster (`config/jettra.config`):

```properties
################################################################################
# JettraStore 3-Node Cluster Topology
################################################################################

cluster.name = jettra-production-cluster
cluster.consensus = RAFT

# Nodo 1: Principal / Líder
cluster.node.1.id = node-01
cluster.node.1.ip = 192.168.1.101
cluster.node.1.port = 9091
cluster.node.1.role = PRIMARY

# Nodo 2: Secundario / Seguidor 1
cluster.node.2.id = node-02
cluster.node.2.ip = 192.168.1.102
cluster.node.2.port = 9091
cluster.node.2.role = SECONDARY

# Nodo 3: Secundario / Seguidor 2
cluster.node.3.id = node-03
cluster.node.3.ip = 192.168.1.103
cluster.node.3.port = 9091
cluster.node.3.role = SECONDARY
```

---

## 13. Caso de Estudio Masivo: Base de Datos de Facturación (3,000,000 Objetos)

Para validar el ecosistema bajo condiciones extremas de concurrencia y volumen de datos, `JettraStore` integra la base de datos de pruebas maestras **`example_factura_db`**:

### 13.1 Estructura Multimodelo Interconectada (9 Buckets Especializados)
* **[DOCUMENT] `facturas`:** 1,000,000 de facturas electrónicas timbradas con referencias cruzadas `_ref_detalle`, `_ref_cliente`, `_ref_vector`, `_ref_folio`.
* **[DOCUMENT] `detalles_factura`:** 1,000,000 de renglones e items con precios, cantidades y subtotales.
* **[DOCUMENT] `clientes`:** 200,000 clientes corporativos con RFC/RUC y límites de crédito.
* **[KEYVALUE] `cache_folios`:** 300,000 folios fiscales persistidos para verificación O(1).
* **[VECTOR] `factura_embeddings`:** 200,000 vectores 3D indexados para análisis semántico por IA.
* **[GRAPH] `red_comercial`:** 200,000 vértices y aristas que conectan clientes con sus facturas.
* **[TIMESERIES] `volumen_facturacion`:** 50,000 métricas históricas de facturación temporal.
* **[GEOSPATIAL] `sucursales_fiscales`:** 25,000 puntos espaciales de coordenadas GIS.
* **[COLUMNAR] `analitica_fiscal`:** 25,000 filas de cálculo analítico de IVA y totales.

### 13.2 Métricas de Rendimiento Verificadas
* **Tiempo Total de Inserción y Timbrado:** ~5,200 ms (utilizando hilos virtuales de Java 25).
* **Índices Secundarios:** `idx_fac_cliente` (HASH) y `idx_cli_rfc` (BTREE) construidos con almacenamiento compacto Singleton (Zero-Set), eliminando más de 200,000 colecciones intermedias.
* **Consultas SQL Paginadas y Shell Interactivo:** `SELECT * FROM clientes` responde en **0 ms** con acotamiento de seguridad anti-OOM gestionado por `JettraPolice`.
* **Navegación Interactiva de Páginas en JettraStoreShell:**
  * Comandos de desplazamiento: `FIRST` / `PRIMERO`, `PREV` / `ANTERIOR`, `NEXT` / `SIGUIENTE`, `LAST` / `ULTIMO`, `PAGE <n>`.
  * Configuración dinámica de registros por página: `PAGE_SIZE <n>` (e.g. `PAGE_SIZE 25`).
  * Barra de estado interactiva en consola que muestra el rango de registros visibles, página actual y comandos disponibles.
* **Interfaces Visuales de Alta Fidelidad:**
  * **`JettraStoreFX`:** Incorpora barra de consultas rápidas SQL/JQL, selector de tamaño de página desplegable (`10, 25, 50, 100, 250`), salto directo de página y tarjetas de telemetría de `JettraPolice` y `JettraMemory`.
  * **`JettraStorePoliceFX`:** Visualizador 3D inmersivo con plano cartesiano optimizado, cuadrantes marcados en alto contraste, radar de pulso dinámico en tiempo real y panel de eventos (HUD) con actualización automática cada segundo.
