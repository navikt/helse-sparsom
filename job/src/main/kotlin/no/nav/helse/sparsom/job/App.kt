package no.nav.helse.sparsom.job

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.net.URI
import java.time.Duration
import javax.sql.DataSource

fun main() {
    val env = System.getenv()
    val databaseInstance = requireNotNull(env["DATABASE_INSTANCE"]) { "database instance must be set" }
    val databaseRegion: String = requireNotNull(env["DATABASE_REGION"]) { "DATABASE_REGION må settes" }
    val gcpProjectId: String = requireNotNull(env["GCP_TEAM_PROJECT_ID"]) { "GCP_TEAM_PROJECT_ID må settes" }
    val databaseName: String = requireNotNull(env["DATABASE_DATABASE"]) { "databasenavn må settes" }
    val dbConfig = HikariConfig().apply {
        jdbcUrl = String.format(
            "jdbc:postgresql:///%s?%s&%s",
            databaseName,
            "cloudSqlInstance=$gcpProjectId:$databaseRegion:$databaseInstance",
            "socketFactory=com.google.cloud.sql.postgres.SocketFactory"
        )
        username = requireNotNull(env["DATABASE_USERNAME"]) { "brukernavn må settes" }
        password = requireNotNull(env["DATABASE_PASSWORD"]) { "passord må settes" }
        initializationFailTimeout = Duration.ofMinutes(1).toMillis()
        connectionTimeout = Duration.ofMinutes(1).toMillis()
        maximumPoolSize = 1
    }

    val openSearchClient = openSearchClient(env)
    HikariDataSource(dbConfig).use { dataSource ->
        runApplication(openSearchClient, dataSource)
    }
}

private fun runApplication(openSearchClient: SearchClient, dataSource: DataSource) {
    ImporterOpenSearch(Dispatcher("arbeidstabell_opensearch", dataSource.connection))
        .migrate(openSearchClient, dataSource)
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


