package com.auracode.assistant.toolwindow.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantMonospaceStyle
import com.auracode.assistant.toolwindow.shared.assistantUiTokens
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
    TimelineSelectableText {
        Markdown(
            content = text,
            colors = markdownColor(
                text = palette.timelinePlainText,
                codeText = palette.markdownCodeText,
                inlineCodeText = palette.markdownCodeText,
                linkText = palette.linkColor,
                codeBackground = palette.markdownCodeBg,
                inlineCodeBackground = palette.markdownInlineCodeBg,
                dividerColor = palette.markdownDivider.copy(alpha = 0.82f),
                tableText = palette.timelineCardText,
                tableBackground = palette.markdownTableBg,
            ),
            typography = markdownTypography(
                h1 = androidx.compose.material.MaterialTheme.typography.h5.copy(
                    color = palette.timelineCardText,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                ),
                h2 = androidx.compose.material.MaterialTheme.typography.h6.copy(
                    color = palette.timelineCardText,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                ),
                h3 = androidx.compose.material.MaterialTheme.typography.subtitle1.copy(
                    color = palette.timelineCardText,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                ),
                h4 = androidx.compose.material.MaterialTheme.typography.subtitle1.copy(
                    color = palette.timelineCardText,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                ),
                h5 = androidx.compose.material.MaterialTheme.typography.body1.copy(
                    color = palette.timelineCardText,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                ),
                h6 = androidx.compose.material.MaterialTheme.typography.body2.copy(
                    color = palette.timelineCardText,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                ),
                text = androidx.compose.material.MaterialTheme.typography.body1.copy(
                    color = palette.timelinePlainText,
                    lineHeight = 20.sp,
                ),
                paragraph = androidx.compose.material.MaterialTheme.typography.body1.copy(
                    color = palette.timelinePlainText,
                    lineHeight = 20.sp,
                ),
                ordered = androidx.compose.material.MaterialTheme.typography.body1.copy(
                    color = palette.timelinePlainText,
                    lineHeight = 20.sp,
                ),
                bullet = androidx.compose.material.MaterialTheme.typography.body1.copy(
                    color = palette.timelinePlainText,
                    lineHeight = 20.sp,
                ),
                list = androidx.compose.material.MaterialTheme.typography.body1.copy(
                    color = palette.timelinePlainText,
                    lineHeight = 20.sp,
                ),
                quote = androidx.compose.material.MaterialTheme.typography.body2.copy(
                    color = palette.markdownQuoteText,
                    lineHeight = 18.sp,
                ),
                code = assistantMonospaceStyle(tokens).copy(
                    color = palette.markdownCodeText,
                    lineHeight = 18.sp,
                ),
                inlineCode = assistantMonospaceStyle(tokens).copy(
                    color = palette.markdownCodeText,
                    fontWeight = FontWeight.Medium,
                ),
                link = androidx.compose.material.MaterialTheme.typography.body1.copy(
                    color = palette.linkColor,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    lineHeight = 20.sp,
                ),
                textLink = TextLinkStyles(
                    style = SpanStyle(
                        color = palette.linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium,
                    ),
                ),
            ),
            padding = markdownPadding(
                block = tokens.markdown.blockSpacing,
                list = tokens.markdown.listSpacing,
                listItemTop = tokens.markdown.listItemTop,
                listItemBottom = tokens.markdown.listItemBottom,
                listIndent = tokens.markdown.listIndent,
                codeBlock = androidx.compose.foundation.layout.PaddingValues(tokens.markdown.codePadding),
                blockQuote = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = tokens.markdown.quoteIndent + 2.dp,
                    vertical = 2.dp,
                ),
                blockQuoteText = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp),
                blockQuoteBar = androidx.compose.foundation.layout.PaddingValues.Absolute(
                    left = 0.dp,
                    top = 2.dp,
                    right = tokens.spacing.xs,
                    bottom = 2.dp,
                ),
            ),
            dimens = markdownDimens(
                codeBackgroundCornerSize = tokens.markdown.codeCorner,
                blockQuoteThickness = tokens.markdown.quoteThickness,
                tableCellPadding = tokens.markdown.tableCellPadding,
                tableCornerSize = tokens.markdown.codeCorner,
            ),
            modifier = modifier,
        )
    }
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
