package ch.ddis.speakeasy.util

import io.javalin.http.Context
import org.slf4j.Logger
import org.slf4j.MDC
import org.slf4j.Marker

/**
 * Extensions and utilities for consistent logging across the application
 */

/**
 * Get the request ID from the context, or "unknown" if not available
 */
fun Context.getRequestId(): String = this.attribute<String>("requestId") ?: "unknown"

/**
 * Get the session token from the context (shortened for logging)
 */
fun Context.getSessionTokenForLogging(): String? = 
    this.attribute<String>("session")?.take(8)

/**
 * Get the user identifier for logging (username or user ID)
 */
fun Context.getUserForLogging(): String? = 
    this.attribute<String>("user")

/**
 * Set up MDC (Mapped Diagnostic Context) for thread-local logging context
 */
fun Context.setupLoggingContext() {
    MDC.put("requestId", getRequestId())
    MDC.put("ip", ip())
    MDC.put("userAgent", userAgent())
    getSessionTokenForLogging()?.let { MDC.put("sessionToken", it) }
    getUserForLogging()?.let { MDC.put("user", it) }
}

/**
 * Clear logging context when request is done
 */
fun clearLoggingContext() {
    MDC.clear()
}

/**
 * Log with context information automatically included
 */
fun Logger.logWithContext(
    level: String,
    marker: Marker? = null,
    message: String,
    requestId: String,
    vararg args: Any?
) {
    val enrichedMessage = "[$requestId] $message"
    when (level.uppercase()) {
        "TRACE" -> if (marker != null) trace(marker, enrichedMessage, *args) else trace(enrichedMessage, *args)
        "DEBUG" -> if (marker != null) debug(marker, enrichedMessage, *args) else debug(enrichedMessage, *args)
        "INFO" -> if (marker != null) info(marker, enrichedMessage, *args) else info(enrichedMessage, *args)
        "WARN" -> if (marker != null) warn(marker, enrichedMessage, *args) else warn(enrichedMessage, *args)
        "ERROR" -> if (marker != null) error(marker, enrichedMessage, *args) else error(enrichedMessage, *args)
    }
}

/**
 * Log info with automatic context
 */
fun Logger.infoWithContext(
    marker: Marker? = null,
    message: String,
    requestId: String,
    vararg args: Any?
) = logWithContext("INFO", marker, message, requestId, *args)

/**
 * Log warning with automatic context
 */
fun Logger.warnWithContext(
    marker: Marker? = null,
    message: String,
    requestId: String,
    vararg args: Any?
) = logWithContext("WARN", marker, message, requestId, *args)

/**
 * Log error with automatic context
 */
fun Logger.errorWithContext(
    marker: Marker? = null,
    message: String,
    requestId: String,
    vararg args: Any?
) = logWithContext("ERROR", marker, message, requestId, *args)



