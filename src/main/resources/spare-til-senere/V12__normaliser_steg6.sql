insert into aktivitet(denormalisert_id, melding_id, personident_id, level, tidsstempel, hash)
select id, melding_id, personident_id, level, tidsstempel, hash
from aktivitet_denormalisert
on conflict do nothing;
