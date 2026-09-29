# JettraStoreFX: Manual Maestro de la Suite Gráfica de Escritorio 3D (`book.md`)

**Plataforma de Administración Visual Avanzada en JavaFX, Modelado Tridimensional de Clústeres, Dashboard de Telemetría, Asistentes de Backup/Restore y Consola LQL/SQL**
*Plataforma: JavaFX 25+ / JettraStore Ecosystem 1.0*

---

## 1. Visión General de `JettraStoreFX`

**`JettraStoreFX`** es la consola de administración visual oficial de `JettraStore`. Diseñada con una estética futurista inspirada en centros de mando de operaciones de misión crítica, implementa un motor híbrido de renderizado **2D Glassmorphic + 3D JavaFX Mesh Engine**. Permite a arquitectos de bases de datos, administradores y operadores supervisar en tiempo real la topología del clúster de 3 nodos, la persistencia en archivos `.jettra`, el balanceo en anillo por saturación de RAM, la ejecución interactiva de consultas y la gestión de copias de seguridad.

---

## 2. Pantalla de Bienvenida y Gestión de Perfiles de Conexión

### 2.1 Asistente de Conexión Inicial
Al iniciar `JettraStoreFX`, la interfaz despliega el panel de conexión holográfico:
* **URL de Conexión:** Formato `jettra://<ip-or-host>:<port>` (ej. `jettra://192.168.1.101:9091`).
* **Credenciales Recomendadas por Defecto:**
  * **Usuario:** `admin`
  * **Contraseña:** `admin-jettra`
* **Etiqueta de Entorno:** Permite clasificar la conexión como `PRODUCCIÓN`, `STAGING` o `LABORATORIO`.

### 2.2 Administrador de Múltiples Perfiles
`JettraStoreFX` almacena en un almacén local cifrado (`~/.jettra/fx_profiles.dat`) perfiles de conexión hacia múltiples instancias o clústeres. El usuario puede alternar de entorno con un solo clic en la barra de herramientas superior, manteniendo sesiones concurrentes protegidas por sus respectivos tokens **`JettraJWT`**.

---

## 3. Módulo de Usuarios, Roles y Seguridad Inviolable

### 3.1 Gestión de Cuentas y Privilegios
* Permite crear, modificar y suspender cuentas secundarias asignando roles granulares (`DB_ADMIN`, `DATA_READER`, `ANALYTICS_USER`, `AUDITOR`).
* Los tokens `JettraJWT` se refrescan automáticamente en segundo plano sin interrumpir las sesiones de trabajo del operador.

### 3.2 Protección e Inviolabilidad del Superusuario
La interfaz aplica validaciones visuales y de protocolo estrictas:
* El usuario `admin` aparece identificado con una insignia dorada de **`SUPER_ADMIN (IMMUTABLE)`**.
* Los botones para eliminar usuario, revocar permisos o degradar roles se deshabilitan permanentemente para la cuenta `admin`.
* Incluso si un usuario secundario con rol `DB_ADMIN` intenta ejecutar alteraciones sobre el superusuario a través de la consola integrada, la interfaz captura el error de protocolo y despliega una advertencia en rojo brillante, notificando la alerta registrada por `JettraPolice`.

---

## 4. Gestión Visual de Motores Multimodelo y Operaciones CRUD

La barra lateral de navegación organiza jerárquicamente las bases de datos y los 8 motores:
* **Explorador de Documentos:** Visor tipo árbol interactivo con resaltado de sintaxis JSON y editor de propiedades in-place.
* **Visor de Embeddings Vectoriales:** Gráfico de dispersión 2D/3D con reducción de dimensionalidad PCA/t-SNE para inspeccionar clústeres de similitud semántica.
* **Explorador de Grafos:** Renderizado interactivo de nodos y aristas con física de resortes (*force-directed layout*).
* **Visor de Series Temporales:** Gráficos continuos de alta resolución con agregación dinámica de métricas.
* **Control de Carga Perezosa (*Lazy Load*):** Cada registro con referencias inter o intra-engine muestra una insignia interactiva `[LazyRef: unresolved]`. Al hacer clic sobre ella, la interfaz emite la petición de resolución y despliega la entidad enlazada de forma suave.

---

## 5. Módulo Visual de Respaldo y Restauración (Backup & Restore)

`JettraStoreFX` proporciona asistentes gráficos paso a paso para salvaguardar y recuperar datos sin requerir conocimientos de terminal:

### 5.1 Asistente de Backup Interactivo
1. **Selección de Base de Datos:** Selector desplegable con todas las bases de datos activas en el clúster.
2. **Explorador de Rutas Físicas:** Diálogo nativo para seleccionar la carpeta o disco de destino (ej. `/mnt/san_backup/jettra_snapshots`).
3. **Opciones Avanzadas:** Casillas para compresión ZSTD rápida, inclusión de índices secundarios dispersos y suma de verificación criptográfica CRC64.
4. **Ejecución y Progreso:** Barra de progreso en tiempo real que reporta la congelación de MemTables, el flushing del WAL y el tamaño final de los archivos `.jettra` respaldados.

### 5.2 Asistente de Restauración (Restore Wizard)
1. Selección del archivo `.jettra_bak` o directorio de respaldo.
2. Validación de integridad previa: análisis de firmas `CRC64` y verificación de compatibilidad de versión de formato `.jettra`.
3. Confirmación de montaje y sincronización automática del nuevo estado en los 3 nodos del clúster Raft.

---

## 6. Dashboard de Telemetría y Panel 3D del Clúster

### 6.1 Panel de Métricas de Recursos en Tiempo Real
* **Consumo de Memoria:** Gráficos circulares y lineales diferenciando el Heap de la JVM (ZGC) y la memoria nativa off-heap asignada mediante **Project Panama**.
* **E/S de Almacenamiento `.jettra`:** Tasas de escritura en MemTables, flushes a disco y latencia de compactación en nanosegundos.
* **Feed de Alertas `JettraPolice`:** Panel deslizante que muestra en tiempo real las notificaciones preventivas (picos de saturación, umbrales de disco y advertencias de seguridad).

### 6.2 Visualizador de Clúster en 3D Interactivo
El componente estrella de `JettraStoreFX` es su lienzo tridimensional impulsado por el motor 3D de JavaFX:
* **Representación Espacial:** Los 3 nodos se renderizan como estaciones nodales flotantes en un espacio 3D interactivo con rotación libre, zoom y paneo orbital con el mouse.
* **Identificación Visual:**
  * **Nodo 1 (Líder Primario):** Esfera o prisma central con aura pulsante azul cobalto.
  * **Nodos 2 y 3 (Secundarios):** Estaciones orbitales conectadas por rayos de luz que representan los canales de sincronización Raft vía `jettraGRPC`.
* **Supervisión Individualizada:** Al pasar el cursor o hacer clic sobre cualquier nodo, se despliega una tarjeta holográfica con su IP, puerto, uso exacto de RAM y cantidad de archivos `.jettra` montados.
* **Operaciones Dinámicas:** Botones interactivos para **agregar nuevos nodos** al clúster o desacoplar nodos de forma segura.
* **Animación en Vivo de la Transición al Anillo Distribuido:**
  * Cuando el monitor detecta que el nodo líder alcanza el umbral de saturación de RAM ($\ge 85\%$), el nodo central cambia su iluminación a un tono ámbar de advertencia.
  * Se dispara una animación 3D continua de partículas luminosas que fluyen desde el nodo central hacia los nodos secundarios en forma de anillo circular giratorio.
  * En pantalla se despliega el indicador: `[ESTADO: MIGRACIÓN EN ANILLO DINÁMICO ACTIVA]`.
  * Los operadores observan visualmente cómo la barra de ocupación del nodo principal se reduce hacia el $40\%$, mientras los secundarios absorben equilibradamente los segmentos off-heap, celebrando la continuidad del servicio sin pérdida de rendimiento.

---

## 7. Consola Integrada de Consultas (LQL y JettraSQL)

* Editor de código con resaltado sintáctico dual (SQL y expresiones Lambda de Java).
* Autocompletado de colecciones, campos y funciones especializadas (`VECTOR_COSINE_SIMILARITY`, `TIME_BUCKET`).
* Tabla interactiva de resultados con exportación inmediata a JSON, CSV o formato plano `.jettra`.
* Visor del plan de ejecución (Explain Plan) que reporta el uso de índices secundarios dispersos y filtros de Bloom en nanosegundos.
