package lk.sat.loan.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Pair;
import lk.sat.loan.model.Loan;
import lk.sat.loan.report.ReportService;
import lk.sat.loan.service.LoanBalance;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.util.Money;

public class ArrearsView extends VBox implements MainShell.Refreshable {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LoanService loans = new LoanService();
    private final ReportService reports = new ReportService();
    private final ObservableList<Pair<Loan, LoanBalance>> data = FXCollections.observableArrayList();
    private final TableView<Pair<Loan, LoanBalance>> table = new TableView<>(data);
    private final DatePicker asOf = Fx.datePicker(LocalDate.now());
    private final Label summary = new Label();

    public ArrearsView() {
        super(10);
        setPadding(new Insets(4));

        asOf.setOnAction(e -> load());

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> load());

        Button pdf = new Button("Export PDF");
        pdf.getStyleClass().add("primary");
        pdf.setOnAction(e -> export(true));

        Button csv = new Button("Export CSV");
        csv.setOnAction(e -> export(false));

        buildColumns();
        table.setPlaceholder(new Label("No loan is in arrears as at this date."));
        VBox.setVgrow(table, Priority.ALWAYS);

        summary.getStyleClass().add("muted");
        getChildren().addAll(
                Fx.h1("Arrears"),
                Fx.toolbar(new Label("As at"), asOf, refresh, pdf, csv, Fx.spacer(), summary),
                table);
    }

    private void buildColumns() {
        TableColumn<Pair<Loan, LoanBalance>, String> no = new TableColumn<>("Loan No");
        no.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey().getLoanNumber()));
        no.setPrefWidth(100);

        TableColumn<Pair<Loan, LoanBalance>, String> client = new TableColumn<>("Client");
        client.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey().getClientName()));
        client.setPrefWidth(200);

        TableColumn<Pair<Loan, LoanBalance>, String> center = new TableColumn<>("Center");
        center.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getKey().getClient().getCenterName()));
        center.setPrefWidth(140);

        TableColumn<Pair<Loan, LoanBalance>, String> coord = new TableColumn<>("Coordinator");
        coord.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey().getCoordinatorName()));
        coord.setPrefWidth(140);

        TableColumn<Pair<Loan, LoanBalance>, String> inst = Fx.rightAlign(new TableColumn<>("Installment"));
        inst.setCellValueFactory(c ->
                new SimpleStringProperty(Money.format(c.getValue().getKey().getInstallmentAmount())));
        inst.setPrefWidth(110);

        TableColumn<Pair<Loan, LoanBalance>, String> due = Fx.rightAlign(new TableColumn<>("Due to Date"));
        due.setCellValueFactory(c -> new SimpleStringProperty(Money.format(c.getValue().getValue().dueToDate())));
        due.setPrefWidth(115);

        TableColumn<Pair<Loan, LoanBalance>, String> paid = Fx.rightAlign(new TableColumn<>("Paid"));
        paid.setCellValueFactory(c -> new SimpleStringProperty(Money.format(c.getValue().getValue().totalPaid())));
        paid.setPrefWidth(110);

        TableColumn<Pair<Loan, LoanBalance>, String> arr = Fx.rightAlign(new TableColumn<>("Arrears"));
        arr.setCellValueFactory(c -> new SimpleStringProperty(Money.format(c.getValue().getValue().arrears())));
        arr.setPrefWidth(115);

        TableColumn<Pair<Loan, LoanBalance>, String> months = Fx.rightAlign(new TableColumn<>("Months"));
        months.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getValue().arrearsMonths())));
        months.setPrefWidth(80);

        table.getColumns().setAll(List.of(no, client, center, coord, inst, due, paid, arr, months));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void load() {
        LocalDate when = asOf.getValue() == null ? LocalDate.now() : asOf.getValue();
        List<Loan> all = loans.findAll();
        Map<Long, LoanBalance> balances = loans.balances(all, when);

        List<Pair<Loan, LoanBalance>> rows = new ArrayList<>();
        BigDecimal total = Money.ZERO;
        for (Loan l : all) {
            LoanBalance b = balances.getOrDefault(l.getId(), LoanBalance.empty());
            if (b.inArrears()) {
                rows.add(new Pair<>(l, b));
                total = total.add(b.arrears());
            }
        }
        rows.sort((x, y) -> y.getValue().arrears().compareTo(x.getValue().arrears()));
        data.setAll(rows);
        summary.setText(rows.size() + " loan(s) in arrears  |  total " + Money.format(total));
    }

    private void export(boolean pdf) {
        LocalDate when = asOf.getValue() == null ? LocalDate.now() : asOf.getValue();
        try {
            var t = reports.arrears(when);
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
