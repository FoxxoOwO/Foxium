package com.chrogecko.browser.feature.chromeui

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrogecko.browser.R
import com.chrogecko.browser.core.engine.SelectionMenu
import com.chrogecko.browser.core.ui.Haptics
import com.chrogecko.browser.feature.favicons.faviconHost

@Composable
internal fun FindBar(
    state: FindState,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (state.query.isEmpty()) {
                                    Text(
                                        stringResource(R.string.find_in_page_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            if (state.found) "${state.current}/${state.total}" else "0/0",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_previous_match))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_next_match))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_find_bar))
            }
        }
    }
}

@Composable
internal fun TabGroupStrip(
    group: TabGroup,
    icons: Map<String, ImageBitmap>,
    tabs: List<BrowserTab>,
    activeTabId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val groupColor = Color(group.color)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable(onClick = onEdit),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(groupColor),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            group.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val newTabLabel = stringResource(R.string.new_tab)
                tabs.forEach { tab ->
                    val isSelected = tab.id == activeTabId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = if (isSelected) BorderStroke(2.dp, groupColor) else null,
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onSelect(tab.id) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val icon = faviconHost(tab.state.url)?.let(icons::get)
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).clip(CircleShape),
                                )
                            } else {
                                Text(
                                    displayTabTitle(tab, newTabLabel).take(1).uppercase(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                FilledTonalIconButton(
                    onClick = onAdd,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_new_tab_in_group),
                        modifier = Modifier.size(18.dp),
                    )
                }
                activeTabId?.let { id ->
                    IconButton(onClick = { onRemove(id) }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_remove_from_group),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PageInfoSheet(tab: BrowserTab?) {
    val secure = tab?.state?.isSecure == true
    val title = tab?.state?.title.orEmpty().ifBlank { stringResource(R.string.current_page) }
    val url = tab?.state?.url.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (url.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (secure) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (secure) Icons.Rounded.VerifiedUser else Icons.Rounded.GppMaybe,
                            contentDescription = null,
                            tint = if (secure) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(if (secure) R.string.connection_secure else R.string.connection_not_secure),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (secure) {
                            stringResource(R.string.connection_secure_detail)
                        } else {
                            stringResource(R.string.connection_not_secure_detail)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Native feedback for the page's pull-to-refresh gesture: a progress ring that fills as the user
 * pulls, an arrow that flips at the threshold and a haptic tick when the reload is armed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PullRefreshIndicator(
    state: BrowserViewModel.PullRefreshState?,
    view: View,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = enabled && state != null,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
        modifier = modifier,
    ) {
        val value = state ?: return@AnimatedVisibility
        val armed = value.progress >= 1f || value.triggered
        var ticked by remember { mutableStateOf(false) }
        LaunchedEffect(armed) {
            if (armed && !ticked) {
                Haptics.gestureStart(view)
                ticked = true
            } else if (!armed) {
                ticked = false
            }
        }
        val rotation by animateFloatAsState(
            targetValue = if (armed) 180f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "pullArrow",
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (value.triggered) 1f else value.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.size(38.dp),
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (armed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    if (armed) R.string.pull_refresh_release else R.string.pull_refresh_hint,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Uses Android's own floating selection ActionMode - the same native popup other browsers show.
 * GeckoView only asks the embedder to show a menu via SelectionActionDelegate, so starting an
 * ActionMode ourselves gives the platform styling (including the overflow) for free.
 */
@Composable
internal fun NativeSelectionMenu(
    selection: SelectionMenu?,
    onCopy: () -> Unit,
    onSearchWeb: (String) -> Unit,
    onShare: () -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val currentOnCopy by rememberUpdatedState(onCopy)
    val currentOnSearchWeb by rememberUpdatedState(onSearchWeb)
    val currentText by rememberUpdatedState(selection?.text.orEmpty())
    val currentCanCopy by rememberUpdatedState(selection?.canCopy == true)
    val currentCanSelectAll by rememberUpdatedState(selection?.canSelectAll == true)
    val currentOnShare by rememberUpdatedState(onShare)
    val currentOnSelectAll by rememberUpdatedState(onSelectAll)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var actionMode by remember { mutableStateOf<ActionMode?>(null) }
    var callback by remember { mutableStateOf<ActionMode.Callback2?>(null) }
    var selectionRect by remember { mutableStateOf<android.graphics.RectF?>(null) }
    var finishingProgrammatically by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            actionMode?.let { existing ->
                finishingProgrammatically = true
                existing.finish()
            }
            actionMode = null
            callback = null
        }
    }

    LaunchedEffect(selection) {
        val current = selection
        if (current == null) {
            actionMode?.let { existing ->
                finishingProgrammatically = true
                existing.finish()
            }
            actionMode = null
            callback = null
            return@LaunchedEffect
        }
        selectionRect = current.screenRect
        if (callback != null && actionMode != null) {
            // Gecko can emit another selection while the ActionMode is open with different
            // available actions; rebuild the menu instead of keeping the first snapshot.
            val existing = actionMode ?: return@LaunchedEffect
            existing.menu.clear()
            callback?.onCreateActionMode(existing, existing.menu)
            existing.invalidateContentRect()
            return@LaunchedEffect
        }
        val created = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (currentCanCopy) {
                    menu.add(R.string.copy).setOnMenuItemClickListener {
                        currentOnCopy()
                        true
                    }
                }
                menu.add(R.string.context_search_web).setOnMenuItemClickListener {
                    currentOnSearchWeb(currentText)
                    true
                }
                if (currentCanSelectAll) {
                    menu.add(R.string.select_all).setOnMenuItemClickListener {
                        currentOnSelectAll()
                        true
                    }
                }
                menu.add(R.string.share).setOnMenuItemClickListener {
                    currentOnShare()
                    true
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = true
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode) {
                val wasProgrammatic = finishingProgrammatically
                finishingProgrammatically = false
                actionMode = null
                callback = null
                if (!wasProgrammatic) currentOnDismiss()
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                selectionRect?.let {
                    outRect.set(
                        it.left.toInt(),
                        it.top.toInt(),
                        it.right.toInt(),
                        it.bottom.toInt(),
                    )
                }
            }
        }
        finishingProgrammatically = false
        val started = view.startActionMode(created, ActionMode.TYPE_FLOATING)
        if (started != null) {
            callback = created
            actionMode = started
        } else {
            callback = null
            actionMode = null
        }
    }
}
