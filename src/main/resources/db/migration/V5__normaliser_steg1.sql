insert into melding(tekst)
select melding from aktivitet_denormalisert
on conflict (tekst) do nothing;
