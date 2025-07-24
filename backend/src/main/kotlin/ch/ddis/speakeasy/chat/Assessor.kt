package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.db.UserId


data class Assessor(val assessor: UserId) : ChatItemContainer()

data class NoFeedback(val mark: Boolean) : ChatItemContainer()
