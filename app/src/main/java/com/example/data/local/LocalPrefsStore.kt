package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.studyShieldDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "study_shield_prefs")

enum class ThemePreference(val key: String, val label: String) {
  SYSTEM("system", "System Default"),
  DARK("dark", "Midnight Dark"),
  LIGHT("light", "Crisp Light");

  companion object {
    fun fromKey(key: String?): ThemePreference {
      return entries.firstOrNull { it.key == key } ?: SYSTEM
    }
  }
}

data class ShieldRuntimeState(
    val isShieldActive: Boolean = false,
    val activeSubject: String = "Deep Focus",
    val sessionEndEpochMs: Long = 0L,
    val blockedPackages: Set<String> = UserProfile.DEFAULT_BLOCKED_PACKAGES.toSet(),
    val cachedPasswordHash: String = "",
    val cachedUid: String = "",
    val lastBlockedAppPackage: String? = null,
)

/**
 * Process-wide state holder kept synchronized with DataStore so AccessibilityService can inspect
 * active block state instantaneously with zero disk I/O latency.
 */
object ShieldStateHolder {
  private val _state = MutableStateFlow(ShieldRuntimeState())
  val state: StateFlow<ShieldRuntimeState> = _state.asStateFlow()

  fun update(transform: (ShieldRuntimeState) -> ShieldRuntimeState) {
    _state.value = transform(_state.value)
  }
}

class LocalPrefsStore(private val context: Context) {
  private val dataStore = context.applicationContext.studyShieldDataStore

  val themePreference: Flow<ThemePreference> =
      dataStore.data.map { prefs -> ThemePreference.fromKey(prefs[KEY_THEME]) }

  val shieldRuntimeState: Flow<ShieldRuntimeState> =
      dataStore.data.map { prefs ->
        val state =
            ShieldRuntimeState(
                isShieldActive = prefs[KEY_SHIELD_ACTIVE] ?: false,
                activeSubject = prefs[KEY_ACTIVE_SUBJECT] ?: "Deep Focus",
                sessionEndEpochMs = prefs[KEY_SESSION_END_MS] ?: 0L,
                blockedPackages =
                    prefs[KEY_BLOCKED_PACKAGES] ?: UserProfile.DEFAULT_BLOCKED_PACKAGES.toSet(),
                cachedPasswordHash = prefs[KEY_SHIELD_PW_HASH] ?: "",
                cachedUid = prefs[KEY_CACHED_UID] ?: "",
                lastBlockedAppPackage = prefs[KEY_LAST_BLOCKED_PKG],
            )
        ShieldStateHolder.update { state }
        state
      }

  val twoFactorVerifiedUid: Flow<String?> =
      dataStore.data.map { prefs -> prefs[KEY_2FA_VERIFIED_UID] }

  suspend fun setThemePreference(theme: ThemePreference) {
    dataStore.edit { prefs -> prefs[KEY_THEME] = theme.key }
  }

  suspend fun setShieldActive(
      active: Boolean,
      subject: String = "Deep Focus",
      endEpochMs: Long = 0L,
  ) {
    dataStore.edit { prefs ->
      prefs[KEY_SHIELD_ACTIVE] = active
      prefs[KEY_ACTIVE_SUBJECT] = subject
      prefs[KEY_SESSION_END_MS] = endEpochMs
      if (!active) {
        prefs.remove(KEY_LAST_BLOCKED_PKG)
      }
    }
    ShieldStateHolder.update {
      it.copy(
          isShieldActive = active,
          activeSubject = subject,
          sessionEndEpochMs = endEpochMs,
          lastBlockedAppPackage = if (!active) null else it.lastBlockedAppPackage,
      )
    }
  }

  suspend fun setBlockedPackages(packages: Set<String>) {
    dataStore.edit { prefs -> prefs[KEY_BLOCKED_PACKAGES] = packages }
    ShieldStateHolder.update { it.copy(blockedPackages = packages) }
  }

  suspend fun cacheSecurityCredentials(uid: String, passwordHash: String) {
    dataStore.edit { prefs ->
      prefs[KEY_CACHED_UID] = uid
      prefs[KEY_SHIELD_PW_HASH] = passwordHash
    }
    ShieldStateHolder.update { it.copy(cachedUid = uid, cachedPasswordHash = passwordHash) }
  }

  suspend fun recordBlockedAttempt(packageName: String?) {
    dataStore.edit { prefs ->
      if (packageName == null) {
        prefs.remove(KEY_LAST_BLOCKED_PKG)
      } else {
        prefs[KEY_LAST_BLOCKED_PKG] = packageName
      }
    }
    ShieldStateHolder.update { it.copy(lastBlockedAppPackage = packageName) }
  }

  suspend fun setTwoFactorVerifiedUid(uid: String?) {
    dataStore.edit { prefs ->
      if (uid == null) {
        prefs.remove(KEY_2FA_VERIFIED_UID)
      } else {
        prefs[KEY_2FA_VERIFIED_UID] = uid
      }
    }
  }

  val isGuestMode: Flow<Boolean> =
      dataStore.data.map { prefs -> prefs[KEY_GUEST_MODE] ?: false }

  suspend fun setGuestMode(guest: Boolean) {
    dataStore.edit { prefs -> prefs[KEY_GUEST_MODE] = guest }
  }

  private companion object {
    val KEY_THEME = stringPreferencesKey("theme_preference")
    val KEY_SHIELD_ACTIVE = booleanPreferencesKey("shield_active")
    val KEY_ACTIVE_SUBJECT = stringPreferencesKey("active_subject")
    val KEY_SESSION_END_MS = longPreferencesKey("session_end_epoch_ms")
    val KEY_BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
    val KEY_SHIELD_PW_HASH = stringPreferencesKey("shield_pw_hash")
    val KEY_CACHED_UID = stringPreferencesKey("cached_uid")
    val KEY_LAST_BLOCKED_PKG = stringPreferencesKey("last_blocked_pkg")
    val KEY_2FA_VERIFIED_UID = stringPreferencesKey("two_factor_verified_uid")
    val KEY_GUEST_MODE = booleanPreferencesKey("guest_mode")
  }
}
