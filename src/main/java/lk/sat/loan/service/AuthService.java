package lk.sat.loan.service;

import java.util.List;
import java.util.Optional;
import lk.sat.loan.model.Role;
import lk.sat.loan.model.User;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Passwords;

public class AuthService {

    public boolean ensureSeedAdmin() {
        return Db.tx(s -> {
            Long admins = s.createSelectionQuery(
                            "select count(u) from User u where u.role = :r and u.active = true", Long.class)
                    .setParameter("r", Role.ADMIN)
                    .getSingleResult();
            if (admins > 0) {
                return false;
            }
            String username = "admin";
            for (int n = 2; usernameExists(s, username); n++) {
                username = "admin" + n;
            }
            User admin = new User();
            admin.setUsername(username);
            admin.setFullName("System Administrator");
            admin.setRole(Role.ADMIN);
            admin.setPasswordHash(Passwords.hash("admin123"));
            s.persist(admin);
            return true;
        });
    }

    private boolean usernameExists(org.hibernate.Session s, String username) {
        return s.createSelectionQuery("select count(u) from User u where u.username = :u", Long.class)
                .setParameter("u", username).getSingleResult() > 0;
    }

    public Optional<User> authenticate(String username, String rawPassword) {
        return Db.read(s -> s.createSelectionQuery(
                        "from User u where u.username = :u and u.active = true", User.class)
                .setParameter("u", username == null ? "" : username.trim())
                .uniqueResultOptional()
                .filter(u -> Passwords.matches(rawPassword, u.getPasswordHash())));
    }

    public List<User> findAll() {
        return Db.read(s -> s.createSelectionQuery("from User u order by u.username", User.class).getResultList());
    }

    public User save(User user, String newRawPassword) {
        return Db.tx(s -> {
            if (newRawPassword != null && !newRawPassword.isBlank()) {
                user.setPasswordHash(Passwords.hash(newRawPassword));
            }
            return user.getId() == null ? persistNew(s, user) : s.merge(user);
        });
    }

    private User persistNew(org.hibernate.Session s, User user) {
        s.persist(user);
        return user;
    }

    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        return Db.tx(s -> {
            User u = s.find(User.class, userId);
            if (u == null || !Passwords.matches(currentPassword, u.getPasswordHash())) {
                return false;
            }
            u.setPasswordHash(Passwords.hash(newPassword));
            s.merge(u);
            return true;
        });
    }

    public void setActive(Long userId, boolean active) {
        Db.txVoid(s -> {
            User u = s.find(User.class, userId);
            if (u != null) {
                u.setActive(active);
                s.merge(u);
            }
        });
    }

    public boolean usernameTaken(String username, Long excludingId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select count(u) from User u where u.username = :u and (:id is null or u.id <> :id)", Long.class)
                .setParameter("u", username)
                .setParameter("id", excludingId)
                .getSingleResult() > 0);
    }
}
