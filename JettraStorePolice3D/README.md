# JettraStorePolice3D

Proyecto unificado de monitoreo 3D en tiempo real para **JettraStore**, integrando la capa profunda de IA (`jettra-dl`) y el motor gráfico nativo (`jettra-3d-java`) en una sola aplicación interactiva con soporte para submundos cuánticos de nodos servidores y evaluación policial por **JettraStorePolice**.

---

## Características Principales

1. **Proyecto Unificado**:
   - Fusión completa de `jettra-dl` y `jettra-3d-java` en un único artefacto Maven `JettraStorePolice3D` sin dependencias modulares intermedias.
   - Desarrollado en **Java 25** aprovechando la API **Foreign Function & Memory (FFM / Project Panama)** para acceso nativo a **Raylib 5.5** y **Project Loom** para Virtual Threads.

2. **Monitoreo 3D de Clúster JettraStore**:
   - Servidores de clúster representados como racks 3D en el mundo macro (`node-01-master`, `node-02-replica`, `node-03-replica`).
   - Balizas luminosas, haces de datos y anillos Raft animados.
   - Centinela autónomo **JettraStorePolice** patrullando físicamente los nodos y emitiendo veredictos en tiempo real.
   - Indicador visual cuando un nodo está **FUERA DE SERVICIO** (sirenas rojas parpadeantes, vigas cortadas y alertas críticas).

3. **Expansión a Submundos Interiores de Nodo**:
   - Al **hacer clic** sobre cualquier nodo servidor (o pulsar el botón en el modal de inspección), el nodo se expande y transporta al usuario al **Mundo Interior del Servidor**.
   - **Bases de Datos 3D Alojadas**:
     - `example_factura_db`: Multimodelo Off-Heap (Document, KeyValue, Vector, Graph, TimeSeries) con 3,750,000 registros (1.2 GB).
     - `samples_hostipal_db`: Red Hospitalaria y Salud Pública con 2,000,000 registros (850 MB).
     - `samples_ambiental_db`: Monitoreo Ambiental Mundial y Clima con 3,000,000 registros (1.45 GB).
     - `system_metadata_db`: Catálogo de Esquemas y Transacciones Raft (45 MB).
   - Fichas técnicas interactivas al hacer clic sobre los pedestales de bases de datos detallando sus buckets especializados y lecturas/escrituras IOPS.

4. **Panel de Recursos Consumidos del Nodo**:
   - HUD flotante de cristal que muestra en tiempo real:
     - Saturación y uso de **Memoria Heap JVM** (con barra progresiva coloreada).
     - **Memoria Off-Heap (Panama Direct Memory)** sin pausa de Garbage Collection.
     - Concurrencia de **Hilos Virtuales Loom** y núcleos de CPU.
     - **Almacenamiento LSM**: MemTable en RAM y volumen de archivos `.sst` en disco.
     - Diagnóstico policial de estabilidad emitido por `JettraStorePolice`.
     - Botón para simular desconexión/reconexión en caliente.

5. **La Puerta al Mundo Principal (Portal Dimensional)**:
   - Portal 3D monumental ubicado en el extremo del submundo con columnas de neón, umbral iluminado y vórtice de anillos plasmáticos giratorios.
   - Al hacer clic en la puerta, acercarse a ella o pulsar la tecla **ESC** / **BACKSPACE**, el portal se activa y regresa al usuario de forma fluida al Mundo Principal.

---

## Controles e Interacción

| Acción | Control |
|---|---|
| **Expandir y entrar al mundo del nodo** | Clic izquierdo sobre un servidor en la vista principal |
| **Inspeccionar nodo (Ficha flotante)** | Clic derecho sobre un servidor o pulsar tecla `N` |
| **Inspeccionar base de datos 3D** | Clic izquierdo sobre el pedestal de la base de datos dentro del nodo |
| **Atravesar Puerta y regresar al mundo principal** | Clic sobre la Puerta 3D, pulsar tecla `ESC` o botón superior derecho |
| **Alternar estado En Línea / Fuera de Servicio** | Botón dentro del panel de recursos de cada nodo |
| **Moverse / Cámara libre** | Teclas `W`, `A`, `S`, `D` y botón derecho del ratón |
| **Zoom de cámara** | Rueda del ratón |

---

## Compilación y Ejecución

Compilar el proyecto y ejecutar los tests:
```bash
mvn clean test
```

Iniciar la aplicación 3D:
```bash
mvn exec:java
# o mediante el script:
./buildandrun.sh
```
