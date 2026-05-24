package com.deepshield.ai.data

import com.deepshield.ai.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor() {
    private val _scanHistory = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanHistory: Flow<List<ScanResult>> = _scanHistory

    fun addScan(result: ScanResult) {
        _scanHistory.value = listOf(result) + _scanHistory.value
    }

    fun clearHistory() {
        _scanHistory.value = emptyList()
    }

    fun scanFlow(scanId: String): Flow<ScanResult?> {
        return scanHistory.map { history -> history.find { it.id == scanId } }
    }
}
