package com.maphutimoviousteffo.wizprly.shared.repository

import com.maphutimoviousteffo.wizprly.shared.currentTimeMillis
import com.maphutimoviousteffo.wizprly.shared.model.SharedChat
import com.maphutimoviousteffo.wizprly.shared.model.SharedMessage
import com.maphutimoviousteffo.wizprly.shared.network.OpenAiKmpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InMemorySharedChatRepository(
    private val openAiService: OpenAiKmpService = OpenAiKmpService()
) : SharedChatRepository {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _chats = MutableStateFlow<List<SharedChat>>(
        listOf(
            SharedChat(id = "1", name = "AI Assistant", lastMessage = "Welcome to WizPrly!", aiRole = "assistant"),
            SharedChat(id = "2", name = "Travel Buddy", lastMessage = "Where to next?", aiRole = "travel_buddy")
        )
    )
    override val chats: StateFlow<List<SharedChat>> = _chats.asStateFlow()

    private val _messagesMap = MutableStateFlow<Map<String, List<SharedMessage>>>(
        mapOf(
            "1" to listOf(
                SharedMessage(id = "m1", chatId = "1", senderId = "ai", content = "Welcome to WizPrly! How can I help you today?", timestamp = 1725100000000L, isAI = true)
            ),
            "2" to listOf(
                SharedMessage(id = "m2", chatId = "2", senderId = "ai", content = "Where to next? Tell me your dream destination!", timestamp = 1725100000000L, isAI = true)
            )
        )
    )

    private val _currentChatId = MutableStateFlow<String?>(null)
    private val _currentMessages = MutableStateFlow<List<SharedMessage>>(emptyList())
    override val currentMessages: StateFlow<List<SharedMessage>> = _currentMessages.asStateFlow()

    override fun loadChats() {}

    override fun loadMessages(chatId: String) {
        _currentChatId.value = chatId
        _currentMessages.value = _messagesMap.value[chatId] ?: emptyList()
    }

    override suspend fun createChat(name: String, role: String, gender: String, context: String?): String {
        val newId = (currentTimeMillis()).toString()
        val newChat = SharedChat(
            id = newId,
            name = name,
            aiRole = role,
            aiGender = gender,
            customContext = context,
            lastMessage = "New chat created",
            lastMessageTime = currentTimeMillis()
        )
        _chats.value = _chats.value + newChat
        _messagesMap.value = _messagesMap.value + (newId to emptyList())
        return newId
    }

    override suspend fun sendMessage(chatId: String, content: String, imageUri: String?, audioUri: String?) {
        val userMsg = SharedMessage(
            id = (currentTimeMillis()).toString(),
            chatId = chatId,
            senderId = "user",
            content = content,
            timestamp = currentTimeMillis(),
            imageUri = imageUri,
            audioUri = audioUri,
            type = if (audioUri != null) "VOICE_NOTE" else if (imageUri != null) "IMAGE" else "TEXT"
        )

        val existing = _messagesMap.value[chatId] ?: emptyList()
        val updatedMsgs = existing + userMsg
        _messagesMap.value = _messagesMap.value + (chatId to updatedMsgs)
        if (_currentChatId.value == chatId) {
            _currentMessages.value = updatedMsgs
        }

        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(lastMessage = content, lastMessageType = userMsg.type, lastMessageTime = currentTimeMillis()) else it
        }

        scope.launch {
            val chat = _chats.value.find { it.id == chatId }
            val aiResponseText = openAiService.getAIResponse(
                userMessage = content,
                role = chat?.aiRole ?: "assistant",
                gender = chat?.aiGender ?: "neutral",
                chatName = chat?.name ?: "WizPrly",
                customContext = chat?.customContext
            )

            val aiMsg = SharedMessage(
                id = (currentTimeMillis() + 1).toString(),
                chatId = chatId,
                senderId = "ai",
                content = aiResponseText,
                timestamp = currentTimeMillis(),
                isAI = true
            )

            val msgsAfterAi = (_messagesMap.value[chatId] ?: emptyList()) + aiMsg
            _messagesMap.value = _messagesMap.value + (chatId to msgsAfterAi)
            if (_currentChatId.value == chatId) {
                _currentMessages.value = msgsAfterAi
            }

            _chats.value = _chats.value.map {
                if (it.id == chatId) it.copy(lastMessage = aiResponseText, lastMessageType = "TEXT", lastMessageTime = currentTimeMillis()) else it
            }
        }
    }

    override suspend fun togglePin(chatId: String) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isPinned = !it.isPinned) else it
        }
    }

    override suspend fun deleteChat(chatId: String) {
        _chats.value = _chats.value.filter { it.id != chatId }
        _messagesMap.value = _messagesMap.value - chatId
    }

    override suspend fun addReaction(messageId: String, reaction: String?) {
        val chatId = _currentChatId.value ?: return
        val existing = _messagesMap.value[chatId] ?: return
        val updated = existing.map {
            if (it.id == messageId) it.copy(reaction = reaction) else it
        }
        _messagesMap.value = _messagesMap.value + (chatId to updated)
        _currentMessages.value = updated
    }
}
