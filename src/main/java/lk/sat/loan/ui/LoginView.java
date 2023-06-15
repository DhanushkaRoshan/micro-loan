package lk.sat.loan.ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lk.sat.loan.model.User;
import lk.sat.loan.service.AuthService;

public class LoginView extends StackPane {

    private final AuthService auth = new AuthService();
    private final Consumer<User> onSuccess;

    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label error = new Label();

    public LoginView(Consumer<User> onSuccess) {
        this.onSuccess = onSuccess;
        getStyleClass().add("login-root");

        Label title = new Label("Sat Loan Management System");
        title.getStyleClass().add("login-title");

        username.setPromptText("Username");
        username.setPrefWidth(260);
        password.setPromptText("Password");
        password.setPrefWidth(260);
        password.setOnAction(e -> doLogin());

        Button login = new Button("Sign In");
        login.getStyleClass().add("primary");
        login.setPrefWidth(260);
        login.setOnAction(e -> doLogin());

        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        VBox card = new VBox(14, title, username, password, login, error);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(340);
        card.setPadding(new Insets(34, 40, 34, 40));

        setAlignment(Pos.CENTER);
        getChildren().add(card);
    }

    private void doLogin() {
        error.setText("");
        String u = username.getText().trim();
        String p = password.getText();
        if (u.isEmpty() || p.isEmpty()) {
            error.setText("Enter your username and password.");
            return;
        }
        auth.authenticate(u, p).ifPresentOrElse(
                user -> {
                    Session.set(user);
                    onSuccess.accept(user);
                },
                () -> error.setText("Invalid username or password."));
    }
}
