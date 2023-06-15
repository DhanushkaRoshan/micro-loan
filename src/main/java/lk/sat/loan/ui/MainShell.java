package lk.sat.loan.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lk.sat.loan.util.Config;

public class MainShell extends BorderPane {

    public interface Refreshable {
        void refresh();
    }

    private final Runnable onLogout;
    private final List<Button> navButtons = new ArrayList<>();
    private Button activeButton;

    public MainShell(Runnable onLogout) {
        this.onLogout = onLogout;

        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 12, 16, 12));

        Label brand = new Label("Sat Loans");
        brand.getStyleClass().add("brand");
        Label sub = new Label("Microfinance Manager");
        sub.getStyleClass().add("brand-sub");

        sidebar.getChildren().addAll(brand, sub);
        sidebar.getChildren().add(new Region() {{ setPrefHeight(20); }});

        addNav(sidebar, "Dashboard", () -> show(new DashboardView()));
        addNav(sidebar, "Clients", () -> show(new ClientsView()));
        addNav(sidebar, "Loans", () -> show(new LoansView()));
        addNav(sidebar, "Payments", () -> show(new PaymentsView()));
        addNav(sidebar, "Arrears", () -> show(new ArrearsView()));
        addNav(sidebar, "Reports", () -> show(new ReportsView()));
        addNav(sidebar, "Master Data", () -> show(new MasterDataView()));

        if (Session.isAdmin()) {
            addNav(sidebar, "Users", () -> show(new UsersView()));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button changePassword = new Button("Change Password");
        changePassword.getStyleClass().add("nav-button");
        changePassword.setMaxWidth(Double.MAX_VALUE);
        changePassword.setOnAction(e -> {
            new ChangePasswordDialog().showAndWait(getScene().getWindow());
        });

        Button logout = new Button("Sign Out");
        logout.getStyleClass().add("nav-button");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> {
            Session.clear();
            onLogout.run();
        });

        Label userLabel = new Label();
        userLabel.getStyleClass().add("brand-sub");
        if (Session.current() != null) {
            userLabel.setText(Session.current().getFullName());
        }

        sidebar.getChildren().addAll(spacer, userLabel, changePassword, logout);
        setLeft(sidebar);

        HBox status = new HBox();
        status.getStyleClass().add("status-bar");
        Label version = new Label("v2.0");
        version.getStyleClass().add("muted");
        status.getChildren().add(version);
        setBottom(status);

        if (!navButtons.isEmpty()) {
            navButtons.get(0).fire();
        }
    }

    private void addNav(VBox sidebar, String label, Runnable action) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            if (activeButton != null) {
                activeButton.getStyleClass().remove("active");
            }
            btn.getStyleClass().add("active");
            activeButton = btn;
            action.run();
        });
        navButtons.add(btn);
        sidebar.getChildren().add(btn);
    }

    private void show(javafx.scene.Node view) {
        VBox wrapper = new VBox(view);
        wrapper.getStyleClass().add("content");
        wrapper.setPadding(new Insets(20));
        VBox.setVgrow(view, Priority.ALWAYS);
        setCenter(wrapper);
        if (view instanceof Refreshable r) {
            r.refresh();
        }
    }
}
