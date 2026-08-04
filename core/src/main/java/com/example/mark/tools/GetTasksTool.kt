package com.example.mark.tools

import com.example.mark.assistant.Tool
import com.example.mark.assistant.ToolRequest
import com.example.mark.assistant.ToolResult
import com.example.mark.network.FunctionDef
import com.example.mark.network.Parameters
import com.example.mark.network.Property
import com.example.mark.repository.TaskRepository
import com.example.mark.router.IntentType

class GetTasksTool(private val repository: TaskRepository = TaskRepository.instance) : Tool {

    override val name = "get_tasks"

    override val intent = IntentType.GET_TASKS

    override val definition = FunctionDef(
        name = name,
        description = "Retrieve the user's current tasks.",
        parameters = Parameters(
            properties = mapOf(
                "only_pending" to Property("boolean", "If true, only return uncompleted tasks")
            )
        )
    )

    override suspend fun execute(request: ToolRequest): ToolResult {
        val onlyPending = request.boolean("only_pending", default = true)

        val tasks = runCatching { repository.getTasksOnce(onlyPending) }.getOrNull()
            ?: return ToolResult.Failure("Failed to retrieve tasks", reason = "db_error")

        if (tasks.isEmpty()) {
            return ToolResult.Success("No ${if (onlyPending) "pending " else ""}tasks found.")
        }

        val text = tasks.joinToString("\n") { "- ${it.title}${if (it.isCompleted) " [Done]" else ""}" }
        return ToolResult.Success(
            text = text,
            data = mapOf("count" to tasks.size.toString())
        )
    }
}
