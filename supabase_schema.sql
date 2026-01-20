-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Calendars table
CREATE TABLE IF NOT EXISTS calendars (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    name TEXT NOT NULL,
    color INTEGER NOT NULL,
    is_default BOOLEAN DEFAULT false,
    is_visible BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, name)
);

-- Events table
CREATE TABLE IF NOT EXISTS events (
    uid TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    calendar_id TEXT NOT NULL REFERENCES calendars(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    description TEXT DEFAULT '',
    location TEXT DEFAULT '',
    dt_start BIGINT NOT NULL,
    dt_end BIGINT NOT NULL,
    duration TEXT,
    all_day BOOLEAN DEFAULT false,
    rrule TEXT,
    rdate TEXT,
    exdate TEXT,
    exrule TEXT,
    color INTEGER NOT NULL,
    last_modified BIGINT NOT NULL,
    original_id BIGINT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_calendars_user_id ON calendars(user_id);
CREATE INDEX IF NOT EXISTS idx_events_user_id ON events(user_id);
CREATE INDEX IF NOT EXISTS idx_events_calendar_id ON events(calendar_id);
CREATE INDEX IF NOT EXISTS idx_events_dt_start ON events(dt_start);

-- Drop existing RLS policies if they exist
DROP POLICY IF EXISTS "Users can view their own calendars" ON calendars;
DROP POLICY IF EXISTS "Users can insert their own calendars" ON calendars;
DROP POLICY IF EXISTS "Users can update their own calendars" ON calendars;
DROP POLICY IF EXISTS "Users can delete their own calendars" ON calendars;
DROP POLICY IF EXISTS "Users can view their own events" ON events;
DROP POLICY IF EXISTS "Users can insert their own events" ON events;
DROP POLICY IF EXISTS "Users can update their own events" ON events;
DROP POLICY IF EXISTS "Users can delete their own events" ON events;

-- Row Level Security (RLS)
ALTER TABLE calendars ENABLE ROW LEVEL SECURITY;
ALTER TABLE events ENABLE ROW LEVEL SECURITY;

-- RLS Policies for calendars
CREATE POLICY "Users can view their own calendars"
    ON calendars FOR SELECT
    USING (auth.uid()::text = user_id);

CREATE POLICY "Users can insert their own calendars"
    ON calendars FOR INSERT
    WITH CHECK (auth.uid()::text = user_id);

CREATE POLICY "Users can update their own calendars"
    ON calendars FOR UPDATE
    USING (auth.uid()::text = user_id);

CREATE POLICY "Users can delete their own calendars"
    ON calendars FOR DELETE
    USING (auth.uid()::text = user_id);

-- RLS Policies for events
CREATE POLICY "Users can view their own events"
    ON events FOR SELECT
    USING (auth.uid()::text = user_id);

CREATE POLICY "Users can insert their own events"
    ON events FOR INSERT
    WITH CHECK (auth.uid()::text = user_id);

CREATE POLICY "Users can update their own events"
    ON events FOR UPDATE
    USING (auth.uid()::text = user_id);

CREATE POLICY "Users can delete their own events"
    ON events FOR DELETE
    USING (auth.uid()::text = user_id);

-- Enable Realtime for tables (only if the tables don't already have realtime enabled)
-- Run this separately if needed:
-- ALTER PUBLICATION supabase_realtime ADD TABLE calendars;
-- ALTER PUBLICATION supabase_realtime ADD TABLE events;
