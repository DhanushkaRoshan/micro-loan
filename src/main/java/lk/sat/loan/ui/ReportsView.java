package lk.sat.loan.ui;

import java.time.LocalDate;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lk.sat.loan.report.ReportService;
import lk.sat.loan.report.ReportTable;

public class ReportsView extends VBox implements MainShell.Refreshable {

    private final ReportService reports = new ReportService();

    private final ComboBox<String> reportType = new ComboBox<>();
    private final ComboBox<ReportService.GroupBy> groupBy = new ComboBox<>();
    private final DatePicker from = Fx.datePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker to = Fx.datePicker(LocalDate.now());
    private final Label summary = new Label();

    private final ObservableList<List<String>> rows = FXCollections.observableArrayList();
    private final TableView<List<String>> table = new TableView<>(rows);

    private ReportTable currentReport;

    public ReportsView() {
        super(10);
        setPadding(new Insets(4));

        reportType.getItems().addAll("Loan Book", "Collections", "Arrears", "Settled Loans");
        reportType.setValue("Loan Book");
        reportType.setOnAction(e -> updateVisibility());

        groupBy.getItems().setAll(ReportService.GroupBy.values());
        groupBy.setValue(ReportService.GroupBy.NONE);

        Button generate = new Button("Generate");
        generate.getStyleClass().add("primary");
        generate.setOnAction(e -> generate());

        Button pdf = new Button("Export PDF");
        pdf.setOnAction(e -> export(true));
        Button csv = new Button("Export CSV");
        csv.setOnAction(e -> export(false));

        summary.getStyleClass().add("muted");
        table.setPlaceholder(new Label("Choose a report and click Generate."));
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(
                Fx.h1("Reports"),
                Fx.toolbar(new Label("Report"), reportType,
                        new Label("From"), from, new Label("To"), to,
                        new Label("Group by"), groupBy,
                        generate),
                Fx.toolbar(pdf, csv, Fx.spacer(), summary),
                table);

        updateVisibility();
    }

    private void updateVisibility() {
        boolean collections = "Collections".equals(reportType.getValue());
        groupBy.setVisible(collections);
        groupBy.setManaged(collections);
    }

    private void generate() {
        LocalDate f = from.getValue() == null ? LocalDate.now().withDayOfMonth(1) : from.getValue();
        LocalDate t = to.getValue() == null ? LocalDate.now() : to.getValue();

        currentReport = switch (reportType.getValue()) {
            case "Loan Book" -> reports.loanBook(t);
            case "Collections" -> reports.collectionsGrouped(f, t, groupBy.getValue());
            case "Arrears" -> reports.arrears(t);
            case "Settled Loans" -> reports.settledLoans(t);
            default -> reports.loanBook(t);
        };

        showReport(currentReport);
    }

    private void showReport(ReportTable report) {
        table.getColumns().clear();
        rows.clear();

        for (int i = 0; i < report.columns().size(); i++) {
            final int col = i;
            TableColumn<List<String>, String> tc = new TableColumn<>(report.columns().get(col));
            tc.setCellValueFactory(c ->
                    new SimpleStringProperty(col < c.getValue().size() ? c.getValue().get(col) : ""));
            if (report.isNumeric(col)) {
                Fx.rightAlign(tc);
            }
            tc.setPrefWidth(120);
            table.getColumns().add(tc);
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        rows.setAll(report.rows());

        StringBuilder sb = new StringBuilder();
        sb.append(report.rows().size()).append(" row(s)");
        report.totals().forEach((k, v) -> sb.append("  |  ").append(k).append(": ").append(v));
        summary.setText(sb.toString());
    }

    private void export(boolean pdf) {
        if (currentReport == null) {
            return;
        }
        try {
            var path = pdf ? reports.exportPdf(currentReport) : reports.exportCsv(currentReport);
            reports.open(path);
        } catch (Exception ex) {
            Fx.error(getScene().getWindow(), "Export failed", String.valueOf(ex.getMessage()));
        }
    }

    @Override
    public void refresh() {
    }
}
