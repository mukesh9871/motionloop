package com.motionloop.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.data.settings.AppSettings
import com.motionloop.app.data.settings.SettingsState
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.pro.Entitlements
import com.motionloop.app.domain.pro.ProPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val entitlements: Entitlements,
) : ViewModel() {

    val state: StateFlow<SettingsState> = settings.state
    val isPro: StateFlow<Boolean> = entitlements.isPro
    val purchasesAvailable: Boolean = entitlements.purchasesAvailable

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setDefaultResolution(r: ExportResolution) = settings.setDefaultResolution(r)
    fun setKeepScreenOn(on: Boolean) = settings.setKeepScreenOn(on)
    fun setSimulatePro(on: Boolean) = settings.setSimulatePro(on)

    fun purchase(plan: ProPlan) {
        viewModelScope.launch {
            entitlements.purchase(plan).onFailure { e -> _message.value = e.message ?: "Purchase failed" }
        }
    }

    fun restore() {
        viewModelScope.launch {
            entitlements.restore()
                .onSuccess { owned -> _message.value = if (owned) "Pro is active." else "No Pro purchase found." }
                .onFailure { e -> _message.value = e.message ?: "Could not restore purchases" }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
