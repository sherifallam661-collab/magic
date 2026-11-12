package com.mediagrab.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.welcomeDataStore by preferencesDataStore(name = "welcome_prefs")

@Singleton
class WelcomePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val disclaimerAcceptedKey = booleanPreferencesKey("disclaimer_accepted")

    val isDisclaimerAccepted: Flow<Boolean> = context.welcomeDataStore.data
        .map { prefs -> prefs[disclaimerAcceptedKey] ?: false }

    suspend fun acceptDisclaimer() {
        context.welcomeDataStore.edit { prefs ->
            prefs[disclaimerAcceptedKey] = true
        }
    }
}
