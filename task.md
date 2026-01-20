# Supabase Auth & Cloud Sync

## Phase 1: Supabase Setup & Auth ✅
- [x] Create Supabase project <!-- id: 0 -->
- [x] Add Supabase dependencies <!-- id: 1 -->
- [x] Create SupabaseClient singleton <!-- id: 2 -->
- [x] Implement AuthRepository <!-- id: 3 -->
- [x] Create LoginActivity UI <!-- id: 4 -->
- [x] Add auth check to MainActivity <!-- id: 5 -->

## Phase 2: Room Database ✅
- [x] Create AppDatabase with Room <!-- id: 6 -->
- [x] Add CalendarDao and EventDao <!-- id: 7 -->
- [x] Update LocalCalendar model for sync <!-- id: 8 -->
- [x] Update ICalEvent model for sync <!-- id: 9 -->
- [x] Migrate from SharedPreferences to Room <!-- id: 10 -->

## Phase 3: Supabase Sync ✅
- [x] Create PostgreSQL schema in Supabase <!-- id: 11 -->
- [x] Implement SupabaseRepository <!-- id: 12 -->
- [x] Create SyncManager <!-- id: 13 -->
- [x] Add real-time subscriptions <!-- id: 14 -->
- [x] Add manual sync button <!-- id: 15 -->
- [x] Add sync status indicator <!-- id: 16 -->

## Phase 4: Offline Support ✅
- [x] Queue offline changes <!-- id: 17 -->
- [x] Monitor network state <!-- id: 18 -->
- [x] Sync on reconnect <!-- id: 19 -->
- [x] Conflict resolution <!-- id: 20 -->

## Phase 5: Testing ✅
- [x] Test auth flow <!-- id: 21 -->
- [x] Test sync flow <!-- id: 22 -->
- [x] Test offline/reconnect <!-- id: 23 -->
- [x] Test multi-device sync <!-- id: 24 -->
