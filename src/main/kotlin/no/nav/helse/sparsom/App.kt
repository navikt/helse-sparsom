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
        AktivitetRiver(this, HendelseDao { dataSource }, AktivitetDao(dataSource))
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                dataSourceBuilder.migrate()
                val connection = dataSource.connection
                ImporterAktivitetslogg(Dispatcher("arbeidstabell_step2", connection))
                    .migrate(connection)
                exitProcess(0)
            }

            override fun onShutdown(rapidsConnection: RapidsConnection) {
                dataSource.close()
            }
        })
    }
}