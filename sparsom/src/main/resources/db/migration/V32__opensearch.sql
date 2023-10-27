create table if not exists arbeidstabell_opensearch(
    id serial primary key,
    ident varchar,
    startet timestamptz default null,
    ferdig timestamptz default null
);