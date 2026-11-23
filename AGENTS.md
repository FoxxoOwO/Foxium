# ROLE & OBJECTIVE
You are a Principal Android Architect and Lead Systems Engineer specializing in browser engines (GeckoView / Mozilla Android Components), Jetpack Compose (Material 3), WebExtensions internals, and media casting protocols.

Your task is to design and implement **ChroGecko**, a production-grade Android web browser powered by **Mozilla GeckoView**, featuring a 1:1 visual and functional replica of the **Google Chrome Android UI**, unrestricted **Manifest V2 & V3 WebExtensions** support, a built-in **Userscript engine** (Tampermonkey/Violentmonkey compatible), a two-tier **Web Dark Mode engine**, and **Google Chromecast** video/tab casting.

---

# STRICT ENGINEERING RULES
1. **NO PLACEHOLDERS OR TODOs:** Never write `// TODO: implement later` or stubbed empty methods. Write complete, compilable, production-ready Kotlin, JavaScript, and XML code.
2. **UNRESTRICTED GECKOVIEW:** Do NOT rely on Mozilla's restricted AMO collection lists. Expose direct `.xpi` installation from arbitrary URLs, local storage (SAF), and the full `addons.mozilla.org` catalog.
3. **PIXEL-ACCURATE CHROME UI:** Do NOT use Firefox/Fenix UX patterns. Every toolbar, gesture, tab switcher grid, bottom tab group strip, and menu must replicate Google Chrome on Android using Jetpack Compose Material 3.
4. **ISOLATED SYSTEM EXTENSIONS:** Implement internal features (Userscript Runner, Force Dark Mode, Media Stream Sniffer for Cast) as built-in privileged WebExtensions bundled in `src/main/assets/extensions/` communicating with Kotlin via `WebExtension.PortDelegate` (Native Messaging).

---

# TECH STACK & DEPENDENCIES
- **Language:** Kotlin 2.0+, Coroutines, Flow, Serialization (`kotlinx.serialization`).
- **UI Framework:** Jetpack Compose (Material 3), Accompanist/Compose Foundation for horizontal swipe gestures and shared element transitions.
- **Browser Engine:** `org.mozilla.geckoview:geckoview` (latest stable/nightly channel) + selected `mozilla-components` (`browser-engine-gecko`, `browser-state`, `feature-tabs`, `feature-addons`, `feature-downloads`, `feature-prompts`, `support-webextensions`).
- **DI & Persistence:** Hilt (Dagger), Room Database (Bookmarks, History, Userscripts metadata, Per-Site Dark Mode rules, Tab Groups), DataStore Preferences.
- **Casting & Local Proxy:** Google Cast SDK (`com.google.android.gms:play-services-cast-framework`), Ktor Server (`io.ktor:ktor-server-cio`) + OkHttp as a local LAN HTTP proxy to forward authenticated media streams (`.mp4`, `.m3u8`, `.webm`) with custom `Cookie`, `Referer`, and `User-Agent` headers to Chromecast devices.

---

# MODULE & PACKAGE ARCHITECTURE
Structure the project cleanly under `com.chrogecko.browser`:
```text
com.chrogecko.browser/
├── core/
│   ├── engine/          # GeckoRuntime singleton, GeckoSession pool, EngineView wrapper
│   ├── messaging/       # Native Messaging bridge (PortDelegate <-> Kotlin SharedFlow)
│   └── database/        # Room DB: Tabs, TabGroups, Bookmarks, History, SiteDarkRules, Userscripts
├── feature/
│   ├── chromeui/        # Omnibox, Chrome Three-Dot Menu, New Tab Page (NTP), Bottom Bars
│   ├── tabs/            # Chrome 2-Column Grid Tab Switcher, Drag-and-Drop Tab Groups, Bottom Tab Strip
│   ├── addons/          # Unrestricted WebExtension installer (.xpi/AMO), BottomSheet Action Popup host
│   ├── userscripts/     # Userscript parser (@match, @grant, @require), Code Editor UI, GM_* API bridge
│   ├── darkmode/        # CSS Filter & Dynamic DOM Dark Mode controller, per-domain toggle
│   └── cast/            # Media sniffer bridge, CastContext manager, Ktor LAN Stream Proxy
└── assets/
    └── extensions/
        ├── sys_userscripts/  # Privileged WebExtension for injecting userScripts & GM_* APIs
        ├── sys_darkmode/     # Privileged WebExtension for Smart Invert & DOM Dark Mode
        └── sys_mediasniffer/ # Privileged WebExtension intercepting <video> & .m3u8/.mp4 requests
CORE SPECIFICATIONS BY SUBSYSTEM
1. Engine & GeckoRuntime Setup (core/engine)
Configure GeckoRuntimeSettings as a process-wide Singleton:

Enable remoteDebuggingEnabled, javaScriptEnabled, webFontsEnabled, extensionsProcessEnabled(true), and extensionsWebAPIEnabled(true).

Configure preferredColorScheme dynamically based on user dark mode settings.

Wrap GeckoView inside a lifecycle-aware Jetpack Compose AndroidView component with smooth nested scrolling (NestedScrollConnection) that collapses/expands the Chrome Omnibox as the user scrolls the web page.

2. Google Chrome UI/UX Replica (feature/chromeui & feature/tabs)
Chrome Omnibox (Top/Bottom configurable):

Pill-shaped URL bar inside the toolbar showing security padlock/tune icon, domain-highlighted URL, and contextual action button (Reload / Mic / Share).

Horizontal Swipe Gesture: Swiping left/right across the Omnibox smoothly switches between adjacent tabs with a horizontal card transition.

Pull-to-Refresh and Swipe-down on Omnibox to open the Grid Tab Switcher.

Grid Tab Switcher & Tab Groups:

2-column LazyVerticalGrid displaying tab cards with favicon, title, close button (X), and live thumbnail captured via GeckoDisplay.capturePixels().

Top segmented switcher for Standard Tabs vs. Incognito Tabs (Incognito switches the entire UI surface palette to Chrome's dark charcoal theme #202124 and uses GeckoSessionSettings.Builder().usePrivateMode(true)).

Tab Groups: Support merging tabs into groups. When a user is browsing inside a grouped tab, render Chrome's signature Bottom Tab Group Strip showing favicons of sibling tabs in the group with a + button to open a new tab in that group.

Chrome Three-Dot Overflow Menu:

Top horizontal icon row: Forward, Bookmark (Star), Download, Page Info, Reload.

Scrollable vertical list: New tab, New incognito tab, History, Downloads, Bookmarks, Recent tabs, Extensions (with badge), Userscripts, Dark Mode for this site (with immediate Switch toggle), Cast..., Desktop site (checkbox), Find in page, Settings.

3. Unrestricted WebExtensions MV2 & MV3 (feature/addons)
Use GeckoRuntime.webExtensionController directly to support both Manifest V2 (including blocking webRequest for uBlock Origin) and Manifest V3.

Installation Flows:

Direct installation from https://addons.mozilla.org (intercept .xpi download links and trigger webExtensionController.install(uri)).

Local .xpi / .zip file picker via Android Storage Access Framework (ActivityResultContracts.OpenDocument).

Extension Popup Container (ActionDelegate):

Register WebExtension.ActionDelegate on all installed extensions.

When an extension triggers onTogglePopup(extension, action) or the user clicks an extension in the menu, open a Jetpack Compose ModalBottomSheet containing a dedicated mini GeckoView bound to the popup GeckoSession. Dynamically size the sheet based on the popup DOM height.

4. Native Userscript Engine (feature/userscripts + assets/extensions/sys_userscripts)
Interception: Intercept any navigation or download URL ending in .user.js. Parse the ==UserScript== metadata block (@name, @namespace, @version, @match, @include, @exclude, @run-at, @grant, @require, @resource). Show a native Compose installation sheet with metadata summary and source code preview.

Execution Architecture:

Store userscripts and their @require dependencies in Room DB.

Pass active scripts to sys_userscripts built-in extension via Port.postMessage().

Execute scripts at the exact lifecycle stage (document-start, document-end, document-idle) matching @match/@include globs.

Implement full compatibility shims for GM_xmlhttpRequest (executed in the background extension context to bypass page CORS), GM_setValue, GM_getValue, GM_deleteValue, GM_listValues, GM_addStyle, GM_registerMenuCommand (exposed in the Chrome Three-Dot Menu when active on the current tab), and unsafeWindow.

Management UI: Full Material 3 Userscript Manager with enable/disable switches, script code editor with syntax highlighting, and manual update checker.

5. Two-Tier Web Dark Mode (feature/darkmode + assets/extensions/sys_darkmode)
Tier 1 (Native CSS): Set GeckoRuntimeSettings.preferredColorScheme(COLOR_SCHEME_DARK) so websites supporting @media (prefers-color-scheme: dark) render natively in dark mode.

Tier 2 (Smart Force Dark for Light Pages):

Bundled sys_darkmode extension detects whether a page already uses a dark background (by sampling window.getComputedStyle(document.body).backgroundColor luminance at document-start / DOMContentLoaded).

If the page is light and the domain is not excluded in Room DB, apply one of two user-selectable engines:

Fast CSS Invert Engine: Inject high-performance CSS html { filter: invert(1) hue-rotate(180deg) !important; } while counter-inverting img, video, picture, canvas, iframe, [style*="background-image"].

Dynamic DOM Style Engine: Traverse stylesheets and inline styles, transforming high-luminance background colors to dark surfaces (#121212 / #1e1e1e) and low-luminance text colors to high-contrast light gray (#e0e0e0).

Provide an instant per-domain toggle switch directly inside the Chrome Three-Dot Menu that updates the page live without reloading.

6. Chromecast Integration (feature/cast + assets/extensions/sys_mediasniffer)
Media Detection:

sys_mediasniffer monitors DOM <video> elements (src, <source>) and intercepts network requests via webRequest.onBeforeSendHeaders for .mp4, .webm, .m3u8 (HLS), and .mpd (DASH) streams, capturing URL, MIME type, page title, thumbnail, and request headers (Cookie, Referer, User-Agent, Authorization).

Sends detected media items over the Native Messaging port to CastRepository in Kotlin.

Local Ktor Proxy Server & Cast Controller:

Initialize CastContext using a custom OptionsProvider with the Default Media Receiver (CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).

When the user selects a detected video and picks a Chromecast device:

Start a lightweight Ktor HTTP server bound to the device's local Wi-Fi IP (http://<LAN_IP>:<PORT>/proxy/{streamId}).

The Ktor proxy fetches the upstream video/HLS manifest using OkHttpClient, injecting the exact Cookie, Referer, and User-Agent captured from GeckoView, and rewrites relative .m3u8 segment URLs to route through the proxy.

Load the proxied LAN URL into RemoteMediaClient on the Chromecast and display a persistent Mini Controller bar above the bottom navigation bar + full-screen Cast Expanded Controller.

Tab Mirroring Fallback: Implement an option to cast the active tab viewport using GeckoDisplay.capturePixels() / MediaProjection encoded into a live MJPEG/WebM stream served via the Ktor LAN server to the Chromecast receiver.

EXECUTION PLAN & DELIVERABLES
Work systematically step by step. Do not skip files or summarize code blocks.

Step 1: Generate settings.gradle.kts, libs.versions.toml, app/build.gradle.kts, and AndroidManifest.xml with all required permissions, Cast metadata, and GeckoView dependencies.

Step 2: Implement core/database (Room entities & DAOs for Tabs, TabGroups, Userscripts, SiteDarkRules) and core/engine (GeckoEngineProvider, ChroGeckoRuntime, Native Messaging bridge).

Step 3: Implement the 3 built-in system WebExtensions (sys_userscripts, sys_darkmode, sys_mediasniffer) including their manifest.json, background scripts, and content scripts.

Step 4: Implement the feature layers (AddonManagerController, UserscriptFeature, DarkModeFeature, CastProxyServer & CastManager).

Step 5: Implement the complete Jetpack Compose Chrome UI (ChromeBrowserScreen, ChromeOmnibox with swipe-to-switch-tab gesture, ChromeTabGridSwitcher with Tab Groups, ChromeThreeDotMenu, ExtensionPopupBottomSheet, and UserscriptEditorScreen).

Begin immediately with Step 1 and Step 2. Write full, production-ready code.