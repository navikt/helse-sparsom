package no.nav.helse.sparsom.api

import com.fasterxml.jackson.core.JsonParseException
import com.jillesvangurp.ktsearch.SearchClient
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.sparsom.api.dao.AktivitetDao
import java.util.*

internal fun Application.api(searchClient: SearchClient, authProviderName: String, spurteDuClient: SpurteDuClient, azureClient: AzureClient) {
    val dao = AktivitetDao(searchClient)
    routing {
        authenticate(authProviderName) {
            get("/api/aktiviteter") {
                withContext(Dispatchers.IO) {
                    val ident = call.ident(spurteDuClient, azureClient)
                    val aktiviteter = when {
                        ident != null -> dao.hentAktiviteterFor(ident)
                        else -> emptyList()
                    }

                    call.respond(mapOf("aktiviteter" to aktiviteter))
                }
            }
        }
    }
}

private suspend fun ApplicationCall.ident(spurteDuClient: SpurteDuClient, azureClient: AzureClient): String? {
    val ident = queryParam("ident").firstNotNullOfOrNull(::uuidOrNumericalOrNull) ?: return null
    val uuid = ident.uuidOrNull() ?: return ident
    return identFraSpurteDu(spurteDuClient, azureClient, uuid)
}

private suspend fun ApplicationCall.identFraSpurteDu(spurteDuClient: SpurteDuClient, azureClient: AzureClient, id: UUID): String? {
    val token = bearerToken ?: return null
    val obo = azureClient.veksleTilOnBehalfOf(token, "api://${System.getenv("NAIS_CLUSTER_NAME")}.tbd.spurtedu/.default")
    val tekstinnhold = spurteDuClient.utveksleSpurteDu(obo, id.toString()) ?: return null
    return try {
        val node = objectMapper.readTree(tekstinnhold)
        return node.path("ident").asText()
    } catch (err: JsonParseException) {
        null
    }
}

private val ApplicationCall.bearerToken: String? get() {
    val httpAuthHeader = request.parseAuthorizationHeader() ?: return null
    if (httpAuthHeader !is HttpAuthHeader.Single) return null
    return httpAuthHeader.blob
}

private fun String.uuidOrNull() = try { UUID.fromString(this) } catch (err: Exception) { null }

private val re = Regex("[^A-Fa-f0-9-]")
private fun uuidOrNumericalOrNull(str: String): String? {
    return re.replace(str, "").takeIf(String::isNotEmpty)
}

private val reNumerical = Regex("[^0-9]")
private fun numericalOnlyOrNull(str: String): String? {
    return reNumerical.replace(str, "").takeIf(String::isNotEmpty)
}

private fun ApplicationCall.queryParam(name: String): List<String> =
    request.queryParameters.getAll(name)?.filter(String::isNotBlank) ?: emptyList()