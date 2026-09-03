package com.maphutimoviousteffo.wizprly.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedChat(
    val id: String,
    val name: String,
    val lastMessage: String = "",
    val lastMessageType: String = "TEXT",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val aiRole: String = "assistant",
    val aiGender: String = "neutral",
    val isOnline: Boolean = true,
    val isPinned: Boolean = false,
    val customContext: String? = null
)
