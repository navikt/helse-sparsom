CREATE TYPE LEVEL AS ENUM ('INFO', 'VARSEL', 'FUNKSJONELL_FEIL', 'LOGISK_FEIL');

CREATE TABLE hendelse(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    hendelse_id uuid NOT NULL UNIQUE,
    personidentifikator VARCHAR NOT NULL,
    hendelse json NOT NULL,
    tidsstempel timestamptz NOT NULL
);

CREATE TABLE aktivitet(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    hendelse_id BIGINT NOT NULL REFERENCES hendelse(id),
    level LEVEL NOT NULL,
    melding varchar NOT NULL,
    tidsstempel timestamptz NOT NULL,
    hash char(64) NOT NULL UNIQUE
);

CREATE TABLE kontekst(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    type VARCHAR NOT NULL,
    identifikatornavn VARCHAR NOT NULL,
    identifikator VARCHAR NOT NULL,
    CONSTRAINT kontekst_constraint UNIQUE (type, identifikatornavn, identifikator)
);

CREATE TABLE aktivitet_kontekst(
    aktivitet_id BIGINT NOT NULL REFERENCES aktivitet(id),
    kontekst_id BIGINT NOT NULL REFERENCES kontekst(id),
    PRIMARY KEY (aktivitet_id, kontekst_id)
);

CREATE INDEX idx_aktivitet ON aktivitet(level, melding, tidsstempel);
CREATE INDEX idx_personidentifikator ON hendelse(personidentifikator);