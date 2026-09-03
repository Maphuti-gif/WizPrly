package com.maphutimoviousteffo.wizprly.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey
    val id: String = "",
    val name: String = "",
    val lastMessage: String = "",
    val lastMessageType: String = "TEXT",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val participants: List<String> = emptyList(),
    val aiRole: String = "assistant",
    val aiGender: String = "neutral",
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val backgroundUri: String? = null,
    val customContext: String? = null,
    val lastCheckInTime: Long = 0,
    val isCheckInPending: Boolean = false
)