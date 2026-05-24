package com.deepshield.ai.ui.screens.forensics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepshield.ai.data.ScanRepository
import com.deepshield.ai.domain.model.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ForensicsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    scanRepository: ScanRepository
) : ViewModel() {

    private val scanId: String = savedStateHandle["scanId"] ?: ""

    val scanResult: StateFlow<ScanResult?> = scanRepository
        .scanFlow(scanId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
