package no.nav.helse.sparsom

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory
import java.net.URI

const val opensearchIndexnavn = "aktivitetslogg"

private val log = LoggerFactory.getLogger("no.nav.helse.sparsom.App")

fun main() {
    val app = createApp(System.getenv())
    app.start()
}

private fun createApp(env: Map<String, String>): RapidsConnection {
    val openSearchClient = openSearchClient(env)
    return RapidApplication.create(env).apply {
        AktivitetRiver(this, openSearchClient)
    }
}

private fun openSearchClient(env: Map<String, String>): SearchClient {
    val uri = URI(env.getValue("OPEN_SEARCH_URI"))
    return SearchClient(KtorRestClient(
        host = uri.host,
        https = uri.scheme.lowercase()== "https",
        port = uri.port,
        user = env.getValue("OPEN_SEARCH_USERNAME"),
        password = env.getValue("OPEN_SEARCH_PASSWORD")
    ))
}