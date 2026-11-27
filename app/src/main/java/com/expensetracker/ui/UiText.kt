package com.expensetracker.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text described by a ViewModel and turned into words by the screen, so it's always in the current
 * language (ViewModels outlive a language switch). Arguments may themselves be [UiText].
 */
sealed interface UiText {
    data class Plain(val value: String) : UiText

    class Res(@param:StringRes val id: Int, vararg val args: Any) : UiText

    class Plural(@param:PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText

    fun resolve(context: Context): String = when (this) {
        is Plain -> value
        is Res -> context.getString(id, *args.map { it.resolveArg(context) }.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.map { it.resolveArg(context) }.toTypedArray())
    }

    private fun Any.resolveArg(context: Context): Any = if (this is UiText) resolve(context) else this
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
