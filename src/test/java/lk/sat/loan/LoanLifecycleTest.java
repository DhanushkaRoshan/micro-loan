package lk.sat.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import lk.sat.loan.model.Client;
import lk.sat.loan.model.Guarantor;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.LoanStatus;
import lk.sat.loan.service.ClientService;
import lk.sat.loan.service.LoanBalance;
import lk.sat.loan.service.LoanMath;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.service.PaymentService;
import lk.sat.loan.util.Config;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Money;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoanLifecycleTest {

    private static final String SUFFIX = "-IT" + System.currentTimeMillis() % 100000;

    private static final LoanService loans = new LoanService();
    private static final ClientService clients = new ClientService();
    private static final PaymentService payments = new PaymentService();

    private static Long clientId;
    private static Long loanId;

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(reachable(), "MySQL is not reachable - skipping integration checks");
        Db.start();
    }

    private static boolean reachable() {
        try (var c = DriverManager.getConnection(Config.dbUrl(), Config.dbUser(), Config.dbPassword())) {
            return c.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Order(1)
    @DisplayName("a new loan is saved with its guarantors and a generated schedule")
    void createLoan() {
        Client c = new Client();
        c.setClientCode("IT" + SUFFIX);
        c.setFullName("Integration Test Borrower");
        c.setNicNumber("NIC" + SUFFIX);
        clientId = clients.save(c).getId();

        BigDecimal principal = Money.of("100000");
        BigDecimal rate = new BigDecimal("12");
        int months = 12;

        Loan l = new Loan();
        l.setLoanNumber("LN" + SUFFIX);
        l.setClient(clients.findById(clientId).orElseThrow());
        l.setIssueDate(LocalDate.now().minusMonths(3));
        l.setFirstDueDate(LocalDate.now().minusMonths(2));
        l.setPrincipal(principal);
        l.setInterestRate(rate);
        l.setInstallmentCount(months);
        l.setInterestAmount(LoanMath.interestForTerm(principal, rate, months));
        l.setInstallmentAmount(LoanMath.installmentAmount(principal, rate, months));
        l.setTotalPayable(LoanMath.totalPayable(principal, rate, months));

        Guarantor g = new Guarantor();
        g.setFullName("First Guarantor");
        l.addGuarantor(g);

        loanId = loans.save(l, true).getId();

        Loan saved = loans.findDetailed(loanId).orElseThrow();
        assertEquals(Money.of("112000.00"), saved.getTotalPayable());
        assertEquals(12, saved.getInstallments().size(), "schedule should be generated");
        assertEquals(1, saved.getGuarantors().size());
    }

    @Test
    @Order(2)
    @DisplayName("re-saving an edited loan replaces the schedule without orphan errors")
    void editLoanRebuildsSchedule() {
        Loan l = loans.findDetailed(loanId).orElseThrow();

        // Shorten the term: the old 12-row schedule must be replaced by 6 rows.
        BigDecimal principal = l.getPrincipal();
        BigDecimal rate = l.getInterestRate();
        int months = 6;
        l.setInstallmentCount(months);
        l.setInterestAmount(LoanMath.interestForTerm(principal, rate, months));
        l.setInstallmentAmount(LoanMath.installmentAmount(principal, rate, months));
        l.setTotalPayable(LoanMath.totalPayable(principal, rate, months));

        // Replace the guarantor list the way LoanDialog does.
        l.getGuarantors().clear();
        Guarantor a = new Guarantor();
        a.setFullName("Replacement Guarantor A");
        l.addGuarantor(a);
        Guarantor b = new Guarantor();
        b.setFullName("Replacement Guarantor B");
        l.addGuarantor(b);

        loans.save(l, true);

        Loan reloaded = loans.findDetailed(loanId).orElseThrow();
        assertEquals(6, reloaded.getInstallments().size(), "old schedule rows must be removed");
        assertEquals(2, reloaded.getGuarantors().size());
        assertEquals(Money.of("106000.00"), reloaded.getTotalPayable());

        BigDecimal scheduled = reloaded.getInstallments().stream()
                .map(i -> i.getDueAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(reloaded.getTotalPayable(), scheduled, "schedule must sum to the total");
    }

    @Test
    @Order(3)
    @DisplayName("payments reduce the balance and are rejected above the outstanding amount")
    void recordPayments() {
        payments.record(loanId, LocalDate.now(), Money.of("50000"), "RCPT-1", "first", null);

        LoanBalance b = loans.balanceOf(loanId, LocalDate.now());
        assertEquals(Money.of("50000.00"), b.totalPaid());
        assertEquals(Money.of("56000.00"), b.balance());
        assertFalse(b.settled());

        IllegalArgumentException tooMuch = assertThrows(IllegalArgumentException.class,
                () -> payments.record(loanId, LocalDate.now(), Money.of("999999"), null, null, null));
        assertTrue(tooMuch.getMessage().contains("exceeds"), tooMuch.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> payments.record(loanId, LocalDate.now(), Money.of("0"), null, null, null));
    }

    @Test
    @Order(4)
    @DisplayName("settling the balance closes the loan, and deleting a payment reopens it")
    void settleAndReopen() {
        payments.record(loanId, LocalDate.now(), Money.of("56000"), "RCPT-2", "final", null);

        Loan closed = loans.findById(loanId).orElseThrow();
        assertEquals(LoanStatus.CLOSED, closed.getStatus());
        assertTrue(loans.balanceOf(loanId, LocalDate.now()).settled());

        List<lk.sat.loan.model.Payment> history = payments.forLoan(loanId);
        assertEquals(2, history.size());
        payments.delete(history.get(1).getId());

        Loan reopened = loans.findById(loanId).orElseThrow();
        assertEquals(LoanStatus.ACTIVE, reopened.getStatus(), "removing a payment must reopen the loan");
        assertEquals(Money.of("56000.00"), loans.balanceOf(loanId, LocalDate.now()).balance());
    }

    @Test
    @Order(5)
    @DisplayName("deleting a client cascades to loans, guarantors, schedule and payments")
    void deleteCascades() {
        clients.delete(clientId);

        assertTrue(loans.findById(loanId).isEmpty(), "loan should be gone with the client");
        assertTrue(payments.forLoan(loanId).isEmpty(), "payments should be gone with the loan");
        assertTrue(clients.findById(clientId).isEmpty());
    }

    @AfterAll
    static void cleanup() {
        if (clientId == null) {
            return;
        }
        try {
            clients.findById(clientId).ifPresent(c -> clients.delete(clientId));
        } catch (RuntimeException ignored) {
            // Already removed by the cascade test.
        }
    }
}
