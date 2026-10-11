package com.metitem.casapic

import androidx.compose.runtime.staticCompositionLocalOf
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.settings.SettingsRepository

data class AppDependencies(
    val db: CasaPicDatabase,
    val settings: SettingsRepository
)

val LocalAppDependencies = staticCompositionLocalOf<AppDependencies> {
    error("No AppDependencies provided")
}
