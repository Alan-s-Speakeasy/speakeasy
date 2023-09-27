package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.user.UsernameConflictException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.jakewharton.picnic.table
import java.io.File
import java.lang.IllegalArgumentException

class UserCommand : NoOpCliktCommand(name = "user") {

    init {
        this.subcommands(
            ListAllUsersCommand(),
            ListCurrentUserSessionsCommand(),
            AddNewUserCommand(),
            RemoveUserCommand(),
            ImportUsersCommand()
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
                            row(it.name, it.role.name, it.id.toString())
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
                            row(it.user.name, it.user.role.name, it.user.id.toString(), it.sessionId.string)
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

    inner class ImportUsersCommand : CliktCommand(name = "import", help = "Imports users from a CSV file. Expected headers: username, password, role") {

        private val inputFileName: String by option("-f", "--file", help = "the path to the input file").required()

        override fun run() {

            val file = File(inputFileName)

            if (!file.exists() || !file.canRead()) {
                println("Cannot read '${file.absolutePath}'")
                return
            }

            csvReader().open(file) {
                readAllWithHeaderAsSequence().forEach { row ->

                    val user = row["username"] ?: return@forEach
                    val password = PlainPassword(row["password"] ?: return@forEach)
                    val role = try{
                        UserRole.valueOf(row["role"]?.uppercase() ?: return@forEach)
                    } catch (e : IllegalArgumentException) {
                        println("no valid role for user '$user'")
                        return@forEach
                    }

                    try{
                        UserManager.addUser(user, role, password)
                        println("added user '$user' with role '$role'")
                    } catch (e: UsernameConflictException) {
                        println("could not add user '$user', user with this name already exists")
                    }

                }
            }

        }
    }

}