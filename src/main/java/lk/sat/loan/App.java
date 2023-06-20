package lk.sat.loan;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import lk.sat.loan.service.AuthService;
import lk.sat.loan.ui.LoginView;
import lk.sat.loan.ui.MainShell;
import lk.sat.loan.util.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage stage) {
        try {
            Db.start();
            new AuthService().ensureSeedAdmin();
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            showFatal(e);
            return;
        }
        showLogin(stage);
    }

    private void showLogin(Stage stage) {
        LoginView login = new LoginView(user -> showMain(stage));
        Scene scene = new Scene(login, 980, 620);
        scene.getStylesheets().add(css());
        stage.setTitle("Sat Loan Management System");
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.centerOnScreen();
        stage.show();
    }

    private void showMain(Stage stage) {
        MainShell shell = new MainShell(() -> showLogin(stage));
        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().add(css());
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.centerOnScreen();
    }

    private static String css() {
        return App.class.getResource("/css/app.css").toExternalForm();
    }

    private void showFatal(Exception e) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Cannot start");
        a.setHeaderText("The database is not reachable.");
        a.setContentText(
                "Start it with:\n\n    docker compose up -d mysql\n\n"
                        + "Then run the application again.\n\nDetail: " + rootMessage(e));
        a.getDialogPane().setMinWidth(560);
        a.showAndWait();
        Platform.exit();
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getMessage() == null ? r.toString() : r.getMessage();
    }

    @Override
    public void stop() {
        Db.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
