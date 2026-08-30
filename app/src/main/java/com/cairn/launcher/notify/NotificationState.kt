package com.cairn.launcher.notify

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What Cairn knows about one package's current notifications.
 *
 * [level] is the whole point of the rule under an icon: a 0..1 fraction from the notification's
 * own progress extras, so any app posting a progress notification drives its rule with no
 * per-app code at all.
 */
data class Notice(
    val packageName: String,
    val key: String,
    val count: Int,
    val title: String?,
    val text: String?,
    val level: Float?,
    val replyAction: Notification.Action?
) {
    val canReply: Boolean get() = replyAction?.remoteInputs?.isNotEmpty() == true
}

/** Process-wide, because the listener service and the UI are in the same process but not linked. */
object NotificationState {

    private val _notices = MutableStateFlow<Map<String, Notice>>(emptyMap())
    val notices: StateFlow<Map<String, Notice>> = _notices

    /** package -> 0..1 media position, refreshed by the listener while something plays. */
    private val _media = MutableStateFlow<Map<String, Float>>(emptyMap())
    val media: StateFlow<Map<String, Float>> = _media

    fun replaceAll(map: Map<String, Notice>) {
        _notices.value = map
    }

    fun setMedia(map: Map<String, Float>) {
        _media.value = map
    }

    /** The value the rule under an icon draws. Notification progress wins over media position. */
    fun levelFor(packageName: String): Float? =
        _notices.value[packageName]?.level ?: _media.value[packageName]

    fun noticeFor(packageName: String): Notice? = _notices.value[packageName]

    /**
     * Fires the app's own reply intent. The message is sent by the app, through the same path
     * Wear OS and Android Auto use, not by us pretending to be it.
     */
    fun sendReply(context: Context, notice: Notice, message: String): Boolean {
        val action = notice.replyAction ?: return false
        val inputs = action.remoteInputs ?: return false
        if (inputs.isEmpty() || message.isBlank()) return false
        return runCatching {
            val intent = Intent()
            val bundle = android.os.Bundle()
            inputs.forEach { bundle.putCharSequence(it.resultKey, message) }
            RemoteInput.addResultsToIntent(inputs, intent, bundle)
            action.actionIntent.send(context, 0, intent)
            true
        }.getOrDefault(false)
    }
}
