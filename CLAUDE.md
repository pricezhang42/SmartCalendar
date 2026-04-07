# SmartCalendar - Project Rules

## Project Overview

SmartCalendar is a native Android calendar app built with Kotlin, XML layouts, and Material Design 3. It features multi-calendar support, AI-powered event creation (via Gemini), Supabase backend sync, and reminder/alarm system.

## Tech Stack

- **Language**: Kotlin
- **UI**: Android XML layouts (View-based, not Compose)
- **Theme**: Material Design 3 (`Theme.Material3.DayNight.NoActionBar`)
- **Database**: Room (SQLite)
- **Backend**: Supabase (auth, database, realtime)
- **AI**: Google Gemini API
- **Navigation**: Jetpack Navigation Component with bottom nav + drawer

## Project Structure

```
app/src/main/java/com/example/smartcalendar/
  ui/
    auth/          # LoginActivity
    calendar/      # CalendarFragment, month/week adapters
    event/         # EventModalFragment
    ai/            # AIAssistantActivity, AIInputFragment, AIPreviewFragment
    mine/          # MineFragment (settings/calendars)
  data/
    model/         # ICalEvent, LocalCalendar, PendingEvent, etc.
    local/         # Room database, DAOs
    repository/    # LocalCalendarRepository, AuthRepository
    ai/            # AICalendarAssistant, Gemini integration
    sync/          # SyncManager, RealtimeSync, CalendarImporter/Exporter
    remote/        # Supabase client
    notification/  # ReminderManager, AlarmActivity

app/src/main/res/
  layout/          # 20 XML layout files
  drawable/        # 23 XML drawable shapes + custom icons
  values/          # colors.xml, themes.xml, strings.xml, dimens.xml
  values-night/    # Dark theme overrides
  navigation/      # nav_graph.xml
  menu/            # Bottom nav menu
```

## Design Tokens

### Colors (`res/values/colors.xml`)

IMPORTANT: Never hardcode hex color values in layouts or drawables. Always reference color resources.

| Token | Hex | Usage |
|-------|-----|-------|
| `primary_blue` | #4285F4 | Primary actions, active states, FAB, links |
| `primary_blue_dark` | #3367D6 | Pressed/dark variant of primary |
| `light_blue` | #8AB4F8 | Secondary blue accents |
| `light_blue_bg` | #E8F0FE | Selected/active backgrounds (drawer, today) |
| `event_purple` | #7986CB | Event category color |
| `event_pink` | #F06292 | Event category color |
| `event_green` | #81C784 | Event category color |
| `event_orange` | #FFB74D | Event category color |
| `event_teal` | #4DB6AC | Event category color |
| `today_highlight` | #E8F0FE | Today date background circle |
| `weekend_text` | #9E9E9E | Weekend day numbers (grayed) |
| `divider` | #E0E0E0 | Section dividers, grid lines |
| `ai_highlight` | #FFF3E0 | AI suggestion card background |
| `ai_highlight_border` | #FF9800 | AI suggestion card border |
| `ai_confidence_high` | #4CAF50 | High confidence badge (green) |
| `ai_confidence_medium` | #FF9800 | Medium confidence badge (orange) |
| `ai_confidence_low` | #F44336 | Low confidence badge (red) |

### Spacing (`res/values/dimens.xml`)

Spacing follows a 4dp base grid. Key tokens:

| Token | Value | Usage |
|-------|-------|-------|
| `fab_margin` | 16dp | FAB edge spacing |
| `hour_height` | 40dp | Week view hour row height |
| `day_cell_min_height` | 60dp | Month view day cell minimum |

Standard spacing values used across layouts: 4dp, 8dp, 12dp, 16dp, 20dp, 24dp, 32dp, 48dp.

### Corner Radius Hierarchy

- **Small (2-4dp)**: Text inputs, edit borders, grid items, drag handles
- **Medium (18dp)**: Chat message bubbles
- **Large (28dp)**: Buttons, alarm dismiss, FAB
- **Pill (100dp)**: Status badges, pill-shaped indicators

### Opacity Patterns

Subtle backgrounds use hex alpha on `primary_blue`:
- 10% opacity: `#1A4285F4` (pill badges, dismiss buttons)
- 16% opacity: `#284285F4` (alarm ring backgrounds)
- 20% opacity: `#334285F4` (pill badge borders)
- 33% opacity: `#554285F4` (alarm ring strokes)

### Typography Scale

| Size | Weight | Usage |
|------|--------|-------|
| 28sp | Bold | App title (login) |
| 20sp | Bold | Screen titles, section headers |
| 16sp | Regular | Body text, form field values, row labels |
| 14sp | Regular/Bold | Section labels, secondary text, view mode labels |
| 12sp | Regular | Hints, subtitles, "Soon" tags, confidence badges |
| 11sp | Regular | Week label |
| 9sp | Medium | Event chip text (compact) |

### Stroke Widths

- **0.5dp**: Calendar grid lines
- **1dp**: Input borders, dropdown borders, dividers
- **1.5dp**: Outlined buttons, alarm dismiss borders
- **2dp**: Unchecked checkbox borders

## Component Patterns

### Layouts

- IMPORTANT: Use `LinearLayout` with `orientation` for simple stacks
- Use `ConstraintLayout` only when complex positioning is needed
- Use `FrameLayout` for overlapping content (FAB over content)
- Use `CoordinatorLayout` for scrolling behavior with AppBar
- IMPORTANT: All screens use white (`@android:color/white`) background

### Material Components

- Text inputs: `TextInputLayout` + `TextInputEditText` with `OutlinedBox` style
- Buttons: `MaterialButton` with appropriate styles:
  - Primary filled: default style with `backgroundTint="@color/primary_blue"`
  - Outlined: `Widget.MaterialComponents.Button.OutlinedButton`
  - Text: `Widget.MaterialComponents.Button.TextButton`
- Toggles: `SwitchMaterial`
- Chips: `Chip` with `Widget.MaterialComponents.Chip.Choice` style
- Bottom nav: `BottomNavigationView` with `labelVisibilityMode="labeled"`
- Toolbar: `MaterialToolbar` with white background, 0dp elevation

### Navigation Drawer

- Uses `DrawerLayout` as root with `NavigationView`
- Drawer width: 300dp
- Header layout: `nav_header_drawer.xml` with settings icon
- View modes listed with icons, active item gets `light_blue_bg` background
- Calendar toggles with colored checkboxes

### Calendar Grid

- Month view: 7-column grid via `LinearLayout` rows
- Day cells: Vertical `LinearLayout` with day number + event chips
- Event chips: Small colored rectangles (rounded 3dp) with white 9sp text
- Today: Light blue circle background on day number
- Weekends: Gray text color (`weekend_text`)

### AI Assistant

- Chat interface with RecyclerView
- User messages: Blue bubbles (right-aligned)
- AI messages: Gray bubbles (left-aligned)
- Suggestion cards: Orange-bordered cards with event details + confidence badge
- Max bubble width: 280dp

### Event Modal

- ScrollView wrapper for long form
- Row pattern: Label left, value right (blue for tappable values)
- Color dots: 12dp circles next to calendar/color selectors
- Repeat options: Collapsed by default, shown via toggle

## Figma MCP Integration Rules

These rules define how to translate Figma designs into code for this project.

### Required Flow

1. Run `get_design_context` first to fetch the structured representation for the exact node(s)
2. If the response is too large, run `get_metadata` for the high-level node map, then re-fetch specific nodes
3. Run `get_screenshot` for a visual reference of the design being implemented
4. Only after having both design context and screenshot, begin implementation
5. Translate the Figma output into Android XML layouts + Kotlin following this project's conventions
6. Validate against Figma for 1:1 visual fidelity before marking complete

### Implementation Rules

- IMPORTANT: Treat Figma MCP output as a design reference, not final code - translate to Android XML
- IMPORTANT: Reuse existing drawable resources from `res/drawable/` instead of creating duplicates
- IMPORTANT: Reference colors from `@color/*` resources, never hardcode hex values in layouts
- IMPORTANT: Use Material Design 3 components (`com.google.android.material.*`) when available
- Use existing string resources from `res/values/strings.xml` for all user-facing text
- Follow the established spacing scale (multiples of 4dp)
- Match the corner radius hierarchy (small/medium/large/pill)
- Respect the typography scale for font sizes

### Asset Handling

- The Figma MCP server provides an assets endpoint for images and SVGs
- IMPORTANT: If the Figma MCP server returns a localhost source for an image or SVG, use that source directly
- IMPORTANT: DO NOT import new icon packages - all assets should come from the Figma payload
- IMPORTANT: DO NOT use or create placeholders if a localhost source is provided
- Store vector drawables in `res/drawable/` with `ic_` prefix
- Store raster images in appropriate `res/mipmap-*` or `res/drawable-*` density buckets

### Figma Variable to Android Resource Mapping

When implementing designs from the Figma file `kr3ZwRBEglvUFrPglCr5Ge`:

| Figma Variable | Android Resource |
|----------------|------------------|
| `Primary/Blue` | `@color/primary_blue` |
| `Primary/Blue Dark` | `@color/primary_blue_dark` |
| `Primary/Light Blue` | `@color/light_blue` |
| `Primary/Light Blue BG` | `@color/light_blue_bg` |
| `Event/Purple` | `@color/event_purple` |
| `Event/Pink` | `@color/event_pink` |
| `Event/Green` | `@color/event_green` |
| `Event/Orange` | `@color/event_orange` |
| `Event/Teal` | `@color/event_teal` |
| `UI/Divider` | `@color/divider` |
| `AI/Highlight` | `@color/ai_highlight` |
| `Base/White` | `@android:color/white` |
| `Base/Black` | `@android:color/black` |

## Accessibility

- All `ImageView` and `ImageButton` must have `contentDescription`
- Interactive elements need `background="?attr/selectableItemBackground"` for touch feedback
- Color contrast should meet WCAG AA standards
- Support dark theme via `values-night/` resource qualifiers

## Build & Run

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Install on connected device
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumentation tests
```
