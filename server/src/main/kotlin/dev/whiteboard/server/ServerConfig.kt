package dev.whiteboard.server

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

data class ServerConfig(
    val production: Boolean,
    val host: String,
    val port: Int,
    val jdbcUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val publicOrigin: String,
    val smtp: SmtpConfig?
) {
    companion object {
        fun from(env: Map<String, String> = System.getenv()): ServerConfig {
            val mode = env["WHITEBOARD_ENV"] ?: "development"
            require(mode in setOf("development", "production")) { "Invalid WHITEBOARD_ENV" }
            val production = mode == "production"
            fun required(key: String) = requireNotNull(env[key]?.takeIf { it.isNotBlank() }) { "Missing $key" }
            val host = env["WHITEBOARD_HOST"] ?: if (production) "0.0.0.0" else "127.0.0.1"
            require(production || host in setOf("127.0.0.1", "::1")) { "Development must bind to loopback" }
            val port = (env["WHITEBOARD_PORT"] ?: "8080").toInt().also { require(it in 1..65535) }
            val jdbc = if (production) {
                required("WHITEBOARD_JDBC_URL")
            } else {
                env["WHITEBOARD_JDBC_URL"] ?: "jdbc:h2:file:./build/dev-db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            }
            require(jdbc.startsWith("jdbc:postgresql:") || (!production && jdbc.startsWith("jdbc:h2:")))
            if (production) {
                val databaseUri = URI(jdbc.removePrefix("jdbc:"))
                require(databaseUri.host != null && databaseUri.userInfo == null) { "Use a TCP database host and separate credentials" }
                val parameters = databaseUri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).map {
                    URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
                        URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
                }
                val keys = parameters.map { it.first.lowercase(Locale.ROOT) }
                require(keys.distinct().size == keys.size) { "Duplicate database options are not allowed" }
                require(parameters.toMap()["sslmode"] == "verify-full") { "Production PostgreSQL requires verified TLS" }
                require(keys.none { it == "password" || it == "user" })
                require(
                    keys.none {
                        it in
                            setOf("sslfactory", "sslfactoryarg", "sslhostnameverifier", "sslpasswordcallback", "socketfactory")
                    }
                ) {
                    "Custom database TLS/socket factories are not allowed"
                }
            }
            val origin = if (production) required("WHITEBOARD_PUBLIC_ORIGIN") else "http://127.0.0.1:$port"
            val uri = URI(origin)
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
            require(uri.path.isNullOrEmpty() || uri.path == "/")
            require(uri.scheme == if (production) "https" else "http")
            val smtp = if (production || env["WHITEBOARD_SMTP_HOST"] != null) {
                SmtpConfig(
                    required("WHITEBOARD_SMTP_HOST"),
                    (env["WHITEBOARD_SMTP_PORT"] ?: "587").toInt(),
                    required("WHITEBOARD_SMTP_USER"),
                    required("WHITEBOARD_SMTP_PASSWORD"),
                    required("WHITEBOARD_MAIL_FROM")
                )
            } else {
                null
            }
            return ServerConfig(
                production,
                host,
                port,
                jdbc,
                if (production) required("WHITEBOARD_DB_USER") else env["WHITEBOARD_DB_USER"] ?: "sa",
                if (production) required("WHITEBOARD_DB_PASSWORD") else env["WHITEBOARD_DB_PASSWORD"] ?: "",
                origin.trimEnd('/'),
                smtp
            )
        }
    }
}

data class SmtpConfig(val host: String, val port: Int, val user: String, val password: String, val from: String) {
    init {
        require(port in 1..65535 && host.isNotBlank())
        require(!from.contains('\r') && !from.contains('\n'))
    }
}
