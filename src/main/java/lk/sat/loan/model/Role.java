package lk.sat.loan.model;

public enum Role {
    ADMIN,
    OFFICER;

    public String display() {
        return this == ADMIN ? "Administrator" : "Loan Officer";
    }
}
