package com.example.twinmindclone.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACTIVE_MEETING_ID = longPreferencesKey("active_meeting_id")
    }

    suspend fun saveActiveMeetingId(id: Long) {
        context.dataStore.edit { it[ACTIVE_MEETING_ID] = id }
    }

    suspend fun clearActiveMeetingId() {
        context.dataStore.edit { it.remove(ACTIVE_MEETING_ID) }
    }

    suspend fun getActiveMeetingId(): Long? {
        val prefs = context.dataStore.data.first()
        return prefs[ACTIVE_MEETING_ID]
    }
}
