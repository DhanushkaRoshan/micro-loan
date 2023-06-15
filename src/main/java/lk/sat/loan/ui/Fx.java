package lk.sat.loan.ui;

import java.time.LocalDate;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class Fx {

    private Fx() {
    }

    public static TextField moneyField() {
        TextField f = new TextField();
        f.setTextFormatter(new TextFormatter<>(c ->
                c.getControlNewText().matches("[0-9,]*\\.?[0-9]{0,2}") ? c : null));
        return f;
    }

    public static TextField rateField() {
        TextField f = new TextField();
        f.setTextFormatter(new TextFormatter<>(c ->
                c.getControlNewText().matches("[0-9]*\\.?[0-9]{0,3}") ? c : null));
        return f;
    }

    public static TextField intField() {
        TextField f = new TextField();
        f.setTextFormatter(new TextFormatter<>(c ->
                c.getControlNewText().matches("[0-9]*") ? c : null));
        return f;
    }

    public static DatePicker datePicker(LocalDate initial) {
        DatePicker dp = new DatePicker(initial);
        dp.setPrefWidth(160);
        return dp;
    }

    public static <S> TableColumn<S, String> rightAlign(TableColumn<S, String> col) {
        col.getStyleClass().add("numeric-column");
        return col;
    }

    public static boolean confirm(Window owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void warn(Window owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void error(Window owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static Label h1(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("h1");
        return l;
    }

    public static Label h2(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("h2");
        return l;
    }

    public static HBox toolbar(Node... children) {
        HBox box = new HBox(8, children);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 4, 0));
        return box;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(8, children);
        box.getStyleClass().add("card");
        return box;
    }
}
