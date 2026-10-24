package com.smartwatch.launcher.ui.themes

import android.content.Context
import android.content.SharedPreferences
import com.smartwatch.launcher.model.AppInfo

enum class AppTheme(val id: Int, val title: String, val subtitle: String) {
    BUBBLE_CLOUD(0, "Bubble Cloud", "Apple Watch Hex Cloud"),
    ROTARY_WHEEL(1, "Rotary Wheel", "Curved Rim Menu"),
    VORTEX_TUNNEL(2, "3D Vortex", "Perspective Spiral"),
    HONEYCOMB_MATRIX(3, "Honeycomb Grid", "Structured Hex Matrix"),
    CURVED_MAGNIFIER(4, "Curved List", "Pixel Bezel List"),
    CARD_DECK(5, "Card Deck", "3D Stacking Cards");

    companion object {
        fun fromId(id: Int): AppTheme = values().firstOrNull { it.id == id } ?: BUBBLE_CLOUD
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
        get() = AppTheme.fromId(prefs.getInt("selected_app_theme", AppTheme.BUBBLE_CLOUD.id))
        set(value) {
            prefs.edit().putInt("selected_app_theme", value.id).apply()
        }
}