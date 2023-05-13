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
import java.sql.ResultSet
import java.util.concurrent.locks.StampedLock
object UserManager {

    private val users = mutableListOf<User>()


    private lateinit var userSQLitePath: String

    private lateinit var userConnection: Connection

    private lateinit var statement: Statement

    private val lock: StampedLock = StampedLock()



    fun init(config: Config) {
        this.lock.write {
            this.userSQLitePath = "jdbc:sqlite:${config.dataPath}/users.db"
            this.userConnection = DriverManager.getConnection(this.userSQLitePath) // if file not exists, it will create this db file
            this.statement = this.userConnection.createStatement()

            val sqlCreate = "create table if not exists users(" +
                    "username varchar(255), " +
                    "password varchar(255), " +
                    "role varchar(255), " +
                    "id varchar(255)," +
                    "UNIQUE(username))"
            this.statement.executeUpdate(sqlCreate)

            // initialize users from users.db
            val sqlQuery = "select * from users"
            val results: ResultSet = statement.executeQuery(sqlQuery)
            while (results.next()){
                val username = results.getString("username")
                if ( users.find { it.name == username } != null ){ // Theoretically it will not happen here, just in case
                    System.err.println("Username conflict! Ignore the second '$username'")
                    continue
                }
                val role = try {
                    UserRole.valueOf(results.getString("role"))
                } catch (e: IllegalArgumentException) {
                    System.err.println("Cannot parse user role ${results.getString("role")}, defaulting to HUMAN")
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
            val sqlInsert =
                "insert into users values ('${user.name}', '${user.password.hash}', '${user.role.name}', '${user.id.string}')"
            statement.executeUpdate(sqlInsert)
        } catch (e: SQLiteException){
            System.err.println("Username conflict in database! Ignore the second '${user.name}.'")
        }
    }

    private fun flushRemovedUser(user: User){
        try {
            val sqlInsert = "delete from users where username = '${user.name}'"
            statement.executeUpdate(sqlInsert)
        } catch (e: SQLiteException){
            System.err.println("Failed to remove '${user.name}' from database.")
        }
    }

    private fun flushUpdatedUser(user: User){
        try {
            val sqlInsert = "update users set password = '${user.password.hash}' where username = '${user.name}'"
            statement.executeUpdate(sqlInsert)
        } catch (e: SQLiteException){
            System.err.println("Failed to update the password of '${user.name}' in database.")
        }
    }

}

class UsernameConflictException(message: String = "Username already exists!") : Exception(message)