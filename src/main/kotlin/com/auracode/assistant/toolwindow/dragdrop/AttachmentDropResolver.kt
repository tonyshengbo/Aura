package com.auracode.assistant.toolwindow.dragdrop

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * 把拖拽载荷解析成「可添加为附件」的本地文件路径。
 *
 * 解析约定：
 * - 只接受本地文件系统中的普通文件：目录、不存在的路径一律忽略；
 * - 非本地资源（库文件、远程 VFS 等拿不到 `java.io.File` 的条目）视为不可附件，直接忽略；
 * - 负载形式按 file list → uri list 顺序依次尝试，取第一个能产出有效路径的结果；
 * - 结果保持原始顺序，并按绝对路径去重。
 */
internal object AttachmentDropResolver {
    private const val FILE_LIST_MIME = "application/x-java-file-list"
    private const val URI_LIST_MIME = "text/uri-list"

    /**
     * 判断拖拽载荷是否「可能」带来本地文件。
     *
     * 只做 flavor 判断、不触发任何 IO，因此可以在拖拽过程中被高频调用；
     * 纯文本拖拽（例如编辑器选区）不参与，避免出现误导性的拖拽高亮。
     */
    fun isFileDrag(transferable: Transferable?): Boolean {
        if (transferable == null) return false
        return transferable.transferDataFlavors.any { flavor ->
            flavor.matchesMimeType(FILE_LIST_MIME) || flavor.matchesMimeType(URI_LIST_MIME)
        }
    }

    /**
     * 解析拖拽载荷中的本地文件路径；没有任何可附件文件时返回空列表。
     */
    fun resolve(transferable: Transferable?): List<String> {
        if (transferable == null) return emptyList()
        val extractors = listOf(::readFileList, ::readUriList)
        extractors.forEach { extractor ->
            val candidates = runCatching { extractor(transferable) }.getOrDefault(emptyList())
            val paths = normalize(candidates)
            if (paths.isNotEmpty()) return paths
        }
        return emptyList()
    }

    /** 标准 file list 负载（IDE 工程视图、Finder、资源管理器）。 */
    private fun readFileList(transferable: Transferable): List<String> {
        val flavor = transferable.flavorFor(FILE_LIST_MIME) ?: return emptyList()
        val data = runCatching { transferable.getTransferData(flavor) }.getOrNull()
        return (data as? List<*>).orEmpty().mapNotNull { (it as? File)?.path }
    }

    /** `text/uri-list` 负载，逐行一个 URI。 */
    private fun readUriList(transferable: Transferable): List<String> {
        val flavor = transferable.flavorFor(URI_LIST_MIME) ?: return emptyList()
        val text = runCatching { transferable.getTransferData(flavor) }.getOrNull() as? String
        return text.orEmpty().toPathCandidates().mapNotNull { raw ->
            runCatching { Path.of(URI(raw)) }.getOrNull()?.toString()
        }
    }

    /** 逐行拆出候选路径：忽略空行与 uri-list 的注释行。 */
    private fun String.toPathCandidates(): List<String> {
        return lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }

    /** 过滤掉非本地文件、目录与重复项，统一为绝对路径。 */
    private fun normalize(candidates: List<String>): List<String> {
        return candidates.asSequence()
            .map { it.trim().removeSurrounding("\"") }
            .filter(String::isNotEmpty)
            .mapNotNull(::toLocalFilePath)
            .distinct()
            .toList()
    }

    private fun toLocalFilePath(raw: String): String? {
        val path = runCatching { Path.of(raw) }.getOrNull() ?: return null
        val isRegularFile = runCatching { Files.isRegularFile(path) }.getOrDefault(false)
        if (!isRegularFile) return null
        return runCatching { path.toAbsolutePath().normalize().toString() }.getOrNull()
    }

    /** 按 MIME 主类型匹配 flavor，避开不同实现声明的表示类差异。 */
    private fun Transferable.flavorFor(mimeType: String): DataFlavor? {
        return transferDataFlavors.firstOrNull { it.matchesMimeType(mimeType) }
    }

    private fun DataFlavor.matchesMimeType(mimeType: String): Boolean {
        return this.mimeType.startsWith(mimeType, ignoreCase = true)
    }
}
