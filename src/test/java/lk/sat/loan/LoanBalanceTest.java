package lk.sat.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lk.sat.loan.service.LoanBalance;
import lk.sat.loan.util.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoanBalanceTest {

    @Test
    @DisplayName("balance is total payable less what has been paid")
    void balance() {
        LoanBalance b = new LoanBalance(Money.of("112000"), Money.of("40000"), Money.of("50000"), 1);
        assertEquals(Money.of("72000.00"), b.balance());
    }

    @Test
    @DisplayName("arrears is what was due to date less what has been paid")
    void arrears() {
        LoanBalance b = new LoanBalance(Money.of("112000"), Money.of("40000"), Money.of("50000"), 1);
        assertEquals(Money.of("10000.00"), b.arrears());
        assertTrue(b.inArrears());
    }

    @Test
    @DisplayName("paying ahead of schedule never reports negative arrears")
    void aheadOfSchedule() {
        LoanBalance b = new LoanBalance(Money.of("112000"), Money.of("60000"), Money.of("50000"), 0);
        assertEquals(Money.ZERO, b.arrears());
        assertFalse(b.inArrears());
    }

    @Test
    @DisplayName("overpayment never reports a negative balance")
    void overpaid() {
        LoanBalance b = new LoanBalance(Money.of("100"), Money.of("150"), Money.of("100"), 0);
        assertEquals(Money.ZERO, b.balance());
        assertTrue(b.settled());
    }

    @Test
    @DisplayName("a fully paid loan is settled")
    void settled() {
        LoanBalance b = new LoanBalance(Money.of("112000"), Money.of("112000"), Money.of("112000"), 0);
        assertTrue(b.settled());
        assertFalse(b.inArrears());
    }
}
