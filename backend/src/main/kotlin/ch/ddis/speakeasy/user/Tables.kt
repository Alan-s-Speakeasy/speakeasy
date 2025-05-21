package ch.ddis.speakeasy.user

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*

object Users : UUIDTable() {
    var username = varchar("username", 60).uniqueIndex()
    var password = registerColumn("password", PasswordColumnType())
    val role = customEnumeration(
        name = "role",
        sql = "INTEGER",
        fromDb = { UserRole.fromInt(it as Int) },
        toDb = { UserRole.toInt(it) })
}

class PasswordColumnType : ColumnType<Password>() {
    override fun sqlType() = "STRING"

    override fun valueFromDB(value: Any): Password {
        return HashedPassword(value.toString()) // need make sure each stored value is a hashed password
    }

    override fun notNullValueToDB(value: Password): Any {
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
