package com.chrogecko.browser.core.engine

import android.net.Uri

/**
 * Everything Gecko asks the user through [org.mozilla.geckoview.GeckoSession.PromptDelegate] and
 * [org.mozilla.geckoview.GeckoSession.PermissionDelegate], modelled as data the Compose layer can
 * render. Each prompt carries an [id] so the answer can be routed back to the pending Gecko object.
 */
sealed interface EnginePrompt {
    val id: Long

    data class Alert(override val id: Long, val message: String) : EnginePrompt

    data class Confirm(override val id: Long, val message: String) : EnginePrompt

    data class TextInput(
        override val id: Long,
        val message: String,
        val value: String,
    ) : EnginePrompt

    data class Choice(
        override val id: Long,
        val message: String,
        val choices: List<String>,
    ) : EnginePrompt

    /** `<input type=file>`: answered with SAF URIs through [EngineSession.confirmFiles]. */
    data class FilePick(
        override val id: Long,
        val mimeTypes: List<String>,
        val multiple: Boolean,
        val capture: Int,
    ) : EnginePrompt

    /** HTTP basic/digest auth. [user] is a hint Gecko may already know. */
    data class Auth(
        override val id: Long,
        val message: String,
        val user: String?,
    ) : EnginePrompt
}

/** A site permission the user has to accept before Gecko hands the capability to the page. */
data class PermissionAsk(
    val id: Long,
    val host: String,
    val kind: Kind,
) {
    enum class Kind { GEOLOCATION, NOTIFICATIONS, CAMERA, MICROPHONE, CAMERA_AND_MICROPHONE }
}

/** Android runtime permissions Gecko needs before the camera or microphone can start. */
data class AndroidPermissionAsk(
    val id: Long,
    val permissions: List<String>,
)

/** A SAF answer for a pending file pick. */
data class FilePickResult(
    val mimeTypes: List<String>,
    val multiple: Boolean,
    val uris: List<Uri>,
)
