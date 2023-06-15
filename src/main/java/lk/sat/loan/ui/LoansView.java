package lk.sat.loan.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.LoanStatus;
import lk.sat.loan.report.ReportService;
import lk.sat.loan.service.LoanBalance;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.util.Money;

public class LoansView extends VBox implements MainShell.Refreshable {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LoanService loans = new LoanService();
    private final ReportService reports = new ReportService();
    private final ObservableList<Loan> data = FXCollections.observableArrayList();
    private final TableView<Loan> table = new TableView<>(data);
    private final TextField search = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final Label summary = new Label();

    private Map<Long, LoanBalance> balances = new HashMap<>();

    public LoansView() {
        super(10);
        setPadding(new Insets(4));

        search.setPromptText("Search by loan number, client name, NIC...");
        search.setPrefWidth(320);
        search.setOnAction(e -> load());

        statusFilter.getItems().add("All");
        for (LoanStatus s : LoanStatus.values()) {
            statusFilter.getItems().add(s.display());
        }
        statusFilter.setValue("All");
        statusFilter.setOnAction(e -> load());

        Button searchBtn = new Button("Search");
        searchBtn.setOnAction(e -> load());

        Button add = new Button("New Loan");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> new LoanDialog(null).showAndWait(getScene().getWindow()).ifPresent(l -> load()));

        Button edit = new Button("Edit");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(e -> {
            Loan l = table.getSelectionModel().getSelectedItem();
            new LoanDialog(l).showAndWait(getScene().getWindow()).ifPresent(x -> load());
        });

        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger");
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(e -> {
            Loan l = table.getSelectionModel().getSelectedItem();
            if (Fx.confirm(getScene().getWindow(), "Delete loan " + l.getLoanNumber() + "?",
                    "This permanently deletes the loan and all its payments.")) {
                loans.delete(l.getId());
                load();
            }
        });

        Button pdf = new Button("Export PDF");
        pdf.setOnAction(e -> export(true));
        Button csv = new Button("Export CSV");
        csv.setOnAction(e -> export(false));

        buildColumns();
        table.setPlaceholder(new Label("No loans found."));
        VBox.setVgrow(table, Priority.ALWAYS);
        summary.getStyleClass().add("muted");

        getChildren().addAll(
                Fx.h1("Loans"),
                Fx.toolbar(search, statusFilter, searchBtn, Fx.spacer(), add, edit, delete, pdf, csv),
                table,
                summary);
    }

    private void buildColumns() {
        TableColumn<Loan, String> no = new TableColumn<>("Loan No");
        no.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLoanNumber()));
        no.setPrefWidth(90);

        TableColumn<Loan, String> client = new TableColumn<>("Client");
        client.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClientName()));
        client.setPrefWidth(180);

        TableColumn<Loan, String> center = new TableColumn<>("Center");
        center.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClient().getCenterName()));
        center.setPrefWidth(120);

        TableColumn<Loan, String> issue = new TableColumn<>("Issue Date");
        issue.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIssueDate().format(DATE)));
        issue.setPrefWidth(100);

        TableColumn<Loan, String> principal = Fx.rightAlign(new TableColumn<>("Principal"));
        principal.setCellValueFactory(c -> new SimpleStringProperty(Money.format(c.getValue().getPrincipal())));
        principal.setPrefWidth(110);

        TableColumn<Loan, String> bal = Fx.rightAlign(new TableColumn<>("Balance"));
        bal.setCellValueFactory(c -> {
            LoanBalance b = balances.getOrDefault(c.getValue().getId(), LoanBalance.empty());
            return new SimpleStringProperty(Money.format(b.balance()));
        });
        bal.setPrefWidth(110);

        TableColumn<Loan, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().display()));
        status.setPrefWidth(90);

        table.getColumns().setAll(List.of(no, client, center, issue, principal, bal, status));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Loan item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("arrears", "settled");
                if (item != null && !empty) {
                    LoanBalance b = balances.getOrDefault(item.getId(), LoanBalance.empty());
                    if (b.settled()) {
                        getStyleClass().add("settled");
                    } else if (b.inArrears()) {
                        getStyleClass().add("arrears");
                    }
                }
            }
        });
    }

    private void load() {
        String term = search.getText();
        String filter = statusFilter.getValue();

        List<Loan> results;
        if (!"All".equals(filter)) {
            LoanStatus s = LoanStatus.valueOf(filter.toUpperCase().replace(' ', '_'));
            results = loans.findByStatus(s);
        } else if (term != null && !term.isBlank()) {
            results = loans.search(term);
        } else {
            results = loans.findAll();
        }

        balances = loans.balances(results, LocalDate.now());
        data.setAll(results);
        summary.setText(results.size() + " loan(s)");
    }

    private void export(boolean pdf) {
        try {
            var t = reports.loanBook(LocalDate.now());
            var path = pdf ? reports.exportPdf(t) : reports.exportCsv(t);
            reports.open(path);
        } catch (Exception ex) {
            Fx.error(getScene().getWindow(), "Export failed", String.valueOf(ex.getMessage()));
        }
    }

    @Override
    public void refresh() {
        load();
    }
}
