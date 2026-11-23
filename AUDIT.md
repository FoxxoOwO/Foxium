# ChroGecko — Audit Report

Date: 2026-11-22 · Version audited: `0.71.0` (versionCode 73) · Scope: whole repository
(`:app` module, Kotlin, bundled WebExtensions JS, Room schema, build config, tests)

Supersedes the 2026-11-21 audit of `0.65.0`.

## Fix status (`0.74.0`)

Version bumped to `0.74.0` (`versionCode = 76` in `app/build.gradle.kts`). Completed feature work, test coverage, and hygiene items:

- **Privacy & Security:** Added third-party cookie policy (`CookiePolicy`, Total Cookie Protection default), automatic cookie banner handling (`CookieBannerMode`, `REJECT` default), bounce tracking protection, Safe Browsing (`ContentBlocking.SafeBrowsing.DEFAULT`), Global Privacy Control (`Sec-GPC`), configurable HTTPS-Only mode (`OFF` / `PRIVATE_ONLY` / `ALL`), and DNS over HTTPS (`DohMode`, presets + validated custom endpoint). Closed OkHttp 4.12's IP-literal redirect SSRF bypass in `CastProxyServer` via `executeFollowingSafeRedirects()`.
- **Testing & Hygiene:** Added `CastProxyServerTest` (6), `PrivacyPreferencesTest` (7), `tools/check-reader.mjs`, and `tools/check-userscript-content.mjs`; converted all 8 `PluralsCandidate` strings to full EN/CS `<plurals>`, fixed `UseKtx` in `AppLocaleManager`, and split `ChromeSettingsScreen.kt` across `PrivacySettingsPages.kt`, `AutofillSettingsPages.kt`, and `SiteAndToolsSettingsPages.kt`.

### What was verified (`0.74.0`)

```
./gradlew :app:testDebugUnitTest           → PASS, 100 tests, 0 failures, 0 errors, 0 skipped (26 test XML files)
./gradlew :app:lintDebug                   → PASS, 0 errors, 24 warnings (version notices, OldTargetApi, AppBundleLocaleChanges)
./gradlew :app:assembleRelease             → PASS (signed, R8 OK, lintVitalRelease OK)
node tools/check-darkmode.mjs              → PASS (exit 0)
node tools/check-expander.mjs              → PASS (exit 0)
node tools/check-reader.mjs                → PASS (exit 0)
node tools/check-userscript-background.mjs → PASS (exit 0)
node tools/check-userscript-content.mjs    → PASS (exit 0)
node tools/check-userscript-matching.mjs   → PASS (exit 0)
./gradlew :app:connectedDebugAndroidTest   → PASS, 9 of 9 on SM-S918B (cs-CZ, Android 17): localized `AddonInstallConsentTest` (3/3), `ChromeSmokeTest` (3/3 incl. `openingSettingsShowsPrivacyControls`), `DatabaseMigrationTest` (1/1), `TabGridSwipeTest` (2/2)
```

## Fix status (`0.73.0`)

Version bumped to `0.73.0` (`versionCode = 75` in `app/build.gradle.kts`). Remediation applied across security, privacy, bundled WebExtensions, Android API compliance, and UI/resource hygiene:

- **Cast LAN proxy & mirror auth (`CastProxyServer.kt`, `CastManager.kt`, `BrowserViewModel.kt`):**
  - Tab mirror routes `/mirror/{token}` and `/mirror/{token}/stream` now require a rotating `@Volatile` token issued by `publishMirror()` and cleared by `clearMirror()` (`CastManager.stopMirror()` / `BrowserViewModel.watchMirror()`).
  - `CastProxyServer.clientFor(originHost)` uses a custom OkHttp `Dns` implementation blocking non-public/literal targets via `NetworkTargetPolicy::isNonPublic` across cross-origin and HTTP redirect hops.
- **Userscripts isolation & `GM_xmlhttpRequest` policy (`sys_userscripts/background.js`, `sys_userscripts/content.js`, `tools/check-userscript-background.mjs`):**
  - `handleXhr()` restricts targets to `http:`/`https:` and blocks loopback, private, link-local, and ULA hosts via `isBlockedXhrHost`.
  - `buildShims()` gates `GM_*`/`GM.*` APIs by `@grant` metadata (keeping `GM_info`, `unsafeWindow`, `GM_log`, `GM_addStyle`); `pageScopeRunner()` / `executeInPageScope()` inject `GM_info`/`GM` with `try/catch` error capture.
  - Added navigation menu-clear (`tabs.onUpdated`) and `gm/xhr` host/scheme blocking checks in `tools/check-userscript-background.mjs`.
- **Reader sanitization & Gestures bugfix (`sys_reader/content.js`, `sys_gestures/content.js`):**
  - Replaced raw `content.innerHTML` assignment in Reader Mode with `sanitizeHtml()` (`DOMParser` stripping dangerous/active/form tags, `on*`/`srcdoc` attributes, and unsafe URI schemes).
  - Fixed invalid Kotlin-style `.removePrefix("www.")` call in `sys_gestures/content.js` to `.replace(/^www\./, "")`.
- **Privacy, Tracking Protection & URL hardening:**
  - `OmniboxUrlNormalizer` removed `file://` from `urlSchemes` (`OmniboxUrlNormalizerTest.fileUrlIsNotLoadedDirectly`).
  - `BrowserViewModel.refreshSuggestions()` returns empty suggestions in private tabs (`isPrivate == true`), and `clearBrowsingData()` clears `StorageController.ClearFlags.PERMISSIONS` together with site data (`cookies`).
  - `EngineSession.setTrackingProtection(enabled)` and `GeckoEngine.applyTrackingProtection(enabled)` apply Tracking Protection updates across open `sessions`.
  - Added `res/xml/data_extraction_rules.xml` (disabling cloud backup and device transfer) and `res/xml/backup_rules.xml` (`fullBackupContent` for Android < 12) referenced by `AndroidManifest.xml`.
- **UI, Dark Mode, Addon actions & Lint cleanup:**
  - `DarkModeRepository.toggle()`, `DarkModeFeature`, and `BrowserViewModel.toggleSiteDarkMode()` pass the active `darkEngine.value` (`DYNAMIC` vs `INVERT`).
  - `InstalledAddon.hasAction` (`AddonManagerController`, `AddonsSheet`) enables the `Open` button on extensions with an action popup even before `actionTitle` is populated; `ContextMenuSheet` uses `R.string.context_share_link`.
  - Replaced the `ChromeBrowserScreen` IME polling loop with `ViewTreeObserver.OnGlobalLayoutListener` in `DisposableEffect(insetsView)` and guarded `POST_NOTIFICATIONS` permission checks with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`.
  - Updated `Haptics` API guards (`UPSIDE_DOWN_CAKE` for `DRAG_START`/`TOGGLE_ON`/`TOGGLE_OFF`, `R` for `CONFIRM`), `AutofillUi.AddressDialog` default label (`R.string.default_address_label`), and `ChromeNewTabPage` `modifier` parameter order.
  - Removed unused `print_save_as_pdf` (`values/strings.xml`), duplicate `mirror_notification_text` (`values-cs/strings.xml`), and stray root artifacts (`nul`, `build-*.png`, `build-logcat.txt`, `build-readability/`, `.kotlin/errors/`).

### What was verified (`0.73.0`)

```
./gradlew :app:testDebugUnitTest           → PASS, 87 tests, 0 failures, 0 errors, 0 skipped (24 test XML files)
./gradlew :app:lintDebug                   → PASS, 0 errors, 33 warnings (down from 42; DataExtractionRules resolved)
./gradlew :app:assembleRelease             → PASS (signed, R8 OK, lintVitalRelease OK)
node tools/check-darkmode.mjs              → PASS (exit 0)
node tools/check-expander.mjs              → PASS (exit 0)
node tools/check-userscript-background.mjs → PASS (exit 0)
node tools/check-userscript-matching.mjs   → PASS (exit 0)
```

> Note: On-device/emulator `:app:connectedDebugAndroidTest` was not re-run in `0.73.0` (last run in `0.71.0`/`0.72.0` below).

## Fix status (`0.72.0`)

| # | Finding | Status |
|---|---|---|
| 0 | Drive-by `.xpi` install | **Fixed** — the download path no longer installs. `.xpi` downloads land in Downloads; installs start from the file picker, an https URL in the menu, or the AMO page button. |
| 1 | Auto-granted extension permissions | **Fixed** — installs now show a native consent dialog with the requested permissions and origins; third-party updates that need new permissions are refused; optional permissions are denied. Bundled system extensions are still auto-granted (they ship in the APK). |
| 2 | Instrumented suite red | **Fixed** — the Espresso 3.7 bump removed the `NoSuchMethodException: InputManager.getInstance` crash, and the root cause of the rest was the app asking for the notification permission on the first screen: the system dialog covered the activity before its first frame, so compose found no roots. The prompt now fires when a download actually starts. `connectedDebugAndroidTest` is green (8/8) on an idle emulator. |
| 3 | No privacy protection | **Fixed** — `GeckoSessionSettings.useTrackingProtection` (ETP, default on, switchable in Settings → Privacy and security, persisted) and `allowInsecureConnections(HTTPS_ONLY_PRIVATE)` (private tabs refuse plaintext). Still no cookie policy and no DoH. |
| 4 | Cloud backup of profile data | **Fixed** — `allowBackup="false"`. |
| 6 | Unverified menu-command channel | **Fixed** — menu commands travel over `browser.runtime` messaging per frame and are merged per tab in the background; the `window.postMessage` path and the page-reachable handler fan-out are gone. |
| 8 | Plaintext extension install | **Fixed** — `installFromUrl` accepts https only. |
| 12 | Red lint gate | **Fixed** — escaped `local.properties` paths; `lintDebug` is green (0 errors). |
| 7 | Unauthenticated cast proxy | **Partially fixed** — tokens now expire after 12 h and proxy responses are `no-store`. `Access-Control-Allow-Origin: *` stays because the Chromecast receiver needs CORS; anyone on the LAN who learns a live token URL can still pull that stream (tokens are 32-hex UUIDs). |
| 9 | Omnibox loads `data:`/`view-source:`/`blob:` | **Fixed** — `OmniboxUrlNormalizer` allowlists http/https/file/about and searches everything else (2 new unit tests). |
| 5 | Userscripts ran in content-script scope | **Fixed** — scripts with no `@grant` (or `@grant none`) now execute in the page realm through an injected `<script>`, with a CSP fallback to the sandbox; scripts that use GM_* keep the isolated sandbox. Verified on device: page scope reports `window.wrappedJSObject === undefined` and a `GM_setValue` script still sees its shim. |
| 10, 11, 13–16 | Missing tests, file size, APK size, cleartext, schema export, Gecko reflection | **Open** — unchanged, see below. |

## Verdict

**Good engine and data core; the release blocker is gone, and what remains is hardening.**

The UI work clearly moved forward (omnibox, back handling, extension popups, autofill, reader,
expander) and the data layer stayed clean. The 2026-11-22 fixes closed the drive-by install: a page
can still hand the user a `.xpi` link, but it now lands in Downloads and installing it requires a
consent dialog that lists the extension's permissions and origins. Extension permissions are no
longer silently granted, tracking protection is on, private tabs refuse plaintext, and the profile
is no longer backed up to the cloud.

What is left is hardening: the cast proxy is unauthenticated on the LAN (tokens now expire and
responses are `no-store`), the riskiest code still has no tests, three source files are over 1,800
lines. Userscripts now match Tampermonkey's execution
model for both cases (`@grant none` in the page realm, GM_* in the sandbox) and the instrumented
suite is green on an idle emulator.

Score by area:

| Area | Grade | Note |
|---|---|---|
| Engine / GeckoView integration | A- | Correct singleton, process guard, private contexts, no cert override; ETP on and switchable |
| Data layer (Room, migrations, crypto) | A- | 10 real migrations, 1→11 migration test, AES-GCM keystore crypto |
| Bundled WebExtensions (7) | B+ | MV2, native messaging, GM_* shims; page realm for `@grant none`, sandbox for GM_* |
| Cast / local proxy | B | Token URLs, 12 h expiry, LRU cap, SSRF + header filtering; no auth, untested |
| Chrome UI replica | B+ | Feature-complete, localized (431 strings × en/cs), keyed lazy lists; three files >1.8k lines |
| Security / privacy posture | B- | Consent dialog for extension installs, ETP + HTTPS-only in private tabs, backup off, omnibox schemes filtered; cast proxy still unauthenticated |
| Test & CI readiness | B- | 86 unit tests + 8 instrumented tests green; cast proxy routes, downloads and most views still untested |
| Build hygiene | B | Release builds and signs; lint gate green; 187 MB arm64 APK |

## What was executed

```
./gradlew :app:testDebugUnitTest        → PASS, 86 tests, 0 failures (24 classes)
./gradlew :app:assembleRelease          → PASS (signed, R8 OK)
./gradlew :app:lintDebug                → PASS after 0.72.0 (was 2 errors, 42 warnings)
./gradlew :app:connectedDebugAndroidTest→ PASS, 8 of 8 (emulator `pcmote_test`; the phone in
                                          hand was in use, so the emulator was used)
```

Notes: the default `JAVA_HOME` on this machine is JVM 11, so Gradle must be run with a JDK 17
(`C:/Program Files/Eclipse Adoptium/jdk-17.0.21.10002-hotspot`).

Measured sizes: `app-arm64-v8a-release.apk` **187 MB**, `armeabi-v7a` 150 MB, `x86_64` 205 MB,
`universal` **505 MB**. Inside arm64: `lib/arm64-v8a/libxul.so` **148.1 MB**, `assets/omni.ja`
13.7 MB, `classes.dex` 7.9 MB.

Code base: 158 files in `app/src`, 18,882 Kotlin LOC in `app/src/main/java`, 17 bundled JS files
(5,944 lines, incl. 2,786-line vendored Mozilla Readability), 7 asset extensions, 24 unit test
classes, 3 instrumented test classes.

## Findings

### P0 — release blocker

**0. A link to a `.xpi` installs a privileged extension without asking.**

`app/src/main/java/com/chrogecko/browser/feature/chromeui/BrowserViewModel.kt:1765-1772`
(`handleExternalResponse`) matches *any* response whose path ends in `.xpi`, closes the body and
calls `installAddonFromUrl(response.uri)` → `AddonManagerController.installFromUrl`
(`feature/addons/AddonManagerController.kt:145-161`) → `controller.install(url)`. The prompt
delegate installed at `AddonManagerController.kt:88` answers
`WebExtension.PermissionPromptResponse(true, true, true)` for every install, so:

* origin is not checked (any site, including a plaintext `http://` one — see P2-4),
* no confirmation UI exists anywhere in the path (`pendingUserscript` has a sheet, addons do not),
* every requested permission is granted silently.

Result: one tap on a page link ⇒ persistent, fully privileged code inside the browser's extension
process. Treat as RCE-class for anyone who browses with this build.

Minimal fix: gate `installFromUrl` behind a Compose confirmation sheet (name, version, requested
permissions, host patterns) and allow auto-approval only for `SystemExtensionIds.ALL`.

### P1 — fix before real use

**1. Extension permissions are still auto-granted.**
`feature/addons/AddonManagerController.kt:88-100` — `onInstallPromptRequest` →
`PermissionPromptResponse(true, true, true)`, `onUpdatePrompt` and `onOptionalPrompt` →
`AllowOrDeny.ALLOW`. AGENTS.md asks for *unrestricted installation*, not *unattended consent*.
**Status (`0.72.0`): fixed** — the prompt is held until the user confirms `AddonInstallDialog`, which
lists name, version, permissions and origins; bundled extensions stay silent; new-permission updates
are refused.

**2. The instrumented test suite is red.**
All four failures are the same platform break, not app logic:
`java.lang.NoSuchMethodException: android.hardware.input.InputManager.getInstance []`, thrown from
`androidx.test.espresso.Espresso.onIdle(Espresso.java:18)` on Android 17 (SDK 37). Affected:
`ChromeSmokeTest.typingIntoTheOmniboxKeepsTheTypedText`,
`TabGridSwipeTest.swipingTabCardClosesItAndOffersUndo`,
`TabGridSwipeTest.tabClosedSnackbarCanBeSwipeAway` (+1).
Fix: bump `androidx.test:runner` / `espresso-core` to a build that handles SDK 37; there is
currently no UI safety net at all.
**Status (`0.72.0`): fixed** — the bump removed the crash, and the actual cause of the remaining
failures was the app's startup notification-permission dialog covering the activity before its first
frame. The prompt moved to the first download; the suite is green (5/5).

**3. No privacy protection is configured.**
`core/engine/GeckoEngine.kt:19-22` builds sessions with only `contextId`, `usePrivateMode`,
`suspendMediaWhenInactive`. `useTrackingProtection` does not appear anywhere in the repository
(`rg` over `app/src/main/java`), and there is no HTTPS-only mode, no third-party cookie policy, no
DoH, no safe-browsing switch. For a browser this is a headline feature, not a preference.
**Status (`0.72.0` → current):** `useTrackingProtection` is on by default and switchable in
Settings → Privacy and security (`AppPreferences.TRACKING_PROTECTION` → `GeckoEngine.applyTrackingProtection`,
applying immediately via `EngineSession.setTrackingProtection` across all open tabs). Third-party cookie
policy (`CookiePolicy`), automatic cookie-banner handling (`CookieBannerMode`), redirect-based bounce
tracking protection, Safe Browsing (`ContentBlocking.SafeBrowsing`), Global Privacy Control (GPC),
HTTPS-Only mode (`OFF` / `PRIVATE_ONLY` / `ALL`), and DNS over HTTPS (`DohMode`, built-in providers +
validated custom resolver URL) are all implemented in `PrivacyPreferences`, `ChroGeckoRuntime`, and
Settings → Privacy and security.

**4. Everything is backed up to the cloud.**
`AndroidManifest.xml:41` `android:allowBackup="true"` with no `android:dataExtractionRules` and no
`android:fullBackupContent`: the whole Room DB (history, bookmarks, saved logins, addresses, cards,
userscripts) and DataStore ride Google backup / device-to-device transfer. Saved-login ciphertext is
unusable elsewhere (keystore key is device-bound) but history and payment metadata are not secret to
that pipeline. Fix: `allowBackup="false"` or an exclusion rules file.
**Status (`0.72.0`): fixed** — `allowBackup="false"`.

### P2 — correctness, privacy, maintainability

**5. Userscripts do not run in page scope.**
`assets/extensions/sys_userscripts/content.js:341` executes scripts with
`new Function(...names, body)` inside the content script, handing `GM_*` shims in as arguments.
Consequences: `window`/`unsafeWindow` are not the page's real globals for library-style scripts,
page-provided globals (`$`, `jQuery`) are invisible, and page monkey-patching does not take effect
unless the script goes through `unsafeWindow`. Tampermonkey compatibility is therefore partial —
this contradicts the "Tampermonkey/Violentmonkey compatible" spec in AGENTS.md.
**Status (`0.72.0`): fixed** — `@grant none` scripts go through an injected page `<script>` (page
realm, `unsafeWindow` defined, CSP fallback to the sandbox), GM_* scripts keep the sandbox. Verified
on device: `typeof window.wrappedJSObject` is `undefined` in the page path and `object` in the sandbox
path.

**6. Menu-command channel accepts unverified messages.**
`assets/extensions/sys_userscripts/content.js:58` posts `chrogecko-menu-commands` to
`window.parent` with target origin `"*"`, and `:392` posts `chrogecko-run-menu-command` the same way.
No `event.source`/origin validation on either side, so any page can inject entries into the native
ChroGecko menu and can trigger a registered userscript menu handler without a user click.
Route both through the extension background script instead.
**Status (`0.72.0`): fixed** — the `window.postMessage` channel is gone; commands go through
`browser.runtime` per frame and are merged per tab, and navigation clears a tab's entries.

**7. The cast proxy is unauthenticated on the LAN.**
`feature/cast/CastProxyServer.kt` binds `0.0.0.0` (ports 8765–8785) and appends
`Access-Control-Allow-Origin: *` (`:334`); `/health`, `/mirror`, `/mirror/stream`, `/proxy/{token}`
have no credential. Tokens are UUIDv4 (unguessable) and header replay is correctly locked to the same
host/registrable domain with a forbidden-header list, so the exposure is "whoever sees the receiver
URL can replay the session headers" — acceptable for a LAN feature, but it should be scoped to the
cast session (start/stop server with the session, expire tokens) rather than living for the app's
lifetime.
**Status (`0.72.0`): partly fixed** — tokens expire after 12 h (`STREAM_TTL_MS`, pruned on publish
and on lookup), responses carry `no-store`, and the server now stops when the cast session ends or
fails to start (`CastManager.onSessionEnded`/`onSessionStartFailed` → `proxy.stop()`), so it no longer
listens on a port for the rest of the process. The routes themselves still have no credential, which
is deliberate: the receiver must fetch them and the token is the only secret.

**8. Extension install accepts plaintext.**
`AddonManagerController.kt:147` allows `http` for `.xpi` URLs, which makes AMO/URL installs
MITM-able. Combined with P0 this is a direct path to privileged code injection on a hostile Wi-Fi.

**9. The omnibox loads `data:`, `file://`, `view-source:`, `blob:` URLs.**
`feature/chromeui/OmniboxUrlNormalizer.kt:5`. Chrome refuses `data:`/`view-source:` from the
address bar and does not let a typed URL read local files. `javascript:` is correctly rejected
(falls through to search), so this is a paste-to-execute/local-file surface, not an immediate hole.

**10. The riskiest code has no tests.**
24 unit classes / 80 tests cover mostly pure helpers (URL normalization, filename sanitization,
HLS rewriting, parsers, locale, discover). Untested: `CastProxyServer` routes and SSRF path,
`DownloadManager` streaming/notification, `AddonManagerController`, `UserScriptFeature`,
`EngineSession`, `BrowserViewModel`, `AutofillController` (2 tests only), and every line of the 5.9k
JS extension code (GM shims, dark engine, media sniffer). Roughly 4% line coverage against 18.9k
Kotlin LOC.
**Status (`0.72.0` → current): partly fixed** — `NetworkTargetPolicyTest` (5), `UrlDomainPolicyTest`
(5), `PrivacyPreferencesTest` (6), and `CastProxyServerTest` (6 integration tests covering `/health`,
mirror token auth, `?u=` SSRF guard, cross-origin redirect DNS block, and header replay filtering)
bring the JVM suite to 99 tests (26 test classes), backed by 6 Node.js checks for bundled
WebExtensions including `tools/check-reader.mjs` and `tools/check-userscript-content.mjs`.
Still untested on JVM: `DownloadManager`, `AddonManagerController`, `UserScriptFeature`,
`EngineSession`, and `BrowserViewModel`.

**11. Three files carry too much weight.**
`ChromeSettingsScreen.kt` 2,349 · `ChromeBrowserScreen.kt` 2,067 · `BrowserViewModel.kt` 1,811 ·
`ChromeSheets.kt` 1,304. Compose recomposition cost is contained (keyed `items`, `collectAsStateWithLifecycle`)
but navigation inside these files is expensive and every UI change touches a 2k-line file.
**Status (current): partly fixed** — `ChromeSettingsScreen.kt` has been split into
`ChromeSettingsScreen.kt` (1,495 lines), `PrivacySettingsPages.kt` (390),
`AutofillSettingsPages.kt` (538), and `SiteAndToolsSettingsPages.kt` (618); `ChromeSheets.kt` (1,385)
has been split into `ChromeSheets.kt` (526), `LibrarySheets.kt` (513), and
`AddonsAndUserscriptsSheets.kt` (416); and `ChromeBrowserScreen.kt` (2,099) has been split into
`ChromeBrowserScreen.kt` (1,121), `ChromeToolbar.kt` (582), and `ChromeBrowserOverlays.kt` (484).
Only `BrowserViewModel.kt` (1,998 lines) remains large.

### P3 — hygiene

**12. `lintDebug` is red.** Two `[PropertyEscape]` errors come from `local.properties:1-2`
(`D:/Android/SDK`, `RELEASE_STORE_FILE=C:/Users/Teresa/Documents/key.jks`). The gate is therefore
unusable; escape the colon (`\:`) or exclude `local.properties` in `lint {}`.
Also flagged: `Haptics.kt:39/64` uses `DRAG_START`/`TOGGLE_ON`/`TOGGLE_OFF` (API 30/33) with
minSdk 26, `AppLocaleManager.kt:85` calls `setLocale` without Play Core language splits.

**13. APK size.** arm64 187 MB / universal 505 MB, dominated by unstripped-looking `libxul.so`
(148.1 MB). The ABI-split decision is right; only universal-APK builds should ship the fat variant.

**14. `usesCleartextTraffic="true"` globally** (`AndroidManifest.xml:50`). Needed for the LAN cast
proxy and plaintext media, but it also permits every OkHttp call (favicons, discover feeds, extension
installs) over http.
**Status (`0.72.0`): partly fixed** — the manifest flag stays (GeckoView and plaintext media streams
need it), but the app's own clients are https-only: userscript install/update fetches
(`UserScriptFeature.fetch`) and favicon lookups (`FaviconRepository`, including icons declared as
`http://`), on top of the already-https extension install path.

**15. Only schema version 11 is exported** (`app/schemas/com.chrogecko.browser.core.database.ChroGeckoDatabase/11.json`).
Migrations 1→11 all exist (`di/DatabaseModule.kt:32-160`) and `DatabaseMigrationTest` builds a v1
schema by hand and asserts the full chain, so the coverage is real — but future schema diffs cannot
be verified against historical versions.

**16. Reflections into package-private GeckoView internals** remain the only way to release a
display (`core/engine/EngineSession.kt:829`, `feature/addons/AddonManagerController.kt:227`,
`getDeclaredMethod("getDisplay")`). They fail silently (`runCatching`) on any GeckoView refactor.

## What is genuinely good

* `ChroGeckoApplication.onCreate` returns early for non-main processes (`:tabN`, `:gpu`,
  `:crashhelper`) — the correct fix for Gecko process startup, rarely done right.
* Private browsing hygiene is consistent: no history, no tab persistence, no favicon fetch, no
  autofill attach, no login save (`BrowserViewModel.kt:1726-1752, 1793-1798`).
* `SecretBox` (AES/GCM, AndroidKeyStore, 12-byte IV prefix, 128-bit tag, null on failure) and
  `LoginRepository` never exposing the plaintext password outside `fill()`.
* Autofill fills only after the user picks a suggestion; `sys_autofill/content.js` never
  self-fills, and the token list is closed.
* Cast proxy hardening is real work: UUID tokens, 64-stream LRU, `NetworkTargetPolicy.isNonPublic`
  SSRF guard that fails closed, registrable-domain lock for captured `Cookie`/`Referer`/`UA`,
  forbidden-header blocklist.
* Download filenames are sanitized (control bytes, `/ \ : * ? " < > |`, all-dots, 120-char cap) —
  the classic path-traversal bug is absent; URI is passed explicitly, never logged.
* Full localization: 431 keys in `values/strings.xml` and 429 in `values-cs/`, `locales_config`,
  runtime locale wrapping, Gecko `locales` sync.
* No `!!` in production Kotlin, no `runBlocking`, no `Thread.sleep`, no hardcoded `http://` literals
  in Kotlin, no stray TODO/FIXME outside vendored Readability.

## Recommended order of work

1. `AddonManagerController.kt` — confirmation sheet + per-extension permission prompt; https only;
   stop treating `.xpi` responses as installs (make it a download + explicit "install" action).
2. `GeckoEngine.kt` — `useTrackingProtection(true)`, HTTPS-only preference, wire the two settings
   into `ChromeSettingsScreen`.
3. `AndroidManifest.xml` — `allowBackup="false"` (or extraction rules).
4. `libs.versions.toml` — bump test/espresso; get `connectedDebugAndroidTest` green.
5. `local.properties` escape (or lint exclude) so `lintDebug` can become a real gate.
6. `sys_userscripts` — move execution to page scope (`unsafeWindow` + injection) and route menu
   messages through the background script.
