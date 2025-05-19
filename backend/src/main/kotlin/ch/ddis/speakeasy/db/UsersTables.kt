package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.user.HashedPassword
import ch.ddis.speakeasy.user.Password
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.ReferenceOption

object Users : UUIDTable() {
    var username = varchar("username", 60).uniqueIndex()
    var password = registerColumn<Password>("password", PasswordColumnType())
    val role = customEnumeration(
        name = "role",
        sql = "INTEGER",
        fromDb = { UserRole.fromInt(it as Int) },
        toDb = { UserRole.toInt(it) })
}

class PasswordColumnType : ColumnType() {
    override fun sqlType() = "STRING"

    override fun valueFromDB(value: Any): Any {
        return HashedPassword(value.toString()) // need make sure each stored value is a hashed password
    }

    override fun notNullValueToDB(value: Any): Any {
        return when (value) {
            is HashedPassword -> value.hash
            else -> (value as PlainPassword).hash().hash
        }
    }
}

object Groups : UUIDTable() {
    var name = varchar("name", 60).uniqueIndex()
}

object GroupUsers : UUIDTable() {
    val group_id = reference("group_id", Groups, onDelete = ReferenceOption.CASCADE)
    val user_id = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    init {
        index(true, group_id, user_id) // Unique index
    }
}
