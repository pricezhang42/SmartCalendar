package com.example.smartcalendar.data.ai

import android.util.Log
import com.example.smartcalendar.data.remote.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * AI service that routes all requests to the SmartCalendar backend (.NET).
 * The backend holds the Gemini API key and executes AI calls server-side.
 */
class BackendAiProvider(private val baseUrl: String) : AIService {

    companion object {
        private const val TAG = "BackendAiProvider"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    callTimeout(90, TimeUnit.SECONDS)
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(90, TimeUnit.SECONDS)
                    writeTimeout(90, TimeUnit.SECONDS)
                }
            }
        }
    }

    override suspend fun parseText(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?,
        conversationHistory: List<Pair<String, String>>?,
        onStatus: ((String) -> Unit)?
    ): ProcessingResult {
        val body = ParseTextRequest(
            text = text,
            currentDate = currentDate,
            timezone = timezone,
            calendarContext = calendarContext,
            conversationHistory = conversationHistory?.map { ConversationTurn(it.first, it.second) }
        )
        // Streaming endpoint: backend pushes "🤖 Thinking…", "🔍 Searching: …" status events
        // before the final result, so the typing bubble updates in real time.
        return postJsonStreaming(
            endpoint = "/ai/parse-text-stream",
            jsonBody = json.encodeToString(ParseTextRequest.serializer(), body),
            onStatus = onStatus
        )
    }

    override fun streamParseText(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): Flow<StreamChunk> = flow {
        val result = parseText(text, currentDate, timezone, calendarContext)
        emit(StreamChunk.Complete(result))
    }

    override suspend fun parseImage(
        imageBytes: ByteArray,
        mimeType: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult = uploadMedia(
        endpoint = "/ai/parse-image",
        fileBytes = imageBytes,
        fileName = "image",
        mimeType = mimeType,
        currentDate = currentDate,
        timezone = timezone,
        calendarContext = calendarContext
    )

    override suspend fun parseDocument(
        documentBytes: ByteArray,
        mimeType: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult = uploadMedia(
        endpoint = "/ai/parse-document",
        fileBytes = documentBytes,
        fileName = "document",
        mimeType = mimeType,
        currentDate = currentDate,
        timezone = timezone,
        calendarContext = calendarContext
    )

    override suspend fun refineEvents(
        events: List<ExtractedEvent>,
        instruction: String
    ): ProcessingResult {
        val body = RefineEventsRequest(events = events, instruction = instruction)
        return postJson("/ai/refine", json.encodeToString(RefineEventsRequest.serializer(), body))
    }

    private fun currentAccessToken(): String? =
        runCatching { SupabaseClient.auth.currentSessionOrNull()?.accessToken }.getOrNull()

    private fun HttpRequestBuilder.addAuthHeader() {
        currentAccessToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun postJson(endpoint: String, jsonBody: String): ProcessingResult {
        val token = currentAccessToken()
        if (token.isNullOrBlank()) {
            return ProcessingResult.Error("Not signed in. Please log in before using AI features.")
        }
        return try {
            val response = httpClient.post("$baseUrl$endpoint") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            handleResponse(response.status.isSuccess(), response.status.value, response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "postJson failed", e)
            ProcessingResult.Error("Backend error: ${e.message}", e)
        }
    }

    /**
     * SSE consumer. Reads `data: { ... }` lines, dispatches:
     *   - type=status   → fires onStatus(text)
     *   - type=complete → returns ProcessingResult.Success
     *   - type=error    → returns ProcessingResult.Error
     */
    private suspend fun postJsonStreaming(
        endpoint: String,
        jsonBody: String,
        onStatus: ((String) -> Unit)?
    ): ProcessingResult {
        val token = currentAccessToken()
        if (token.isNullOrBlank()) {
            return ProcessingResult.Error("Not signed in. Please log in before using AI features.")
        }

        return try {
            var finalResult: ProcessingResult? = null

            httpClient.preparePost("$baseUrl$endpoint") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    Log.e(TAG, "SSE error ${response.status}: ${body.take(500)}")
                    val msg = when (response.status.value) {
                        401 -> "Not authorized. Please sign in again."
                        429 -> "Daily AI quota reached. Try again tomorrow."
                        else -> "Backend returned ${response.status.value}: ${body.take(300)}"
                    }
                    finalResult = ProcessingResult.Error(msg)
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substringAfter("data:").trim()
                    if (payload.isEmpty()) continue
                    handleSseEvent(payload, onStatus)?.let { finalResult = it }
                }
            }

            finalResult ?: ProcessingResult.Error("Stream ended without a result")
        } catch (e: Exception) {
            Log.e(TAG, "postJsonStreaming failed", e)
            ProcessingResult.Error("Backend error: ${e.message}", e)
        }
    }

    private fun handleSseEvent(
        payload: String,
        onStatus: ((String) -> Unit)?
    ): ProcessingResult? {
        return try {
            val event = json.decodeFromString<SseEvent>(payload)
            when (event.type) {
                "status" -> {
                    event.text?.let { onStatus?.invoke(it) }
                    null
                }
                "complete" -> ProcessingResult.Success(
                    AIResponse(
                        events = event.events ?: emptyList(),
                        confidence = event.confidence ?: 0f,
                        message = event.message,
                        rawResponse = event.rawResponse ?: payload,
                        warnings = event.warnings ?: emptyList()
                    )
                )
                "error" -> ProcessingResult.Error(event.message ?: "Backend error")
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSE event: $payload", e)
            null
        }
    }

    private suspend fun uploadMedia(
        endpoint: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult {
        val token = currentAccessToken()
        if (token.isNullOrBlank()) {
            return ProcessingResult.Error("Not signed in. Please log in before using AI features.")
        }
        val contextJson = calendarContext?.let {
            json.encodeToString(ListSerializer(CalendarContextEvent.serializer()), it)
        }
        return try {
            val response = httpClient.submitFormWithBinaryData(
                url = "$baseUrl$endpoint",
                formData = formData {
                    append("File", fileBytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        append(HttpHeaders.ContentType, mimeType)
                    })
                    append("CurrentDate", currentDate)
                    append("Timezone", timezone)
                    if (contextJson != null) append("CalendarContext", contextJson)
                }
            ) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            handleResponse(response.status.isSuccess(), response.status.value, response.bodyAsText())
        } catch (e: Exception) {
            Log.e(TAG, "uploadMedia failed", e)
            ProcessingResult.Error("Backend error: ${e.message}", e)
        }
    }

    private fun handleResponse(ok: Boolean, status: Int, body: String): ProcessingResult {
        if (!ok) {
            Log.e(TAG, "Backend error $status: ${body.take(500)}")
            val msg = when (status) {
                401 -> "Not authorized. Please sign in again."
                429 -> "Daily AI quota reached. Try again tomorrow."
                else -> "Backend returned $status: ${body.take(300)}"
            }
            return ProcessingResult.Error(msg)
        }
        return try {
            val dto = json.decodeFromString<BackendAiResponse>(body)
            ProcessingResult.Success(
                AIResponse(
                    events = dto.events,
                    confidence = dto.confidence,
                    message = dto.message,
                    rawResponse = dto.rawResponse ?: body,
                    warnings = dto.warnings ?: emptyList()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse backend response", e)
            ProcessingResult.Error("Failed to parse backend response: ${e.message}", e)
        }
    }

    @Serializable
    private data class ParseTextRequest(
        val text: String,
        val currentDate: String,
        val timezone: String,
        val calendarContext: List<CalendarContextEvent>? = null,
        val conversationHistory: List<ConversationTurn>? = null
    )

    @Serializable
    private data class ConversationTurn(val role: String, val text: String)

    @Serializable
    private data class RefineEventsRequest(
        val events: List<ExtractedEvent>,
        val instruction: String
    )

    @Serializable
    private data class BackendAiResponse(
        val events: List<ExtractedEvent> = emptyList(),
        val confidence: Float = 0f,
        val message: String? = null,
        val rawResponse: String? = null,
        val warnings: List<String>? = null
    )

    /** One line of `data: { ... }` from the SSE stream. Single shape, nullable fields. */
    @Serializable
    private data class SseEvent(
        val type: String,
        val text: String? = null,
        val events: List<ExtractedEvent>? = null,
        val confidence: Float? = null,
        val message: String? = null,
        val rawResponse: String? = null,
        val warnings: List<String>? = null
    )
}
