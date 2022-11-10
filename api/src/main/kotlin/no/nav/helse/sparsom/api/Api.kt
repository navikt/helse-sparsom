package no.nav.helse.sparsom.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.helse.sparsom.api.dao.AktivitetDao
import javax.sql.DataSource

internal fun Application.api(dataSource: DataSource, authProviderName: String) {
    val dao = AktivitetDao(dataSource)
    routing {
        authenticate(authProviderName) {
            get("/api/varsler") {
                withContext(Dispatchers.IO) {
                    call.respond(mapOf(
                        "varsler" to dao.hentVarsler()
                    ))
                }
            }
            get("/api/aktiviteter") {
                withContext(Dispatchers.IO) {
                    val kontekstNavn = call.queryParam("navn").firstNotNullOfOrNull(::alphaNumericalOnlyOrNull)
                    val kontekstVerdi = call.queryParam("verdi").firstNotNullOfOrNull(::alphaNumericalOnlyOrNull)
                    val ident = call.queryParam("ident").firstNotNullOfOrNull(::numericalOnlyOrNull)

                    val aktiviteter = when {
                        kontekstNavn != null && kontekstVerdi != null -> dao.hentAktiviteterFor(kontekstNavn, kontekstVerdi)
                        ident != null -> dao.hentAktiviteterFor(ident)
                        else -> emptyList()
                    }

                    call.respond(mapOf("aktiviteter" to aktiviteter))
                }
            }
        }
    }
}

private val re = Regex("[^A-Za-z0-9æøåÆØÅ_-]")
private fun alphaNumericalOnlyOrNull(str: String): String? {
    return re.replace(str, "").takeIf(String::isNotEmpty)
}

private val reNumerical = Regex("[^0-9]")
private fun numericalOnlyOrNull(str: String): String? {
    return reNumerical.replace(str, "").takeIf(String::isNotEmpty)
}

private fun ApplicationCall.queryParam(name: String): List<String> =
    request.queryParameters.getAll(name)?.filter(String::isNotBlank) ?: emptyList()