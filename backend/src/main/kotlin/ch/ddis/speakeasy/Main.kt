package ch.ddis.speakeasy

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.RestApi
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.cli.Cli
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.Config
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists


object Speakeasy : CliktCommand(help = "Runs the Speakeasy application") {

    private val dataPath: File? by option(
        "--datapath",
        help = "Path to the data directory"
    ).file(canBeFile = false, canBeDir = true, mustExist = true)

    private val configPath: File? by option(
        "--config",
        help = "Path to the configuration file"
    ).file(canBeFile = true, canBeDir = false, mustExist = true)


    override fun run() {
        println("===== Speakeasy =====")

        var config = configPath?.let {
            println("Running with config file: $it")
            Config.read(it)
        } ?: run {
            println("No config file provided, using default configuration.")
            Config()
        }
        println("config: $config")
        if (dataPath != null) {
            println("Using data path: ${Path(dataPath!!.absolutePath).normalize()}")
            config = config.withDataPath(dataPath!!.absolutePath)
        }

        // Make sure data directory exists
        val dataDir = Path(config.dataPath)
        if (!dataDir.exists()) {
            println("WARNING : Creating data directory: $dataDir")
            dataDir.createDirectory()
        }

        UserManager.init(config)
        AccessManager.init(config)
        FeedbackManager.init(config)
        ChatRoomManager.init(config)

        println("Starting api")
        RestApi.init(config)

        Cli.loop()

        AccessManager.stop()
        RestApi.stop()
    }
}

fun main(args: Array<String>) {
    Speakeasy.main(args)
}