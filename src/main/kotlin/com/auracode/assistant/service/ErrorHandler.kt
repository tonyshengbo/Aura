package com.auracode.assistant.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ErrorHandler(private val project: Project) {

    fun handleError(error: Throwable, context: String): String {
        val message = when (error) {
            is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            is java.io.IOException -> "Network error. Check your connection."
            else -> "An error occurred: ${error.message}"
        }
        return message
    }

    companion object {
        fun getInstance(project: Project): ErrorHandler =
            project.getService(ErrorHandler::class.java)
    }
}
