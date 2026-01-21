package com.example.smartcalendar.data.ai

import android.util.Log
import com.example.smartcalendar.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.serialization.json.Json

/**
 * AI service implementation using Google Gemini.
 */
class GeminiProvider : AIService {

    companion object {
        private const val TAG = "GeminiProvider"
        private const val MODEL_NAME = "gemini-1.5-flash"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.2f
                topK = 32
                topP = 0.95f
                maxOutputTokens = 2048
            }
        )
    }

    override suspend fun parseText(
        text: String,
        currentDate: String,
        timezone: String
    ): ProcessingResult {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            return ProcessingResult.Error("Gemini API key not configured")
        }

        val prompt = buildTextParsingPrompt(text, currentDate, timezone)

        return try {
            val response = generativeModel.generateContent(
                content {
                    text(prompt)
                }
            )

            val responseText = response.text ?: return ProcessingResult.Error("Empty response from Gemini")
            Log.d(TAG, "Gemini response: $responseText")

            parseGeminiResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            ProcessingResult.Error("Failed to process: ${e.message}", e)
        }
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

        return try {
            val response = generativeModel.generateContent(
                content {
                    text(prompt)
                }
            )

            val responseText = response.text ?: return ProcessingResult.Error("Empty response from Gemini")
            Log.d(TAG, "Gemini refinement response: $responseText")

            parseGeminiResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error refining events", e)
            ProcessingResult.Error("Failed to refine: ${e.message}", e)
        }
    }

    private fun buildTextParsingPrompt(text: String, currentDate: String, timezone: String): String {
        return """
You are a calendar assistant. Extract calendar events from the following text.

Current date: $currentDate
User timezone: $timezone

Text to parse:
"$text"

Instructions:
1. Extract ALL events mentioned in the text
2. Parse relative dates (tomorrow, next Monday, etc.) relative to the current date
3. If no specific time is given, leave startTime and endTime as null
4. For all-day events (like birthdays, holidays), set isAllDay to true
5. Parse recurrence patterns if mentioned (e.g., "every Monday", "weekly")
6. Estimate confidence (0.0-1.0) based on how clearly the event details are specified

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
      "isAllDay": false,
      "recurrence": "Natural language recurrence or null",
      "confidence": 0.95
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

            val parsed = json.decodeFromString<GeminiEventResponse>(cleanedResponse)

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
}
