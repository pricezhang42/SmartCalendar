package com.example.smartcalendar.data.ai

import android.util.Log
import com.example.smartcalendar.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * AI service implementation using Google Gemini.
 */
class GeminiProvider : AIService {

    companion object {
        private const val TAG = "GeminiProvider"
        private val MODEL_CANDIDATES = listOf(
            "gemini-2.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-1.5-flash-001",
            "gemini-1.0-pro"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    callTimeout(30, TimeUnit.SECONDS)
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }

    override suspend fun parseText(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            return ProcessingResult.Error("Gemini API key not configured")
        }

        val prompt = buildTextParsingPrompt(text, currentDate, timezone, calendarContext)

        return generateContent(prompt).fold(
            onSuccess = { responseText ->
                Log.d(TAG, "Gemini response: $responseText")
                parseGeminiResponse(responseText)
            },
            onFailure = { error ->
                Log.e(TAG, "Error calling Gemini API", error)
                ProcessingResult.Error("Failed to process: ${error.message}", error as? Exception)
            }
        )
    }

    override suspend fun parseImage(
        imageBytes: ByteArray,
        mimeType: String
    ): ProcessingResult {
        // Image parsing will be implemented in Phase 6B
        return ProcessingResult.Error("Image parsing not yet implemented")
    }

    override suspend fun refineEvents(
        events: List<ExtractedEvent>,
        instruction: String
    ): ProcessingResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            return ProcessingResult.Error("Gemini API key not configured")
        }

        val prompt = buildRefinementPrompt(events, instruction)

        return generateContent(prompt).fold(
            onSuccess = { responseText ->
                Log.d(TAG, "Gemini refinement response: $responseText")
                parseGeminiResponse(responseText)
            },
            onFailure = { error ->
                Log.e(TAG, "Error refining events", error)
                ProcessingResult.Error("Failed to refine: ${error.message}", error as? Exception)
            }
        )
    }

    private fun buildTextParsingPrompt(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): String {
        val contextJson = calendarContext?.let {
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(CalendarContextEvent.serializer()),
                it
            )
        }
        return """
You are a calendar assistant. Extract calendar events from the following text.

Current date: $currentDate
User timezone: $timezone

Calendar context (use for updates/deletes when relevant):
${contextJson ?: "[]"}

Text to parse:
"$text"

Instructions:
1. Extract ALL events mentioned in the text
2. Parse relative dates (tomorrow, next Monday, etc.) relative to the current date
3. If no specific time is given, leave startTime and endTime as null
4. For all-day events (like birthdays, holidays), set isAllDay to true
5. Parse recurrence patterns if mentioned (e.g., "every Monday", "weekly")
6. Estimate confidence (0.0-1.0) based on how clearly the event details are specified
7. If the user is modifying or deleting existing events, set action to UPDATE or DELETE
8. For UPDATE/DELETE, set targetEventId to the matching id from calendar context
9. For UPDATE, change date/time fields instead of adding phrases like "postponed" to description
10. For recurring events, set scope to THIS_INSTANCE, THIS_AND_FOLLOWING, or ALL
11. For scope THIS_INSTANCE or THIS_AND_FOLLOWING, set instanceDate (YYYY-MM-DD)

Output ONLY a valid JSON object with this exact structure (no markdown, no code blocks):
{
  "events": [
    {
      "title": "Event title",
      "description": "Optional description or null",
      "location": "Optional location or null",
      "date": "YYYY-MM-DD",
      "startTime": "HH:MM or null",
      "endTime": "HH:MM or null",
      "isAllDay": true,
      "recurrence": "Natural language recurrence or null",
      "confidence": 0.95,
      "action": "CREATE|UPDATE|DELETE",
      "targetEventId": "required when UPDATE or DELETE",
      "scope": "THIS_INSTANCE|THIS_AND_FOLLOWING|ALL",
      "instanceDate": "required for recurring updates/deletes on a specific date"
    }
  ]
}

If no events found, return: {"events": []}
""".trimIndent()
    }

    private fun buildRefinementPrompt(events: List<ExtractedEvent>, instruction: String): String {
        val eventsJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExtractedEvent.serializer()),
            events
        )

        return """
You are a calendar assistant. Modify the following events based on the user's instruction.

Current events:
$eventsJson

User instruction: "$instruction"

Apply the instruction to the relevant event(s) and return the modified events.

Output ONLY a valid JSON object (no markdown, no code blocks):
{
  "events": [
    {
      "title": "Event title",
      "description": "Optional description or null",
      "location": "Optional location or null",
      "date": "YYYY-MM-DD",
      "startTime": "HH:MM or null",
      "endTime": "HH:MM or null",
      "isAllDay": false,
      "recurrence": "Natural language recurrence or null",
      "confidence": 0.95
    }
  ]
}
""".trimIndent()
    }

    private fun parseGeminiResponse(responseText: String): ProcessingResult {
        return try {
            // Clean response - remove markdown code blocks if present
            val cleanedResponse = responseText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val parsed = if (cleanedResponse.startsWith("[")) {
                GeminiEventResponse(
                    events = json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(ExtractedEvent.serializer()),
                        cleanedResponse
                    )
                )
            } else {
                json.decodeFromString<GeminiEventResponse>(cleanedResponse)
            }

            val avgConfidence = if (parsed.events.isNotEmpty()) {
                parsed.events.map { it.confidence }.average().toFloat()
            } else {
                0.0f
            }

            ProcessingResult.Success(
                AIResponse(
                    events = parsed.events,
                    confidence = avgConfidence,
                    rawResponse = responseText
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: $responseText", e)
            ProcessingResult.Error("Failed to parse response: ${e.message}", e)
        }
    }

    private suspend fun generateContent(prompt: String): Result<String> {
        return try {
            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiRequestContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )
            var lastError: Exception? = null
            for (modelName in MODEL_CANDIDATES) {
                val response = httpClient.post(
                    "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
                ) {
                    url {
                        parameters.append("key", BuildConfig.GEMINI_API_KEY)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(GeminiGenerateRequest.serializer(), request))
                }
                val responseBody = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    lastError = Exception("Gemini API error: ${response.status} $responseBody")
                    continue
                }

                val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), responseBody)
                val text = parsed.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?.trim()

                if (!text.isNullOrEmpty()) {
                    return Result.success(text)
                }
                lastError = Exception("Empty response from Gemini")
            }

            Result.failure(lastError ?: Exception("Failed to call Gemini API"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class GeminiGenerateRequest(
        val contents: List<GeminiRequestContent>
    )

    @Serializable
    private data class GeminiRequestContent(
        val parts: List<GeminiPart>,
        val role: String = "user"
    )

    @Serializable
    private data class GeminiPart(
        val text: String
    )

    @Serializable
    private data class GeminiGenerateResponse(
        val candidates: List<GeminiCandidate> = emptyList()
    )

    @Serializable
    private data class GeminiCandidate(
        val content: GeminiResponseContent? = null
    )

    @Serializable
    private data class GeminiResponseContent(
        val parts: List<GeminiPart> = emptyList(),
        val role: String? = null
    )
}
