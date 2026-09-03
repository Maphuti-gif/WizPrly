package com.maphutimoviousteffo.wizprly.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.maphutimoviousteffo.wizprly.data.Message
import com.maphutimoviousteffo.wizprly.data.WizPrlyDatabase
import com.maphutimoviousteffo.wizprly.network.OpenAIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val topic = intent.getStringExtra("message") ?: "Your scheduled reminder"
        val chatId = intent.getStringExtra("chatId")
        val reminderType = intent.getStringExtra("reminderType")?.uppercase() ?: "TEXT"

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (chatId != null) {
                    val db = WizPrlyDatabase.getInstance(context)
                    val chat = db.chatDao().getChatById(chatId)
                    val aiName = chat?.name ?: "WizPrly"

                    when (reminderType) {
                        "CALL" -> {
                            val callMsg = Message(
                                id = UUID.randomUUID().toString(),
                                chatId = chatId,
                                senderId = "ai",
                                content = "📞 Call Reminder for: $topic",
                                isAI = true,
                                type = "CALL_STATUS",
                                timestamp = System.currentTimeMillis()
                            )
                            db.messageDao().insertMessage(callMsg)
                            chat?.let {
                                db.chatDao().updateChat(
                                    it.copy(
                                        lastMessage = "📞 Call Reminder: $topic",
                                        lastMessageType = "CALL_STATUS",
                                        lastMessageTime = System.currentTimeMillis(),
                                        unreadCount = it.unreadCount + 1
                                    )
                                )
                            }

                            NotificationHelper.showNotification(
                                context = context,
                                title = "📞 Incoming Call from $aiName",
                                message = "Reminder for: $topic",
                                chatId = chatId,
                                autoStartCall = true
                            )
                        }

                        "VOICE_NOTE" -> {
                            val openAIService = OpenAIService()
                            val voice = if (chat?.aiGender == "female") "shimmer" else "onyx"
                            var audioUri: String? = null
                            val msgId = UUID.randomUUID().toString()

                            try {
                                val body = openAIService.generateSpeech("Here is your reminder for: $topic", voice)
                                body?.let { responseBody ->
                                    val file = File(context.filesDir, "reminder_voice_$msgId.mp3")
                                    val inputStream = responseBody.byteStream()
                                    val outputStream = FileOutputStream(file)
                                    inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
                                    audioUri = Uri.fromFile(file).toString()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val voiceMsg = Message(
                                id = msgId,
                                chatId = chatId,
                                senderId = "ai",
                                content = "🎙️ Voice Note Reminder for: $topic",
                                isAI = true,
                                type = "VOICE_NOTE",
                                audioUri = audioUri,
                                audioDuration = (topic.length / 10).coerceAtLeast(5),
                                timestamp = System.currentTimeMillis()
                            )
                            db.messageDao().insertMessage(voiceMsg)
                            chat?.let {
                                db.chatDao().updateChat(
                                    it.copy(
                                        lastMessage = "🎙️ Voice Note: $topic",
                                        lastMessageType = "VOICE_NOTE",
                                        lastMessageTime = System.currentTimeMillis(),
                                        unreadCount = it.unreadCount + 1
                                    )
                                )
                            }

                            NotificationHelper.showNotification(
                                context = context,
                                title = "🎙️ Voice Note Reminder from $aiName",
                                message = "Reminder for: $topic",
                                chatId = chatId
                            )
                        }

                        else -> { // TEXT
                            val textMsg = Message(
                                id = UUID.randomUUID().toString(),
                                chatId = chatId,
                                senderId = "ai",
                                content = "⏰ Reminder for: $topic",
                                isAI = true,
                                type = "TEXT",
                                timestamp = System.currentTimeMillis()
                            )
                            db.messageDao().insertMessage(textMsg)
                            chat?.let {
                                db.chatDao().updateChat(
                                    it.copy(
                                        lastMessage = "⏰ Reminder: $topic",
                                        lastMessageType = "TEXT",
                                        lastMessageTime = System.currentTimeMillis(),
                                        unreadCount = it.unreadCount + 1
                                    )
                                )
                            }

                            NotificationHelper.showNotification(
                                context = context,
                                title = "⏰ Reminder from $aiName",
                                message = "Reminder for: $topic",
                                chatId = chatId
                            )
                        }
                    }
                } else {
                    NotificationHelper.showNotification(context, "⏰ Reminder", "Reminder for: $topic", null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                NotificationHelper.showNotification(context, "⏰ Reminder", "Reminder for: $topic", chatId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
