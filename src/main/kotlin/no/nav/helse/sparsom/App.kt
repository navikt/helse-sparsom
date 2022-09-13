package no.nav.helse.sparsom

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.sparsom.db.AktivitetDao
import no.nav.helse.sparsom.db.DataSourceBuilder
import no.nav.helse.sparsom.db.HendelseDao
import java.time.Duration
import kotlin.system.exitProcess

fun main() {
    val app = createApp(System.getenv())
    app.start()
}

private fun createApp(env: Map<String, String>): RapidsConnection {
    val dataSourceBuilder = DataSourceBuilder(env)
    val dataSource by lazy { dataSourceBuilder.getDataSource() }
    return RapidApplication.create(env).apply {
        val aktivitetFactory = AktivitetFactory(AktivitetDao(dataSource))
        AktivitetRiver(this, HendelseDao { dataSource }, aktivitetFactory)
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                dataSourceBuilder.migrate()

                val connection = dataSource.connection

                /*val spleisConfig = HikariConfig().apply {
                    jdbcUrl = String.format(
                        "jdbc:postgresql:///%s?%s&%s",
                        env["DATABASE_SPARSOM_DATABASE"],
                        "cloudSqlInstance=${env["GCP_TEAM_PROJECT_ID"]}:${env["DATABASE_SPARSOM_REGION"]}:${env["DATABASE_SPARSOM_INSTANCE"]}",
                        "socketFactory=com.google.cloud.sql.postgres.SocketFactory"
                    )
                    username = env["DATABASE_SPARSOM_USERNAME"]
                    password = env["DATABASE_SPARSOM_PASSWORD"]
                    initializationFailTimeout = Duration.ofMinutes(1).toMillis()
                    connectionTimeout = Duration.ofMinutes(1).toMillis()
                    maximumPoolSize = 1
                }
                val spleisDs = HikariDataSource(spleisConfig)
                HentAktivitetslogg(Dispatcher("arbeidstabell_step1", connection))
                    .migrate(connection, spleisDs.connection)*/

                ImporterAktivitetslogg(Dispatcher("arbeidstabell_step2", connection))
                    .migrate(connection)
            }

            override fun onShutdown(rapidsConnection: RapidsConnection) {
                dataSource.close()
            }
        })
    }
}