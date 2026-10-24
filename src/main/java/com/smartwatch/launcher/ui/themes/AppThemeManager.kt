package com.smartwatch.launcher.ui.themes

import android.content.Context
import android.content.SharedPreferences
import com.smartwatch.launcher.model.AppInfo

enum class AppTheme(val id: Int, val title: String) {
    ROTARY_WHEEL(0, "Rotary Wheel"),
    VORTEX_TUNNEL(1, "3D Vortex"),
    HONEYCOMB_MATRIX(2, "Honeycomb Grid"),
    CURVED_MAGNIFIER(3, "Curved List"),
    CARD_DECK(4, "Card Deck");

    companion object {
        fun fromId(id: Int): AppTheme = values().firstOrNull { it.id == id } ?: ROTARY_WHEEL
    }
}

interface AppThemeView {
    fun setApps(apps: List<AppInfo>)
    fun resetToCenter(animated: Boolean = true)
}

class AppThemeManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartwatch_app_themes", Context.MODE_PRIVATE)

    var currentTheme: AppTheme
        get() = AppTheme.fromId(prefs.getInt("selected_app_theme", AppTheme.ROTARY_WHEEL.id))
        set(value) {
            prefs.edit().putInt("selected_app_theme", value.id).apply()
        }
}