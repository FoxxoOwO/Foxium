package com.chrogecko.browser.feature.chromeui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrogecko.browser.R
import com.chrogecko.browser.core.ui.Haptics
import com.chrogecko.browser.feature.search.SearchSuggestion
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChromeToolbar(
    omnibox: String,
    focused: Boolean,
    isLoading: Boolean,
    isSecure: Boolean,
    isPrivate: Boolean,
    isNewTab: Boolean,
    toolbarBottom: Boolean,
    hasExtensionBadge: Boolean,
    tabCount: Int,
    onOmniboxChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onPageInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenTabs: () -> Unit,
    onMenu: () -> Unit,
    onSwipeTab: (Int) -> Unit,
    onPullDown: () -> Unit,
    onVoice: () -> Unit,
    amoAddonSlug: String?,
    onInstallAmoAddon: () -> Unit,
    focusGuard: Boolean,
    toolbarShortcut: String,
    suggestions: List<SearchSuggestion>,
    onNewTab: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onCloseTab: () -> Unit,
    onSuggestionPick: (SearchSuggestion) -> Unit,
    focusRequest: Int,
    onFocusRequestConsumed: () -> Unit,
) {
    val omniboxRequester = remember { FocusRequester() }
    val hapticView = LocalView.current
    val currentOnSwipeTab by rememberUpdatedState(onSwipeTab)
    val currentOnPullDown by rememberUpdatedState(onPullDown)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(focusGuard) {
        if (focusGuard) focusManager.clearFocus()
    }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            onFocusRequestConsumed()
            if (!focusGuard) {
                omniboxRequester.requestFocus()
            }
        }
    }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(omnibox, TextRange(omnibox.length)))
    }
    // Set on focus so the first typed character replaces the whole URL even when the tap that
    // focused the field moved the cursor into it.
    var replaceOnNextEdit by remember { mutableStateOf(false) }
    // Chrome selects the whole URL on focus so the next keystroke replaces it. The flag is also
    // re-armed whenever the omnibox text changes while unfocused (e.g. after a navigation), because
    // Compose may keep the field focused even though the browser considers it dismissed.
    LaunchedEffect(focused) {
        if (focused) {
            replaceOnNextEdit = true
            fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
        }
    }
    LaunchedEffect(omnibox, focused) {
        if (!focused && fieldValue.text != omnibox) {
            replaceOnNextEdit = true
            fieldValue = TextFieldValue(omnibox, TextRange(omnibox.length))
        }
    }

    val hostColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val domainTransformation = remember(hostColor, mutedColor) {
        OmniboxDomainVisualTransformation(hostColor = hostColor, mutedColor = mutedColor)
    }
    val pillColor by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "omniboxPillColor",
    )

    Surface(
        color = if (isNewTab) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (toolbarBottom && !isNewTab) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPrivate && !focused) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onOpenTabs),
                        contentAlignment = Alignment.Center,
                    ) {
                        IncognitoBadgeIcon(
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                } else if (!isNewTab && !focused) {
                    IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.cd_home),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        // Only the omnibox pill owns the tab-swipe / pull-down gesture. Dragging on
                        // the surrounding buttons must not switch tabs, and while the field is
                        // focused the drag belongs to text selection.
                        // Key by focus so the gesture handler is rebuilt with the current value;
                        // otherwise the "focused" guard below would stay false forever.
                        .pointerInput(focused) {
                            var dragX = 0f
                            var dragY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDragEnd = {
                                    if (focused) return@detectDragGestures
                                    if (abs(dragX) > 60f && abs(dragX) > abs(dragY)) {
                                        Haptics.gestureEnd(hapticView)
                                        currentOnSwipeTab(if (dragX < 0) 1 else -1)
                                    } else if (dragY > 80f) {
                                        Haptics.gestureStart(hapticView)
                                        currentOnPullDown()
                                    }
                                },
                                onDragCancel = {
                                    dragX = 0f
                                    dragY = 0f
                                },
                            ) { change, amount ->
                                if (focused) return@detectDragGestures
                                change.consume()
                                dragX += amount.x
                                dragY += amount.y
                            }
                        },
                    shape = CircleShape,
                    color = pillColor,
                    border = if (focused) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp),
                    ) {
                        when {
                            focused || isNewTab -> Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).padding(end = 2.dp),
                            )
                            isSecure -> Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onPageInfo),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = stringResource(R.string.cd_page_info),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            else -> Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onPageInfo),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = stringResource(R.string.cd_page_info),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = fieldValue,
                            enabled = !focusGuard,
                            onValueChange = { new ->
                                val textChanged = new.text != fieldValue.text
                                val replacesAll = replaceOnNextEdit &&
                                    textChanged &&
                                    new.selection.collapsed &&
                                    fieldValue.selection.collapsed &&
                                    new.text.length > fieldValue.text.length
                                // Placing the cursor fires onValueChange with an unchanged text; keep the flag.
                                if (textChanged) replaceOnNextEdit = false
                                val typed = if (replacesAll) firstEditReplacement(fieldValue.text, new.text) else null
                                if (typed != null) {
                                    fieldValue = TextFieldValue(typed, TextRange(typed.length))
                                    onOmniboxChange(typed)
                                } else {
                                    fieldValue = new
                                    onOmniboxChange(new.text)
                                }
                            },
                            visualTransformation = if (!focused && omnibox.isNotBlank()) {
                                domainTransformation
                            } else {
                                VisualTransformation.None
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(omniboxRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused && focusGuard) {
                                        focusManager.clearFocus()
                                    } else {
                                        onFocusChange(state.isFocused)
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    // Chrome drops the keyboard as soon as the page starts loading.
                                    focusManager.clearFocus()
                                    onSubmit()
                                },
                            ),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (omnibox.isEmpty()) {
                                        Text(
                                            stringResource(R.string.omnibox_placeholder),
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        when {
                            isLoading -> IconButton(
                                onClick = onStop,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.stop),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            focused && fieldValue.text.isNotEmpty() -> IconButton(
                                onClick = {
                                    replaceOnNextEdit = false
                                    fieldValue = TextFieldValue("")
                                    onOmniboxChange("")
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_url),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            focused || isNewTab -> IconButton(
                                onClick = onVoice,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.cd_voice_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            else -> IconButton(
                                onClick = onShare,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.share_page),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (toolbarShortcut != "none") {
                    val shortcut: Pair<ImageVector, Int> = when (toolbarShortcut) {
                        "home" -> Icons.Outlined.Home to R.string.cd_home
                        "newTab" -> Icons.Rounded.Add to R.string.new_tab
                        "bookmarks" -> Icons.Outlined.Bookmarks to R.string.bookmarks_title
                        "history" -> Icons.Outlined.History to R.string.history_title
                        "closeTab" -> Icons.Rounded.Close to R.string.close
                        else -> Icons.Rounded.Add to R.string.new_tab
                    }
                    IconButton(
                        onClick = {
                            Haptics.confirm(hapticView)
                            when (toolbarShortcut) {
                                "home" -> onHome()
                                "newTab" -> onNewTab()
                                "bookmarks" -> onBookmarks()
                                "history" -> onHistory()
                                "closeTab" -> onCloseTab()
                            }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            shortcut.first,
                            contentDescription = stringResource(shortcut.second),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = amoAddonSlug != null && !focused,
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    IconButton(
                        onClick = onInstallAmoAddon,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.addons_install_from_amo),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                val tabsContentDescription = stringResource(R.string.tab_grid_tabs_label)
                IconButton(
                    onClick = onOpenTabs,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = tabsContentDescription
                        },
                ) {
                    ChromeTabCountIcon(
                        count = tabCount,
                        isPrivate = isPrivate,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                    Box {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.cd_menu),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        if (hasExtensionBadge) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = focused && suggestions.isNotEmpty(),
                enter = expandVertically(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Column {
                        suggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Haptics.tick(hapticView)
                                        onSuggestionPick(suggestion)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val icon: ImageVector = when (suggestion.kind) {
                                    SearchSuggestion.Kind.QUERY -> Icons.Rounded.Search
                                    SearchSuggestion.Kind.HISTORY -> Icons.Outlined.History
                                    SearchSuggestion.Kind.BOOKMARK -> Icons.Outlined.Bookmarks
                                }
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        suggestion.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (suggestion.kind != SearchSuggestion.Kind.QUERY) {
                                        Text(
                                            suggestion.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!toolbarBottom && !isNewTab) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}
