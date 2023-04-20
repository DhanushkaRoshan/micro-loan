package lk.sat.loan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.LoanStatus;
import lk.sat.loan.model.Payment;
import lk.sat.loan.model.User;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Money;

public class PaymentService {

    private final LoanService loans = new LoanService();

    public List<Payment> forLoan(Long loanId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select p from Payment p left join fetch p.recordedBy"
                                + " where p.loan.id = :id order by p.paymentDate, p.id", Payment.class)
                .setParameter("id", loanId).getResultList());
    }

    public List<Payment> between(LocalDate from, LocalDate to) {
        return Db.read(s -> s.createSelectionQuery(
                        "select p from Payment p"
                                + " join fetch p.loan l join fetch l.client c"
                                + " left join fetch c.center ce left join fetch ce.area"
                                + " left join fetch p.recordedBy"
                                + " where p.paymentDate between :f and :t"
                                + " order by p.paymentDate, p.id", Payment.class)
                .setParameter("f", from).setParameter("t", to).getResultList());
    }

    public Payment record(Long loanId, LocalDate date, BigDecimal amount, String receiptNo,
                          String remark, User recordedBy) {
        BigDecimal amt = Money.nz(amount);
        if (amt.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        return Db.tx(s -> {
            Loan loan = s.find(Loan.class, loanId);
            if (loan == null) {
                throw new IllegalArgumentException("Loan not found.");
            }
            BigDecimal paid = Money.nz(s.createSelectionQuery(
                            "select coalesce(sum(p.amount), 0) from Payment p where p.loan.id = :id", BigDecimal.class)
                    .setParameter("id", loanId).uniqueResult());
            BigDecimal outstanding = Money.floorZero(Money.nz(loan.getTotalPayable()).subtract(paid));
            if (amt.compareTo(outstanding) > 0) {
                throw new IllegalArgumentException(
                        "Amount " + Money.format(amt) + " exceeds the outstanding balance of "
                                + Money.format(outstanding) + ".");
            }

            Payment p = new Payment();
            p.setLoan(loan);
            p.setPaymentDate(date == null ? LocalDate.now() : date);
            p.setAmount(amt);
            p.setReceiptNo(receiptNo == null || receiptNo.isBlank() ? null : receiptNo.trim());
            p.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
            p.setRecordedBy(recordedBy == null ? null : s.getReference(User.class, recordedBy.getId()));
            s.persist(p);

            if (paid.add(amt).compareTo(Money.nz(loan.getTotalPayable())) >= 0) {
                loan.setStatus(LoanStatus.CLOSED);
                s.merge(loan);
            }
            return p;
        });
    }

    public void delete(Long paymentId) {
        Db.txVoid(s -> {
            Payment p = s.find(Payment.class, paymentId);
            if (p == null) {
                return;
            }
            Loan loan = p.getLoan();
            s.remove(p);
            s.flush();
            BigDecimal paid = Money.nz(s.createSelectionQuery(
                            "select coalesce(sum(x.amount), 0) from Payment x where x.loan.id = :id", BigDecimal.class)
                    .setParameter("id", loan.getId()).uniqueResult());
            if (loan.getStatus() == LoanStatus.CLOSED
                    && paid.compareTo(Money.nz(loan.getTotalPayable())) < 0) {
                loan.setStatus(LoanStatus.ACTIVE);
                s.merge(loan);
            }
        });
    }

    public String nextReceiptNo() {
        return Db.read(s -> {
            Long count = s.createSelectionQuery("select count(p) from Payment p", Long.class).getSingleResult();
            return String.format("R%06d", count + 1);
        });
    }

    public LoanBalance balanceOf(Long loanId) {
        return loans.balanceOf(loanId, LocalDate.now());
    }
}
