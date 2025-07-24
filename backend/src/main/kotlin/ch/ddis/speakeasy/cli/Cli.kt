package ch.ddis.speakeasy.cli

import ch.ddis.speakeasy.assignment.ChatAssignmentGenerator
import ch.ddis.speakeasy.cli.commands.AssignmentCommand
import ch.ddis.speakeasy.cli.commands.ChatCommand
import ch.ddis.speakeasy.cli.commands.EvaluationCommand
import ch.ddis.speakeasy.cli.commands.UserCommand
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import org.jline.builtins.Completers
import org.jline.reader.*
import org.jline.reader.impl.completer.AggregateCompleter
import org.jline.reader.impl.completer.StringsCompleter
import org.jline.terminal.TerminalBuilder
import java.io.IOException
import java.util.regex.Pattern
import kotlin.system.exitProcess

/**
 * Command Line Interface endpoint
 */
object Cli {

    private const val PROMPT = "speakeasy> "

    private lateinit var clikt: CliktCommand

    var assignmentGenerator: ChatAssignmentGenerator? = null

    fun loop() {

        clikt = BaseCommand().subcommands(
            AssignmentCommand(),
            UserCommand(),
            ChatCommand(),
            // EvaluationCommand()
        )

        val terminal = try {
            TerminalBuilder.builder()
                .build()
        } catch (e: IOException) {
            System.err.println("Could not initialize terminal: ${e.message}")
            exitProcess(-1)
        }

        val completer = DelegateCompleter(
            AggregateCompleter(
                StringsCompleter("quit", "exit", "help"),
                Completers.TreeCompleter(
                    clikt.registeredSubcommands().map {
                        if (it.registeredSubcommands().isNotEmpty()) {
                            Completers.TreeCompleter.node(
                                it.commandName,
                                Completers.TreeCompleter.node(*it.registeredSubcommandNames().toTypedArray())
                            )
                        } else {
                            Completers.TreeCompleter.node(it.commandName)
                        }
                    }
                ),
                Completers.FileNameCompleter()
            )
        )

        val lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(completer)
            .build()

        var eofFallback = false

        while (true) {
            try {
                val line = lineReader.readLine(PROMPT).trim()
                val lower = line.lowercase()
                if (lower == "exit" || lower == "quit") {
                    break
                }
                if (lower == "help") {
                    println(clikt.getFormattedHelp())
                    continue
                }
                if (line.isBlank()) {
                    continue
                }

                try {
                    execute(line)
                } catch (e: Exception) {
                    when (e) {
                        is com.github.ajalt.clikt.core.NoSuchSubcommand -> println("command not found")
                        is com.github.ajalt.clikt.core.PrintHelpMessage -> println(e.command.getFormattedHelp())
                        is com.github.ajalt.clikt.core.UsageError -> println("invalid command")
                        is com.github.ajalt.clikt.core.NoSuchOption -> println(e.localizedMessage)
                        else -> e.printStackTrace()
                    }
                }
            } catch (e: EndOfFileException) {
                System.err.println("Could not read from terminal due to EOF.")
                eofFallback = true
                break
            } catch (e: UserInterruptException) {
                break
            }
        }

        if (eofFallback) {

            println("Interactive terminal disabled, close application with Ctrl + C")

            while (true) {
                Thread.sleep(10_000)
            }

        }

    }


    private fun execute(line: String) {
        if (!::clikt.isInitialized) {
            error("CLI not initialised. Aborting...") // Technically, this should never ever happen
        }
        clikt.parse(splitLine(line))
    }

    private val lineSplitRegex: Pattern = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'")

    //based on https://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double/366532
    @JvmStatic
    fun splitLine(line: String?): List<String> {
        if (line == null || line.isEmpty()) {
            return emptyList()
        }
        val matchList: MutableList<String> = ArrayList()
        val regexMatcher = lineSplitRegex.matcher(line)
        while (regexMatcher.find()) {
            if (regexMatcher.group(1) != null) {
                // Add double-quoted string without the quotes
                matchList.add(regexMatcher.group(1))
            } else if (regexMatcher.group(2) != null) {
                // Add single-quoted string without the quotes
                matchList.add(regexMatcher.group(2))
            } else {
                // Add unquoted word
                matchList.add(regexMatcher.group())
            }
        }
        return matchList
    }

    class BaseCommand : NoOpCliktCommand(name = "speakeasy") {

        init {
            context { helpFormatter = CliHelpFormatter() }
        }

    }

    /**
     * Delegate for [Completer] to dynamically exchange and / or adapt a completer.
     * Delegates incoming completion requests to the delegate
     */
    class DelegateCompleter(var delegate: Completer) : Completer {
        override fun complete(
            reader: LineReader?,
            line: ParsedLine?,
            candidates: MutableList<Candidate>?
        ) {
            delegate.complete(reader, line, candidates)
        }
    }

    class CliHelpFormatter : CliktHelpFormatter() {
        override fun formatHelp(
            prolog: String,
            epilog: String,
            parameters: List<HelpFormatter.ParameterHelp>,
            programName: String
        ) = buildString {
            addOptions(parameters)
            addArguments(parameters)
            addCommands(parameters)
        }
    }

}