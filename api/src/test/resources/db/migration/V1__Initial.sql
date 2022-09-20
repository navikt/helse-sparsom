CREATE TYPE LEVEL AS ENUM ('INFO', 'VARSEL', 'FUNKSJONELL_FEIL', 'LOGISK_FEIL');

CREATE TABLE personident(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    ident varchar NOT NULL UNIQUE
);
CREATE INDEX idx_personident ON personident(ident);

CREATE TABLE hendelse(
     id BIGSERIAL NOT NULL PRIMARY KEY,
     hendelse_id uuid NOT NULL UNIQUE,
     personident_id BIGINT NOT NULL REFERENCES personident(id),
     hendelse json NOT NULL,
     tidsstempel timestamptz NOT NULL
);
CREATE INDEX idx_hendelse_personident_fk ON hendelse(personident_id);

CREATE TABLE melding(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    tekst varchar NOT NULL unique
);

CREATE TABLE kontekst_type (
   id BIGSERIAL NOT NULL PRIMARY KEY,
   type VARCHAR NOT NULL UNIQUE
);

CREATE TABLE kontekst_navn (
   id BIGSERIAL NOT NULL PRIMARY KEY,
   navn VARCHAR NOT NULL UNIQUE
);

CREATE TABLE kontekst_verdi (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    verdi VARCHAR NOT NULL UNIQUE
);

CREATE TABLE aktivitet(
      id BIGSERIAL NOT NULL PRIMARY KEY,
      melding_id BIGINT NOT NULL REFERENCES melding(id),
      personident_id BIGINT NOT NULL REFERENCES personident(id),
      hendelse_id BIGINT REFERENCES hendelse(id),
      level LEVEL NOT NULL,
      tidsstempel timestamptz NOT NULL,
      hash char(64) NOT NULL UNIQUE
);

CREATE INDEX idx_level_error_melding on aktivitet(level,melding_id) where level='FUNKSJONELL_FEIL'::level;
CREATE INDEX idx_level_varsel_melding on aktivitet(level,melding_id) where level='VARSEL'::level;
CREATE INDEX idx_aktivitet_personident_fk ON aktivitet(personident_id);
CREATE INDEX idx_aktivitet_hendelse_fk ON aktivitet(hendelse_id);

CREATE TABLE aktivitet_kontekst(
    aktivitet_id BIGINT NOT NULL REFERENCES aktivitet(id),
    kontekst_type_id BIGINT NOT NULL REFERENCES kontekst_type(id),
    kontekst_navn_id BIGINT NOT NULL REFERENCES kontekst_navn(id),
    kontekst_verdi_id BIGINT NOT NULL REFERENCES kontekst_verdi(id),
    PRIMARY KEY (aktivitet_id, kontekst_type_id, kontekst_navn_id, kontekst_verdi_id)
);
CREATE INDEX idx_aktivitet_kontekst_multi on aktivitet_kontekst(kontekst_navn_id,kontekst_verdi_id);