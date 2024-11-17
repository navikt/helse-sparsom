package no.nav.helse.sparsom.api.config

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import io.ktor.server.auth.jwt.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

internal class ApplicationConfiguration(env: Map<String, String> = System.getenv()) {
    internal val azureConfig = AzureAdAppConfig(
        clientId = env.getValue("AZURE_APP_CLIENT_ID"),
        configurationUrl = env.getValue("AZURE_APP_WELL_KNOWN_URL")
    )

    internal val searchClient by lazy { openSearchClient(env) }
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

internal class AzureAdAppConfig(private val clientId: String, configurationUrl: String) {
    private val issuer: String
    private val jwkProvider: JwkProvider
    private val jwksUri: String

    init {
        configurationUrl.getJson().also {
            this.issuer = it["issuer"].textValue()
            this.jwksUri = it["jwks_uri"].textValue()
        }

        jwkProvider = JwkProviderBuilder(URI(this.jwksUri).toURL()).build()
    }

    fun configureVerification(configuration: JWTAuthenticationProvider.Config) {
        configuration.verifier(jwkProvider, issuer) {
            withAudience(clientId)
        }
        configuration.validate { credentials -> JWTPrincipal(credentials.payload) }
    }

    private fun String.getJson(): JsonNode {
        val (responseCode, responseBody) = this.fetchUrl()
        if (responseCode >= 300 || responseBody == null) throw RuntimeException("got status $responseCode from ${this}.")
        return jacksonObjectMapper().readTree(responseBody)
    }

    private fun String.fetchUrl() = with(URI(this).toURL().openConnection() as HttpURLConnection) {
        requestMethod = "GET"
        val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
        responseCode to stream?.bufferedReader()?.readText()
    }
}
