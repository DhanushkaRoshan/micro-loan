package lk.sat.loan.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.Payment;
import lk.sat.loan.service.LoanBalance;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.service.PaymentService;
import lk.sat.loan.util.Money;

public class PaymentsView extends VBox implements MainShell.Refreshable {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LoanService loans = new LoanService();
    private final PaymentService payments = new PaymentService();

    private final ComboBox<Loan> loanPicker = new ComboBox<>();
    private final DatePicker date = Fx.datePicker(LocalDate.now());
    private final TextField amount = Fx.moneyField();
    private final TextField receipt = new TextField();
    private final TextField remark = new TextField();

    private final Label totalPayable = new Label("-");
    private final Label totalPaid = new Label("-");
    private final Label balance = new Label("-");
    private final Label arrears = new Label("-");
    private final Label error = new Label();

    private final ObservableList<Payment> history = FXCollections.observableArrayList();
    private final TableView<Payment> table = new TableView<>(history);

    public PaymentsView() {
        super(12);
        setPadding(new Insets(4));

        loanPicker.setPrefWidth(420);
        loanPicker.setVisibleRowCount(16);
        loanPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(Loan l) {
                return l == null ? "" : l.getLoanNumber() + "  -  " + l.getClientName()
                        + "  (" + l.getClient().getCenterName() + ")";
            }

            @Override
            public Loan fromString(String s) {
                return null;
            }
        });
        loanPicker.setOnAction(e -> loanChanged());

        amount.setPrefWidth(150);
        receipt.setPrefWidth(150);
        remark.setPrefWidth(260);

        Button post = new Button("Record Payment");
        post.getStyleClass().add("primary");
        post.setOnAction(e -> record());
        post.disableProperty().bind(loanPicker.valueProperty().isNull());

        Button payFull = new Button("Fill Installment");
        payFull.setOnAction(e -> {
            Loan l = loanPicker.getValue();
            if (l != null) {
                amount.setText(l.getInstallmentAmount().toPlainString());
            }
        });
        payFull.disableProperty().bind(loanPicker.valueProperty().isNull());

        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(9);
        form.addRow(0, new Label("Loan *"), loanPicker);
        form.addRow(1, new Label("Payment date *"), date);
        form.addRow(2, new Label("Amount (Rs) *"), new HBox(8, amount, payFull));
        form.addRow(3, new Label("Receipt number"), receipt);
        form.addRow(4, new Label("Remark"), remark);
        form.add(new HBox(8, post), 1, 5);
        form.add(error, 1, 6);

        for (Label l : List.of(totalPayable, totalPaid, balance, arrears)) {
            l.getStyleClass().add("h2");
        }
        GridPane figures = new GridPane();
        figures.setHgap(14);
        figures.setVgap(8);
        figures.addRow(0, new Label("Total payable"), totalPayable);
        figures.addRow(1, new Label("Total paid"), totalPaid);
        figures.addRow(2, new Label("Balance"), balance);
        figures.addRow(3, new Label("Arrears"), arrears);

        buildColumns();
        table.setPlaceholder(new Label("Select a loan to see its payment history."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button deletePayment = new Button("Delete Selected Payment");
        deletePayment.getStyleClass().add("danger");
        deletePayment.setOnAction(e -> deleteSelected());
        deletePayment.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        HBox top = new HBox(20, Fx.card(Fx.h2("Record a Payment"), form), Fx.card(Fx.h2("Position"), figures));
        getChildren().addAll(
                Fx.h1("Payments"),
                top,
                Fx.toolbar(Fx.h2("Payment History"), Fx.spacer(), deletePayment),
                table);
    }

    private void buildColumns() {
        TableColumn<Payment, String> d = new TableColumn<>("Date");
        d.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPaymentDate().format(DATE)));
        d.setPrefWidth(110);

        TableColumn<Payment, String> r = new TableColumn<>("Receipt");
        r.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getReceiptNo())));
        r.setPrefWidth(120);

        TableColumn<Payment, String> a = Fx.rightAlign(new TableColumn<>("Amount"));
        a.setCellValueFactory(c -> new SimpleStringProperty(Money.format(c.getValue().getAmount())));
        a.setPrefWidth(130);

        TableColumn<Payment, String> by = new TableColumn<>("Recorded By");
        by.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRecordedByName()));
        by.setPrefWidth(170);

        TableColumn<Payment, String> rem = new TableColumn<>("Remark");
        rem.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getRemark())));
        rem.setPrefWidth(260);

        table.getColumns().setAll(List.of(d, r, a, by, rem));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void record() {
        error.setText("");
        Loan l = loanPicker.getValue();
        if (l == null) {
            return;
        }
        try {
            payments.record(l.getId(), date.getValue(), Money.of(amount.getText()),
                    receipt.getText(), remark.getText(), Session.current());
            amount.clear();
            remark.clear();
            receipt.setText(payments.nextReceiptNo());
            reloadLoans(l.getId());
            loanChanged();
        } catch (IllegalArgumentException ex) {
            error.setText(ex.getMessage());
        }
    }

    private void deleteSelected() {
        Payment p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        if (!Fx.confirm(getScene().getWindow(), "Delete this payment?",
                "Removing the " + Money.format(p.getAmount()) + " payment dated "
                        + p.getPaymentDate().format(DATE) + " cannot be undone.")) {
            return;
        }
        Long loanId = loanPicker.getValue() == null ? null : loanPicker.getValue().getId();
        payments.delete(p.getId());
        reloadLoans(loanId);
        loanChanged();
    }

    private void loanChanged() {
        Loan l = loanPicker.getValue();
        error.setText("");
        if (l == null) {
            history.clear();
            totalPayable.setText("-");
            totalPaid.setText("-");
            balance.setText("-");
            arrears.setText("-");
            return;
        }
        LoanBalance b = loans.balanceOf(l.getId(), LocalDate.now());
        totalPayable.setText(Money.format(b.totalPayable()));
        totalPaid.setText(Money.format(b.totalPaid()));
        balance.setText(Money.format(b.balance()));
        arrears.setText(Money.format(b.arrears())
                + (b.arrearsMonths() > 0 ? "  (" + b.arrearsMonths() + " month(s))" : ""));
        history.setAll(payments.forLoan(l.getId()));
        if (receipt.getText().isBlank()) {
            receipt.setText(payments.nextReceiptNo());
        }
    }

    private void reloadLoans(Long selectedId) {
        List<Loan> all = loans.findAll();
        loanPicker.getItems().setAll(all);
        if (selectedId != null) {
            all.stream().filter(l -> l.getId().equals(selectedId)).findFirst()
                    .ifPresent(loanPicker::setValue);
        }
    }

    @Override
    public void refresh() {
        reloadLoans(null);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
