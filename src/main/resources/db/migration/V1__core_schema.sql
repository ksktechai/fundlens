-- FundLens core schema

CREATE TABLE funds (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                  varchar(255) NOT NULL,
    provider              varchar(255) NOT NULL,
    disclose_fund_number  varchar(64) UNIQUE,
    disclose_offer_number varchar(64),
    disclose_etag         varchar(255),
    status                varchar(32),
    classification        varchar(64),
    risk_indicator        integer,
    description           text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE fund_metrics (
    id                              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fund_id                         bigint NOT NULL REFERENCES funds (id),
    period_end                      date NOT NULL,
    total_annual_fund_charge        numeric(8, 4),
    managers_basic_fee              numeric(8, 4),
    performance_based_fees          numeric(8, 4),
    contribution_fee                numeric(8, 4),
    withdrawal_fee                  numeric(8, 4),
    past_year_return_net            numeric(8, 4),
    avg_five_year_return_net        numeric(8, 4),
    market_index_past_year_return   numeric(8, 4),
    total_fund_value                numeric(18, 2),
    number_of_investors             integer,
    investment_mix                  text,
    top_ten_investments             text,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_fund_metrics_period UNIQUE (fund_id, period_end)
);

CREATE TABLE fund_documents (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fund_id     bigint REFERENCES funds (id),
    title       varchar(512) NOT NULL,
    provider    varchar(255),
    doc_type    varchar(32) NOT NULL,
    period_end  date,
    source      varchar(64) NOT NULL,
    chunk_count integer NOT NULL DEFAULT 0,
    ingested_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE sync_runs (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    started_at  timestamptz NOT NULL,
    finished_at timestamptz,
    triggered_by varchar(16) NOT NULL,
    status      varchar(16) NOT NULL,
    outcomes    text
);

-- Append-only audit trail: no updated_at, and UPDATE/DELETE are rejected at
-- the database level so the decision trail is immutable.
CREATE TABLE audit_records (
    id                 uuid PRIMARY KEY,
    created_at         timestamptz NOT NULL,
    question           text NOT NULL,
    audience           varchar(16),
    fund_ids           text,
    retrieved_chunks   text,
    research_findings  text,
    drafts             text,
    compliance_results text,
    final_answer       text,
    status             varchar(32) NOT NULL,
    model_name         varchar(128),
    latency_ms         bigint NOT NULL DEFAULT 0
);

CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_records is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_records_immutable
    BEFORE UPDATE OR DELETE ON audit_records
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
