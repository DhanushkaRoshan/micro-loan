package lk.sat.loan.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import lk.sat.loan.model.Client;
import lk.sat.loan.model.Coordinator;
import lk.sat.loan.model.Guarantor;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.LoanStatus;
import lk.sat.loan.service.ClientService;
import lk.sat.loan.service.LoanMath;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.service.MasterDataService;
import lk.sat.loan.util.Money;

public class LoanDialog {

    private final LoanService loans = new LoanService();
    private final ClientService clients = new ClientService();
    private final MasterDataService master = new MasterDataService();
    private final Loan existing;

    private final ObservableList<Guarantor> guarantors = FXCollections.observableArrayList();

    public LoanDialog(Loan existing) {
        this.existing = existing;
    }

    public Optional<Loan> showAndWait(Window owner) {
        boolean creating = existing == null;
        Loan model = creating
                ? new Loan()
                : loans.findDetailed(existing.getId()).orElse(existing);

        Dialog<Loan> dialog = new Dialog<>();
        dialog.setTitle(creating ? "New Loan" : "Edit Loan");
        dialog.setHeaderText(creating ? "Issue a loan" : "Edit loan " + model.getLoanNumber());
        dialog.initOwner(owner);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        // ---- identification -------------------------------------------
        TextField loanNo = new TextField(creating ? loans.nextLoanNumber() : model.getLoanNumber());
        ComboBox<Client> client = new ComboBox<>();
        client.getItems().setAll(clients.findAll());
        client.setValue(model.getClient());
        client.setPrefWidth(280);
        client.setVisibleRowCount(14);

        ComboBox<Coordinator> coordinator = new ComboBox<>();
        coordinator.getItems().setAll(master.coordinators());
        coordinator.setValue(model.getCoordinator());
        coordinator.setPrefWidth(280);

        DatePicker issueDate = Fx.datePicker(creating ? LocalDate.now() : model.getIssueDate());
        DatePicker firstDue = Fx.datePicker(creating
                ? LocalDate.now().plusMonths(1)
                : (model.getFirstDueDate() != null ? model.getFirstDueDate() : model.getIssueDate().plusMonths(1)));

        ComboBox<String> groupCode = new ComboBox<>();
        groupCode.getItems().add("");
        for (char c = 'A'; c <= 'L'; c++) {
            groupCode.getItems().add(String.valueOf(c));
        }
        groupCode.setValue(model.getGroupCode() == null ? "" : model.getGroupCode());

        ComboBox<LoanStatus> status = new ComboBox<>();
        status.getItems().setAll(LoanStatus.values());
        status.setValue(model.getStatus());

        TextField description = new TextField(nz(model.getDescription()));
        TextField remarks = new TextField(nz(model.getRemarks()));

        // ---- terms ----------------------------------------------------
        TextField principal = Fx.moneyField();
        principal.setText(creating ? "" : model.getPrincipal().toPlainString());
        TextField rate = Fx.rateField();
        rate.setText(creating ? "" : model.getInterestRate().toPlainString());
        TextField months = Fx.intField();
        months.setText(creating ? "" : String.valueOf(model.getInstallmentCount()));
        TextField deduction = Fx.moneyField();
        deduction.setText(creating ? "" : model.getDeduction().toPlainString());

        Label interestOut = new Label("0.00");
        Label installmentOut = new Label("0.00");
        Label totalOut = new Label("0.00");
        interestOut.getStyleClass().add("h2");
        installmentOut.getStyleClass().add("h2");
        totalOut.getStyleClass().add("h2");

        Runnable recalc = () -> {
            BigDecimal p = Money.of(principal.getText());
            BigDecimal r = parseRate(rate.getText());
            int m = parseInt(months.getText());
            interestOut.setText(Money.format(LoanMath.interestForTerm(p, r, m)));
            installmentOut.setText(Money.format(LoanMath.installmentAmount(p, r, m)));
            totalOut.setText(Money.format(LoanMath.totalPayable(p, r, m)));
        };
        principal.textProperty().addListener((o, a, b) -> recalc.run());
        rate.textProperty().addListener((o, a, b) -> recalc.run());
        months.textProperty().addListener((o, a, b) -> recalc.run());
        recalc.run();

        GridPane terms = new GridPane();
        terms.setHgap(10);
        terms.setVgap(9);
        terms.addRow(0, new Label("Principal (Rs) *"), principal);
        terms.addRow(1, new Label("Interest rate (% p.a.) *"), rate);
        terms.addRow(2, new Label("Installments (months) *"), months);
        terms.addRow(3, new Label("Deduction at issue (Rs)"), deduction);
        terms.addRow(4, new Label("Interest for term"), interestOut);
        terms.addRow(5, new Label("Installment amount"), installmentOut);
        terms.addRow(6, new Label("Total payable"), totalOut);

        // ---- guarantors ------------------------------------------------
        guarantors.setAll(creating ? List.of() : new ArrayList<>(model.getGuarantors()));
        TableView<Guarantor> gTable = new TableView<>(guarantors);
        gTable.setEditable(true);
        gTable.setPrefHeight(150);
        gTable.setPlaceholder(new Label("No guarantors. Use “Add Guarantor”."));
        gTable.getColumns().setAll(List.of(
                editableColumn("Name", "fullName", 190, Guarantor::setFullName),
                editableColumn("NIC", "nicNumber", 120, Guarantor::setNicNumber),
                editableColumn("Address", "address", 190, Guarantor::setAddress),
                editableColumn("Phone", "phone", 110, Guarantor::setPhone)));
        gTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button addG = new Button("Add Guarantor");
        addG.setOnAction(e -> {
            Guarantor g = new Guarantor();
            g.setFullName("New guarantor");
            guarantors.add(g);
            gTable.getSelectionModel().selectLast();
            gTable.edit(guarantors.size() - 1, gTable.getColumns().get(0));
        });
        Button removeG = new Button("Remove");
        removeG.getStyleClass().add("danger");
        removeG.setOnAction(e -> {
            Guarantor g = gTable.getSelectionModel().getSelectedItem();
            if (g != null) {
                guarantors.remove(g);
            }
        });
        removeG.disableProperty().bind(gTable.getSelectionModel().selectedItemProperty().isNull());

        // ---- layout ----------------------------------------------------
        GridPane head = new GridPane();
        head.setHgap(10);
        head.setVgap(9);
        head.addRow(0, new Label("Loan number *"), loanNo);
        head.addRow(1, new Label("Client *"), client);
        head.addRow(2, new Label("Coordinator"), coordinator);
        head.addRow(3, new Label("Issue date *"), issueDate);
        head.addRow(4, new Label("First due date"), firstDue);
        head.addRow(5, new Label("Group code"), groupCode);
        head.addRow(6, new Label("Status"), status);
        head.addRow(7, new Label("Description"), description);
        head.addRow(8, new Label("Remarks"), remarks);

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        error.setMaxWidth(700);

        HBox columns = new HBox(24, Fx.card(Fx.h2("Loan"), head), Fx.card(Fx.h2("Terms"), terms));
        VBox root = new VBox(14,
                columns,
                Fx.card(Fx.h2("Guarantors"), gTable, new HBox(8, addG, removeG)),
                error);
        root.setPadding(new Insets(14));
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            error.setText("");
            String msg = validate(loanNo.getText(), client.getValue(), issueDate.getValue(),
                    principal.getText(), months.getText(), model.getId());
            if (msg != null) {
                error.setText(msg);
                e.consume();
            }
        });

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            BigDecimal p = Money.of(principal.getText());
            BigDecimal r = parseRate(rate.getText());
            int m = parseInt(months.getText());

            model.setLoanNumber(loanNo.getText().trim());
            model.setClient(client.getValue());
            model.setCoordinator(coordinator.getValue());
            model.setIssueDate(issueDate.getValue());
            model.setFirstDueDate(firstDue.getValue());
            model.setGroupCode(groupCode.getValue() == null || groupCode.getValue().isBlank()
                    ? null : groupCode.getValue());
            model.setStatus(status.getValue());
            model.setDescription(blankToNull(description.getText()));
            model.setRemarks(blankToNull(remarks.getText()));
            model.setPrincipal(p);
            model.setInterestRate(r);
            model.setInstallmentCount(m);
            model.setDeduction(Money.of(deduction.getText()));
            model.setInterestAmount(LoanMath.interestForTerm(p, r, m));
            model.setInstallmentAmount(LoanMath.installmentAmount(p, r, m));
            model.setTotalPayable(LoanMath.totalPayable(p, r, m));

            model.getGuarantors().clear();
            for (Guarantor g : guarantors) {
                Guarantor copy = new Guarantor();
                copy.setFullName(g.getFullName());
                copy.setNicNumber(blankToNull(g.getNicNumber()));
                copy.setAddress(blankToNull(g.getAddress()));
                copy.setPhone(blankToNull(g.getPhone()));
                model.addGuarantor(copy);
            }
            return loans.save(model, true);
        });

        return dialog.showAndWait();
    }

    private TableColumn<Guarantor, String> editableColumn(
            String title, String property, int width, java.util.function.BiConsumer<Guarantor, String> setter) {
        TableColumn<Guarantor, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setPrefWidth(width);
        col.setOnEditCommit(e -> setter.accept(e.getRowValue(), e.getNewValue()));
        return col;
    }

    private String validate(String loanNo, Client client, LocalDate issue,
                            String principal, String months, Long id) {
        if (loanNo == null || loanNo.isBlank()) {
            return "A loan number is required.";
        }
        if (client == null) {
            return "Select the client this loan is issued to. Register the client first if they are new.";
        }
        if (issue == null) {
            return "An issue date is required.";
        }
        if (Money.of(principal).signum() <= 0) {
            return "The principal must be greater than zero.";
        }
        if (parseInt(months) <= 0) {
            return "Enter the number of monthly installments.";
        }
        if (loans.loanNumberTaken(loanNo.trim(), id)) {
            return "Loan number " + loanNo.trim() + " already exists.";
        }
        return null;
    }

    private static BigDecimal parseRate(String s) {
        if (s == null || s.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static int parseInt(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
