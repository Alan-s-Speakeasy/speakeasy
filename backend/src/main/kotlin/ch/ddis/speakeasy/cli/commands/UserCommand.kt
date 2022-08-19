package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
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

        private val role: UserRole by option(
            "-r",
            "--role",
            help = "Role of the user to add"
        ).enum<UserRole>().required()

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

            val setPassword: String = if (password != null) {
                UserManager.addUser(username, role, PlainPassword(password!!))
                password!!
            } else {
                UserManager.addUser(username, role)
            }

            println("added $role $username with password $setPassword")
        }
    }

    inner class RemoveUserCommand :
        CliktCommand(name = "remove", help = "Remove an existing user") {

        private val username: String by option(
            "-u",
            "--username",
            help = "Name of the user to remove"
        ).required()

        private val force: Boolean by option(
            "-f",
            "--force",
            help = "Remove user even if sessions are active"
        ).flag(default = false)

        override fun run() {

            if (UserManager.list().none { it.name == username }) {
                println("a user with this name does not exist")
                return
            }

            if (UserManager.removeUser(username, force)) {
                println("user $username removed")
            }
            else {
                println("could not remove user $username: active sessions found")
            }
            return
        }
    }
}