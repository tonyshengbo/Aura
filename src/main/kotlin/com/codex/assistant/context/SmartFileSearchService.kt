package com.codex.assistant.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

@Service(Service.Level.PROJECT)
class SmartFileSearchService(private val project: Project) {

    fun searchByName(query: String, limit: Int = 20): List<String> {
        val scope = GlobalSearchScope.projectScope(project)
        val files = FilenameIndex.getAllFilesByExt(project, "", scope)
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(limit)
        return files.mapNotNull { it.path }
    }

    companion object {
        fun getInstance(project: Project): SmartFileSearchService =
            project.getService(SmartFileSearchService::class.java)
    }
}
