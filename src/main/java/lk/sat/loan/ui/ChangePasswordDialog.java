package lk.sat.loan.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import lk.sat.loan.service.AuthService;

public class ChangePasswordDialog {

    private final AuthService auth = new AuthService();

    public void showAndWait(Window owner) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Change your password");
        dialog.initOwner(owner);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        PasswordField current = new PasswordField();
        current.setPrefWidth(220);
        PasswordField newPw = new PasswordField();
        newPw.setPrefWidth(220);
        PasswordField confirm = new PasswordField();
        confirm.setPrefWidth(220);

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);
        grid.setPadding(new Insets(14));
        grid.addRow(0, new Label("Current password"), current);
        grid.addRow(1, new Label("New password"), newPw);
        grid.addRow(2, new Label("Confirm"), confirm);
        grid.add(error, 1, 3);

        dialog.getDialogPane().setContent(grid);
        ButtonType change = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, change);

        var ok = dialog.getDialogPane().lookupButton(change);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            error.setText("");
            if (current.getText().isEmpty()) {
                error.setText("Enter your current password.");
                e.consume();
            } else if (newPw.getText().length() < 6) {
                error.setText("New password must be at least 6 characters.");
                e.consume();
            } else if (!newPw.getText().equals(confirm.getText())) {
                error.setText("Passwords do not match.");
                e.consume();
            } else if (Session.current() == null) {
                error.setText("Not signed in.");
                e.consume();
            } else {
                boolean ok2 = auth.changePassword(
                        Session.current().getId(), current.getText(), newPw.getText());
                if (!ok2) {
                    error.setText("Current password is incorrect.");
                    e.consume();
                }
            }
        });

        dialog.setResultConverter(bt -> bt == change);
        dialog.showAndWait();
    }
}
