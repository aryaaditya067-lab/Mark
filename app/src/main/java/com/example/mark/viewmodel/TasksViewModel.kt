package com.example.mark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mark.model.Task
import com.example.mark.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(
    private val repository: TaskRepository = TaskRepository.instance
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.tasks
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTask(title: String) = viewModelScope.launch {
        runCatching { repository.addTask(title) }
    }

    fun toggleTask(task: Task) = viewModelScope.launch {
        runCatching { repository.toggleTask(task.id, task.isCompleted) }
    }

    fun deleteTask(id: String) = viewModelScope.launch {
        runCatching { repository.deleteTask(id) }
    }
}