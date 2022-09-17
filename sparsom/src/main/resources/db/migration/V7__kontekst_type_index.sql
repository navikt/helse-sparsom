alter table aktivitet
    alter column hendelse_id drop not null,
    add column denormalisert_id bigint
;
update arbeidstabell set startet=null, ferdig=null;