package ch.ddis.speakeasy

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.RestApi
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.cli.Cli
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.Config
import java.io.File

object Main {

    @JvmStatic
    fun main(args: Array<String>) {

        val config = if (args.isNotEmpty()) {
            Config.read(File(args[0]))
        } else {
            null
        } ?: Config()

        UserManager.init(config)
        AccessManager.init()
        FeedbackManager.init(config)
        ChatRoomManager.init()

        println("starting api")
        RestApi.init(config)

        Cli.loop()

        AccessManager.stop()
        RestApi.stop()
    }

}