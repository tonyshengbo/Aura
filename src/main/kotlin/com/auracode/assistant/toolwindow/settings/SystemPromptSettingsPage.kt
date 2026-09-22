package com.auracode.assistant.toolwindow.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shell.SidePanelAreaState
import com.auracode.assistant.toolwindow.shared.assistantUiTokens

/**
 * 全局角色设置页：只包含启用开关与角色正文。
 *
 * 关掉开关时正文编辑器置灰但保持可见，方便用户查看与随时重新启用；
 * 正文输入即持久化，因此页面不提供保存按钮。
 */
@Composable
internal fun SystemPromptSettingsPage(
    p: DesignPalette,
    state: SidePanelAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    val contentInputState = rememberSettingsTextInputState(state.systemPromptContent)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(t.spacing.lg),
    ) {
        SettingsToggleField(
            p = p,
            title = AuraCodeBundle.message("settings.role.enabled.label"),
            description = AuraCodeBundle.message("settings.role.enabled.hint"),
            checked = state.systemPromptEnabled,
            onCheckedChange = { onIntent(UiIntent.EditSystemPromptEnabled(it)) },
        )
        SettingsField(
            p = p,
            title = AuraCodeBundle.message("settings.role.content.label"),
            description = AuraCodeBundle.message("settings.role.content.hint"),
        ) {
            // 与智能体编辑器保持同一高度，长角色定义也能在页内滚动查看。
            SettingsTextInput(
                p = p,
                value = contentInputState.value,
                onValueChange = {
                    contentInputState.value = it
                    onIntent(UiIntent.EditSystemPromptContent(it.text))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                singleLine = false,
                minLines = 12,
                maxLines = 12,
                enabled = state.systemPromptEnabled,
            )
        }
    }
}
