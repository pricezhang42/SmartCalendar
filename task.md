# Supabase Auth & Cloud Sync

## Phase 1: Supabase Setup & Auth ✅
- [x] Create Supabase project <!-- id: 0 -->
- [x] Add Supabase dependencies <!-- id: 1 -->
- [x] Create SupabaseClient singleton <!-- id: 2 -->
- [x] Implement AuthRepository <!-- id: 3 -->
- [x] Create LoginActivity UI <!-- id: 4 -->
- [x] Add auth check to MainActivity <!-- id: 5 -->

## Phase 2: Room Database
- [/] Create AppDatabase with Room <!-- id: 6 -->
- [ ] Add CalendarDao and EventDao <!-- id: 7 -->
- [ ] Update LocalCalendar model for sync <!-- id: 8 -->
- [ ] Update ICalEvent model for sync <!-- id: 9 -->
- [ ] Migrate from SharedPreferences to Room <!-- id: 10 -->

## Phase 3: Supabase Sync
- [ ] Create PostgreSQL schema in Supabase <!-- id: 11 -->
- [ ] Implement SupabaseRepository <!-- id: 12 -->
- [ ] Create SyncManager <!-- id: 13 -->
- [ ] Add real-time subscriptions <!-- id: 14 -->
- [ ] Add manual sync button <!-- id: 15 -->
- [ ] Add sync status indicator <!-- id: 16 -->

## Phase 4: Offline Support
- [ ] Queue offline changes <!-- id: 17 -->
- [ ] Monitor network state <!-- id: 18 -->
- [ ] Sync on reconnect <!-- id: 19 -->
- [ ] Conflict resolution <!-- id: 20 -->

## Phase 5: Testing
- [ ] Test auth flow <!-- id: 21 -->
- [ ] Test sync flow <!-- id: 22 -->
- [ ] Test offline/reconnect <!-- id: 23 -->
- [ ] Test multi-device sync <!-- id: 24 -->
