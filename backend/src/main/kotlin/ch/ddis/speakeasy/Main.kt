package ch.ddis.speakeasy

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.RestApi
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.cli.Cli
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.user.GroupNameConflictException
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UsernameNotFoundException
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

        // ------- TODO delete it later -------

//        val groupName = "group1"
//        try {
//            UserManager.createGroup(groupName, listOf("human1", "human2", "bot1"))
//        } catch (e: GroupNameConflictException){
//            println("$groupName exists!")
//        }
//
//        val groupName2 = "group2"
//        try {
//            UserManager.createGroup(groupName2, listOf("human1", "human2", "no_user"))
//        } catch (e: GroupNameConflictException){
//            println("$groupName exists!")
//        } catch (e: UsernameNotFoundException){
//            println(e.message)
//        }

        UserManager.checkGroups()
        UserManager.checkGroupsInDB()
        UserManager.clearAllGroups()

        // ------- TODO delete it later -------

        Cli.loop()

        AccessManager.stop()
        RestApi.stop()
    }

}