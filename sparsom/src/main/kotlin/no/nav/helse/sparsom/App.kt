package no.nav.helse.sparsom

import com.jillesvangurp.ktsearch.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.sparsom.db.AktivitetDao
import no.nav.helse.sparsom.db.DataSourceBuilder
import no.nav.helse.sparsom.db.HendelseDao
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.net.URI

const val opensearchIndexnavn = "aktivitetslogg_v1"

private val log = LoggerFactory.getLogger("no.nav.helse.sparsom.App")

fun main() {
    val app = createApp(System.getenv())
    app.start()
}

private fun createApp(env: Map<String, String>): RapidsConnection {
    val dataSourceBuilder = DataSourceBuilder(env)
    val dataSource by lazy { dataSourceBuilder.getDataSource() }
    val openSearchClient = openSearchClient(env)
    return RapidApplication.create(env).apply {
        AktivitetRiver(this, HendelseDao { dataSource }, AktivitetDao(dataSource), openSearchClient)
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                dataSourceBuilder.migrate()
            }

            override fun onShutdown(rapidsConnection: RapidsConnection) {
                dataSource.close()
            }
        })
    }
}

private fun openSearchClient(env: Map<String, String>): SearchClient? {
    if ("OPEN_SEARCH_PASSWORD" !in env) return null
    val uri = URI(env.getValue("OPEN_SEARCH_URI"))
    return SearchClient(KtorRestClient(
        host = uri.host,
        https = uri.scheme.lowercase()== "https",
        port = uri.port,
        user = env.getValue("OPEN_SEARCH_USERNAME"),
        password = env.getValue("OPEN_SEARCH_PASSWORD")
    ))
}