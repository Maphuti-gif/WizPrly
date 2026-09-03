package com.maphutimoviousteffo.wizprly.shared.repository

import com.maphutimoviousteffo.wizprly.shared.model.SharedChat
import com.maphutimoviousteffo.wizprly.shared.model.SharedMessage
import kotlinx.coroutines.flow.StateFlow

interface SharedChatRepository {
    val chats: StateFlow<List<SharedChat>>
    val currentMessages: StateFlow<List<SharedMessage>>
    
    fun loadChats()
    fun loadMessages(chatId: String)
    suspend fun createChat(name: String, role: String, gender: String, context: String? = null): String
    suspend fun sendMessage(chatId: String, content: String, imageUri: String? = null, audioUri: String? = null)
    suspend fun togglePin(chatId: String)
    suspend fun deleteChat(chatId: String)
    suspend fun addReaction(messageId: String, reaction: String?)
}
