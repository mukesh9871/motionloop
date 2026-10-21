package com.motionloop.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.motionloop.app.BuildConfig
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.pro.Entitlements
import com.motionloop.app.domain.pro.ProPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SettingsState(
    val defaultResolution: ExportResolution = ExportResolution.FULL_HD,
    val keepScreenOn: Boolean = true,
    /** Debug builds only: pretend to own Pro to test locked features. */
    val simulatePro: Boolean = false,
)

/**
 * App preferences in SharedPreferences (a handful of values, no extra dependency needed).
 * The first read is lazy and happens on the main thread when the first screen asks.
 * The file is tiny, so that costs a few milliseconds once.
 */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("motionloop_settings", Context.MODE_PRIVATE) }

    private val _state by lazy { MutableStateFlow(read()) }
    val state: StateFlow<SettingsState> get() = _state.asStateFlow()

    private val _simulatePro by lazy { MutableStateFlow(_state.value.simulatePro) }
    val simulatePro: StateFlow<Boolean> get() = _simulatePro.asStateFlow()

    fun setDefaultResolution(r: ExportResolution) = edit { it.copy(defaultResolution = r) }
    fun setKeepScreenOn(on: Boolean) = edit { it.copy(keepScreenOn = on) }
    fun setSimulatePro(on: Boolean) = edit { it.copy(simulatePro = on && BuildConfig.DEBUG) }

    private fun edit(change: (SettingsState) -> SettingsState) {
        val next = change(_state.value)
        _state.value = next
        _simulatePro.value = next.simulatePro
        // apply() writes to disk asynchronously, so this is safe on the main thread.
        prefs.edit()
            .putString(KEY_RESOLUTION, next.defaultResolution.name)
            .putBoolean(KEY_SCREEN_ON, next.keepScreenOn)
            .putBoolean(KEY_SIMULATE_PRO, next.simulatePro)
            .apply()
    }

    private fun read() = SettingsState(
        // Only HD and Full HD can be defaults: 4K depends on the encoder and on Pro.
        defaultResolution = prefs.getString(KEY_RESOLUTION, null)
            ?.let { name -> ExportResolution.entries.firstOrNull { it.name == name } }
            ?.takeIf { it != ExportResolution.UHD }
            ?: ExportResolution.FULL_HD,
        keepScreenOn = prefs.getBoolean(KEY_SCREEN_ON, true),
        simulatePro = BuildConfig.DEBUG && prefs.getBoolean(KEY_SIMULATE_PRO, false),
    )

    private companion object {
        const val KEY_RESOLUTION = "default_resolution"
        const val KEY_SCREEN_ON = "keep_screen_on"
        const val KEY_SIMULATE_PRO = "simulate_pro"
    }
}

/**
 * Entitlements before billing exists: Pro only through the debug switch.
 * Replace with a Play Billing implementation in [com.motionloop.app.core.di.AppModule].
 */
@Singleton
class LocalEntitlements @Inject constructor(private val settings: AppSettings) : Entitlements {

    override val isPro: StateFlow<Boolean> get() = settings.simulatePro

    override val purchasesAvailable: Boolean = false

    override suspend fun purchase(plan: ProPlan): Result<Unit> =
        Result.failure(UnsupportedOperationException("Purchases are not available yet"))

    override suspend fun restore(): Result<Boolean> = Result.success(settings.state.value.simulatePro)
}
