
package ch.ddis.speakeasy.util

/**
 * A templated version of Kotlin's built-in `require` function that allows specifying a custom exception type.
 *
 * @param condition The condition to validate
 * @param message The error message to include in the exception
 * @throws E The exception type specified by the exceptionFactory
 */
inline fun <reified E : Exception> require(
    condition: Boolean,
    message: String
) {
    if (!condition) {
        val constructor = E::class.java.getConstructor(String::class.java)
        throw constructor.newInstance(message)
    }
}