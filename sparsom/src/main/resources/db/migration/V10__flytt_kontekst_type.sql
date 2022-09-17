insert into kontekst_type(type)
select distinct kontekst_type from aktivitet_kontekst_denormalisert order by kontekst_type
on conflict do nothing
;