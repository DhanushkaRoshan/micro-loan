package lk.sat.loan.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lk.sat.loan.model.Client;
import lk.sat.loan.service.ClientService;

public class ClientsView extends VBox implements MainShell.Refreshable {

    private final ClientService clients = new ClientService();
    private final ObservableList<Client> data = FXCollections.observableArrayList();
    private final TableView<Client> table = new TableView<>(data);
    private final TextField search = new TextField();

    public ClientsView() {
        super(10);
        setPadding(new Insets(4));

        search.setPromptText("Search by name, NIC, code or phone...");
        search.setPrefWidth(320);
        search.setOnAction(e -> load());

        Button searchBtn = new Button("Search");
        searchBtn.setOnAction(e -> load());

        Button add = new Button("New Client");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> new ClientDialog(null).showAndWait(getScene().getWindow()).ifPresent(c -> load()));

        Button edit = new Button("Edit");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(e -> {
            Client c = table.getSelectionModel().getSelectedItem();
            new ClientDialog(c).showAndWait(getScene().getWindow()).ifPresent(x -> load());
        });

        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(e -> {
            Client c = table.getSelectionModel().getSelectedItem();
            if (clients.loanCount(c.getId()) > 0) {
                Fx.warn(getScene().getWindow(), "Client has loans",
                        "Delete or reassign " + c.getFullName() + "'s loans first.");
                return;
            }
            if (Fx.confirm(getScene().getWindow(), "Delete " + c.getFullName() + "?",
                    "This cannot be undone.")) {
                clients.delete(c.getId());
                load();
            }
        });

        buildColumns();
        table.setPlaceholder(new Label("No clients found."));
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(
                Fx.h1("Clients"),
                Fx.toolbar(search, searchBtn, Fx.spacer(), add, edit, delete),
                table);
    }

    private void buildColumns() {
        TableColumn<Client, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClientCode()));
        code.setPrefWidth(80);

        TableColumn<Client, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        name.setPrefWidth(200);

        TableColumn<Client, String> nic = new TableColumn<>("NIC");
        nic.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getNicNumber())));
        nic.setPrefWidth(130);

        TableColumn<Client, String> phone = new TableColumn<>("Phone");
        phone.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getPhone())));
        phone.setPrefWidth(120);

        TableColumn<Client, String> center = new TableColumn<>("Center");
        center.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCenterName()));
        center.setPrefWidth(140);

        TableColumn<Client, String> village = new TableColumn<>("Village");
        village.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getVillage())));
        village.setPrefWidth(140);

        table.getColumns().setAll(java.util.List.of(code, name, nic, phone, center, village));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void load() {
        data.setAll(clients.search(search.getText()));
    }

    @Override
    public void refresh() {
        load();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
