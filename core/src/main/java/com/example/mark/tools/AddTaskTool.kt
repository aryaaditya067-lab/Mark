package com.example.mark.tools

import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.repository.TaskRepository
import com.example.mark.router.IntentType

class AddTaskTool(private val repository: TaskRepository = TaskRepository.instance) : Tool {

    override val name = "add_task"

    override val intent = IntentType.ADD_TASK

    override val definition = FunctionDef(
        name = name,
        description = "Add a new task or to-do item for the user.",
        parameters = Parameters(
            properties = mapOf(
                "content" to Property("string", "The task description, e.g. 'Buy milk'")
            ),
            required = listOf("content")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val title = request.string("content") ?: return ToolResult.Failure("What should I add to your tasks sir?", reason = "missing_arg")
        val dueDate = "" // Simplified for now

        return runCatching { repository.addTask(title, dueDate) }.fold(
            onSuccess = { ToolResult.Success("Task added: $title") },
            onFailure = { ToolResult.Failure("Failed to add task: ${it.message}", reason = "db_error") }
        )
    }
}
