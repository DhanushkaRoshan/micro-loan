package lk.sat.loan.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import lk.sat.loan.model.Area;
import lk.sat.loan.model.Center;
import lk.sat.loan.model.Client;
import lk.sat.loan.model.Coordinator;
import lk.sat.loan.model.Guarantor;
import lk.sat.loan.model.Installment;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.Payment;
import lk.sat.loan.model.User;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Db {

    private static final Logger log = LoggerFactory.getLogger(Db.class);
    private static volatile SessionFactory sessionFactory;

    private Db() {
    }

    public static synchronized void start() {
        if (sessionFactory != null) {
            return;
        }
        migrate();
        sessionFactory = buildSessionFactory();
        log.info("Persistence ready at {}", Config.dbUrl().replaceAll("\\?.*$", ""));
    }

    public static void migrate() {
        Flyway.configure()
                .dataSource(Config.dbUrl(), Config.dbUser(), Config.dbPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private static SessionFactory buildSessionFactory() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("jakarta.persistence.jdbc.url", Config.dbUrl());
        settings.put("jakarta.persistence.jdbc.user", Config.dbUser());
        settings.put("jakarta.persistence.jdbc.password", Config.dbPassword());
        settings.put("hibernate.hbm2ddl.auto", Config.get("hibernate.hbm2ddl.auto", "validate"));
        settings.put("hibernate.show_sql", Config.get("hibernate.show_sql", "false"));
        settings.put("hibernate.format_sql", "true");
        settings.put("hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        settings.put("hibernate.hikari.maximumPoolSize", String.valueOf(Config.getInt("db.pool.size", 10)));
        settings.put("hibernate.hikari.minimumIdle", "2");
        settings.put("hibernate.hikari.poolName", "loan-pool");

        Configuration cfg = new Configuration();
        settings.forEach((k, v) -> cfg.setProperty(k, String.valueOf(v)));
        cfg.addAnnotatedClass(User.class)
                .addAnnotatedClass(Area.class)
                .addAnnotatedClass(Center.class)
                .addAnnotatedClass(Coordinator.class)
                .addAnnotatedClass(Client.class)
                .addAnnotatedClass(Loan.class)
                .addAnnotatedClass(Guarantor.class)
                .addAnnotatedClass(Payment.class)
                .addAnnotatedClass(Installment.class);
        return cfg.buildSessionFactory();
    }

    public static SessionFactory sessionFactory() {
        if (sessionFactory == null) {
            start();
        }
        return sessionFactory;
    }

    public static <T> T tx(Function<org.hibernate.Session, T> work) {
        return sessionFactory().fromTransaction(work::apply);
    }

    public static void txVoid(Consumer<org.hibernate.Session> work) {
        sessionFactory().inTransaction(work::accept);
    }

    public static <T> T read(Function<org.hibernate.Session, T> work) {
        return sessionFactory().fromSession(work::apply);
    }

    public static synchronized void stop() {
        if (sessionFactory != null) {
            sessionFactory.close();
            sessionFactory = null;
        }
    }
}
