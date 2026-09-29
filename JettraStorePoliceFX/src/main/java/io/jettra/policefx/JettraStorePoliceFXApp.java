package io.jettra.policefx;

import io.jettra.driver.JettraClient;
import io.jettra.policefx.world3d.ImmersiveWorld3D;
import io.jettra.policefx.world3d.JettraPoliceAgentMesh;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class JettraStorePoliceFXApp extends Application {
    private JettraClient client;
    private ImmersiveWorld3D world3D;
    private AnimationTimer timer;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JettraStorePoliceFX - Entorno de Monitoreo Preventivo 3D Inmersivo");

        this.client = JettraClient.connect("127.0.0.1", 9091, "admin", "admin-jettra");
        this.world3D = new ImmersiveWorld3D();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #020617;");

        // SubScene 3D
        SubScene subScene3D = new SubScene(world3D, 900, 650, true, SceneAntialiasing.BALANCED);
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-420);
        camera.setTranslateY(-80);
        camera.setNearClip(0.1);
        camera.setFarClip(1500.0);
        subScene3D.setCamera(camera);

        // Header Superior HUD
        HBox hudTop = new HBox(15);
        hudTop.setPadding(new Insets(12, 20, 12, 20));
        hudTop.setAlignment(Pos.CENTER_LEFT);
        hudTop.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-border-color: #0284C7; -fx-border-width: 0 0 1 0;");

        Label title = new Label("JETTRASTORE POLICE 3D MATRIX");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label status = new Label("Agente: PATRULLA PREVENTIVA ACTIVA");
        status.setStyle("-fx-background-color: #047857; -fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label session = new Label("Autenticado con JettraJWT: admin (SUPER_ADMIN)");
        session.setStyle("-fx-text-fill: #FACC15;");

        hudTop.getChildren().addAll(title, status, spacer, session);
        root.setTop(hudTop);

        // Panel Inferior de Control de Alertas
        HBox bottomControls = new HBox(15);
        bottomControls.setPadding(new Insets(10, 20, 10, 20));
        bottomControls.setAlignment(Pos.CENTER);
        bottomControls.setStyle("-fx-background-color: #0F172A;");

        Button btnNormal = new Button("Patrulla Normal");
        btnNormal.setOnAction(e -> {
            world3D.getAgentMesh().setStatus(JettraPoliceAgentMesh.AgentStatus.NORMAL);
            status.setText("Agente: PATRULLA NORMAL");
            status.setStyle("-fx-background-color: #047857; -fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");
        });

        Button btnRamAlert = new Button("Alerta Saturación RAM (Anillo)");
        btnRamAlert.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-font-weight: bold;");
        btnRamAlert.setOnAction(e -> {
            world3D.getAgentMesh().setStatus(JettraPoliceAgentMesh.AgentStatus.WARNING_RAM);
            status.setText("Agente: INTERVENCIÓN - SATURACIÓN DE MEMORIA");
            status.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");
        });

        Button btnSecurityAlert = new Button("Alerta Intrusión Superusuario");
        btnSecurityAlert.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSecurityAlert.setOnAction(e -> {
            world3D.getAgentMesh().setStatus(JettraPoliceAgentMesh.AgentStatus.CRITICAL_SECURITY);
            status.setText("Agente: ¡BLOQUEO DE INTRUSIÓN CRÍTICA!");
            status.setStyle("-fx-background-color: #DC2626; -fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");
        });

        bottomControls.getChildren().addAll(btnNormal, btnRamAlert, btnSecurityAlert);
        root.setBottom(bottomControls);
        root.setCenter(subScene3D);

        // Bucle de Animación continua 60 FPS
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                world3D.tickAnimation();
            }
        };
        timer.start();

        Scene scene = new Scene(root, 1050, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (timer != null) timer.stop();
        if (client != null) client.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
