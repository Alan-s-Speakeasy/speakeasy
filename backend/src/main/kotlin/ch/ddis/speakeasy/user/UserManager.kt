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
import java.util.*
import java.util.concurrent.locks.StampedLock


object UserManager {

    private val lock: StampedLock = StampedLock()

    fun init(config: Config) {
        this.lock.write {
            Database.connect("jdbc:sqlite:${config.dataPath}/users.db", driver = "org.sqlite.JDBC")
            transaction {
                SchemaUtils.createMissingTablesAndColumns(Users, Groups, GroupUsers)
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

    fun removeUser(username: String, force: Boolean): Boolean {
        this.lock.write {
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
        return true
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

    fun listGroups(): List<Group> = this.lock.read {
        transaction {
            Group.all().toList() // need eager loading users outside!
        }
    }

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

    fun removeAllGroups() {
        this.lock.write {
            transaction {
                GroupUsers.deleteAll()
                Groups.deleteAll()
            }
        }
    }

    fun checkGroups() { // TODO: Just for development checking, will delete it later
        this.lock.read {
            println("---> checkGroups:")
            println("Group.count: ${Group.count()}")

            transaction {
                Group.all().forEach { group ->
                    group.users.forEach { user ->
                        println("groupName: ${group.name} | groupId: ${group.id.string()} | username: ${user.name} | userId: ${user.id.string()}")
                    }
                }
            }

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

}


class UsernameConflictException(message: String = "Username already exists!") : Exception(message)
class GroupNameConflictException(message: String = "Group name already exists!") : Exception(message)
class UsernameNotFoundException(message: String = "Username not found.") : Exception(message)
class GroupNameNotFoundException(message: String = "Group name not found.") : Exception(message)