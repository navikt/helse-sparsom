package no.nav.helse.sparsom.api

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jillesvangurp.ktsearch.SearchClient
import io.ktor.http.*
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import java.util.UUID
import no.nav.helse.sparsom.api.config.ApplicationConfiguration
import no.nav.helse.sparsom.api.config.AzureAdAppConfig
import no.nav.helse.sparsom.api.config.KtorConfig
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

internal val objectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())
    .setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
        indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
        indentObjectsWith(DefaultIndenter("  ", "\n"))
    })

private val httpTraceLog = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val config = ApplicationConfiguration()
    val app = createApp(config.ktorConfig, config.azureConfig, config.searchClient, config.spurteDuClient, config.azureClient)
    app.start(wait = true)
}

internal fun createApp(ktorConfig: KtorConfig, azureConfig: AzureAdAppConfig, searchClient: SearchClient, spurteDuClient: SpurteDuClient, azureClient: AzureClient) =
    embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment {
            ktorConfig.configure(this)
            log = LoggerFactory.getLogger("no.nav.helse.sparsom.api.App")
            module {
                install(CallId) {
                    header("callId")
                    verify { it.isNotEmpty() }
                    generate { UUID.randomUUID().toString() }
                }
                install(CallLogging) {
                    logger = httpTraceLog
                    level = Level.INFO
                    disableDefaultColors()
                    callIdMdc("callId")
                    filter { call -> call.request.path().startsWith("/api/") }
                }
                install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
                requestResponseTracing(httpTraceLog)
                nais()
                azureAdAppAuthentication(azureConfig)
                api(searchClient, API_SERVICE, spurteDuClient, azureClient)
            }
        },
        configure = {
            this.responseWriteTimeoutSeconds = 30
        }
    )

