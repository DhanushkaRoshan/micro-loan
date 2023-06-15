package lk.sat.loan.ui;

import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lk.sat.loan.model.Role;
import lk.sat.loan.model.User;
import lk.sat.loan.service.AuthService;

public class UsersView extends VBox implements MainShell.Refreshable {

    private final AuthService auth = new AuthService();
    private final ObservableList<User> data = FXCollections.observableArrayList();
    private final TableView<User> table = new TableView<>(data);

    public UsersView() {
        super(10);
        setPadding(new Insets(4));

        Button add = new Button("New User");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> editUser(null));

        Button edit = new Button("Edit");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(e -> editUser(table.getSelectionModel().getSelectedItem()));

        Button toggle = new Button("Enable / Disable");
        toggle.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        toggle.setOnAction(e -> {
            User u = table.getSelectionModel().getSelectedItem();
            auth.setActive(u.getId(), !u.isActive());
            load();
        });

        buildColumns();
        table.setPlaceholder(new Label("No users found."));
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(
                Fx.h1("Users"),
                Fx.toolbar(add, edit, toggle),
                table);
    }

    private void buildColumns() {
        TableColumn<User, String> username = new TableColumn<>("Username");
        username.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        username.setPrefWidth(140);

        TableColumn<User, String> name = new TableColumn<>("Full Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        name.setPrefWidth(200);

        TableColumn<User, String> role = new TableColumn<>("Role");
        role.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole().display()));
        role.setPrefWidth(140);

        TableColumn<User, String> active = new TableColumn<>("Active");
        active.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Yes" : "No"));
        active.setPrefWidth(80);

        table.getColumns().setAll(List.of(username, name, role, active));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void editUser(User existing) {
        boolean creating = existing == null;
        User model = creating ? new User() : existing;

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(creating ? "New User" : "Edit User");
        dialog.setHeaderText(creating ? "Create a user account" : "Edit " + model.getUsername());
        dialog.initOwner(getScene().getWindow());
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());

        TextField username = new TextField(creating ? "" : model.getUsername());
        username.setPrefWidth(220);
        TextField fullName = new TextField(creating ? "" : model.getFullName());
        fullName.setPrefWidth(220);
        PasswordField password = new PasswordField();
        password.setPromptText(creating ? "Required" : "Leave blank to keep current");
        password.setPrefWidth(220);

        ComboBox<Role> role = new ComboBox<>();
        role.getItems().setAll(Role.values());
        role.setValue(model.getRole());

        Label error = new Label();
        error.getStyleClass().add("error-text");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);
        grid.setPadding(new Insets(14));
        grid.addRow(0, new Label("Username *"), username);
        grid.addRow(1, new Label("Full name *"), fullName);
        grid.addRow(2, new Label("Password" + (creating ? " *" : "")), password);
        grid.addRow(3, new Label("Role"), role);
        grid.add(error, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            error.setText("");
            if (username.getText().isBlank()) {
                error.setText("Username is required.");
                e.consume();
            } else if (fullName.getText().isBlank()) {
                error.setText("Full name is required.");
                e.consume();
            } else if (creating && password.getText().length() < 6) {
                error.setText("Password must be at least 6 characters.");
                e.consume();
            } else if (auth.usernameTaken(username.getText().trim(), model.getId())) {
                error.setText("Username is already taken.");
                e.consume();
            }
        });

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            model.setUsername(username.getText().trim());
            model.setFullName(fullName.getText().trim());
            model.setRole(role.getValue());
            String pw = password.getText().isEmpty() ? null : password.getText();
            return auth.save(model, pw);
        });

        dialog.showAndWait().ifPresent(u -> load());
    }

    private void load() {
        data.setAll(auth.findAll());
    }

    @Override
    public void refresh() {
        load();
    }
}
