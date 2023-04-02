package lk.sat.loan.model;

public enum LoanStatus {
    ACTIVE,
    CLOSED,
    DEFAULTED,
    WRITTEN_OFF;

    public String display() {
        return switch (this) {
            case ACTIVE -> "Active";
            case CLOSED -> "Closed";
            case DEFAULTED -> "Defaulted";
            case WRITTEN_OFF -> "Written off";
        };
    }
}
