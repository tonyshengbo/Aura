package com.auracode.assistant.toolwindow.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.auracode.assistant.i18n.AuraCodeBundle
import java.io.FileInputStream

internal data class AttachmentPreviewPayload(
    val assetPath: String,
    val isImage: Boolean,
)

/**
 * Describes the interaction chrome used by the unified attachment preview overlay.
 */
internal data class AttachmentPreviewPresentation(
    val displaysImageContent: Boolean,
    val showsTitle: Boolean,
    val showsSecondaryActions: Boolean,
    val dismissOnScrimClick: Boolean,
)

/**
 * Describes where a click landed inside the preview overlay.
 */
internal enum class AttachmentPreviewClickTarget {
    PREVIEW_CONTENT,
    CONTENT_PADDING,
    SCRIM,
}

/**
 * Resolves the presentation rules shared by timeline and composer previews.
 */
internal fun attachmentPreviewPresentation(isImage: Boolean): AttachmentPreviewPresentation {
    return AttachmentPreviewPresentation(
        displaysImageContent = isImage,
        showsTitle = false,
        showsSecondaryActions = false,
        dismissOnScrimClick = true,
    )
}

/**
 * Resolves whether the preview should dismiss for a given click target.
 */
internal fun shouldDismissAttachmentPreview(target: AttachmentPreviewClickTarget): Boolean {
    if (target == AttachmentPreviewClickTarget.PREVIEW_CONTENT) {
        return false
    }
    return when (target) {
        AttachmentPreviewClickTarget.CONTENT_PADDING,
        AttachmentPreviewClickTarget.SCRIM,
        -> true
        AttachmentPreviewClickTarget.PREVIEW_CONTENT -> false
    }
}

/**
 * Renders a single full-screen overlay for attachment previews without title or action rows.
 */
@Composable
internal fun AttachmentPreviewOverlay(
    palette: DesignPalette,
    preview: AttachmentPreviewPayload?,
    onDismiss: () -> Unit,
) {
    preview ?: return
    val t = assistantUiTokens()
    val bitmap = rememberAttachmentPreviewBitmap(preview.assetPath)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val scrimInteractionSource = remember { MutableInteractionSource() }
        val previewContentInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = scrimInteractionSource,
                    indication = null,
                    onClick = {
                        if (shouldDismissAttachmentPreview(AttachmentPreviewClickTarget.SCRIM)) {
                            onDismiss()
                        }
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (preview.isImage && bitmap != null) {
                    AttachmentPreviewImage(
                        bitmap = bitmap,
                        interactionSource = previewContentInteractionSource,
                    )
                } else {
                    androidx.compose.material.Icon(
                        painter = painterResource("/icons/attach-file.svg"),
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable(
                                interactionSource = previewContentInteractionSource,
                                indication = null,
                                onClick = {},
                            ),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = t.spacing.md, end = t.spacing.md),
            ) {
                AttachmentPreviewCloseButton(
                    palette = palette,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * Sizes the preview image to its visible bounds so surrounding padding still dismisses the overlay.
 */
@Composable
private fun AttachmentPreviewImage(
    bitmap: ImageBitmap,
    interactionSource: MutableInteractionSource,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val imageSize = rememberAttachmentPreviewImageSize(
            bitmap = bitmap,
            maxWidthPx = constraints.maxWidth,
            maxHeightPx = constraints.maxHeight,
        )
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(imageSize)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                ),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Calculates the fitted image size inside the available preview bounds.
 */
@Composable
private fun rememberAttachmentPreviewImageSize(
    bitmap: ImageBitmap,
    maxWidthPx: Int,
    maxHeightPx: Int,
): DpSize {
    val density = LocalDensity.current
    return remember(bitmap, maxWidthPx, maxHeightPx, density) {
        val widthScale = maxWidthPx.toFloat() / bitmap.width.toFloat()
        val heightScale = maxHeightPx.toFloat() / bitmap.height.toFloat()
        val scale = minOf(widthScale, heightScale, 1f)
        with(density) {
            DpSize(
                width = (bitmap.width * scale).toDp(),
                height = (bitmap.height * scale).toDp(),
            )
        }
    }
}

/**
 * Loads preview images from disk for both composer and timeline attachments.
 */
@Composable
internal fun rememberAttachmentPreviewBitmap(path: String): ImageBitmap? {
    return remember(path) {
        runCatching {
            FileInputStream(path).use(::loadImageBitmap)
        }.getOrNull()
    }
}

/**
 * Keeps the preview overlay action limited to a single close affordance.
 */
@Composable
private fun AttachmentPreviewCloseButton(
    palette: DesignPalette,
    onClick: () -> Unit,
) {
    val t = assistantUiTokens()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(palette.topStripBg.copy(alpha = 0.96f), androidx.compose.foundation.shape.RoundedCornerShape(t.spacing.sm))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material.Icon(
            painter = painterResource("/icons/close.svg"),
            contentDescription = AuraCodeBundle.message("common.close"),
            tint = palette.textPrimary,
            modifier = Modifier.size(t.controls.iconLg),
        )
    }
}
