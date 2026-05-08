# SmartScheduler

An intelligent Android calendar application that combines traditional calendar management with advanced AI capabilities powered by **Cali** (Calendar Intelligence).

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)
![TargetSDK](https://img.shields.io/badge/TargetSDK-36-brightgreen.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [AI Integration](#ai-integration)
- [Roadmap](#roadmap)
- [Contact](#contact)

---

## Overview

SmartScheduler is a next-generation calendar application that revolutionizes how users manage their schedules. By combining traditional calendar functionality with AI-powered event extraction, users can create calendar events through natural language, images, documents, and voice commands—while maintaining full manual control.

**Key Highlights:**
- 📅 **Full-Featured Calendar** - Day, Week, Month, and Agenda views with recurring events support
- 🤖 **AI Assistant (Cali)** - Intelligent event extraction from multiple input sources
- 📱 **Multi-Modal Input** - Text, images, documents, and voice processing
- ☁️ **Cloud Sync** - Real-time synchronization with offline support
- 🔄 **RFC 5545 Compliant** - Full iCalendar format compatibility with advanced recurrence rules
- 🔐 **Secure Authentication** - Supabase-powered user authentication

---
## Demos

### Create Recurring Event with Text in Cali
Example Input:
```
I have a French class every Friday evening at 7pm-8:30pm from this week until the end of July. But there is no class at the last week of June and the class at the first week of June will be postponed by 1 hour.
```

https://github.com/user-attachments/assets/3e7bf9ce-61d5-44f5-a527-9297ef874c49

### Search Event online and Add it to Calendar
Example Input:
```
Search for and add the Tech Thursday event in Winnipeg for the next week. Set up an alarm 20 min before the event.
```

https://github.com/user-attachments/assets/61fdfa01-c046-46ce-b011-a3cc1a9377af

### Create Recurring Event with Image
Upload a screenshot of my class schedule and refine new events on the review screen.

https://github.com/user-attachments/assets/371616ab-4bd2-4f2f-ac04-daa206609592

---

## Features

### Traditional Calendar Management
- ✅ Create, view, edit, and delete calendar events manually
- ✅ Multiple calendar views (Day, Week, Month, Agenda)
- ✅ Advanced recurring events with RFC 5545 support
- ✅ Multiple calendars with customizable colors and visibility
- ✅ Event categories and color coding
- ✅ Reminders and notifications
- ✅ Import/export from device calendar
- ✅ All-day event support

### AI-Powered Event Creation (Cali)

#### Multi-Modal Input Processing
- **Text Input**: Natural language processing for emails, messages, and prompts
  - Example: "Team meeting tomorrow at 2pm in Conference Room A"
- **Image Input**: Extract calendar information from screenshots, schedules, and flyers
  - Upload class schedules, event posters, or timetable screenshots
- **Document Input**: Parse PDF and Word documents for event information
- **Voice Input**: Speech-to-text for hands-free event creation
- **Combined Input**: Process multiple input types together for comprehensive event extraction

#### Intelligent Event Extraction
- Parse dates and times from unstructured text
- Extract event titles, locations, attendees, and descriptions
- Identify recurring patterns automatically
- Recognize event categories and suggest appropriate tags
- Generate confidence scores for each extracted field
- Handle relative dates ("tomorrow", "next Monday", "in 2 weeks")

#### Smart User Confirmation Flow
- Preview screen showing pending calendar changes
- Edit AI-generated suggestions before approval
- Iterative refinement through follow-up natural language prompts
- Batch approval for multiple events
- Conflict detection and resolution suggestions
- Undo/redo functionality

### Cloud Sync & Offline Support
- Real-time synchronization with Supabase backend
- Offline-first architecture with automatic sync when reconnected
- Conflict resolution for simultaneous edits
- Pending operation queue for offline changes
- Network connectivity monitoring

---

## Technology Stack

### Platform & Language
- **Platform**: Android (minSdk: 26, targetSdk: 36)
- **Language**: Kotlin
- **UI Framework**: Android XML layouts + Material Design 3

### Cloud Services & Database & Networking 
- **Room** - Local SQLite database with type-safe queries
- **Supabase** - Backend as a Service
  - GoTrue - Authentication
  - PostgREST - PostgreSQL API
  - Realtime - Real-time sync
- **Ktor Client** - HTTP client

### AI Integration
- **Google Gemini API** - Multi-modal AI processing
  - gemini-2.5-flash (primary)
  - gemini-1.5-flash (fallback)
- **Natural Language Processing** - Event extraction and parsing
- **Image Understanding** - GPT-4 Vision API integration (planned)
- **Speech-to-Text** - Voice input processing (planned)

### Testing
- **JUnit** - Unit testing
- **Espresso** - UI testing

---

### Quick Start Guide

1. **Sign Up / Login** - Create an account or sign in with email/password
2. **Create Your First Event** - Tap the "+" button to manually add an event
3. **Try AI Assistant** - Tap the AI button and say "Meeting tomorrow at 2pm"
4. **Upload a Schedule** - Take a photo of a class schedule and let Cali extract events
5. **Approve & Edit** - Review AI suggestions, edit if needed, and approve

---

## AI Integration

### How Cali Works

SmartScheduler's AI assistant, **Cali**, uses Google Gemini API to understand and extract calendar information from various input sources.

#### Processing Pipeline

```
User Input (Text/Image/Document/Voice)
           ↓
    Preprocessing
           ↓
    Gemini API Analysis
           ↓
    Event Extraction
           ↓
    Confidence Scoring
           ↓
    Conflict Detection
           ↓
    User Preview & Approval
           ↓
    Calendar Integration
```

### Example Prompts

**Simple Event:**
```
"Team standup tomorrow at 9am"
→ Extracts: title, date, time
```

**Complex Event:**
```
"Weekly client review every Tuesday and Thursday from 2-3pm
in Conference Room B, starting next week for 3 months"
→ Extracts: title, recurrence rule, location, duration, date range
```

**Image Input:**
Upload a class schedule screenshot
→ Extracts: Multiple events with dates, times, locations, recurring patterns


---

## Roadmap

### Current Phase (v1.0)
- ✅ Core calendar functionality
- ✅ Supabase authentication and sync
- ✅ Google Gemini AI integration
- ✅ Multi-modal input (text, images, documents)
- ✅ Recurring events with RFC 5545 support
- ✅ Offline support with sync queue
- 🚧 Voice input integration (in progress)

### Upcoming Features (v1.1+)
- [ ] GPT-4 Vision API integration for enhanced image understanding
- [ ] iOS version
- [ ] On-device ML Kit fallback for offline AI
- [ ] Smart meeting scheduling across multiple calendars
- [ ] Email client integration for automatic event extraction
- [ ] Third-party integrations (Zoom, Google Meet)
- [ ] Predictive scheduling based on user patterns

---

## Contact

**Project Maintainers:**
- GitHub: Linpu Zhang [@pricezhang42](https://github.com/pricezhang42)
- Email: zhangl53@myumanitoba.ca

**Support:**
- Issue Tracker: [GitHub Issues](https://github.com/yourusername/SmartScheduler/issues)
- Documentation: [PROJECT_PLAN.md](PROJECT_PLAN.md)

---

<div align="center">
  <p>
    <a href="#top">Back to Top ⬆️</a>
  </p>
</div>
