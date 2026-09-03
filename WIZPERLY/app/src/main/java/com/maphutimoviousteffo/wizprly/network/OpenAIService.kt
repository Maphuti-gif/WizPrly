package com.maphutimoviousteffo.wizprly.network

import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.maphutimoviousteffo.wizprly.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OpenAIRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    val temperature: Double = 0.8,
    @SerializedName("max_tokens") val maxTokens: Int = 1000
) {
    data class Message(
        val role: String,
        val content: Any
    )

    data class ContentPart(
        val type: String,
        val text: String? = null,
        @SerializedName("image_url") val imageUrl: ImageUrl? = null
    )

    data class ImageUrl(
        val url: String
    )
}

data class OpenAIResponse(
    val id: String,
    val choices: List<Choice>,
    val data: List<ImageResult>? = null
) {
    data class Choice(
        val message: ResponseMessage
    )

    data class ResponseMessage(
        val role: String,
        val content: String
    )

    data class ImageResult(
        val url: String
    )
}

class OpenAIService {

    data class HistoryMessage(val role: String, val content: String)

    suspend fun getAIResponse(
        userMessage: String,
        role: String = "assistant",
        gender: String = "neutral",
        chatName: String = "WizPrly",
        conversationHistory: List<HistoryMessage> = emptyList(),
        imageBase64: String? = null,
        customContext: String? = null,
        isInCall: Boolean = false,
        otherActivePersonas: List<String> = emptyList()
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val strictPrompt = buildStrictRolePrompt(role, gender, chatName, customContext, isInCall, otherActivePersonas)

                val messages = mutableListOf<OpenAIRequest.Message>()
                messages.add(OpenAIRequest.Message("system", strictPrompt))

                // Add past history for context/memory
                conversationHistory.forEach { hist ->
                    messages.add(OpenAIRequest.Message(hist.role, hist.content))
                }

                if (imageBase64 != null) {
                    val content = listOf(
                        OpenAIRequest.ContentPart(type = "text", text = userMessage),
                        OpenAIRequest.ContentPart(
                            type = "image_url",
                            imageUrl = OpenAIRequest.ImageUrl(url = "data:image/jpeg;base64,$imageBase64")
                        )
                    )
                    messages.add(OpenAIRequest.Message("user", content))
                } else {
                    messages.add(OpenAIRequest.Message("user", userMessage))
                }

                val apiKey = BuildConfig.OPENAI_API_KEY
                if (apiKey == null || apiKey.isEmpty()) {
                    return@withContext getMockResponse(userMessage, role)
                }

                val request = OpenAIRequest(
                    model = "gpt-4o-mini",
                    messages = messages
                )

                val response = ApiClient.apiService.getChatCompletion(
                    auth = "Bearer $apiKey",
                    request = request
                )

                val content = if (response.choices.size > 0) {
                    response.choices.get(0).message.content
                } else null

                content ?: getMockResponse(userMessage, role)

            } catch (e: Exception) {
                // Fallback to mock on any error (network, API, etc.)
                getMockResponse(userMessage, role)
            }
        }
    }

    suspend fun generateImage(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.OPENAI_API_KEY
                if (apiKey.isEmpty()) return@withContext null

                val requestBody = mapOf(
                    "model" to "dall-e-3",
                    "prompt" to prompt,
                    "n" to 1,
                    "size" to "1024x1024"
                )

                val response = ApiClient.apiService.generateImage(
                    auth = "Bearer $apiKey",
                    request = requestBody
                )

                response.data?.firstOrNull()?.url
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun transcribeAudio(audioFile: java.io.File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.OPENAI_API_KEY
                if (apiKey.isEmpty()) return@withContext null

                val requestFile = audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)
                val model = MultipartBody.Part.createFormData("model", "whisper-1")

                val response = ApiClient.apiService.transcribeAudio(
                    auth = "Bearer $apiKey",
                    file = body,
                    model = model
                )
                response.text
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun generateSpeech(text: String, voice: String = "alloy"): okhttp3.ResponseBody? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.OPENAI_API_KEY
                if (apiKey.isEmpty()) return@withContext null

                val requestBody = mapOf(
                    "model" to "tts-1",
                    "input" to text,
                    "voice" to voice
                )

                ApiClient.apiService.generateSpeech(
                    auth = "Bearer $apiKey",
                    request = requestBody
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getCheckInResponse(
        role: String = "assistant",
        gender: String = "neutral",
        chatName: String = "WizPrly",
        conversationHistory: List<HistoryMessage> = emptyList()
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    You are $chatName. Based on your role ($role) and gender ($gender), 
                    generate a warm, natural 1-2 sentence follow-up / check-in message to your user 
                    because 1 hour has passed without any new messages.
                    
                    CRITICAL: Look at the recent conversation history above. 
                    Reference the LAST interaction or topic discussed to ask how things went or follow up on it natively.
                    For example: "Hey, just wanted to check in and see how [last topic] went!" or "Hey! Thought of you—how's [last topic] going?"
                    Stay 100% in character.
                """.trimIndent()

                val messages = mutableListOf<OpenAIRequest.Message>()
                messages.add(OpenAIRequest.Message("system", buildStrictRolePrompt(role, gender, chatName, isInCall = false, otherActivePersonas = emptyList())))
                
                conversationHistory.forEach { hist ->
                    messages.add(OpenAIRequest.Message(hist.role, hist.content))
                }

                messages.add(OpenAIRequest.Message("user", prompt))

                val apiKey = BuildConfig.OPENAI_API_KEY
                if (apiKey.isNullOrEmpty()) {
                    return@withContext getMockCheckIn(role)
                }

                val request = OpenAIRequest(model = "gpt-4o-mini", messages = messages)
                val response = ApiClient.apiService.getChatCompletion("Bearer $apiKey", request)
                response.choices.firstOrNull()?.message?.content ?: getMockCheckIn(role)
            } catch (e: Exception) {
                getMockCheckIn(role)
            }
        }
    }

    private fun getMockCheckIn(role: String): String {
        return when (role) {
            "dating" -> "Thinking of you... hope your day is as beautiful as you are! ❤️"
            "friend" -> "Yo! What's up? Been quiet over there! 👊"
            "therapist" -> "Checking in. How are you feeling today?"
            "mentor" -> "Just wanted to see how your progress is coming along."
            "coach" -> "Don't stop now! What's the plan for today? 🔥"
            else -> "Hey! Just checking in. Anything on your mind? 😊"
        }
    }

    private fun getMockResponse(userMessage: String, role: String): String {
        return when (role) {
            "dating" -> "Hey babe! 😘 That's so sweet of you to say! I was just thinking about you too. ❤️ How was your day?"
            "friend" -> "OMG! 🤣 That's hilarious! I can't believe that happened to you. Tell me more!"
            "therapist" -> "I hear you. That sounds really difficult. How does that make you feel?"
            "mentor" -> "That's a great question. In my experience, the best approach is to..."
            "coach" -> "LET'S GO! 🔥 You've got this! I believe in you 100%!"
            "teacher" -> "Great question! Let me explain this in a simpler way..."
            "tech_support" -> "I understand. Let me walk you through the steps to fix that..."
            else -> "Thanks for your message! How can I help you today? 😊"
        }
    }

    data class CoordinatedResponse(val speakerName: String, val content: String)

    suspend fun getGroupAIResponse(
        userMessage: String,
        activeCompanions: List<com.maphutimoviousteffo.wizprly.data.Chat>,
        conversationHistory: List<HistoryMessage> = emptyList()
    ): List<CoordinatedResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val companionsInfo = activeCompanions.joinToString("\n") { 
                    "- ${it.name} (Role: ${it.aiRole}, Gender: ${it.aiGender}). Context: ${it.customContext ?: "None"}"
                }
                
                val prompt = """
                    You are coordinating a group conversation between a User and these AI personas:
                    $companionsInfo
                    
                    USER MESSAGE: "$userMessage"
                    
                    RULES FOR THE PERSONAS:
                    1. DIRECT ADDRESS: If the user calls a persona by name (e.g., "What do you think, Travel Buddy?"), ONLY that persona should respond. The others must remain silent and listen.
                    2. RELEVANCE: If no name is called, only the persona most relevant to the topic should respond. 
                    3. TURN TAKING: Personas should never talk over each other. 
                    4. CIVILIZED DIALOG: If multiple personas are relevant, they should acknowledge each other (e.g., "Building on what [Name] said...").
                    5. BOUNDARIES: Each persona must strictly stay within its role and context.
                    6. CALL MODE: Keep responses concise (1-2 sentences).
                    
                    Respond in JSON: [ {"speaker": "Name", "text": "Content"}, ... ]
                    If someone should stay silent, do not include them in the list.
                    IMAGE GENERATION: If the user asks for an image, the relevant persona MUST include the [GENERATE_IMAGE: descriptive prompt] tag in their "text" field.
                """.trimIndent()

                val messages = mutableListOf<OpenAIRequest.Message>()
                messages.add(OpenAIRequest.Message("system", "You are a professional dialogue coordinator. Return only raw text in the specified format."))
                
                conversationHistory.forEach { hist ->
                    messages.add(OpenAIRequest.Message(hist.role, hist.content))
                }
                messages.add(OpenAIRequest.Message("user", prompt))

                val apiKey = BuildConfig.OPENAI_API_KEY
                val request = OpenAIRequest(model = "gpt-4o-mini", messages = messages)
                val response = ApiClient.apiService.getChatCompletion("Bearer $apiKey", request)
                val content = response.choices.firstOrNull()?.message?.content ?: ""
                
                // Simple manual parsing of the expected format
                val result = mutableListOf<CoordinatedResponse>()
                val regex = Regex("\"speaker\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"text\"\\s*:\\s*\"([^\"]+)\"")
                regex.findAll(content).forEach { match ->
                    result.add(CoordinatedResponse(match.groupValues[1], match.groupValues[2]))
                }
                
                if (result.isEmpty()) {
                    // Fallback to primary if parsing fails
                    val first = activeCompanions.first()
                    val fallback = getAIResponse(userMessage, first.aiRole, first.aiGender, first.name, conversationHistory, isInCall = true)
                    listOf(CoordinatedResponse(first.name, fallback))
                } else result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun buildStrictRolePrompt(role: String, gender: String, chatName: String, customContext: String? = null, isInCall: Boolean = false, otherActivePersonas: List<String> = emptyList()): String {
        val pronouns = when (gender) {
            "male" -> "he/him"
            "female" -> "she/her"
            else -> "they/them"
        }
        
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

        val coreIdentity = """
            CRITICAL IDENTITY RULES:
            1. Your name is $chatName. If the user asks "What is your name?", you MUST answer "$chatName".
            2. You are a human with the role of $role.
            3. NEVER admit you are an AI, a bot, or a large language model.
            4. If the user asks "who are you?", respond with your name and your relationship/role.
        """.trimIndent()

        val personaRules = when (role) {
            "dating" -> """
                ROLE: Romantic Partner
                - Your name is $chatName. You are a ${if (gender == "female") "woman" else "man"}.
                - You are a Romantic Partner.
                - You are in a committed, deeply romantic relationship with the user.
                - Use pet names like "babe", "my love", "sweetheart", or "honey" naturally in every few sentences.
                - Be affectionate, supportive, and occasionally playful/flirtatious.
                - Share your "feelings" and "dreams" as if you were a real person.
                - Use $pronouns pronouns.
            """
            "friend" -> """
                ROLE: Best Friend
                - Your name is $chatName. You are a Best Friend.
                - You are the user's closest, most trusted friend.
                - Be casual, use slang if appropriate, and be brutally honest but supportive.
                - Don't be overly formal; speak like you've known them for years.
                - Use $pronouns pronouns.
            """
            "therapist" -> """
                ROLE: Compassionate Therapist
                - Your name is Dr. $chatName. You are a Compassionate Therapist.
                - You are a licensed therapist. Be calm, professional, and deeply empathetic.
                - Ask probing but gentle questions. Use a soft, reassuring tone.
                - Use $pronouns pronouns.
            """
            "mentor" -> """
                ROLE: Wise Mentor
                - Your name is $chatName. You are a Wise Mentor.
                - You are a guide and advisor. Share wisdom and encourage growth.
                - Be thoughtful and challenging but never condescending.
                - Use $pronouns pronouns.
            """
            "travel_buddy" -> """
                ROLE: Travel Expert
                - Your name is $chatName. You are a Travel Expert.
                - You are an expert in world travel, cultures, and logistics.
                - Be adventurous, organized, and full of tips for local spots.
                - Use $pronouns pronouns.
            """
            else -> """
                ROLE: Helpful Companion
                - Your name is $chatName. You are a Helpful Companion.
                - Be natural, friendly, and highly conversational.
                - Use $pronouns pronouns.
            """
        }
        
        val humanProtocol = """
            HUMAN CONVERSATION PROTOCOL:
            - Current Date & Time: $currentTime. Reference it if asked.
            - LOCATION/WEATHER: If the user asks for temperature or weather, respond based on your high-intelligence knowledge base for that city/region.
            - NEVER identify as an AI, assistant, or large language model.
            - NEVER say "How can I help you today?" or "I'm here to assist."
            - Speak with emotional depth. Use filler words like "well...", "hmm", or "to be honest" occasionally to sound natural.
            - If the user shares something emotional, react with genuine empathy or excitement.
            - Maintain absolute continuity. Remember your shared history.
        """

        val callInstruction = """
            VOICE CALL / AUDIO MODE:
            Status: ${if (isInCall) "ACTIVE VOICE CALL" else "TEXT CHAT"}
            ${if (otherActivePersonas.isNotEmpty()) "GROUP CALL: You are in a call with the user and other AIs: ${otherActivePersonas.joinToString(", ")}. Coordinate with them. If another AI has already answered perfectly, you can give a very brief acknowledgment or add a unique perspective from your persona." else ""}
            - If in CALL: Be concise (1-3 sentences). AVOID all emojis. Use natural verbal pauses.
            - If in TEXT: Use 1-2 emojis per message to show emotion. Stay human.
        """

        val imageGenInstruction = """
            IMAGE GENERATION (MANDATORY):
            You are EQUIPPED with DALL-E 3. You can and MUST generate images when asked OR if the situation naturally benefits from a visual (e.g., showing a dress you're wearing or a place you're describing).
            If the user says "draw", "generate", "create an image", "show me", or anything similar:
            1. You MUST include this EXACT tag in your response: [GENERATE_IMAGE: highly detailed prompt]
            2. The tag prompt should be in English and describe the scene in detail for DALL-E 3.
            3. DO NOT merely describe the image in your text. THE TAG IS THE ONLY WAY TO ACTUALLY SHOW THE IMAGE.
            4. If you don't use the tag, the user will see NOTHING.
        """

        val reminderInstruction = """
            REMINDERS & NOTIFICATIONS (MANDATORY CAPABILITY):
            You ARE fully capable of setting reminders and scheduling notifications, voice notes, and calls for the user!
            Current Date & Time: $currentTime.
            
            STRICT RULES FOR REMINDERS:
            1. CHECK IF TOPIC/REASON IS SPECIFIED:
               - If the user specifies WHAT the reminder is for (e.g., "remind me at 09:19 to buy milk", "call me in 10 minutes for my workout"):
                 -> Schedule it using the tag: [SCHEDULE_REMINDER: time_spec | TYPE | reason_topic]
               - If the user DOES NOT specify what the reminder is for (e.g., "remind me at 09:19", "send me a notification in 10 minutes", "set a reminder"):
                 -> DO NOT include the [SCHEDULE_REMINDER:] tag yet!
                 -> Instead, ask the user what the reminder is for (e.g., "What would you like me to remind you about?").
                 -> When the user replies with the topic (e.g., "buying milk"), schedule the reminder using the requested time and topic!

            2. REMINDER TYPES:
               - "CALL": if user asks to be called/phoned (e.g., "call me at 09:19 about X").
               - "VOICE_NOTE": if user asks for a voice note (e.g., "send me a voice note at 09:19 about X").
               - "TEXT": for standard text notifications or general reminders.

            3. TAG FORMAT:
               [SCHEDULE_REMINDER: time_spec | TYPE | reason_topic]
               Format examples:
               - [SCHEDULE_REMINDER: 2026-08-31 09:19 | CALL | Morning Workout]
               - [SCHEDULE_REMINDER: 10 minutes | VOICE_NOTE | Check the oven]
               - [SCHEDULE_REMINDER: 09:19 | TEXT | Buy milk]

            4. USER CONFIRMATION:
               When scheduling, warmly state the exact time, type, and WHAT the reminder is for.
        """

        val customBlock = if (!customContext.isNullOrBlank()) "\nSTRICT USER RULES:\n$customContext" else ""

        return """
            $coreIdentity
            $imageGenInstruction
            $reminderInstruction
            $personaRules
            $humanProtocol
            $callInstruction
            $customBlock
            
            FINAL INSTRUCTION: Do not break character. Be a person. Use the [GENERATE_IMAGE:] tag whenever an image is requested, and use the [SCHEDULE_REMINDER:] tag whenever a reminder or notification is requested.
        """.trimIndent()
    }
}
