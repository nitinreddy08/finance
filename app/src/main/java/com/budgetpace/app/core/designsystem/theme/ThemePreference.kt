package com.budgetpace.app.core.designsystem.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Spec §13: Settings → Appearance → Theme (System / Light / Dark), persisted across launches. */
@Singleton
class ThemePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(loadMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun loadMode(): ThemeMode {
        val stored = prefs.getString(KEY_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
    }
}
