package no.nav.helse.sparsom.api

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = LoggerFactory.getLogger(SpurteDuClient::class.java)

class SpurteDuClient(
    private val objectMapper: ObjectMapper
) {

    fun utveksleSpurteDu(token: String, id: String): String? {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI("http://spurtedu/vis_meg/$id"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        logger.debug("kaller spurtedu")
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        logger.debug("fikk svar fra spurtedu")

        return try {
            objectMapper.readTree(response.body()).path("text").asText()
        } catch (err: JsonParseException) {
            null
        }
    }
}
