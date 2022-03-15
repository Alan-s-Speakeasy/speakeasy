package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.UserManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.jakewharton.picnic.table

class UserCommand : NoOpCliktCommand(name = "user") {

    init {
        this.subcommands(
            ListAllUsersCommand(),
            ListCurrentUserSessionsCommand()
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

}