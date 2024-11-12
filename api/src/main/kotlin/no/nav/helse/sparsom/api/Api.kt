package no.nav.helse.sparsom.api

import com.fasterxml.jackson.core.JsonParseException
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
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

internal fun Application.api(searchClient: SearchClient, authProviderName: String, spurteDuClient: SpurteDuClient, azureClient: AzureTokenProvider) {
    val dao = AktivitetDao(searchClient)
    routing {
        authenticate(authProviderName) {
            get("/api/aktiviteter") {
                withContext(Dispatchers.IO) {
                    application.log.debug("henter ident fra spurtedu")
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

private fun ApplicationCall.ident(spurteDuClient: SpurteDuClient, azureClient: AzureTokenProvider): String? {
    val ident = queryParam("ident").firstNotNullOfOrNull(::uuidOrNumericalOrNull) ?: return null
    val uuid = ident.uuidOrNull() ?: return ident
    return identFraSpurteDu(spurteDuClient, azureClient, uuid)
}

private fun ApplicationCall.identFraSpurteDu(spurteDuClient: SpurteDuClient, azureClient: AzureTokenProvider, id: UUID): String? {
    val token = bearerToken ?: return null
    val obo = azureClient.onBehalfOfToken("api://${System.getenv("NAIS_CLUSTER_NAME")}.tbd.spurtedu/.default", token).getOrThrow().token
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
