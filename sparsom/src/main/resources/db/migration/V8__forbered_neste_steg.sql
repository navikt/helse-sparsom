delete from arbeidstabell where 1=1;
drop index if exists aktivitet_denorm_melding;
drop table aktivitet_denormalisert;


create index if not exists aktivitet_melding_denorm_id on aktivitet(denormalisert_id);