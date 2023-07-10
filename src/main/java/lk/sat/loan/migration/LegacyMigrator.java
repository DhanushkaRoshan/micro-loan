package lk.sat.loan.migration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lk.sat.loan.model.Area;
import lk.sat.loan.model.Center;
import lk.sat.loan.model.Client;
import lk.sat.loan.model.Coordinator;
import lk.sat.loan.model.Guarantor;
import lk.sat.loan.model.Installment;
import lk.sat.loan.model.Loan;
import lk.sat.loan.model.LoanStatus;
import lk.sat.loan.model.Payment;
import lk.sat.loan.model.Role;
import lk.sat.loan.model.User;
import lk.sat.loan.service.AuthService;
import lk.sat.loan.service.LoanMath;
import lk.sat.loan.util.Db;
import lk.sat.loan.util.Money;
import lk.sat.loan.util.Passwords;
import org.hibernate.Session;

public class LegacyMigrator {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
    };

    private final String url;
    private final String user;
    private final String password;
    private final MigrationReport report = new MigrationReport();

    public LegacyMigrator(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static void main(String[] args) {
        String legacyUrl = args.length > 0 ? args[0] : "jdbc:mysql://localhost:3308/sat";
        String legacyUser = args.length > 1 ? args[1] : "root";
        String legacyPassword = args.length > 2 ? args[2] : "rootpw";

        try {
            Db.start();
            new AuthService().ensureSeedAdmin();
            LegacyMigrator m = new LegacyMigrator(legacyUrl, legacyUser, legacyPassword);
            m.migrate();
            m.report.print();
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            Db.stop();
        }
    }

    public void migrate() throws SQLException {
        try (Connection legacy = DriverManager.getConnection(url, user, password)) {
            Db.tx(s -> {
                try {
                    migrateAreas(legacy, s);
                    migrateCenters(legacy, s);
                    migrateCoordinators(legacy, s);
                    migrateClients(legacy, s);
                    migrateLoans(legacy, s);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }
    }

    private void migrateAreas(Connection legacy, Session s) throws SQLException {
        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT are FROM loanin WHERE are IS NOT NULL AND are != ''")) {
            Set<String> seen = new HashSet<>();
            while (rs.next()) {
                String name = rs.getString(1).trim();
                if (!name.isEmpty() && seen.add(name)) {
                    Area a = new Area(name);
                    s.persist(a);
                    report.count("areas");
                }
            }
            s.flush();
        }
    }

    private void migrateCenters(Connection legacy, Session s) throws SQLException {
        Map<String, Area> areaMap = new HashMap<>();
        s.createSelectionQuery("from Area", Area.class).getResultList()
                .forEach(a -> areaMap.put(a.getName(), a));

        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT cen, are FROM loanin WHERE cen IS NOT NULL AND cen != ''")) {
            Set<String> seen = new HashSet<>();
            while (rs.next()) {
                String name = rs.getString(1).trim();
                String areaName = rs.getString(2) == null ? "" : rs.getString(2).trim();
                if (!name.isEmpty() && seen.add(name)) {
                    Center c = new Center(name, areaMap.get(areaName));
                    s.persist(c);
                    report.count("centers");
                }
            }
            s.flush();
        }
    }

    private void migrateCoordinators(Connection legacy, Session s) throws SQLException {
        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT cordi FROM loanin WHERE cordi IS NOT NULL AND cordi != ''")) {
            Set<String> seen = new HashSet<>();
            while (rs.next()) {
                String name = rs.getString(1).trim();
                if (!name.isEmpty() && seen.add(name)) {
                    Coordinator c = new Coordinator(name);
                    s.persist(c);
                    report.count("coordinators");
                }
            }
            s.flush();
        }
    }

    private void migrateClients(Connection legacy, Session s) throws SQLException {
        Map<String, Center> centerMap = new HashMap<>();
        s.createSelectionQuery("from Center", Center.class).getResultList()
                .forEach(c -> centerMap.put(c.getName(), c));

        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT idloanin, nama, nic, address, village, mobile, nameofwa, cen, moreloan "
                             + "FROM loanin ORDER BY idloanin")) {
            Set<String> seenNic = new HashSet<>();
            int code = 1;
            while (rs.next()) {
                String name = rs.getString("nama");
                if (name == null || name.trim().isEmpty()) {
                    report.skip("client (no name) id=" + rs.getInt("idloanin"));
                    continue;
                }

                String nic = rs.getString("nic");
                if (nic != null) {
                    nic = nic.trim();
                    if (nic.isEmpty() || !seenNic.add(nic)) {
                        nic = null;
                    }
                }

                Client c = new Client();
                c.setClientCode(String.format("C%04d", code++));
                c.setFullName(name.trim());
                c.setNicNumber(nic);
                c.setAddress(trimOrNull(rs.getString("address")));
                c.setVillage(trimOrNull(rs.getString("village")));
                c.setPhone(trimOrNull(rs.getString("mobile")));
                c.setWaName(trimOrNull(rs.getString("nameofwa")));
                c.setCenter(centerMap.get(trimOrNull(rs.getString("cen"))));
                s.persist(c);
                report.count("clients");
            }
            s.flush();
        }
    }

    private void migrateLoans(Connection legacy, Session s) throws SQLException {
        Map<String, Coordinator> coordMap = new HashMap<>();
        s.createSelectionQuery("from Coordinator", Coordinator.class).getResultList()
                .forEach(c -> coordMap.put(c.getName(), c));

        List<Client> clients = s.createSelectionQuery("from Client c order by c.id", Client.class).getResultList();
        Map<Integer, Client> clientByOldId = new HashMap<>();

        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery("SELECT idloanin FROM loanin ORDER BY idloanin")) {
            int i = 0;
            while (rs.next()) {
                if (i < clients.size()) {
                    clientByOldId.put(rs.getInt(1), clients.get(i++));
                }
            }
        }

        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT idloanin, nama, naya, poliya, warike, kapaganna, ekathuwa, "
                             + "dinaya, cordi, moreloan, drcipsion, em "
                             + "FROM loanin ORDER BY idloanin")) {
            int loanNum = 1;
            while (rs.next()) {
                int oldId = rs.getInt("idloanin");
                Client client = clientByOldId.get(oldId);
                if (client == null) {
                    report.skip("loan (no client) oldId=" + oldId);
                    continue;
                }

                BigDecimal principal = Money.of(rs.getDouble("naya"));
                BigDecimal rate = new BigDecimal(rs.getString("poliya") == null ? "0" : rs.getString("poliya").trim());
                int months = rs.getInt("warike");
                BigDecimal installment = Money.of(rs.getDouble("kapaganna"));
                BigDecimal total = Money.of(rs.getDouble("ekathuwa"));
                BigDecimal interest = LoanMath.interestForTerm(principal, rate, months);

                Loan loan = new Loan();
                loan.setLoanNumber(String.format("L%05d", loanNum++));
                loan.setClient(client);
                loan.setCoordinator(coordMap.get(trimOrNull(rs.getString("cordi"))));
                loan.setIssueDate(parseDate(rs.getString("dinaya")));
                loan.setFirstDueDate(loan.getIssueDate().plusMonths(1));
                loan.setPrincipal(principal);
                loan.setInterestRate(rate);
                loan.setInterestAmount(interest);
                loan.setInstallmentCount(months);
                loan.setInstallmentAmount(installment);
                loan.setTotalPayable(total);
                loan.setDeduction(Money.ZERO);
                loan.setGroupCode(trimOrNull(rs.getString("moreloan")));
                loan.setDescription(trimOrNull(rs.getString("drcipsion")));
                loan.setRemarks(trimOrNull(rs.getString("em")));
                s.persist(loan);

                for (Installment ins : LoanMath.buildSchedule(total, months, loan.getFirstDueDate())) {
                    ins.setLoan(loan);
                    s.persist(ins);
                }

                migratePayments(legacy, s, oldId, loan);
                report.count("loans");
                report.addAmount(total);
            }
            s.flush();
        }
    }

    private void migratePayments(Connection legacy, Session s, int oldId, Loan loan) throws SQLException {
        String table = "s" + oldId;
        try (Statement st = legacy.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT dinaya, aokaya, gana, kamaya FROM `" + table + "` ORDER BY id")) {
            BigDecimal paid = Money.ZERO;
            while (rs.next()) {
                BigDecimal amt = Money.of(rs.getDouble("gana"));
                if (amt.signum() <= 0) {
                    continue;
                }
                Payment p = new Payment();
                p.setLoan(loan);
                p.setPaymentDate(parseDate(rs.getString("dinaya")));
                p.setAmount(amt);
                p.setReceiptNo(trimOrNull(rs.getString("aokaya")));
                p.setRemark(trimOrNull(rs.getString("kamaya")));
                s.persist(p);
                paid = paid.add(amt);
                report.count("payments");
            }
            report.addCollected(paid);

            if (paid.compareTo(Money.nz(loan.getTotalPayable())) >= 0) {
                loan.setStatus(LoanStatus.CLOSED);
            }
        } catch (SQLException e) {
            report.skip("payments for loan " + loan.getLoanNumber() + " (table " + table + " not found)");
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return LocalDate.of(2014, 1, 1);
        }
        String s = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (Exception ignored) {
            }
        }
        report.skip("unparseable date: " + s);
        return LocalDate.of(2014, 1, 1);
    }

    private static String trimOrNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
