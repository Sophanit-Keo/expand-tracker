package com.expensetracker.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A short confirmation shown at the bottom of the app, optionally with an action such as Undo. */
data class AppMessage(
    val text: UiText,
    val actionLabel: UiText? = null,
    val action: (suspend () -> Unit)? = null,
)

/**
 * App-wide messages. Screens that close right after an action (saving in the editor, for example)
 * post here, and the main scaffold shows them on whatever screen comes next.
 */
object AppMessages {
    private val _events = MutableSharedFlow<AppMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    fun post(message: AppMessage) {
        _events.tryEmit(message)
    }
}
