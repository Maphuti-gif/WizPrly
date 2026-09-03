package com.maphutimoviousteffo.wizprly.network

import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun getChatCompletion(
        @Header("Authorization") auth: String,
        @Body request: OpenAIRequest
    ): OpenAIResponse

    @POST("v1/images/generations")
    @Headers("Content-Type: application/json")
    suspend fun generateImage(
        @Header("Authorization") auth: String,
        @Body request: Map<String, Any>
    ): OpenAIResponse

    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part,
        @Part model: MultipartBody.Part
    ): WizPrlyResponse

    @POST("v1/audio/speech")
    @Headers("Content-Type: application/json")
    @Streaming
    suspend fun generateSpeech(
        @Header("Authorization") auth: String,
        @Body request: Map<String, String>
    ): okhttp3.ResponseBody
}

data class WizPrlyResponse(
    val text: String
)