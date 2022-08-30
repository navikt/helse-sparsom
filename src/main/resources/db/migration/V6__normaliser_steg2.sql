alter table aktivitet alter column hendelse_id drop not null;
alter table aktivitet add column denormalisert_id bigint;
alter table aktivitet_denormalisert add melding_id bigint;
-- oppdater melding_id for alle rader
update aktivitet_denormalisert as a
set melding_id = m.id
from melding as m
where m.tekst=a.melding;
