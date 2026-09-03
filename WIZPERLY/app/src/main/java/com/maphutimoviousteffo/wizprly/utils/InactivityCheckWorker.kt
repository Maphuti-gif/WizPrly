package com.maphutimoviousteffo.wizprly.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.maphutimoviousteffo.wizprly.data.Message
import com.maphutimoviousteffo.wizprly.data.WizPrlyDatabase
import com.maphutimoviousteffo.wizprly.network.OpenAIService
import java.util.UUID
import java.util.concurrent.TimeUnit

class InactivityCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val db = WizPrlyDatabase.getInstance(appContext)
            val chats = db.chatDao().getAllChats()
            val now = System.currentTimeMillis()
            val openAIService = OpenAIService()

            chats.forEach { chat ->
                // Check if 1 hour (3600000 ms) has passed since last message AND no check-in is pending
                if (now - chat.lastMessageTime >= 3600000L && !chat.isCheckInPending) {
                    val messages = db.messageDao().getMessagesForChat(chat.id)
                    if (messages.isNotEmpty()) {
                        val history = messages.takeLast(10).map { msg ->
                            OpenAIService.HistoryMessage(
                                role = if (msg.senderId == "user") "user" else "assistant",
                                content = msg.content
                            )
                        }

                        val response = openAIService.getCheckInResponse(
                            role = chat.aiRole,
                            gender = chat.aiGender,
                            chatName = chat.name,
                            conversationHistory = history
                        )

                        val aiMsg = Message(
                            id = UUID.randomUUID().toString(),
                            chatId = chat.id,
                            senderId = "ai",
                            content = response,
                            isAI = true,
                            type = "TEXT",
                            timestamp = System.currentTimeMillis()
                        )
                        db.messageDao().insertMessage(aiMsg)

                        val updatedChat = chat.copy(
                            lastMessage = response.take(50),
                            lastMessageType = "TEXT",
                            lastMessageTime = System.currentTimeMillis(),
                            unreadCount = chat.unreadCount + 1,
                            isCheckInPending = true
                        )
                        db.chatDao().updateChat(updatedChat)

                        val unreadCount = db.chatDao().getAllChats().sumOf { it.unreadCount }
                        NotificationHelper.showNotification(
                            context = appContext,
                            title = chat.name,
                            message = response,
                            chatId = chat.id,
                            totalUnread = unreadCount
                        )
                    }
                }
            }
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "WizPrlyInactivityCheckWork"

        fun scheduleBackgroundInactivityCheck(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<InactivityCheckWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
