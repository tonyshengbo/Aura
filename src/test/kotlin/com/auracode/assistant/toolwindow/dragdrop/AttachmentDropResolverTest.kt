package com.auracode.assistant.toolwindow.dragdrop

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentDropResolverTest {
    @Test
    fun `file list drag resolves regular files in order`() {
        val root = createTempDirectory("drop-root")
        val first = root.resolve("first.kt").also { it.writeText("first") }
        val second = root.resolve("second.md").also { it.writeText("second") }

        val paths = AttachmentDropResolver.resolve(fileListTransferable(first.toFile(), second.toFile()))

        assertEquals(listOf(first.toString(), second.toString()), paths)
    }

    @Test
    fun `directories and missing paths are ignored`() {
        val root = createTempDirectory("drop-root")
        val directory = root.resolve("nested").also { it.createDirectories() }
        val file = root.resolve("kept.txt").also { it.writeText("kept") }

        val paths = AttachmentDropResolver.resolve(
            fileListTransferable(directory.toFile(), root.resolve("missing.txt").toFile(), file.toFile()),
        )

        assertEquals(listOf(file.toString()), paths)
    }

    @Test
    fun `duplicated entries collapse into a single path`() {
        val file = createTempDirectory("drop-root").resolve("same.txt").also { it.writeText("same") }

        val paths = AttachmentDropResolver.resolve(
            fileListTransferable(file.toFile(), file.toFile(), file.toFile()),
        )

        assertEquals(listOf(file.toString()), paths)
    }

    @Test
    fun `empty file list falls back to uri list payload`() {
        val file = createTempDirectory("drop-root").resolve("uri.txt").also { it.writeText("uri") }

        val paths = AttachmentDropResolver.resolve(
            fileListAndUriListTransferable(emptyList(), "${file.toUri()}\nhttp://example.com/remote.txt\n"),
        )

        assertEquals(listOf(file.toString()), paths)
    }

    @Test
    fun `uri list payload ignores comments and non file schemes`() {
        val file = createTempDirectory("drop-root").resolve("kept.txt").also { it.writeText("kept") }

        val paths = AttachmentDropResolver.resolve(
            uriListTransferable("# comment\n\n${file.toUri()}\nhttps://example.com/a.txt\n"),
        )

        assertEquals(listOf(file.toString()), paths)
    }

    @Test
    fun `plain text drag is not treated as a file drag`() {
        val file = createTempDirectory("drop-root").resolve("text.txt").also { it.writeText("text") }

        val transferable = StringSelection(file.toString())

        assertFalse(AttachmentDropResolver.isFileDrag(transferable))
        assertEquals(emptyList(), AttachmentDropResolver.resolve(transferable))
    }

    @Test
    fun `file list flavor marks the drag as droppable`() {
        assertTrue(AttachmentDropResolver.isFileDrag(fileListTransferable(File("/tmp"))))
        assertFalse(AttachmentDropResolver.isFileDrag(null))
    }

    @Test
    fun `plain text drag is ignored when mixed with an empty file list`() {
        val transferable = object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> =
                arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                flavor in getTransferDataFlavors()

            override fun getTransferData(flavor: DataFlavor): Any {
                return if (flavor == DataFlavor.javaFileListFlavor) emptyList<File>() else "not a path"
            }
        }

        assertTrue(AttachmentDropResolver.isFileDrag(transferable))
        assertEquals(emptyList(), AttachmentDropResolver.resolve(transferable))
    }

    /** 模拟 IDE / 系统文件拖拽：只提供标准 file list 负载。 */
    private fun fileListTransferable(vararg files: File): Transferable = fileListAndUriListTransferable(files.toList(), null)

    /** 模拟 `text/uri-list` 负载（X11 文件管理器等）。 */
    private fun uriListTransferable(uriList: String): Transferable = fileListAndUriListTransferable(null, uriList)

    /**
     * 构造同时声明 file list / uri list 的载荷；
     * 传 null 表示该 flavor 不出现在声明里，传空列表表示声明了但拿不到本地文件（非本地资源）。
     */
    private fun fileListAndUriListTransferable(files: List<File>?, uriList: String?): Transferable {
        val flavors = buildList {
            if (files != null) add(DataFlavor.javaFileListFlavor)
            if (uriList != null) add(URI_LIST_FLAVOR)
        }.toTypedArray()

        return object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = flavors

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

            override fun getTransferData(flavor: DataFlavor): Any {
                return when {
                    files != null && flavor == DataFlavor.javaFileListFlavor -> files
                    uriList != null && flavor == URI_LIST_FLAVOR -> uriList
                    else -> throw UnsupportedOperationException("unsupported flavor: $flavor")
                }
            }
        }
    }

    private companion object {
        val URI_LIST_FLAVOR = DataFlavor("text/uri-list;class=java.lang.String")
    }
}
