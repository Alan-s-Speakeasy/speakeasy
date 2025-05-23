package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId

data class Assessor(val assessor: UserId) : ChatItemContainer()

data class NoFeedback(val mark: Boolean) : ChatItemContainer()
