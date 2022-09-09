package ch.ddis.speakeasy.chat

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use =JsonTypeInfo.Id.CLASS, include =JsonTypeInfo.As.PROPERTY, property ="class")
abstract class ChatItemContainer()
