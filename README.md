# Sat Loan Management System

> Originally built in 2022. Uploaded to GitHub in 2026.

A microfinance loan management system built with **JavaFX + Hibernate + MySQL**.

---

## Quick start

```bash
docker compose up -d mysql                 # MySQL 8.4 on port 3307
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.Bootstrap -Dexec.args="--demo"
mvn javafx:run                             # or: mvn package && java -jar target/loan-system-2.0.0.jar
```

Sign in with **admin / admin123** and change the password from the sidebar.

Requirements: JDK 17+, Maven 3.9+, Docker.

Locked out? This creates or resets an administrator:

```bash
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.CreateAdmin
# or pick the credentials:
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.CreateAdmin \
    -Dexec.args="manager s3cret-pass"
```

---

## Layout

```
src/main/java/lk/sat/loan/
  App.java, Launcher.java     JavaFX entry points
  model/                      JPA entities
  service/                    business logic (LoanMath, balances, payments, auth)
  report/                     ReportTable + PDF/CSV exporters + ReportService
  ui/                         JavaFX views, one per screen
  migration/                  LegacyMigrator + MigrationReport
  tools/                      Bootstrap, ExportReports, UiSnapshot
src/main/resources/
  db/migration/V1__baseline_schema.sql    the schema (Flyway)
  application.properties, css/app.css
```

**Flyway owns the schema; Hibernate is set to `validate`.** Change the database by
adding `V2__*.sql`, never by editing `V1` or letting Hibernate generate DDL.

### Deriving money figures

`LoanBalance` (total paid, balance, due-to-date, arrears) is computed on read.
`LoanService.balances(...)` loads figures for a whole list in one query — use it
in list screens rather than calling `balanceOf` per row.

---

## Commands

```bash
mvn test                      # unit tests (LoanMath, LoanBalance)
mvn javafx:run                # run from source
mvn package                   # fat jar -> target/loan-system-2.0.0.jar
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.Bootstrap
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.Bootstrap -Dexec.args="--demo"
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.CreateAdmin    # recover a locked-out system
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.ExportReports
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.UiSnapshot   # renders every screen to PNG
mvn test -Dtest=LoanMathTest#scheduleSumsExactly                            # a single test
```

`UiSnapshot` also works as a UI smoke test — it fails if any view throws while
loading its data.

## Configuration

`src/main/resources/application.properties`, overridden by an
`application.properties` next to the jar, overridden by environment variables:

| Setting | Env var | Default |
|---|---|---|
| `db.url` | `LOAN_DB_URL` | `jdbc:mysql://localhost:3307/loandb?...` |
| `db.user` | `LOAN_DB_USER` | `loanapp` |
| `db.password` | `LOAN_DB_PASSWORD` | `loanapp` |
| `reports.dir` | `LOAN_REPORTS_DIR` | `reports` |

Change the MySQL credentials in `docker-compose.yml` before putting this on a
shared machine — the defaults are development values.
