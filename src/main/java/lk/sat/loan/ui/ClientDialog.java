package lk.sat.loan.ui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lk.sat.loan.model.Center;
import lk.sat.loan.model.Client;
import lk.sat.loan.service.ClientService;
import lk.sat.loan.service.MasterDataService;

public class ClientDialog {

    private static final long MAX_PHOTO = 2 * 1024 * 1024;

    private final ClientService clients = new ClientService();
    private final MasterDataService master = new MasterDataService();
    private final Client existing;
    private byte[] photoBytes;

    public ClientDialog(Client existing) {
        this.existing = existing;
    }

    public Optional<Client> showAndWait(Window owner) {
        boolean creating = existing == null;
        Client model = creating ? new Client() : existing;

        Dialog<Client> dialog = new Dialog<>();
        dialog.setTitle(creating ? "New Client" : "Edit Client");
        dialog.setHeaderText(creating ? "Register a client" : "Edit " + model.getFullName());
        dialog.initOwner(owner);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        TextField code = new TextField(creating ? clients.nextClientCode() : model.getClientCode());
        TextField name = new TextField(nz(model.getFullName()));
        name.setPrefWidth(280);
        TextField nic = new TextField(nz(model.getNicNumber()));
        TextField address = new TextField(nz(model.getAddress()));
        address.setPrefWidth(320);
        TextField village = new TextField(nz(model.getVillage()));
        TextField phone = new TextField(nz(model.getPhone()));
        TextField waName = new TextField(nz(model.getWaName()));

        ComboBox<Center> center = new ComboBox<>();
        center.getItems().setAll(master.centers());
        center.setValue(model.getCenter());
        center.setPrefWidth(260);

        ImageView photo = new ImageView();
        photo.setFitWidth(100);
        photo.setFitHeight(100);
        photo.setPreserveRatio(true);
        if (!creating) {
            byte[] raw = clients.loadPhoto(model.getId());
            if (raw != null && raw.length > 0) {
                photo.setImage(new Image(new ByteArrayInputStream(raw)));
                photoBytes = raw;
            }
        }

        Button choosePhoto = new Button("Choose Photo");
        choosePhoto.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Client Photo");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
            var file = fc.showOpenDialog(dialog.getOwner());
            if (file != null) {
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    if (bytes.length > MAX_PHOTO) {
                        Fx.warn(dialog.getOwner(), "Photo too large",
                                "Maximum size is 2 MB. Choose a smaller image.");
                        return;
                    }
                    photoBytes = bytes;
                    photo.setImage(new Image(new ByteArrayInputStream(bytes)));
                } catch (IOException ex) {
                    Fx.error(dialog.getOwner(), "Cannot read file", ex.getMessage());
                }
            }
        });

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);
        grid.addRow(0, new Label("Client code *"), code);
        grid.addRow(1, new Label("Full name *"), name);
        grid.addRow(2, new Label("NIC number"), nic);
        grid.addRow(3, new Label("Address"), address);
        grid.addRow(4, new Label("Village"), village);
        grid.addRow(5, new Label("Phone"), phone);
        grid.addRow(6, new Label("WA name"), waName);
        grid.addRow(7, new Label("Center"), center);

        HBox photoBox = new HBox(10, photo, choosePhoto);
        VBox root = new VBox(14,
                Fx.card(Fx.h2("Client Details"), grid),
                Fx.card(Fx.h2("Photo"), photoBox),
                error);
        root.setPadding(new Insets(14));

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            error.setText("");
            String msg = validate(code.getText(), name.getText(), model.getId());
            if (msg != null) {
                error.setText(msg);
                e.consume();
            }
        });

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            model.setClientCode(code.getText().trim());
            model.setFullName(name.getText().trim());
            model.setNicNumber(blankToNull(nic.getText()));
            model.setAddress(blankToNull(address.getText()));
            model.setVillage(blankToNull(village.getText()));
            model.setPhone(blankToNull(phone.getText()));
            model.setWaName(blankToNull(waName.getText()));
            model.setCenter(center.getValue());
            if (photoBytes != null) {
                model.setPhoto(photoBytes);
            }
            return clients.save(model);
        });

        return dialog.showAndWait();
    }

    private String validate(String code, String name, Long id) {
        if (code == null || code.isBlank()) {
            return "A client code is required.";
        }
        if (name == null || name.isBlank()) {
            return "The client's full name is required.";
        }
        if (clients.codeTaken(code.trim(), id)) {
            return "Client code " + code.trim() + " is already in use.";
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
