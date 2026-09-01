package ch.ddis.speakeasy.mail

import ch.ddis.speakeasy.util.Config
import jakarta.activation.DataHandler
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

data class SmtpSettings(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
    val startTls: Boolean,
    val source: String
)

object EvaluationMailer {
    private val logger = LoggerFactory.getLogger(EvaluationMailer::class.java)
    private var dataPath: File = File("data")

    fun init(config: Config) {
        dataPath = File(config.dataPath)
        val settings = loadSettings()
        if (settings == null) {
            logger.info("Evaluation SMTP is not configured. Copy data/smtp.properties.example to data/smtp.properties or set SPEAKEASY_SMTP_* env vars.")
        } else {
            logger.info("Evaluation SMTP ready via ${settings.source} (${settings.host}:${settings.port}, from ${settings.from})")
        }
    }

    fun status(): EvaluationMailStatus {
        val settings = loadSettings()
        return if (settings == null) {
            EvaluationMailStatus(configured = false)
        } else {
            EvaluationMailStatus(
                configured = true,
                host = settings.host,
                from = settings.from
            )
        }
    }

    fun sendTranscript(username: String, recipient: String, content: String) {
        val settings = loadSettings()
            ?: throw IllegalStateException("SMTP is not configured. Copy data/smtp.properties.example to data/smtp.properties or set SPEAKEASY_SMTP_* environment variables.")

        val session = Session.getInstance(mailProperties(settings), object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(settings.username, settings.password)
            }
        })

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(settings.from, "Speakeasy Evaluation"))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false))
        message.subject = "Speakeasy evaluation transcript — $username"
        message.setHeader("X-Speakeasy-Evaluation", username)

        val textPart = MimeBodyPart()
        textPart.setText(
            "Your Speakeasy evaluation chat log for $username is attached.\n\n$content\n",
            "UTF-8"
        )

        val filePart = MimeBodyPart()
        filePart.dataHandler = DataHandler(ByteArrayDataSource(content.toByteArray(Charsets.UTF_8), "text/plain; charset=UTF-8"))
        filePart.fileName = "${safeAttachmentName(username)}.txt"
        filePart.disposition = MimeBodyPart.ATTACHMENT

        val multipart = MimeMultipart()
        multipart.addBodyPart(textPart)
        multipart.addBodyPart(filePart)
        message.setContent(multipart)

        try {
            Transport.send(message)
        } catch (error: Exception) {
            throw IllegalStateException(sanitizeError(error, settings), error)
        }
    }

    fun loadSettings(): SmtpSettings? {
        return fromEnv() ?: fromFile()
    }

    private fun fromEnv(): SmtpSettings? {
        val host = env("SPEAKEASY_SMTP_HOST") ?: return null
        val username = env("SPEAKEASY_SMTP_USER") ?: return null
        val password = env("SPEAKEASY_SMTP_PASSWORD") ?: return null
        val port = env("SPEAKEASY_SMTP_PORT")?.toIntOrNull() ?: 587
        val from = env("SPEAKEASY_SMTP_FROM") ?: username
        val startTls = env("SPEAKEASY_SMTP_STARTTLS")?.equals("false", ignoreCase = true) != true
        return SmtpSettings(host, port, username, password, from, startTls, "environment")
    }

    private fun fromFile(): SmtpSettings? {
        val file = File(dataPath, "smtp.properties")
        if (!file.isFile) {
            return null
        }
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val host = props.getProperty("host")?.trim().orEmpty()
        val username = props.getProperty("username")?.trim().orEmpty()
        val password = props.getProperty("password")?.trim().orEmpty()
        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            logger.warn("data/smtp.properties is incomplete (need host, username, password).")
            return null
        }
        val port = props.getProperty("port")?.trim()?.toIntOrNull() ?: 587
        val from = props.getProperty("from")?.trim()?.ifEmpty { null } ?: username
        val startTls = !props.getProperty("starttls", "true").equals("false", ignoreCase = true)
        return SmtpSettings(host, port, username, password, from, startTls, "data/smtp.properties")
    }

    private fun mailProperties(settings: SmtpSettings): Properties {
        val props = Properties()
        props["mail.smtp.host"] = settings.host
        props["mail.smtp.port"] = settings.port.toString()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "20000"
        props["mail.smtp.writetimeout"] = "20000"
        if (settings.port == 465) {
            props["mail.smtp.ssl.enable"] = "true"
        } else if (settings.startTls) {
            props["mail.smtp.starttls.enable"] = "true"
            props["mail.smtp.starttls.required"] = "true"
        }
        return props
    }

    private fun env(name: String): String? {
        return System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun safeAttachmentName(username: String): String {
        val cleaned = username.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
        return cleaned.ifEmpty { "chat" }
    }

    private fun sanitizeError(error: Throwable, settings: SmtpSettings): String {
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            ?: "SMTP send failed"
        return raw
            .replace(settings.password, "***")
            .replace(settings.username, settings.username.take(2) + "***")
    }
}

data class EvaluationMailStatus(
    val configured: Boolean,
    val host: String? = null,
    val from: String? = null
)
