package ch.ddis.speakeasy.user

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.mindrot.jbcrypt.BCrypt

sealed class Password(private val pass: String)

class PlainPassword(internal val pass: String) : Password(pass) {

    fun hash(): HashedPassword {
        return HashedPassword(BCrypt.hashpw(pass, BCrypt.gensalt()))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlainPassword

        if (pass != other.pass) return false

        return true
    }

    override fun hashCode(): Int {
        return pass.hashCode()
    }

}

class HashedPassword @JsonCreator constructor(@JsonProperty("hash") val hash: String) : Password(hash) {
    fun check(plain: PlainPassword): Boolean {
        return BCrypt.checkpw(plain.pass, this.hash)
    }
}