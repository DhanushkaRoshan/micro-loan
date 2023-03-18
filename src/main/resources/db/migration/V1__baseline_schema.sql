CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    role           VARCHAR(20)  NOT NULL DEFAULT 'OFFICER',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'OFFICER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE areas (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_areas_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE centers (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(120) NOT NULL,
    area_id    BIGINT       NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_centers_name (name),
    KEY ix_centers_area (area_id),
    CONSTRAINT fk_centers_area FOREIGN KEY (area_id) REFERENCES areas (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coordinators (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(120) NOT NULL,
    phone      VARCHAR(20)  NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coordinators_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE clients (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    client_code VARCHAR(30)  NOT NULL,
    full_name   VARCHAR(150) NOT NULL,
    nic_number  VARCHAR(20)  NULL,
    address     VARCHAR(200) NULL,
    village     VARCHAR(100) NULL,
    phone       VARCHAR(20)  NULL,
    wa_name     VARCHAR(120) NULL COMMENT 'Women''s Association (old loanin.nameofwa)',
    center_id   BIGINT       NULL,
    photo       LONGBLOB     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_clients_code (client_code),
    UNIQUE KEY uk_clients_nic (nic_number),
    KEY ix_clients_center (center_id),
    KEY ix_clients_name (full_name),
    CONSTRAINT fk_clients_center FOREIGN KEY (center_id) REFERENCES centers (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE loans (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    loan_number        VARCHAR(40)   NOT NULL,
    client_id          BIGINT        NOT NULL,
    coordinator_id     BIGINT        NULL,
    issue_date         DATE          NOT NULL,
    principal          DECIMAL(15,2) NOT NULL,
    interest_rate      DECIMAL(6,3)  NOT NULL DEFAULT 0.000 COMMENT 'Annual %, old loanin.poliya',
    interest_amount    DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT 'Interest over the full term',
    deduction          DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    first_due_date     DATE          NULL,
    installment_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT 'Per-installment Rs, old loanin.kapaganna',
    installment_count  INT           NOT NULL DEFAULT 0 COMMENT 'Number of installments, old loanin.warike',
    total_payable      DECIMAL(15,2) NOT NULL,
    group_code         VARCHAR(1)    NULL COMMENT 'Old loanin.moreloan: sub-group A-L',
    description        VARCHAR(255)  NULL,
    remarks            VARCHAR(255)  NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_loans_number (loan_number),
    KEY ix_loans_client (client_id),
    KEY ix_loans_coordinator (coordinator_id),
    KEY ix_loans_status (status),
    KEY ix_loans_issue_date (issue_date),
    CONSTRAINT fk_loans_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_loans_coordinator FOREIGN KEY (coordinator_id) REFERENCES coordinators (id) ON DELETE SET NULL,
    CONSTRAINT ck_loans_status CHECK (status IN ('ACTIVE', 'CLOSED', 'DEFAULTED', 'WRITTEN_OFF')),
    CONSTRAINT ck_loans_principal CHECK (principal >= 0),
    CONSTRAINT ck_loans_total CHECK (total_payable >= 0),
    CONSTRAINT ck_loans_rate CHECK (interest_rate >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guarantors (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    loan_id    BIGINT       NOT NULL,
    position   INT          NOT NULL,
    full_name  VARCHAR(150) NOT NULL,
    address    VARCHAR(200) NULL,
    nic_number VARCHAR(20)  NULL,
    phone      VARCHAR(20)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_guarantors_loan_pos (loan_id, position),
    CONSTRAINT fk_guarantors_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE CASCADE,
    CONSTRAINT ck_guarantors_position CHECK (position >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    loan_id       BIGINT        NOT NULL,
    receipt_no    VARCHAR(40)   NULL,
    payment_date  DATE          NOT NULL,
    amount        DECIMAL(15,2) NOT NULL,
    remark        VARCHAR(255)  NULL,
    recorded_by   BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_payments_loan (loan_id),
    KEY ix_payments_date (payment_date),
    KEY ix_payments_loan_date (loan_id, payment_date),
    CONSTRAINT fk_payments_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_user FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE installments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    loan_id         BIGINT        NOT NULL,
    installment_no  INT           NOT NULL,
    due_date        DATE          NOT NULL,
    due_amount      DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_installments_loan_no (loan_id, installment_no),
    KEY ix_installments_due (due_date),
    CONSTRAINT fk_installments_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE CASCADE,
    CONSTRAINT ck_installments_amount CHECK (due_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE VIEW loan_balances AS
SELECT
    l.id                                              AS loan_id,
    l.loan_number                                     AS loan_number,
    l.client_id                                       AS client_id,
    l.total_payable                                   AS total_payable,
    COALESCE(p.paid, 0.00)                            AS total_paid,
    l.total_payable - COALESCE(p.paid, 0.00)          AS balance,
    COALESCE(d.due_to_date, 0.00)                     AS due_to_date,
    GREATEST(COALESCE(d.due_to_date, 0.00) - COALESCE(p.paid, 0.00), 0.00) AS arrears
FROM loans l
LEFT JOIN (
    SELECT loan_id, SUM(amount) AS paid FROM payments GROUP BY loan_id
) p ON p.loan_id = l.id
LEFT JOIN (
    SELECT loan_id, SUM(due_amount) AS due_to_date
    FROM installments WHERE due_date <= CURDATE() GROUP BY loan_id
) d ON d.loan_id = l.id;
