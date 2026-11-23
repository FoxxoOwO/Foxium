package com.chrogecko.browser.feature.chromeui

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import com.chrogecko.browser.R
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrogecko.browser.core.ui.Haptics
import com.chrogecko.browser.feature.favicons.faviconHost
import com.chrogecko.browser.ui.theme.ChroGeckoTheme

private class DragTracker(
    var position: Offset = Offset.Zero,
    var distance: Float = 0f,
)

/** Which gesture currently owns a tab card, so swiping and reordering never fight over the pointer. */
private enum class CardGesture { NONE, SWIPE, REORDER }

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun ChromeTabGridSwitcher(
    tabs: List<BrowserTab>,
    groups: List<TabGroup> = emptyList(),
    icons: Map<String, ImageBitmap>,
    activeTabId: String?,
    onSelect: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAllPrivate: () -> Unit,
    onMoveTab: (String, String) -> Unit,
    onDuplicateTab: (String) -> Unit,
    onCloseOtherTabs: (String) -> Unit,
    onGroupWith: (String, String) -> Unit,
    onGroupAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val activeIsPrivate = tabs.firstOrNull { it.id == activeTabId }?.isPrivate
    var showPrivate by remember(activeIsPrivate) { mutableStateOf(activeIsPrivate ?: false) }
    val visible = remember(tabs, showPrivate) { tabs.filter { it.isPrivate == showPrivate } }
    val normalCount = remember(tabs) { tabs.count { !it.isPrivate } }
    val privateCount = remember(tabs) { tabs.count { it.isPrivate } }
    val groupById = remember(groups) { groups.associateBy { it.id } }

    // Drag-to-reorder: window-space bounds of every card drive the drop target.
    val cardBounds = remember { mutableMapOf<String, Rect>() }
    val drag = remember { DragTracker() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var hoverId by remember { mutableStateOf<String?>(null) }
    // Long press without moving opens the card menu, like Chrome; dragging reorders.
    var menuTab by remember { mutableStateOf<BrowserTab?>(null) }
    var groupPick by remember { mutableStateOf(false) }
    val hapticView = LocalView.current
    val cardScope = rememberCoroutineScope()

    ChroGeckoTheme(darkTheme = isSystemInDarkTheme() || showPrivate, isPrivate = showPrivate) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(
                            onClick = { onNewTab(showPrivate) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_tab))
                        }
                        Spacer(Modifier.weight(1f))
                        ProfileSwitcher(
                            showPrivate = showPrivate,
                            normalCount = normalCount,
                            privateCount = privateCount,
                            onSelectPrivate = { showPrivate = it },
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onGroupAll, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Outlined.GridView,
                                contentDescription = stringResource(R.string.tab_grid_group_tabs),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                    }

                    if (showPrivate && visible.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            FilledTonalButton(
                                onClick = onCloseAllPrivate,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text(stringResource(R.string.close_all_incognito), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (visible.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (showPrivate) {
                                    IncognitoBadgeIcon(
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                                Text(
                                    stringResource(if (showPrivate) R.string.no_incognito_tabs else R.string.no_tabs_open),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 92.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(visible, key = { it.id }, contentType = { "tab_card" }) { tab ->
                                    val group = tab.groupId?.let(groupById::get)
                                    TabCard(
                                        modifier = Modifier.animateItem().testTag("tab_card"),
                                        tab = tab,
                                        group = group,
                                        icon = faviconHost(tab.state.url)?.let(icons::get),
                                        hapticView = hapticView,
                                        scope = cardScope,
                                        isActive = tab.id == activeTabId,
                                        isDragging = tab.id == dragId,
                                        isDropTarget = tab.id == hoverId,
                                        // A long press opens the card menu; the release that follows must
                                        // not also count as a tap and close the grid underneath it.
                                        onClick = {
                                            if (dragId == null && menuTab == null) onSelect(tab.id)
                                        },
                                        onClose = { onCloseTab(tab.id) },
                                        onBoundsChanged = { cardBounds[tab.id] = it },
                                        onDisposeCard = { cardBounds.remove(tab.id) },
                                        onDragStart = {
                                            dragId = tab.id
                                            drag.position = cardBounds[tab.id]?.center ?: Offset.Zero
                                            hoverId = null
                                            drag.distance = 0f
                                        },
                                        onDrag = { amount ->
                                            drag.distance += amount.getDistance()
                                            drag.position += amount
                                            hoverId = visible.firstOrNull { candidate ->
                                                candidate.id != dragId &&
                                                    cardBounds[candidate.id]?.contains(drag.position) == true
                                            }?.id
                                        },
                                        onDragEnd = {
                                            val dragged = dragId
                                            val target = hoverId
                                            val draggedTab = visible.firstOrNull { it.id == dragged }
                                            when {
                                                dragged != null && target != null -> onMoveTab(dragged, target)
                                                draggedTab != null && drag.distance < 24f -> {
                                                    groupPick = false
                                                    menuTab = draggedTab
                                                }
                                            }
                                            dragId = null
                                            hoverId = null
                                        },
                                        onDragCancel = {
                                            dragId = null
                                            hoverId = null
                                        },
                                    )
                                }
                            }
                        }

                        ExtendedFloatingActionButton(
                            onClick = { onNewTab(showPrivate) },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            text = { Text(stringResource(if (showPrivate) R.string.new_incognito else R.string.new_tab)) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        )
                    }
                }

                menuTab?.let { tab ->
                    TabCardMenu(
                        tab = tab,
                        candidates = visible.filter { it.id != tab.id },
                        groupPick = groupPick,
                        onGroupPickChange = { groupPick = it },
                        onDuplicate = {
                            menuTab = null
                            onDuplicateTab(tab.id)
                        },
                        onClose = {
                            menuTab = null
                            onCloseTab(tab.id)
                        },
                        onCloseOthers = {
                            menuTab = null
                            onCloseOtherTabs(tab.id)
                        },
                        onGroupWith = { other ->
                            menuTab = null
                            groupPick = false
                            onGroupWith(tab.id, other)
                        },
                        onDismiss = {
                            menuTab = null
                            groupPick = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileSwitcher(
    showPrivate: Boolean,
    normalCount: Int,
    privateCount: Int,
    onSelectPrivate: (Boolean) -> Unit,
) {
    val toggleColors = ToggleButtonDefaults.tonalToggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleButton(
            checked = !showPrivate,
            onCheckedChange = { if (it) onSelectPrivate(false) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = toggleColors,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            ChromeTabCountIcon(
                count = normalCount,
                isPrivate = false,
                tint = LocalContentColor.current,
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.tab_grid_tabs_label), maxLines = 1, style = MaterialTheme.typography.labelLarge)
        }
        ToggleButton(
            checked = showPrivate,
            onCheckedChange = { if (it) onSelectPrivate(true) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = toggleColors,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            IncognitoBadgeIcon(
                modifier = Modifier.size(18.dp),
                tint = LocalContentColor.current,
            )
            if (privateCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text(privateCount.toString(), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: BrowserTab,
    group: TabGroup?,
    isActive: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    icon: ImageBitmap?,
    hapticView: View,
    scope: CoroutineScope,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onDisposeCard: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeX = remember(tab.id) { Animatable(0f) }
    var dismissed by remember(tab.id) { mutableStateOf(false) }
    var gesture by remember(tab.id) { mutableStateOf(CardGesture.NONE) }
    var reorderAborted by remember(tab.id) { mutableStateOf(false) }
    val cardWidthRef = remember(tab.id) { floatArrayOf(1f) }
    val currentOnClose by rememberUpdatedState(onClose)
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnDisposeCard by rememberUpdatedState(onDisposeCard)
    DisposableEffect(tab.id) {
        onDispose { currentOnDisposeCard() }
    }
    val border = when {
        isDropTarget -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.secondary)
        isActive -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        group != null -> BorderStroke(1.5.dp, Color(group.color))
        else -> null
    }
    // Gestures live on this non-translating box; only the inner card moves while it is swiped.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .onSizeChanged { size ->
                cardWidthRef[0] = size.width.toFloat().coerceAtLeast(1f)
            }
            .onLayoutRectChanged(throttleMillis = 64L) { bounds ->
                val windowRect = bounds.boundsInWindow
                onBoundsChanged(
                    Rect(
                        left = windowRect.left.toFloat(),
                        top = windowRect.top.toFloat(),
                        right = windowRect.right.toFloat(),
                        bottom = windowRect.bottom.toFloat(),
                    ),
                )
            }
            .pointerInput(tab.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        // A quick swipe can own the card before the long-press timer fires. In that
                        // case reordering (and the menu that follows a stationary long press) must
                        // not also run.
                        reorderAborted = gesture == CardGesture.SWIPE || dismissed
                        if (!reorderAborted) {
                            Haptics.dragStart(hapticView)
                            gesture = CardGesture.REORDER
                            currentOnDragStart()
                        }
                    },
                    onDragCancel = {
                        if (!reorderAborted) currentOnDragCancel()
                        reorderAborted = false
                        gesture = CardGesture.NONE
                    },
                    onDragEnd = {
                        if (!reorderAborted) currentOnDragEnd()
                        reorderAborted = false
                        gesture = CardGesture.NONE
                    },
                    onDrag = { change, amount ->
                        if (reorderAborted) return@detectDragGesturesAfterLongPress
                        // Only consume while actually reordering, so a swipe that started first
                        // keeps receiving the remaining pointer events.
                        if (gesture == CardGesture.REORDER) change.consume()
                        currentOnDrag(amount)
                    },
                )
            }
            .pointerInput(tab.id) {
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        if (gesture != CardGesture.REORDER) gesture = CardGesture.SWIPE
                    },
                    onDragEnd = {
                        val cardWidthPx = cardWidthRef[0]
                        val velocityX = velocityTracker.calculateVelocity().x
                        val offset = swipeX.value
                        val flung = abs(velocityX) > 600f && offset * velocityX > 0f
                        if (!dismissed && (abs(offset) > cardWidthPx * 0.28f || flung)) {
                            dismissed = true
                            Haptics.gestureEnd(hapticView)
                            val direction = if (offset == 0f) {
                                if (velocityX < 0f) -1f else 1f
                            } else if (offset < 0f) -1f else 1f
                            scope.launch {
                                swipeX.animateTo(direction * cardWidthPx * 1.35f, tween(durationMillis = 180))
                                currentOnClose()
                            }
                        } else if (!dismissed) {
                            scope.launch { swipeX.animateTo(0f, settleSpec) }
                        }
                        gesture = CardGesture.NONE
                    },
                    onDragCancel = {
                        if (!dismissed) scope.launch { swipeX.animateTo(0f, settleSpec) }
                        gesture = CardGesture.NONE
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (gesture == CardGesture.REORDER || dismissed) {
                            return@detectHorizontalDragGestures
                        }
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { swipeX.snapTo(swipeX.value + dragAmount) }
                    },
                )
            },
    ) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when {
            isDropTarget -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = border,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val cardWidthPx = cardWidthRef[0]
                translationX = swipeX.value
                rotationZ = (swipeX.value / cardWidthPx).coerceIn(-1f, 1f) * 8f
                alpha = if (isDragging) {
                    0.45f
                } else {
                    (1f - abs(swipeX.value) / (cardWidthPx * 1.15f)).coerceIn(0.15f, 1f)
                }
            }
            .combinedClickable(
                onClick = { if (abs(swipeX.value) < 4f && !dismissed) onClick() },
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Chrome places the header bar with favicon, title and close button at the TOP of the card.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (tab.state.title.ifBlank { tab.state.url.ifBlank { "N" } }).take(1).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (group != null) {
                    Spacer(Modifier.width(5.dp))
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(group.color)),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    tab.state.title.ifBlank { tab.state.url.ifBlank { stringResource(R.string.new_tab) } },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_tab),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                val thumb = tab.thumbnail
                val thumbBitmap = remember(thumb) { thumb?.asImageBitmap() }
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (tab.isPrivate) {
                            IncognitoBadgeIcon(
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                (tab.state.title.ifBlank { stringResource(R.string.new_tab) }).take(1).uppercase(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/** Chrome's long-press card menu, including the stringResource(R.string.tab_grid_group_with_ellipsis) picker it opens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabCardMenu(
    tab: BrowserTab,
    candidates: List<BrowserTab>,
    groupPick: Boolean,
    onGroupPickChange: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
    onGroupWith: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                if (groupPick) stringResource(R.string.tab_grid_group_with) else tab.state.title.ifBlank { tab.state.url.ifBlank { stringResource(R.string.new_tab) } },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            if (groupPick) {
                if (candidates.isEmpty()) {
                    MenuRow(stringResource(R.string.tab_grid_no_other_tabs)) {}
                } else {
                    candidates.forEach { candidate ->
                        MenuRow(candidate.state.title.ifBlank { candidate.state.url }) {
                            onGroupWith(candidate.id)
                        }
                    }
                }
            } else {
                if (candidates.isNotEmpty()) MenuRow(stringResource(R.string.tab_grid_group_with_ellipsis)) { onGroupPickChange(true) }
                MenuRow(stringResource(R.string.tab_grid_duplicate)) { onDuplicate() }
                MenuRow(stringResource(R.string.close)) { onClose() }
                if (candidates.isNotEmpty()) MenuRow(stringResource(R.string.tab_grid_close_other_tabs)) { onCloseOthers() }
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
