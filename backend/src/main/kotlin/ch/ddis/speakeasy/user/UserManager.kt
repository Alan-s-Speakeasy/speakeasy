package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*
import java.util.concurrent.locks.StampedLock


object UserManager {

    private val lock: StampedLock = StampedLock()

    fun init(config: Config) {
        // Check if the sql exists
        if (File("${config.dataPath}/users.db").exists().not()) {
            System.err.println("WARNING : No user database found, a new one will be created.")
        }

        this.lock.write {
            Database.connect("jdbc:sqlite:${config.dataPath}/users.db", driver = "org.sqlite.JDBC")
            transaction {
                SchemaUtils.createMissingTablesAndColumns(Users, Groups, GroupUsers)
            }
            transaction {
                // Check if DB is empty
                if (Users.selectAll().empty()) {
                    System.err.println("WARNING : The user database is empty !")
                }
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
            transaction {
                if (!User.find { Users.username eq username }.empty()) {
                    throw UsernameConflictException()
                }
                User.new {
                    this.name = username
                    this.role = role
                    this.password = password
                }
            }
        }
    }

    fun removeUser(username: String, force: Boolean): Boolean = this.lock.write {
        transaction {
            val queryRes = User.find { Users.username eq username }
            val userToRemove = queryRes.firstOrNull() ?: return@transaction true

            if (!force && AccessManager.hasUserIdActiveSessions(userToRemove.id.UID())) {
                return@transaction false
            }
            if (force) {
                AccessManager.forceClearUserId(userToRemove.id.UID())
            }

            removeUserInGroups(userToRemove)
            userToRemove.delete()
            return@transaction true
        }
    }

    private fun removeUserInGroups(user: User) {
        transaction {
            GroupUsers.deleteWhere { user_id eq user.id }
            Group.all().forEach {
                if (it.users.empty()) {
                    it.delete()
                }
            }
        }
    }

    /**
     * Checks if a user with the given id already exists.
     *
     * @param userId The id of the user to check.
     *
     * @return True if a user with the given id exists, false otherwise.
     */
    fun checkUserIdExists(userId : UserId): Boolean =
        transaction {
            return@transaction User.findById(userId.toUUID()) != null
    }

    fun getMatchingUser(username: String, password: PlainPassword): User? = this.lock.read {
        transaction {
            val queryRes =  User.find { Users.username eq username }
            val userToCheck = queryRes.firstOrNull() ?: return@transaction null
            return@transaction if ((userToCheck.password as HashedPassword).check(password)) userToCheck else null
        }
    }

    fun getUsernameFromId(userId: UserId): String? = this.lock.read {
        transaction {
            val userToGet =  User.findById(userId.toUUID())  // TODO: can it be simplified?
            return@transaction userToGet?.name
        }
    }

    fun getUserIdFromUsername(username: String): UserId? = this.lock.read {
        transaction {
            val queryRes =  User.find { Users.username eq username }
            return@transaction queryRes.firstOrNull()?.id?.UID()
        }
    }

    fun getPasswordFromId(userId: UserId): Password? = this.lock.read {
        transaction {
            val userToGet = User.findById(userId.toUUID())
            return@transaction userToGet?.password as HashedPassword
        }

    }

    fun updatePassword(userId: EntityID<UUID>, newPassword: Password) {
        this.lock.write {
            transaction {
                val userToUpdate = User.findById(userId)
                    ?: throw IllegalArgumentException("User with id $userId not found")
                userToUpdate.password = newPassword as PlainPassword
            }
        }
    }

    fun list(): List<User> = this.lock.read {
        transaction {
            User.all().toList()
        }
    }

    /**
     * Lists all users with given role
     *
     * @param role The role to filter by
     * @return List of users with the given role
     */
    fun listUsersWithRole(role: UserRole): List<User> = this.lock.read {
        transaction {
            User.find { Users.role eq role }.toList()
        }
    }

    /**
     * Counts all users with given role.
     *
     * @param role The role to filter by
     * @return Number of users with the given role
     */
    fun countUsersWithRole(role: UserRole): Int = this.lock.read {
        transaction {
            User.find { Users.role eq role }.count()
        }
    }.toInt()


    fun listGroups(): List<Group> = this.lock.read {
        transaction {
            Group.all().toList() // need eager loading users outside!
        }
    }

    /**
     * Creates a new group with the given name and adds the users to it.
     *
     * @param groupName The name of the group to create.
     * @param usernames The list of usernames to add to the group.
     */

    // NOTE : Userids should be used instead of usernames ...
    fun createGroup(groupName: String, usernames: List<String>) {
        this.lock.write {

            transaction {
                if (!Group.find { Groups.name eq groupName }.empty()) {
                    throw GroupNameConflictException()
                }
            }

            transaction {
                val userListToAdd = mutableListOf<User>()

                for (username in usernames.distinct()) { // remove duplicates in usernames
                    val userToAdd = User.find { Users.username eq username }.firstOrNull()
                        ?: throw UsernameNotFoundException("$username is not found, abort this group creation")
                    userListToAdd.add(userToAdd)
                }
                if (userListToAdd.isNotEmpty()) {
                    Group.new {
                        name = groupName
                        users = SizedCollection(userListToAdd)
                    }
                }
            }
        }
    }

    /**
     * Remove a group of the given name.
     *
     * @param groupName The name of the group to remove.
     *
     * NOTE : This should use the group id instead of the name
     */
    fun removeGroup(groupName: String) {
        this.lock.write {
            transaction {
                val groupToRemove = Group.find { Groups.name eq groupName }.firstOrNull()
                    ?: throw GroupNameNotFoundException()
                GroupUsers.deleteWhere { group_id eq groupToRemove.id }
                groupToRemove.delete()
            }
        }
    }

    /**
     * Alter a group with the given id.
     *
     * @param groupId The id of the group to alter.
     * @param newName The new name of the group.
     * @param newUserIds The list of usernames to add to the group.
     */
    fun updateGroup(groupId: GroupId, newName : String, newUserIds : List<UserId>) {
        // TODO : Use a lock here ?
        transaction {
            val group = Group.findById(groupId.toUUID())
                ?: throw GroupNameNotFoundException()
            group.name = newName
            val newUserEntities = User.find { Users.id inList newUserIds.distinct().map { it.toUUID() } }
            group.users = newUserEntities
        }
    }

    fun removeAllGroups() {
        this.lock.write {
            transaction {
                GroupUsers.deleteAll()
                Groups.deleteAll()
            }
        }
    }

    fun areInSameGroup(username1: String, username2: String): Boolean = this.lock.read {
        transaction {
            val user1 = User.find { Users.username eq username1 }.firstOrNull()
            val user2 = User.find { Users.username eq username2 }.firstOrNull()
            if (user1 == null || user2 == null) throw UsernameNotFoundException()

            val user1Groups = GroupUsers.
                 select(GroupUsers.group_id)
                .where { GroupUsers.user_id eq user1.id }
                .map { it[GroupUsers.group_id] }
                .toSet()

            val user2Groups = GroupUsers
                .select(GroupUsers.group_id)
                .where { GroupUsers.user_id eq user2.id }
                .map { it[GroupUsers.group_id] }
                .toSet()
            return@transaction user1Groups.intersect(user2Groups).isNotEmpty()
        }
    }

    fun getUserRoleByUserID(userId: UserId): UserRole? = this.lock.read {
        transaction {
            val userToGet = User.findById(userId.toUUID())
            return@transaction userToGet?.role
        }
    }

    fun getUserRoleByUserName(username: String): UserRole? = this.lock.read {
        transaction {
            val userToGet = User.find { Users.username eq username }.firstOrNull()
            return@transaction userToGet?.role
        }
    }

    fun checkIfUserIsActive(username: String): Boolean{
        val userSession = AccessManager.listSessions().firstOrNull { it.user.name == username }
        if (userSession != null) {
            return true
        }
        return false
    }

    fun listOfActiveUsersByRole(role: UserRole): List<User> = this.lock.read {
        transaction {
            val listOfActiveUsers = mutableListOf<User>()
            AccessManager.listSessions().forEach { session ->
                if (session.user.role == role) {
                    listOfActiveUsers.add(session.user)
                }
            }
            return@transaction listOfActiveUsers
        }
    }

    fun getUsersIDsFromUserRole(role: UserRole): List<UserId> = this.lock.read {
        transaction {
            val listOfActiveUsers = mutableListOf<UserId>()
            AccessManager.listSessions().forEach { session ->
                if (session.user.role == role) {
                    listOfActiveUsers.add(session.user.id.UID())
                }
            }
            return@transaction listOfActiveUsers
        }
    }
}


class UsernameConflictException(message: String = "Username already exists!") : Exception(message)
class GroupNameConflictException(message: String = "Group name already exists!") : Exception(message)
class UsernameNotFoundException(message: String = "Username not found.") : Exception(message)
class GroupNameNotFoundException(message: String = "Group name not found.") : Exception(message)