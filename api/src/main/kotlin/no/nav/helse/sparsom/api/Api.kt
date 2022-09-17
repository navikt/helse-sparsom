package no.nav.helse.sparsom.api

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
        get("/api/varsler") {
            withContext(Dispatchers.IO) {
                call.respond(mapOf(
                    "varsler" to dao.hentVarsler()
                ))
            }
        }

        authenticate(authProviderName) {

        }
    }
}