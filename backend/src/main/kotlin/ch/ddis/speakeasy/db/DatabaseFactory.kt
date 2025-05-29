package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.util.Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseHandler {
    private var mainDB: Database? = null

    fun init(config: Config) {
        mainDB = Database.connect("jdbc:sqlite:${config.dataPath}/database.db", driver = "org.sqlite.JDBC")
        // Print the database connection URL for debugging

        transaction {
            // Create all tables in the correct order (dependencies first)
            SchemaUtils.create(
                Users,        // Base table for users
                Groups,      // Base table for groups
                GroupUsers,  // Junction table for users and groups
                FeedbackForms,      // Base table for feedback forms
                FeedbackResponses,
                ChatRooms,
                ChatroomParticipants,
                ChatMessages,

            )

        }
    }

    /**
     * Initialize with a custom database (useful for testing)
     */
    fun initWithDatabase(database: Database) {
        mainDB = database
        transaction(database) {
            SchemaUtils.create(
                Users,
                Groups,
                GroupUsers,
                FeedbackForms,
                FeedbackResponses,
                ChatRooms,
                ChatroomParticipants,
                ChatMessages,
            )
        }
    }

    /**
     * Execute a database transaction.
     *
     * @param statement The code to execute within the transaction
     * @return The result of the transaction
     */
    fun <T> dbQuery(statement: () -> T): T {
        return transaction(mainDB) {
            statement()
        }
    }

    /**
     * Get the current database (useful for testing)
     */
    fun getCurrentDatabase(): Database? = mainDB

    /**
     * Reset the database handler (useful for testing cleanup)
     */
    fun reset() {
        mainDB = null
    }
}