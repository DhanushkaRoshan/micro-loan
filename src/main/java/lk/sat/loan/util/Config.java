package lk.sat.loan.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {

    private static final Properties PROPS = load();

    private Config() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = Config.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read application.properties", e);
        }
        Path external = Path.of("application.properties");
        if (Files.isReadable(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                p.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read ./application.properties", e);
            }
        }
        return p;
    }

    public static String get(String key) {
        String env = System.getenv("LOAN_" + key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) {
            return env;
        }
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        return PROPS.getProperty(key);
    }

    public static String get(String key, String fallback) {
        String v = get(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    public static int getInt(String key, int fallback) {
        String v = get(key);
        return (v == null || v.isBlank()) ? fallback : Integer.parseInt(v.trim());
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String v = get(key);
        return (v == null || v.isBlank()) ? fallback : Boolean.parseBoolean(v.trim());
    }

    public static String dbUrl() {
        return get("db.url");
    }

    public static String dbUser() {
        return get("db.user");
    }

    public static String dbPassword() {
        return get("db.password");
    }

    public static Path reportsDir() {
        return Path.of(get("reports.dir", "reports"));
    }
}
