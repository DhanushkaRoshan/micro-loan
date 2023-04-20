package lk.sat.loan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lk.sat.loan.model.Guarantor;
import lk.sat.loan.model.Installment;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.LoanStatus;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Money;
import org.hibernate.Session;

public class LoanService {

    private static final String FETCH =
            "select distinct l from Loan l"
                    + " join fetch l.client c"
                    + " left join fetch c.center ce"
                    + " left join fetch ce.area"
                    + " left join fetch l.coordinator ";

    public List<Loan> findAll() {
        return Db.read(s -> s.createSelectionQuery(FETCH + " order by l.issueDate desc, l.id desc", Loan.class)
                .getResultList());
    }

    public List<Loan> findByStatus(LoanStatus status) {
        return Db.read(s -> s.createSelectionQuery(
                        FETCH + " where l.status = :st order by l.issueDate desc", Loan.class)
                .setParameter("st", status).getResultList());
    }

    public List<Loan> findByClient(Long clientId) {
        return Db.read(s -> s.createSelectionQuery(
                        FETCH + " where l.client.id = :id order by l.issueDate desc", Loan.class)
                .setParameter("id", clientId).getResultList());
    }

    public List<Loan> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return Db.read(s -> s.createSelectionQuery(
                        FETCH + " where lower(l.loanNumber) like :q or lower(c.fullName) like :q"
                                + " or lower(c.clientCode) like :q or lower(coalesce(c.nicNumber,'')) like :q"
                                + " order by l.issueDate desc", Loan.class)
                .setParameter("q", like).getResultList());
    }

    public Optional<Loan> findById(Long id) {
        return Db.read(s -> Optional.ofNullable(s.find(Loan.class, id)));
    }

    public Optional<Loan> findDetailed(Long id) {
        return Db.read(s -> {
            Loan l = s.find(Loan.class, id);
            if (l != null) {
                l.getGuarantors().size();
                l.getPayments().size();
                l.getInstallments().size();
            }
            return Optional.ofNullable(l);
        });
    }

    public boolean loanNumberTaken(String number, Long excludingId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select count(l) from Loan l where l.loanNumber = :n and (:id is null or l.id <> :id)", Long.class)
                .setParameter("n", number).setParameter("id", excludingId).getSingleResult() > 0);
    }

    public String nextLoanNumber() {
        return Db.read(s -> {
            Long count = s.createSelectionQuery("select count(l) from Loan l", Long.class).getSingleResult();
            return String.format("L%05d", count + 1);
        });
    }

    public Loan save(Loan loan, boolean rebuildSchedule) {
        return Db.tx(s -> {
            List<Guarantor> incoming = new ArrayList<>(loan.getGuarantors());
            loan.getGuarantors().clear();
            if (rebuildSchedule) {
                loan.getInstallments().clear();
            }

            Loan managed = s.merge(loan);
            s.flush();

            replaceGuarantors(s, managed, incoming);
            if (rebuildSchedule) {
                rebuildSchedule(s, managed);
            }
            return managed;
        });
    }

    private void replaceGuarantors(Session s, Loan loan, List<Guarantor> incoming) {
        s.createMutationQuery("delete from Guarantor g where g.loan.id = :id")
                .setParameter("id", loan.getId()).executeUpdate();
        loan.getGuarantors().clear();
        int position = 1;
        for (Guarantor g : incoming) {
            g.setId(null);
            g.setLoan(loan);
            g.setPosition(position++);
            s.persist(g);
            loan.getGuarantors().add(g);
        }
    }

    private void rebuildSchedule(Session s, Loan loan) {
        s.createMutationQuery("delete from Installment i where i.loan.id = :id")
                .setParameter("id", loan.getId()).executeUpdate();
        loan.getInstallments().clear();
        LocalDate firstDue = loan.getFirstDueDate() != null
                ? loan.getFirstDueDate()
                : loan.getIssueDate().plusMonths(1);
        for (Installment ins : LoanMath.buildSchedule(
                loan.getTotalPayable(), loan.getInstallmentCount(), firstDue)) {
            ins.setLoan(loan);
            s.persist(ins);
            loan.getInstallments().add(ins);
        }
    }

    public void delete(Long loanId) {
        Db.txVoid(s -> {
            Loan l = s.find(Loan.class, loanId);
            if (l != null) {
                s.remove(l);
            }
        });
    }

    public void setStatus(Long loanId, LoanStatus status) {
        Db.txVoid(s -> {
            Loan l = s.find(Loan.class, loanId);
            if (l != null) {
                l.setStatus(status);
                s.merge(l);
            }
        });
    }

    public LoanBalance balanceOf(Long loanId, LocalDate asOf) {
        return Db.read(s -> balanceOf(s, loanId, asOf));
    }

    private LoanBalance balanceOf(Session s, Long loanId, LocalDate asOf) {
        Object[] row = s.createSelectionQuery(
                        "select l.totalPayable,"
                                + " (select coalesce(sum(p.amount), 0) from Payment p where p.loan.id = l.id),"
                                + " (select coalesce(sum(i.dueAmount), 0) from Installment i"
                                + "    where i.loan.id = l.id and i.dueDate <= :asOf),"
                                + " (select count(i2) from Installment i2"
                                + "    where i2.loan.id = l.id and i2.dueDate <= :asOf),"
                                + " l.installmentAmount"
                                + " from Loan l where l.id = :id", Object[].class)
                .setParameter("id", loanId)
                .setParameter("asOf", asOf)
                .uniqueResult();
        if (row == null) {
            return LoanBalance.empty();
        }
        BigDecimal total = Money.nz((BigDecimal) row[0]);
        BigDecimal paid = Money.nz((BigDecimal) row[1]);
        BigDecimal due = Money.nz((BigDecimal) row[2]);
        BigDecimal perInstallment = Money.nz((BigDecimal) row[4]);
        return new LoanBalance(total, paid, due, arrearsMonths(due.subtract(paid), perInstallment));
    }

    private static int arrearsMonths(BigDecimal shortfall, BigDecimal perInstallment) {
        if (shortfall.signum() <= 0 || perInstallment.signum() <= 0) {
            return 0;
        }
        return shortfall.divide(perInstallment, 0, java.math.RoundingMode.FLOOR).intValue();
    }

    public Map<Long, LoanBalance> balances(List<Loan> loans, LocalDate asOf) {
        Map<Long, LoanBalance> out = new HashMap<>();
        if (loans.isEmpty()) {
            return out;
        }
        List<Long> ids = new ArrayList<>(loans.size());
        for (Loan l : loans) {
            ids.add(l.getId());
        }
        Db.read(s -> {
            List<Object[]> rows = s.createSelectionQuery(
                            "select l.id, l.totalPayable, l.installmentAmount,"
                                    + " (select coalesce(sum(p.amount), 0) from Payment p where p.loan.id = l.id),"
                                    + " (select coalesce(sum(i.dueAmount), 0) from Installment i"
                                    + "    where i.loan.id = l.id and i.dueDate <= :asOf)"
                                    + " from Loan l where l.id in (:ids)", Object[].class)
                    .setParameter("ids", ids)
                    .setParameter("asOf", asOf)
                    .getResultList();
            for (Object[] r : rows) {
                BigDecimal total = Money.nz((BigDecimal) r[1]);
                BigDecimal per = Money.nz((BigDecimal) r[2]);
                BigDecimal paid = Money.nz((BigDecimal) r[3]);
                BigDecimal due = Money.nz((BigDecimal) r[4]);
                out.put((Long) r[0], new LoanBalance(total, paid, due, arrearsMonths(due.subtract(paid), per)));
            }
            return null;
        });
        return out;
    }

    public PortfolioSummary portfolio(LocalDate asOf) {
        return Db.read(s -> {
            Object[] r = s.createSelectionQuery(
                            "select count(l), coalesce(sum(l.principal), 0), coalesce(sum(l.totalPayable), 0)"
                                    + " from Loan l", Object[].class).uniqueResult();
            BigDecimal paid = Money.nz(s.createSelectionQuery(
                    "select coalesce(sum(p.amount), 0) from Payment p", BigDecimal.class).uniqueResult());
            Long clients = s.createSelectionQuery("select count(c) from Client c", Long.class).getSingleResult();
            Long active = s.createSelectionQuery(
                            "select count(l) from Loan l where l.status = :st", Long.class)
                    .setParameter("st", LoanStatus.ACTIVE).getSingleResult();
            BigDecimal dueToDate = Money.nz(s.createSelectionQuery(
                            "select coalesce(sum(i.dueAmount), 0) from Installment i where i.dueDate <= :asOf",
                            BigDecimal.class)
                    .setParameter("asOf", asOf).uniqueResult());
            BigDecimal disbursed = Money.nz((BigDecimal) r[1]);
            BigDecimal expected = Money.nz((BigDecimal) r[2]);
            return new PortfolioSummary(clients, (Long) r[0], active, disbursed, expected, paid,
                    Money.floorZero(expected.subtract(paid)), Money.floorZero(dueToDate.subtract(paid)));
        });
    }
}
