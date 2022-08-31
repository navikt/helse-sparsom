-- oppdater melding_id for alle rader
update aktivitet_denormalisert as a
set melding_id = m.id
from melding as m
where m.tekst=a.melding;
