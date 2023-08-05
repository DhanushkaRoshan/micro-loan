package lk.sat.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lk.sat.loan.model.Installment;
import lk.sat.loan.service.LoanMath;
import lk.sat.loan.util.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoanMathTest {

    @Test
    @DisplayName("interest matches the old formula: principal * rate/100 * months/12")
    void interestForTerm() {
        // 100,000 at 12% for 12 months -> 12,000
        assertEquals(Money.of("12000.00"),
                LoanMath.interestForTerm(Money.of("100000"), new BigDecimal("12"), 12));
        // Half a year at the same rate is half the interest.
        assertEquals(Money.of("6000.00"),
                LoanMath.interestForTerm(Money.of("100000"), new BigDecimal("12"), 6));
    }

    @Test
    @DisplayName("total payable is principal plus term interest")
    void totalPayable() {
        assertEquals(Money.of("112000.00"),
                LoanMath.totalPayable(Money.of("100000"), new BigDecimal("12"), 12));
    }

    @Test
    @DisplayName("installment is the total spread over the term")
    void installmentAmount() {
        assertEquals(Money.of("9333.33"),
                LoanMath.installmentAmount(Money.of("100000"), new BigDecimal("12"), 12));
    }

    @Test
    @DisplayName("zero or negative terms never divide by zero")
    void degenerateTerms() {
        assertEquals(Money.ZERO, LoanMath.interestForTerm(Money.of("1000"), BigDecimal.TEN, 0));
        assertEquals(Money.ZERO, LoanMath.installmentAmount(Money.of("1000"), BigDecimal.TEN, 0));
        assertTrue(LoanMath.buildSchedule(Money.of("1000"), 0, LocalDate.now()).isEmpty());
    }

    @Test
    @DisplayName("the schedule sums exactly to the total, with drift in the last installment")
    void scheduleSumsExactly() {
        BigDecimal total = Money.of("112000.00");
        List<Installment> schedule = LoanMath.buildSchedule(total, 12, LocalDate.of(2026, 1, 15));

        assertEquals(12, schedule.size());
        BigDecimal sum = schedule.stream()
                .map(Installment::getDueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(total, sum, "rounding drift must be absorbed, not lost");
    }

    @Test
    @DisplayName("a total that does not divide evenly still sums exactly")
    void scheduleHandlesRoundingDrift() {
        BigDecimal total = Money.of("1000.00");
        List<Installment> schedule = LoanMath.buildSchedule(total, 3, LocalDate.of(2026, 1, 1));

        BigDecimal sum = schedule.stream()
                .map(Installment::getDueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(total, sum);
        // 333.33 + 333.33 + 333.34
        assertEquals(Money.of("333.34"), schedule.get(2).getDueAmount());
    }

    @Test
    @DisplayName("due dates advance one month at a time from the first due date")
    void scheduleDates() {
        List<Installment> schedule = LoanMath.buildSchedule(Money.of("300"), 3, LocalDate.of(2026, 1, 31));

        assertEquals(LocalDate.of(2026, 1, 31), schedule.get(0).getDueDate());
        assertEquals(LocalDate.of(2026, 2, 28), schedule.get(1).getDueDate());
        assertEquals(LocalDate.of(2026, 3, 31), schedule.get(2).getDueDate());
        assertEquals(1, schedule.get(0).getInstallmentNo());
        assertEquals(3, schedule.get(2).getInstallmentNo());
    }
}
