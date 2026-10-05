package com.example.mark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mark.repository.MemoryStore
import com.example.mark.repository.SettingsRepository
import com.example.mark.utils.TextToSpeechManager

/**
 * Factory for creating [SettingsViewModel] with a [SettingsRepository].
 */
class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val ttsManager: TextToSpeechManager,
    private val memory: MemoryStore? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(repository, ttsManager, memory) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
