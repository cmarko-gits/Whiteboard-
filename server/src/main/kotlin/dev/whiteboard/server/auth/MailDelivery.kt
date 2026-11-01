package dev.whiteboard.server.auth

import dev.whiteboard.server.SmtpConfig
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties
import org.json.JSONObject

fun interface MailDelivery {
    fun send(email: String, purpose: String, token: String)
}

/** Development only: tokens are never returned by public API endpoints or written to application logs. */
class DevelopmentMail(private val path: Path) : MailDelivery {
    @Synchronized
    override fun send(email: String, purpose: String, token: String) {
        Files.createDirectories(path.toAbsolutePath().parent)
        Files.writeString(
            path,
            JSONObject().put("email", email).put("purpose", purpose).put("token", token).toString() + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }
}

class SmtpMail(private val config: SmtpConfig) : MailDelivery {
    private val session = Session.getInstance(
        Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.ssl.checkserveridentity", "true")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        }
    )

    override fun send(email: String, purpose: String, token: String) {
        val subject = if (purpose == "verify") "Verify your Whiteboard account" else "Reset your Whiteboard password"
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.from, true))
            setRecipient(Message.RecipientType.TO, InternetAddress(email, true))
            setSubject(subject, "UTF-8")
            setText("$subject\n\nYour one-time code:\n$token\n\nIf you did not request this, ignore this email.", "UTF-8")
        }
        session.getTransport("smtp").use { transport ->
            transport.connect(config.host, config.port, config.user, config.password)
            transport.sendMessage(message, message.allRecipients)
        }
    }
}
