insert into kontekst_navn(navn)
select distinct kontekst_navn from aktivitet_kontekst_denormalisert order by kontekst_navn
on conflict do nothing
;