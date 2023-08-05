package lk.sat.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.DriverManager;
import lk.sat.loan.model.Role;
import lk.sat.loan.model.User;
import lk.sat.loan.service.AuthService;
import lk.sat.loan.util.Config;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Passwords;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthTest {

    @BeforeAll
    static void startDb() {
        try {
            DriverManager.getConnection(Config.dbUrl(), Config.dbUser(), Config.dbPassword()).close();
        } catch (Exception e) {
            assumeTrue(false, "Database not reachable, skipping auth tests");
        }
        Db.start();
    }

    @Test
    @DisplayName("BCrypt hash and verify round-trips")
    void hashRoundTrip() {
        String hash = Passwords.hash("secret123");
        assertTrue(Passwords.matches("secret123", hash));
        assertFalse(Passwords.matches("wrong", hash));
    }

    @Test
    @DisplayName("null and blank passwords never match")
    void nullPassword() {
        assertFalse(Passwords.matches(null, Passwords.hash("x")));
        assertFalse(Passwords.matches("x", null));
        assertFalse(Passwords.matches("x", ""));
    }

    @Test
    @DisplayName("seed admin is created on first run")
    void seedAdmin() {
        AuthService auth = new AuthService();
        auth.ensureSeedAdmin();
        var user = auth.authenticate("admin", "admin123");
        assertTrue(user.isPresent());
        assertEquals(Role.ADMIN, user.get().getRole());
    }

    @Test
    @DisplayName("authenticate rejects wrong password")
    void wrongPassword() {
        AuthService auth = new AuthService();
        auth.ensureSeedAdmin();
        assertTrue(auth.authenticate("admin", "wrongpassword").isEmpty());
    }

    @Test
    @DisplayName("authenticate rejects unknown user")
    void unknownUser() {
        AuthService auth = new AuthService();
        assertTrue(auth.authenticate("nonexistent", "anything").isEmpty());
    }
}
