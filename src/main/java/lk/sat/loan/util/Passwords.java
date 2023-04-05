package lk.sat.loan.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class Passwords {

    private static final int COST = 12;

    private Passwords() {
    }

    public static String hash(String raw) {
        return BCrypt.withDefaults().hashToString(COST, raw.toCharArray());
    }

    public static boolean matches(String raw, String hash) {
        if (raw == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.verifyer().verify(raw.toCharArray(), hash).verified;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
