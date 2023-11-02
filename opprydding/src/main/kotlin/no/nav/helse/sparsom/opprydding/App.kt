package no.nav.helse.sparsom.opprydding

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.deleteByQuery
import com.jillesvangurp.searchdsls.querydsl.term
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.net.URI

private val log = LoggerFactory.getLogger("no.nav.helse.sparsom.opprydding.App")
private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()

    val app = RapidApplication.create(env).apply {
        Opprydding(this, openSearchClient(env))
    }

    app.start()
}

private fun openSearchClient(env: Map<String, String>): SearchClient {
    val uri = URI(env.getValue("OPEN_SEARCH_URI"))
    return SearchClient(
        KtorRestClient(
            host = uri.host,
            https = uri.scheme.lowercase() == "https",
            port = uri.port,
            user = env.getValue("OPEN_SEARCH_USERNAME"),
            password = env.getValue("OPEN_SEARCH_PASSWORD")
        )
    )
}


private class Opprydding(rapidsConnection: RapidsConnection, private val searchClient: SearchClient) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "slett_person")
                it.requireKey("@id", "fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet["fødselsnummer"].asText()
        runBlocking {
            searchClient.deleteByQuery("aktivitetslogg") {
                query = term("fødselsnummer", ident)
            }
        }
        log.info("sletter testdata for testperson $ident")
        sikkerLogg.info("sletter testdata for testperson $ident")
    }
}
