# JettraStore: Catálogo Detallado de Componentes y Módulos del Ecosistema (`components.md`)

**Especificación Técnica de los Módulos de Infraestructura, Conectividad, Serialización, Reglas y Pruebas**
*Ecosistema Jettra Core — Versión 1.0*

---

## 1. Módulo `jettra collection` (Colecciones Primitivas Off-Heap Zero-Boxing)

### Propósito y Arquitectura
`jettra collection` es el pilar de rendimiento de memoria en `JettraStore`. En arquitecturas de bases de datos que procesan millones de lecturas y escrituras por segundo, las estructuras tradicionales de `java.util.*` provocan una saturación severa del montículo de la JVM debido al *boxing/unboxing* de tipos primitivos (`long`, `int`, `double`) y la fragmentación de memoria provocada por las cabeceras de objetos.

### Componentes Principales
* **`JettraLongLongHashMap`:** Mapa hash asociativo de `long` a `long` implementado mediante direccionamiento abierto (*open addressing*) con sondeo lineal (*linear probing*), almacenado en memoria contigua fuera del montículo (`MemorySegment`).
* **`JettraByteArrayList`:** Lista dinámica de bytes planos que replica la funcionalidad de `ArrayList<Byte>` sin generar instancias de envoltura, optimizada para recepción directa de streams `jettraGRPC`.
* **`JettraIntObjectMap<V>`:** Mapa optimizado para identificar índices dispersos por ID entero de bloque.

---

## 2. Módulo `jettraRest` (Capa de Servicios Web HTTP/REST Ligera)

### Propósito y Arquitectura
`jettraRest` proporciona una interfaz HTTP/REST de ultra-baja latencia orientada a aplicaciones web y microservicios externos:
* **Ejecución sobre Virtual Threads:** Cada conexión entrante HTTP se asigna a un Virtual Thread nativo de Java 25, permitiendo manejar cientos de miles de conexiones concurrentes con una huella de memoria minúscula.
* **Cero Dependencias Pesadas:** No requiere servlets tradicionales ni servidores de aplicaciones de gran tamaño; corre sobre un servidor HTTP embebido afinado con I/O no bloqueante.
* **Seguridad Nativa con `JettraJWT`:** Filtro de interceptación que valida la cabecera `Authorization: JettraJWT <token>` antes de despachar cualquier operación de base de datos.

---

## 3. Módulo `jettraGRPC` (Transporte de Clúster de Ultra-Baja Latencia)

### Propósito y Arquitectura
`jettraGRPC` es el canal de comunicación interna entre los nodos del clúster de `JettraStore` y los drivers nativos:
* **Soporte Raft sin Bloqueo:** Utilizado para la replicación del log WAL, votaciones de consenso y heartbeats periódicos de quórum.
* **Túnel de Migración de Anillo:** Empleado durante el desbordamiento por saturación de RAM para transmitir MemTables y SSTables inmutables en bloques binarios directos sin serialización intermedia.
* **Firmado Criptográfico:** Toda trama RPC incluye una firma de integridad basada en el token `JettraJWT` de sesión del clúster.

---

## 4. Módulo `jettraJson` (Motor de Serialización y Parsing JSON Zero-Copy)

### Propósito y Arquitectura
Diseñado para alimentar el motor de Documentos (`DocumentEngine`):
* **Aceleración SIMD:** Emplea instrucciones vectoriales (Vector API de Java 25) para escanear delimitadores JSON (`{`, `}`, `[`, `]`, `"`) en paralelo sobre buffers nativos de memoria.
* **Lazy Deserialization:** Los campos no solicitados en una consulta no se deserializan en cadenas Java, manteniéndose como punteros de memoria dentro del archivo `.jettra` hasta su acceso explícito.

---

## 5. Módulo `jettraJWT` (Infraestructura Criptográfica de Tokens)

### Propósito y Arquitectura
Responsable de la seguridad perimetral e interna del ecosistema:
* **Algoritmos Criptográficos:** Firma y verificación con **Ed25519** (curvas elípticas de alto rendimiento) o HMAC-SHA512.
* **Claims Estandarizados:** Incluye emisor (`iss`), sujeto/usuario (`sub`), rol de seguridad (`role`), ID de clúster (`cluster_id`) y permisos granulares por motor (`permissions`).
* **Protección del Superusuario:** Valida de forma estricta que las llamadas administrativas reservadas al usuario `admin` lleven un token válido con rol `SUPER_ADMIN`, impidiendo cualquier modificación o degradación por roles inferiores.

---

## 6. Módulo `jettraRules` (Motor Autónomo de Reglas de Negocio y Telemetría)

### Propósito y Arquitectura
Base lógica empleada tanto por el componente **`JettraPolice`** como por las validaciones de esquemas en tiempo de inserción:
* **Evaluación de Expresiones Ultrarrápida:** Evalúa condiciones booleanas complejas sobre el estado del clúster (ej. `RAM_USAGE > 0.85 && DISK_FREE < 0.10`).
* **Motor Declarativo:** Permite añadir reglas de supervisión preventiva sin necesidad de recompilar el motor central de almacenamiento.

---

## 7. Módulo `jettraAnnotation` (Metadatos y Procesamiento en Tiempo de Compilación)

### Propósito y Arquitectura
Facilita el mapeo objeto-documento y la definición de esquemas multimodelo en Java:
* **Anotaciones Nativas:**
  * `@JettraEntity(engine = EngineType.DOCUMENT, collection = "customers")`: Declara la persistencia de una clase o Java Record.
  * `@JettraId`: Identificador único de clave primaria en el archivo `.jettra`.
  * `@JettraRef(targetEngine = EngineType.VECTOR, lazy = true)`: Define una referencia cruzada inter-engine con soporte de carga perezosa.
  * `@JettraIndex(sparse = true)`: Marca campos para indexación secundaria dispersa.
* **Generación de Código APT:** Los procesadores de anotaciones generan serializadores nativos en tiempo de compilación (*Ahead-Of-Time*), erradicando el uso de reflexión en tiempo de ejecución.

---

## 8. Módulo `jettraTest` (Framework de Validación Distribuida)

### Propósito y Arquitectura
Herramienta de pruebas unitarias, de integración y de estrés para desarrolladores de `JettraStore`:
* **Clúster Efímero en Memoria:** Proporciona utilidades para inicializar clústeres virtuales de 3 nodos en milisegundos.
* **Inyector de Fallos de Red y Memoria:** Permite simular particiones de red (*network splits*), caídas súbitas del líder Raft y saturación forzada de memoria para evaluar la activación del anillo distribuido.

---

## 9. Módulo `jettraEE` (Capa de Integración Empresarial)

### Propósito y Arquitectura
Proporciona compatibilidad con estándares empresariales de Java:
* **Transaccionalidad ACID Distribuida:** Soporte para transacciones bi-fase (2PC) coordinadas por el nodo líder.
* **Inyección de Dependencias:** Integración liviana de componentes para inyectar instancias de `JettraDatabase` y motores en aplicaciones empresariales sin sobrecarga.
