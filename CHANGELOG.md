# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Fixed

- **Update installation:** the finalized APK lives in `filesDir/updates`, but the FileProvider had no root for it – installing from the dialog or notification crashed. Added the missing root and turned URI creation failures into a visible error instead of an exception. Cancelling a download before its coroutine starts no longer wedges all future downloads. The final release is now correctly seen as newer than its RC/beta.
- **Suggestions:** a dropped connection while reading the response body left the suggestion coroutine suspended forever; failures are now propagated to the caller.
- **Passwords:** the "Never" button in the save prompt now persists a per-host rule instead of just hiding the dialog; a rejected autofill no longer discards the credential bar; duplicate snippet triggers get a correct error message.
- **Reader/expander:** a manual reader-off is no longer immediately re-enabled on auto-reader sites (the flag now survives the native relay); the expander keeps the word separator with hardware keyboards and ignores IME composition events.
- **Intent/favicons:** `intent://` links with `file:`/`content:` data now honour their browser fallback URL; uppercase-scheme icon URLs are no longer discarded.
- **UI:** the find-in-page field receives focus when opened; the add-shortcut tile no longer squeezes a full 4-tile row; the translate target follows system locale changes.
- **Security:** the registrable-domain helper now understands shared-hosting suffixes (`github.io`, `s3.amazonaws.com`, `co.kr`, …) so tenants cannot be treated as the same site.

## [Unreleased]

### Fixed

- **Cast:** a rejected/timeout load no longer leaves the UI showing a connected player; uppercase-scheme HLS segments are accepted by the proxy; DASH `SegmentTemplate` placeholders are preserved while rewriting; child segments get a MIME type derived from their own URL; `Accept-Ranges` is only advertised when upstream supports it; captured `Referer` and custom headers no longer cross origins (including via redirects). Media stop keeps notification controls bound to the session.
- **Engine:** Android permission and SAF file results are matched to the exact request id; find-in-page failures reset the counters and navigation clears stale find/selection state; `window.print()` in a background tab no longer hijacks the print UI; print results are keyed by tab; folder file prompts no longer use the multi-file SAF contract.
- **Build:** split APKs now have distinct version codes (universal 89, armeabi-v7a 90, arm64-v8a 91, x86_64 92); release builds fail instead of silently producing unsigned APKs; KSP updated to 2.3.14; downloads on API 26–28 use app-private storage so no legacy storage permission is needed; test harnesses resolve scripts relative to the repository.

## [Unreleased]

### Fixed

- **Localization:** Czech permission dialogs no longer read “Web Tento web…”; the Cast media action uses a real verb string instead of the dialog title, and the hardcoded English “media” fallback is localized.
- **Accessibility:** the incognito toolbar/tab-grid toggles, tab-group sibling chips and group-color swatches now expose names and selection state to screen readers; small destructive controls keep a 48 dp touch target.
- **RTL:** omnibox swipe-to-switch-tab is direction-aware and the shortcut remove badge no longer shifts inward.
- **Test harnesses:** dark-mode and reader checks now execute the scripts instead of matching source text; userscript plan-full/injected relays, expander reconfiguration and other native payloads are asserted; harnesses are portable across working directories.

## [Unreleased]

### Fixed

- **Security:** userscript `@require`, `@resource` and `GM_xmlhttpRequest` now validate every redirect hop instead of following a public URL into loopback/metadata endpoints; `@include`/`@exclude` regex length is capped; AMO install clicks require a trusted primary gesture; media sniffer caches headers only for media candidates.
- **Autofill:** fields are identified by opaque ids generated in the content script and filled only when the user selects an entry; page-injected hidden inputs can no longer receive saved card/address values.
- **Add-ons/userscripts:** editing a script into another script's `(namespace, name)` is rejected instead of REPLACE-deleting it; disabled extensions drop their action/badge/popup; the editor no longer overwrites in-progress text on external updates; per-tab status maps are bounded and cleared; popup GeckoSessions are closed on ViewModel teardown.
- **Background work:** permission-request flows buffer multiple requests; Cast `release()` cancels its collectors before tearing down the proxy.

## [0.79.1] - 2026-11-25

### WebAuthn / passkeys

- **Release builds:** added a ProGuard rule keeping GMS FIDO2 Parcelable class names, which R8 otherwise renames (Mozilla Bug 2093295). GeckoView 146 predates Mozilla's own fix.
- **Diagnostics:** the Activity-result callback now propagates the real platform/GMS error instead of a generic cancellation, and the delegate follows the official GeckoView ordering (launch first, then create/store the result).
- **Limit (external):** arbitrary passkey sites still require Foxium to be on Google's privileged-browser allowlist and the signing key on the FIDO2 privileged allowlist. Those approvals cannot be done in code.

### Performance

- **Discover:** fetches at most a bounded oversample of items per source instead of 200, sources are fetched in parallel outside the cache mutex, and the redundant second DNS lookup was removed (~800 HTTP calls and ~1,600 DNS lookups per cache miss before).
- **Cast proxy:** the LAN base address is resolved once at startup instead of enumerating network interfaces for every HLS segment; proxy startup is forced onto the IO dispatcher; media snapshots are capped at 32 tabs; sniffer flush events are de-duplicated.
- **Downloads and media:** progress database writes and notification rebuilds are throttled to once per second; media metadata/notifications are rebuilt only when the content actually changes, not on every position tick.
- **Startup/UI:** embedded-extension re-registration is idempotent and no longer force-disconnects live ports; the locale tag is cached in memory; omnibox suggestions are debounced; the incognito badge path is cached between draws; tab-swipe pointer events are coalesced into one consumer job; a duplicated language flow collection was removed.
- **Bookmarks:** HTML import preloads existing URLs in one query instead of one SELECT per entry.

## [0.79.0] - 2026-11-25

### Changed

- **Update checks are throttled to once per hour instead of 12 hours.** A check still runs on app launch when the interval has elapsed, and the update dialog is shown as soon as a newer release is found (a version the user explicitly dismissed stays hidden until a newer tag appears).

### Added

- **"Search the web" in the long-press context menu.** Both the link/image context sheet and the native text-selection menu now offer a localized *Vyhledat na webu / Search the web* action. It searches the long-pressed text with the active search engine in the current tab; unlike the omnibox, host-like text is searched instead of navigated to.

### Fixed

- **Cast.** `cast()` now returns failures as `Result` instead of letting proxy/MediaInfo exceptions kill the caller, and binds the `RemoteMediaClient` so Play/Pause/Seek and live status work even when casting starts before the session callbacks. Media detected before the native port connects is re-flushed on `sniffer/ready` and on port reconnect.
- **Userscripts.** Menu commands are dispatched only to the frames that registered them (instead of running once per frame); `GM_info` now carries `matches`/`includes`/`excludes` and the full `scriptMetaStr`; `GM_xmlhttpRequest` no longer fails when `data` is set with the default GET method.
- **Autofill.** Focus jobs, suggestions and session references are keyed per tab, so credentials from one tab can never be filled into another; detaching a view no longer discards Android's platform autofill delegate, and repeated recompositions no longer replace the composite delegate mid-edit.
- **Native messaging.** Delegate re-registration replaces instead of stacking listeners, a reconnecting extension port displaces the old one, and `post()` resolves the port on the main thread instead of posting to a stale reference.
- **Browser UI.** Omnibox history/bookmark suggestions stay fresh while browsing; the Undo snackbar now refreshes when a second tab is closed; Discover fetches cancel older overlapping requests; back gestures read live UI state; revisiting the same URL in a different tab is recorded in history; voice search clears real keyboard focus.
- **Downloads.** A successful HTTP response with no body now creates a visible failed row instead of silently doing nothing.

### Second audit round

- **Update flow.** Cancelling a download returns to the update prompt instead of showing `Error("Canceled")`; the finished APK lives in `filesDir` so cache trimming cannot remove it; the "ready to install" notification now launches the installer (also after a cold start) and is only dismissed once an installer or Settings activity actually opened. Notification permission denial is reported and no longer lets the user hide the only remaining download UI.
- **WebAuthn.** A second overlapping request is rejected instead of reusing the single Activity-result launcher, so one request cannot be completed with another request's assertion.
- **Screen mirroring.** The LAN mirror URL is published only after a live virtual display exists; a failed projection no longer leaves a dead URL, and the single-use consent token is not retried.
- **Media playback.** Notification actions guarantee foreground-service state, preventing a service with no notification.
- **Dark mode.** Global off is honored even with saved per-site rules, and pages that change luminance after load are re-evaluated in both directions.
- **Extensions.** AMO's "Add to Firefox" click is cancelled so it no longer triggers a duplicate `.xpi` download; autofill no longer steals focus or fills unrelated fields when the input has no `<form>`; reader mode stays off after a manual toggle on an auto-enabled site.
- **UI.** The Cast and link sheets actually scroll, the shortcut dialog validates host input and supports the Done key, and the floating selection menu refreshes its Copy/Select-all items when the available actions change.

### Third audit round

- **Security:** RSS/Discover fetches no longer follow redirects blindly (every hop is revalidated and DNS is pinned at connect); resolved private addresses are rejected; Cast credentials are only forwarded to the exact origin and never downgraded from HTTPS to HTTP; addon installs no longer silently grant incognito access; blocked IP ranges now include `0.0.0.0/8`, IPv4-compatible IPv6 and 6to4.
- **Userscripts:** shorthand `@match` patterns, explicit ports, localized `@name`/`@description` keys and `@exclude-match` now work; injection order is consistent after a reconnect.
- **Discover:** caches store unfiltered results, so un-hiding an article or increasing the limit takes effect immediately.
- **Updates:** a prerelease no longer beats a stable release of the same numeric version.
- **Downloads:** literal `+` in filenames is preserved.
- **Cast proxy:** DASH `BaseURL` rewriting handles attributes, whitespace and XML entities; publishing fails clearly when no routable LAN address exists.
- **Database/preferences:** snippet edits no longer corrupt another row, parallel tab creation cannot duplicate sort order, transient DataStore errors no longer freeze all preferences, a saved locale survives an interrupted DataStore write, and shortcut rename/remove is case-insensitive. Schema export test now tracks the current database version.

### Fourth audit round

- **Engine:** displays are no longer stolen reflectively from an old GeckoView (avoids "Display already acquired" and orphaned surfaces); a session is deactivated before closing so its newest history/scroll state is flushed; built-in extensions use idempotent `ensureBuiltIn`, and native-messaging delegates are tracked per extension instance so a fresh instance still receives messages.
- **ViewModel:** closing a tab clears its prompt/permission dialogs before the observer is cancelled; popups from incognito tabs stay incognito; tabs created during restore are preserved instead of leaking their sessions; `.user.js#fragment` opens the installer; child tabs inherit the current group; bookmark star refreshes after delete/import; clearing history resets the per-tab visit guards; mirror publishing is invalidated by a Stop in flight.
- **UI/media:** the one-shot omnibox focus request survives the focus guard; the user's text in the script editor is refreshed when the script changes; media notification follows the tab that actually started playing and survives service start/stop races; Discover RSS quota is based on feeds that returned data; the Cast sheet no longer lists duplicate media from other tabs on the same URL.
- **Regression fix:** the RSS URL validator no longer performs DNS on the UI thread; address validation moved to the IO fetch path.

## [0.78.4] - 2026-11-24

### Fixed

- **Two-step back for manually created tabs.** With no page history and no opener tab, the back
  gesture first returns to the home/new-tab page (replacing and purging history so it cannot walk
  back into the page); only the next back gesture on that home page exits the browser.

## [0.78.3] - 2026-11-24

### Fixed

- **Back gesture on a tab opened from a link now closes that tab and returns to the originating tab**
  instead of closing the whole app. Popup/new-tab flows (`target="_blank"`, `window.open`, external
  links and links opened from lists) now record their opener; `PredictiveBackHandler` checks the
  opener before falling back to `finish()`. Added pure `TabOpenerPolicy` with 2 unit tests.

## [0.76.1] - 2026-11-23

Fixed random address bar focusing when scrolling upward on pages (`versionCode = 80`).

### Fixes

- **Prevented accidental omnibox focus during upward scrolling.** Hoisted `toolbarFocusGuard` / `wasToolbarHidden` above `AnimatedVisibility` in `ChromeBrowserScreen` so the 400 ms post-reappear guard survives toolbar disposal, consumed one-shot `omniboxFocusRequest` triggers immediately in `ChromeToolbar`, and temporarily disabled `BasicTextField` input while `focusGuard` is active.

## [0.76.0] - 2026-11-23

Rebranded browser UI & resources to **Foxium** and added custom RSS/Atom feed support to New Tab Page Discover (`versionCode = 79`).

### Branding & Features

- **Rebrand to Foxium.** Updated user-facing app name, EN/CS strings, New Tab Page & About headers, bookmark export filename (`foxium-bookmarks.html`), home-screen shortcut fallback, print job title, `MediaSession` tag, Cast mirror page title, userscript `User-Agent` / `GM_info.scriptHandler`, built-in extension display names, `settings.gradle.kts`, and `README.md` to **Foxium** (preserving `applicationId`, Room DB, KeyStore alias, and internal extension IDs for seamless in-place upgrades).
- **Custom RSS/Atom Feed for Discover.** Added feed type switching (`Hacker News` vs `RSS feed`) and custom RSS source management in both the New Tab Page Discover customize sheet and *Settings → Discover*, backed by XXE-safe `RssFeedParser` (RSS 2.0/1.0 + Atom 1.0, public `https://` validation via `NetworkTargetPolicy`), 2 MB bounded fetches with per-feed caching in `DiscoverRepository`, round-robin interleaving in `DiscoverMerger`, EN/CS localization, and unit tests (`RssFeedParserTest`, `DiscoverMergerTest` — 109 JVM tests total).

## [0.75.1] - 2026-11-22

Fix held Android edge back gestures triggering the web page long-press context menu (`versionCode = 78`).

### Fixes & Tests

- **Prevented context menu from opening during edge back gestures.** Added a 32 px edge touch guard and `pointercancel`/`scroll`/`blur`/`visibilitychange` timer cancellation in `sys_gestures/content.js`, switched `ChromeBrowserScreen` from `BackHandler` to `PredictiveBackHandler`, and suppressed queued/incoming `contextmenu/request` and GeckoView context menu emissions in `BrowserViewModel` during and immediately after a back gesture (`setBackGestureActive`), backed by `tools/check-gestures.mjs`.

## [0.75.0] - 2026-11-22

Query Parameter Stripping, custom search engine support, Netscape HTML bookmark import/export, and UI modularization (`versionCode = 77`, see `AUDIT.md`).

### Privacy & Browser Features

- **Query Parameter Stripping.** Enabled `ContentBlocking.Settings` query parameter stripping (`queryParameterStrippingEnabled` and `queryParameterStrippingPrivateBrowsingEnabled`) by default in `ChroGeckoRuntime`, backed by `AppPreferences.QUERY_PARAM_STRIPPING` and a live switch under *Settings → Privacy and security*.
- **Custom Search Engine.** Added `SearchEngines.CUSTOM_ID`, validated HTTPS `%s` URL template normalization (`SearchEngines.normalizeCustomTemplate`), DataStore persistence (`CUSTOM_SEARCH_NAME` / `CUSTOM_SEARCH_TEMPLATE`), `CustomSearchEngineForm` in Search Engine settings, and omnibox / New Tab Page integration (`activeSearchEngine`).
- **Netscape HTML Bookmark Import & Export.** Added `BookmarkHtmlCodec` (nested `<DL>` folder preservation, HTML entity escaping/unescaping, strict `http`/`https` filtering), transactional `BookmarkRepository.importHtml`/`exportHtml` deduplication, 10 MB SAF read cap in `BrowserViewModel`, and `BookmarksSheet` overflow import/export actions.

### Testing & Maintenance

- **Expanded instrumentation & unit test coverage.** Localized `AddonInstallConsentTest` (`R.string.install`/`R.string.cancel`) and added `ChromeSmokeTest.openingSettingsShowsPrivacyControls` (9/9 instrumented tests passing on `cs-CZ`), alongside new unit tests in `SearchEnginesTest` and `BookmarkHtmlCodecTest` (104 JVM tests across 27 files).
- **Split remaining oversized UI files.** Split `ChromeSheets.kt` (1,385 → 526 lines) into `LibrarySheets.kt` (513) and `AddonsAndUserscriptsSheets.kt` (416), and `ChromeBrowserScreen.kt` (2,099 → 1,121 lines) into `ChromeToolbar.kt` (582) and `ChromeBrowserOverlays.kt` (484).

## [0.74.0] - 2026-11-22

Privacy expansion, OkHttp redirect SSRF fix, expanded test coverage, and code-hygiene release (`versionCode = 76`, see `AUDIT.md`).

### Security & Privacy

- **Third-party cookie policy, cookie banner handling, and bounce tracking protection.** Added `CookiePolicy` (`ISOLATE_CROSS_SITE` Total Cookie Protection default, plus tracker blocking, third-party blocking, block all, and allow all), `CookieBannerMode` (`REJECT` default, `REJECT_OR_ACCEPT`, `DISABLED` including global and subframe rules), and redirect-based bounce tracking protection under *Settings → Privacy and security → Third-party cookies*.
- **Safe Browsing & Global Privacy Control.** Enabled `ContentBlocking.SafeBrowsing.DEFAULT` and `Sec-GPC` (`globalPrivacyControlEnabled`) by default, with live switches in *Settings → Privacy and security*.
- **Configurable HTTPS-Only mode.** Added `HttpsOnlyMode` (`ALL`, `PRIVATE_ONLY` default, `OFF`) in *Settings → Privacy and security → Always use secure connections*.
- **DNS over HTTPS (DoH).** Added `DohMode` (`OFF`, `INCREASED` / `TRR_MODE_FIRST`, `MAX` / `TRR_MODE_ONLY`) with Cloudflare, Quad9, NextDNS, and validated custom `https://` endpoint support (`DohUriValidator`).
- **Closed OkHttp IP-literal redirect SSRF bypass in Cast proxy.** `CastProxyServer` now disables automatic redirects (`followRedirects(false)`, `followSslRedirects(false)`) and routes hops through `executeFollowingSafeRedirects()`, validating IP literals and domains against `NetworkTargetPolicy` on every hop and stripping `Cookie`/`Authorization` across cross-site redirects.

### Testing & Maintenance

- **Expanded JVM & extension test suite.** Added `CastProxyServerTest` (6 proxy integration tests), `PrivacyPreferencesTest` (7 preference/DoH validator tests), `tools/check-reader.mjs`, and `tools/check-userscript-content.mjs`, reaching 100 JVM unit tests and 6 Node.js extension checks.
- **Full EN/CS pluralization & KTX cleanup.** Converted all 8 `PluralsCandidate` strings into `<plurals>` resources (with Czech `one`/`few`/`many`/`other` forms), replaced manual `SharedPreferences.edit().apply()` in `AppLocaleManager` with Android KTX `edit { ... }`, and reduced `lintDebug` to 0 errors and 24 warnings.
- **Split oversized settings UI.** Extracted `ChromeSettingsScreen.kt` (~2,418 → 1,355 lines) into cohesive files with `PrivacySettingsPages.kt`, `AutofillSettingsPages.kt`, and `SiteAndToolsSettingsPages.kt`.

## [0.73.0] - 2026-11-22

Audit remediation release (`versionCode = 75`) addressing P1–P3 security, privacy, correctness, and lint findings (see `AUDIT.md`).

### Security

- **Authenticated tab-mirror LAN endpoints.** `CastProxyServer` now gates `/mirror/{token}` and `/mirror/{token}/stream` behind a rotating `@Volatile` token issued by `publishMirror()` and cleared by `clearMirror()`. `CastManager.stopMirror()` and `BrowserViewModel.watchMirror()` clear the active token when mirroring stops.
- **DNS-level SSRF protection across redirects and hops.** `CastProxyServer.clientFor(originHost)` configures a custom OkHttp `Dns` resolver that checks `NetworkTargetPolicy::isNonPublic` and literal non-public host rules across cross-origin and redirect hops.
- **Hardened `GM_xmlhttpRequest` target filtering.** `sys_userscripts/background.js` restricts `handleXhr()` to `http:`/`https:` URLs and rejects loopback, private, link-local, and ULA destinations via `isBlockedXhrHost`.
- **Grant-gated Userscript `GM_*` APIs and page-scope isolation.** `sys_userscripts/content.js` gates `GM_*` and `GM.*` shims in `buildShims()` against `@grant` declarations (keeping `GM_info`, `unsafeWindow`, `GM_log`, and `GM_addStyle`), injects `GM_info`/`GM` into `pageScopeRunner()` / `executeInPageScope()`, and wraps execution in `try/catch`.
- **Reader Mode HTML sanitization.** `sys_reader/content.js` sanitizes parsed output via `sanitizeHtml()` (`DOMParser`), stripping active/form tags (`script`, `style`, `iframe`, `frame`, `frameset`, `object`, `embed`, `applet`, `form`, `input`, `button`, `select`, `textarea`, `meta`, `link`, `base`, `noscript`, `template`), `on*`/`srcdoc` attributes, and unsafe URI schemes (`javascript:`, `vbscript:`, `data:text/html`, `data:application/xhtml`).
- **Blocked direct `file://` navigation from the Omnibox.** `OmniboxUrlNormalizer` removed `file://` from `urlSchemes` (verified by `OmniboxUrlNormalizerTest.fileUrlIsNotLoadedDirectly`).
- **Explicit data extraction and backup rules.** Added `res/xml/data_extraction_rules.xml` disabling cloud backup and device transfer plus `res/xml/backup_rules.xml` for Android < 12, wired via `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/backup_rules"` in `AndroidManifest.xml`.

### Privacy

- **Incognito suggestion isolation.** `BrowserViewModel.refreshSuggestions()` returns empty suggestions when `isPrivate` is `true`.
- **Site permissions cleared with site data.** `BrowserViewModel.clearBrowsingData()` includes `StorageController.ClearFlags.PERMISSIONS` when `cookies` cleanup is selected.
- **Live Tracking Protection updates across open tabs.** `EngineSession.setTrackingProtection(enabled)` and `GeckoEngine.applyTrackingProtection(enabled)` propagate setting changes immediately to all active `sessions`.

### Bug Fixes & UX

- **Gestures domain stripping fix.** `sys_gestures/content.js` replaces invalid Kotlin-style `.removePrefix("www.")` with `.replace(/^www\./, "")`.
- **Preferred dark-mode engine respected on per-site toggle.** `DarkModeRepository.toggle()`, `DarkModeFeature`, and `BrowserViewModel.toggleSiteDarkMode()` pass the active `darkEngine.value` (`DYNAMIC` vs `INVERT`).
- **Extension `Open` action visibility.** `InstalledAddon.hasAction` (populated in `AddonManagerController.toAddon` and `updateAddon`) lets `AddonsSheet` render the `Open` button even before an action title is emitted.
- **Event-driven IME listener.** Replaced the `while(true) { delay(120) }` polling loop in `ChromeBrowserScreen` with `ViewTreeObserver.OnGlobalLayoutListener` inside `DisposableEffect(insetsView)`.
- **Android API level & resource fixes.** Guarded `POST_NOTIFICATIONS` permission launch for `Build.VERSION_CODES.TIRAMISU+`, updated `Haptics` constants (`UPSIDE_DOWN_CAKE` for `DRAG_START`/`TOGGLE_ON`/`TOGGLE_OFF`, `R` for `CONFIRM`), used `R.string.default_address_label` in `AutofillUi.AddressDialog` and `R.string.context_share_link` in `ContextMenuSheet`.

### Maintenance

- Normalized modifier parameter order in `ChromeNewTabPage`, removed unused `print_save_as_pdf` string (`values/strings.xml`) and duplicate `mirror_notification_text` (`values-cs/strings.xml`), updated `tools/check-userscript-background.mjs` tests, and cleaned stale temporary artifacts from the repository root.

## [0.72.0] - 2026-11-22

Security and privacy pass driven by the 0.72.0 audit (see `AUDIT.md`).

### Security

- **Installing an extension now requires consent.** `AddonManagerController` no longer answers
  GeckoView's install prompt with an automatic allow: the prompt is held until the user confirms in a
  native dialog that lists the extension's name, version, permissions and origins. Bundled system
  extensions still install silently. Third-party updates that request new permissions are refused,
  and optional permissions are denied instead of granted.
- **No more drive-by install.** A `.xpi` link no longer installs anything: the download path sends it
  to Downloads like any other file, and installation starts only from the file picker, an https URL
  picked in the menu, or the "Add to Firefox" button on an AMO page.
- **Extension URLs must be https**, so an add-on cannot be fetched over plaintext.
- **Tracking protection is enabled by default** (`useTrackingProtection`) and switchable in Settings
  → Privacy and security; the choice persists and governs every tab opened afterwards.
- **Private tabs refuse plaintext** connections (`allowInsecureConnections(HTTPS_ONLY_PRIVATE)`).
- **Userscript menu commands can no longer be forged by the page.** Commands travel through
  `browser.runtime` messaging per frame and are merged per tab in the extension background, so page
  scripts cannot inject menu entries or fire a registered handler; tab navigations clear them.
- **`allowBackup` is off** so cookie and history databases stop leaving the device through cloud
  backup.
- **The address bar no longer loads `data:`, `view-source:` or `blob:` URLs.** A pasted
  `data:text/html,...` page renders with no browser chrome, which makes it a convincing phishing
  surface; such input is now searched instead. `http`, `https`, `file` and `about` still load.
- **Casting links expire after 12 hours** and proxy responses are `Cache-Control: no-store`, so a
  leaked LAN URL stops working on its own and captured Cookie/Referer headers do not linger.

### Fixed

- **Userscripts and favicons are fetched over https only.** A userscript is executable code in every
  matching page, so an update check over plaintext was a remote code injection on hostile Wi-Fi;
  favicon lookups (including icons a page declares as `http://`) told the network which hosts the
  user visits. Both now refuse `http`. `usesCleartextTraffic` stays for GeckoView and plaintext media
  streams.
- **Cast proxy SSRF and credential scoping are covered by tests** — 10 new unit tests pin
  `NetworkTargetPolicy` (loopback, RFC1918, CGNAT, documentation, reserved and v4-mapped ranges) and
  `UrlDomainPolicy` (multi-label suffixes, case and trailing-dot normalisation).
- **The instrumented suite is green (8/8).** The `No such compose hierarchies found` failures were
  not caused by a busy device: the app requested the notification permission on its first screen, so
  the system dialog covered the activity before it ever drew its first frame. The prompt is now
  requested when a download starts (`DownloadManager.notificationPermissionRequests`), which is also
  when Chrome asks. Besides unblocking the tests this removes a dialog from the cold start path.
- **The cast LAN server stops with the cast session.** It used to stay bound to its port for the rest
  of the process after the receiver disconnected; `CastManager` now calls `proxy.stop()` on
  `onSessionEnded` and `onSessionStartFailed` (the next cast re-binds it lazily in `publish()`).
- **The extension install consent dialog is covered by tests** — three new instrumented tests
  (`AddonInstallConsentTest`) assert that the dialog shows the requested permissions and origins and
  that only its own Install/Cancel buttons answer the prompt.
- **Userscripts with `@grant none` run in the page realm.** They previously executed in the
  content-script sandbox, so scripts that read or write page globals (page libraries, `window.foo`)
  silently did nothing. They are now injected as a page `<script>` element — an `eval` from an
  exported function still evaluates in the sandbox compartment, which was measured — and pages whose
  CSP refuses inline scripts fall back to the sandbox. Scripts that use `GM_*` keep the isolated
  sandbox. Verified on device for both paths.
- **Lint gate is green again** — the two `[PropertyEscape]` errors came from the unescaped paths in
  `local.properties`.

### Changed

- Instrumented test dependencies bumped to `androidx.test` runner 1.7.0 / ext-junit 1.3.0 and
  Espresso 3.7.0, replacing the `compose.ui.test.junit4.v2` rule with the stable one. This removes
  the `NoSuchMethodException: android.hardware.input.InputManager.getInstance` crash that reddened
  the whole suite on Android 17.
- Unit tests grew from 80 to 86: the omnibox scheme policy, the cast SSRF policy and the credential
  scoping policy are now covered.

## [0.71.0] - 2026-11-22

### Fixed

- **Bottom toolbar stays visible while typing in the omnibox.** The IME-based hiding now applies
  only when the keyboard belongs to a web form; when the address bar itself has focus, its toolbar
  no longer disappears.

## [0.70.0] - 2026-11-22

### Fixed

- **Back gesture no longer randomly opens page info.** All back handling is now one deterministic
  handler with an explicit priority (sheet → find bar → tab grid → fullscreen → page history → exit),
  replacing several overlapping `BackHandler`s whose dispatch order could let the page branch win
  while an overlay was appearing.
- **Missing animations added:** search-suggestion dropdown (expand + fade), saved-login suggestion
  bar (expand + fade) and the AMO install button (scale + fade).

## [0.69.0] - 2026-11-22

### Changed

- **Bottom toolbar hides while typing in the page.** IME visibility is observed from the root view;
  when the keyboard opens, the bottom toolbar disappears so it cannot cover the web form/keyboard.
- **Closing a tab in the grid switcher keeps the switcher open.** The replacement tab becomes active,
  but the user stays in the tab overview; *Close all incognito* behaves the same way.

## [0.68.0] - 2026-11-22

### Fixed / Added

- **Extension popups open correctly with their real content.** The app now notifies GeckoView which
  native tab is active (`WebExtensionController.setTabActive`), so `Action.click()` no longer fails
  with `nativeTab is null` and Gecko's own popup pipeline loads the popup document.
- **Installation via the page's own "Add to Firefox" button.** `sys_gestures` intercepts clicks on
  AMO pages and asks the app to install the add-on through `WebExtensionController`, so the button
  no longer just downloads a file.

## [0.67.0] - 2026-11-22

### Added

- **One-tap installation from Firefox Add-ons (AMO).** When an `addons.mozilla.org/.../addon/<slug>/`
  page is open, a download button appears in the toolbar and installs that add-on from AMO's latest-XPI
  endpoint. `AmoInstallUrls` parses the page URL (3 unit tests); invalid pages report a snackbar.

## [0.66.0] - 2026-11-22

### Fixed

- **No more dark strip above the bottom toolbar or above the keyboard.** The overlay toolbars now
  respect `navigationBarsPadding()`, `imePadding()` and `statusBarsPadding()`, so they sit exactly
  above the system UI instead of being pinned to the raw screen edge.

## [0.65.0] - 2026-11-22

### Changed

- **Toolbar slide is smooth.** The toolbar is now an overlay: the page receives its padding in one
  step and only the toolbar's translation is animated, so GeckoView is no longer re-laid out on
  every animation frame. No black gap remains.
- **Pull-to-reload requires a longer, sustained pull** (170 px and at least 450 ms). The visual ring
  fills only when both conditions are met, so a quick flick no longer reloads the page.

## [0.64.0] - 2026-11-22

### Fixed

- **Functional audit fixes.** Omnibox swipe gesture now reacts to focus changes (text selection no
  longer switches tabs); undo of a batched close restores the originally active tab by its new id;
  per-site dark-mode switch reflects the global master switch and an explicit site rule can now turn
  dark mode on even when the master is off (in the extension too).
- Downloads: public Download files are covered by FileProvider on API 26–28 (no open/share crash);
  Cancel no longer overwrites a completed download or leaves its completion notification; orphaned
  MediaStore pending rows are cleaned up.
- Addons: action delegates are re-attached after enable/update; switching popups releases the old
  display before closing it.
- Discover now allows up to two articles per host instead of one, and search suggestions work for
  multi-word queries. Failed Cast loads roll back the optimistic playback state. MJPEG viewer loop
  terminates when capture stops or no frame arrives within 10 s. Native selection Share uses the
  latest selected text.

## [0.63.0] - 2026-11-21

### Changed

- **Text selection uses the real native Android popup.** Instead of a Compose-rendered toolbar,
  the browser now starts an `ActionMode.TYPE_FLOATING` with `Callback2.onGetContentRect()` using
  GeckoView's `Selection.screenRect`, so the platform draws its own Copy / Select all / Share menu
  with the standard overflow, positioned right above the selected text.

## [0.62.0] - 2026-11-21

### Changed

- **Text selection actions are now a native-style floating toolbar anchored near the selected text**
  instead of a bar at the bottom of the screen. GeckoView's `Selection.screenRect` is propagated
  through `SelectionMenu`, converted into page-local coordinates and used to place the Material 3
  `HorizontalFloatingToolbar` above the selection, clamped to the viewport.

## [0.61.0] - 2026-11-21

### Fixed

- **Toolbar no longer opens the keyboard when it slides back on scroll-up.** A short focus guard
  during the reveal clears any focus caused by the finger landing on the omnibox surface.
- **Extension installation.** `WebExtensionController.PromptDelegate` is now registered (auto-grants
  install/update/optional permission prompts), local `.xpi` installs use
  `INSTALLATION_METHOD_FROM_FILE`, and failures include the GeckoView install error code.

### Added

- **Downloads go to the public `Downloads` folder by default** (MediaStore on Android 10+, where the
  file appears in the Files app). *Settings → Downloads* can switch back to the app-private folder.
  Open/share/delete now handle both file paths and `content://` URIs.

## [0.60.0] - 2026-11-21

### Added

- **Multiple Discover sources.** The feed can now combine any subset of four Hacker News lists:
  *Top stories*, *Newest*, *Best* and *Show HN*. The source picker (in the new-tab sheet and in
  *Settings → Discover*) uses multi-select chips; the old single-source preference is migrated
  automatically. Repository caches each feed separately, merges and de-duplicates results, and
  `DiscoverMerger` (3 tests) and `DiscoverSources` (3 tests) are covered by unit tests.

## [0.59.0] - 2026-11-21

### Fixed

- **Toolbar auto-hide no longer leaves a black strip.** The top/bottom toolbar animations now use
  `expandVertically`/`shrinkVertically` (layout size) instead of a pure slide, so the Gecko surface
  grows continuously while the toolbar collapses.
- **Pull-to-refresh indicator cannot hang.** The gestures extension no longer throttles the terminal
  (0 / not-triggered) report, and the ViewModel clears any pull state via a timeout safety net even
  if the terminal message is lost (navigation, cancelled touch).

## [0.58.0] - 2026-11-21

### Fixed

- **Dark mode race in the bundled extension:** a storage read finishing after a native `dark/config`
  could overwrite the freshly pushed policy with a stale one. Native config now carries a sequence
  number; stale storage results and superseded persist writes are ignored. The policy is also
  re-broadcast on every page load from Kotlin, so a content script that started early still gets it.
- **Pull-to-refresh indicator was invisible:** it was composed before the `GeckoView` surface, which
  covered it. It is now drawn last (above the engine) and auto-hides shortly after the reload fires.

## [0.57.0] - 2026-11-21

### Fixed

- **Dark mode now applies reliably.** The content script re-requests the policy on `load`, `pageshow`
  (bfcache / same-document navigation) and re-evaluates the page luminance once deferred CSS has
  loaded, so a page that looked already-dark at `DOMContentLoaded` is darkened if it turns out light.
  The background's `dark/config` reply now includes per-site rules, which the content script keeps
  for later live updates. Added a Node self-check for the background reply.

### Added

- **Visual feedback for pull-to-refresh.** `sys_gestures` reports pull progress (0..1) and the
  triggered state to Kotlin over native messaging; a Compose indicator at the top of the page shows
  a filling progress ring, an arrow that flips at the threshold, a localized hint
  ("Pull/Release to reload") and a haptic tick when the reload is armed.

## [0.56.0] - 2026-11-21

### Added

- **Room migration smoke test** (`DatabaseMigrationTest`): builds a version-1 database by hand,
  inserts a row in every original table, runs the full 1→11 migration chain via the exposed
  `DatabaseModule.migrations()`, and verifies all 14 tables, the new columns and the preserved rows.
- **More ViewModel coverage through pure policies:** `ToolbarAutoHidePolicy` (5 tests) and
  `SuggestionMerger` (4 tests) extracted from `BrowserViewModel`, which now delegates to them.

## [0.55.0] - 2026-11-21

### Changed

- Final audit follow-ups: snackbar swipe uses the modern `SwipeToDismissBox` anchored-state pattern
  (no deprecated `confirmValueChange`), instrumented tests use the v2 Compose test rule, and a JVM
  test guards that the Room schema export exists. No remaining Kotlin compiler warnings.

## [0.54.0] - 2026-11-21

### Changed

- **Document-start userscripts run immediately.** The background plan is now split: scripts whose
  `@require`/`@resource` are cached execute at `document_start` without waiting for network fetches;
  scripts with uncached dependencies are marked `pending` and delivered via a `userscripts/plan-full`
  message once fetched. Execution is de-duplicated by script id.
- **Dialogs raised in background tabs** (e.g. `window.open`) are now published and can be answered;
  previously only the active tab's prompts/permissions reached the UI.
- **Proxy same-site logic extracted** to `UrlDomainPolicy` with tests; fixed multi-label public
  suffix handling (`bbc.co.uk` vs `evil.co.uk` no longer share a site).
- **Room schemas are exported** (`app/schemas/…/11.json`) so future migrations can be verified.
- Modernized Cast `seek` and a settings icon to non-deprecated APIs.

## [0.53.0] - 2026-11-21

### Fixed

- **Audit fixes (P0/P1).** Closing all incognito tabs no longer spawns a fresh private tab; Chrome
  now leaves incognito mode (or switches to a normal tab). Added `TabClosePolicy` + 5 unit tests.
- **Cast proxy SSRF hardened.** `NetworkTargetPolicy` blocks loopback, link-local, RFC1918, CGNAT,
  reserved and IPv6 ULA ranges for cross-host child URLs (plus 3 unit tests). Cookies/Authorization
  are now forwarded to CDN subdomains of the same registrable domain but never to unrelated hosts;
  invalid captured header values are skipped instead of failing the response.
- **Prompt/permission answers** are routed to the session that raised them, not to whichever tab is
  active at answer time (the API used to fail after an `ACTION_VIEW` handover or `window.open`).
- Hard-coded NTP search hint replaced with a localized, engine-aware string; download row tap now
  reports "no app can open" via toast; instrumented tests no longer depend on English strings.
- Omnibox swipe/pull gesture moved to the URL pill only and disabled while the field is focused, so
  text selection works again. Cast seek slider no longer snaps back to the old position.
- Mirror teardown runs on the capture worker, viewer count resets on clear; print jobs complete
  their Gecko result and delete leftover PDFs on tab close.
- Text snippet save updates a conflicting row in place instead of Room REPLACE deleting another
  snippet. `ACTION_VIEW` links survive activity recreation.
- Native messaging: failed port posts drop the stale port and report failure; fast reconnects no
  longer flip the connected state. Download cancel marks the row consistently and invalid URLs are
  reported; Cast load has a 15 s timeout. Favicons retry over HTTP for intranet hosts.
- Extension popups inherit incognito mode and release their display on close; userscript menu
  commands registered in iframes are forwarded to the top frame and routed back for execution;
  undoing a multi-tab close restores the originally active tab.

## [0.52.1] - 2026-11-20

### Changed

- **Refined haptics.** Replaced the uniform long-press vibration with a central `Haptics` helper
  that uses semantic platform feedback via `View.performHapticFeedback`: `GESTURE_START` for pull-down,
  `GESTURE_END` for tab/snackbar swipes, `DRAG_START` for reordering, `CONFIRM` for the toolbar
  shortcut, `CLOCK_TICK` for suggestion/row selection and `TOGGLE_ON`/`TOGGLE_OFF` for switches.
  Modern/premium devices map these constants to their own crisp haptic primitives while system
  haptic settings are still respected; older devices get `VIRTUAL_KEY`/`LONG_PRESS` fallbacks.

## [0.52.0] - 2026-11-20

### Added

- **Haptic feedback** on tab swipe-to-close, omnibox tab switching, pull-down to the grid, the
  optional toolbar button and picking a suggestion.
- **Toolbar auto-hide while scrolling.** `EngineSession` exposes Gecko's `ScrollDelegate` position;
  scrolling down hides the toolbar, any upward movement brings it back. Configurable in
  *Settings → Address bar* and disabled while the omnibox is focused, on the new tab page, in
  fullscreen or in the grid.
- **Toolbar shortcut.** *Settings → Address bar → Toolbar shortcut* adds one user-chosen button to
  the toolbar: Home, New tab, Bookmarks, History or Close tab (default: None).
- **Search suggestions with bookmarks and history.** While typing in the omnibox, completions from
  the selected engine (Google/DuckDuckGo suggest API) are merged with matching bookmarks and history
  entries. *Settings → Search suggestions* controls the master switch and both sources.
- Unit test for the suggest endpoint mapping.

## [0.51.0] - 2026-11-20

### Added

- **Discover channel settings.** The new tab feed can be turned off, switched between Hacker News
  *Top stories* and *Newest*, and sized to 6/12/20 articles. Individual articles can be hidden with
  a × button and all hidden ones restored at once. Settings live in a sheet opened from the feed
  header (tune icon) and in *Settings → Discover*; preferences are stored in DataStore and the feed
  reloads automatically on any change. The repository caches each source separately and skips
  hidden URLs.
- Unit test for the source-to-endpoint mapping.

## [0.50.1] - 2026-11-20

### Added

- **Swipe to dismiss the "Tab closed / Undo" snackbar.** Material 3's `SnackbarHost` in this version
  has no built-in swipe gesture, so each snackbar is now wrapped in `SwipeToDismissBox`; swiping it
  dismisses the snackbar just like tapping elsewhere, and the queued undo data is discarded.
  Covered by `TabGridSwipeTest`.

## [0.50.0] - 2026-11-20

### Changed

- **Czech translation finished.** All remaining hard-coded English UI text now comes from string
  resources: the extension/userscript/bookmark/history/download/cast sheets, address and card forms,
  permission and save-password dialogs, toolbar and settings descriptions, incognito page, print job
  name, snackbar messages and history day headings. Added 8 new resources (download completion,
  media notification actions/channel, print, mirror notification) with Czech equivalents.
- Format specifiers of every resource were checked against the Czech version; `app_name` stays
  non-translatable and brand/technical terms (JavaScript, Nightly, engine names) are intentionally
  kept.

## [0.49.0] - 2026-11-20

### Added

- **Swipe to close a tab in the tab switcher is now reliable.** The card keeps a single gesture
  owner (`CardGesture`): a horizontal swipe closes the tab, while a long press still reorders the
  card or opens its menu. The two detectors no longer consume each other's pointer events, so a
  slower swipe cannot be stolen by the long-press detector.
- Closing needs a 28% drag (was 35%) or a 600 px/s flick (was 900), so short swipes work on phones.
- The tab-counter button has an accessibility label (*Tabs*), and `TabGridSwipeTest` verifies the
  whole gesture end to end on a device (swipe → tab closed → *Undo* shown).

## [0.48.0] - 2026-11-20

### Added

- **Czech interface and language chooser.** All Compose strings now come from resources; the full
  Czech translation lives in `values-cs/strings.xml` and is declared in `locales_config.xml`.
  *Settings → Language* offers *System default*, *Čeština* and *English* and applies the choice live:
  Compose resources are overridden through `LocalResources`/`LocalConfiguration`, GeckoView's
  `Accept-Language` is updated through `GeckoRuntimeSettings.locales()`, and Android 13+ per-app
  locales are kept in sync (`AppLocaleManager`). Below Android 13 the activity base context is
  wrapped at attach time.
- **Android Autofill / third-party password managers.** `GeckoView`'s platform
  `AndroidAutofillDelegate` is chained with ChroGecko's own delegate in `AutofillController`, so
  Bitwarden, 1Password, KeePassDX and Google Password Manager can fill and save logins in web forms
  while the built-in save prompt and saved-login chips keep working. *Settings → Passwords* has a
  *Third-party password managers* switch and a row that opens Android's autofill-service picker and
  reports whether a service is active, not selected or unsupported. Private tabs never offer the
  platform service a save.

### Fixed

- Repaired the interrupted localization/autofill edit that left `ChromeTabGrid.kt` with missing
  closing braces and `ChromeSettingsScreen.kt` with missing imports and an unregistered Language
  route; replaced the non-existent GeckoView property `isAutofillEnabled` with `autofillEnabled`.
- Localized the remaining hard-coded English strings in settings, sheets, the incognito page,
  permission dialogs and the media notification.

## [0.47.0] - 2026-11-20

### Added

- **Renaming pinned shortcuts.** In the new tab page editor, tapping a pinned tile opens a *Rename
  shortcut* dialog with the current name filled in; the name is stored next to the URL in DataStore
  (`url|label`) and survives restarts. History- and bookmark-derived tiles keep their site name and
  are still removed with the × badge only.

## [0.46.0] - 2026-11-20

### Added

- **Discover on the new tab page.** Below the shortcuts the page now lists suggested reading
  (title, site and favicon, tap to open) loaded from the public Hacker News API through the shared
  OkHttp client, with a refresh button. Incognito tabs keep showing only the notice.
- **Pinned shortcuts.** *Edit* adds an *Add* tile; the dialog takes a site address and the shortcut
  is stored in DataStore, so pinned sites survive restarts and appear before history-derived tiles.
  The existing × badge removes both kinds.
- The new tab page scrolls, so shortcuts and the feed no longer compete for one screen.

## [0.45.0] - 2026-11-20

### Added

- **Bookmark folders.** The Bookmarks sheet is now navigable: folders appear above the bookmarks,
  a folder row shows its item count, and its header carries *Up* and a *New folder* button. Each
  bookmark's ⋮ menu offers *Move to folder…* (pick the root or any folder) and *Remove*, and a
  folder can be deleted from its own trash button — its bookmarks and subfolders move one level up
  instead of being lost (`bookmark_folders` table, `bookmarks.folderId`, database version 11).

## [0.44.0] - 2026-11-20

### Added

- **Download row menu.** Finished downloads show a ⋮ button with *Open with…*, *Share* and *Remove*.
  *Open with…* hands the file to the system chooser (Photos for images, which opens directly when it
  is the only handler), *Share* sends the FileProvider URI through the share sheet, and a MIME type
  nothing can handle now reports *No app can open this file* instead of silently doing nothing.

## [0.43.0] - 2026-11-20

### Added

- **Reader mode.** The overflow menu gained *Show reader view* / *Hide reader view*. Availability
  is decided per page with Mozilla's `isProbablyReaderable()` heuristic, the article is extracted
  with `Readability.js` (both vendored from `@mozilla/readability@0.6.0` under
  `assets/extensions/sys_reader/`, MIT licence kept as `READABILITY-LICENSE.md`) inside the bundled
  `sys_reader` extension, and the page is rebuilt into a single 44 rem column. Hiding restores the
  original nodes without a reload, so the scroll position survives.
- **Reader mode per site.** *Settings → Site settings* has a *Reader mode* switch; pages of that
  host turn the reader on automatically once they report as readable (`site_rules.readerMode`,
  database version 10).

## [0.42.0] - 2026-11-20

### Added

- **History grouped by day.** The history sheet now splits entries into Chrome's day sections
  (*Today*, *Yesterday*, then dated sections such as *Nov 17, 2026*), keeping the newest first.
  Grouping lives in `groupHistoryByDay()` and is covered by `HistoryGroupingTest`.
- **Search inside lists.** *History* (*Search history*), *Bookmarks* (*Search bookmarks*) and
  *Downloads* (*Search downloads*) gained a search field that filters by title, URL or file name
  and shows *No matches.* when nothing fits.

## [0.41.0] - 2026-11-20

### Added

- **Undo closed tab.** Closing a tab (or several through *Close other tabs* / *Close all incognito
  tabs*) shows Chrome's snackbar with an *Undo* action that recreates the tabs in the order they
  were closed, including their tab group, and only persists them again once restored.

## [0.40.0] - 2026-11-20

### Added

- **Download notifications.** Running downloads post a progress notification with a live
  percentage, transferred/total size and a *Cancel* action; finished downloads replace it with a
  *Download complete* notification carrying an *Open* action that launches the file through the
  app's FileProvider. Cancelling from the notification also dismisses it and marks the row cancelled
  in the Downloads sheet.

## [0.39.0] - 2026-11-20

### Added

- **Translate page.** The overflow menu offers *Translate…* for HTTP(S) pages and opens the page
  through Google Translate's proxy (`example-com.translate.goog`), carrying the device language as
  the target. The row hides itself on pages that are already translated.
- **Find in page navigation.** The find bar gained previous/next arrows alongside the match counter,
  so matches can be stepped through without retyping the query.

## [0.38.0] - 2026-11-20

### Changed

- **Toolbar matches Chrome.** The tab counter moved to the leading edge, the permanent Share button
  is gone (Share lives in the overflow menu) and the omnibox no longer grows a reload button: while a
  page loads the pill shows a stop cross, once it settles the pill is plain, and the microphone only
  appears while the omnibox has focus. Pull-to-refresh and the menu keep reload reachable.

## [0.37.0] - 2026-11-20

### Added

- **New tab page search field.** A pill reading *Search Google or type URL* sits above the wordmark;
  tapping it focuses the omnibox, as Chrome does.
- **Editable shortcuts.** *Edit* switches the most-visited tiles into edit mode with a remove badge on
  each tile, *Done* leaves it, and *Restore* brings hidden sites back. Removals persist as a host set
  in DataStore and the grid pulls further entries from history to stay full.

## [0.36.0] - 2026-11-20

### Added

- **ChroGecko is a browser target.** `MainActivity` handles `ACTION_VIEW` for `http`/`https`, so links
  from other apps (and pinned shortcuts) open as a new tab. The intent waits for the saved session to
  restore, otherwise the restore replaces the tab list and the handed-over URL disappears.
- **Add to Home screen** in the overflow menu pins a shortcut to the current page
  (`ShortcutManagerCompat`); the shortcut re-enters the activity with the page URL.

### Changed

- The overflow menu quick row follows Chrome's order — Forward, Bookmark, Download, Page info,
  Reload — and *Share…* moved to the list, matching Chrome.
- The new tab page labels the tile grid **Most visited**, as Chrome does.

## [0.35.0] - 2026-11-20

### Added

- **Text expander.** Settings → **Text expander** keeps a table of snippet triggers and their
  expansions (Room `text_snippets`, schema v9). Typing a trigger followed by a space, Enter or Tab
  in any page text field replaces it with the expansion; `{{date}}`, `{{time}}` and `{{datetime}}`
  are resolved at expansion time. The master switch and every snippet have their own toggle, and a
  change reaches open tabs without a reload.
- Bundled `sys_expander` WebExtension (`app/src/main/assets/extensions/sys_expander/`) mirrors the
  table from Kotlin over the `"chrogecko"` native messaging port and does the DOM work, so expansions
  work inside cross-origin iframes too (`all_frames`).
- `tools/check-expander.mjs` exercises the content script in Node with a fake DOM, covering the
  keydown path, the IME path and the rejection cases.

### Notes

- Android soft keyboards usually commit text instead of sending `keydown`, so the script expands
  from `input` mutations as well; synthetic events are ignored so the replacement cannot re-enter.

## [0.34.0] - 2026-11-20

### Added

- **Per-site settings.** Settings → **Site settings** keeps a rule per host: *JavaScript* on/off and
  *Desktop site*. Rules are stored in Room (`site_rules`, schema v8), applied from
  `EngineSession.siteRuleApplier` inside `NavigationDelegate.onLoadRequest` — the only point where
  `allowJavascript` and the user-agent mode still affect the document being loaded — and the active
  tab reloads as soon as a rule for its host changes.

## [0.33.0] - 2026-11-19

### Added

- **Print to PDF.** The overflow menu gained **Print**, which asks Gecko to render the current page
  to PDF and hands it to the Android print spooler (`PrintManager` + a `PrintDocumentAdapter`
  streaming the file), so the page can be printed by a service or saved as a PDF.

### Fixed

- The print delegate has to be attached **after** `GeckoSession.open()`; set earlier, Gecko never
  calls it (same registration timing as autofill). `printPageContent()` only signals the app — the
  PDF arrives through `didPrintPageContent()` and `onPrintWithStatus(...)`.

## [0.32.0] - 2026-11-19

### Added

- **Tab card menu.** Long-pressing a tab in the grid opens a sheet with *Group with…*, *Duplicate*,
  *Close* and *Close other tabs*; the group picker lists the other tabs of the same profile.
- `BrowserViewModel.duplicateTab(id)` and `closeOtherTabs(id)`.

### Changed

- Dragging a tab card onto another now **reorders** the grid (`tabs.sortOrder`) instead of grouping
  the two; grouping moved to the card menu and the grid header **Group** button.

### Fixed

- `java.lang.IllegalStateException: Display already acquired` no longer kills the app when tabs are
  switched or closed quickly. The `GeckoView` instance is kept across tab switches and the previous
  session's display is released before `setSession`; `EngineSession.releaseDisplay()` frees a
  display stranded by a detached view (`GeckoSession.getDisplay()` is package-private, so it is
  reached reflectively).

## [0.31.0] - 2026-11-19

### Added

- **Drag to reorder tabs.** Dropping a tab card on another moves the dragged tab to that slot and
  renumbers `tabs.sortOrder`, so the order survives a restart and drives the omnibox swipe and the
  bottom tab strip.

### Changed

- Grouping by dragging is gone: tab groups are formed from the grid header **Group** button
  (or, since 0.32.0, the card menu).

## [0.30.0] - 2026-11-19

### Added

- **Address and payment-method autofill.** GeckoView exposes no address/card autofill API, so
  this ships as a fifth bundled extension, `sys_autofill@chrogecko.browser`: its content script
  maps `autocomplete` tokens (`name`, `street-address`, `address-level2`, `postal-code`,
  `country`, `tel`, `email`, `cc-name`, `cc-number`, `cc-exp`, `cc-csc`) with a name/id/placeholder
  regex fallback, reports the focused form over native messaging, and fills the chosen entry with
  proper `input`/`change` events. Addresses live in a new `addresses` table and cards in `cards`
  (number encrypted with the same Keystore secret box, only the last four digits are ever shown);
  Room version 7, `MIGRATION_6_7`.
- **Autofill strip above the page** with one chip per matching saved entry, plus
  **Settings → Addresses and more** to add or remove addresses and payment methods.

### Fixed

- `BrowserViewModel` now routes `autofill/fields` messages from the extension straight into the
  chip bar instead of dropping them in the context-menu branch.

## [0.29.0] - 2026-11-19

### Added

- **Saved logins with in-page autofill.** Passwords typed into a login form raise a
  "Save password?" prompt; saved credentials encrypt with an AES/GCM key held in the Android
  Keystore (alias `chrogecko.logins`, 12-byte IV prefix + 128-bit tag) and live in a new `logins`
  table (Room v6, `MIGRATION_5_6`, unique index on host + username). Focusing a login form shows a
  suggestion chip for the matching host; tapping it fills both fields through
  `GeckoSession.autofillSession.autofill(...)`.
- **Passwords manager.** Settings → Passwords lists stored logins and deletes them.

### Fixed

- **Autofill delegate was never called.** `setAutofillDelegate` has to run after the session has
  started: Gecko rebuilds its autofill listener on the first navigation, so a delegate attached in
  the constructor was silently dropped and no `onSessionStart`/`onNodeFocus`/`onSessionCommit`
  arrived. `EngineSession` now attaches it from the first `ProgressDelegate.onPageStart`.
- **Session commit had no values.** `onSessionCommit` reports the form node with hint `NONE` and no
  value, so the credentials are read from the session's own nodes via `autofillSession.dataFor(...)`,
  and `Autofill.Node.domain` (a full URL) is reduced to a bare host.

### Verified on device

- `login_auto.html` submitting `demo` / `secret123` raised the prompt, storing
  `localhost|demo|37 bytes` (12 IV + 9 password + 16 tag); reloading the page with the username
  field focused showed the `demo` chip, and tapping it filled both inputs (`•••••••••`).

## [0.28.0] - 2026-11-19

### Added

- **Background audio and video with system media controls.** `MediaPlaybackService` is a
  `mediaPlayback` foreground service that publishes a platform `MediaSession` and a `MediaStyle`
  notification (previous / play-pause / next) for whatever the active Gecko session is playing.
  `EngineSession` now sets a `MediaSession.Delegate` before `open()`, mirrors Gecko's metadata,
  features and position into the notification and forwards notification taps back to Gecko.
- `GeckoSessionSettings.suspendMediaWhenInactive(false)` keeps audio running when the app is
  backgrounded, which is what makes the notification useful.
- New manifest entries: `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission and the
  `android:foregroundServiceType="mediaPlayback"` service.

### Fixed

- Notification title falls back to the page title when the media element has no metadata title.
- Deactivating a tab no longer tears the notification down while audio keeps playing; the session is
  released only when playback actually stops.

### Verified on device

- Emulator: `tools/testpages/media2.html` played a tone, the notification appeared with the page title,
  pressing Home kept `state=PLAYING` in `dumpsys media_session`, and `cmd media_session dispatch pause`
  / `dispatch play` paused and resumed the page.

### Notes

- Autoplay of audible media stays blocked (GeckoView's default), matching Chrome's policy.

## [0.27.0] - 2026-11-19

### Added

- **Fullscreen video and page elements.** `EngineSession` implements
  `ContentDelegate.onFullScreen`, so a page calling `requestFullscreen()` or a player going fullscreen now
  hides the omnibox, the progress bar, the find bar, the selection bar, the cast controller and the tab
  group strip, and hides the system bars through
  `WindowInsetsControllerCompat.hide(systemBars())` with transient bars by swipe.
- Back exits fullscreen first (`GeckoSession.exitFullScreen()`) and only then walks history, matching
  Chrome. `BrowserViewModel.exitFullscreen()` is the entry point.

### Verified on device

- `tools/testpages/fullscreen.html` on the emulator: tapping *Go fullscreen* hid the status bar and grew
  the page to the full 1080x2400 surface; Back restored the toolbar and the system bar without
  navigating away, and the process stayed alive.

## [0.26.0] - 2026-11-19

### Added

- **Site dialogs and permissions.** `EngineSession` now implements `PromptDelegate` and
  `PermissionDelegate`: `alert`, `confirm`, `prompt`, `select`, HTTP auth, file upload, popup, and the
  geolocation / notification / camera / microphone permission asks all render in Compose.
  `EnginePromptDialogs.kt` hosts the dialogs, the SAF picker for file uploads, and the runtime
  permission launcher; `EnginePrompts.kt` carries the payloads between Gecko and the UI.
- `window.open` popups open as a new tab through `BrowserViewModel`, and HTTP auth is answered from a
  user/password dialog instead of silently failing.
- Camera, microphone and location permissions were added to the manifest.

### Fixed

- Geolocation and desktop notifications ask the user even when Gecko has no stored decision; GeckoView
  reports an implicit deny, so the old pass-through made `getCurrentPosition` fail instantly.

### Verified on device

- `alert()` and `confirm()` render with the page text and answer correctly.
- Known gap: tapping a page's file input did not open SAF on the test AVD, so the upload path is
  wired but unverified on device.
- `navigator.geolocation` on `http://localhost:8099` shows the location dialog, *Allow* triggers the
  Android runtime permission prompt, and granting proceeds into Gecko's provider.

## [0.25.0] - 2026-11-19

### Added

- **Clear browsing data** in Settings, mirroring Chrome's dialog: tick browsing history, cookies and
  site data, cached images and files, or downloaded files, then tap *Clear data*. History and download
  rows are deleted from Room, cookies/storage go through
  `StorageController.clearData(COOKIES | DOM_STORAGES | SITE_DATA)` and caches through
  `ClearFlags.ALL_CACHES`, so site logins and cached assets disappear together.
- `ChroGeckoRuntime.clearData(flags)` exposes the Gecko `StorageController` to the feature layer.

### Verified on device

- Loading `wikipedia.org` left 1 history row and cookies in `moz_cookies`; after *Clear data* the
  history count was 0 and `moz_cookies` held 0 rows, while the app kept running (pid unchanged).

## [0.24.0] - 2026-11-19

### Added

- Long-pressing text now raises Chrome's selection bar with **Share**, **Done** and, when Gecko reports
  them, **Copy** and **Select all**. The bar is driven by `GeckoSession.SelectionActionDelegate`, so it
  appears for pages where a long press turns into word selection.
- `sys_gestures` can report long-press targets (links, images) to the app over native messaging:
  the extension got the `geckoViewAddons` permission and a background page that forwards page
  messages to the `chrogecko` port.
- The app renders a context menu for those reports: open in a new or incognito tab, copy or share a
  link, download a link or an image, copy an image address.
- `DownloadManager.enqueueUrl` downloads a URL the user picked in the menu, keeping the page as
  `Referer` for hotlink protection.

### Fixed

- `MediaRouteButton` no longer crashes the Cast sheet (`background can not be translucent`) now that
  the theme defines the appcompat `colorPrimary`/`colorAccent` pair mediaremote reads.
- Executing an action the current selection did not advertise is caught instead of crashing the app
  with `IllegalStateException: Action not available`.

### Known limits

- A long press on a link still shows the selection bar first: GeckoView turns the gesture into word
  selection before the page script sees it, so the link menu only appears when the extension's
  timer wins the race.

## [0.23.0] - 2026-11-19

### Added

- Screen mirroring can be stopped without hunting for the app: the foreground notification carries a
  **Stop** action, and the projection is also dropped when the system revokes it (the MediaProjection
  callback now releases the virtual display and stops the service). Closing the service clears the
  frames, so the Cast sheet switches back to the "Mirror" button instead of advertising a dead URL.

### Fixed

- `MirrorCaptureService` returned `START_STICKY`, which asked the system to restart a service that
  cannot capture without the one-shot consent intent.

## [0.22.0] - 2026-11-19

### Added

- Instrumented smoke tests (`app/src/androidTest/java/com/chrogecko/browser/ChromeSmokeTest.kt`).
  They launch `MainActivity` against the real application class, so the GeckoView runtime, the
  bundled `sys_*` extensions and Room all boot; one asserts the new tab page placeholder, the other
  types into the omnibox and checks the text survives. Build wiring: `testInstrumentationRunner =
  "androidx.test.runner.AndroidJUnitRunner"`, compose `ui-test-junit4` and `ui-test-manifest`
  dependencies, README commands for `connectedDebugAndroidTest` and the split-APK variant.

## [0.21.0] - 2026-11-19

### Fixed

- Typing in the omnibox after tapping it wiped nothing: the tap moved the cursor into the URL and
  the typed text was inserted in the middle of it. The first character typed after focusing now
  replaces the whole URL, which is what Chrome does; later edits and deletions are applied as typed.
  The placeholder-URL case (new tab page) is unaffected.

## [0.20.0] - 2026-11-19

### Added

- Search engine picker in Settings: Google, DuckDuckGo, Bing, Brave Search and Startpage. The
  choice is persisted in DataStore and free text typed in the omnibox is sent to that provider
  instead of the previously hardcoded DuckDuckGo URL.

### Changed

- `normalizeOmniboxInput` takes the provider template (`%s` placeholder) as a parameter; the
  default stays DuckDuckGo so existing behaviour is unchanged until a provider is picked.

### Verified

- Emulator: picked Google, searched `gecko browser` -> `https://www.google.com/search?q=gecko+browser`;
  after `force-stop` and relaunch a second search still went to Google.

## [0.19.0] - 2026-11-19

### Added

- Cast sheet can mirror the screen: MediaProjection capture runs in a foreground
  `MirrorCaptureService`, encodes frames as JPEG and serves them as
  `multipart/x-mixed-replace` MJPEG from the existing LAN cast server, so any browser on the
  network can watch `http://<lan-ip>:8765/mirror`. The sheet shows the URL once mirroring starts.

### Fixed

- Opening the Cast sheet crashed with `IllegalArgumentException: background can not be translucent`
  from `MediaRouterThemeHelper`: the app theme defined only `android:colorPrimary`/`android:colorAccent`
  while mediarouter reads the appcompat attributes. Both are now set, and the `MediaRouteButton`
  is created with a `ContextThemeWrapper` on `Theme.ChroGecko`.
- Mirroring failed silently when the foreground-service start was rejected while the consent
  activity was still finishing; it now retries a few times and logs the reason.

### Notes

- The Default Media Receiver cannot play MJPEG, so mirroring targets LAN browsers; casting a page
  still uses the sniffed media URL through the authenticated proxy.

### Verified

- Emulator: consent granted for "Entire screen", `virtual display=5 540x1200`, first frame
  10800 bytes, and `curl http://127.0.0.1:8765/mirror/stream` returned 413098 bytes with 38 valid
  JPEG parts.

## [0.18.0] - 2026-11-19

### Added

- Tabs survive a restart with their full session: `GeckoSession.SessionState` is persisted per tab
  (new `tabs.sessionState` column, Room v5) and replayed with `restoreState()`, so back/forward
  history and scroll position come back. Saving happens on every committed navigation.
- Hardware back walks page history before it leaves the app (Chrome behaviour); with no history
  left it finishes the activity.

### Changed

- Debug builds are ABI-split as well, so the debug APK is now e.g. `app-x86_64-debug.apk`.

### Verified

- Emulator: two navigations produced a 2.4 KB session state; after `force-stop` and relaunch the
  restored session reported 3 history entries and successive back presses moved the tab from
  `/en-US/emozilla.org` back to `/en-US/example.com`.

## [0.17.0] - 2026-11-19

### Changed

- Release builds now ship one APK per ABI instead of a single 504 MB universal APK:
  `arm64-v8a` (~177 MB), `x86_64` (~195 MB), `armeabi-v7a` (~142 MB), plus
  `app-universal-release.apk` (~480 MB) for sideloading to unknown devices.
- `x86` is not in the split list: GeckoView 146 ships no 32-bit x86 libraries, so such an APK
  contained no engine at all.

### Verified

- `app-x86_64-release.apk` installed on the emulator: process stays up, all three bundled
  extension ports connect.

## [0.16.0] - 2026-11-19

### Fixed

- Bundled extensions can finally talk to Kotlin. GeckoView routes `browser.runtime.connectNative`
  to the embedding app only for extensions holding the `geckoViewAddons` permission; without it the
  call fell through to `NativeManifests.sys.mjs` and failed with `Native manifests are not supported
  on android`. `sys_userscripts`, `sys_darkmode` and `sys_mediasniffer` now declare the permission
  (version `1.0.1`), and all three ports connect at startup.
- `NativeMessagingBridge` logs both directions (`port connected`, `ext -> type`, `app -> type`) so
  the channels are observable in `logcat`.

### Verified

- Emulator run: `port connected` for `sys_userscripts`, `sys_darkmode` and `sys_mediasniffer`;
  inbound `userscripts/ready`, `userscripts/status`, `dark/ready`, `dark/status`, `sniffer/ready`
  and `media/snapshot`; outbound `dark/config` and `userscripts/sync` on connect.

## [0.15.0] - 2026-11-19

### Added

- Real site icons. GeckoView 146 ships neither a favicon API nor a JavaScript-evaluation API, so
  `FaviconRepository` resolves icons over HTTP: it expands the `<link rel="icon">` hrefs found in
  the site's own HTML, downloads the first that decodes (falling back to `/favicon.ico`), and caches
  it in Room (`favicons` table, DB v4, 300-entry cap).
- Icons now render in the new tab page tiles, the grid tab switcher footer, the bottom tab group
  strip and the bookmarks/history sheets, replacing the letter badges when an icon is known.

### Fixed

- `NativeMessagingBridge.register` always hops to the main looper, because `setMessageDelegate` is
  documented as `@UiThread` while `GeckoResult` callbacks arrive on other threads.

### Verified

- 27 unit tests green: `IconHrefsTest` covers relative/absolute links, dedupe, the four-link cap and
  bad input, and `NewTabShortcutTest` now builds the `host` field.
- Emulator run: visiting `github.com` and `mozilla.org` filled the `favicons` table
  (`github.com -> https://github.com/fluidicon.png`, 33 KB; `mozilla.org -> apple-touch-icon`, 467 B).

## [0.14.0] - 2026-11-18

### Added

- Chrome-style new tab page. A blank tab renders a Compose page over the engine instead of the
  white `about:blank`: wordmark, up to eight most-visited tiles (bookmarks first, then history, one
  tile per host, `www.` stripped) and, for private tabs, Chrome's incognito notice with no tiles.
- Tiles load in the current tab; the omnibox shows its "Search or type URL" hint (not
  `about:blank`) while the new tab page is visible.

### Changed

- `buildNewTabShortcuts` ranks and dedupes shortcuts; it uses `java.net.URI` rather than
  `android.net.Uri` so it is testable on the JVM.

### Verified

- On the debug APK after `-wipe-data`: empty history shows the wordmark plus "Your most visited
  sites will show up here"; after visiting `https://example.com/` a new tab shows the "E" tile
  labelled "Example Domain"; tapping it loads the page; a new incognito tab shows "You're incognito".
- 19 unit tests green, including three for shortcut ranking.

## [0.13.0] - 2026-11-18

### Added

- Drag-and-drop tab groups in the grid: long-press a card and drag it onto another one. The dragged
  tab joins the target's group when the target already has one, otherwise a new group is created
  holding both. The card under the pointer is highlighted, the dragged card dims, and the grouping
  is persisted to Room immediately.

### Removed

- Long-press-to-group single tab (`BrowserViewModel.groupTabs`); long-press is now the drag handle
  and the menu's "Group" action still groups every normal tab at once.

### Verified

- On the debug APK: two ungrouped tabs dragged together produced one `tab_groups` row with both
  `tabs.groupId` values set; dragging a third tab onto a grouped tab merged it into the existing
  group (still one group, three grouped tabs).

## [0.12.0] - 2026-11-18

### Fixed

- The app no longer dies seconds after launch. `ChroGeckoApplication` ran Gecko runtime creation,
  bundled extension installation and Google Cast initialisation in every process, including
  GeckoView's `:tabN`, `:gpu` and `:crashhelper` children. Blocking those children's startup made
  Android kill them, which took the browser process with it. Initialisation now runs only in the
  main process.
- `CastContext.getSharedInstance` moved off the main thread; Dynamite module loading was blocking
  the UI thread for seconds.
- Submitting from the keyboard now clears omnibox focus first, so the IME cannot drop focus before
  the navigation action is delivered.

### Verified

- Signed release APK on API 35 x86_64 emulator: one Gecko runtime, stable process, all four bundled
  extensions installed, omnibox navigation to `https://example.com/` committed (title
  "Example Domain" recorded in history and tabs).

## [0.11.0] - 2026-11-18

### Added

- Download streaming progress (`downloadedBytes`, 256 KiB report interval), cancel via a tracked
  job map, `STATUS_CANCELLED`, and a completion notification on the `downloads` channel.
- Room v3 migration `MIGRATION_2_3` adding `downloadedBytes`; downloads sheet shows progress and a
  Cancel button while running.
- Extension badges: the menu "Extensions" subtitle lists extensions that expose an action badge.

## [0.10.0] - 2026-11-18

### Added

- Toolbar position preference (`toolbar_position`, top/bottom) with a Settings switch; the screen
  builds the toolbar once as a composable lambda and renders it above or below the page.
- Omnibox actions: voice search (`RecognizerIntent`) when the field is focused, share sheet when it
  is not.
- `sys_gestures` built-in extension: pull-to-refresh at page top (touch handlers, 110 px threshold,
  horizontal slop, 1.5 s cooldown), installed by `SystemExtensionsInstaller`.

## [0.9.0] - 2026-11-18

### Added

- Tab group editing: rename, recolour from a six-colour palette, delete group (tabs survive and are
  ungrouped). Changes persist through `TabGroupDao.update` / `deleteGroup`.
- Recent tabs: `TabDao.observeRecent(50)` + `TabRepository.recentTabs`, Chrome menu row and
  `RecentTabsSheet`; selecting an entry opens it in a new normal tab.
- Fixed restored group colours sign-extending negative Room ints (`and 0xFFFFFFFFL`).

## [0.8.0] - 2026-11-18

### Added

- Cast mini controller (device, title, play/pause, stop, expand) shown above the tab group strip,
  and an expanded controller dialog with scrub slider, timing labels, pause/resume, stop and
  disconnect; `BrowserViewModel.seekCast`.
- Private tabs force the charcoal dark palette for every Compose surface via an inner
  `ChroGeckoTheme(darkTheme = systemDark || isPrivate)` wrapper.
- Extension popup sheet is viewport-relative (62%, min partial state) instead of a fixed 420dp box,
  so tall popups can be dragged open.

### Changed

- `MainActivity` no longer wraps the screen in a theme; `ChromeBrowserScreen` owns it so incognito
  can override the palette.

## [0.7.0] - 2026-11-18

### Added

- Userscript install review dialog: metadata, grants, match count and scrollable source preview before
  `Install`; URL, file and pasted installs all route through it.
- `@resource` support: the privileged extension resolves resources (2 MB per resource) and exposes
  `GM_getResourceText` / `GM_getResourceURL` (data URLs) plus promise forms on `GM`.
- `GM_registerMenuCommand` commands for the active page are rendered in the three-dot menu and
  dispatched back to the extension with the originating Gecko tab id.
- Menu commands are keyed by page URL instead of the internal session id (Gecko tab ids are not
  app session ids).

## [0.6.0] - 2026-11-18

### Added

- Downloads: `DownloadManager` streams `WebResponse` bodies to the app Downloads directory, records
  each transfer in a new Room `downloads` table (migration 1 -> 2), and exposes a Downloads sheet
  with open/remove/clear actions via `FileProvider`.
- `.xpi` downloads are routed to `AddonManagerController` and `.user.js` navigation is intercepted
  and handed to `UserScriptFeature` instead of rendering the script source.
- `ContentDelegate.onExternalResponse` and `NavigationDelegate.onLoadRequest` hooks on
  `EngineSession`.
- Download file-name parsing tests (Content-Disposition, encoded names, path fallback, sanitizing).

## [0.5.0] - 2026-11-18

### Added

- Tab and tab-group persistence: normal tabs are restored from Room on launch (URL, title, order,
  group membership and last active tab). Tab creation, navigation/title changes, closes, selection
  touches and group assignments are written through `TabRepository`.
- Incognito tabs are never persisted and stale private rows are removed at startup.

## [0.4.0] - 2026-11-18

### Added

- Chrome-style Compose UI: pill omnibox, swipe between tabs, pull-down to the tab grid, 2-column
  grid switcher with Standard/Incognito profiles and captured thumbnails, tab groups with a bottom
  group strip, three-dot overflow menu, find-in-page bar and Chrome dark palette.
- Sheets for extensions, userscripts, bookmarks, history, casting and settings, plus a full-screen
  userscript editor and an extension action popup host.
- `AddonManagerController` for `.xpi` installation from SAF, arbitrary URLs and AMO, enable/disable
  and action popup sessions.

## [0.3.0] - 2026-11-18

### Added

- `UserScriptFeature` with userscript metadata parsing, install from file/URL/source, enable,
  delete, edit, and update checks.
- `DarkModeFeature` with global force-dark switch, invert/dynamic engine selection, per-site rules
  and live policy pushes to `sys_darkmode`.
- `CastManager` and `CastProxyServer`: Ktor LAN proxy on ports 8765-8785, OkHttp upstream requests
  with captured headers, HLS and DASH playlist rewriting, Cast session/media control.
- `ChroGeckoCastOptionsProvider`, OkHttp network module, userscript parser and HLS rewriter tests.

## [0.2.0] - 2026-11-18

### Added

- Privileged `sys_userscripts` extension: `@match`/`@include`/`@exclude` matching, `@require`
  caching, GM value storage, `GM_addStyle`, menu commands, `unsafeWindow`, and a background
  `GM_xmlhttpRequest` proxy that bypasses page CORS.
- Privileged `sys_darkmode` extension: native luminance detection, CSS invert engine with
  counter-inverted media, computed-style dynamic engine with reversible inline overrides, and a
  native-messaging policy channel for live per-site switches.
- Privileged `sys_mediasniffer` extension: `<video>`/`<audio>` source discovery plus webRequest
  capture of progressive, HLS and DASH URLs with request headers for the cast proxy.
- `SystemExtensionsInstaller` installs all bundled extensions from APK assets at startup.
- Node self-checks for userscript matching and background messaging.

## [0.1.0] - 2026-11-18

### Added

- GeckoView-based browser core with a process-wide runtime, configurable color scheme, and
  normal/private session contexts.
- Session state flows for URL, title, progress, loading, history and security state.
- Native messaging bridge between privileged WebExtensions and Kotlin.
- Room persistence for tabs, tab groups, bookmarks, history, per-site dark mode rules, and
  userscript metadata with repositories.
- Cast SDK options provider for the Default Media Receiver.
- Initial Compose browser screen with a Chrome-style omnibox, progress indicator, reload/stop,
  and a live GeckoView surface.
- Unit tests for omnibox URL normalization.
