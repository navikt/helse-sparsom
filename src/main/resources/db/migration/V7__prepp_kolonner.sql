alter table aktivitet alter column hendelse_id drop not null;
alter table aktivitet add column denormalisert_id bigint;
alter table aktivitet_denormalisert add melding_id bigint;
