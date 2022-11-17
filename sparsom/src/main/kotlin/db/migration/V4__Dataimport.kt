package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.intellij.lang.annotations.Language

internal class V4__Dataimport : BaseJavaMigration() {
    override fun migrate(context: Context) {
        context.connection.createStatement().use {
            it.execute(datalasttabeller)
        }
    }

    private companion object {
        @Language("PostgreSQL")
        private val datalasttabeller = """
CREATE TABLE aktivitet_denormalisert(
    id BIGSERIAL NOT NULL PRIMARY KEY,
    personident_id BIGINT NOT NULL,
    hendelse_id BIGINT,
    level LEVEL NOT NULL,
    melding text not null,
    tidsstempel timestamptz NOT NULL,
    hash char(64) NOT NULL
);

CREATE TABLE aktivitet_kontekst_denormalisert (
    aktivitet_id BIGINT NOT NULL,
    kontekst_type varchar NOT NULL,
    kontekst_navn varchar NOT NULL,
    kontekst_verdi varchar not null
);
"""
    }
}