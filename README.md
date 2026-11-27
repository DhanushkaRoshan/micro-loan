# Sat Loan Management System

> Originally built in 2022. Uploaded to GitHub in 2026.

A microfinance loan management system built with **JavaFX + Hibernate + MySQL**.

## Quick start

```bash
cp .env.example .env                       # set database credentials
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

## Commands

```bash
mvn test                      # unit tests (LoanMath, LoanBalance)
mvn javafx:run                # run from source
mvn package                   # fat jar -> target/loan-system-2.0.0.jar
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.Bootstrap
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.Bootstrap -Dexec.args="--demo"
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.CreateAdmin
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.ExportReports
mvn compile exec:java -Dexec.mainClass=lk.sat.loan.tools.UiSnapshot
```

## Configuration

`application.properties`, overridden by environment variables:

| Setting | Env var | Default |
|---|---|---|
| `db.url` | `LOAN_DB_URL` | `jdbc:mysql://localhost:3307/loandb?...` |
| `db.user` | `LOAN_DB_USER` | — |
| `db.password` | `LOAN_DB_PASSWORD` | — |
| `reports.dir` | `LOAN_REPORTS_DIR` | `reports` |
