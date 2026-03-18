package com.codex.assistant.context

internal object MentionFileWhitelist {
    private val allowedExtensions = setOf(
        "kt", "kts", "java", "groovy", "scala", "py", "js", "jsx", "ts", "tsx",
        "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "m", "mm", "cs", "go", "rs", "swift", "rb", "php",
        "sh", "bash", "zsh", "fish", "ps1", "sql",
        "vue", "svelte", "css", "scss", "sass", "less", "html", "htm",
        "xml", "json", "jsonc", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties",
        "gradle", "editorconfig", "env",
        "md", "markdown", "txt", "rst", "adoc", "log",
        "cmake", "mk", "makefile", "dockerfile", "tf", "tfvars", "proto",
    )

    private val allowedFileNames = setOf(
        "dockerfile",
        "makefile",
        "jenkinsfile",
        ".gitignore",
        ".gitattributes",
        ".editorconfig",
        ".npmrc",
        ".yarnrc",
        ".prettierrc",
        ".eslintrc",
    )

    private val blockedExtensions = setOf(
        "class", "jar", "war", "zip", "tar", "gz", "7z",
        "png", "jpg", "jpeg", "gif", "webp", "pdf",
        "so", "dylib", "dll", "exe", "bin",
    )

    fun allowPath(path: String): Boolean {
        val normalized = path.trim()
        if (normalized.isBlank()) return false
        val fileName = normalized.substringAfterLast('/').substringAfterLast('\\')
        if (fileName.isBlank()) return false
        val lowerFileName = fileName.lowercase()
        if (lowerFileName in allowedFileNames) return true

        val extension = lowerFileName.substringAfterLast('.', "")
        if (extension.isBlank()) return false
        if (extension in blockedExtensions) return false
        return extension in allowedExtensions
    }
}
