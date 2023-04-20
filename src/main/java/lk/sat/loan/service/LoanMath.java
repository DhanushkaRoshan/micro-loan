package lk.sat.loan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.sat.loan.model.Installment;
import lk.sat.loan.util.Money;

// interestForTerm = principal * rate/100 * months/12
// totalPayable = principal + interestForTerm
// installment = totalPayable / months
public final class LoanMath {

    private LoanMath() {
    }

    public static BigDecimal interestForTerm(BigDecimal principal, BigDecimal rate, int months) {
        if (principal == null || rate == null || months <= 0) {
            return Money.ZERO;
        }
        return principal.multiply(rate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal totalPayable(BigDecimal principal, BigDecimal rate, int months) {
        return Money.nz(principal).add(interestForTerm(principal, rate, months));
    }

    public static BigDecimal installmentAmount(BigDecimal principal, BigDecimal rate, int months) {
        if (months <= 0) {
            return Money.ZERO;
        }
        return totalPayable(principal, rate, months)
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    // Rounding drift is absorbed by the final installment
    public static List<Installment> buildSchedule(BigDecimal totalPayable, int months, LocalDate firstDue) {
        List<Installment> out = new ArrayList<>();
        if (months <= 0 || firstDue == null) {
            return out;
        }
        BigDecimal total = Money.nz(totalPayable);
        BigDecimal each = total.divide(BigDecimal.valueOf(months), 2, RoundingMode.DOWN);
        BigDecimal allocated = each.multiply(BigDecimal.valueOf(months - 1L));
        for (int i = 1; i <= months; i++) {
            Installment ins = new Installment();
            ins.setInstallmentNo(i);
            ins.setDueDate(firstDue.plusMonths(i - 1L));
            ins.setDueAmount(i < months ? each : total.subtract(allocated));
            out.add(ins);
        }
        return out;
    }
}
