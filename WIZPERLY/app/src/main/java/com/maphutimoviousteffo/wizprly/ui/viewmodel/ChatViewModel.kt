package com.maphutimoviousteffo.wizprly.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.apache.poi.xwpf.usermodel.XWPFDocument
import com.maphutimoviousteffo.wizprly.data.Chat
import com.maphutimoviousteffo.wizprly.data.Message
import com.maphutimoviousteffo.wizprly.data.WizPrlyDatabase
import com.maphutimoviousteffo.wizprly.network.OpenAIService
import com.maphutimoviousteffo.wizprly.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = WizPrlyDatabase.getInstance(application)
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("wizprly_prefs", Context.MODE_PRIVATE)
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _themeColor = MutableStateFlow(sharedPrefs.getInt("theme_color", 0xFFA855F7.toInt()))
    val themeColor: StateFlow<Int> = _themeColor.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(sharedPrefs.getBoolean("use_dynamic_color", false))
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(sharedPrefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _voiceFeedbackEnabled = MutableStateFlow(sharedPrefs.getBoolean("voice_feedback_enabled", true))
    val voiceFeedbackEnabled: StateFlow<Boolean> = _voiceFeedbackEnabled.asStateFlow()

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "WizPrly User") ?: "WizPrly User")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userProfileImageUri = MutableStateFlow(sharedPrefs.getString("user_profile_image", null))
    val userProfileImageUri: StateFlow<String?> = _userProfileImageUri.asStateFlow()

    private val _selectedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedChatIds: StateFlow<Set<String>> = _selectedChatIds.asStateFlow()

    private var currentChatId: String? = null
    private val openAIService = OpenAIService()

    private var lastUserActivityTime = System.currentTimeMillis()
    private var isChatScreenActive = false

    private val _isUserInCall = MutableStateFlow(false)
    val isUserInCall: StateFlow<Boolean> = _isUserInCall.asStateFlow()

    private val callTranscript = StringBuilder()

    private val _activeCompanions = MutableStateFlow<List<Chat>>(emptyList())
    val activeCompanions: StateFlow<List<Chat>> = _activeCompanions.asStateFlow()

    private val _isAiRecording = MutableStateFlow(false)
    val isAiRecording: StateFlow<Boolean> = _isAiRecording.asStateFlow()

    private val _globalBackgroundUri = MutableStateFlow(sharedPrefs.getString("global_background", null))
    val globalBackgroundUri: StateFlow<String?> = _globalBackgroundUri.asStateFlow()

    // Audio Playback State (WhatsApp style)
    private val _currentlyPlayingMessageId = MutableStateFlow<String?>(null)
    val currentlyPlayingMessageId: StateFlow<String?> = _currentlyPlayingMessageId.asStateFlow()

    private val _audioPlaybackProgress = MutableStateFlow<Float>(0f)
    val audioPlaybackProgress: StateFlow<Float> = _audioPlaybackProgress.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val mediaPlayer = android.media.MediaPlayer()

    init {
        PDFBoxResourceLoader.init(context)
        loadChats()
        monitorPresence()
        checkInactivity()
        setupMediaPlayer()
    }

    private fun setupMediaPlayer() {
        mediaPlayer.setOnCompletionListener {
            val finishedId = _currentlyPlayingMessageId.value
            _currentlyPlayingMessageId.value = null
            _audioPlaybackProgress.value = 0f
            if (finishedId != null) {
                playNextAudioMessage(finishedId)
            }
        }
    }

    fun playAudio(messageId: String) {
        val message = _messages.value.find { it.id == messageId } ?: return
        val audioUri = message.audioUri ?: return

        if (_currentlyPlayingMessageId.value == messageId) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            } else {
                mediaPlayer.start()
                trackProgress(messageId)
            }
            return
        }

        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, Uri.parse(audioUri))
            mediaPlayer.prepare()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(_playbackSpeed.value)
            }
            mediaPlayer.start()
            _currentlyPlayingMessageId.value = messageId
            trackProgress(messageId)
        } catch (e: Exception) {
            _error.value = "Error playing audio"
        }
    }

    private fun trackProgress(messageId: String) {
        viewModelScope.launch {
            while (mediaPlayer.isPlaying && _currentlyPlayingMessageId.value == messageId) {
                val duration = mediaPlayer.duration
                if (duration > 0) {
                    _audioPlaybackProgress.value = mediaPlayer.currentPosition.toFloat() / duration.toFloat()
                }
                delay(100)
            }
        }
    }

    fun togglePlaybackSpeed() {
        val newSpeed = when (_playbackSpeed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackSpeed.value = newSpeed
        if (mediaPlayer.isPlaying) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(newSpeed)
            }
        }
    }

    fun seekAudio(progress: Float) {
        if (_currentlyPlayingMessageId.value != null && mediaPlayer.duration > 0) {
            val msec = (progress * mediaPlayer.duration).toInt()
            mediaPlayer.seekTo(msec)
            _audioPlaybackProgress.value = progress
        }
    }

    private fun playNextAudioMessage(currentId: String) {
        val allMessages = _messages.value
        val currentIndex = allMessages.indexOfFirst { it.id == currentId }
        if (currentIndex != -1 && currentIndex < allMessages.size - 1) {
            for (i in (currentIndex + 1) until allMessages.size) {
                val nextMsg = allMessages[i]
                if (nextMsg.audioUri != null && nextMsg.type == "VOICE_NOTE") {
                    playAudio(nextMsg.id)
                    break
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.release()
    }

    fun recordActivity() {
        lastUserActivityTime = System.currentTimeMillis()
        updateOnlineStatus(true)
    }

    private fun updateOnlineStatus(online: Boolean) {
        currentChatId?.let { id ->
            val chat = _chats.value.find { it.id == id }
            if (chat != null && chat.isOnline != online) {
                viewModelScope.launch(Dispatchers.IO) {
                    val updated = chat.copy(isOnline = online)
                    chatDao.updateChat(updated)
                    withContext(Dispatchers.Main) {
                        _chats.value = _chats.value.map { if (it.id == id) updated else it }
                    }
                }
            }
        }
    }

    private fun monitorPresence() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(10000)
                val isStillActive = System.currentTimeMillis() - lastUserActivityTime < 30000
                updateOnlineStatus(isStillActive)
            }
        }
    }

    fun loadChats() {
        viewModelScope.launch(Dispatchers.IO) {
            val allChats = chatDao.getAllChats()
            if (allChats.isEmpty()) {
                val initialChats = listOf(
                    Chat(id = "1", name = "AI Assistant", lastMessage = "Welcome to WizPrly!", aiRole = "assistant"),
                    Chat(id = "2", name = "Travel Buddy", lastMessage = "Where to next?", aiRole = "friend")
                )
                initialChats.forEach { chatDao.insertChat(it) }
                withContext(Dispatchers.Main) { _chats.value = initialChats }
            } else {
                withContext(Dispatchers.Main) { _chats.value = allChats }
            }
        }
    }

    fun loadMessages(chatId: String) {
        currentChatId = chatId
        isChatScreenActive = true
        viewModelScope.launch(Dispatchers.IO) {
            val chat = chatDao.getChatById(chatId)
            if (chat != null && (chat.unreadCount > 0 || chat.isCheckInPending)) {
                val updated = chat.copy(unreadCount = 0, isCheckInPending = false)
                chatDao.updateChat(updated)
                loadChats()
            }
            val dbMessages = messageDao.getMessagesForChat(chatId)
            withContext(Dispatchers.Main) { _messages.value = dbMessages }
        }
    }

    fun setChatScreenActive(active: Boolean) {
        isChatScreenActive = active
    }

    fun createChat(name: String, role: String = "assistant", gender: String = "neutral", context: String? = null, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalAiGender = gender // Selected gender directly applies to the AI companion

            val id = UUID.randomUUID().toString()
            val chat = Chat(
                id = id,
                name = name,
                aiRole = role,
                aiGender = finalAiGender,
                customContext = context,
                lastMessage = "New chat created",
                lastMessageTime = System.currentTimeMillis()
            )
            chatDao.insertChat(chat)
            loadChats()
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun inviteToCall(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chat = chatDao.getChatById(chatId)
            if (chat != null && !_activeCompanions.value.any { it.id == chatId }) {
                _activeCompanions.value = _activeCompanions.value + chat
                val joinMsg = Message(
                    id = UUID.randomUUID().toString(),
                    chatId = currentChatId!!,
                    senderId = "system",
                    content = "${chat.name} joined the call",
                    type = "CALL_STATUS",
                    isFromCall = true
                )
                messageDao.insertMessage(joinMsg)
                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value + joinMsg
                    sendMessage("[System: ${chat.name} has just joined. Say hello to everyone briefly.]", isFromCall = true)
                }
            }
        }
    }

    fun removeCompanionFromCall(chatId: String) {
        _activeCompanions.value = _activeCompanions.value.filter { it.id != chatId }
        if (_activeCompanions.value.isEmpty()) {
            setCallActive(false)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val chat = chatDao.getChatById(chatId)
                chat?.let {
                    val leaveMsg = Message(
                        id = UUID.randomUUID().toString(),
                        chatId = currentChatId!!,
                        senderId = "system",
                        content = "${it.name} left the call",
                        type = "CALL_STATUS",
                        isFromCall = true
                    )
                    messageDao.insertMessage(leaveMsg)
                    withContext(Dispatchers.Main) { _messages.value = _messages.value + leaveMsg }
                }
            }
        }
    }

    fun setCallActive(active: Boolean) {
        if (_isUserInCall.value != active) {
            _isUserInCall.value = active
            viewModelScope.launch(Dispatchers.IO) {
                currentChatId?.let { chatId ->
                    val mainChat = chatDao.getChatById(chatId)
                    if (active && mainChat != null) {
                        _activeCompanions.value = listOf(mainChat)
                    }

                    if (active) {
                        callTranscript.setLength(0)
                        val startMsg = Message(
                            id = UUID.randomUUID().toString(),
                            chatId = chatId,
                            senderId = "system",
                            content = "Call started",
                            type = "CALL_STATUS",
                            isFromCall = true
                        )
                        messageDao.insertMessage(startMsg)
                        withContext(Dispatchers.Main) { _messages.value = _messages.value + startMsg }
                    } else {
                        if (callTranscript.isNotEmpty()) {
                            val chat = mainChat ?: chatDao.getChatById(chatId)
                            val fullTranscriptText = callTranscript.toString()

                            withContext(Dispatchers.Main) {
                                _messages.value = _messages.value.filter { !it.isFromCall || it.type == "CALL_STATUS" }
                            }

                            val identity = "- My name is ${chat?.name ?: "WizPrly"}. I am your ${chat?.aiRole ?: "assistant"}."
                            
                            val summary = openAIService.getAIResponse(
                                userMessage = "The call has ended. Here is the transcript of our conversation: $fullTranscriptText. Please provide a clear, warm, and descriptive verbal summary of everything we discussed. Start with something like 'In our call today...' and cover all the key points as if you are recounting it to me.",
                                role = chat?.aiRole ?: "assistant",
                                gender = chat?.aiGender ?: "neutral",
                                chatName = chat?.name ?: "WizPrly",
                                customContext = identity,
                                isInCall = false
                            )

                            val voice = if (chat?.aiGender == "female") "shimmer" else "onyx"
                            var aiAudioUri: String? = null
                            val recordId = UUID.randomUUID().toString()

                            _isAiRecording.value = true
                            try {
                                val responseBody = openAIService.generateSpeech(summary, voice)
                                responseBody?.let { body ->
                                    try {
                                        val file = java.io.File(context.filesDir, "master_call_record_$recordId.mp3")
                                        val inputStream = body.byteStream()
                                        val outputStream = java.io.FileOutputStream(file)
                                        inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
                                        aiAudioUri = Uri.fromFile(file).toString()
                                    } catch (e: Exception) {}
                                }
                            } finally {
                                _isAiRecording.value = false
                            }

                            val callRecordMsg = Message(
                                id = recordId,
                                chatId = chatId,
                                senderId = "ai",
                                content = "Full Call Record", // Clean archival title
                                isAI = true,
                                type = "VOICE_NOTE",
                                audioUri = aiAudioUri,
                                audioDuration = (summary.length / 10).coerceAtLeast(10),
                                isFromCall = true,
                                timestamp = System.currentTimeMillis()
                            )
                            messageDao.insertMessage(callRecordMsg)
                            mainChat?.let {
                                val updated = it.copy(
                                    lastMessage = callRecordMsg.content,
                                    lastMessageType = callRecordMsg.type,
                                    lastMessageTime = System.currentTimeMillis()
                                )
                                chatDao.updateChat(updated)
                            }
                            withContext(Dispatchers.Main) { _messages.value = _messages.value + callRecordMsg }
                        }

                        val endMsg = Message(
                            id = UUID.randomUUID().toString(),
                            chatId = chatId,
                            senderId = "system",
                            content = "Call ended",
                            type = "CALL_STATUS",
                            isFromCall = true,
                            timestamp = System.currentTimeMillis() + 10
                        )
                        messageDao.insertMessage(endMsg)
                        mainChat?.let {
                            val updated = it.copy(
                                lastMessage = endMsg.content,
                                lastMessageType = endMsg.type,
                                lastMessageTime = System.currentTimeMillis()
                            )
                            chatDao.updateChat(updated)
                            loadChats()
                        }
                        withContext(Dispatchers.Main) {
                            _messages.value = _messages.value + endMsg
                            _activeCompanions.value = emptyList()
                        }
                    }
                }
            }
        }
    }

    fun sendMessage(content: String, imageUri: String? = null, fileUri: String? = null, fileName: String? = null, audioUri: String? = null, audioDuration: Int = 0, audioTranscription: String? = null, isFromCall: Boolean = false) {
        if ((content.isBlank() && imageUri == null && fileUri == null && audioUri == null) || currentChatId == null) return
        recordActivity()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isSending.value = true
                    _typing.value = true
                }

                val chatId = currentChatId!!
                val chat = _chats.value.find { it.id == chatId }

                var finalTranscription = audioTranscription
                if (audioUri != null && finalTranscription == null) {
                    _isAiRecording.value = true
                    try {
                        val file = java.io.File(Uri.parse(audioUri).path ?: "")
                        if (file.exists()) {
                            finalTranscription = openAIService.transcribeAudio(file)
                        }
                    } catch (e: Exception) {
                    } finally {
                        _isAiRecording.value = false
                    }
                }

                val userMsgType = when {
                    audioUri != null -> "VOICE_NOTE"
                    fileUri != null -> "DOCUMENT"
                    imageUri != null -> "IMAGE"
                    else -> "TEXT"
                }

                val userMsg = Message(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    senderId = "user",
                    content = if (userMsgType == "VOICE_NOTE") (finalTranscription ?: "") else content,
                    imageUri = imageUri,
                    fileUri = fileUri,
                    fileName = fileName,
                    audioUri = audioUri,
                    audioDuration = audioDuration,
                    audioTranscription = finalTranscription,
                    isFromCall = isFromCall,
                    type = userMsgType
                )
                
                if (!_isUserInCall.value) {
                    messageDao.insertMessage(userMsg)
                    chat?.let {
                        val updated = it.copy(
                            lastMessage = userMsg.content.take(50),
                            lastMessageType = userMsg.type,
                            lastMessageTime = System.currentTimeMillis()
                        )
                        chatDao.updateChat(updated)
                        // Local update for speed, avoid full reload
                        _chats.value = _chats.value.map { c -> if (c.id == it.id) updated else c }
                    }
                }
                withContext(Dispatchers.Main) { _messages.value = _messages.value + userMsg }

                if (_isUserInCall.value) {
                    callTranscript.append("User: ${finalTranscription ?: content}\n")
                }

                val history = messageDao.getMessagesForChat(chatId).takeLast(40)
                val conversationHistory = history.map { msg ->
                    OpenAIService.HistoryMessage(
                        role = if (msg.senderId == "user") "user" else "assistant",
                        content = if (msg.type == "CALL_STATUS") "[System: ${msg.content}]" else msg.content
                    )
                }

                var imageBase64: String? = null
                var finalUserMessage = content
                if (imageUri != null) {
                    imageBase64 = encodeImageToBase64(imageUri)
                    if (finalUserMessage.isBlank()) finalUserMessage = "Describe this image and respond based on your persona."
                }
                if (fileUri != null) {
                    val extractedText = extractTextFromFile(fileUri)
                    finalUserMessage = if (content.isNotBlank()) "$content\n\n[Document: $fileName]\n$extractedText" else "[Document: $fileName]\n$extractedText"
                }

                val activeChatList = _activeCompanions.value
                val responses = mutableListOf<OpenAIService.CoordinatedResponse>()

                if (_isUserInCall.value && activeChatList.size > 1) {
                    val groupCompanions = activeChatList.map { 
                        it.copy(customContext = "- My name is ${it.name}. I am your ${it.aiRole}.\n${it.customContext ?: ""}")
                    }
                    val groupResponses = openAIService.getGroupAIResponse(
                        userMessage = finalTranscription ?: content,
                        activeCompanions = groupCompanions,
                        conversationHistory = conversationHistory
                    )
                    responses.addAll(groupResponses)
                } else {
                    val companion = activeChatList.firstOrNull() ?: chat
                    val role = companion?.aiRole ?: "assistant"
                    val name = companion?.name ?: "WizPrly"
                    val identity = "- My name is $name. I am your $role."
                    val context = if (companion?.customContext != null) "$identity\n${companion.customContext}" else identity
                    
                    val response = openAIService.getAIResponse(
                        userMessage = finalTranscription ?: (if (finalUserMessage.isNotBlank()) finalUserMessage else "Hi"),
                        role = role,
                        gender = companion?.aiGender ?: "neutral",
                        chatName = name,
                        customContext = context,
                        conversationHistory = conversationHistory,
                        imageBase64 = imageBase64,
                        isInCall = _isUserInCall.value
                    )
                    responses.add(OpenAIService.CoordinatedResponse(name, response))
                }

                for (coordResp in responses) {
                    val name = coordResp.speakerName
                    val responseText = processReminderTag(coordResp.content, chatId, name)
                    val aiMsgId = UUID.randomUUID().toString()
                    var aiAudioUri: String? = null

                    if (responseText.contains("[GENERATE_IMAGE:")) {
                        val tag = responseText.substringAfter("[GENERATE_IMAGE:").substringBefore("]")
                        if (tag.isNotBlank()) generateAIImage(tag)
                    }

                    val shouldGenerateAudio = (audioUri != null || _isUserInCall.value) && _voiceFeedbackEnabled.value
                    if (shouldGenerateAudio) {
                        _isAiRecording.value = true
                        try {
                            val companion = activeChatList.find { it.name == name } ?: chat
                            val voice = if (companion?.aiGender == "female") "shimmer" else "onyx"
                            val responseBody = openAIService.generateSpeech(responseText, voice)
                            responseBody?.let { body ->
                                try {
                                    val file = java.io.File(context.filesDir, "ai_voice_$aiMsgId.mp3")
                                    val inputStream = body.byteStream()
                                    val outputStream = java.io.FileOutputStream(file)
                                    inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
                                    aiAudioUri = Uri.fromFile(file).toString()
                                } catch (e: Exception) {}
                            }
                        } finally {
                            _isAiRecording.value = false
                        }
                    }

                    if (!_isUserInCall.value) {
                        val aiMsg = Message(
                            id = aiMsgId,
                            chatId = chatId,
                            senderId = "ai",
                            content = responseText,
                            isAI = true,
                            type = if (aiAudioUri != null) "VOICE_NOTE" else "TEXT",
                            audioUri = aiAudioUri,
                            audioDuration = if (aiAudioUri != null) (responseText.length / 15).coerceAtLeast(1) else 0
                        )
                        messageDao.insertMessage(aiMsg)
                        chat?.let {
                            val updated = it.copy(
                                lastMessage = aiMsg.content.take(50),
                                lastMessageType = aiMsg.type,
                                lastMessageTime = System.currentTimeMillis(),
                                unreadCount = if (!isChatScreenActive) it.unreadCount + 1 else 0,
                                isCheckInPending = false
                            )
                            chatDao.updateChat(updated)
                            // Local update for speed
                            _chats.value = _chats.value.map { c -> if (c.id == it.id) updated else c }
                        }
                        withContext(Dispatchers.Main) { _messages.value = _messages.value + aiMsg }
                    } else {
                        callTranscript.append("$name: $responseText\n")
                        val tempMsg = Message(
                            id = aiMsgId, chatId = chatId, senderId = "ai", content = "[$name]: $responseText",
                            isAI = true, type = "VOICE_NOTE", audioUri = aiAudioUri, isFromCall = true
                        )
                        withContext(Dispatchers.Main) { _messages.value = _messages.value + tempMsg }
                    }
                    if (activeChatList.size > 1) delay(1000)
                }

                if (_notificationsEnabled.value && !_isUserInCall.value && !isChatScreenActive) {
                    val first = responses.firstOrNull()
                    NotificationHelper.showNotification(context, first?.speakerName ?: "WizPrly", first?.content ?: "New message", chatId, getTotalUnreadCount())
                }

                withContext(Dispatchers.Main) {
                    _isSending.value = false
                    _typing.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _error.value = "Failed to send: ${e.message}"
                    _isSending.value = false
                    _typing.value = false
                }
            }
        }
    }

    fun toggleTheme() {
        val newVal = !_isDarkMode.value
        _isDarkMode.value = newVal
        sharedPrefs.edit().putBoolean("dark_mode", newVal).apply()
    }

    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
        sharedPrefs.edit().putBoolean("dark_mode", isDark).apply()
    }

    fun setThemeColor(color: Int) {
        _themeColor.value = color
        _useDynamicColor.value = false
        sharedPrefs.edit()
            .putInt("theme_color", color)
            .putBoolean("use_dynamic_color", false)
            .apply()
    }

    fun setUseDynamicColor(use: Boolean) {
        _useDynamicColor.value = use
        sharedPrefs.edit().putBoolean("use_dynamic_color", use).apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        sharedPrefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        _voiceFeedbackEnabled.value = enabled
        sharedPrefs.edit().putBoolean("voice_feedback_enabled", enabled).apply()
    }

    fun updateUserName(name: String) {
        _userName.value = name
        sharedPrefs.edit().putString("user_name", name).apply()
    }

    fun updateUserProfileImage(uriString: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uriString == null) {
                _userProfileImageUri.value = null
                sharedPrefs.edit().remove("user_profile_image").apply()
                return@launch
            }
            try {
                val persistentUri = saveImageToInternal(uriString, "profile_image.jpg")
                withContext(Dispatchers.Main) {
                    _userProfileImageUri.value = persistentUri
                    sharedPrefs.edit().putString("user_profile_image", persistentUri).apply()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _error.value = "Failed to save profile image" }
            }
        }
    }

    fun toggleChatSelection(chatId: String) {
        val current = _selectedChatIds.value
        _selectedChatIds.value = if (current.contains(chatId)) current - chatId else current + chatId
    }

    fun clearChatSelection() { _selectedChatIds.value = emptySet() }

    fun deleteSelectedChats() {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedChatIds.value.forEach { id ->
                chatDao.getChatById(id)?.let {
                    chatDao.deleteChat(it)
                    messageDao.getMessagesForChat(id).forEach { m -> messageDao.deleteMessage(m) }
                }
            }
            withContext(Dispatchers.Main) {
                clearChatSelection()
                loadChats()
            }
        }
    }

    fun pinSelectedChats() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = _selectedChatIds.value
            if (ids.size + chatDao.getPinnedCount() > 3) {
                withContext(Dispatchers.Main) { _error.value = "Max 3 chats can be pinned" }
            } else {
                ids.forEach { id -> chatDao.getChatById(id)?.let { chatDao.updateChat(it.copy(isPinned = true)) } }
                withContext(Dispatchers.Main) {
                    clearChatSelection()
                    loadChats()
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearAllTables()
            sharedPrefs.edit().clear().apply()
            withContext(Dispatchers.Main) {
                _chats.value = emptyList()
                _messages.value = emptyList()
                loadChats()
            }
        }
    }

    fun setGlobalBackground(uri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val persistentUri = if (uri != null) saveImageToInternal(uri, "global_background.jpg") else null
            _globalBackgroundUri.value = persistentUri
            sharedPrefs.edit().putString("global_background", persistentUri).apply()
        }
    }

    fun renameChat(chatId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.getChatById(chatId)?.let {
                chatDao.updateChat(it.copy(name = newName))
                loadChats()
            }
        }
    }

    fun clearChat(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.getMessagesForChat(chatId).forEach { messageDao.deleteMessage(it) }
            withContext(Dispatchers.Main) { if (currentChatId == chatId) _messages.value = emptyList() }
        }
    }

    fun deleteSingleMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = _messages.value.find { it.id == messageId }
            if (msg != null) {
                messageDao.deleteMessage(msg)
                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value.filter { it.id != messageId }
                }
                currentChatId?.let { chatId ->
                    val remaining = messageDao.getMessagesForChat(chatId)
                    val lastMsg = remaining.lastOrNull()
                    chatDao.getChatById(chatId)?.let { chat ->
                        val updated = chat.copy(
                            lastMessage = lastMsg?.content?.take(50) ?: "",
                            lastMessageType = lastMsg?.type ?: "TEXT",
                            lastMessageTime = lastMsg?.timestamp ?: System.currentTimeMillis()
                        )
                        chatDao.updateChat(updated)
                        _chats.value = _chats.value.map { c -> if (c.id == chatId) updated else c }
                    }
                }
            }
        }
    }

    private fun processReminderTag(text: String, chatId: String, chatName: String): String {
        if (!text.contains("[SCHEDULE_REMINDER:")) return text
        var cleanText = text
        
        // 3-part tag: [SCHEDULE_REMINDER: time | TYPE | message]
        val regex3 = Regex("\\[SCHEDULE_REMINDER:\\s*([^|\\]]+)\\|\\s*([^|\\]]+)\\|\\s*([^\\]]+)\\]")
        regex3.findAll(text).forEach { match ->
            val timeSpec = match.groupValues[1].trim()
            val reminderType = match.groupValues[2].trim().uppercase()
            val reminderMsg = match.groupValues[3].trim()
            
            val targetTimeMs = parseReminderTime(timeSpec)
            if (targetTimeMs > System.currentTimeMillis()) {
                val title = if (reminderType == "CALL") "📞 Incoming Call from $chatName" else "Reminder from $chatName"
                NotificationHelper.scheduleReminder(
                    context = context,
                    title = title,
                    message = reminderMsg,
                    timeInMillis = targetTimeMs,
                    chatId = chatId,
                    reminderType = reminderType
                )
            }
            cleanText = cleanText.replace(match.value, "").trim()
        }

        // 2-part tag fallback: [SCHEDULE_REMINDER: time | message]
        val regex2 = Regex("\\[SCHEDULE_REMINDER:\\s*([^|\\]]+)\\|\\s*([^\\]]+)\\]")
        regex2.findAll(cleanText).forEach { match ->
            val timeSpec = match.groupValues[1].trim()
            val reminderMsg = match.groupValues[2].trim()
            val targetTimeMs = parseReminderTime(timeSpec)
            if (targetTimeMs > System.currentTimeMillis()) {
                NotificationHelper.scheduleReminder(
                    context = context,
                    title = "Reminder from $chatName",
                    message = reminderMsg,
                    timeInMillis = targetTimeMs,
                    chatId = chatId,
                    reminderType = "TEXT"
                )
            }
            cleanText = cleanText.replace(match.value, "").trim()
        }
        return cleanText
    }

    private fun parseReminderTime(spec: String): Long {
        val now = System.currentTimeMillis()
        try {
            val lower = spec.lowercase().trim()
            if (lower.contains("minute") || lower.contains("min")) {
                val num = lower.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 1L
                return now + (num * 60 * 1000L)
            }
            if (lower.contains("hour") || lower.contains("hr")) {
                val num = lower.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 1L
                return now + (num * 3600 * 1000L)
            }

            var targetHour = -1
            var targetMinute = -1

            val timeRegex = Regex("(\\d{1,2}):(\\d{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE)
            val match = timeRegex.find(spec)
            if (match != null) {
                var h = match.groupValues[1].toInt()
                val m = match.groupValues[2].toInt()
                val ampm = match.groupValues[3].lowercase()

                if (ampm == "pm" && h < 12) h += 12
                if (ampm == "am" && h == 12) h = 0

                targetHour = h
                targetMinute = m
            }

            if (targetHour != -1 && targetMinute != -1) {
                val calTarget = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                    set(java.util.Calendar.MINUTE, targetMinute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                var targetMs = calTarget.timeInMillis
                if (targetMs <= now) {
                    if (now - targetMs <= 10 * 60 * 1000L) {
                        targetMs = now + 30000L // 30 seconds from now if time was within last 10 mins
                    } else {
                        calTarget.add(java.util.Calendar.DAY_OF_MONTH, 1)
                        targetMs = calTarget.timeInMillis
                    }
                }
                return targetMs
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return now + (60 * 1000L)
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.getChatById(chatId)?.let {
                chatDao.deleteChat(it)
                messageDao.getMessagesForChat(chatId).forEach { m -> messageDao.deleteMessage(m) }
                loadChats()
            }
        }
    }

    fun togglePin(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chat = chatDao.getChatById(chatId) ?: return@launch
            if (!chat.isPinned && chatDao.getPinnedCount() >= 3) {
                withContext(Dispatchers.Main) { _error.value = "Max 3 chats can be pinned" }
                return@launch
            }
            chatDao.updateChat(chat.copy(isPinned = !chat.isPinned))
            loadChats()
        }
    }

    fun addReaction(messageId: String, reaction: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _messages.value.find { it.id == messageId }?.let {
                val updated = it.copy(reaction = reaction)
                messageDao.updateMessage(updated)
                withContext(Dispatchers.Main) { _messages.value = _messages.value.map { m -> if (m.id == messageId) updated else m } }
            }
        }
    }

    fun generateAIImage(prompt: String) {
        if (currentChatId == null) return
        recordActivity()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { _isSending.value = true }
                openAIService.generateImage(prompt)?.let { url ->
                    val aiMsg = Message(id = UUID.randomUUID().toString(), chatId = currentChatId!!, senderId = "ai", content = "Here is the image you requested: $prompt", imageUri = url, isAI = true, type = "IMAGE")
                    messageDao.insertMessage(aiMsg)
                    withContext(Dispatchers.Main) { _messages.value = _messages.value + aiMsg }
                } ?: withContext(Dispatchers.Main) { _error.value = "Failed to generate image." }
            } catch (e: Exception) { withContext(Dispatchers.Main) { _error.value = "Error: ${e.message}" }
            } finally { withContext(Dispatchers.Main) { _isSending.value = false } }
        }
    }

    fun setChatBackground(chatId: String, uri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val persistentUri = if (uri != null) saveImageToInternal(uri, "chat_bg_$chatId.jpg") else null
            chatDao.getChatById(chatId)?.let {
                chatDao.updateChat(it.copy(backgroundUri = persistentUri))
                loadChats()
            }
        }
    }

    private fun checkInactivity() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(30000) // Check every 30 seconds
                val now = System.currentTimeMillis()
                _chats.value.forEach { chat ->
                    // 1 hour = 3600000 ms of no action
                    if (now - chat.lastMessageTime >= 3600000L && !chat.isCheckInPending && currentChatId != chat.id) {
                        val messages = messageDao.getMessagesForChat(chat.id)
                        if (messages.isNotEmpty()) {
                            triggerCheckUp(chat)
                        }
                    }
                }
            }
        }
    }

    private fun triggerCheckUp(chat: Chat) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = messageDao.getMessagesForChat(chat.id).takeLast(10).map { msg ->
                OpenAIService.HistoryMessage(if (msg.senderId == "user") "user" else "assistant", msg.content)
            }
            val response = openAIService.getCheckInResponse(chat.aiRole, chat.aiGender, chat.name, history)
            val aiMsg = Message(id = UUID.randomUUID().toString(), chatId = chat.id, senderId = "ai", content = response, isAI = true)
            messageDao.insertMessage(aiMsg)

            val updatedChat = chat.copy(
                lastMessage = response.take(50), 
                lastMessageType = "TEXT",
                lastMessageTime = System.currentTimeMillis(), 
                unreadCount = chat.unreadCount + 1, 
                isCheckInPending = true
            )
            chatDao.updateChat(updatedChat)
            _chats.value = _chats.value.map { c -> if (c.id == chat.id) updatedChat else c }

            if (_notificationsEnabled.value && !_isUserInCall.value && (currentChatId != chat.id || !isChatScreenActive)) {
                NotificationHelper.showNotification(context, chat.name, response, chat.id, getTotalUnreadCount())
            }
        }
    }

    fun clearError() { _error.value = null }

    private fun getTotalUnreadCount(): Int = _chats.value.sumOf { it.unreadCount }

    private fun saveImageToInternal(uriString: String, fileName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(uriString))
            val file = java.io.File(context.filesDir, fileName)
            inputStream?.use { input -> java.io.FileOutputStream(file).use { output -> input.copyTo(output) } }
            Uri.fromFile(file).toString()
        } catch (e: Exception) { null }
    }

    private fun encodeImageToBase64(uriString: String): String? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(context.contentResolver.openInputStream(Uri.parse(uriString)), null, options)
            
            var scale = 1
            val maxDimension = 1024
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                scale = Math.pow(2.0, Math.ceil(Math.log(maxDimension.toDouble() / Math.max(options.outWidth, options.outHeight).toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt()
            }
            
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
            val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(Uri.parse(uriString)), null, decodeOptions) ?: return null
            
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun extractTextFromFile(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return "Empty file."
            val fileName = getFileName(context, uri).lowercase()
            when {
                fileName.endsWith(".pdf") -> {
                    val doc = PDDocument.load(inputStream)
                    val text = PDFTextStripper().getText(doc)
                    doc.close()
                    if (text.length > 15000) text.take(15000) + "..." else text
                }
                fileName.endsWith(".docx") -> {
                    val doc = XWPFDocument(inputStream)
                    val text = doc.paragraphs.joinToString("\n") { it.text }
                    doc.close()
                    if (text.length > 15000) text.take(15000) + "..." else text
                }
                else -> {
                    val text = inputStream.bufferedReader().readText()
                    if (text.length > 10000) text.take(10000) + "..." else text
                }
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "Document"
        context.contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }
}
