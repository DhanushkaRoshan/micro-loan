package lk.sat.loan.tools;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.sat.loan.model.Role;
import lk.sat.loan.model.User;
import lk.sat.loan.ui.ArrearsView;
import lk.sat.loan.ui.ClientsView;
import lk.sat.loan.ui.DashboardView;
import lk.sat.loan.ui.LoansView;
import lk.sat.loan.ui.LoginView;
import lk.sat.loan.ui.MainShell;
import lk.sat.loan.ui.MasterDataView;
import lk.sat.loan.ui.PaymentsView;
import lk.sat.loan.ui.ReportsView;
import lk.sat.loan.ui.Session;
import lk.sat.loan.ui.UsersView;
import lk.sat.loan.util.Db;

public class UiSnapshot extends Application {

    private static Path outDir = Path.of("target/screenshots");

    public static void main(String[] args) {
        if (args.length > 0) {
            outDir = Path.of(args[0]);
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Db.start();
        Session.set(adminUser());
        outDir.toFile().mkdirs();

        Map<String, Parent> screens = new LinkedHashMap<>();
        screens.put("01-login", new LoginView(u -> { }));
        screens.put("02-dashboard", wrap(new DashboardView()));
        screens.put("03-clients", wrap(new ClientsView()));
        screens.put("04-loans", wrap(new LoansView()));
        screens.put("05-payments", wrap(new PaymentsView()));
        screens.put("06-arrears", wrap(new ArrearsView()));
        screens.put("07-reports", wrap(new ReportsView()));
        screens.put("08-master-data", wrap(new MasterDataView()));
        screens.put("09-users", wrap(new UsersView()));
        screens.put("10-shell", new MainShell(() -> { }));

        int written = 0;
        for (var entry : screens.entrySet()) {
            Scene scene = new Scene(entry.getValue(), 1280, 800);
            scene.getStylesheets().add(
                    UiSnapshot.class.getResource("/css/app.css").toExternalForm());
            stage.setScene(scene);
            stage.show();

            WritableImage image = scene.snapshot(null);
            File target = outDir.resolve(entry.getKey() + ".png").toFile();
            javax.imageio.ImageIO.write(
                    SwingFXUtils.fromFXImage(image, null), "png", target);
            System.out.println("wrote " + target.getPath());
            written++;
        }

        System.out.println("\n" + written + " screen(s) rendered without error.");
        Db.stop();
        stage.hide();
        Platform.exit();
    }

    private static Parent wrap(javafx.scene.Node view) {
        VBox box = new VBox(view);
        javafx.scene.layout.VBox.setVgrow(view, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private static User adminUser() {
        User u = new User();
        u.setId(0L);
        u.setUsername("snapshot");
        u.setFullName("Screenshot Bot");
        u.setRole(Role.ADMIN);
        return u;
    }
}
