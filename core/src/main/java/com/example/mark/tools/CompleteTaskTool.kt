package com.example.mark.tools

import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.repository.TaskRepository
import com.example.mark.router.IntentType

class CompleteTaskTool(private val repository: TaskRepository = TaskRepository.instance) : Tool {

    override val name = "complete_task"

    override val intent = IntentType.COMPLETE_TASK

    override val definition = FunctionDef(
        name = name,
        description = "Mark a task as completed/done.",
        parameters = Parameters(
            properties = mapOf(
                "index" to Property("string", "The number of the task in the list to complete")
            ),
            required = listOf("index")
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val index = request.int("index") 
            ?: return ToolResult.Failure("Which task number should I complete sir?", reason = "missing_arg")

        val success = runCatching { repository.completeTaskByIndex(index - 1) }.getOrDefault(false)
        return if (success) {
            ToolResult.Success("Marked task $index as done.")
        } else {
            ToolResult.Failure("Could not find task number $index.", reason = "not_found")
        }
    }
}
