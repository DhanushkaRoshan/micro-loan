package lk.sat.loan.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lk.sat.loan.model.Area;
import lk.sat.loan.model.Center;
import lk.sat.loan.model.Client;
import lk.sat.loan.model.Coordinator;
import lk.sat.loan.model.Guarantor;
import lk.sat.loan.model.Installment;
import lk.sat.loan.model.Loan;
import lk.sat.loan.service.AuthService;
import lk.sat.loan.service.LoanMath;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.service.PortfolioSummary;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Money;

public final class Bootstrap {

    private Bootstrap() {
    }

    public static void main(String[] args) {
        boolean demo = List.of(args).contains("--demo");
        try {
            System.out.println("Applying migrations and validating the mapping...");
            Db.start();
            boolean seeded = new AuthService().ensureSeedAdmin();
            System.out.println(seeded
                    ? "Schema OK. Created the seed administrator: admin / admin123"
                    : "Schema OK. An administrator already exists; no seed account created.");

            if (demo) {
                int created = seedDemo();
                System.out.println(created == 0
                        ? "Demo data already present; nothing added."
                        : "Inserted a demo portfolio (" + created + " loans).");
            }

            PortfolioSummary s = new LoanService().portfolio(LocalDate.now());
            System.out.printf("""

                    Portfolio
                    ---------
                      clients          %d
                      loans            %d (%d active)
                      disbursed        %s
                      expected         %s
                      collected        %s
                      outstanding      %s
                      arrears          %s
                    %n""",
                    s.clientCount(), s.loanCount(), s.activeLoanCount(),
                    Money.format(s.totalDisbursed()), Money.format(s.totalExpected()),
                    Money.format(s.totalCollected()), Money.format(s.outstanding()),
                    Money.format(s.arrears()));
        } catch (RuntimeException e) {
            System.err.println("FAILED: " + rootMessage(e));
            System.err.println("Is MySQL running?  docker compose up -d mysql");
            Db.stop();
            System.exit(1);
        }
        Db.stop();
    }

    private static int seedDemo() {
        LoanService loanService = new LoanService();
        if (loanService.findAll().size() > 0) {
            return 0;
        }

        return Db.tx(s -> {
            Area area = new Area("Colombo");
            s.persist(area);

            Center center = new Center("Main Branch", area);
            s.persist(center);

            Coordinator coord = new Coordinator("K. Perera");
            s.persist(coord);

            String[][] clients = {
                    {"C0001", "Nimal Silva", "901234567V"},
                    {"C0002", "Kamala Fernando", "885678901V"},
                    {"C0003", "Sunil Jayawardena", "760123456V"},
            };

            int loanCount = 0;
            BigDecimal rate = new BigDecimal("12");
            LocalDate today = LocalDate.now();

            for (String[] row : clients) {
                Client c = new Client();
                c.setClientCode(row[0]);
                c.setFullName(row[1]);
                c.setNicNumber(row[2]);
                c.setCenter(center);
                s.persist(c);

                BigDecimal principal = Money.of((50000 + loanCount * 25000) + "");
                int months = 12;
                BigDecimal interest = LoanMath.interestForTerm(principal, rate, months);
                BigDecimal total = LoanMath.totalPayable(principal, rate, months);
                BigDecimal installment = LoanMath.installmentAmount(principal, rate, months);

                Loan loan = new Loan();
                loan.setLoanNumber("L%05d".formatted(loanCount + 1));
                loan.setClient(c);
                loan.setCoordinator(coord);
                loan.setIssueDate(today.minusMonths(6));
                loan.setFirstDueDate(today.minusMonths(5));
                loan.setPrincipal(principal);
                loan.setInterestRate(rate);
                loan.setInterestAmount(interest);
                loan.setInstallmentCount(months);
                loan.setInstallmentAmount(installment);
                loan.setTotalPayable(total);
                loan.setDeduction(Money.ZERO);
                s.persist(loan);

                Guarantor g = new Guarantor();
                g.setFullName("Guarantor for " + row[1]);
                g.setNicNumber("000000000V");
                g.setLoan(loan);
                g.setPosition(1);
                s.persist(g);

                for (Installment ins : LoanMath.buildSchedule(total, months, today.minusMonths(5))) {
                    ins.setLoan(loan);
                    s.persist(ins);
                }

                loanCount++;
            }
            return loanCount;
        });
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getMessage() == null ? r.toString() : r.getMessage();
    }
}
