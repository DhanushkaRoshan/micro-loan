package lk.sat.loan.service;

import java.math.BigDecimal;
import lk.sat.loan.util.Money;

public record LoanBalance(BigDecimal totalPayable,
                          BigDecimal totalPaid,
                          BigDecimal dueToDate,
                          int arrearsMonths) {

    public BigDecimal balance() {
        return Money.floorZero(Money.nz(totalPayable).subtract(Money.nz(totalPaid)));
    }

    public BigDecimal arrears() {
        return Money.floorZero(Money.nz(dueToDate).subtract(Money.nz(totalPaid)));
    }

    public boolean settled() {
        return balance().signum() == 0;
    }

    public boolean inArrears() {
        return arrears().signum() > 0;
    }

    public static LoanBalance empty() {
        return new LoanBalance(Money.ZERO, Money.ZERO, Money.ZERO, 0);
    }
}
