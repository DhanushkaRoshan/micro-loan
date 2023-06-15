package lk.sat.loan.ui;

import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lk.sat.loan.model.Area;
import lk.sat.loan.model.Center;
import lk.sat.loan.model.Coordinator;
import lk.sat.loan.service.MasterDataService;

public class MasterDataView extends TabPane implements MainShell.Refreshable {

    private final MasterDataService master = new MasterDataService();

    private final ObservableList<Area> areas = FXCollections.observableArrayList();
    private final ObservableList<Center> centers = FXCollections.observableArrayList();
    private final ObservableList<Coordinator> coordinators = FXCollections.observableArrayList();

    private final TableView<Area> areaTable = new TableView<>(areas);
    private final TableView<Center> centerTable = new TableView<>(centers);
    private final TableView<Coordinator> coordinatorTable = new TableView<>(coordinators);

    public MasterDataView() {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getTabs().addAll(areaTab(), centerTab(), coordinatorTab());
    }

    // ---- Areas ---------------------------------------------------------

    private Tab areaTab() {
        TableColumn<Area, String> name = new TableColumn<>("Area");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        name.setPrefWidth(320);
        areaTable.getColumns().setAll(List.of(name));
        areaTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        areaTable.setPlaceholder(new Label("No areas yet."));

        Button add = new Button("New Area");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> {
            promptName("New Area", "Area name", "").ifPresent(n -> {
                master.saveArea(new Area(n));
                load();
            });
        });

        Button rename = new Button("Rename");
        rename.disableProperty().bind(areaTable.getSelectionModel().selectedItemProperty().isNull());
        rename.setOnAction(e -> {
            Area a = areaTable.getSelectionModel().getSelectedItem();
            promptName("Rename Area", "Area name", a.getName()).ifPresent(n -> {
                a.setName(n);
                master.saveArea(a);
                load();
            });
        });

        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(areaTable.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(e -> {
            Area a = areaTable.getSelectionModel().getSelectedItem();
            if (master.areaInUse(a.getId())) {
                Fx.warn(getScene().getWindow(), "Area is in use",
                        "Centers still reference “" + a.getName() + "”. Move or delete those centers first.");
                return;
            }
            if (Fx.confirm(getScene().getWindow(), "Delete " + a.getName() + "?", "This cannot be undone.")) {
                master.deleteArea(a.getId());
                load();
            }
        });

        return tab("Areas", areaTable, add, rename, delete);
    }

    // ---- Centers -------------------------------------------------------

    private Tab centerTab() {
        TableColumn<Center, String> name = new TableColumn<>("Center");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        name.setPrefWidth(260);

        TableColumn<Center, String> area = new TableColumn<>("Area");
        area.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getArea() == null ? "" : c.getValue().getArea().getName()));
        area.setPrefWidth(260);

        centerTable.getColumns().setAll(List.of(name, area));
        centerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        centerTable.setPlaceholder(new Label("No centers yet."));

        Button add = new Button("New Center");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> editCenter(null));

        Button edit = new Button("Edit");
        edit.disableProperty().bind(centerTable.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(e -> editCenter(centerTable.getSelectionModel().getSelectedItem()));

        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(centerTable.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(e -> {
            Center c = centerTable.getSelectionModel().getSelectedItem();
            if (master.centerInUse(c.getId())) {
                Fx.warn(getScene().getWindow(), "Center is in use",
                        "Clients are still assigned to “" + c.getName() + "”. Reassign them first.");
                return;
            }
            if (Fx.confirm(getScene().getWindow(), "Delete " + c.getName() + "?", "This cannot be undone.")) {
                master.deleteCenter(c.getId());
                load();
            }
        });

        return tab("Centers", centerTable, add, edit, delete);
    }

    private void editCenter(Center existing) {
        boolean creating = existing == null;
        Center model = creating ? new Center() : existing;

        Dialog<Center> dialog = new Dialog<>();
        dialog.setTitle(creating ? "New Center" : "Edit Center");
        dialog.setHeaderText(creating ? "Add a center" : "Edit " + model.getName());
        dialog.initOwner(getScene().getWindow());
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());

        TextField name = new TextField(model.getName() == null ? "" : model.getName());
        name.setPrefWidth(240);
        ComboBox<Area> area = new ComboBox<>();
        area.getItems().setAll(master.areas());
        area.setValue(model.getArea());
        area.setPrefWidth(240);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);
        grid.setPadding(new Insets(14));
        grid.addRow(0, new Label("Center name *"), name);
        grid.addRow(1, new Label("Area"), area);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.getDialogPane().lookupButton(ButtonType.OK)
                .addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                    if (name.getText() == null || name.getText().isBlank()) {
                        e.consume();
                    }
                });

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            model.setName(name.getText().trim());
            model.setArea(area.getValue());
            return master.saveCenter(model);
        });
        dialog.showAndWait().ifPresent(c -> load());
    }

    // ---- Coordinators --------------------------------------------------

    private Tab coordinatorTab() {
        TableColumn<Coordinator, String> name = new TableColumn<>("Coordinator");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        name.setPrefWidth(300);

        TableColumn<Coordinator, String> phone = new TableColumn<>("Phone");
        phone.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPhone() == null ? "" : c.getValue().getPhone()));
        phone.setPrefWidth(200);

        coordinatorTable.getColumns().setAll(List.of(name, phone));
        coordinatorTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        coordinatorTable.setPlaceholder(new Label("No coordinators yet."));

        Button add = new Button("New Coordinator");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> promptName("New Coordinator", "Coordinator name", "").ifPresent(n -> {
            master.saveCoordinator(new Coordinator(n));
            load();
        }));

        Button rename = new Button("Rename");
        rename.disableProperty().bind(coordinatorTable.getSelectionModel().selectedItemProperty().isNull());
        rename.setOnAction(e -> {
            Coordinator c = coordinatorTable.getSelectionModel().getSelectedItem();
            promptName("Rename Coordinator", "Coordinator name", c.getName()).ifPresent(n -> {
                c.setName(n);
                master.saveCoordinator(c);
                load();
            });
        });

        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(coordinatorTable.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(e -> {
            Coordinator c = coordinatorTable.getSelectionModel().getSelectedItem();
            if (master.coordinatorInUse(c.getId())) {
                Fx.warn(getScene().getWindow(), "Coordinator is in use",
                        "Loans still reference “" + c.getName() + "”. Reassign those loans first.");
                return;
            }
            if (Fx.confirm(getScene().getWindow(), "Delete " + c.getName() + "?", "This cannot be undone.")) {
                master.deleteCoordinator(c.getId());
                load();
            }
        });

        return tab("Coordinators", coordinatorTable, add, rename, delete);
    }

    // ---- shared --------------------------------------------------------

    private Tab tab(String title, TableView<?> table, Button... buttons) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().addAll(Fx.toolbar(buttons), table);
        return new Tab(title, box);
    }

    private Optional<String> promptName(String title, String label, String initial) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(label);
        dialog.initOwner(getScene().getWindow());
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isBlank());
    }

    private void load() {
        areas.setAll(master.areas());
        centers.setAll(master.centers());
        coordinators.setAll(master.coordinators());
    }

    @Override
    public void refresh() {
        load();
    }
}
