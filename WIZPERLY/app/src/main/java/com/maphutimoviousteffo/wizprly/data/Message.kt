package com.maphutimoviousteffo.wizprly.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [androidx.room.Index(value = ["chatId", "timestamp"])]
)
data class Message(
    @PrimaryKey
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isAI: Boolean = false,
    val type: String = "TEXT", // TEXT, IMAGE, DOCUMENT
    val imageUri: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val reaction: String? = null,
    val audioUri: String? = null,
    val audioDuration: Int = 0,
    val audioTranscription: String? = null,
    val isFromCall: Boolean = false
)