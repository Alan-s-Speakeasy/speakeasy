package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.UID
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.jakewharton.picnic.table
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class ChatCommand : NoOpCliktCommand(name = "chat") {

    init {
        this.subcommands(
            ListAllChatRoomsCommand(),
            ListActiveChatRoomsCommand(),
            ShowChatRoomCommand(),
            ShowUsersInChatCommand()
        )
    }

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("M/d/y H:m:ss")

        private fun formatTimeStamp(timeStamp: Long): String =
            Date(timeStamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter)
    }

    inner class ListAllChatRoomsCommand : CliktCommand(name = "all", help = "Lists all known chat rooms") {

        override fun run() {
            println(
                table {
                    cellStyle {
                        border = true
                        paddingLeft = 1
                        paddingRight = 1
                    }
                    header {
                        row("id", "user name", "user name", "active", "start time", "end time")
                    }
                    body {

                        ChatRoomManager.listAll().forEach {
                            val usersIds = it.users.keys
                            val usernames = usersIds.map { id -> UserManager.getUsernameFromId(id) }
                            row(
                                it.uid.string,
                                usernames.getOrNull(0) ?: " n/a ",
                                usernames.getOrNull(0) ?: " n/a ",
                                it.active,
                                formatTimeStamp(it.startTime),
                                if (it.endTime != null) formatTimeStamp(it.endTime!!) else " n/a "
                            )
                        }
                    }
                }
            )
        }
    }

    inner class ListActiveChatRoomsCommand : CliktCommand(name = "current", help = "Lists active known chat rooms") {

        override fun run() {
            println(
                table {
                    cellStyle {
                        border = true
                        paddingLeft = 1
                        paddingRight = 1
                    }
                    header {
                        row("id", "user name", "user name", "start time")
                    }
                    body {

                        ChatRoomManager.listActive().forEach {
                            val usersIds = it.users.keys
                            val usernames = usersIds.map { id -> UserManager.getUsernameFromId(id) }
                            row(
                                it.uid.string,
                                usernames.getOrNull(0) ?: " n/a ",
                                usernames.getOrNull(0) ?: " n/a ",
                                formatTimeStamp(it.startTime)
                            )
                        }
                    }
                }
            )
        }
    }

    inner class ShowChatRoomCommand : CliktCommand(name = "show", help = "Shows the current content of a chat room") {

        val id: String by option("-i", "--id", help = "The id of the chat room").required()

        override fun run() {

            val uid = try {
                id.UID()
            } catch (e: IllegalArgumentException) {
                println("'$id' is not a valid id")
                return
            }

            val room = ChatRoomManager[uid]

            if (room == null) {
                println("Chatroom with id '$id' not found")
                return
            }

            room.getAllMessages().forEach { message ->
                val userId = room.aliasToUserId[message.authorAlias]
                val username = UserManager.getUsernameFromId(userId!!) ?: "unknown"
                val reaction = room.getAllReactions().find { it.messageOrdinal == message.ordinal }
                println("${username}@${formatTimeStamp(message.time)}: ${message.message}")
                if (reaction != null) {
                    println("*Reaction: ${reaction.type}*")
                }
                println()
            }
        }
    }

    inner class ShowUsersInChatCommand : CliktCommand(name = "users", help = "Shows the current users of a chat room") {

        val id: String by option("-i", "--id", help = "The id of the chat room").required()

        override fun run() {

            val uid = try {
                id.UID()
            } catch (e: IllegalArgumentException) {
                println("'$id' is not a valid id")
                return
            }

            val room = ChatRoomManager[uid]

            if (room == null) {
                println("Chatroom with id '$id' not found")
                return
            }

            var listUsers = ChatRoomManager.getUsers(uid)
            println("Users in chatroom $id:")
            listUsers.forEach { user ->
                println(user)
            }

        }
    }

}