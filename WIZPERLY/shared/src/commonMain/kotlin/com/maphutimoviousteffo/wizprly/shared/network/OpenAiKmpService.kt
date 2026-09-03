package com.maphutimoviousteffo.wizprly.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class KmpOpenAiRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<KmpMessage>
) {
    @Serializable
    data class KmpMessage(
        val role: String,
        val content: String
    )
}

@Serializable
data class KmpOpenAiResponse(
    val choices: List<Choice> = emptyList()
) {
    @Serializable
    data class Choice(
        val message: KmpMessage
    )

    @Serializable
    data class KmpMessage(
        val role: String = "",
        val content: String = ""
    )
}

class OpenAiKmpService(
    private val apiKeyProvider: () -> String = { "" }
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getAIResponse(
        userMessage: String,
        role: String = "assistant",
        gender: String = "neutral",
        chatName: String = "WizPrly",
        customContext: String? = null
    ): String {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return getMockResponse(role, chatName)
        }

        return try {
            val systemPrompt = "Your name is $chatName. You are a $gender $role. Be warm, human, and concise."
            val request = KmpOpenAiRequest(
                messages = listOf(
                    KmpOpenAiRequest.KmpMessage("system", systemPrompt),
                    KmpOpenAiRequest.KmpMessage("user", userMessage)
                )
            )

            val httpResponse: KmpOpenAiResponse = client.post("https://api.openai.com/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }.body()

            httpResponse.choices.firstOrNull()?.message?.content ?: getMockResponse(role, chatName)
        } catch (e: Exception) {
            getMockResponse(role, chatName)
        }
    }

    private fun getMockResponse(role: String, chatName: String): String {
        return when (role) {
            "dating" -> "Hey babe! 😘 I was just thinking about you. How was your day?"
            "friend" -> "Yo! 🤣 That's awesome. What else is happening?"
            "therapist" -> "I hear you. Tell me more about how that made you feel."
            "mentor" -> "That's a great question. Here's how I'd approach it..."
            else -> "Hey there! I'm $chatName. How can I help you today? 😊"
        }
    }
}
