package com.maphutimoviousteffo.wizprly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val isAI: Boolean = false,
    val type: String = "TEXT",
    val imageUri: String? = null,
    val audioUri: String? = null,
    val audioDuration: Int = 0,
    val audioTranscription: String? = null,
    val reaction: String? = null
)
