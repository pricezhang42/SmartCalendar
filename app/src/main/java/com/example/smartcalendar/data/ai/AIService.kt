package com.example.smartcalendar.data.ai

/**
 * Interface for AI services that extract calendar events from input.
 */
interface AIService {
    /**
     * Parse text input to extract calendar events.
     * @param text The user's text input (natural language)
     * @param currentDate Current date string in YYYY-MM-DD format for relative date parsing
     * @param timezone User's timezone (e.g., "America/New_York")
     * @param calendarContext Optional list of calendar events for updates/deletes
     * @return ProcessingResult with extracted events or error
     */
    suspend fun parseText(
        text: String,
        currentDate: String,
        timezone: String,
        calendarContext: List<CalendarContextEvent>? = null
    ): ProcessingResult

    /**
     * Parse image to extract calendar events.
     * @param imageBytes The image data
     * @param mimeType Image MIME type (e.g., "image/jpeg")
     * @return ProcessingResult with extracted events or error
     */
    suspend fun parseImage(
        imageBytes: ByteArray,
        mimeType: String
    ): ProcessingResult

    /**
     * Refine previously extracted events based on user instruction.
     * @param events Current events to refine
     * @param instruction Natural language instruction (e.g., "make it 30 minutes earlier")
     * @return ProcessingResult with refined events or error
     */
    suspend fun refineEvents(
        events: List<ExtractedEvent>,
        instruction: String
    ): ProcessingResult
}
