package dev.whiteboard.server

import dev.whiteboard.server.auth.AuthService
import dev.whiteboard.server.auth.DevelopmentMail
import dev.whiteboard.server.auth.SmtpMail
import dev.whiteboard.server.boards.BoardService
import dev.whiteboard.server.database.Database
import dev.whiteboard.server.http.whiteboardApi
import dev.whiteboard.server.security.ArgonPasswords
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.nio.file.Path

fun main() {
    val config = ServerConfig.from()
    val database = Database(config)
    try {
        database.migrate()
        val mail = config.smtp?.let(::SmtpMail) ?: DevelopmentMail(Path.of("build", "dev-mail.jsonl"))
        val auth = AuthService(database, ArgonPasswords(), mail)
        Runtime.getRuntime().addShutdownHook(Thread { database.close() })
        embeddedServer(Netty, host = config.host, port = config.port) {
            whiteboardApi(auth, BoardService(auth))
        }.start(wait = true)
    } finally {
        database.close()
    }
}
