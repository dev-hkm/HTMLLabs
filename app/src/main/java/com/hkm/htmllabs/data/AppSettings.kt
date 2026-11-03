package com.hkm.htmllabs.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent app settings backed by SharedPreferences.
 * Currently used to toggle between in-app HTML viewer and external browser.
 */
object AppSettings {
    private const val PREFS_NAME = "html_labs_settings"
    private const val KEY_OPEN_IN_APP = "open_html_in_app"
    private const val KEY_CARD_PREVIEW_MODE = "card_preview_mode"
    private const val KEY_CLOUD_BACKUP_TOKEN = "cloud_backup_token"
    private const val KEY_LAST_CLOUD_BACKUP_AT = "last_cloud_backup_at"
    private const val KEY_THEME_MODE = "theme_mode"

    enum class ThemeMode {
        SYSTEM, LIGHT, DARK
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Whether the "Open" action should launch the in-app WebView viewer.
     * Defaults to true (in-app viewer) for a smoother workflow.
     */
    fun isOpenInApp(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_OPEN_IN_APP, true)
    }

    fun setOpenInApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_OPEN_IN_APP, value).apply()
    }

    fun isCardPreviewEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_CARD_PREVIEW_MODE, true)
    }

    fun setCardPreviewEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CARD_PREVIEW_MODE, value).apply()
    }

    fun getCloudBackupToken(context: Context): String? {
        return prefs(context).getString(KEY_CLOUD_BACKUP_TOKEN, null)
    }

    fun setCloudBackupToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_CLOUD_BACKUP_TOKEN, token).apply()
    }

    fun clearCloudBackupToken(context: Context) {
        prefs(context).edit().remove(KEY_CLOUD_BACKUP_TOKEN).apply()
    }

    fun getLastCloudBackupAt(context: Context): String? {
        return prefs(context).getString(KEY_LAST_CLOUD_BACKUP_AT, null)
    }

    fun setLastCloudBackupAt(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LAST_CLOUD_BACKUP_AT, value).apply()
    }

    fun getThemeMode(context: Context): ThemeMode {
        val rawValue = prefs(context).getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == rawValue } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
