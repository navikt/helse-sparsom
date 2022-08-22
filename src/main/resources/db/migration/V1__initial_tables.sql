CREATE TYPE NIVÅ AS ENUM ('INFO', 'VARSEL', 'FUNKSJONELL_FEIL', 'LOGISK_FEIL');

CREATE TABLE hendelse(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    hendelse_id uuid NOT NULL UNIQUE,
    personidentifikator VARCHAR NOT NULL,
    hendelse json NOT NULL,
    tidsstempel timestamptz NOT NULL
);

CREATE TABLE aktivitet(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    nivå NIVÅ NOT NULL,
    melding varchar NOT NULL,
    tidsstempel timestamptz NOT NULL
);

CREATE TABLE kontekst(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    type VARCHAR NOT NULL,
    identifikatornavn VARCHAR NOT NULL,
    identifikator VARCHAR NOT NULL,
    CONSTRAINT kontekst_constraint UNIQUE (type, identifikatornavn, identifikator)
);

CREATE TABLE aktivitet_kontekst(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    aktivitet_ref BIGINT NOT NULL REFERENCES aktivitet(id),
    kontekst_ref BIGINT NOT NULL REFERENCES kontekst(id),
    hendelse_ref BIGINT NOT NULL REFERENCES hendelse(id)
);
