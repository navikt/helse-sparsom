DO $$BEGIN
    IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'spesialist-migrering-sparsom')
    THEN GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "spesialist-migrering-sparsom";
    END IF;
END$$;
