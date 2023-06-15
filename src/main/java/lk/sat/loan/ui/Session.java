package lk.sat.loan.ui;

import lk.sat.loan.model.Role;
import lk.sat.loan.model.User;

public final class Session {

    private static User currentUser;

    private Session() {
    }

    public static void set(User user) {
        currentUser = user;
    }

    public static User current() {
        return currentUser;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }

    public static void clear() {
        currentUser = null;
    }
}
