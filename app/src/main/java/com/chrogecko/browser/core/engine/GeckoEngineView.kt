package com.chrogecko.browser.core.engine

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Hosts a GeckoSession inside Compose. The GeckoView instance is kept alive across tab switches:
 * recreating it makes Compose attach the new view before the old one is released, which Gecko
 * rejects with "Display already acquired".
 */
@Composable
fun GeckoEngineView(
    session: EngineSession,
    modifier: Modifier = Modifier,
    androidAutofillEnabled: Boolean = true,
) {
    val boundSession = remember { arrayOfNulls<EngineSession>(1) }
    val importantForAutofillMode = if (androidAutofillEnabled) {
        View.IMPORTANT_FOR_AUTOFILL_YES
    } else {
        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GeckoView(context).apply {
                autofillEnabled = true
                importantForAutofill = importantForAutofillMode
                setSession(session.geckoSession)
                session.attachView(this)
                boundSession[0] = session
            }
        },
        update = { view ->
            if (view.importantForAutofill != importantForAutofillMode) {
                view.importantForAutofill = importantForAutofillMode
            }
            if (view.session !== session.geckoSession) {
                boundSession[0]?.detachView(view)
                // Only setSession() after a successful release. Reflectively stealing the display
                // leaves the old view with a stale session whose later release throws, and can
                // trigger "Display already acquired" on the next setSession().
                val released = runCatching { view.releaseSession() }
                if (released.isSuccess) view.setSession(session.geckoSession)
            }
            session.attachView(view)
            boundSession[0] = session
        },
        onRelease = { view ->
            boundSession[0]?.detachView(view)
            boundSession[0] = null
            runCatching { view.releaseSession() }
        },
    )
}

/** Hosts a raw [GeckoSession] such as a WebExtension action popup. */
@Composable
fun RawGeckoView(
    session: GeckoSession,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> GeckoView(context).apply { setSession(session) } },
        update = { view ->
            if (view.session !== session) {
                runCatching { view.releaseSession() }
                view.setSession(session)
            }
        },
        onRelease = { view -> runCatching { view.releaseSession() } },
    )
}
