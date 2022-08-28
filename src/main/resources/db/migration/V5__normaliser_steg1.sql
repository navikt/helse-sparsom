insert into kontekst_type(type)
select kontekst_type from aktivitet_kontekst_denormalisert
on conflict do nothing
;