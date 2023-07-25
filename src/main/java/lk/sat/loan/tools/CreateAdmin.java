package lk.sat.loan.tools;

import lk.sat.loan.model.Role;
import lk.sat.loan.model.User;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Passwords;

public final class CreateAdmin {

    private CreateAdmin() {
    }

    public static void main(String[] args) {
        String username = args.length > 0 ? args[0].trim() : "admin";
        String password = args.length > 1 ? args[1] : "admin123";

        if (username.isBlank()) {
            System.err.println("The username cannot be blank.");
            System.exit(2);
        }
        if (password.length() < 6) {
            System.err.println("The password must be at least 6 characters.");
            System.exit(2);
        }

        try {
            Db.start();
            boolean created = Db.tx(s -> {
                User u = s.createSelectionQuery("from User u where u.username = :n", User.class)
                        .setParameter("n", username)
                        .uniqueResultOptional()
                        .orElse(null);
                boolean isNew = (u == null);
                if (isNew) {
                    u = new User();
                    u.setUsername(username);
                    u.setFullName("System Administrator");
                }
                u.setRole(Role.ADMIN);
                u.setActive(true);
                u.setPasswordHash(Passwords.hash(password));
                if (isNew) {
                    s.persist(u);
                } else {
                    s.merge(u);
                }
                return isNew;
            });

            System.out.println((created ? "Created" : "Reset")
                    + " administrator \"" + username + "\" with the password you supplied.");
            System.out.println("Sign in, then change it from the sidebar.");
        } catch (RuntimeException e) {
            System.err.println("FAILED: " + rootMessage(e));
            System.err.println("Is MySQL running?  docker compose up -d mysql");
            Db.stop();
            System.exit(1);
        }
        Db.stop();
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getMessage() == null ? r.toString() : r.getMessage();
    }
}
