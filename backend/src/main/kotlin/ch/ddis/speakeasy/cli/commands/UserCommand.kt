package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.jakewharton.picnic.table

class UserCommand : NoOpCliktCommand(name = "user") {

    init {
        this.subcommands(
            ListAllUsersCommand(),
            ListCurrentUserSessionsCommand(),
            AddNewUserCommand(),
            RemoveUserCommand()
        )
    }

    inner class ListAllUsersCommand : CliktCommand(name = "all", help = "Lists all known users") {

        override fun run() {

            println(
                table {
                    cellStyle {
                        border = true
                        paddingLeft = 1
                        paddingRight = 1
                    }
                    header {
                        row("user name", "role", "user id")
                    }
                    body {
                        UserManager.list().forEach {
                            row(it.name, it.role.name, it.id.string)
                        }
                    }
                }
            )
        }
    }

    inner class ListCurrentUserSessionsCommand :
        CliktCommand(name = "current", help = "Lists all active user sessions") {

        override fun run() {

            println(
                table {
                    cellStyle {
                        border = true
                        paddingLeft = 1
                        paddingRight = 1
                    }
                    header {
                        row("user name", "role", "user id", "session id")
                    }
                    body {
                        AccessManager.listSessions().forEach {
                            row(it.user.name, it.user.role.name, it.user.id.string, it.sessionId.string)
                        }
                    }
                }
            )
        }
    }

    inner class AddNewUserCommand :
        CliktCommand(name = "add", help = "Add a new user") {

        private val role: String by option(
            "-r",
            "--role",
            help = "Role of the user to add"
        ).required()

        private val username: String by option(
            "-u",
            "--username",
            help = "Name of the user to add"
        ).required()

        private val password: String? by option(
            "-p",
            "--password",
            help = "Password of the user to add"
        )

        override fun run() {

            if (UserManager.list().any { it.name == username }) {
                println("a user with this name already exists")
                return
            }

            if(!arrayOf("HUMAN", "BOT", "ADMIN").contains(role)) {
               println("role is not one of HUMAN, ADMIN or BOT")
               return
            }

            val setPassword: String
            if (password != null) {
                UserManager.addUser(username, UserRole.valueOf(role), PlainPassword(password!!))
                setPassword = password!!
            }
            else {
                setPassword = UserManager.addUser(username, UserRole.valueOf(role))
            }

            println("added %s %s with password %s".format(role, username, setPassword))
        }
    }

    inner class RemoveUserCommand :
        CliktCommand(name = "remove", help = "Remove an existing user") {

        private val username: String by option(
            "-u",
            "--username",
            help = "Name of the user to remove"
        ).required()

        override fun run() {

            if (UserManager.list().none { it.name == username }) {
                println("a user with this name does not exist")
                return
            }

            if (UserManager.removeUser(username)) {
                println("user %s removed".format(username))
            }
            else {
                println("could not remove user %s: active sessions found".format(username))
            }
            return
        }
    }
}