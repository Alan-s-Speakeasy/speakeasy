package ch.ddis.speakeasy.util

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class Config(
    val httpPort: Int = 8080,
    val httpsPort: Int = 8443,
    val enableSsl: Boolean = false,
    val keystorePath: String = "keystore.jks",
    val keystorePassword: String = "password",
    val dataPath: String = "data"
    ){

    init {
        File(dataPath).mkdirs()
    }

    companion object{
        fun read(file: File): Config? {
            val mapper = ObjectMapper()
            return try {
                mapper.readValue(file, Config::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

}