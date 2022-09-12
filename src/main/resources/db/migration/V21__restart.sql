create table if not exists arbeidstabell_step1(
    id serial primary key,
    ident varchar,
    startet timestamptz default null,
    ferdig timestamptz default null
);
create table if not exists arbeidstabell_step2(
    id serial primary key,
    ident varchar,
    startet timestamptz default null,
    ferdig timestamptz default null
);

CREATE TABLE IF NOT EXISTS aktivitetslogg
(
    fnr  BIGSERIAL NOT NULL PRIMARY KEY,
    data TEXT      NOT NULL
);

truncate table aktivitet_kontekst;
truncate table aktivitet CASCADE;

alter table aktivitet
    add column aktivitet_uuid uuid not null unique,
    drop column hash
;