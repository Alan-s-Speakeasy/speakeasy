package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import org.sqlite.SQLiteException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.locks.StampedLock
object UserManager {

    private val users = mutableListOf<User>()

    private lateinit var userSQLitePath: String

    private lateinit var userConnection: Connection

    private lateinit var tableName: String

    private lateinit var initStatement: Statement
    private lateinit var preparedInsertStatement: PreparedStatement
    private lateinit var preparedDeleteStatement: PreparedStatement
    private lateinit var preparedUpdateStatement: PreparedStatement

    private val lock: StampedLock = StampedLock()


    fun init(config: Config) {
        this.lock.write {
            this.userSQLitePath = "jdbc:sqlite:${config.dataPath}/users.db"
            this.userConnection = DriverManager.getConnection(this.userSQLitePath) // if file not exists, it will create this db file
            this.tableName = "users"
            this.initStatement = this.userConnection.createStatement()
            // create table if not exists
            val sqlCreate = "CREATE TABLE IF NOT EXISTS $tableName (" +
                    "username VARCHAR(60) NOT NULL UNIQUE, " +
                    "password CHAR(60) NOT NULL, " + // password hashes have the same length 60
                    "role TINYINT NOT NULL, " +
                    "id CHAR(36) NOT NULL)" // ids have the same length 36
            this.initStatement.executeUpdate(sqlCreate)

            // prepare some statements for later use
            val sqlInsert = "INSERT INTO $tableName VALUES (?, ?, ?, ?)"
            this.preparedInsertStatement = userConnection.prepareStatement(sqlInsert)
            val sqlDelete = "DELETE FROM $tableName WHERE username = ?"
            this.preparedDeleteStatement = userConnection.prepareStatement(sqlDelete)
            val sqlUpdate = "UPDATE $tableName SET password = ? WHERE username = ?"
            this.preparedUpdateStatement = userConnection.prepareStatement(sqlUpdate)

            // initialize users from users.db
            val sqlQuery = "select * from $tableName"
            val results: ResultSet = this.initStatement.executeQuery(sqlQuery)
            while (results.next()){
                val username = results.getString("username")
                if ( users.find { it.name == username } != null ){ // Theoretically it will not happen here, just in case
                    System.err.println("Username conflict! Ignore the second '$username'")
                    continue
                }
                val role = try {
                    UserRole.values()[results.getInt("role")]
                } catch (e: IndexOutOfBoundsException) {
                    System.err.println("Role in database should be integer 0, 1 or 2, " +
                            "not ${results.getInt("role")}, defaulting to 0(HUMAN)")
                    UserRole.HUMAN
                }
                val password = if (results.getString("password").startsWith("$")) {
                    HashedPassword(results.getString("password"))
                } else {
                    System.err.println("The password of $username is not hashed in database!")
                    PlainPassword(results.getString("password"))
                }
                val uid = try {
                    results.getString("id").UID()
                } catch (e: IllegalArgumentException) {
                    UID()
                }
                users.add(User(uid, username, role, password)) // will convert password to hashed password

            }
        }
    }


    fun addUser(username: String, role: UserRole): String {
        val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val password = List(15) { alphabet.random() }.joinToString("")

        addUser(username, role, PlainPassword(password))
        return password
    }

    fun addUser(username: String, role: UserRole, password: Password) {
        this.lock.write {
            if (users.find { it.name == username } != null) {
                throw UsernameConflictException()
            }
            val uid = UID()
            val newUser = User(uid, username, role, password)
            users.add(newUser)
            flushAddedUser(newUser)
        }
    }

    fun removeUser(username: String, force: Boolean): Boolean {
        this.lock.write {
            for (user in users) {
                if (username == user.name) {
                    if (!force && AccessManager.hasUserIdActiveSessions(user.id)) {
                        return false
                    }
                    if (force) {
                        AccessManager.forceClearUserId(user.id)
                    }
                    users.remove(user)
                    flushRemovedUser(user)
                    return true
                }
            }
        }
        return true
    }

    fun getMatchingUser(username: String, password: PlainPassword): User? = this.lock.read {
        val user = users.find { it.name == username } ?: return null
        return if (user.password.check(password)) user else null
    }

    fun getUsernameFromId(userId: UserId): String? = this.lock.read {
        return users.find { it.id == userId }?.name
    }

    fun getUserIdFromUsername(username: String): UserId? = this.lock.read {
        return users.find { it.name == username }?.id
    }

    fun getPasswordFromId(userId: UserId): Password? = this.lock.read {
        return users.find { it.id == userId }?.password
    }

    fun updatePassword(userId: UserId, newPassword: Password) {
        this.lock.write {
            val oldUser =
                users.find { it.id == userId } ?: throw IllegalArgumentException("User with id $userId not found")
            users.remove(oldUser)
            val newUser = User(oldUser.id, oldUser.name, oldUser.role, newPassword)
            users.add(newUser)
            flushUpdatedUser(newUser)
        }
    }

    fun list(): List<User> = this.lock.read {
        this.users.toList()
    }

    private fun flushAddedUser(user: User){
        try {
            preparedInsertStatement.setString(1, user.name)
            preparedInsertStatement.setString(2, user.password.hash)
            preparedInsertStatement.setInt(3, user.role.ordinal)
            preparedInsertStatement.setString(4, user.id.string)
            preparedInsertStatement.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Username conflict in database! Ignore the second '${user.name}.'")
        }
    }

    private fun flushRemovedUser(user: User){
        try {
            preparedDeleteStatement.setString(1, user.name)
            preparedDeleteStatement.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Failed to remove '${user.name}' from database.")
        }
    }

    private fun flushUpdatedUser(user: User){
        try {
            preparedUpdateStatement.setString(1, user.password.hash)
            preparedUpdateStatement.setString(2, user.name)
            preparedUpdateStatement.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Failed to update the password of '${user.name}' in database.")
        }
    }
}

class UsernameConflictException(message: String = "Username already exists!") : Exception(message)