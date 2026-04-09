package com.example.smartcalendar.data.ai

import android.util.Base64
import android.util.Log
import com.example.smartcalendar.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * AI service implementation using Google Gemini.
 */
class GeminiProvider : AIService {

    /**
     * Function executor for calendar queries. Set by AICalendarAssistant.
     */
    var functionExecutor: ((name: String, args: Map<String, String>) -> String)? = null

    companion object {
        private const val TAG = "GeminiProvider"
        private const val MODEL = "gemini-3-flash-preview"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
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

    /**
     * Shared helper: call Gemini, parse the JSON response, handle errors.
     */
    private suspend fun executeAndParse(
        prompt: String,
        attachmentMimeType: String? = null,
        attachmentBytes: ByteArray? = null
    ): ProcessingResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            return ProcessingResult.Error("Gemini API key not configured")
        }
        return generateContent(prompt, attachmentMimeType, attachmentBytes).fold(
            onSuccess = { parseGeminiResponse(it) },
            onFailure = { ProcessingResult.Error("Failed to process: ${it.message}", it as? Exception) }
        )
    }

    override suspend fun parseText(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?,
        conversationHistory: List<Pair<String, String>>?
    ): ProcessingResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            return ProcessingResult.Error("Gemini API key not configured")
        }
        val prompt = buildTextParsingPrompt(text, currentDate, timezone, calendarContext)
        return generateContent(prompt, conversationHistory = conversationHistory).fold(
            onSuccess = { parseGeminiResponse(it) },
            onFailure = { ProcessingResult.Error("Failed to process: ${it.message}", it as? Exception) }
        )
    }

    override fun streamParseText(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): Flow<StreamChunk> = flow {
        // No streaming — just call non-streaming and emit the result
        val result = parseText(text, currentDate, timezone, calendarContext)
        emit(StreamChunk.Complete(result))
    }

    override suspend fun parseImage(
        imageBytes: ByteArray,
        mimeType: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult = executeAndParse(buildImageParsingPrompt(currentDate, timezone, calendarContext), mimeType, imageBytes)

    override suspend fun parseDocument(
        documentBytes: ByteArray,
        mimeType: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): ProcessingResult = executeAndParse(buildDocumentParsingPrompt(currentDate, timezone, calendarContext), mimeType, documentBytes)

    override suspend fun refineEvents(
        events: List<ExtractedEvent>,
        instruction: String
    ): ProcessingResult = executeAndParse(buildRefinementPrompt(events, instruction))

    private fun buildTextParsingPrompt(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): String {
        return """
You are a calendar assistant. Extract calendar events from the following text.
If the user is asking to SEARCH, FIND, or LOOK UP events, use your Google Search tool to find real events from the internet with accurate titles, dates, times, and locations. Do NOT create a placeholder event with the search query as the title.
You have access to the user's calendar via function calls. Use search_events to find events by name, get_events_by_date to check a date range, or get_event_details for full info about a specific event. Always use these functions when the user references existing events.

Current date and time: $currentDate ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())}
User timezone: $timezone

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

If no events found, return: {"message": "Summary and any follow-up question", "events": []}
""".trimIndent()
    }

    private fun buildImageParsingPrompt(
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): String {
        return """
You are a calendar assistant. Extract calendar events from the attached image.
You have access to the user's calendar via function calls if needed.

Current date: $currentDate
User timezone: $timezone

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

If no events found, return: {"message": "Summary and any follow-up question", "events": []}
""".trimIndent()
    }

    private fun buildDocumentParsingPrompt(
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>?
    ): String {
        return """
You are a calendar assistant. Extract calendar events from the attached document.
You have access to the user's calendar via function calls if needed.

Current date: $currentDate
User timezone: $timezone

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

If no events found, return: {"message": "Summary and any follow-up question", "events": []}
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
- If the instruction is a CONFIRMATION (e.g., "Yes", "OK", "Sure", "Do it", "Confirm", "Go ahead"), return the current events unchanged. The user is approving the proposed changes.
- If the instruction is asking to MODIFY existing events (e.g., "change the time", "make it red", "move to Tuesday"), apply the changes to the relevant event(s) and return the modified list.
- If the instruction is asking to ADD a new event by description (e.g., "also add a meeting tomorrow"), create the new event and append it to the existing list.
- If the instruction is asking to SEARCH or FIND new events, return the existing events plus any new events found.

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
            // Response is plain text (not JSON) — treat as a message with no events
            Log.d(TAG, "Response is plain text, wrapping as message")
            ProcessingResult.Success(
                AIResponse(
                    events = emptyList(),
                    confidence = 0f,
                    message = responseText.trim(),
                    rawResponse = responseText
                )
            )
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

    private val calendarFunctions = listOf(
        GeminiFunctionDeclaration(
            name = "search_events",
            description = "Search the user's calendar for events by title keyword. Use this ONLY when the user mentions a specific event name or title. Pass just the event name/title as the query, NOT dates or descriptions.",
            parameters = GeminiFunctionParameters(
                type = "OBJECT",
                properties = mapOf("query" to GeminiPropertySchema("STRING", "Event title or name keyword to search for")),
                required = listOf("query")
            )
        ),
        GeminiFunctionDeclaration(
            name = "get_events_by_date",
            description = "Get ALL events on a specific date or date range from the user's calendar. Use this when the user mentions a date (e.g. 'April 16', 'tomorrow', 'next Monday'). This is the preferred function when the user references events by date.",
            parameters = GeminiFunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "start_date" to GeminiPropertySchema("STRING", "Start date in YYYY-MM-DD format"),
                    "end_date" to GeminiPropertySchema("STRING", "End date in YYYY-MM-DD format (same as start_date for a single day)")
                ),
                required = listOf("start_date", "end_date")
            )
        ),
        GeminiFunctionDeclaration(
            name = "get_event_details",
            description = "Get full details of a specific event by its ID",
            parameters = GeminiFunctionParameters(
                type = "OBJECT",
                properties = mapOf("event_id" to GeminiPropertySchema("STRING", "The event UID")),
                required = listOf("event_id")
            )
        ),
        GeminiFunctionDeclaration(
            name = "search_internet",
            description = "Search the internet for real-world events, schedules, or information. Use this when the user asks to find events that are NOT in their calendar (e.g., sports games, concerts, conferences, public events).",
            parameters = GeminiFunctionParameters(
                type = "OBJECT",
                properties = mapOf("query" to GeminiPropertySchema("STRING", "The search query")),
                required = listOf("query")
            )
        )
    )

    /**
     * Build the initial request contents (user prompt + optional attachment).
     */
    private fun buildInitialContents(
        prompt: String,
        attachmentMimeType: String? = null,
        attachmentBytes: ByteArray? = null
    ): List<GeminiRequestContent> {
        val parts = mutableListOf(GeminiPart(text = prompt))
        if (attachmentMimeType != null && attachmentBytes != null) {
            parts.add(GeminiPart(inlineData = GeminiInlineData(
                mimeType = attachmentMimeType,
                data = Base64.encodeToString(attachmentBytes, Base64.NO_WRAP)
            )))
        }
        return listOf(GeminiRequestContent(parts = parts))
    }

    private val tools = listOf(
        GeminiTool(function_declarations = calendarFunctions)
    )

    /**
     * Execute an internet search via a separate Gemini call with google_search tool.
     */
    private suspend fun executeInternetSearch(query: String): String {
        return try {
            val searchRequest = GeminiGenerateRequest(
                contents = listOf(GeminiRequestContent(parts = listOf(GeminiPart(text = query)))),
                tools = listOf(GeminiTool(google_search = GeminiGoogleSearch()))
            )
            val body = json.encodeToString(GeminiGenerateRequest.serializer(), searchRequest)
            val response = httpClient.post("$BASE_URL/$MODEL:generateContent") {
                url { parameters.append("key", BuildConfig.GEMINI_API_KEY) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                return """{"error": "Search failed: ${response.status}"}"""
            }
            val parsed = parseResponse(response.bodyAsText())
            val text = parsed.candidates.firstOrNull()?.content?.parts
                ?.firstOrNull { it.text != null }?.text?.trim()
            text ?: """{"error": "No search results found"}"""
        } catch (e: Exception) {
            Log.e(TAG, "Internet search failed", e)
            """{"error": "Search failed: ${e.message}"}"""
        }
    }

    /**
     * Parse a Gemini response. Returns the parsed response object.
     */
    private fun parseResponse(responseBody: String): GeminiGenerateResponse {
        return json.decodeFromString(GeminiGenerateResponse.serializer(), responseBody)
    }

    /**
     * Extract text from response parts. Returns null if no text.
     */
    private fun extractText(parts: List<GeminiPart>): String? {
        return parts.firstOrNull { it.text != null }?.text?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Extract a function call from response parts. Returns null if none.
     */
    private fun extractFunctionCall(parts: List<GeminiPart>): GeminiFunctionCall? {
        return parts.firstOrNull { it.functionCall != null }?.functionCall
    }

    /**
     * Call Gemini API with multi-turn function calling support.
     * If Gemini requests a function call, executes it locally and sends the result back.
     * Max 3 rounds to prevent infinite loops.
     */
    private suspend fun generateContent(
        prompt: String,
        attachmentMimeType: String? = null,
        attachmentBytes: ByteArray? = null,
        conversationHistory: List<Pair<String, String>>? = null
    ): Result<String> {
        return try {
            val contents = mutableListOf<GeminiRequestContent>()

            // Add conversation history as prior turns
            conversationHistory?.forEach { (role, text) ->
                val geminiRole = if (role == "user") "user" else "model"
                contents.add(GeminiRequestContent(parts = listOf(GeminiPart(text = text)), role = geminiRole))
            }

            // Add current prompt
            contents.addAll(buildInitialContents(prompt, attachmentMimeType, attachmentBytes))

            for (round in 0 until 3) {
                val noThinking = GeminiGenerationConfig(thinkingConfig = GeminiThinkingConfig(thinkingBudget = 0))
                val request = GeminiGenerateRequest(contents = contents, tools = tools, generationConfig = noThinking)
                val requestBody = json.encodeToString(GeminiGenerateRequest.serializer(), request)
                Log.d(TAG, "Request tools: ${requestBody.substringAfter("\"tools\"").take(400)}")

                val response = httpClient.post("$BASE_URL/$MODEL:generateContent") {
                    url { parameters.append("key", BuildConfig.GEMINI_API_KEY) }
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    Log.e(TAG, "Gemini error ${response.status}: ${errorBody.take(500)}")
                    return Result.failure(Exception("Gemini API error: ${response.status}"))
                }

                val responseBody = response.bodyAsText()
                val parsed = parseResponse(responseBody)
                val responseContent = parsed.candidates.firstOrNull()?.content
                val responseParts = responseContent?.parts ?: emptyList()

                val functionCall = extractFunctionCall(responseParts)
                if (functionCall != null) {
                    Log.d(TAG, "Function call: ${functionCall.name}(${functionCall.args})")
                    val result = if (functionCall.name == "search_internet") {
                        executeInternetSearch(functionCall.args["query"] ?: "")
                    } else {
                        functionExecutor?.invoke(functionCall.name, functionCall.args) ?: """{"error": "No executor"}"""
                    }
                    Log.d(TAG, "Function result: ${result.take(300)}")

                    // Preserve the FULL model response (includes thought_signature for thinking models)
                    contents.add(GeminiRequestContent(
                        parts = responseParts,
                        role = responseContent?.role ?: "model"
                    ))
                    contents.add(GeminiRequestContent(
                        parts = listOf(GeminiPart(functionResponse = GeminiFunctionResponse(
                            name = functionCall.name,
                            response = GeminiFunctionResponseContent(result = result)
                        ))),
                        role = "function"
                    ))
                } else {
                    val text = extractText(responseParts)
                    return Result.success(text ?: """{"events": []}""")
                }
            }

            Result.success("""{"message": "I needed more information but couldn't complete the request. Please try again.", "events": []}""")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Gemini API data classes ---

    @Serializable
    private data class GeminiGenerationConfig(
        val thinkingConfig: GeminiThinkingConfig? = null
    )

    @Serializable
    private data class GeminiThinkingConfig(
        val thinkingBudget: Int = 0
    )

    @Serializable
    private data class GeminiGenerateRequest(
        val contents: List<GeminiRequestContent>,
        val tools: List<GeminiTool>? = null,
        val generationConfig: GeminiGenerationConfig? = null
    )

    @Serializable
    private data class GeminiTool(
        val google_search: GeminiGoogleSearch? = null,
        val function_declarations: List<GeminiFunctionDeclaration>? = null
    )

    @Serializable
    private class GeminiGoogleSearch

    @Serializable
    private data class GeminiFunctionDeclaration(
        val name: String,
        val description: String,
        val parameters: GeminiFunctionParameters
    )

    @Serializable
    private data class GeminiFunctionParameters(
        val type: String,
        val properties: Map<String, GeminiPropertySchema>,
        val required: List<String> = emptyList()
    )

    @Serializable
    private data class GeminiPropertySchema(
        val type: String,
        val description: String
    )

    @Serializable
    private data class GeminiRequestContent(
        val parts: List<GeminiPart>,
        val role: String = "user"
    )

    @Serializable
    private data class GeminiPart(
        val text: String? = null,
        val inlineData: GeminiInlineData? = null,
        val functionCall: GeminiFunctionCall? = null,
        val functionResponse: GeminiFunctionResponse? = null,
        val thoughtSignature: String? = null,
        val thought: Boolean? = null
    )

    @Serializable
    private data class GeminiInlineData(
        val mimeType: String,
        val data: String
    )

    @Serializable
    private data class GeminiFunctionCall(
        val name: String,
        val args: Map<String, String> = emptyMap()
    )

    @Serializable
    private data class GeminiFunctionResponse(
        val name: String,
        val response: GeminiFunctionResponseContent
    )

    @Serializable
    private data class GeminiFunctionResponseContent(
        val result: String
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
