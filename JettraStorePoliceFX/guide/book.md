# JettraStorePoliceFX: Guía de Arquitectura y Manual del Entorno 3D Inmersivo (`book.md`)

**Plataforma de Monitoreo Espacial Tridimensional y Supervisión Preventiva de Datos Basada en la Fundación Visual de `JettraICore`**
*Plataforma: JavaFX 3D / Java 25+ / JettraPolice Ecosystem*

---

## 1. Visión General y Fundación en `JettraICore`

**`JettraStorePoliceFX`** es un cliente especializado de visualización y supervisión preventiva que transforma la telemetría abstracta de un clúster de base de datos en un **mundo tridimensional navegable en tiempo real**. Tomando como base conceptual y estructural el motor de renderizado y la arquitectura visual de **`JettraICore`** (proyecto de visualización neuronal e inteligencia espacial del ecosistema Jettra), `JettraStorePoliceFX` recrea una matriz tridimensional cibernética donde:
* Los nodos de cómputo y sus almacenes físicos de archivos `.jettra` se manifiestan como macroestructuras espaciales.
* El componente autónomo **`JettraPolice`** adopta la forma de un **agente centinela 3D interactivo** que patrulla activamente las rutas de datos.
* Los paquetes de transacciones y los flujos de migración del anillo distribuido se proyectan como partículas cinéticas dinámicas.
* Las condiciones críticas disparan alertas holográficas tridimensionales que advierten de manera inmersiva al equipo de operaciones.

---

## 2. Requisitos del Sistema e Instalación

### 2.1 Requisitos de Hardware y Software
* **Sistema Operativo:** Linux (Ubuntu 22.04+, Fedora, Debian), macOS 14+ o Windows 11.
* **JVM:** OpenJDK 25+ con soporte para Project Panama y Virtual Threads.
* **Tarjeta Gráfica:** Aceleradora GPU compatible con OpenGL 4.3+ o DirectX 11 / Vulkan con al menos $2\text{ GB}$ de VRAM dedicada.
* **JavaFX:** JavaFX 25 SDK con soporte nativo de aceleración por hardware (`-Dprism.order=es2,d3d`).

### 2.2 Compilación y Ejecución
```bash
# Navegar al directorio del proyecto
cd JettraStorePoliceFX

# Compilación con Maven
mvn clean package -DskipTests

# Ejecución del cliente 3D conectándose al clúster principal
java --module-path $JAVAFX_HOME/lib --add-modules javafx.controls,javafx.graphics,javafx.fxml \
     -jar target/JettraStorePoliceFX.jar \
     --host 192.168.1.101 --port 9091 \
     --user admin --password admin-jettra
```

---

## 3. Conexión Segura con `JettraJWT` y Protocolo de Telemetría

1. **Handshake Seguro:** Al arrancar, `JettraStorePoliceFX` envía una petición de login vía `jettraGRPC` utilizando las credenciales administrativas.
2. **Recepción del Token:** El servidor emite un token de sesión `JettraJWT` con permisos de telemetría y suscripción de eventos (`TELEMETRY_SUBSCRIBER`).
3. **Flujo de Eventos Push:** A través de un canal bidireccional streaming de gRPC protegido, el motor envía 60 paquetes de telemetría por segundo hacia el motor de renderizado de la UI.

---

## 4. Arquitectura de Renderizado 3D y Navegación Espacial

La escena principal está construida sobre un grafo de escena `SubScene` de JavaFX configurado con `PerspectiveCamera`:

```
               [ PerspectiveCamera (Orbital / First-Person) ]
                                    │
                                    ▼
       ┌───────────────────── 3D Matrix Root ─────────────────────┐
       │                                                          │
       ├─► [ Suelo Holográfico de Grilla Reticular (GridMesh) ]    │
       │                                                          │
       ├─► [ Nodo 1 Primario: Monolito Cúbico Azul con Glow ]    │
       │      └─► [ Cilindros de Persistencia FÍSICA: /var/jettra ]│
       │                                                          │
       ├─► [ Nodos 2 y 3 Secundarios: Torres de Cómputo ]        │
       │                                                          │
       ├─► [ Sistema de Partículas: Flujo de Paquetes Raft ]     │
       │                                                          │
       └─► [ Avatar 3D de JettraPolice: Agente Móvil Centinela ]   │
```

### Controles de Navegación del Usuario
* **Rotación de Cámara:** Clic derecho sostenido + Arrastre del ratón (Rotación orbital en ejes X e Y).
* **Desplazamiento Espacial (Pan):** Clic central o Shift + Arrastre.
* **Zoom Continuo:** Rueda del ratón para acercarse o alejarse de los nodos.
* **Modo Seguimiento (Follow Camera):** Tecla `F` para anclar la cámara a la espalda del agente `JettraPolice` mientras patrulla.

---

## 5. El Agente `JettraPolice` en el Mundo 3D

El agente **`JettraPolice`** no es un simple indicador de estado estático, sino una entidad autónoma con comportamientos programados basados en la telemetría del servidor:

```java
public class JettraPoliceAgentMesh extends Group {
    // Esfera central luminosa rodeada por anillos de escaneo giratorios
    private final Sphere coreSphere;
    private final Torus scanRingX;
    private final Torus scanRingY;
    
    public void onTelemetryUpdate(PoliceTelemetryEvent event) {
        if (event.isAnomalous()) {
            // Transición de color a rojo/ámbar e incremento en la velocidad de rotación
            setScanColor(Color.CRIMSON);
            acceleratePatrolTo(event.getNodeTargetCoordinates());
            projectWarningBeacon(event.getWarningMessage());
        } else {
            setScanColor(Color.CYAN);
            patrolIdleWaypoints();
        }
    }
}
```

### Comportamientos Visuales de `JettraPolice`
1. **Patrulla Preventiva:** Durante la operación normal, el avatar flota suavemente a lo largo de los enlaces de red que unen al Nodo 1 con los Nodos 2 y 3, inspeccionando visualmente las particiones de disco.
2. **Intervención por Alta Memoria:** Cuando el consumo de RAM supera el umbral de advertencia ($75\%$), el agente se desplaza a máxima velocidad hacia el nodo afectado, proyectando un cono de luz analítico sobre sus cilindros de memoria.
3. **Escudo de Mitigación:** Al activarse la arquitectura de anillo por saturación ($\ge 85\%$), el agente expande un campo de fuerza toroidal alrededor del nodo líder mientras canaliza los flujos de partículas que viajan hacia los nodos secundarios.

---

## 6. Dinámica de Paquetes de Datos y Animación de Desbordamiento en Anillo

### 6.1 Flujo Normal de Transacciones (Raft Quorum)
Los paquetes de lectura y escritura se representan como **esferas de fotones de luz azul y verde** que viajan a alta velocidad a lo largo de splines cúbicos que conectan a los clientes con el nodo líder y desde este hacia los seguidores.

### 6.2 Visualización 3D del Anillo de Saturación
En el instante exacto en que el nodo principal activa la transferencia de anillo:
* Una **banda toroidal dorada giratoria** emerge en el centro del espacio tridimensional conectando los 3 nodos en un bucle cerrado.
* Miles de partículas de datos incandescentes fluyen a través de la banda de anillo hacia los nodos secundarios.
* La altura visual de las barras de almacenamiento en los nodos secundarios crece en tiempo real a medida que reciben los archivos `.jettra` off-heap.
* El volumen del nodo primario se contrae visualmente, ilustrando la descompresión inmediata de la RAM.

---

## 7. Notificaciones de Advertencia y Alertas Holográficas 3D

Cuando se detecta una contingencia crítica, `JettraStorePoliceFX` proyecta paneles holográficos tridimensionales flotantes en el espacio virtual:
* **Fallo de Nodo (Node Crash / Disconnection):** El nodo caído pierde su iluminación, se fragmenta en mallas oscuras y un prisma rojo parpadeante proyecta: `[CRITICAL: NODE-02 UNREACHABLE - RAFT QUORUM PRESERVED]`.
* **Saturación Inminente de Disco Físico:** Si la ruta `/var/jettra/data` alcanza el $90\%$, los cilindros de disco emiten chispas visuales y `JettraPolice` ejecuta una animación de rayo láser sobre el nodo, simbolizando la compactación en caliente de SSTables.
* **Ataque de Seguridad o Intrusión:** Si se registran violaciones de permisos sobre el usuario `admin`, un campo de advertencia estroboscópico cubre la escena con el texto: `[SECURITY BREACH: SUPERUSER TAMPERING PREVENTED]`.

---

## 9. Plano Cartesiano Cybernetic 3D y Referencias Espaciales Mejoradas

El entorno 3D implementa un sistema de coordenadas espaciales de precisión cibernética para facilitar la orientación tridimensional de los operadores:

* **Ejes Cartesianos Tridimensionales:**
  * **Eje X (Rojo Neón):** 720 unidades a lo largo del eje este-oeste con punteros cónicos en $\pm 360$.
  * **Eje Y (Verde Neón Cenital):** 200 unidades verticales con anillos holográficos de altitud cada 40 unidades.
  * **Eje Z (Azul Eléctrico / Cian):** 720 unidades en profundidad norte-sur con flechas directrices.
* **Anillos de Alcance Radar:** Cuatro círculos concéntricos a radios de 100, 200, 300 y 360 unidades con resplandor neón en el plano del suelo.
* **Balizas de Límites Perimetrales:** 4 torres en las esquinas $(\pm 360, 0, \pm 360)$ con haces de luz ascendentes y orbes de energía dorada.

---

## 10. Panel de Control: Pestaña de Eventos y Estado en Tiempo Real

El panel lateral derecho incorpora la pestaña **`Eventos & Estado`**, permitiendo la supervisión reactiva del centinela `JettraPolice`:
* **Estado del Centinela:** Indicador de estado del agente (`NORMAL`, `WARNING_RAM`, `CRITICAL_SECURITY`) y confirmación del hilo daemon virtual.
* **Telemetría de Memoria Heap:** Medidor visual animado de saturación de Heap en tiempo real, indicando MB en uso, libres y límites máximos.
* **Feed de Eventos en Tiempo Real:** Lista cronológica de intervenciones del sistema:
  * `HEAP_EXHAUSTION_PREVENTED`: Intervenciones donde se forzó paginación lazy preventiva.
  * `CRITICAL_RAM_PRESSURE`: Advertencias críticas de memoria.
  * `POLICE_STARTED`: Estado de arranque del supervisor.
* **Botonera Interactiva:** Permite simular consultas masivas para probar el mecanismo de auto-paginación y verificar la respuesta reactiva del agente 3D sin detener el clúster.
