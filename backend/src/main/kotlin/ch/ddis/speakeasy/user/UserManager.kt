package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import org.sqlite.SQLiteException
import java.sql.*
import java.util.concurrent.locks.StampedLock
object UserManager {

    private val users = mutableListOf<User>()
    private val groups = mutableListOf<Group>() // id, name, users

    private lateinit var userSQLitePath: String

    private lateinit var userConnection: Connection

    private lateinit var tableNameForUser: String
    private lateinit var tableNameForGroup: String

    private lateinit var initStatement: Statement

    private lateinit var preparedInsertStatementForUser: PreparedStatement
    private lateinit var preparedDeleteStatementForUser: PreparedStatement
    private lateinit var preparedUpdateStatementForUser: PreparedStatement

    private lateinit var preparedInsertStatementForGroup: PreparedStatement
    private lateinit var preparedDeleteGroupStatementForGroup: PreparedStatement
    private lateinit var preparedDeleteUserStatementForGroup: PreparedStatement
    private lateinit var preparedQueryStatementForGroup: PreparedStatement
    private lateinit var preparedClearAllStatementForGroup: PreparedStatement

    private val lock: StampedLock = StampedLock()


    fun init(config: Config) {
        this.lock.write {
            this.userSQLitePath = "jdbc:sqlite:${config.dataPath}/users.db"
            this.userConnection = DriverManager.getConnection(this.userSQLitePath) // if file not exists, it will create this db file
            this.tableNameForUser = "users"
            this.tableNameForGroup = "groups"
            this.initStatement = this.userConnection.createStatement()

            this.createUserTable()
            this.prepareStatementsForUserTable()
            this.initUsersFromDB()

            this.createGroupTable()
            this.prepareStatementsForGroupTable()
            this.initGroupsFromDB()
        }
    }

    private fun createUserTable() {
        val sqlCreate = "CREATE TABLE IF NOT EXISTS $tableNameForUser (" +
                "username VARCHAR(60) NOT NULL UNIQUE, " +
                "password CHAR(60) NOT NULL, " + // password hashes have the same length 60
                "role TINYINT NOT NULL, " +
                "id CHAR(36) NOT NULL)" // ids have the same length 36
        this.initStatement.executeUpdate(sqlCreate)
    }

    private fun createGroupTable() {
        val sqlCreate = "CREATE TABLE IF NOT EXISTS $tableNameForGroup (" +
                "groupName VARCHAR(60) NOT NULL, " +
                "groupId CHAR(36) NOT NULL, " +
                "username VARCHAR(60) NOT NULL, " +
                "userId CHAR(36) NOT NULL, " +
                "UNIQUE(groupName, username)" +
                ")"
        this.initStatement.executeUpdate(sqlCreate)
    }

    private fun prepareStatementsForUserTable() {
        // prepare some statements for later use
        val sqlInsert = "INSERT INTO $tableNameForUser VALUES (?, ?, ?, ?)"
        this.preparedInsertStatementForUser = userConnection.prepareStatement(sqlInsert)
        val sqlDelete = "DELETE FROM $tableNameForUser WHERE username = ?"
        this.preparedDeleteStatementForUser = userConnection.prepareStatement(sqlDelete)
        val sqlUpdate = "UPDATE $tableNameForUser SET password = ? WHERE username = ?"
        this.preparedUpdateStatementForUser = userConnection.prepareStatement(sqlUpdate)
    }

    private fun prepareStatementsForGroupTable() {
        val sqlInsert = "INSERT INTO $tableNameForGroup VALUES (?, ?, ?, ?)"
        this.preparedInsertStatementForGroup = userConnection.prepareStatement(sqlInsert)
        val sqlDeleteGroup = "DELETE FROM $tableNameForGroup WHERE groupName = ?"
        this.preparedDeleteGroupStatementForGroup = userConnection.prepareStatement(sqlDeleteGroup)
        val sqlDeleteUser = "DELETE FROM $tableNameForGroup WHERE username = ?"
        this.preparedDeleteUserStatementForGroup = userConnection.prepareStatement(sqlDeleteUser)
        val sqlQuery = "SELECT * FROM $tableNameForGroup WHERE groupName = ?"
        this.preparedQueryStatementForGroup = userConnection.prepareStatement(sqlQuery)
        val sqlClear = "DELETE FROM $tableNameForGroup"
        this.preparedClearAllStatementForGroup = userConnection.prepareStatement(sqlClear)
    }

    private fun initUsersFromDB() {
        // initialize users from users.db
        val sqlQuery = "SELECT * FROM $tableNameForUser"
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

    private fun initGroupsFromDB() {
        // initialize groups from users.db
        val sqlQuery = "SELECT * FROM $tableNameForGroup"
        val results: ResultSet = this.initStatement.executeQuery(sqlQuery)
        while (results.next()){
            val groupName = try {
                results.getString("groupName")
            } catch (e: SQLException){
                System.err.println("Something wrong when getting the groupName from group table. Skipped this line.")
                continue
            }

            val groupId = try {
                results.getString("groupId").UID()
            } catch (e: SQLException){
                System.err.println("Something wrong when getting the groupId from group table. Skipped this line.")
                continue
            } catch (e: IllegalArgumentException){
                System.err.println("Something wrong when convert groupId to UID. Skipped this line.")
                continue
            }

            val username = try {
                results.getString("username")
            } catch (e: SQLException){
                System.err.println("Something wrong when getting the username from group table. Skipped this line.")
                continue
            }

            val user = users.find{ it.name == username}
            if (user == null) {
                System.err.println("Cannot find user $username in user table, something wrong with group table. " +
                        "Skipped this user when adding it to some group.")
                continue
            }

            val group = groups.find { it.name ==  groupName } ?: Group(
                groupId,
                groupName
            )

            if (group.isEmpty()) { // only a new group to add is empty here
                groups.add(group)
            }
            group.addUser(user)

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
                    removeUserInGroups(user) // Consistent with group table
                    users.remove(user)
                    flushRemovedUser(user)
                    return true
                }
            }
        }
        return true
    }

    private fun removeUserInGroups(user: User){
        groups.forEach { it.removeUser(user) }
        groups.removeAll { it.isEmpty() }
        flushRemovedUserFromAllGroups(user)
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

    fun listGroups(): List<Group> = this.lock.read {
        this.groups.toList()
    }

    fun createGroup(groupName: String, usernames: List<String>) {
        this.lock.write {
            if (groups.find { it.name == groupName } != null) {
                throw GroupNameConflictException()
            }
            val groupId = UID()
            val groupToAdd = Group(groupId, groupName)

            for ( username in usernames.distinct() ) { // remove duplicates in usernames
                val userToAdd = users.find { it.name ==  username}
                if (userToAdd != null) {
                    groupToAdd.addUser(userToAdd)
                }else {
                    // once a user is not found by username, abort the whole process
                    throw UsernameNotFoundException("$username is not found, abort this group creation")
                }
            }
            if (!groupToAdd.isEmpty()) { // group with empty users will not be added
                groups.add(groupToAdd)
                flushCreatedGroup(groupToAdd)
            }
        }
    }

    fun removeGroup(groupName: String) {
        this.lock.write {
            val groupToRemove = groups.find { it.name == groupName } ?: throw GroupNameNotFoundException()
            groups.remove(groupToRemove)
            flushRemovedGroup(groupToRemove)
        }
    }

    fun checkGroups() { // TODO: Just for development checking, will delete it later
        this.lock.read {
            println("---> checkGroups:")
            println("groups.size: ${groups.size}")
            groups.forEach { group ->
                group.users.forEach { user ->
                    println("groupName: ${group.name} | groupId: ${group.id.string} | username: ${user.name} | userId: ${user.id.string}")
                }
            }
        }
    }

    fun checkGroupsInDB() { // TODO: Just for development checking, will delete it later
        this.lock.read {
            println("---> checkGroupsInDB:")
            val sqlQuery = "SELECT * FROM $tableNameForGroup"
            val results: ResultSet = this.initStatement.executeQuery(sqlQuery)
            while (results.next()){
                val groupName: String = results.getString("groupName")
                val groupId: String = results.getString("groupId")
                val username: String = results.getString("username")
                val userId: String = results.getString("userId")
                println("groupName: $groupName | groupId: $groupId | username: $username | userId: $userId")
            }
        }
    }

    fun removeAllGroups() {
        this.lock.write {
            groups.clear()
            preparedClearAllStatementForGroup.executeUpdate()
        }
    }

    private fun flushAddedUser(user: User){
        try {
            preparedInsertStatementForUser.setString(1, user.name)
            preparedInsertStatementForUser.setString(2, user.password.hash)
            preparedInsertStatementForUser.setInt(3, user.role.ordinal)
            preparedInsertStatementForUser.setString(4, user.id.string)
            preparedInsertStatementForUser.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Username conflict in database! Ignore the second '${user.name}.'")
        }
    }

    private fun flushRemovedUser(user: User){
        try {
            preparedDeleteStatementForUser.setString(1, user.name)
            preparedDeleteStatementForUser.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Failed to remove '${user.name}' from database.")
        }
    }

    private fun flushUpdatedUser(user: User){
        try {
            preparedUpdateStatementForUser.setString(1, user.password.hash)
            preparedUpdateStatementForUser.setString(2, user.name)
            preparedUpdateStatementForUser.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Failed to update the password of '${user.name}' in database.")
        }
    }

    private fun flushCreatedGroup(group: Group){
        try {
            preparedInsertStatementForGroup.setString(1, group.name)
            preparedInsertStatementForGroup.setString(2, group.id.string)
            group.users.forEach{ user ->
                preparedInsertStatementForGroup.setString(3, user.name)
                preparedInsertStatementForGroup.setString(4, user.id.string)
                try {
                    preparedInsertStatementForGroup.executeUpdate()
                } catch (e: SQLiteException) {
                    System.err.println("Failed to add ${user.name} to ${group.name} in database, Skipped this line. " +
                            "Error:${e.message}")
                }
            }
        } catch (e: SQLiteException){
            System.err.println("Failed to flush the database when adding group ${group.name}")
        }
    }

    private fun flushRemovedGroup(group: Group){
        try {
            preparedDeleteGroupStatementForGroup.setString(1, group.name)
            preparedDeleteGroupStatementForGroup.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Failed to flush the database when removing group ${group.name}.")
        }
    }

    private fun flushRemovedUserFromAllGroups(user: User){
        try {
            preparedDeleteUserStatementForGroup.setString(1, user.name)
            preparedDeleteUserStatementForGroup.executeUpdate()
        } catch (e: SQLiteException){
            System.err.println("Failed to flush the database when removing user ${user.name} from all groups.")
        }
    }
}

class UsernameConflictException(message: String = "Username already exists!") : Exception(message)
class GroupNameConflictException(message: String = "Group name already exists!") : Exception(message)
class UsernameNotFoundException(message: String = "Username not found.") : Exception(message)
class GroupNameNotFoundException(message: String = "Group name not found.") : Exception(message)