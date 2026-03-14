package com.codex.assistant.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

@Service(Service.Level.PROJECT)
class GitContextProvider(private val project: Project) {

    fun getUncommittedChanges(): String? {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.repositories.firstOrNull() ?: return null

        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.addParameters("HEAD")

        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.outputAsJoinedString else null
    }

    fun getStagedChanges(): String? {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.repositories.firstOrNull() ?: return null

        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.addParameters("--cached")

        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.outputAsJoinedString else null
    }

    companion object {
        fun getInstance(project: Project): GitContextProvider =
            project.getService(GitContextProvider::class.java)
    }
}
