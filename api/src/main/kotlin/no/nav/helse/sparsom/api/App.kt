package no.nav.helse.sparsom.api

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.naisful.naisApp
import com.jillesvangurp.ktsearch.SearchClient
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.helse.sparsom.api.config.ApplicationConfiguration
import no.nav.helse.sparsom.api.config.AzureAdAppConfig
import org.slf4j.LoggerFactory

internal val objectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())
    .setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
        indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
        indentObjectsWith(DefaultIndenter("  ", "\n"))
    })

fun main() {
    val config = ApplicationConfiguration()
    val app = createApp(config.azureConfig, config.searchClient, config.spurteDuClient, config.azureClient)
    app.start(wait = true)
}

internal fun createApp(azureConfig: AzureAdAppConfig, searchClient: SearchClient, spurteDuClient: SpurteDuClient, azureClient: AzureTokenProvider) =
    naisApp(
        meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM),
        objectMapper = objectMapper,
        applicationLogger = LoggerFactory.getLogger(::main::class.java),
        callLogger = LoggerFactory.getLogger("tjenestekall")
    ) {
        azureAdAppAuthentication(azureConfig)
        api(searchClient, API_SERVICE, spurteDuClient, azureClient)
    }

