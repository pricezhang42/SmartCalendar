package com.example.smartcalendar.data.model

/**
 * Sync status for local entities.
 */
enum class SyncStatus {
    SYNCED,     // Entity is synced with server
    PENDING,    // Entity has local changes not yet uploaded
    CONFLICT,   // Entity has conflicting changes
    DELETED     // Entity marked for deletion
}
