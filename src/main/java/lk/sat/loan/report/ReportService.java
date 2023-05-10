package lk.sat.loan.report;

import java.awt.Desktop;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.Payment;
import lk.sat.loan.service.LoanBalance;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.service.PaymentService;
import lk.sat.loan.util.Config;
import lk.sat.loan.util.Money;

public class ReportService {

    public enum GroupBy {
        NONE("None"),
        CENTER("Center"),
        AREA("Area"),
        COORDINATOR("Coordinator");

        private final String label;

        GroupBy(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LoanService loanService = new LoanService();
    private final PaymentService paymentService = new PaymentService();

    public ReportTable loanBook(LocalDate asOf) {
        List<Loan> loans = loanService.findAll();
        Map<Long, LoanBalance> balances = loanService.balances(loans, asOf);

        ReportTable t = new ReportTable("Loan Book")
                .subtitle("As at " + asOf.format(DATE))
                .column("Loan No").column("Client").column("NIC").column("Center")
                .column("Issue Date").column("Principal", true).column("Total Payable", true)
                .column("Paid", true).column("Balance", true).column("Status");

        BigDecimal principal = Money.ZERO;
        BigDecimal payable = Money.ZERO;
        BigDecimal paid = Money.ZERO;
        for (Loan l : loans) {
            LoanBalance b = balances.getOrDefault(l.getId(), LoanBalance.empty());
            t.row(l.getLoanNumber(), l.getClientName(), nz(l.getClient().getNicNumber()),
                    l.getClient().getCenterName(), l.getIssueDate().format(DATE),
                    Money.format(l.getPrincipal()), Money.format(l.getTotalPayable()),
                    Money.format(b.totalPaid()), Money.format(b.balance()), l.getStatus().display());
            principal = principal.add(Money.nz(l.getPrincipal()));
            payable = payable.add(Money.nz(l.getTotalPayable()));
            paid = paid.add(b.totalPaid());
        }
        return t.total("Loans", String.valueOf(loans.size()))
                .total("Total Principal", Money.format(principal))
                .total("Total Payable", Money.format(payable))
                .total("Total Collected", Money.format(paid))
                .total("Outstanding", Money.format(Money.floorZero(payable.subtract(paid))));
    }

    public ReportTable arrears(LocalDate asOf) {
        List<Loan> loans = loanService.findAll();
        Map<Long, LoanBalance> balances = loanService.balances(loans, asOf);

        ReportTable t = new ReportTable("Arrears Report")
                .subtitle("As at " + asOf.format(DATE))
                .column("Loan No").column("Client").column("Center").column("Coordinator")
                .column("Installment", true).column("Due to Date", true).column("Paid", true)
                .column("Arrears", true).column("Months", true);

        BigDecimal arrears = Money.ZERO;
        int count = 0;
        for (Loan l : loans) {
            LoanBalance b = balances.getOrDefault(l.getId(), LoanBalance.empty());
            if (!b.inArrears()) {
                continue;
            }
            count++;
            arrears = arrears.add(b.arrears());
            t.row(l.getLoanNumber(), l.getClientName(), l.getClient().getCenterName(),
                    l.getCoordinatorName(), Money.format(l.getInstallmentAmount()),
                    Money.format(b.dueToDate()), Money.format(b.totalPaid()),
                    Money.format(b.arrears()), String.valueOf(b.arrearsMonths()));
        }
        return t.total("Loans in Arrears", String.valueOf(count))
                .total("Total Arrears", Money.format(arrears));
    }

    public ReportTable collections(LocalDate from, LocalDate to) {
        List<Payment> payments = paymentService.between(from, to);
        ReportTable t = new ReportTable("Collection Report")
                .subtitle(from.format(DATE) + " to " + to.format(DATE))
                .column("Date").column("Receipt").column("Loan No").column("Client")
                .column("Center").column("Amount", true).column("Recorded By").column("Remark");

        BigDecimal total = Money.ZERO;
        for (Payment p : payments) {
            total = total.add(Money.nz(p.getAmount()));
            t.row(p.getPaymentDate().format(DATE), nz(p.getReceiptNo()),
                    p.getLoan().getLoanNumber(), p.getLoan().getClientName(),
                    p.getLoan().getClient().getCenterName(), Money.format(p.getAmount()),
                    p.getRecordedByName(), nz(p.getRemark()));
        }
        return t.total("Receipts", String.valueOf(payments.size()))
                .total("Total Collected", Money.format(total));
    }

    public ReportTable collectionsGrouped(LocalDate from, LocalDate to, GroupBy groupBy) {
        if (groupBy == GroupBy.NONE) {
            return collections(from, to);
        }
        List<Payment> payments = paymentService.between(from, to);
        Function<Payment, String> key = switch (groupBy) {
            case CENTER -> p -> blankToDash(p.getLoan().getClient().getCenterName());
            case AREA -> p -> blankToDash(p.getLoan().getClient().getAreaName());
            case COORDINATOR -> p -> blankToDash(p.getLoan().getCoordinatorName());
            case NONE -> p -> "";
        };

        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Payment p : payments) {
            String k = key.apply(p);
            sums.merge(k, Money.nz(p.getAmount()), BigDecimal::add);
            counts.merge(k, 1, Integer::sum);
        }

        List<String> keys = new ArrayList<>(sums.keySet());
        keys.sort(String::compareToIgnoreCase);

        ReportTable t = new ReportTable("Collection Summary by " + groupBy)
                .subtitle(from.format(DATE) + " to " + to.format(DATE))
                .column(groupBy.toString()).column("Receipts", true).column("Total Collected", true);

        BigDecimal total = Money.ZERO;
        for (String k : keys) {
            t.row(k, String.valueOf(counts.get(k)), Money.format(sums.get(k)));
            total = total.add(sums.get(k));
        }
        return t.total("Groups", String.valueOf(keys.size()))
                .total("Total Collected", Money.format(total));
    }

    public ReportTable loanStatement(Long loanId, LocalDate asOf) {
        Loan l = loanService.findDetailed(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        LoanBalance b = loanService.balanceOf(loanId, asOf);

        ReportTable t = new ReportTable("Loan Statement - " + l.getLoanNumber())
                .subtitle(l.getClientName() + "  |  " + nz(l.getClient().getNicNumber())
                        + "  |  " + l.getClient().getCenterName())
                .column("#", true).column("Date").column("Receipt").column("Amount", true)
                .column("Running Total", true).column("Remark");

        BigDecimal running = Money.ZERO;
        int n = 1;
        for (Payment p : l.getPayments()) {
            running = running.add(Money.nz(p.getAmount()));
            t.row(String.valueOf(n++), p.getPaymentDate().format(DATE), nz(p.getReceiptNo()),
                    Money.format(p.getAmount()), Money.format(running), nz(p.getRemark()));
        }
        return t.total("Principal", Money.format(l.getPrincipal()))
                .total("Interest (" + l.getInterestRate() + "% p.a.)", Money.format(l.getInterestAmount()))
                .total("Total Payable", Money.format(l.getTotalPayable()))
                .total("Total Paid", Money.format(b.totalPaid()))
                .total("Balance", Money.format(b.balance()))
                .total("Arrears", Money.format(b.arrears()));
    }

    public ReportTable schedule(Long loanId) {
        Loan l = loanService.findDetailed(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        ReportTable t = new ReportTable("Repayment Schedule - " + l.getLoanNumber())
                .subtitle(l.getClientName())
                .column("#", true).column("Due Date").column("Due Amount", true);
        BigDecimal total = Money.ZERO;
        for (var i : l.getInstallments()) {
            t.row(String.valueOf(i.getInstallmentNo()), i.getDueDate().format(DATE),
                    Money.format(i.getDueAmount()));
            total = total.add(Money.nz(i.getDueAmount()));
        }
        return t.total("Installments", String.valueOf(l.getInstallments().size()))
                .total("Total", Money.format(total));
    }

    public ReportTable settledLoans(LocalDate asOf) {
        List<Loan> loans = loanService.findAll();
        Map<Long, LoanBalance> balances = loanService.balances(loans, asOf);
        ReportTable t = new ReportTable("Settled Loans")
                .subtitle("As at " + asOf.format(DATE))
                .column("Loan No").column("Client").column("Center").column("Issue Date")
                .column("Principal", true).column("Total Paid", true);
        BigDecimal paid = Money.ZERO;
        int count = 0;
        for (Loan l : loans) {
            LoanBalance b = balances.getOrDefault(l.getId(), LoanBalance.empty());
            if (!b.settled()) {
                continue;
            }
            count++;
            paid = paid.add(b.totalPaid());
            t.row(l.getLoanNumber(), l.getClientName(), l.getClient().getCenterName(),
                    l.getIssueDate().format(DATE), Money.format(l.getPrincipal()),
                    Money.format(b.totalPaid()));
        }
        return t.total("Settled Loans", String.valueOf(count)).total("Total Collected", Money.format(paid));
    }

    // ---- export helpers -------------------------------------------------

    public Path exportPdf(ReportTable table) throws IOException {
        return PdfExporter.write(table, Config.reportsDir().resolve(fileName(table, "pdf")));
    }

    public Path exportCsv(ReportTable table) throws IOException {
        return CsvExporter.write(table, Config.reportsDir().resolve(fileName(table, "csv")));
    }

    private String fileName(ReportTable table, String ext) {
        String base = table.title().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        return base + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "." + ext;
    }

    public void open(Path file) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file.toAbsolutePath().toFile());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToDash(String s) {
        return s == null || s.isBlank() ? "(none)" : s;
    }
}
