insert into kontekst_navn(navn)
select kontekst_navn from aktivitet_kontekst_denormalisert
on conflict do nothing;

;