package io.jettra.fx;

import io.jettra.driver.JettraClient;
import io.jettra.fx.profile.ConnectionProfile;
import io.jettra.fx.view3d.Cluster3DVisualizer;
import io.jettra.store.core.JettraDatabase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Map;

public class JettraStoreFXApp extends Application {
    private JettraClient client;
    private final ConnectionProfile.ProfileManager profileManager = new ConnectionProfile.ProfileManager();
    private Cluster3DVisualizer cluster3D;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JettraStoreFX - Centro de Mando 3D y Administración de Clúster (Java 25+)");

        // Conectar inicialmente con el perfil por defecto
        ConnectionProfile def = profileManager.getActiveProfile();
        this.client = JettraClient.connect(def.getHost(), def.getPort(), def.getUsername(), def.getPassword());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0F172A; -fx-text-fill: white;");

        // Header Superior
        HBox header = createHeader();
        root.setTop(header);

        // TabPane Central
        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
            createCluster3DTab(),
            createDatabaseEnginesTab(),
            createBackupRestoreTab(),
            createSecurityUsersTab(),
            createConsoleTab()
        );
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1100, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155; -fx-border-width: 0 0 1 0;");

        Label title = new Label("JETTRASTORE FX");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label badge = new Label("PRODUCCIÓN - 3 NODOS RAFT");
        badge.setStyle("-fx-background-color: #0369A1; -fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label("Sesión: admin (SUPER_ADMIN)");
        userLabel.setStyle("-fx-text-fill: #FACC15; -fx-font-weight: bold;");

        header.getChildren().addAll(title, badge, spacer, userLabel);
        return header;
    }

    private Tab createCluster3DTab() {
        Tab tab = new Tab("Visualizador 3D y Telemetría");
        tab.setClosable(false);

        BorderPane layout = new BorderPane();
        this.cluster3D = new Cluster3DVisualizer();

        // SubScene 3D
        SubScene subScene3D = new SubScene(cluster3D, 750, 600, true, SceneAntialiasing.BALANCED);
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-350);
        camera.setTranslateY(-50);
        camera.setNearClip(0.1);
        camera.setFarClip(1000.0);
        subScene3D.setCamera(camera);

        // Panel Lateral de Control y Telemetría
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(15));
        sidebar.setPrefWidth(320);
        sidebar.setStyle("-fx-background-color: #1E293B;");

        Label lblTitle = new Label("TELEMETRÍA EN TIEMPO REAL");
        lblTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label ramUsage = new Label("RAM Off-Heap (Panama): 42.5% [Seguro]");
        ramUsage.setStyle("-fx-text-fill: #4ADE80;");

        Label jvmHeap = new Label("JVM ZGC Heap: 18.2% (Pausas < 1ms)");
        jvmHeap.setStyle("-fx-text-fill: #E2E8F0;");

        Label ringStatus = new Label("Estado del Anillo: INACTIVO (Modo Local)");
        ringStatus.setStyle("-fx-text-fill: #94A3B8;");

        Button btnSimulateSaturation = new Button("Simular Saturación RAM (>= 85%)");
        btnSimulateSaturation.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSimulateSaturation.setOnAction(e -> {
            boolean active = !cluster3D.isRingTransitionActive();
            cluster3D.setRingTransitionActive(active);
            if (active) {
                ringStatus.setText("Estado del Anillo: ¡ACTIVO! (Desbordando)");
                ringStatus.setStyle("-fx-text-fill: #FACC15; -fx-font-weight: bold;");
                ramUsage.setText("RAM Off-Heap: Descargada a 40.0%");
            } else {
                ringStatus.setText("Estado del Anillo: INACTIVO (Modo Local)");
                ringStatus.setStyle("-fx-text-fill: #94A3B8;");
                ramUsage.setText("RAM Off-Heap (Panama): 42.5% [Seguro]");
            }
        });

        Button btnAddNode = new Button("+ Agregar Nodo Dinámico");
        btnAddNode.setOnAction(e -> cluster3D.addNode("node-dyn", false, 0, -120, -50));

        sidebar.getChildren().addAll(lblTitle, ramUsage, jvmHeap, ringStatus, btnSimulateSaturation, new Separator(), btnAddNode);

        layout.setCenter(subScene3D);
        layout.setRight(sidebar);
        tab.setContent(layout);
        return tab;
    }

    private Tab createDatabaseEnginesTab() {
        Tab tab = new Tab("Bases de Datos y Motores");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(15));
        Label lbl = new Label("Explorador de Motores Multimodelo (Documentos, Vectores, Grafos, TimeSeries)");
        lbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        TextArea area = new TextArea("Colección: products\n[_id: prod_01, name: 'Quantum Chip', price: 1200.0, _ref_vector: 'vector::emb_01 (LazyRef: Click to resolve)']");
        area.setPrefRowCount(15);
        content.getChildren().addAll(lbl, area);
        tab.setContent(content);
        return tab;
    }

    private Tab createBackupRestoreTab() {
        Tab tab = new Tab("Asistente Backup & Restore");
        tab.setClosable(false);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label title = new Label("Respaldos en Caliente y Restauración Consistente");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        TextField txtDb = new TextField("sample_enterprise_db");
        txtDb.setPromptText("Nombre de base de datos");

        TextField txtPath = new TextField("./data/backup_snapshot.jettra_bak");
        txtPath.setPromptText("Ruta de archivo .jettra_bak");

        Label status = new Label("Listo para operaciones de persistencia.");

        Button btnBackup = new Button("Ejecutar Hot Backup (.jettra)");
        btnBackup.setOnAction(e -> {
            var res = client.admin().backupDatabase(client.getDatabase(txtDb.getText()), Path.of(txtPath.getText()));
            status.setText(res.message());
        });

        Button btnRestore = new Button("Ejecutar Restauración");
        btnRestore.setOnAction(e -> {
            var res = client.admin().restoreDatabase(Path.of(txtPath.getText()), client.getDatabase(txtDb.getText()));
            status.setText(res.message());
        });

        content.getChildren().addAll(title, new Label("Base de Datos:"), txtDb, new Label("Ruta Destino/Origen:"), txtPath, btnBackup, btnRestore, status);
        tab.setContent(content);
        return tab;
    }

    private Tab createSecurityUsersTab() {
        Tab tab = new Tab("Usuarios y Seguridad JettraJWT");
        tab.setClosable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label title = new Label("Administración de Roles y Seguridad Criptográfica");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label adminNote = new Label("SUPERUSUARIO: admin (Inmutable - Privilegios protegidos contra alteración)");
        adminNote.setStyle("-fx-text-fill: #FACC15; -fx-font-weight: bold;");

        Button btnAttemptAlter = new Button("Intentar Modificar Superusuario admin");
        Label alertResult = new Label();
        btnAttemptAlter.setOnAction(e -> {
            alertResult.setText("[SEGURIDAD RECHAZADA] Los privilegios del superusuario 'admin' son absolutos e inviolables.");
            alertResult.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
        });

        content.getChildren().addAll(title, adminNote, btnAttemptAlter, alertResult);
        tab.setContent(content);
        return tab;
    }

    private Tab createConsoleTab() {
        Tab tab = new Tab("Consola SQL / LQL");
        tab.setClosable(false);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        TextArea editor = new TextArea("SELECT * FROM products WHERE price > 500.0;");
        editor.setPrefRowCount(4);

        Button btnRun = new Button("Ejecutar Sentencia");
        TextArea output = new TextArea();
        output.setPrefRowCount(10);
        output.setEditable(false);

        btnRun.setOnAction(e -> {
            var res = client.sql("sample_enterprise_db", editor.getText());
            output.setText("Filas Afectadas: " + res.affectedRows() + "\nMensaje: " + res.message());
        });

        content.getChildren().addAll(editor, btnRun, output);
        tab.setContent(content);
        return tab;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
