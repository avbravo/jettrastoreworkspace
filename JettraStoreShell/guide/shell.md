# JettraStoreShell: Guía Completa de la Consola Interactiva de Línea de Comandos (`shell.md`)

**Manual Operativo, Administración de Seguridad JettraJWT, Comandos CRUD, Gestión de Clúster, Backup y Restore**
*Versión de Shell: 1.0 | Compatible con JettraStore Cluster*

---

## 1. Visión General de `JettraStoreShell`

**`JettraStoreShell`** es la interfaz interactiva de terminal (CLI) oficial para la administración, depuración, mantenimiento y consulta en tiempo real de clústeres `JettraStore`. Construida en Java 25+, cuenta con autocompletado ANSI, soporte de historial de comandos, modo interactivo con menús guiados y conexión cifrada punto a punto mediante tokens **`JettraJWT`**.

---

## 2. Inicio y Conexión Segura

### 2.1 Sintaxis de Ejecución
Para iniciar el shell interactivo:
```bash
# Conexión interactiva solicitando credenciales
java -jar JettraStoreShell.jar --host 192.168.1.101 --port 9091

# Conexión directa mediante parámetros
java -jar JettraStoreShell.jar --host localhost --port 9091 -u admin -p admin-jettra
```

### 2.2 Autenticación con el Superusuario por Defecto
Al conectar por primera vez, el sistema solicita autenticación:
```text
================================================================================
                    __     __  __               _____ __          __  
                    \ \   / / / /              / ____|\ \        / /  
                     \ \_/ / / /_             | (___   \ \  /\  / /   
                      \   / / __ \    ______   \___ \   \ \/  \/ /    
                       | | / /_/ /   |______|  ____) |   \  /\  /     
                       |_|/_.___/             |_____/     \/  \/      
                                  JETTRASTORE SHELL v1.0
================================================================================
Connecting to JettraStore node at 127.0.0.1:9091... Connected via jettraGRPC.
Enter Username: admin
Enter Password: ************
[AUTH] Authenticated successfully as 'admin' (Role: SUPER_ADMIN).
[AUTH] JettraJWT session token issued [Valid: 24h]. Type 'help' or 'menu'.

admin@jettra-cluster:primary> 
```

---

## 3. Menú Interactivo y Asistente de Instalación de Ejemplos

Al ejecutar el comando `menu` en el prompt, el shell despliega un menú interactivo numerado:

```text
admin@jettra-cluster:primary> menu

========================= JETTRASTORE INTERACTIVE MENU =========================
  [1] Instalar Base de Datos de Ejemplos ('sample_enterprise_db')
  [2] Administrar Bases de Datos y Motores
  [3] Gestionar Usuarios y Permisos de Seguridad (JettraJWT)
  [4] Ejecutar Respaldo Inmediato (Hot Backup)
  [5] Restaurar Base de Datos desde Archivos .jettra
  [6] Inspeccionar Métricas de Clúster y Memoria Off-Heap
  [7] Salir del Menú Interactivo
================================================================================
Seleccione una opción [1-7]: 1

[SAMPLES] Provisionando base de datos 'sample_enterprise_db'...
[SAMPLES] Creando Motor Document: 'products' (10,000 registros)... [OK]
[SAMPLES] Creando Motor Vectorial: 'product_embeddings' (512-dim)... [OK]
[SAMPLES] Creando Motor Grafos: 'product_categories' (Vertices/Edges)... [OK]
[SAMPLES] Creando Motor TimeSeries: 'sales_telemetry'... [OK]
[SAMPLES] Estableciendo referencias cruzadas Inter-Engine con Lazy Load... [OK]
[SUCCESS] Base de datos de ejemplo instalada exitosamente en archivos .jettra.
```

---

## 4. Comandos de Administración de Usuarios y Seguridad

### 4.1 Creación de Usuarios Secundarios
```sql
CREATE USER 'operator_user' IDENTIFIED BY 'SecretPass#2026' ROLE 'DB_ADMIN';
```

### 4.2 Restricción Absoluta del Superusuario `admin`
Cualquier intento por parte de un usuario que no sea el superusuario para alterar los privilegios de `admin` será inmediatamente bloqueado:
```text
operator@jettra-cluster> ALTER USER 'admin' ROLE 'READ_ONLY';
[SECURITY ERROR] Security violation: Superuser privileges cannot be altered, modified, or revoked by secondary accounts.
[JettraPolice ALERT] Incident logged: Unauthorized role modification attempt on 'admin'.
```

### 4.3 Cambio Recomendado de Contraseña de `admin`
```sql
ALTER USER 'admin' IDENTIFIED BY 'SuperSecureK3y#2026!';
```

---

## 5. Gestión de Bases de Datos y Motores

```sql
-- Creación de una nueva base de datos
CREATE DATABASE financial_analytics;

-- Conmutación de contexto de base de datos
USE financial_analytics;

-- Creación de motores específicos
CREATE ENGINE transactions TYPE DOCUMENT;
CREATE ENGINE customer_vectors TYPE VECTOR DIMENSIONS 768 METRIC COSINE;
CREATE ENGINE fraud_graph TYPE GRAPH;
CREATE ENGINE stock_ticks TYPE TIMESERIES;

-- Listar motores y bases de datos activas
SHOW DATABASES;
SHOW ENGINES;
```

---

## 6. Operaciones CRUD, Paginación y Carga Perezosa

### 6.1 Inserción de Documentos y Referencias
```sql
INSERT INTO transactions (_id, amount, currency, customer_id, _ref_vector) 
VALUES ('tx_9901', 4500.00, 'USD', 'cust_12', 'vector::customer_vectors#emb_cust_12');
```

### 6.2 Control de Carga Perezosa (*Lazy Load*) y Paginación
```text
-- Configurar la sesión para no resolver referencias automáticamente (ahorro de RAM)
SET LAZY_LOAD = TRUE;

-- Mostrar u ocultar descriptores de referencias cruzadas en el output
SET SHOW_REFERENCES = ON;

-- Paginación interactiva
SET PAGE_SIZE = 25;

-- Consulta con paginación
SELECT _id, amount, currency, _ref_vector 
FROM transactions 
WHERE amount > 1000.00 
ORDER BY amount DESC 
LIMIT 25 OFFSET 0;
```

---

## 7. Comandos Nativos de Backup y Restore

### 7.1 Copia de Seguridad Instantánea (`BACKUP DATABASE`)
Congela las MemTables y genera una copia atómica de los archivos `.jettra` en el directorio de destino configurado o especificado:
```sql
BACKUP DATABASE financial_analytics TO '/backup/financial_analytics_snapshot.jettra_bak';
```
*Salida de confirmación:*
```text
[BACKUP] Initiating atomic flush of MemTable and WAL...
[BACKUP] Copying SSTables and Bloom Filters (.jettra) off-heap...
[SUCCESS] Backup completed in 68ms. Target: /backup/financial_analytics_snapshot.jettra_bak (2.4 GB).
```

### 7.2 Restauración de Base de Datos (`RESTORE DATABASE`)
Restaura una base de datos validando los checksums criptográficos `CRC64` y reconstruyendo los índices dispersos:
```sql
RESTORE DATABASE financial_analytics FROM '/backup/financial_analytics_snapshot.jettra_bak';
```
*Salida de confirmación:*
```text
[RESTORE] Validating .jettra binary blocks... OK
[RESTORE] Checking Raft consensus log synchronization... OK
[RESTORE] Rebuilding sparse secondary index structures... OK
[SUCCESS] Database 'financial_analytics' restored successfully and mounted in cluster.
```

---

## 8. Comando de Ayuda (`help` / `?`)

Al escribir `help` o `?`, el shell despliega la lista completa de sintaxis clasificada:

```text
admin@jettra-cluster:primary> help

============================= JETTRASTORE SHELL HELP ============================
SINTAXIS GENERAL:
  help, ?                         Muestra esta guía de comandos.
  menu                            Abre el menú interactivo guiado.
  exit, quit                      Cierra la sesión actual de forma segura.

ADMINISTRACIÓN DE SEGURIDAD (JettraJWT):
  CREATE USER <name> IDENTIFIED BY '<pass>' ROLE '<role>'
  ALTER USER <name> IDENTIFIED BY '<pass>'
  DROP USER <name>
  SHOW USERS

BASES DE DATOS Y ENGINES:
  CREATE DATABASE <dbname>
  DROP DATABASE <dbname>
  USE <dbname>
  SHOW DATABASES
  CREATE ENGINE <name> TYPE <DOCUMENT|VECTOR|GRAPH|TIMESERIES|COLUMNAR|KEYVALUE|GEO|RECORD>
  SHOW ENGINES

CONSULTAS Y MUTACIONES (JettraSQL / LQL):
  SELECT <cols> FROM <engine> [WHERE ...] [ORDER BY ...] [LIMIT n OFFSET m]
  INSERT INTO <engine> (<cols>) VALUES (<vals>)
  UPDATE <engine> SET <col> = <val> [WHERE ...]
  DELETE FROM <engine> [WHERE ...]

DIRECTIVAS DE RENDIMIENTO:
  SET LAZY_LOAD = TRUE | FALSE    Activa o desactiva la resolución diferida de punteros.
  SET SHOW_REFERENCES = ON | OFF  Muestra/oculta referencias cruzadas inter-engine.
  SET PAGE_SIZE = <num>           Establece el tamaño de página para visualización.

RESPALDO Y RECUPERACIÓN (BACKUP & RESTORE):
  BACKUP DATABASE <dbname> TO '<path>'
  RESTORE DATABASE <dbname> FROM '<path>'

CLÚSTER Y TELEMETRÍA:
  SHOW CLUSTER                    Muestra los 3 nodos, líder Raft y estado del anillo.
  SHOW MEMORY                     Muestra uso de Heap JVM y memoria nativa Panama.
================================================================================
```
