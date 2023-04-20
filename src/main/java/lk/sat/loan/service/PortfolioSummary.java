package lk.sat.loan.service;

import java.math.BigDecimal;

public record PortfolioSummary(long clientCount,
                               long loanCount,
                               long activeLoanCount,
                               BigDecimal totalDisbursed,
                               BigDecimal totalExpected,
                               BigDecimal totalCollected,
                               BigDecimal outstanding,
                               BigDecimal arrears) {
}
