package com.codex.assistant.toolwindow.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.assistant.toolwindow.shared.DesignPalette
import com.codex.assistant.toolwindow.shared.assistantMonospaceStyle
import com.codex.assistant.toolwindow.shared.assistantUiTokens
import com.mikepenz.markdown.m2.Markdown
import com.mikepenz.markdown.m2.markdownColor
import com.mikepenz.markdown.m2.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import java.awt.Desktop
import java.net.URI

@Composable
internal fun TimelineMarkdown(
    text: String,
    palette: DesignPalette,
    modifier: Modifier = Modifier,
) {
    val tokens = assistantUiTokens()
    Markdown(
        content = text,
        colors = markdownColor(
            text = palette.timelinePlainText,
            codeText = palette.markdownCodeText,
            inlineCodeText = palette.markdownCodeText,
            linkText = palette.linkColor,
            codeBackground = palette.markdownCodeBg,
            inlineCodeBackground = palette.markdownInlineCodeBg,
            dividerColor = palette.markdownDivider,
            tableText = palette.timelineCardText,
            tableBackground = palette.markdownTableBg,
        ),
        typography = markdownTypography(
            h1 = androidx.compose.material.MaterialTheme.typography.h5.copy(color = palette.timelineCardText),
            h2 = androidx.compose.material.MaterialTheme.typography.h6.copy(color = palette.timelineCardText),
            h3 = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelineCardText, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            h4 = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelineCardText, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            h5 = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelineCardText),
            h6 = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelineCardText),
            text = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelinePlainText),
            paragraph = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelinePlainText),
            ordered = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelinePlainText),
            bullet = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelinePlainText),
            list = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.timelinePlainText),
            quote = androidx.compose.material.MaterialTheme.typography.body2.copy(color = palette.markdownQuoteText),
            code = assistantMonospaceStyle(tokens).copy(color = palette.markdownCodeText),
            inlineCode = assistantMonospaceStyle(tokens).copy(color = palette.markdownCodeText),
            link = androidx.compose.material.MaterialTheme.typography.body1.copy(color = palette.linkColor),
        ),
        padding = markdownPadding(
            block = tokens.spacing.sm,
            list = tokens.spacing.sm,
            listItemTop = 4.dp,
            listItemBottom = 5.dp,
            listIndent = tokens.markdown.quoteIndent + 4.dp,
            codeBlock = androidx.compose.foundation.layout.PaddingValues(tokens.markdown.codePadding),
            blockQuote = androidx.compose.foundation.layout.PaddingValues(horizontal = tokens.markdown.quoteIndent + 3.dp, vertical = 3.dp),
            blockQuoteText = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
            blockQuoteBar = androidx.compose.foundation.layout.PaddingValues.Absolute(left = 0.dp, top = 4.dp, right = tokens.spacing.sm, bottom = 4.dp),
        ),
        dimens = markdownDimens(
            codeBackgroundCornerSize = tokens.spacing.sm,
            blockQuoteThickness = 3.dp,
            tableCellPadding = tokens.markdown.tableCellPadding,
            tableCornerSize = tokens.spacing.sm,
        ),
        modifier = modifier,
    )
}

internal fun isSafeHttpUrl(url: String): Boolean {
    return try {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

internal fun openExternalUrlSafely(url: String) {
    if (!isSafeHttpUrl(url)) return
    runCatching {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(URI(url))
        }
    }
}
