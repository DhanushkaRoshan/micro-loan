package lk.sat.loan.migration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.sat.loan.util.Money;

public class MigrationReport {

    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final List<String> skipped = new ArrayList<>();
    private BigDecimal totalAmount = Money.ZERO;
    private BigDecimal totalCollected = Money.ZERO;

    public void count(String entity) {
        counts.merge(entity, 1, Integer::sum);
    }

    public void skip(String reason) {
        skipped.add(reason);
    }

    public void addAmount(BigDecimal amount) {
        totalAmount = totalAmount.add(Money.nz(amount));
    }

    public void addCollected(BigDecimal amount) {
        totalCollected = totalCollected.add(Money.nz(amount));
    }

    public void print() {
        System.out.println("\n--- Migration Report ---");
        counts.forEach((k, v) -> System.out.printf("  %-20s %,d%n", k, v));
        System.out.println("  total payable        " + Money.format(totalAmount));
        System.out.println("  total collected      " + Money.format(totalCollected));
        if (!skipped.isEmpty()) {
            System.out.println("\n  Skipped (" + skipped.size() + "):");
            skipped.forEach(s -> System.out.println("    - " + s));
        }
        System.out.println("--- End ---\n");
    }
}
