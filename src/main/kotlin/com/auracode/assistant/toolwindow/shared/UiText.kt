package com.auracode.assistant.toolwindow.shared

import com.auracode.assistant.i18n.AuraCodeBundle

internal sealed interface UiText {
    data class Bundle(
        val key: String,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Raw(
        val value: String,
    ) : UiText

    companion object {
        fun bundle(key: String, vararg args: Any): UiText = Bundle(key = key, args = args.toList())

        fun raw(value: String): UiText = Raw(value)
    }
}

internal fun UiText.resolve(): String {
    return when (this) {
        is UiText.Bundle -> AuraCodeBundle.message(key, *args.toTypedArray())
        is UiText.Raw -> value
    }
}
