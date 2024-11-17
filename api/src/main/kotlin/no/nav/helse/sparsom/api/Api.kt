package no.nav.helse.sparsom.api

import com.jillesvangurp.ktsearch.SearchClient
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.sparsom.api.dao.AktivitetDao

internal fun Application.api(searchClient: SearchClient, authProviderName: String) {
    val dao = AktivitetDao(searchClient)
    routing {
        authenticate(authProviderName) {
            post("/api/aktiviteter") {
                withContext(Dispatchers.IO) {
                    application.log.debug("henter ident fra spurtedu")
                    val request = call.receive<AktiviteterRequest>()
                    call.respond(mapOf("aktiviteter" to dao.hentAktiviteterFor(request.ident)))
                }
            }
        }
    }
}

private data class AktiviteterRequest(val ident: String)

private fun ApplicationCall.ident(): String? {
    return queryParam("ident").firstNotNullOfOrNull(::numericalOnlyOrNull)
}

private val reNumerical = Regex("[^0-9]")
private fun numericalOnlyOrNull(str: String): String? {
    return reNumerical.replace(str, "").takeIf(String::isNotEmpty)
}

private fun ApplicationCall.queryParam(name: String): List<String> =
    request.queryParameters.getAll(name)?.filter(String::isNotBlank) ?: emptyList()
