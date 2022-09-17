insert into kontekst_verdi(verdi)
select distinct kontekst_verdi from aktivitet_kontekst_denormalisert order by kontekst_verdi
on conflict do nothing
;