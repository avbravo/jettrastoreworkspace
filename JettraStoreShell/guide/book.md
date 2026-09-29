# JettraStoreShell: Manual Integral de la Consola Interactiva y Guía Operativa

**Ecosistema de Base de Datos Multimodelo Distribuido en Java 25+**  
*Documentación Oficial y Manual de Referencia de Comandos CLI*

---

## 📑 Tabla de Contenidos
1. [Arquitectura y Visión General](#1-arquitectura-y-visión-general)
2. [Ciclo de Inicio, Conexión y Autenticación](#2-ciclo-de-inicio-conexión-y-autenticación)
3. [Gestor de Perfiles de Conexión](#3-gestor-de-perfiles-de-conexión)
4. [Telemetría de Recursos y Monitoreo del Clúster](#4-telemetría-de-recursos-y-monitoreo-del-clúster)
5. [Carga Perezosa de Referencias (Lazy Reference)](#5-carga-perezosa-de-referencias-lazy-reference)
6. [Lenguajes de Consulta: JettraQL y JettraSQL](#6-lenguajes-de-consulta-jettraql-y-jettrasql)
7. [Administración de Bases de Datos](#7-administración-de-bases-de-datos)
8. [Administración de Colecciones](#8-administración-de-colecciones)
9. [Operaciones CRUD sobre Registros](#9-operaciones-crud-sobre-registros)
10. [Motores Multimodelo Especializados](#10-motores-multimodelo-especializados)
11. [Bases de Datos de Ejemplo (INSTALL SAMPLES)](#11-bases-de-datos-de-ejemplo-install-samples)
12. [Respaldos en Caliente y Restauración (Backup & Restore)](#12-respaldos-en-caliente-y-restauración-backup--restore)
13. [Tutorial Práctico Extremo a Extremo (Paso a Paso)](#13-tutorial-práctico-extremo-a-extremo-paso-a-paso)
14. [Tabla Rápida de Comandos y Ayuda (`help`)](#14-tabla-rápida-de-comandos-y-ayuda-help)

---

## 1. Arquitectura y Visión General

`JettraStoreShell` es la consola interactiva oficial de línea de comandos (CLI) para gestionar, monitorear y consultar clústeres `JettraStore`. Está diseñada para aprovechar las características de **Java 25 LTS**:

- **Project Panama FFM (Foreign Function & Memory API)**: Almacenamiento directo off-heap de velocidad C/C++ sin presión sobre el recolector de basura.
- **Java 25 Generational ZGC & Compact Object Headers**: Pausas de recolección de memoria garantizadas menores a 1 ms y ahorro del 22% en encabezados de objetos.
- **Java Virtual Threads (Loom)**: Capacidad de ejecutar miles de comandos y consultas concurrentes de forma ligera.
- **Seguridad Criptográfica JettraJWT**: Control de acceso granular con validación criptográfica y protección inmutable del superusuario `admin`.
- **Soporte Bilingüe de Consultas**: Ejecución simultánea de **JettraQL** (lenguaje declarativo orientado a grafos y documentos) y **JettraSQL** (lenguaje relacional ANSI SQL).

---

## 2. Ciclo de Inicio, Conexión y Autenticación

Al ingresar al shell interactivo, el sistema solicita de forma guiada el host/puerto del clúster y las credenciales de acceso:

```text
================================================================================
                         JETTRASTORE INTERACTIVE SHELL                         
================================================================================
Iniciando sesión interactiva de JettraStore CLI (Java 25 LTS)...

Servidor JettraStore Host [127.0.0.1]: 127.0.0.1
Puerto del Servidor [9091]: 9091
Usuario [admin]: admin
Contraseña [admin-jettra]: ************

[CONNECTED] Conectado exitosamente al servidor JettraStore en 127.0.0.1:9091 (Cluster Raft 3 Nodos)
[AUTH SUCCESS] Autenticado exitosamente como 'admin' (SUPER_ADMIN INMUTABLE). Token JettraJWT emitido y activo.
Escriba 'help' o '?' para ver la lista completa de comandos, o 'menu' para el menú interactivo.

admin@default_db> 
```

### Comandos de Sesión:

#### `connect <url> <port>` o `connect <nombre-perfil>`
Conecta la sesión a otro endpoint o conmuta hacia un perfil guardado.
```bash
connect 192.168.1.150 9091
connect local-cluster
```

#### `login <username> <password>`
Autentica al operador en el clúster generando un nuevo token **JettraJWT**.
```bash
login admin admin-jettra
login operador clave_segura_2026
```

#### `logout`
Cierra la sesión activa revocando las credenciales y cerrando el socket del cliente.
```bash
logout
# Salida: [LOGOUT] Sesión cerrada para el usuario 'admin'. Puede conectarse o autenticarse nuevamente con 'login <username> <password>'.
```

> [!NOTE]
> Si la sesión está cerrada (`logout`), todos los comandos de base de datos y telemetría son bloqueados preventivamente con el mensaje `[AUTH REQUIRED]`, permitiendo únicamente operaciones de conexión, perfiles y ayuda.

---

## 3. Gestor de Perfiles de Conexión

El shell permite almacenar y gestionar perfiles de conexión para simplificar la conmutación entre entornos locales, réplicas y clústeres de producción.

### Comandos Disponibles:

#### `save connection <nombre-conexion>`
Guarda los parámetros del host, puerto y usuario actualmente activos bajo el alias indicado.
```bash
admin@default_db> save connection prod-cluster-raft
# Salida: [SUCCESS] Conexión 'prod-cluster-raft' guardada exitosamente (127.0.0.1:9091, usuario: admin).
```

#### `remove connection <nombre-conexion>`
Elimina el perfil de conexión guardado.
```bash
admin@default_db> remove connection prod-cluster-raft
# Salida: [SUCCESS] Conexión guardada 'prod-cluster-raft' eliminada exitosamente.
```

#### `list connections` / `list conections`
Muestra la lista de todos los perfiles de conexión en formato tabular.
```bash
admin@default_db> list connections
+-----------------------+--------------------+--------+-----------------+
| Perfil de Conexión    | Host               | Puerto | Usuario         |
+-----------------------+--------------------+--------+-----------------+
| local-cluster         | 127.0.0.1          | 9091   | admin           |
| node-02-replica       | 127.0.0.1          | 9092   | admin           |
| node-03-replica       | 127.0.0.1          | 9093   | admin           |
+-----------------------+--------------------+--------+-----------------+
Total: 3 conexión(es) guardada(s). Endpoint activo actual: 127.0.0.1:9091
```

---

## 4. Telemetría de Recursos y Monitoreo del Clúster

### Comando `status`
Despliega el diagnóstico exhaustivo de consumo de recursos del motor:
- **RAM**: Consumo del segmento de memoria nativa Panama FFM Off-Heap, estado del Dynamic Ring Engine (umbral crítico 85%), heap del JVM ZGC y confirmación de Compact Object Headers.
- **PROCESADOR (CPU)**: Número de cores lógicos del host, tasa estimada de uso del procesador y conteo de Virtual Threads (Loom workers).
- **DISCO**: Espacio en partición, estrategia de flush de MemTable y almacenamiento en formato binario LSM `.jettra`.

```bash
admin@default_db> status
========================= JETTRASTORE CONSUMO DE RECURSOS (STATUS) =========================
  RAM (MEMORIA):
    - Panama FFM Off-Heap Asignado:   512 MB (Project Panama MemorySegment nativo)
    - Panama FFM Off-Heap Utilizado:  217.6 MB (42.5% saturación - Rango Seguro)
    - Anillo por Saturación RAM:      UMBRAL 85% (Estado: LOCAL / Desborde Inactivo)
    - JVM Heap Utilizado (ZGC):       65 MB de 496 MB (Máximo: 7824 MB)
    - Pausas de Recolección ZGC:      < 1 ms garantizadas (Zero GC Latency)
    - Compact Object Headers:         HABILITADO (Ahorro del 22% en encabezados de memoria)

  PROCESADOR (CPU):
    - Cores / Hilos Disponibles:      20 Cores lógicos
    - Uso Estimado de CPU JVM:        8.4% (Bajo consumo en reposo)
    - Arquitectura de Concurrencia:   Java 25 Virtual Threads (Loom Worker Pool activo)
    - Hilos Virtuales en Ejecución:   128 workers procesando transacciones concurrentes

  DISCO (ALMACENAMIENTO):
    - Motor de Almacenamiento:        LSM SSTables en formato binario nativo '.jettra'
    - MemTable Flush Strategy:        Direct I/O sincrónico en background
    - Espacio en Disco Partición:     115 GB Usados / 352 GB Libres (Total: 467 GB)
    - Estado de Persistencia:         CONSISTENTE (ACID Wal & Snapshot activos)
============================================================================================
```

### Comando `show nodes` (o `show cluster`)
Muestra la topología en tiempo real del clúster Raft distribuido de 3 nodos:

```bash
admin@default_db> show nodes
======================= TOPOLOGÍA DEL CLÚSTER JETTRASTORE (SHOW NODES) =======================
  Nodo       Endpoint Host:Port   Rol Raft       Estado    Latencia   Sincronización   Quórum
  ---------------------------------------------------------------------------------------
  node-01    127.0.0.1:9091       LEADER         ONLINE    < 0.2 ms   100%             ACTIVO
  node-02    127.0.0.1:9092       FOLLOWER       ONLINE      0.8 ms   100%             ACTIVO
  node-03    127.0.0.1:9093       FOLLOWER       ONLINE      1.1 ms   100%             ACTIVO
  ---------------------------------------------------------------------------------------
  Quórum Total: 3 de 3 nodos alcanzado | Algoritmo: Raft Distribuido | Heartbeats: cada 150ms
  Tolerancia a Fallos: 1 nodo con recuperación automática sin pérdida de datos.
==============================================================================================
```

---

## 5. Carga Perezosa de Referencias (Lazy Reference)

En `JettraStore`, los documentos pueden enlazar datos en otros motores multimodelo mediante punteros `_ref_*` (como `vector::product_embeddings#emb_01` o `graph::catalog#node_01`). El shell permite configurar el comportamiento de resolución de dichas referencias:

- **`lazy reference on`**: Activa la carga diferida (*Proxy on-demand*). La referencia solo se expande cuando el cliente o visor la solicita explícitamente.
- **`lazy reference off`**: Activa la carga anticipada (*Eager Loading*), resolviendo inmediatamente en memoria todos los punteros enlazados.
- **`lazy reference status`**: Consulta el modo de resolución activo.

```bash
admin@default_db> lazy reference on
[CONFIG] Lazy Reference ACTIVADO (ON). Las referencias JettraRef se resolverán bajo demanda (Proxy).

admin@default_db> lazy reference off
[CONFIG] Lazy Reference DESACTIVADO (OFF). Las referencias JettraRef se cargarán inmediatamente en memoria (Eager).
```

---

## 6. Lenguajes de Consulta: JettraQL y JettraSQL

### 6.1 JettraQL (Expresivo y Multimodelo)
`JettraQL` permite consultar colecciones documentales, recorrer grafos y buscar similitud vectorial de forma directa:

```bash
# 1. Consulta Documental declarativa
FROM products WHERE category = Hardware

# 2. Recorrido de Grafos
MATCH (prod_01)-[BELONGS_TO]->(target) IN catalog_graph

# 3. Búsqueda de Similitud Vectorial
VECTOR SIMILARITY product_embeddings TO [0.15, -0.42, 0.88] LIMIT 3

# 4. Recuperación con Resolución Forzada de Enlaces
FETCH products prod_01 RESOLVE REFS
```

Ejemplo de salida en terminal:
```text
=== JETTRAQL [FROM] ===
Resumen: JettraQL FROM 'products' retornó 1 registro(s)
Columnas: [_id, document]
  [01] [prod_01, {name=Quantum Neural Accelerator, _ref_vector=vector::product_embeddings#emb_01, _id=prod_01, category=Hardware, price=4500.0}]
```

### 6.2 JettraSQL (Relacional ANSI)
El procesador SQL soporta sentencias tradicionales:
```sql
SELECT * FROM products;
INSERT INTO products (_id, name, price) VALUES ('p1', 'GPU', 1200);
UPDATE products SET price = 1100 WHERE _id = 'p1';
DELETE FROM products WHERE _id = 'p1';
```

---

## 7. Administración de Bases de Datos

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `SHOW DATABASES` / `SHOW DBS` | Lista todas las bases de datos registradas e indica la activa. | `SHOW DBS` |
| `CREATE DATABASE <nombre>` | Crea una base de datos y la selecciona inmediatamente. | `CREATE DATABASE core_banking` |
| `DROP DATABASE <nombre>` | Elimina la base de datos especificada y retorna a `default_db`. | `DROP DATABASE core_banking` |
| `USE <nombre>` | Conmuta la sesión hacia la base de datos especificada. | `USE sample_enterprise_db` |
| `DB STATS` / `STATS` | Muestra resumen de motores, colecciones y asignación de memoria. | `DB STATS` |

---

## 8. Administración de Colecciones

| Comando | Descripción | Ejemplo |
| :--- | :--- | :--- |
| `SHOW COLLECTIONS` / `SHOW TABLES` | Lista colecciones con su motor multimodelo y conteo de registros. | `SHOW COLLECTIONS` |
| `CREATE COLLECTION <col> [TYPE <tipo>]` | Crea colección de tipo `DOCUMENT`, `VECTOR`, `GRAPH`, `TIMESERIES`, `KEYVALUE`. | `CREATE COLLECTION users TYPE DOCUMENT` |
| `DROP COLLECTION <col>` | Elimina la colección y sus datos. | `DROP COLLECTION users` |
| `COUNT <col>` | Retorna la cantidad de registros de la colección. | `COUNT users` |
| `TRUNCATE <col>` | Elimina todos los registros manteniendo la colección intacta. | `TRUNCATE users` |

---

## 9. Operaciones CRUD sobre Registros

### Inserción de Documentos:
```bash
INSERT INTO users ID usr_01 JSON {"nombre": "Laura Vega", "cargo": "Directora de TI", "salario": 98000.0}
```

### Consulta por ID (`GET` / `FIND ONE`):
```bash
GET users usr_01
```
Salida en terminal:
```text
--- REGISTRO [usr_01] EN 'users' ---
  nombre          : Laura Vega
  cargo           : Directora de TI
  salario         : 98000.0
  _id             : usr_01
```

### Listado Paginado (`FIND ALL` / `SCAN`):
```bash
FIND ALL users LIMIT 25
```

### Actualización (`UPDATE`):
```bash
UPDATE users usr_01 SET salario=105000.0, departamento=Ingenieria
```

### Eliminación (`DELETE`):
```bash
DELETE users usr_01
# o también:
DELETE FROM users WHERE ID = usr_01
```

---

## 10. Motores Multimodelo Especializados

### 10.1 Motor Vectorial (`VectorEngine`)
- **Indexación de Vector**:
  ```bash
  VECTOR INDEX product_embeddings vec_alpha [0.25, -0.68, 0.44]
  ```
- **Búsqueda por Similaridad Coseno**:
  ```bash
  VECTOR SEARCH product_embeddings [0.25, -0.68, 0.44] K 3
  ```

### 10.2 Motor de Grafos (`GraphEngine`)
- **Agregar Vértice**:
  ```bash
  GRAPH ADD VERTEX network_graph server_alpha
  ```
- **Agregar Arista Ponderada**:
  ```bash
  GRAPH ADD EDGE network_graph server_alpha -> server_beta LABEL REPLICATES WEIGHT 0.8
  ```
- **Consultar Aristas Salientes**:
  ```bash
  GRAPH GET EDGES network_graph server_alpha
  ```

### 10.3 Motor de Series Temporales (`TimeSeriesEngine`)
- **Insertar Muestra Temporal**:
  ```bash
  TS RECORD cpu_telemetry 78.4
  TS RECORD cpu_telemetry 82.1 TIME 1727600000000
  ```
- **Consultar Rango y Promedio**:
  ```bash
  TS RANGE cpu_telemetry 1727500000000 1727700000000
  ```

### 10.4 Motor Clave-Valor (`KeyValueEngine`)
- **Almacenar y Recuperar Clave**:
  ```bash
  KV PUT app_cache cluster_mode HIGH_PERFORMANCE
  KV GET app_cache cluster_mode
  ```

---

## 11. Bases de Datos de Ejemplo (INSTALL SAMPLES)

Al ejecutar el comando `INSTALL SAMPLES`, se configuran automáticamente **las 5 bases de datos maestras de demostración**:

1. **`sample_enterprise_db`**:
   - `products`: Documentos empresariales con referencias cruzadas `_ref_vector`.
   - `product_embeddings`: Vectores 3D indexados para IA.
   - `catalog_graph`: Grafo de taxonomía y categorías de productos.
   - `telemetry`: Registros temporales de rendimiento de hardware.
   - `app_cache`: Almacén clave-valor con flags de configuración.
2. **`sample_ecommerce_db`**:
   - `customers` y `orders`: Documentos de clientes y pedidos.
   - `order_analytics`: Analítica columnar de ingresos.
   - `shopping_carts`: Carritos de compra en tiempo real vía Clave-Valor.
3. **`sample_ai_graph_db`**:
   - `knowledge_network`: Red de conceptos de Deep Learning y Transformers.
   - `concept_embeddings`: Vectores semánticos densos para RAG.
   - `prompts_corpus`: Colección de prompts del sistema.
4. **`sample_iot_telemetry_db`**:
   - `sensor_temperature` y `sensor_vibration`: Sensores industriales de precisión.
   - `smart_devices`: Dispositivos IoT de borde.
   - `device_locations`: Capa geoespacial Haversine.
5. **`sample_financial_db`**:
   - `transactions`: Transacciones monetarias con firma y balance.
   - `stock_feed`: Cotizaciones de bolsa de alta frecuencia.

---

## 12. Respaldos en Caliente y Restauración (Backup & Restore)

### Respaldo Instantáneo (.jettra snapshot):
```bash
BACKUP DATABASE sample_enterprise_db TO './data/backups/enterprise_snapshot.jettra_bak'
```

### Restauración con Validación de Quórum:
```bash
RESTORE DATABASE sample_enterprise_db FROM './data/backups/enterprise_snapshot.jettra_bak'
```

---

## 13. Tutorial Práctico Extremo a Extremo (Paso a Paso)

Sigue estos pasos en tu terminal para probar la suite completa:

```bash
# 1. Iniciar la consola interactiva
mvn exec:java -Dexec.mainClass="io.jettra.shell.JettraStoreShellApp"

# 2. Instalar todas las bases de datos de prueba
admin@default_db> INSTALL SAMPLES

# 3. Guardar la conexión de desarrollo
admin@sample_enterprise_db> save connection local-dev

# 4. Listar conexiones
admin@sample_enterprise_db> list connections

# 5. Consultar los recursos del sistema
admin@sample_enterprise_db> status

# 6. Inspeccionar el estado de los nodos del clúster Raft
admin@sample_enterprise_db> show nodes

# 7. Ejecutar consulta JettraQL documental
admin@sample_enterprise_db> FROM products

# 8. Obtener un producto con resolución de vector
admin@sample_enterprise_db> GET products prod_01

# 9. Conmutar el modo Lazy Reference
admin@sample_enterprise_db> lazy reference off
admin@sample_enterprise_db> GET products prod_01

# 10. Cerrar sesión
admin@sample_enterprise_db> logout

# 11. Intentar consultar sin sesión (comportamiento protegido)
unauthenticated@default_db> SHOW DATABASES
# [AUTH REQUIRED] Debe iniciar sesión con 'login <username> <password>'

# 12. Re-autenticar con superusuario
unauthenticated@default_db> login admin admin-jettra

# 13. Salir del shell
admin@default_db> exit
```

---

## 14. Tabla Rápida de Comandos y Ayuda (`help`)

```text
================================ JETTRASTORE SHELL HELP ================================
🔌 CONEXIÓN, AUTENTICACIÓN Y SESIÓN:
  connect <url> <port>                  Conecta la sesión a un servidor JettraStore.
  login <username> <password>           Autentica con JettraJWT ('admin' / 'admin-jettra').
  logout                                Cierra la sesión activa actual y desconecta.

💾 GESTIÓN DE PERFILES DE CONEXIÓN:
  save connection <nombre>              Guarda los parámetros de conexión actuales.
  remove connection <nombre>            Elimina un perfil de conexión guardado.
  list connections / list conections    Muestra la tabla de todas las conexiones guardadas.
  connect <nombre-perfil>               Conecta directamente usando un perfil guardado.

📊 TELEMETRÍA, RECURSOS Y CLÚSTER:
  status                                Muestra consumo de recursos (RAM, PROCESADOR, DISCO).
  show nodes                            Muestra todos los nodos del clúster Raft y su estado.
  SHOW USERS / SECURITY STATUS          Muestra control de acceso (admin SUPER_ADMIN inmutable).

⚙️ RESOLUCIÓN DE REFERENCIAS (JettraRef):
  lazy reference on                     Activa la resolución perezosa bajo demanda (Proxy).
  lazy reference off                    Desactiva lazy reference; carga directa en memoria (Eager).
  lazy reference status                 Muestra el estado actual del modo lazy reference.

🔍 CONSULTAS Y LENGUAJES (JettraQL & JettraSQL):
  FROM <collection> [WHERE k = v]       Consulta documental expresiva JettraQL.
  MATCH (<src>)-[<lbl>]->(<tgt>)        Consulta de relaciones y aristas en grafos JettraQL.
  VECTOR SIMILARITY <col> TO [...]      Búsqueda vectorial Top-K por similaridad coseno.
  FETCH <col> <id> [RESOLVE REFS]       Recuperación de registro resolviendo enlaces JettraRef.
  SELECT ... FROM <col>                 Sentencias SQL tradicionales en JettraStore.

📁 BASES DE DATOS:
  SHOW DATABASES / SHOW DBS             Lista todas las bases de datos disponibles.
  CREATE DATABASE <dbname>              Crea una base de datos y la selecciona como activa.
  DROP DATABASE <dbname>                Elimina la base de datos especificada.
  USE <dbname>                          Conmuta la base de datos activa.
  DB STATS / STATS                      Muestra resumen y telemetría de la base de datos activa.

🗃️ COLECCIONES Y MODELOS:
  SHOW COLLECTIONS / SHOW TABLES        Lista colecciones y motores multimodelo activos.
  CREATE COLLECTION <col> [TYPE <tipo>] Crea colección (DOCUMENT, VECTOR, GRAPH, TS, KV).
  DROP COLLECTION <col>                 Elimina una colección y todos sus registros.
  COUNT <col>                           Retorna la cantidad total de registros en la colección.
  TRUNCATE <col>                        Vacía todos los registros de una colección.

📝 REGISTROS Y CRUD (DOCUMENT ENGINE):
  INSERT INTO <col> ID <id> JSON {..}   Inserta documento con _id y campos estructurados.
  GET <col> <id>                        Obtiene un documento por ID (resuelve JettraRef).
  FIND ALL <col> [LIMIT <n>]            Lista registros de la colección con paginación.
  UPDATE <col> <id> SET k=v, ...        Actualiza campos específicos del documento.
  DELETE <col> <id>                     Elimina un documento por su clave primaria _id.

⚡ MOTORES MULTIMODELO ESPECIALIZADOS:
  VECTOR INDEX <col> <id> [f1,f2,..]    Indexa vector float[] en el motor vectorial.
  VECTOR SEARCH <col> [f1,f2,..] [K 5]  Búsqueda de similaridad coseno (Top-K matches).
  GRAPH ADD VERTEX <col> <vId>          Agrega un nodo o vértice al grafo.
  GRAPH ADD EDGE <c> <s> -> <t> LABEL <l> Agrega una arista dirigida con etiqueta y peso.
  GRAPH GET EDGES <col> <vId>           Lista aristas salientes del vértice dado.
  TS RECORD <col> <val> [TIME <ts>]     Inserta punto en serie temporal.
  TS RANGE <col> <desde> <hasta>        Consulta rango temporal y calcula promedio.
  KV PUT <col> <clave> <valor>          Almacena par clave-valor binario.
  KV GET <col> <clave>                  Recupera valor correspondiente a la clave.

💾 PERSISTENCIA Y MUESTRAS COMPLETAS:
  INSTALL SAMPLES                       Instala TODAS las bases de datos de ejemplo (5 dbs).
  BACKUP DATABASE <db> TO '<path>'      Genera respaldo instantáneo en archivos .jettra.
  RESTORE DATABASE <db> FROM '<path>'   Restaura base de datos con validación de quórum.
  SET PAGE_SIZE = <n>                   Define la cantidad de registros por página.
  menu                                  Despliega el menú interactivo guiado.
  exit, quit                            Cierra la sesión del shell.
========================================================================================
```
