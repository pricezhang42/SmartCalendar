package com.example.smartcalendar.data.ai

import android.util.Base64
import android.util.Log
import com.example.smartcalendar.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
            "gemini-2.5-flash"
        )
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
                    callTimeout(60, TimeUnit.SECONDS)
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
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

    override fun streamParseText(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): Flow<StreamChunk> = flow {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            emit(StreamChunk.Complete(ProcessingResult.Error("Gemini API key not configured")))
            return@flow
        }

        val prompt = buildTextParsingPrompt(text, currentDate, timezone, calendarContext)

        try {
            var fullText = ""
            streamGenerateContent(prompt).collect { partialText ->
                fullText = partialText
                emit(StreamChunk.Partial(partialText))
            }

            if (fullText.isNotBlank()) {
                Log.d(TAG, "Streaming complete: $fullText")
                emit(StreamChunk.Complete(parseGeminiResponse(fullText)))
            } else {
                // Streaming returned empty/whitespace — fall back to non-streaming
                Log.d(TAG, "Streaming empty, falling back to non-streaming")
                emit(StreamChunk.Partial("Searching..."))
                val fallbackResult = generateContent(prompt)
                fallbackResult.fold(
                    onSuccess = { responseText ->
                        Log.d(TAG, "Fallback response: $responseText")
                        emit(StreamChunk.Complete(parseGeminiResponse(responseText)))
                    },
                    onFailure = { error ->
                        emit(StreamChunk.Complete(ProcessingResult.Error("Failed to process: ${error.message}", error as? Exception)))
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming error", e)
            emit(StreamChunk.Complete(ProcessingResult.Error("Failed to process: ${e.message}", e)))
        }
    }

    private fun streamGenerateContent(
        prompt: String,
        attachmentMimeType: String? = null,
        attachmentBytes: ByteArray? = null
    ): Flow<String> = flow {
        val parts = mutableListOf<GeminiPart>()
        parts.add(GeminiPart(text = prompt))

        if (attachmentMimeType != null && attachmentBytes != null) {
            val encoded = Base64.encodeToString(attachmentBytes, Base64.NO_WRAP)
            parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = attachmentMimeType, data = encoded)))
        }

        val request = GeminiGenerateRequest(
            contents = listOf(GeminiRequestContent(parts = parts)),
            tools = listOf(GeminiTool(google_search = GeminiGoogleSearch()))
        )
        val requestBody = json.encodeToString(GeminiGenerateRequest.serializer(), request)

        // Use primary model only for streaming — fallback handled by non-streaming path
        val modelName = MODEL_CANDIDATES.first()
        try {
            val accumulated = StringBuilder()

            httpClient.preparePost(
                "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent"
                ) {
                    url { parameters.append("key", BuildConfig.GEMINI_API_KEY); parameters.append("alt", "sse") }
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        Log.w(TAG, "Streaming $modelName failed: ${response.status}")
                        return@execute
                    }

                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        if (!line.startsWith("data: ")) continue
                        val jsonData = line.removePrefix("data: ").trim()
                        if (jsonData == "[DONE]" || jsonData.isEmpty()) continue

                        try {
                            Log.d(TAG, "SSE raw chunk: ${jsonData.take(500)}")
                            val parsed = json.decodeFromString(GeminiGenerateResponse.serializer(), jsonData)
                            val chunkText = parsed.candidates
                                .firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (!chunkText.isNullOrBlank()) {
                                accumulated.append(chunkText)
                                emit(accumulated.toString())
                            } else {
                                Log.d(TAG, "SSE chunk had no text, candidates count: ${parsed.candidates.size}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse SSE chunk: ${jsonData.take(500)}", e)
                        }
                    }
                }

            if (accumulated.isNotEmpty()) return@flow
            // Streaming returned nothing — the fallback in streamParseText handles this
        } catch (e: Exception) {
            Log.w(TAG, "Streaming $modelName failed: ${e.message}")
            // Don't throw — let streamParseText fallback to non-streaming
        }
    }

    override suspend fun parseImage(
        imageBytes: ByteArray,
        mimeType: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            return ProcessingResult.Error("Gemini API key not configured")
        }

        val prompt = buildImageParsingPrompt(currentDate, timezone, calendarContext)

        return generateContent(prompt, mimeType, imageBytes).fold(
            onSuccess = { responseText ->
                Log.d(TAG, "Gemini image response: $responseText")
                parseGeminiResponse(responseText)
            },
            onFailure = { error ->
                Log.e(TAG, "Error calling Gemini API for image", error)
                ProcessingResult.Error("Failed to process image: ${error.message}", error as? Exception)
            }
        )
    }

    override suspend fun parseDocument(
        documentBytes: ByteArray,
        mimeType: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            return ProcessingResult.Error("Gemini API key not configured")
        }

        val prompt = buildDocumentParsingPrompt(currentDate, timezone, calendarContext)

        return generateContent(prompt, mimeType, documentBytes).fold(
            onSuccess = { responseText ->
                Log.d(TAG, "Gemini document response: $responseText")
                parseGeminiResponse(responseText)
            },
            onFailure = { error ->
                Log.e(TAG, "Error calling Gemini API for document", error)
                ProcessingResult.Error("Failed to process document: ${error.message}", error as? Exception)
            }
        )
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
If the user is asking to SEARCH, FIND, or LOOK UP events (e.g., "search for conferences", "find rampup events", "what events are happening in..."), use your Google Search tool to find real events from the internet and return them with accurate titles, dates, times, and locations from the search results. Do NOT create a placeholder event with the search query as the title.

Current date: $currentDate
User timezone: $timezone

Calendar context (use for updates/deletes when relevant):
${contextJson ?: "[]"}

Text to parse:
"$text"

Instructions:
1. Extract ALL events mentioned in the text, or search the internet for events if the user asks
2. Parse relative dates (tomorrow, next Monday, etc.) relative to the current date
3. If no specific time is given, leave startTime and endTime as null
4. For all-day events (like birthdays, holidays), set isAllDay to true
5. Parse recurrence patterns if mentioned (e.g., "every Monday", "weekly")
6. Estimate confidence (0.0-1.0) based on how clearly the event details are specified
7. If the user is modifying or deleting existing events, set action to UPDATE or DELETE
8. For UPDATE/DELETE, set targetEventId to the matching id from calendar context
9. For UPDATE, change date/time fields instead of adding phrases like "postponed" to description
10. Parse color changes when mentioned (e.g., red/blue/lavender) and set color as color name or #RRGGBB
11. If no color is mentioned, leave color as null
12. For recurring events, set scope to THIS_INSTANCE, THIS_AND_FOLLOWING, or ALL
13. For scope THIS_INSTANCE or THIS_AND_FOLLOWING, set instanceDate (YYYY-MM-DD)
14. If you can infer an RRULE, set recurrenceRule (e.g., "FREQ=WEEKLY;BYDAY=MO;UNTIL=20260330T235959Z")
15. If there are exceptions, list them in exceptionDates (YYYY-MM-DD) and do not put them in description
16. If a single occurrence changes time/date, create TWO events:
    - The recurring event with exceptionDates including the original date
    - A separate single (non-recurring) event at the new date/time
17. Do NOT create a duplicated recurring instance; use exceptionDates + a single event for single-occurrence changes

Output ONLY a valid JSON object with this exact structure (no markdown, no code blocks):
{
  "message": "Short natural-language summary and any follow-up question",
  "events": [
    {
      "title": "Event title",
      "description": "Optional description or null",
      "location": "Optional location or null",
      "color": "Optional color name or #RRGGBB or null",
      "date": "YYYY-MM-DD",
      "startTime": "HH:MM or null",
      "endTime": "HH:MM or null",
      "isAllDay": true,
      "recurrence": "Natural language recurrence or null",
      "recurrenceRule": "RRULE or null",
      "exceptionDates": ["YYYY-MM-DD", "YYYY-MM-DD"],
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

    private fun buildImageParsingPrompt(
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
You are a calendar assistant. Extract calendar events from the attached image.

Current date: $currentDate
User timezone: $timezone

Calendar context (use for updates/deletes when relevant):
${contextJson ?: "[]"}

Instructions:
1. Read dates, times, titles, locations from the image
2. Parse relative dates based on the current date
3. If no specific time is given, leave startTime and endTime as null
4. For all-day events (like birthdays, holidays), set isAllDay to true
5. Parse recurrence patterns if mentioned
6. Estimate confidence (0.0-1.0)
7. If the user is modifying or deleting existing events, set action to UPDATE or DELETE
8. For UPDATE/DELETE, set targetEventId to the matching id from calendar context
9. Parse color changes when mentioned and set color as color name or #RRGGBB
10. If no color is mentioned, leave color as null
11. For recurring events, set scope to THIS_INSTANCE, THIS_AND_FOLLOWING, or ALL
12. For scope THIS_INSTANCE or THIS_AND_FOLLOWING, set instanceDate (YYYY-MM-DD)
13. If you can infer an RRULE, set recurrenceRule
14. If there are exceptions, list them in exceptionDates
15. If a single occurrence changes time/date, create TWO events:
    - The recurring event with exceptionDates including the original date
    - A separate single (non-recurring) event at the new date/time
16. Do NOT create a duplicated recurring instance; use exceptionDates + a single event for single-occurrence changes

Output ONLY a valid JSON object with this exact structure (no markdown, no code blocks):
{
  "message": "Short natural-language summary and any follow-up question",
  "events": [
    {
      "title": "Event title",
      "description": "Optional description or null",
      "location": "Optional location or null",
      "color": "Optional color name or #RRGGBB or null",
      "date": "YYYY-MM-DD",
      "startTime": "HH:MM or null",
      "endTime": "HH:MM or null",
      "isAllDay": true,
      "recurrence": "Natural language recurrence or null",
      "recurrenceRule": "RRULE or null",
      "exceptionDates": ["YYYY-MM-DD", "YYYY-MM-DD"],
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

    private fun buildDocumentParsingPrompt(
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
You are a calendar assistant. Extract calendar events from the attached document.

Current date: $currentDate
User timezone: $timezone

Calendar context (use for updates/deletes when relevant):
${contextJson ?: "[]"}

Instructions:
1. Extract ALL events mentioned in the document
2. Parse relative dates (tomorrow, next Monday, etc.) relative to the current date
3. If no specific time is given, leave startTime and endTime as null
4. For all-day events, set isAllDay to true
5. Parse recurrence patterns if mentioned
6. Estimate confidence (0.0-1.0)
7. If the document modifies or deletes existing events, set action to UPDATE or DELETE
8. For UPDATE/DELETE, set targetEventId to the matching id from calendar context
9. Parse color changes when mentioned and set color as color name or #RRGGBB
10. If no color is mentioned, leave color as null
11. For recurring events, set scope to THIS_INSTANCE, THIS_AND_FOLLOWING, or ALL
12. For scope THIS_INSTANCE or THIS_AND_FOLLOWING, set instanceDate (YYYY-MM-DD)
13. If you can infer an RRULE, set recurrenceRule
14. If there are exceptions, list them in exceptionDates
15. If a single occurrence changes time/date, create TWO events:
    - The recurring event with exceptionDates including the original date
    - A separate single (non-recurring) event at the new date/time
16. Do NOT create a duplicated recurring instance; use exceptionDates + a single event for single-occurrence changes

Output ONLY a valid JSON object with this exact structure (no markdown, no code blocks):
{
  "message": "Short natural-language summary and any follow-up question",
  "events": [
    {
      "title": "Event title",
      "description": "Optional description or null",
      "location": "Optional location or null",
      "color": "Optional color name or #RRGGBB or null",
      "date": "YYYY-MM-DD",
      "startTime": "HH:MM or null",
      "endTime": "HH:MM or null",
      "isAllDay": true,
      "recurrence": "Natural language recurrence or null",
      "recurrenceRule": "RRULE or null",
      "exceptionDates": ["YYYY-MM-DD", "YYYY-MM-DD"],
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

Determine the user's intent:
- If the instruction is asking to SEARCH, FIND, or LOOK UP new events (e.g., "search for conferences", "find events in my area", "look up holidays"), use your Google Search tool to find real events from the internet. Return the existing events PLUS any new events you found, with accurate dates, times, locations, and titles from the search results.
- If the instruction is asking to MODIFY existing events (e.g., "change the time", "make it red", "move to Tuesday"), apply the changes to the relevant event(s) and return the modified list.
- If the instruction is asking to ADD a new event by description (e.g., "also add a meeting tomorrow"), create the new event and append it to the existing list.

For modifications:
If a single occurrence changes time/date, remove that date from the recurring series via exceptionDates and add a new single event at the new time.
Apply color changes when the user asks (e.g., "make it red").

Output ONLY a valid JSON object (no markdown, no code blocks):
{
  "message": "Short natural-language summary and any follow-up question",
  "events": [
    {
      "title": "Event title",
      "description": "Optional description or null",
      "location": "Optional location or null",
      "color": "Optional color name or #RRGGBB or null",
      "date": "YYYY-MM-DD",
      "startTime": "HH:MM or null",
      "endTime": "HH:MM or null",
      "isAllDay": false,
      "recurrence": "Natural language recurrence or null",
      "recurrenceRule": "RRULE or null",
      "exceptionDates": ["YYYY-MM-DD", "YYYY-MM-DD"],
      "confidence": 0.95
    }
  ]
}
""".trimIndent()
    }

    private fun parseGeminiResponse(responseText: String): ProcessingResult {
        Log.d(TAG, "parseGeminiResponse input (${responseText.length} chars): ${responseText.take(800)}")
        return try {
            // Clean response - remove markdown code blocks if present
            val cleanedResponse = responseText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val jsonPayload = extractJsonPayload(cleanedResponse)

            val parsed = if (jsonPayload.startsWith("[")) {
                GeminiEventResponse(
                    events = json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(ExtractedEvent.serializer()),
                        jsonPayload
                    ),
                    message = null
                )
            } else {
                json.decodeFromString<GeminiEventResponse>(jsonPayload)
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
                    message = parsed.message,
                    rawResponse = responseText
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: $responseText", e)
            ProcessingResult.Error("Failed to parse response: ${e.message}", e)
        }
    }

    private fun extractJsonPayload(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }

        val firstBrace = trimmed.indexOf('{')
        val firstBracket = trimmed.indexOf('[')
        val start = listOf(firstBrace, firstBracket)
            .filter { it >= 0 }
            .minOrNull() ?: return trimmed

        val endBrace = trimmed.lastIndexOf('}')
        val endBracket = trimmed.lastIndexOf(']')
        val end = listOf(endBrace, endBracket)
            .filter { it >= 0 }
            .maxOrNull() ?: return trimmed

        return if (end > start) trimmed.substring(start, end + 1) else trimmed
    }

    private suspend fun generateContent(
        prompt: String,
        attachmentMimeType: String? = null,
        attachmentBytes: ByteArray? = null
    ): Result<String> {
        return try {
            val parts = mutableListOf<GeminiPart>()
            parts.add(GeminiPart(text = prompt))
            if (attachmentMimeType != null && attachmentBytes != null) {
                val encoded = Base64.encodeToString(attachmentBytes, Base64.NO_WRAP)
                parts.add(
                    GeminiPart(
                        inlineData = GeminiInlineData(
                            mimeType = attachmentMimeType,
                            data = encoded
                        )
                    )
                )
            }
            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiRequestContent(
                        parts = parts
                    )
                ),
                tools = listOf(GeminiTool(google_search = GeminiGoogleSearch()))
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

                if (!text.isNullOrBlank()) {
                    return Result.success(text)
                }

                // Gemini searched but returned no text content — return a helpful message
                Log.w(TAG, "Model $modelName returned empty text. Full response: ${responseBody.take(500)}")
                return Result.success("""{"message": "I searched but couldn't find specific details for your request. Could you try rephrasing or being more specific?", "events": []}""")
            }

            Result.failure(lastError ?: Exception("Failed to call Gemini API"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class GeminiGenerateRequest(
        val contents: List<GeminiRequestContent>,
        val tools: List<GeminiTool>? = null
    )

    @Serializable
    private data class GeminiTool(
        val google_search: GeminiGoogleSearch? = null
    )

    @Serializable
    private data class GeminiGoogleSearch(
        val placeholder: String? = null
    )

    @Serializable
    private data class GeminiRequestContent(
        val parts: List<GeminiPart>,
        val role: String = "user"
    )

    @Serializable
    private data class GeminiPart(
        val text: String? = null,
        val inlineData: GeminiInlineData? = null
    )

    @Serializable
    private data class GeminiInlineData(
        val mimeType: String,
        val data: String
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
