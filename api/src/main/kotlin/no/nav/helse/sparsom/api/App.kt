package no.nav.helse.sparsom.api

import com.github.navikt.tbd_libs.naisful.naisApp
import com.jillesvangurp.ktsearch.SearchClient
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.helse.sparsom.api.config.ApplicationConfiguration
import no.nav.helse.sparsom.api.config.AzureAdAppConfig
import org.slf4j.LoggerFactory
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.module.kotlin.jacksonMapperBuilder

internal val objectMapper =
    jacksonMapperBuilder()
        .defaultPrettyPrinter(
            DefaultPrettyPrinter()
                .withArrayIndenter(DefaultPrettyPrinter.FixedSpaceIndenter.instance())
                .withObjectIndenter(DefaultIndenter("  ", "\n")),
        ).build()

fun main() {
    val config = ApplicationConfiguration()
    val app = createApp(config.azureConfig, config.searchClient)
    app.start(wait = true)
}

internal fun createApp(
    azureConfig: AzureAdAppConfig,
    searchClient: SearchClient,
) = naisApp(
    meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM),
    objectMapper = objectMapper,
    applicationLogger = LoggerFactory.getLogger(::main::class.java),
    callLogger = LoggerFactory.getLogger("tjenestekall"),
    timersConfig = { call, _ ->
        this
            .tag("azp_name", call.principal<JWTPrincipal>()?.get("azp_name") ?: "n/a")
            // https://github.com/linkerd/polixy/blob/main/DESIGN.md#l5d-client-id-client-id
            // eksempel: <APP>.<NAMESPACE>.serviceaccount.identity.linkerd.cluster.local
            .tag("konsument", call.request.header("L5d-Client-Id") ?: "n/a")
    },
    mdcEntries =
        mapOf(
            "azp_name" to { call: ApplicationCall -> call.principal<JWTPrincipal>()?.get("azp_name") },
            "konsument" to { call: ApplicationCall -> call.request.header("L5d-Client-Id") },
        ),
) {
    azureAdAppAuthentication(azureConfig)
    api(searchClient, API_SERVICE)
}
