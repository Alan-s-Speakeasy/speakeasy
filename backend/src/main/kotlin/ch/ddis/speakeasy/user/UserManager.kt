package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.locks.StampedLock

object UserManager {

    private val users = mutableListOf<User>()

    private lateinit var userFile: File

    private val lock: StampedLock = StampedLock()

    private lateinit var fileLock: FileLock

    private fun updateFileLock() {
        this.fileLock = RandomAccessFile(userFile, "rw").channel.lock()
    }

    fun init(config: Config) = this.lock.write {
        this.userFile = File(File(config.dataPath), "users.csv")
        if (!this.userFile.exists()) {
            return
        }
        csvReader { skipEmptyLine = true }.open(this.userFile) {
            readAllWithHeader().forEach { row: Map<String, String> ->
                val role = try {
                    UserRole.valueOf(row["role"]!!)
                } catch (e: IllegalArgumentException) {
                    System.err.println("Cannot parse user role ${row["role"]}, defaulting to HUMAN")
                    UserRole.HUMAN
                }
                val password = if (row["password"]!!.startsWith("$")) {
                    HashedPassword(row["password"]!!)
                } else {
                    PlainPassword(row["password"]!!)
                }
                val uid = try {
                    row["id"]!!.UID()
                } catch (e: IllegalArgumentException) {
                    UID()
                }

                users.add(User(uid, row["username"]!!, role, password))

            }
        }
        updateFileLock()
    }


    fun addUser(username: String, role: UserRole): String {
        val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val password = List(15) { alphabet.random() }.joinToString("")

        addUser(username, role, PlainPassword(password))
        return password
    }

    fun addUser(username: String, role: UserRole, password: Password) {
        this.lock.write {
            val uid = UID()
            users.add(User(uid, username, role, password))
        }
        store()
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
                    return true
                }
            }
        }
        store()
        return true
    }

    fun store() = this.lock.read {
        this.fileLock.release()
        csvWriter().open(this.userFile) {
            writeRow(listOf("username", "password", "role", "id"))
            users.forEach {
                writeRow(listOf(it.name, it.password.hash, it.role.name, it.id.string))
            }
        }
        updateFileLock()
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
        }
        store()
    }

    fun list(): List<User> = this.lock.read {
        this.users.toList()
    }

}