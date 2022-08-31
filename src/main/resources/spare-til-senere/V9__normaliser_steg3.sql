insert into kontekst_verdi(verdi)
select kontekst_verdi from aktivitet_kontekst_denormalisert
on conflict do nothing;
