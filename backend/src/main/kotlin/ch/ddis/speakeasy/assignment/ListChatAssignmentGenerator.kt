package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

/**
 * Generates mappings based on a json file encoding a list of list of list of user ids and a prompt
 */
class ListChatAssignmentGenerator(jsonFile: File) : ChatAssignmentGenerator {

    private val jsonList: List<List<List<String>>>
    private var idx = 0

    init {
        val objectMapper = jacksonObjectMapper()

        jsonList = objectMapper.readValue(jsonFile, object :
            TypeReference<List<List<List<String>>>>(){})
    }

    override fun generateAssignments(): List<ChatAssignment> {

        if (idx >= jsonList.size) {
            return emptyList()
        }

        val list = jsonList[idx++]

        val currentSessions = AccessManager.listSessions().associateBy { it.user.id.UID() }

        return list.mapNotNull {
            if ( it.size < 3) {
               null
            } else {
                val sessions = it.subList(0, 2).mapNotNull { id ->
                    currentSessions[UID(id)]
                }
                if (sessions.size != 2) {
                    null
                } else {
                    null
                    /*when {
                        //bot and human
                        sessions[0].user.role == UserRole.BOT && sessions[1].user.role == UserRole.HUMAN ||
                        sessions[0].user.role == UserRole.BOT && sessions[1].user.role == UserRole.ADMIN -> ChatAssignment(sessions[1], sessions[0], it[2])
                        sessions[0].user.role == UserRole.HUMAN && sessions[1].user.role == UserRole.BOT ||
                        sessions[0].user.role == UserRole.ADMIN && sessions[1].user.role == UserRole.BOT -> ChatAssignment(sessions[0], sessions[1], it[2])
                        //admin and human --> admin acts as bot
                        sessions[0].user.role == UserRole.ADMIN && sessions[1].user.role == UserRole.HUMAN -> ChatAssignment(sessions[1], sessions[0], it[2])
                        sessions[0].user.role == UserRole.HUMAN && sessions[1].user.role == UserRole.ADMIN -> ChatAssignment(sessions[0], sessions[1], it[2])
                        //not a valid mapping
                        else -> null
                    }*/
                }
            }
        }
    }


}