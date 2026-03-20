package com.auracode.assistant.provider

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal enum class CodexEnvironmentStatus {
    CONFIGURED,
    DETECTED,
    MISSING,
    FAILED,
}

internal data class CodexEnvironmentCheckResult(
    val codexPath: String = "",
    val nodePath: String = "",
    val codexStatus: CodexEnvironmentStatus = CodexEnvironmentStatus.MISSING,
    val nodeStatus: CodexEnvironmentStatus = CodexEnvironmentStatus.MISSING,
    val appServerStatus: CodexEnvironmentStatus = CodexEnvironmentStatus.MISSING,
    val message: String = "",
)

internal data class CodexEnvironmentResolution(
    val codexPath: String,
    val nodePath: String?,
    val shellEnvironment: Map<String, String>,
    val environmentOverrides: Map<String, String>,
)

internal class CodexEnvironmentDetector(
    private val shellEnvironmentLoader: () -> Map<String, String> = ::loadShellEnvironment,
    private val commonSearchPaths: List<String> = DEFAULT_SEARCH_PATHS,
) {
    @Volatile
    private var cachedShellEnvironment: Map<String, String>? = null

    fun autoDetect(
        configuredCodexPath: String,
        configuredNodePath: String,
    ): CodexEnvironmentCheckResult {
        return inspect(
            configuredCodexPath = configuredCodexPath,
            configuredNodePath = configuredNodePath,
            refreshShellEnvironment = true,
            testAppServer = false,
        )
    }

    fun testEnvironment(
        configuredCodexPath: String,
        configuredNodePath: String,
    ): CodexEnvironmentCheckResult {
        return inspect(
            configuredCodexPath = configuredCodexPath,
            configuredNodePath = configuredNodePath,
            refreshShellEnvironment = true,
            testAppServer = true,
        )
    }

    fun resolveForLaunch(
        configuredCodexPath: String,
        configuredNodePath: String,
    ): CodexEnvironmentResolution {
        val shellEnvironment = shellEnvironment(refresh = false)
        val codex = resolveExecutable(
            configuredPath = configuredCodexPath,
            commandName = "codex",
            shellEnvironment = shellEnvironment,
        )
        val node = resolveExecutable(
            configuredPath = configuredNodePath,
            commandName = "node",
            shellEnvironment = shellEnvironment,
        )
        val codexPath = codex.path?.takeIf { it.isNotBlank() }
            ?: configuredCodexPath.trim().takeIf { it.isNotBlank() }
            ?: "codex"
        return CodexEnvironmentResolution(
            codexPath = codexPath,
            nodePath = node.path?.takeIf { it.isNotBlank() },
            shellEnvironment = shellEnvironment,
            environmentOverrides = buildLaunchEnvironmentOverrides(
                shellEnvironment = shellEnvironment,
                nodePath = node.path,
            ),
        )
    }

    private fun inspect(
        configuredCodexPath: String,
        configuredNodePath: String,
        refreshShellEnvironment: Boolean,
        testAppServer: Boolean,
    ): CodexEnvironmentCheckResult {
        val shellEnvironment = shellEnvironment(refresh = refreshShellEnvironment)
        val codex = resolveExecutable(
            configuredPath = configuredCodexPath,
            commandName = "codex",
            shellEnvironment = shellEnvironment,
        )
        val node = resolveExecutable(
            configuredPath = configuredNodePath,
            commandName = "node",
            shellEnvironment = shellEnvironment,
        )
        val appServerProbe: Pair<CodexEnvironmentStatus, String> = when {
            codex.path.isNullOrBlank() -> CodexEnvironmentStatus.MISSING to buildSummary(
                codex.status,
                node.status,
                CodexEnvironmentStatus.MISSING,
            )
            node.path.isNullOrBlank() -> CodexEnvironmentStatus.FAILED to buildSummary(
                codex.status,
                node.status,
                CodexEnvironmentStatus.FAILED,
            )
            !testAppServer -> if (codex.status == CodexEnvironmentStatus.CONFIGURED || node.status == CodexEnvironmentStatus.CONFIGURED) {
                CodexEnvironmentStatus.CONFIGURED to buildSummary(
                    codex.status,
                    node.status,
                    CodexEnvironmentStatus.CONFIGURED,
                )
            } else {
                CodexEnvironmentStatus.DETECTED to buildSummary(
                    codex.status,
                    node.status,
                    CodexEnvironmentStatus.DETECTED,
                )
            }
            else -> testAppServerStartup(
                codexPath = codex.path,
                nodePath = node.path,
                shellEnvironment = shellEnvironment,
            )
        }
        return CodexEnvironmentCheckResult(
            codexPath = codex.path.orEmpty(),
            nodePath = node.path.orEmpty(),
            codexStatus = codex.status,
            nodeStatus = node.status,
            appServerStatus = appServerProbe.first,
            message = appServerProbe.second,
        )
    }

    private fun shellEnvironment(refresh: Boolean): Map<String, String> {
        if (refresh) {
            cachedShellEnvironment = null
        }
        return cachedShellEnvironment ?: runCatching(shellEnvironmentLoader).getOrNull().orEmpty().also {
            cachedShellEnvironment = it
        }
    }

    private fun resolveExecutable(
        configuredPath: String,
        commandName: String,
        shellEnvironment: Map<String, String>,
    ): ResolvedExecutable {
        val normalized = configuredPath.trim()
        if (normalized.isNotBlank()) {
            resolveExactPath(normalized)?.let {
                return ResolvedExecutable(it, CodexEnvironmentStatus.CONFIGURED)
            }
            if (!normalized.contains(File.separatorChar)) {
                searchExecutable(normalized, shellEnvironment["PATH"]).let { found ->
                    if (found != null) return ResolvedExecutable(found, CodexEnvironmentStatus.CONFIGURED)
                }
            }
            return ResolvedExecutable(null, CodexEnvironmentStatus.FAILED)
        }

        searchExecutable(commandName, shellEnvironment["PATH"])?.let {
            return ResolvedExecutable(it, CodexEnvironmentStatus.DETECTED)
        }
        searchExecutable(commandName, commonSearchPaths.joinToString(File.pathSeparator))?.let {
            return ResolvedExecutable(it, CodexEnvironmentStatus.DETECTED)
        }
        return ResolvedExecutable(null, CodexEnvironmentStatus.MISSING)
    }

    private fun resolveExactPath(path: String): String? {
        val file = File(path)
        return file.takeIf { it.exists() && it.isFile && it.canExecute() }?.absolutePath
    }

    private fun searchExecutable(
        executable: String,
        pathValue: String?,
    ): String? {
        return pathValue
            ?.split(File.pathSeparator)
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { File(it, executable) }
            ?.firstOrNull { candidate -> candidate.exists() && candidate.isFile && candidate.canExecute() }
            ?.absolutePath
    }

    private fun testAppServerStartup(
        codexPath: String,
        nodePath: String,
        shellEnvironment: Map<String, String>,
    ): Pair<CodexEnvironmentStatus, String> {
        val overrides = buildLaunchEnvironmentOverrides(shellEnvironment = shellEnvironment, nodePath = nodePath)
        val stderrLines = mutableListOf<String>()
        val process = runCatching {
            ProcessBuilder(listOf(codexPath, "app-server"))
                .apply {
                    environment().putAll(overrides)
                    redirectErrorStream(false)
                }
                .start()
        }.getOrElse {
            return CodexEnvironmentStatus.FAILED to "Failed to start codex app-server: ${it.message.orEmpty()}"
        }

        val reader = thread(start = true, isDaemon = true) {
            runCatching {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(stderrLines) {
                            stderrLines += line
                        }
                    }
                }
            }
        }

        return try {
            if (process.waitFor(APP_SERVER_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                val stderr = synchronized(stderrLines) { stderrLines.joinToString("\n") }.trim()
                if (stderr.contains("node: No such file or directory", ignoreCase = true)) {
                    CodexEnvironmentStatus.FAILED to "Aura Code can start, but Node is not visible to the app-server."
                } else {
                    CodexEnvironmentStatus.FAILED to "codex app-server exited early${stderr.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
                }
            } else {
                CodexEnvironmentStatus.DETECTED to "Aura Code, Node, and the built-in app-server look available."
            }
        } finally {
            process.destroy()
            process.waitFor(200, TimeUnit.MILLISECONDS)
            if (process.isAlive) {
                process.destroyForcibly()
            }
            reader.interrupt()
        }
    }

    private fun buildSummary(
        codexStatus: CodexEnvironmentStatus,
        nodeStatus: CodexEnvironmentStatus,
        appServerStatus: CodexEnvironmentStatus,
    ): String {
        val codexSummary = "Codex ${statusWord(codexStatus)}"
        val nodeSummary = "Node ${statusWord(nodeStatus)}"
        val appServerSummary = "App Server ${statusWord(appServerStatus)}"
        return listOf(codexSummary, nodeSummary, appServerSummary).joinToString(" · ")
    }

    private fun statusWord(status: CodexEnvironmentStatus): String = when (status) {
        CodexEnvironmentStatus.CONFIGURED -> "configured"
        CodexEnvironmentStatus.DETECTED -> "detected"
        CodexEnvironmentStatus.MISSING -> "missing"
        CodexEnvironmentStatus.FAILED -> "failed"
    }

    private data class ResolvedExecutable(
        val path: String?,
        val status: CodexEnvironmentStatus,
    )

    private companion object {
        private val DEFAULT_SEARCH_PATHS = listOf(
            "/opt/homebrew/bin",
            "/usr/local/bin",
            "/usr/bin",
            "/bin",
        )
        private const val APP_SERVER_PROBE_TIMEOUT_MS = 1500L
    }
}

internal fun buildLaunchEnvironmentOverrides(
    shellEnvironment: Map<String, String>,
    nodePath: String?,
): Map<String, String> {
    val overrides = linkedMapOf<String, String>()
    SHELL_ENV_KEYS.forEach { key ->
        shellEnvironment[key]?.takeIf { it.isNotBlank() }?.let { overrides[key] = it }
    }
    val currentPath = overrides["PATH"].orEmpty().ifBlank { System.getenv("PATH").orEmpty() }
    val nodeDir = nodePath?.trim()
        ?.takeIf { it.isNotBlank() && it.contains(File.separatorChar) }
        ?.let { File(it).parentFile?.absolutePath }
        ?.takeIf { it.isNotBlank() }
    val mergedPath = listOfNotNull(nodeDir, currentPath.takeIf { it.isNotBlank() })
        .distinct()
        .joinToString(File.pathSeparator)
        .ifBlank { null }
    if (mergedPath != null) {
        overrides["PATH"] = mergedPath
    }
    return overrides
}

private val SHELL_ENV_KEYS = listOf(
    "PATH",
    "HOME",
    "SHELL",
    "NVM_DIR",
    "FNM_DIR",
    "ASDF_DIR",
    "VOLTA_HOME",
    "PNPM_HOME",
)

private fun loadShellEnvironment(): Map<String, String> {
    val shell = System.getenv("SHELL").takeUnless { it.isNullOrBlank() } ?: "/bin/zsh"
    val process = ProcessBuilder(shell, "-lc", "env").start()
    return try {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator) to line.substring(separator + 1)
                }
            }.toMap()
        }
    } finally {
        process.waitFor(2, TimeUnit.SECONDS)
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }
}
