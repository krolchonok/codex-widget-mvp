package dev.ushagent.codexwidgetmvp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_NAME = "codex_widget_prefs"
private const val KEY_AUTH_JSON = "auth_json"
private const val KEY_LAST_WIDGET_HEADER = "last_widget_header"
private const val KEY_LAST_WIDGET_PRIMARY_VALUE = "last_widget_primary_value"
private const val KEY_LAST_WIDGET_PRIMARY_PERCENT = "last_widget_primary_percent"
private const val KEY_LAST_WIDGET_PRIMARY_CAPTION = "last_widget_primary_caption"
private const val KEY_LAST_WIDGET_SECONDARY_VALUE = "last_widget_secondary_value"
private const val KEY_LAST_WIDGET_SECONDARY_PERCENT = "last_widget_secondary_percent"
private const val KEY_LAST_WIDGET_SECONDARY_CAPTION = "last_widget_secondary_caption"
private const val KEY_LAST_WIDGET_UPDATED = "last_widget_updated"
private const val KEY_LAST_WIDGET_IS_REFRESHING = "last_widget_is_refreshing"

object CodexPrefs {
  @Volatile
  private var cachedPrefs: SharedPreferences? = null

  private fun prefs(context: Context): SharedPreferences {
    cachedPrefs?.let { return it }

    return synchronized(this) {
      cachedPrefs?.let { return@synchronized it }

      DebugLog.d("Opening encrypted preferences")
      val appContext = context.applicationContext
      val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

      EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      ).also {
        cachedPrefs = it
      }
    }
  }

  fun loadAuthJson(context: Context): String =
    (prefs(context).getString(KEY_AUTH_JSON, "") ?: "").also {
      DebugLog.d("Loaded auth.json from encrypted prefs: length=${it.length}")
    }

  fun saveAuthJson(context: Context, value: String) {
    DebugLog.d("Saving auth.json to encrypted prefs: length=${value.length}")
    prefs(context).edit().putString(KEY_AUTH_JSON, value).apply()
  }

  fun saveWidgetSnapshot(context: Context, snapshot: WidgetSnapshot) {
    DebugLog.d(
      "Saving widget snapshot: primary=${snapshot.primaryValueText}, " +
        "secondary=${snapshot.secondaryValueText}, refreshing=${snapshot.isRefreshing}, updated=${snapshot.updatedText}"
    )
    prefs(context).edit()
      .putString(KEY_LAST_WIDGET_HEADER, snapshot.headerText)
      .putString(KEY_LAST_WIDGET_PRIMARY_VALUE, snapshot.primaryValueText)
      .putInt(KEY_LAST_WIDGET_PRIMARY_PERCENT, snapshot.primaryPercent ?: -1)
      .putString(KEY_LAST_WIDGET_PRIMARY_CAPTION, snapshot.primaryCaptionText)
      .putString(KEY_LAST_WIDGET_SECONDARY_VALUE, snapshot.secondaryValueText)
      .putInt(KEY_LAST_WIDGET_SECONDARY_PERCENT, snapshot.secondaryPercent ?: -1)
      .putString(KEY_LAST_WIDGET_SECONDARY_CAPTION, snapshot.secondaryCaptionText)
      .putString(KEY_LAST_WIDGET_UPDATED, snapshot.updatedText)
      .putBoolean(KEY_LAST_WIDGET_IS_REFRESHING, snapshot.isRefreshing)
      .apply()
  }

  fun loadWidgetSnapshot(context: Context): WidgetSnapshot =
    WidgetSnapshot(
      headerText = prefs(context).getString(KEY_LAST_WIDGET_HEADER, "Codex limits") ?: "Codex limits",
      primaryValueText = prefs(context).getString(KEY_LAST_WIDGET_PRIMARY_VALUE, "--%") ?: "--%",
      primaryPercent = prefs(context).getInt(KEY_LAST_WIDGET_PRIMARY_PERCENT, -1).takeIf { it >= 0 },
      primaryCaptionText = prefs(context).getString(KEY_LAST_WIDGET_PRIMARY_CAPTION, "Tap to refresh") ?: "Tap to refresh",
      secondaryValueText = prefs(context).getString(KEY_LAST_WIDGET_SECONDARY_VALUE, null),
      secondaryPercent = prefs(context).getInt(KEY_LAST_WIDGET_SECONDARY_PERCENT, -1).takeIf { it >= 0 },
      secondaryCaptionText = prefs(context).getString(KEY_LAST_WIDGET_SECONDARY_CAPTION, null),
      updatedText = prefs(context).getString(KEY_LAST_WIDGET_UPDATED, "") ?: "",
      isRefreshing = prefs(context).getBoolean(KEY_LAST_WIDGET_IS_REFRESHING, false),
    ).also {
      DebugLog.d(
        "Loaded widget snapshot: primary=${it.primaryValueText}, " +
          "secondary=${it.secondaryValueText}, refreshing=${it.isRefreshing}, updated=${it.updatedText}"
      )
    }
}
