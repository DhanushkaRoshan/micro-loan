package lk.sat.loan.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DecimalFormat DISPLAY = new DecimalFormat("#,##0.00");

    private Money() {
    }

    public static BigDecimal of(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZERO;
        }
        return new BigDecimal(raw.trim().replace(",", "")).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal floorZero(BigDecimal v) {
        BigDecimal x = nz(v);
        return x.signum() < 0 ? ZERO : x;
    }

    public static String format(BigDecimal v) {
        return DISPLAY.format(nz(v));
    }
}
