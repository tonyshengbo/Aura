package com.codex.assistant.toolwindow.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.codex.assistant.provider.CodexModelCatalog
import com.codex.assistant.settings.SavedAgentDefinition
import com.codex.assistant.toolwindow.eventing.AppEvent
import com.codex.assistant.toolwindow.eventing.ComposerMode
import com.codex.assistant.toolwindow.eventing.ComposerReasoning
import com.codex.assistant.toolwindow.eventing.UiIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.name
import kotlin.math.min

internal data class ContextEntry(
    val path: String,
    val displayName: String,
    val tailPath: String = "",
)

internal data class MentionEntry(
    val id: String,
    val path: String,
    val displayName: String,
    val start: Int = 0,
    val endExclusive: Int = 0,
)

internal enum class AttachmentKind {
    TEXT,
    IMAGE,
    BINARY,
}

internal data class AttachmentEntry(
    val id: String,
    val path: String,
    val displayName: String,
    val tailPath: String = "",
    val kind: AttachmentKind,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
)

internal data class MentionLookupRequest(
    val query: String,
    val documentVersion: Long,
)

internal data class ComposerLookupRequest(
    val mention: MentionLookupRequest? = null,
    val agentQuery: String? = null,
    val documentVersion: Long = 0L,
)

internal data class AgentContextEntry(
    val id: String,
    val name: String,
    val prompt: String,
)

internal data class ComposerAreaState(
    val document: TextFieldValue = TextFieldValue(""),
    val documentVersion: Long = 0L,
    val selectedMode: ComposerMode = ComposerMode.AUTO,
    val selectedModel: String = CodexModelCatalog.defaultModel,
    val selectedReasoning: ComposerReasoning = ComposerReasoning.MEDIUM,
    val modeMenuExpanded: Boolean = false,
    val modelMenuExpanded: Boolean = false,
    val reasoningMenuExpanded: Boolean = false,
    val manualContextEntries: List<ContextEntry> = emptyList(),
    val attachments: List<AttachmentEntry> = emptyList(),
    val focusedContextEntry: ContextEntry? = null,
    val contextEntries: List<ContextEntry> = emptyList(),
    val agentEntries: List<AgentContextEntry> = emptyList(),
    val previewAttachmentId: String? = null,
    val mentionEntries: List<MentionEntry> = emptyList(),
    val mentionQuery: String = "",
    val mentionSuggestions: List<ContextEntry> = emptyList(),
    val mentionPopupVisible: Boolean = false,
    val activeMentionIndex: Int = 0,
    val agentQuery: String = "",
    val agentSuggestions: List<SavedAgentDefinition> = emptyList(),
    val agentPopupVisible: Boolean = false,
    val activeAgentIndex: Int = 0,
) {
    val inputText: String
        get() = normalizePromptBody(removeMentionRanges(document.text, mentionEntries))

    fun serializedPrompt(): String {
        val mentionBlock = mentionEntries.sortedBy { it.start }.joinToString("\n") { it.path.trim() }.trim()
        val textBlock = normalizePromptBody(removeMentionRanges(document.text, mentionEntries))
        return when {
            mentionBlock.isBlank() -> textBlock
            textBlock.isBlank() -> mentionBlock
            else -> "$mentionBlock\n\n$textBlock"
        }
    }

    fun serializedSystemInstructions(): List<String> = agentEntries.mapNotNull { it.prompt.trim().takeIf(String::isNotBlank) }

    fun hasPromptContent(): Boolean = serializedPrompt().isNotBlank() || agentEntries.isNotEmpty()
}

internal class ComposerAreaStore {
    companion object {
        const val MAX_CONTEXT_FILES: Int = 10
        const val MAX_ATTACHMENTS: Int = 10
        const val MAX_IMAGE_BYTES: Long = 20L * 1024L * 1024L
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        private val TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "md", "txt", "json", "xml", "yaml", "yml", "gradle",
            "js", "ts", "tsx", "jsx", "py", "sql", "properties", "toml", "sh", "log",
        )
    }

    private val _state = MutableStateFlow(ComposerAreaState())
    val state: StateFlow<ComposerAreaState> = _state.asStateFlow()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.UiIntentPublished -> {
                when (val intent = event.intent) {
                    is UiIntent.UpdateDocument -> applyDocumentUpdate(intent.value)
                    is UiIntent.InputChanged -> applyDocumentUpdate(
                        TextFieldValue(intent.value, TextRange(intent.value.length)),
                    )
                    UiIntent.ToggleModeMenu -> _state.value = _state.value.copy(
                        modeMenuExpanded = !_state.value.modeMenuExpanded,
                        modelMenuExpanded = false,
                        reasoningMenuExpanded = false,
                    )
                    is UiIntent.SelectMode -> _state.value = _state.value.copy(
                        selectedMode = intent.mode,
                        modeMenuExpanded = false,
                    )
                    UiIntent.ToggleModelMenu -> _state.value = _state.value.copy(
                        modelMenuExpanded = !_state.value.modelMenuExpanded,
                        modeMenuExpanded = false,
                        reasoningMenuExpanded = false,
                    )
                    is UiIntent.SelectModel -> _state.value = _state.value.copy(
                        selectedModel = intent.model,
                        modelMenuExpanded = false,
                    )
                    UiIntent.ToggleReasoningMenu -> _state.value = _state.value.copy(
                        reasoningMenuExpanded = !_state.value.reasoningMenuExpanded,
                        modeMenuExpanded = false,
                        modelMenuExpanded = false,
                    )
                    is UiIntent.SelectReasoning -> _state.value = _state.value.copy(
                        selectedReasoning = intent.reasoning,
                        reasoningMenuExpanded = false,
                    )
                    is UiIntent.AddAttachments -> addAttachments(intent.paths)
                    is UiIntent.AddContextFiles -> addContextFiles(intent.paths)
                    is UiIntent.RemoveAttachment -> _state.value = _state.value.copy(
                        attachments = _state.value.attachments.filterNot { it.id == intent.id },
                        previewAttachmentId = _state.value.previewAttachmentId?.takeUnless { it == intent.id },
                    ).withResolvedContextEntries()
                    is UiIntent.OpenAttachmentPreview -> _state.value = _state.value.copy(previewAttachmentId = intent.id)
                    UiIntent.CloseAttachmentPreview -> _state.value = _state.value.copy(previewAttachmentId = null)
                    is UiIntent.UpdateFocusedContextFile -> _state.value = _state.value.copy(
                        focusedContextEntry = intent.path?.takeIf { it.isNotBlank() }?.let(::toContextEntry),
                    ).withResolvedContextEntries()
                    is UiIntent.RemoveContextFile -> _state.value = _state.value.copy(
                        manualContextEntries = _state.value.manualContextEntries.filterNot { it.path == intent.path },
                        focusedContextEntry = _state.value.focusedContextEntry?.takeUnless { it.path == intent.path },
                    ).withResolvedContextEntries()
                    is UiIntent.SelectMentionFile -> addMention(intent.path)
                    is UiIntent.RemoveMentionFile -> removeMention(intent.id)
                    is UiIntent.SelectAgent -> addAgent(intent.agent)
                    is UiIntent.RemoveSelectedAgent -> _state.value = _state.value.copy(
                        agentEntries = _state.value.agentEntries.filterNot { it.id == intent.id },
                    )
                    UiIntent.MoveMentionSelectionNext -> {
                        val size = _state.value.mentionSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeMentionIndex = (_state.value.activeMentionIndex + 1) % size)
                        }
                    }
                    UiIntent.MoveMentionSelectionPrevious -> {
                        val size = _state.value.mentionSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeMentionIndex = (_state.value.activeMentionIndex - 1 + size) % size)
                        }
                    }
                    UiIntent.DismissMentionPopup -> _state.value = _state.value.copy(
                        mentionPopupVisible = false,
                        mentionSuggestions = emptyList(),
                        mentionQuery = "",
                        activeMentionIndex = 0,
                    )
                    UiIntent.MoveAgentSelectionNext -> {
                        val size = _state.value.agentSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeAgentIndex = (_state.value.activeAgentIndex + 1) % size)
                        }
                    }
                    UiIntent.MoveAgentSelectionPrevious -> {
                        val size = _state.value.agentSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeAgentIndex = (_state.value.activeAgentIndex - 1 + size) % size)
                        }
                    }
                    UiIntent.DismissAgentPopup -> _state.value = _state.value.copy(
                        agentPopupVisible = false,
                        agentSuggestions = emptyList(),
                        agentQuery = "",
                        activeAgentIndex = 0,
                    )
                    else -> Unit
                }
            }
            is AppEvent.MentionSuggestionsUpdated -> {
                val current = _state.value
                if (
                    event.documentVersion != current.documentVersion ||
                    event.query != current.mentionQuery ||
                    current.document.composition != null
                ) {
                    return
                }
                _state.value = current.copy(
                    mentionSuggestions = event.suggestions,
                    mentionPopupVisible = event.suggestions.isNotEmpty(),
                    activeMentionIndex = 0,
                )
            }

            is AppEvent.AgentSuggestionsUpdated -> {
                val current = _state.value
                if (
                    event.documentVersion != current.documentVersion ||
                    event.query != current.agentQuery ||
                    current.document.composition != null
                ) {
                    return
                }
                _state.value = current.copy(
                    agentSuggestions = event.suggestions,
                    agentPopupVisible = event.suggestions.isNotEmpty(),
                    activeAgentIndex = 0,
                )
            }

            is AppEvent.PromptAccepted -> {
                _state.value = _state.value.copy(
                    document = TextFieldValue(""),
                    documentVersion = _state.value.documentVersion + 1,
                    attachments = emptyList(),
                    previewAttachmentId = null,
                    mentionEntries = emptyList(),
                    mentionQuery = "",
                    mentionSuggestions = emptyList(),
                    mentionPopupVisible = false,
                    activeMentionIndex = 0,
                    agentQuery = "",
                    agentSuggestions = emptyList(),
                    agentPopupVisible = false,
                    activeAgentIndex = 0,
                )
            }

            AppEvent.ConversationReset -> _state.value = ComposerAreaState()

            else -> Unit
        }
    }

    fun applyDocumentUpdate(value: TextFieldValue): ComposerLookupRequest? {
        val current = _state.value
        val normalizedValue = normalizeMentionSelection(current.document, value, current.mentionEntries)
        val syncedMentions = syncMentions(current.mentionEntries, current.document.text, normalizedValue)
        val next = current.nextDocumentState(document = normalizedValue, mentionEntries = syncedMentions)
        _state.value = next
        return next.activeLookup()
    }

    private fun addAttachments(paths: List<String>, clearMention: Boolean = false) {
        if (paths.isEmpty()) return
        val current = _state.value
        val existingByPath = current.attachments.associateBy { it.path }.toMutableMap()
        paths.mapNotNull { toAttachmentEntry(it) }.forEach { candidate ->
            existingByPath.putIfAbsent(candidate.path, candidate)
        }
        val attachments = existingByPath.values.take(MAX_ATTACHMENTS)
        val nextDocument = if (clearMention) {
            val clearedText = clearTrailingMentionQuery(current.document, current.mentionEntries)
            current.document.copy(text = clearedText, selection = TextRange(clearedText.length))
        } else {
            current.document
        }
        val next = current.copy(
            attachments = attachments,
            document = nextDocument,
            mentionQuery = if (clearMention) "" else current.mentionQuery,
            mentionSuggestions = if (clearMention) emptyList() else current.mentionSuggestions,
            mentionPopupVisible = if (clearMention) false else current.mentionPopupVisible,
            activeMentionIndex = if (clearMention) 0 else current.activeMentionIndex,
        )
        _state.value = next.withResolvedContextEntries()
    }

    private fun addContextFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        val current = _state.value
        val additions = paths.map(::toContextEntry)
        _state.value = current.copy(
            manualContextEntries = mergeManualEntries(current.manualContextEntries, additions),
        ).withResolvedContextEntries()
    }

    private fun addMention(path: String) {
        val current = _state.value
        val entry = toContextEntry(path)
        val inserted = insertMentionLabel(
            document = current.document,
            mentions = current.mentionEntries,
            mentionPath = entry.path,
            displayName = entry.displayName,
        ) ?: return
        val nextValue = inserted.first
        val newMention = inserted.second
        _state.value = current.nextDocumentState(
            document = nextValue,
            mentionEntries = syncMentions(current.mentionEntries, current.document.text, nextValue) + newMention,
        )
    }

    private fun removeMention(id: String) {
        val current = _state.value
        val removed = removeMentionById(current.document, current.mentionEntries, id) ?: return
        _state.value = current.nextDocumentState(document = removed.first, mentionEntries = removed.second)
    }

    private fun addAgent(agent: SavedAgentDefinition) {
        val current = _state.value
        val queryMatch = findAgentQuery(current.document, current.mentionEntries)
        val nextText = clearTrailingAgentQuery(current.document, current.mentionEntries)
        val nextDocument = current.document.copy(
            text = nextText,
            selection = TextRange(queryMatch?.start ?: nextText.length),
        )
        val existing = current.agentEntries.associateBy { it.id }.toMutableMap()
        existing.putIfAbsent(
            agent.id,
            AgentContextEntry(id = agent.id, name = agent.name, prompt = agent.prompt),
        )
        _state.value = current.copy(
            document = nextDocument,
            documentVersion = current.documentVersion + 1,
            agentEntries = existing.values.toList(),
            agentQuery = "",
            agentSuggestions = emptyList(),
            agentPopupVisible = false,
            activeAgentIndex = 0,
            mentionSuggestions = emptyList(),
            mentionPopupVisible = false,
            mentionQuery = if (findMentionQuery(nextDocument, current.mentionEntries) != null) current.mentionQuery else "",
        )
    }

    private fun mergeManualEntries(existing: List<ContextEntry>, additions: List<ContextEntry>): List<ContextEntry> {
        val merged = LinkedHashMap<String, ContextEntry>()
        existing.forEach { merged[it.path] = it }
        additions.forEach { merged.putIfAbsent(it.path, it) }
        return merged.values.take(MAX_CONTEXT_FILES)
    }

    private fun ComposerAreaState.withResolvedContextEntries(): ComposerAreaState {
        val merged = LinkedHashMap<String, ContextEntry>()
        focusedContextEntry?.let { merged[it.path] = it }
        manualContextEntries.forEach { merged.putIfAbsent(it.path, it) }
        return copy(
            contextEntries = merged.values.take(MAX_CONTEXT_FILES),
        )
    }

    private fun clearTrailingMentionQuery(document: TextFieldValue, mentions: List<MentionEntry>): String {
        val query = findMentionQuery(document, mentions) ?: return document.text
        return buildString {
            append(document.text.substring(0, query.start))
            append(document.text.substring(query.end))
        }.trimEnd()
    }

    private fun clearTrailingAgentQuery(document: TextFieldValue, mentions: List<MentionEntry>): String {
        val query = findAgentQuery(document, mentions) ?: return document.text
        return buildString {
            append(document.text.substring(0, query.start))
            append(document.text.substring(query.end))
        }
    }

    private fun ComposerAreaState.nextDocumentState(
        document: TextFieldValue,
        mentionEntries: List<MentionEntry>,
    ): ComposerAreaState {
        val query = findMentionQuery(document, mentionEntries)?.query.orEmpty()
        val agentQuery = findAgentQuery(document, mentionEntries)?.query.orEmpty()
        val keepSuggestions = query.isNotBlank() &&
            query == this.mentionQuery &&
            this.mentionSuggestions.isNotEmpty() &&
            document.composition == null
        val keepAgentSuggestions = agentQuery == this.agentQuery &&
            this.agentSuggestions.isNotEmpty() &&
            document.composition == null &&
            findAgentQuery(document, mentionEntries) != null

        return copy(
            document = document,
            documentVersion = documentVersion + 1,
            mentionEntries = mentionEntries,
            mentionQuery = query,
            mentionSuggestions = if (keepSuggestions) mentionSuggestions else emptyList(),
            mentionPopupVisible = keepSuggestions && mentionPopupVisible,
            activeMentionIndex = if (keepSuggestions) activeMentionIndex.coerceAtMost((mentionSuggestions.size - 1).coerceAtLeast(0)) else 0,
            agentQuery = agentQuery,
            agentSuggestions = if (keepAgentSuggestions) agentSuggestions else emptyList(),
            agentPopupVisible = keepAgentSuggestions && agentPopupVisible,
            activeAgentIndex = if (keepAgentSuggestions) activeAgentIndex.coerceAtMost((agentSuggestions.size - 1).coerceAtLeast(0)) else 0,
        )
    }

    private fun ComposerAreaState.activeLookup(): ComposerLookupRequest? {
        if (document.composition != null) return null
        findAgentQuery(document, mentionEntries)?.let { match ->
            return ComposerLookupRequest(agentQuery = match.query, documentVersion = documentVersion)
        }
        val query = mentionQuery
        if (findMentionQuery(document, mentionEntries) == null) return null
        return ComposerLookupRequest(
            mention = MentionLookupRequest(query = query, documentVersion = documentVersion),
            documentVersion = documentVersion,
        )
    }

    private fun toContextEntry(path: String): ContextEntry {
        val normalized = path.trim()
        val p = runCatching { Path.of(normalized) }.getOrNull()
        val name = p?.name ?: normalized.substringAfterLast('/').substringAfterLast('\\').ifBlank { normalized }
        val parent = p?.parent?.fileName?.toString().orEmpty()
        return ContextEntry(path = normalized, displayName = name, tailPath = parent)
    }

    private fun toAttachmentEntry(path: String): AttachmentEntry? {
        val normalized = path.trim()
        if (normalized.isBlank()) return null
        val p = runCatching { Path.of(normalized) }.getOrNull() ?: return null
        val name = p.name.ifBlank { normalized.substringAfterLast('/').substringAfterLast('\\') }
        val parent = p.parent?.fileName?.toString().orEmpty()
        val size = runCatching { Files.size(p) }.getOrDefault(0L)
        val ext = name.substringAfterLast('.', "").lowercase()
        val kind = when {
            ext in IMAGE_EXTENSIONS -> {
                if (size > MAX_IMAGE_BYTES) return null
                AttachmentKind.IMAGE
            }
            ext in TEXT_EXTENSIONS -> AttachmentKind.TEXT
            isProbablyTextFile(p, size) -> AttachmentKind.TEXT
            else -> AttachmentKind.BINARY
        }
        val mime = runCatching { Files.probeContentType(p).orEmpty() }.getOrDefault("")
        return AttachmentEntry(
            id = UUID.randomUUID().toString(),
            path = normalized,
            displayName = name,
            tailPath = parent,
            kind = kind,
            sizeBytes = size,
            mimeType = mime,
        )
    }

    private fun isProbablyTextFile(path: Path, size: Long): Boolean {
        if (size <= 0L) return false
        val sampleSize = min(size, 4096L).toInt()
        val bytes = runCatching {
            Files.newInputStream(path).use { input ->
                val sample = ByteArray(sampleSize)
                val read = input.read(sample)
                if (read <= 0) ByteArray(0) else sample.copyOf(read)
            }
        }.getOrDefault(ByteArray(0))
        if (bytes.isEmpty()) return false
        if (bytes.any { it == 0.toByte() }) return false
        return runCatching {
            String(bytes, StandardCharsets.UTF_8)
            true
        }.getOrDefault(false)
    }
}
