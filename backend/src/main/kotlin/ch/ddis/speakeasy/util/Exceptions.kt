package ch.ddis.speakeasy.util

import ch.ddis.speakeasy.chat.ChatRoomId
import ch.ddis.speakeasy.db.UserId

/**
 * Exception thrown when a form with the specified string is not found.
 */
class FormNotFoundException(formName: String) : Exception("Form with name $formName not found")


/**
 * Indicates that the form is of an invalid format or contains validation errors.
 */
class InvalidFormException(message: String) : Exception(message)

class UserNotFoundException(userId: UserId) : Exception("User with ID $userId not found")

class ChatRoomNotFoundException(roomId: ChatRoomId) : IllegalArgumentException("Chat room with ID $roomId not found")