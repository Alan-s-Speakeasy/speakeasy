package ch.ddis.speakeasy.util

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.util.concurrent.TimeUnit

@JsonIgnoreProperties(ignoreUnknown = true)
data class Config(
    val httpPort: Int = 8080,
    val httpsPort: Int = 8443,
    val enableSsl: Boolean = false,
    val keystorePath: String = "keystore.jks",
    val keystorePassword: String = "password",
    val dataPath: String = "data",
    // Amount of request allowed per rateLimitUnit.
    val rateLimit: Int = 60 * 7,
    // Time unit for rate limiting.
    val rateLimitUnit: TimeUnit = TimeUnit.MINUTES,
    // Rate limit for login (should be tighter to prevent bruteforce) (per minute)
    // NOTE : With token based rate limiting, the incoming requests don't have a token so the IP is used.
    // As for our usecase we don't have ip limiting, we up an arbitrary high value.
    val rateLimitLogin : Int = 60 * 7 * 100,
    ){

    /**
     * Create a new Config object with the provided dataPath.
     * @param dataPath the new dataPath
     */
    fun withDataPath(dataPath: String): Config {
        // I don't know a better way to do that in Kotlin sorry
        return Config(httpPort, httpsPort, enableSsl, keystorePath, keystorePassword, dataPath)
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