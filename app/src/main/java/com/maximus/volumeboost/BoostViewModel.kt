package com.maximus.volumeboost

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BoostUiState(
    val boostLevel: Float = 50f,
    val boostEnabled: Boolean = false,
    val snackbarMessage: String? = null,
    val needsNotificationPermission: Boolean = false
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
        checkNotificationPermission()
        // Restore service if boost was left on
        viewModelScope.launch {
            // small delay for flows to emit
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

    fun onBoostLevelChange(level: Float) {
        _uiState.value = _uiState.value.copy(boostLevel = level)
        if (_uiState.value.boostEnabled) {
            BoostForegroundService.updateGain(getApplication(), level)
        }
    }

    fun onBoostLevelChangeFinished(level: Float) {
        viewModelScope.launch {
            prefs.setBoostLevel(level)
        }
        if (_uiState.value.boostEnabled) {
            BoostForegroundService.updateGain(getApplication(), level)
        }
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

    fun maxVolumes() {
        controller.maxAllVolumes()
        showSnackbar("All volume streams set to maximum")
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
