# Foxium

Android web browser built on **Mozilla GeckoView** with a Chrome-style UI.

The project follows the Foxium master plan: Chrome-like Compose UI, unrestricted WebExtensions
(MV2/MV3), a native userscript engine, per-site force-dark mode, and Chromecast with a local LAN
stream proxy for authenticated media.

## Current status

Implemented:

- Gradle build with GeckoView, Compose Material 3, Hilt, Room, DataStore, OkHttp, Ktor and the
  Cast SDK.
- `ChroGeckoRuntime` singleton with extensions process/Web API enabled and a user-controlled
  `prefers-color-scheme`.
- `GeckoEngine` / `EngineSession` session pool (normal and private context ids) exposing URL,
  title, progress, history flags, TLS state, desktop mode, find-in-page, thumbnail capture and
  private-mode switching.
- `NativeMessagingBridge`: `WebExtension.MessageDelegate` / `PortDelegate` to `SharedFlow`, with
  replies through `Port.postMessage`. Bundled extensions connect with
  `browser.runtime.connectNative("chrogecko")` and must declare the `geckoViewAddons` permission.
- Room schema and repositories for tabs, tab groups, bookmarks, history, per-site dark mode rules
  and userscripts (including `@match`/`@grant`/`@require` metadata).
- Three privileged bundled extensions installed from APK assets at startup:
  - `sys_userscripts` - `@match`/`@include`/`@exclude` matching, `@require` caching, GM storage,
    `GM_addStyle`, menu commands, `unsafeWindow` and a CORS-free `GM_xmlhttpRequest` proxy.
  - `sys_darkmode` - native `prefers-color-scheme` support, luminance probing, CSS invert and
    computed-style dynamic engines, with live per-domain policy and reversible overrides.
  - `sys_mediasniffer` - `<video>`/`<audio>` discovery plus webRequest capture of progressive,
    HLS and DASH URLs with Cookie/Referer/User-Agent headers.
- Feature controllers: `AddonManagerController` (file/URL/AMO `.xpi` install, enable/disable,
  action popups hosted in a Compose sheet), `UserScriptFeature` (metadata parser, install from
  file/URL/source, update checker, injection status), `DarkModeFeature` (global switch, engine
  selection, per-site rules) and `CastManager` + `CastProxyServer` (Ktor LAN proxy, HLS/DASH
  playlist rewriting, OkHttp upstream requests with captured headers, Cast session/media control).
- Chrome-style Compose UI: pill omnibox with security indicator and swipe-to-switch-tabs /
  swipe-down-to-grid gestures, find-in-page bar, 2-column grid tab switcher with Standard/Incognito
  profiles and live thumbnails, tab groups with a bottom group strip, three-dot overflow menu,
  extension/userscript/bookmark/history/cast/settings sheets, userscript editor and extension
  popup host. Overflow menu **Print** renders the page to PDF and opens the Android print spooler.
- Downloads: Gecko external responses stream to the app Downloads directory through
  `DownloadManager`, are persisted in Room (`downloads` table) and appear in a Downloads sheet with
  open/delete/clear. `.xpi` responses are routed to the extension installer and `.user.js` links are
  intercepted before navigation and handed to the userscript installer.
- Cast playback: persistent mini controller above the tab strip plus a full-screen expanded
  controller (play/pause, seek, stop, disconnect) bound to `CastPlaybackState`.
- Bookmarks live in folders: the sheet navigates into a folder, shows item counts, creates folders
  with *New folder*, moves a bookmark with *Move to folder…*, and deletes a folder whose contents
  fall back one level instead of disappearing.
- Closing a tab offers *Undo* in a snackbar, which restores the tab (and its group); bulk closes
  report the number of tabs and restore all of them at once.
- History splits into Chrome's day sections (*Today*, *Yesterday*, then dated sections) via
  `groupHistoryByDay()`; History, Bookmarks and Downloads sheets each carry a search field that
  filters by title, URL or file name.
- Downloads stream with byte progress, a Cancel action (cancelled rows persist) and a completion
  notification. The running notification shows a percentage plus transferred/total size and a
  *Cancel* action that stops the transfer and dismisses itself; the finished one becomes
  *Download complete* with an *Open* action bound to the app FileProvider. Notification delivery
  needs the runtime `POST_NOTIFICATIONS` grant on Android 13+.
- Toolbar position preference (top/bottom, Settings) moves the omnibox below the page; voice search
  and share actions live in the omnibox pill.
- Search engine is chosen in Settings (Google, DuckDuckGo, Bing, Brave Search, Startpage) and stored
  in DataStore; free text in the omnibox uses that provider's template.
- Pull-to-refresh is handled by the `sys_gestures` content extension (single-finger downward pull of
  110 px at scroll top triggers `location.reload()`); GeckoView consumes page touches, so a Compose
  pull-refresh wrapper cannot observe the page scroll.
- Site icons are fetched in Kotlin: when a tab settles on an `https` URL, `FaviconRepository` reads
  the site root's `<link rel="icon">` hrefs (no cookies), downloads the first that decodes (falling
  back to `/favicon.ico`) and caches it in the `favicons` Room table. They show up in new-tab tiles,
  grid cards, the group strip and the bookmarks/history sheets. Pages that only declare an icon after
  JavaScript runs, or that sit behind a login, keep their letter badge.
- Tab groups can be renamed, recolored (six-color palette) and deleted (tabs are ungrouped, not
  closed) from the group strip title.
- Long-pressing a tab card opens its menu: group with another tab, duplicate, close, close other
  tabs. Dragging a card onto another reorders the grid, and the **Group** button in the header
  collects every normal tab into one group.
- Recent tabs: menu entry and sheet listing the 50 most recently active normal tabs from Room, each
  openable in a new tab.
- New tab page: blank tabs show a scrollable Compose page instead of `about:blank` — wordmark, up to
  eight tiles (pinned shortcuts first, then bookmarks and history, one per host), a *Discover* list
  of suggested reading, and, in private tabs, Chrome's incognito notice without tiles. Tiles and
  feed rows load in the current tab and the omnibox shows its "Search or type URL" hint instead of
  `about:blank`.
- Incognito surfaces use the charcoal dark palette regardless of the system theme (private tabs
  switch the whole Compose theme).
- Extension popups open in a resizable sheet sized to 62% of the viewport with an initial partially
  expanded state; GeckoView exposes no DOM measurement API, so true content-height sizing is not
  possible without cooperation from the extension.
- Background playback: audio and video keep playing when the app is backgrounded, and the platform
  media controls (notification and lock screen) drive the active tab through a `mediaPlayback`
  foreground service.
- Autofill: Settings → Addresses and more stores shipping addresses and payment methods
  (card numbers encrypted with the same Keystore key, only the last four digits are shown); a
  focused address or card form raises chips above the page that fill every matching field.
- Tab grid drag and drop reorders tabs (`tabs.sortOrder`); the **Group** button collects them into a group.
- Saved logins: login forms raise a Chrome-style save prompt, credentials are encrypted with an
  Android Keystore AES/GCM key and offered back as a fill chip; Settings → Passwords manages them.
- Third-party password managers: GeckoView's platform autofill delegate is chained with the built-in
  one, so Bitwarden, 1Password, KeePassDX or Google Password Manager can fill and save logins.
  Settings → Passwords can switch it off and jump to Android's autofill-service picker.
- Languages: the interface is available in English and Czech; Settings → Language picks the system
  language or either one and updates the UI and Gecko's `Accept-Language` without a restart.
- Userscripts: install review sheet (metadata, grants, source preview) before anything is saved,
  `@resource` support through `GM_getResourceText`/`GM_getResourceURL` (data URLs, 2 MB cap), and
  `GM_registerMenuCommand` entries surfaced in the three-dot menu for the current page.
- Unit tests for omnibox normalization, userscript parsing, HLS rewriting, download file naming and
  new-tab shortcut ranking; Node self-checks for userscript matching and the userscript background
  protocol.

Known limits:

- Normal tabs and tab groups are restored from Room on launch (URL, title, order, group and last
  active tab). Incognito tabs are never written to disk; any stale private rows are cleared at
  startup.
- Tab groups belong to the grid: tabs can be merged into a group from a card's long-press menu or in
  bulk with the header **Group** button, but a tab cannot be pulled back out of its group by
  dragging (use the group strip) and the grid does not auto-scroll while dragging to an off-screen
  card.
- Cast screen mirroring streams the whole display as MJPEG from the cast server
  (`http://<lan-ip>:8765/mirror`), started from the Cast sheet after the MediaProjection consent;
  the foreground notification has a Stop action and the stream also ends when the system revokes
  the projection. Chromecast's Default Media Receiver cannot play MJPEG, so the viewer is a LAN
  browser. Video, HLS and DASH still cast through the authenticated LAN proxy.
- Downloads stream with progress, a cancel action and a completion notification; files live in the
  app-specific external Downloads directory and open through `FileProvider`. Each finished row has a
  ⋮ menu with *Open with…* (system chooser), *Share* (share sheet) and *Remove*, and reports
  *No app can open this file* when no activity matches the MIME type.
- Long press selects a word and raises the selection bar (Copy when Gecko offers it, Share, Done); a
  long press on a link reaches the link menu only when the bundled gesture extension wins the race
  against Gecko's word selection.
- Settings → *Clear browsing data* wipes history, cookies/site data, caches and downloads in one dialog.
- *Translate…* in the overflow menu opens the page through Google Translate's proxy, and *Find in
  page* shows a match counter with previous/next arrows.
- The toolbar mirrors Chrome: tab counter on the left, an omnibox pill that only grows a stop cross
  while loading and a microphone while focused, and no permanent share or reload buttons.
- The new tab page shows a *Search Google or type URL* pill that focuses the omnibox. *Edit* turns
  the tile grid into an editor: an *Add* tile opens a dialog for a pinned site (stored in DataStore,
  shown before history tiles), each tile gets a × badge, and *Restore* brings hidden tiles back.
  Tapping a pinned tile in that mode opens *Rename shortcut*, so a pinned site can carry its own
  name; history-derived tiles keep the site name. *Discover* below the tiles lists suggested reading
  with a refresh button.
- Links from other apps arrive through the `ACTION_VIEW` filter and open in a new tab; the overflow
  menu also pins the current page to the launcher with **Add to Home screen**.
- Settings → *Text expander* keeps snippets: typing a trigger plus a space, Enter or Tab in any text
  field replaces it with the expansion (`{{date}}`, `{{time}}`, `{{datetime}}` are filled in). A
  master switch, per-snippet switches and live updates require no reload.
- *Show reader view* in the overflow menu rebuilds a readable article into one column with the
  vendored `Readability.js` running inside `sys_reader`; *Hide reader view* restores the original
  nodes without reloading.
- Settings → *Site settings* stores a rule per host (JavaScript on/off, Desktop site, Reader mode) in Room and
  applies it in `onLoadRequest`, so the rule governs the document that is loading; the active tab
  reloads when its own rule changes.
- Fullscreen video hides the browser chrome and the system bars; Back leaves fullscreen before it walks
  history.
- JS dialogs (`alert`, `confirm`, `prompt`, `select`), HTTP auth, file uploads, `window.open` popups and
  the geolocation / notification / camera / microphone asks are answered from Compose dialogs
  (`EnginePromptDialogs.kt`). `openFilePrompt` uses SAF, so uploads come back as a `content://` URI.
- Force dark applies to sites without a per-site override; `prefers-color-scheme` is set from the
  global switch only.
- Native messaging needs the `geckoViewAddons` permission in every bundled extension manifest:
  GeckoView only routes `browser.runtime.connectNative` to the app for extensions holding it,
  otherwise the call falls through to `NativeManifests` and fails with `Native manifests are not
  supported on android`. Keep it on any new `sys_*` extension that needs the port.

## Requirements

- JDK 17
- Android SDK 36 (`compileSdk = 36`, `minSdk = 26`)

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
node tools/check-userscript-matching.mjs
node tools/check-userscript-background.mjs
node tools/check-expander.mjs
```

Manual dialog/permission checks: serve `tools/testpages` (`python -m http.server 8099`), then open
`http://localhost:8099/prompttest.html` (alert, confirm, popup, file upload) and
`http://localhost:8099/geotest.html` (geolocation + runtime permission) from the omnibox.

Instrumented smoke tests run on a connected device or emulator and exercise the real boot path
(GeckoView runtime, bundled extensions, Room):

```bash
./gradlew :app:connectedDebugAndroidTest
```

With ABI splits the test APK must match the installed app, so on an x86_64 emulator install
`app-x86_64-debug.apk` and `app-debug-androidTest.apk` first and run:

```bash
adb shell am instrument -w -e class com.chrogecko.browser.ChromeSmokeTest   com.chrogecko.browser.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The debug APK follows the same ABI split, e.g.
`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` for a phone and
`app-x86_64-debug.apk` for an x86_64 emulator.

The release build produces one APK per ABI plus a universal one in
`app/build/outputs/apk/release/`:

| Output | Size |
| --- | --- |
| `app-arm64-v8a-release.apk` | ~177 MB |
| `app-x86_64-release.apk` | ~195 MB |
| `app-armeabi-v7a-release.apk` | ~142 MB |
| `app-universal-release.apk` | ~480 MB |

GeckoView 146 ships no 32-bit x86 libraries, so `x86` is deliberately not in the split list.

## Toolchain note

The build pins **GeckoView 146.0.20251201213807** and **AGP 8.13.2**. GeckoView 147+ depends on
AndroidX Core 1.18+/Lifecycle 2.10+, whose AAR metadata requires `compileSdk 37` and AGP 9.1+.
Upgrade GeckoView together with the AGP 9 / Gradle 9.5 toolchain; changing only the GeckoView
version fails the AAR metadata check.

## Bundled extensions

Built-in extensions live in `app/src/main/assets/extensions/` and are installed with
`WebExtensionController.installBuiltIn`. Kotlin and JavaScript communicate over the native app name
`chrogecko`; each message is a JSON object with a `type` field.

| Extension | ID | Kotlin <-> extension messages |
| --- | --- | --- |
| `sys_userscripts` | `sys_userscripts@chrogecko.browser` | `userscripts/sync`, `userscripts/status`, `userscripts/injected`, `userscripts/menu`, `userscripts/menu-command` |
| `sys_darkmode` | `sys_darkmode@chrogecko.browser` | `dark/config`, `dark/status`, `dark/ready` |
| `sys_mediasniffer` | `sys_mediasniffer@chrogecko.browser` | `media/snapshot`, `media/clear`, `sniffer/ready` |
| `sys_expander` | `sys_expander@chrogecko.browser` | `expander/config`, `expander/apply`, `expander/status`, `expander/ready` |
| `sys_reader` | `sys_reader@chrogecko.browser` | `reader/toggle`, `reader/activate`, `reader/state`, `reader/ready` |

## Package layout

```text
com.chrogecko.browser
├── core/engine        # runtime, session pool, Compose GeckoView host
├── core/messaging     # native messaging bridge
├── core/database      # Room entities, DAOs, repositories
├── core/network       # OkHttp client
├── core/preferences   # DataStore settings
├── feature/addons     # extension install/enable, action popup host
├── feature/cast       # Cast manager, options provider, Ktor LAN proxy, HLS/DASH rewriter
├── feature/chromeui   # toolbar, tab grid, menu, sheets, ViewModel, URL normalizer
├── feature/darkmode   # force-dark controller
├── feature/expander   # text expander snippet mirror
├── feature/reader     # reader mode bridge (Readability via sys_reader)
├── feature/userscripts# parser, installer, injection status
└── ui/theme           # Chrome palette (Material 3)
```
