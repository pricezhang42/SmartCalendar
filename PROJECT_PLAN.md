# SmartScheduler - Mobile Calendar App with AI Assistant

## Project Overview

**App Name**: SmartScheduler  
**AI Assistant Name**: Cali (Calendar Intelligence)

SmartScheduler is an intelligent mobile calendar application that combines traditional calendar management with advanced AI capabilities. The app allows users to manage their schedules through natural language, visual inputs (photos), documents, and voice commands, while maintaining full manual control over calendar entries.

---

## Core Features

### 1. Traditional Calendar Management
- Create, view, edit, and delete calendar events manually
- Multiple calendar views (Day, Week, Month, Agenda)
- Recurring events support
- Event categories/colors
- Reminders and notifications
- Event search and filtering

### 2. AI-Powered Event Creation
- **Multi-Modal Input Processing**:
  - Text prompts (emails, messages, natural language)
  - Image inputs (screenshot of class schedules, timetables, event flyers)
  - Document parsing (PDF, Word, Excel files)
  - Voice/speech input
  - Combined inputs (multiple photos + text + voice together)

- **Intelligent Event Extraction**:
  - Parse dates and times from unstructured text
  - Extract event titles, locations, attendees, descriptions
  - Identify recurring patterns
  - Recognize event categories automatically

- **Smart Suggestion System**:
  - Generate pending changes with confidence scores
  - Suggest event conflicts and resolutions
  - Recommend optimal scheduling times
  - Propose event categories and tags

### 3. User Confirmation & Adjustment Flow
- Preview screen showing pending calendar changes
- Ability to edit AI-generated suggestions directly
- Iterative refinement through follow-up prompts
- Batch approval for multiple events
- Undo/redo functionality

### 4. Seamless Integration
- Sync with device native calendar
- Export/import capabilities
- Cloud backup support

---

## Technical Architecture

### Platform & Framework
- **Platform**: Android Studio / Kotlin (native Android development)

### Backend Services
- **Cloud Provider**: AWS / Google Cloud / Firebase
- **Database**: PostgreSQL
  - User data and calendar events stored in PostgreSQL with optimized indexing
  - File storage: S3 / Cloud Storage for images/documents
- **API**: RESTful API

### AI/ML Services
- **Image Processing**: OpenAI GPT-4 Vision API for image understanding and calendar information extraction
  - Direct image-to-text processing without traditional OCR to avoid information loss
  - GPT Vision handles both text extraction and contextual understanding from images
- **Natural Language Processing**: 
  - OpenAI GPT-4 / Claude API for text understanding
  - Custom fine-tuned model for calendar-specific parsing
- **Speech-to-Text**: Google Speech-to-Text / AWS Transcribe / Whisper
- **Document Processing**: 
  - PDF parsing libraries
  - Office document parsers (Apache POI, python-docx)

### Key Libraries & Tools
- Date/time handling: java.time / Joda-Time / ThreeTenABP
- Image processing: Android Image Picker / Glide / Coil
- Document handling: Android Storage Access Framework / Apache POI
- Voice input: Android Speech Recognition API / Google ML Kit Speech-to-Text
- Calendar UI: Material Design Components / Custom Calendar Views
- State management: ViewModel / LiveData / StateFlow / Compose State
- UI Framework: Jetpack Compose / XML Layouts
- Networking: Retrofit / OkHttp / Ktor
- Dependency Injection: Hilt / Koin

---

## Data Models

### User
```
- id: UUID
- email: string
- name: string
- preferences: object
- createdAt: timestamp
```

### Calendar Event
```
- id: UUID
- userId: UUID
- title: string
- description: string
- startDateTime: datetime
- endDateTime: datetime
- location: string
- attendees: array[email]
- category: enum
- color: hex
- isRecurring: boolean
- recurrenceRule: string (RRULE format)
- reminder: object
- source: enum (manual, ai_text, ai_image, ai_document, ai_voice, ai_combined)
- aiConfidence: float
- createdAt: timestamp
- updatedAt: timestamp
```

### AI Processing Request
```
- id: UUID
- userId: UUID
- inputType: enum (text, image, document, voice, combined)
- rawInput: object (text content, image URLs, document URLs, audio URL)
- status: enum (pending, processing, completed, failed)
- extractedEvents: array[Event]
- processingMetadata: object
- createdAt: timestamp
```

### Pending Changes (Session)
```
- sessionId: UUID
- userId: UUID
- proposedEvents: array[Event]
- sourceRequest: UUID (AI Processing Request ID)
- userEdits: array[EditAction]
- status: enum (pending_review, approved, rejected, partially_approved)
```

---

## User Flows

### Flow 1: AI-Assisted Event Creation (Multi-Modal Input)
1. User opens SmartScheduler app
2. Taps "Add with AI" or "Cali" button
3. Selects input method(s):
   - Upload photo(s) of schedule
   - Paste/type text (email, message)
   - Upload document file
   - Record voice note
   - Combine multiple inputs
4. User taps "Process" button
5. Loading screen with progress indicator
6. AI processes inputs:
   - Extracts calendar information
   - Generates event suggestions
   - Calculates confidence scores
7. Preview screen displays:
   - List of proposed events
   - Highlighted dates/times
   - Conflict warnings if any
   - Confidence indicators
8. User can:
   - Approve all events
   - Approve selected events
   - Edit individual events before approval
   - Request refinement ("make this meeting 30 minutes earlier")
   - Reject all
9. Approved events are saved to calendar
10. Confirmation screen with summary

### Flow 2: Manual Event Creation
1. User opens SmartScheduler app
2. Taps "+" button or long-presses on calendar date
3. Manual event creation form opens
4. User fills in:
   - Title
   - Date & time
   - Duration
   - Location
   - Description
   - Category/color
   - Reminders
   - Recurrence (optional)
5. User saves event
6. Event appears on calendar

### Flow 3: Event Modification/Deletion
1. User views calendar
2. Taps on existing event
3. Event detail screen opens
4. Options:
   - Edit event
   - Delete event
   - Duplicate event
5. Changes saved to calendar

### Flow 4: Iterative AI Refinement
1. User reviews AI-generated events
2. User taps "Adjust" on an event
3. Text input appears: "How would you like to adjust?"
4. User types natural language instruction:
   - "Make it 2 hours long instead"
   - "Move to next Tuesday"
   - "Add John as attendee"
5. AI processes adjustment request
6. Updated event preview shown
7. User approves or requests further changes

---

## AI Processing Pipeline

### Stage 1: Input Preprocessing
- **Text Input**: Direct to NLP pipeline
- **Image Input**: GPT-4 Vision API → Direct calendar information extraction (no OCR step to avoid information loss)
- **Document Input**: Parse format → Extract text → Structure data
- **Voice Input**: Speech-to-Text → Text output
- **Combined Input**: Merge all extracted information with source metadata

### Stage 2: Information Extraction
- **Entity Recognition**: Dates, times, locations, people, event names
- **Intent Classification**: Create event, modify event, delete event
- **Temporal Resolution**: 
  - Relative dates ("next Monday", "tomorrow")
  - Absolute dates ("March 15, 2024")
  - Time zones
- **Event Parsing**:
  - Extract title from context
  - Determine duration (explicit or infer from context)
  - Identify recurring patterns
  - Extract location and attendees

### Stage 3: Calendar Integration
- **Conflict Detection**: Check against existing events
- **Time Slot Optimization**: Suggest best available times
- **Category Assignment**: Auto-tag based on keywords/context
- **Confidence Scoring**: Rate each extracted field

### Stage 4: Suggestion Generation
- Format as calendar events
- Generate user-friendly preview
- Prepare edit-ready templates
- Create batch operations (multiple events from one input)

---

## UI/UX Design Considerations

### Design Principles
- **Clean & Minimal**: Calendar-first design, AI features easily accessible but not intrusive
- **Visual Feedback**: Clear indication of AI processing status
- **Trust & Transparency**: Show confidence scores, explain AI reasoning
- **Flexibility**: Easy toggle between manual and AI-assisted modes

### Key Screens
1. **Main Calendar View**: Traditional calendar with floating action buttons
2. **AI Input Screen**: Multi-select interface for input types
3. **Processing Screen**: Animated loading with progress feedback
4. **Preview & Approval Screen**: 
   - Card-based event previews
   - Inline editing capability
   - Batch selection checkboxes
5. **Event Detail Screen**: Full event information with edit/delete
6. **Settings Screen**: AI preferences, calendar sync, notifications

### Accessibility
- Voice commands support
- Screen reader compatibility
- High contrast mode
- Text size adjustments
- Haptic feedback for actions

---

## Security & Privacy

### Data Protection
- End-to-end encryption for calendar data
- Secure file upload handling
- AI processing logs anonymization
- User data retention policies
- GDPR/CCPA compliance

### Permissions Required
- Calendar access (read/write)
- Camera (for photo input)
- Storage (for document upload)
- Microphone (for voice input)
- Internet (for AI processing)

---

## Implementation Phases

### Phase 1: MVP - Core Calendar (Weeks 1-4)
- Basic calendar UI (day/week/month views)
- Manual event CRUD operations
- Calendar sync with device
- User authentication

### Phase 2: AI Integration - Text Input (Weeks 5-8)
- Text input interface
- NLP processing pipeline
- Basic event extraction from text
- Preview and approval flow

### Phase 3: Multi-Modal Inputs (Weeks 9-12)
- Image upload and GPT-4 Vision API integration
- Document parsing support
- Voice input integration
- Combined input handling

### Phase 4: Advanced AI Features (Weeks 13-16)
- Confidence scoring
- Conflict detection and resolution
- Iterative refinement capability
- Smart category suggestions
- Recurring pattern detection

### Phase 5: Polish & Optimization (Weeks 17-20)
- Performance optimization
- UI/UX refinements
- Error handling and edge cases
- User testing and feedback incorporation
- App store preparation

### Phase 6: Launch & Post-Launch (Week 21+)
- Beta testing
- App store submission
- Marketing materials
- Analytics integration
- User feedback collection system
- Continuous improvement based on usage data

---

## Success Metrics

### Technical Metrics
- AI processing accuracy rate (>90% for simple inputs)
- Average processing time (<5 seconds per request)
- App crash rate (<0.1%)
- API response time (<2 seconds)

### User Engagement Metrics
- Daily active users
- Events created per user per week
- AI vs manual event creation ratio
- User retention rate (30-day, 90-day)
- Average session duration

### Business Metrics
- App store ratings (>4.5 stars)
- User acquisition cost
- Feature adoption rates
- Premium feature conversion (if applicable)

---

## Risk Assessment & Mitigation

### Technical Risks
- **AI Processing Failures**: Implement fallback to manual entry, clear error messages
- **GPT Vision API Accuracy**: User confirmation required for image-extracted events, allow manual corrections
- **Android Version Compatibility**: Early testing across different Android versions and device sizes
- **Performance with Large Calendars**: Pagination, lazy loading, indexing

### Business Risks
- **AI API Costs**: Implement caching, rate limiting, cost monitoring
- **User Trust in AI**: Transparency, easy manual override, confidence indicators
- **Competition**: Focus on unique multi-modal input combination
- **Data Privacy Concerns**: Clear privacy policy, opt-in for advanced features

---

## Future Enhancements (Post-Launch)

- Smart meeting scheduling across multiple calendars
- Integration with email clients for automatic event extraction
- Team collaboration features
- Integration with third-party services (Zoom, Google Meet, etc.)
- Offline mode with sync
- Wearable device support (Wear OS)
- Natural language calendar queries ("Show me all meetings next week")
- Predictive scheduling suggestions based on user patterns
- Integration with travel apps for automatic flight/hotel event creation

---

## Team Requirements

### Core Team
- **Mobile Developer** (Android/Kotlin specialist)
- **Backend Developer** (RESTful API, PostgreSQL, cloud infrastructure)
- **AI/ML Engineer** (NLP, GPT Vision API integration, model fine-tuning)
- **UI/UX Designer** (Mobile design expertise)
- **QA Engineer** (Mobile testing, automation)
- **Product Manager** (Feature prioritization, roadmap)

### External Resources
- Cloud infrastructure services
- AI/ML API providers
- App store account (Google Play)

---

## Budget Considerations

### Development Costs
- Development team salaries
- Cloud infrastructure (scales with usage)
- AI API costs (per-request pricing)
- Third-party service subscriptions
- Design tools and licenses

### Operational Costs
- Server hosting and maintenance
- AI API usage fees
- Storage costs (images, documents)
- App store fees
- Marketing and user acquisition

---

## Conclusion

SmartScheduler aims to revolutionize calendar management by seamlessly integrating AI capabilities with traditional calendar functionality. The multi-modal input approach sets it apart from existing solutions, making calendar entry more intuitive and less time-consuming for users.

The phased implementation approach ensures a solid foundation before adding advanced features, allowing for iterative testing and user feedback incorporation.

**App Name**: SmartScheduler  
**AI Assistant Name**: Cali (Calendar Intelligence)

---

## AI Integration Technical Plan

### Current Implementation Status

The app has completed Phases 1-5 of core development:
- ✅ Room Database with CalendarDao, EventDao
- ✅ Supabase Auth (email/password, session management)
- ✅ Supabase PostgreSQL sync with RLS
- ✅ Offline support with pending queue
- ✅ Network monitoring and auto-sync
- ✅ Real-time sync infrastructure
- ✅ Comprehensive test suite

### Data Available for AI

**Event Data (ICalEvent model):**
- `summary` - Event title
- `description` - Event details
- `location` - Event location
- `dtStart/dtEnd` - Timestamps (milliseconds)
- `duration` - ISO 8601 format
- `rrule` - RFC 5545 recurrence rules
- `allDay` - Boolean flag
- `color` - Event categorization

**User Context:**
- Calendar organization (Personal/Work calendars)
- Event patterns (timing, duration, frequency)
- Recurring event habits
- Free/busy time slots

---

### AI Model & Service Options

#### Option 1: OpenAI GPT-4 / GPT-4o (Recommended for MVP)

**Pros:**
- GPT-4 Vision handles image→calendar extraction directly
- Excellent natural language understanding
- Large context window (128k tokens)
- Well-documented Android/Kotlin SDK via Retrofit
- JSON mode for structured output

**Cons:**
- Higher cost per request ($0.01-0.03/1k tokens)
- Requires internet connection
- Rate limits on free tier

**Best For:** Image parsing, natural language input, complex reasoning

**Cost Estimate:** ~$0.05-0.15 per AI-assisted event creation

---

#### Option 2: Google Gemini (Pro/Flash)

**Pros:**
- Native Android/Firebase integration
- Gemini Flash is very fast and cheap
- Good multimodal capabilities (text, image, audio)
- Generous free tier (60 requests/minute)
- On-device inference option with Gemini Nano

**Cons:**
- Vision quality slightly below GPT-4V
- Less mature API than OpenAI

**Best For:** Cost-sensitive production, Android-native integration

**Cost Estimate:** ~$0.01-0.05 per AI-assisted event creation

---

#### Option 3: Anthropic Claude (3.5 Sonnet / 3.5 Haiku)

**Pros:**
- Excellent instruction following
- Strong at structured data extraction
- Good reasoning capabilities
- Haiku is fast and cheap for simple tasks

**Cons:**
- No vision capability in Haiku
- Requires API key management
- No official Android SDK (use Retrofit)

**Best For:** Text-only NLP, complex calendar reasoning

**Cost Estimate:** ~$0.01-0.08 per AI-assisted event creation

---

#### Option 4: On-Device Models (ML Kit / TensorFlow Lite)

**Pros:**
- No API costs
- Works offline
- Privacy-preserving (data stays on device)
- Fast response times

**Cons:**
- Limited capabilities vs cloud models
- Larger app size (50-200MB for models)
- Complex implementation
- Less accurate for complex inputs

**Best For:** Simple entity extraction, offline fallback

**Models:**
- Google ML Kit Text Recognition (OCR)
- ML Kit Entity Extraction (dates, times)
- TensorFlow Lite custom model

---

#### Option 5: Hybrid Approach (Recommended)

**Architecture:**
```
User Input
    ↓
┌─────────────────────────────────────┐
│  On-Device Preprocessing            │
│  - ML Kit for quick text extraction │
│  - Simple date/time parsing         │
│  - Input validation                 │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│  Cloud AI (when needed)             │
│  - Complex image understanding      │
│  - Natural language processing      │
│  - Multi-event extraction           │
│  - Conflict resolution suggestions  │
└─────────────────────────────────────┘
    ↓
Event Suggestions → User Approval → Calendar
```

**Benefits:**
- Cost-effective (only call cloud AI when needed)
- Works offline for simple inputs
- Best accuracy for complex inputs
- Graceful degradation

---

### Recommended AI Service Stack

| Feature | Primary Service | Fallback |
|---------|----------------|----------|
| Image → Calendar | GPT-4 Vision | Gemini Pro Vision |
| Text → Events | Gemini Flash | Claude Haiku |
| Voice → Text | Google Speech-to-Text | Whisper API |
| Date/Time Extraction | ML Kit (on-device) | Regex + Cloud |
| Smart Suggestions | Gemini Flash | Local heuristics |

---

### AI Integration Architecture

```
┌──────────────────────────────────────────────────────┐
│                    UI Layer                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │ AI Input    │  │ Preview     │  │ Refinement  │  │
│  │ Screen      │  │ Screen      │  │ Dialog      │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
└──────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────┐
│               AI Processing Layer                    │
│  ┌─────────────────────────────────────────────────┐ │
│  │              AICalendarAssistant                │ │
│  │  - Orchestrates AI calls                        │ │
│  │  - Manages input preprocessing                  │ │
│  │  - Handles response parsing                     │ │
│  └─────────────────────────────────────────────────┘ │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │ ImageParser │  │ TextParser  │  │ VoiceParser │  │
│  │ (GPT-4V)    │  │ (Gemini)    │  │ (STT API)   │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
└──────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────┐
│                Data Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │ Pending     │  │ Local       │  │ Supabase    │  │
│  │ Events DAO  │  │ Calendar    │  │ Repository  │  │
│  │ (Room)      │  │ Repository  │  │ (Sync)      │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
└──────────────────────────────────────────────────────┘
```

---

### New Components to Create

#### 1. AI Service Interfaces

```kotlin
// data/ai/AIService.kt
interface AIService {
    suspend fun parseImage(image: ByteArray, prompt: String): AIResponse
    suspend fun parseText(text: String): AIResponse
    suspend fun refineEvents(events: List<PendingEvent>, instruction: String): AIResponse
}

// data/ai/AIResponse.kt
data class AIResponse(
    val events: List<ExtractedEvent>,
    val confidence: Float,
    val warnings: List<String>,
    val rawResponse: String
)

data class ExtractedEvent(
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Long?,
    val endTime: Long?,
    val duration: String?,
    val isAllDay: Boolean,
    val recurrenceRule: String?,
    val confidence: Float,
    val suggestedCalendar: String?
)
```

#### 2. AI Provider Implementations

```kotlin
// data/ai/providers/OpenAIProvider.kt
class OpenAIProvider(private val apiKey: String) : AIService {
    // GPT-4 Vision for images
    // GPT-4 for text
}

// data/ai/providers/GeminiProvider.kt
class GeminiProvider(private val context: Context) : AIService {
    // Firebase Vertex AI integration
    // Gemini Pro/Flash
}

// data/ai/providers/ClaudeProvider.kt
class ClaudeProvider(private val apiKey: String) : AIService {
    // Claude API for text processing
}
```

#### 3. AI Calendar Assistant (Orchestrator)

```kotlin
// data/ai/AICalendarAssistant.kt
class AICalendarAssistant(
    private val imageProvider: AIService,
    private val textProvider: AIService,
    private val voiceService: SpeechToTextService,
    private val calendarRepository: LocalCalendarRepository
) {
    suspend fun processInput(input: AIInput): ProcessingResult
    suspend fun refineEvents(events: List<PendingEvent>, instruction: String): ProcessingResult
    suspend fun detectConflicts(events: List<ExtractedEvent>): List<Conflict>
    suspend fun suggestOptimalTimes(event: ExtractedEvent): List<TimeSlot>
}
```

#### 4. Pending Events System

```kotlin
// data/model/PendingEvent.kt
@Entity(tableName = "pending_events")
data class PendingEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Long?,
    val endTime: Long?,
    val isAllDay: Boolean,
    val recurrenceRule: String?,
    val confidence: Float,
    val status: PendingStatus, // PENDING, APPROVED, REJECTED, MODIFIED
    val suggestedCalendarId: String?,
    val sourceType: InputType, // TEXT, IMAGE, VOICE, DOCUMENT
    val createdAt: Long
)
```

#### 5. UI Components

```
ui/ai/
├── AIInputFragment.kt        # Multi-modal input screen
├── AIPreviewFragment.kt      # Event preview/approval screen
├── AIRefinementDialog.kt     # Natural language refinement
├── components/
│   ├── InputTypeSelector.kt  # Photo/Text/Voice/Doc selector
│   ├── EventPreviewCard.kt   # Single event preview
│   ├── ConfidenceIndicator.kt
│   └── ConflictWarning.kt
```

---

### AI Prompt Engineering

#### Image Parsing Prompt (GPT-4 Vision)

```
You are a calendar assistant. Extract calendar events from this image.

For each event found, provide:
- title: Event name
- date: In YYYY-MM-DD format
- startTime: In HH:MM format (24h)
- endTime: In HH:MM format (24h) or null
- location: If mentioned
- recurrence: If it's recurring (e.g., "weekly on Mondays")

Output as JSON array. If no events found, return empty array.
Include confidence score (0.0-1.0) for each field.

Example output:
{
  "events": [
    {
      "title": "Team Meeting",
      "date": "2024-03-15",
      "startTime": "14:00",
      "endTime": "15:00",
      "location": "Conference Room A",
      "recurrence": null,
      "confidence": 0.95
    }
  ]
}
```

#### Text Parsing Prompt

```
Extract calendar events from the following text.
Current date: {currentDate}
User timezone: {timezone}

Text: "{userInput}"

Parse any dates (including relative like "tomorrow", "next Monday").
Output as JSON with the same structure as above.
```

---

### Cost Management Strategy

1. **Request Caching**
   - Cache identical requests for 24 hours
   - Hash input content for cache key

2. **Tiered Processing**
   - Simple inputs → On-device ML Kit
   - Complex inputs → Cloud AI
   - User preference for quality vs cost

3. **Rate Limiting**
   - Free tier: 10 AI requests/day
   - Premium: Unlimited
   - Show remaining requests in UI

4. **Batch Processing**
   - Combine multiple images into single request
   - Process in batches during off-peak hours

---

### Implementation Phases (AI-Specific)

#### Phase 6A: Text Input AI (2-3 weeks)
- [ ] Create AIService interface
- [ ] Implement Gemini/OpenAI text provider
- [ ] Build AI input screen (text only)
- [ ] Build preview/approval screen
- [ ] Add pending events to Room database
- [ ] Integration with existing calendar

#### Phase 6B: Image Input AI (2-3 weeks)
- [ ] Implement GPT-4 Vision provider
- [ ] Add image picker/camera integration
- [ ] Handle image preprocessing
- [ ] Test with various schedule formats

#### Phase 6C: Voice Input (1-2 weeks)
- [ ] Integrate Google Speech-to-Text
- [ ] Add voice recording UI
- [ ] Pipe voice transcription to text parser

#### Phase 6D: Advanced Features (2-3 weeks)
- [ ] Iterative refinement dialog
- [ ] Conflict detection
- [ ] Smart time suggestions
- [ ] Confidence indicators
- [ ] Batch approval

#### Phase 6E: Optimization (1-2 weeks)
- [ ] On-device ML Kit fallback
- [ ] Request caching
- [ ] Cost monitoring
- [ ] Performance optimization

---

### API Key Management

**Development:**
- Store in `local.properties` (gitignored)
- Access via BuildConfig fields

**Production:**
- Store in Supabase Edge Functions (server-side)
- Or use Android Keystore for client-side
- Never expose keys in APK

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "OPENAI_API_KEY",
            "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
    }
}
```

---

### Security Considerations

1. **API Keys**: Never hardcode, use secure storage
2. **User Data**: Don't send unnecessary context to AI
3. **Image Privacy**: Warn users before uploading images
4. **Data Retention**: Configure AI providers to not retain data
5. **Audit Logging**: Track AI requests for debugging

---

### Testing Strategy for AI

1. **Unit Tests**
   - Mock AI responses
   - Test date/time parsing
   - Test JSON parsing

2. **Integration Tests**
   - Test with real API (limited)
   - Verify end-to-end flow

3. **Test Cases**
   - Simple event: "Meeting tomorrow at 2pm"
   - Complex: "Weekly standup every Monday and Wednesday at 9am"
   - Image: Class schedule, event flyer, screenshot
   - Edge cases: Ambiguous dates, missing times

---

### Recommended First Implementation

**Start with Option 2 (Gemini) because:**
1. Free tier is generous for development
2. Native Android/Firebase integration
3. Good enough quality for MVP
4. Easy to swap to GPT-4 later if needed

**MVP Feature Set:**
1. Text input → Event extraction
2. Simple preview screen
3. Basic approval flow
4. Single calendar target

**Then iterate to add:**
- Image input
- Voice input
- Refinement dialog
- Conflict detection

---

*Document Version: 1.1*
*Last Updated: January 2026*
