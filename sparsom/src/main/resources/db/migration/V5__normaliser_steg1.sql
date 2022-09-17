create index if not exists aktivitet_denorm_melding on aktivitet_denormalisert(melding);
insert into melding(tekst)
select distinct melding from aktivitet_denormalisert order by melding
on conflict do nothing
;