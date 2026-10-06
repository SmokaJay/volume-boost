package com.maximus.volumeboost

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BoostUiState(
    val boostLevel: Float = 50f,
    val boostEnabled: Boolean = false,
    val softCap: Boolean = false,
    val snackbarMessage: String? = null,
    val needsNotificationPermission: Boolean = false,
    val showBatteryNudge: Boolean = false,
    val streamVolumes: List<LoudnessBoostController.StreamVolume> = emptyList()
)

class BoostViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = BoostPreferences(application)
    private val controller = LoudnessBoostController(application)

    private val _uiState = MutableStateFlow(BoostUiState())
    val uiState: StateFlow<BoostUiState> = _uiState.asStateFlow()

    init {
        prefs.boostLevel
            .stateIn(viewModelScope, SharingStarted.Eagerly, 50f)
            .let { flow ->
                viewModelScope.launch {
                    flow.collect { level ->
                        _uiState.value = _uiState.value.copy(boostLevel = level)
                    }
                }
            }
        viewModelScope.launch {
            prefs.boostEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(boostEnabled = enabled)
            }
        }
        viewModelScope.launch {
            prefs.softCap.collect { cap ->
                _uiState.value = _uiState.value.copy(softCap = cap)
                if (cap && _uiState.value.boostLevel > BoostPreferences.SOFT_CAP_PERCENT) {
                    applyLevel(BoostPreferences.SOFT_CAP_PERCENT, persist = true)
                }
            }
        }
        checkNotificationPermission()
        refreshStreamVolumes()
        refreshBatteryNudge()
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            val state = _uiState.value
            if (state.boostEnabled && !BoostForegroundService.isRunning) {
                BoostForegroundService.start(getApplication(), state.boostLevel)
            }
        }
    }

    fun checkNotificationPermission() {
        val needs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        _uiState.value = _uiState.value.copy(needsNotificationPermission = needs)
    }

    fun refreshBatteryNudge() {
        viewModelScope.launch {
            val dismissed = prefs.batteryNudgeDismissed.first()
            val app = getApplication<Application>()
            val pm = app.getSystemService(PowerManager::class.java)
            val ignoring = pm?.isIgnoringBatteryOptimizations(app.packageName) == true
            _uiState.value = _uiState.value.copy(showBatteryNudge = !ignoring && !dismissed)
        }
    }

    fun onBoostLevelChange(level: Float) {
        val capped = effectiveMax(level)
        _uiState.value = _uiState.value.copy(boostLevel = capped)
        if (_uiState.value.boostEnabled) {
            BoostForegroundService.updateGain(getApplication(), capped)
        }
    }

    fun onBoostLevelChangeFinished(level: Float) {
        val capped = effectiveMax(level)
        _uiState.value = _uiState.value.copy(boostLevel = capped)
        viewModelScope.launch {
            prefs.setBoostLevel(capped)
        }
        if (_uiState.value.boostEnabled) {
            BoostForegroundService.updateGain(getApplication(), capped)
        }
    }

    fun applyPreset(percent: Float) {
        applyLevel(effectiveMax(percent), persist = true)
    }

    private fun applyLevel(level: Float, persist: Boolean) {
        val capped = effectiveMax(level)
        _uiState.value = _uiState.value.copy(boostLevel = capped)
        if (persist) {
            viewModelScope.launch { prefs.setBoostLevel(capped) }
        }
        if (_uiState.value.boostEnabled) {
            BoostForegroundService.updateGain(getApplication(), capped)
        }
    }

    private fun effectiveMax(level: Float): Float {
        val max = if (_uiState.value.softCap) BoostPreferences.SOFT_CAP_PERCENT else 100f
        return level.coerceIn(0f, max)
    }

    fun setBoostEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBoostEnabled(enabled)
        }
        val app = getApplication<Application>()
        if (enabled) {
            BoostForegroundService.start(app, _uiState.value.boostLevel)
            _uiState.value = _uiState.value.copy(boostEnabled = true)
        } else {
            BoostForegroundService.stop(app)
            controller.disable()
            _uiState.value = _uiState.value.copy(boostEnabled = false)
        }
    }

    fun setSoftCap(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSoftCap(enabled)
        }
        _uiState.value = _uiState.value.copy(softCap = enabled)
        if (enabled && _uiState.value.boostLevel > BoostPreferences.SOFT_CAP_PERCENT) {
            applyLevel(BoostPreferences.SOFT_CAP_PERCENT, persist = true)
        }
    }

    fun maxVolumes() {
        controller.maxAllVolumes()
        refreshStreamVolumes()
        showSnackbar("All volume streams set to maximum")
    }

    fun refreshStreamVolumes() {
        _uiState.value = _uiState.value.copy(streamVolumes = controller.getStreamVolumes())
    }

    fun setStreamVolumeFraction(stream: Int, fraction: Float) {
        controller.setStreamVolumeFraction(stream, fraction)
        refreshStreamVolumes()
    }

    fun adjustStreamVolume(stream: Int, delta: Int) {
        val current = _uiState.value.streamVolumes.find { it.stream == stream } ?: return
        controller.setStreamVolume(stream, current.current + delta)
        refreshStreamVolumes()
    }

    fun dismissBatteryNudge() {
        viewModelScope.launch {
            prefs.setBatteryNudgeDismissed(true)
        }
        _uiState.value = _uiState.value.copy(showBatteryNudge = false)
    }

    fun onResume() {
        refreshStreamVolumes()
        refreshBatteryNudge()
    }

    fun showSnackbar(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun onBoostFailed(message: String) {
        viewModelScope.launch {
            prefs.setBoostEnabled(false)
        }
        _uiState.value = _uiState.value.copy(
            boostEnabled = false,
            snackbarMessage = message
        )
    }
}
