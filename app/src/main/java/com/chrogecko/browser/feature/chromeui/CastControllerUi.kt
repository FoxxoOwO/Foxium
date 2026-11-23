package com.chrogecko.browser.feature.chromeui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chrogecko.browser.feature.cast.CastPlaybackState
import androidx.compose.ui.res.stringResource
import com.chrogecko.browser.R

@Composable
fun CastMiniController(
    state: CastPlaybackState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onExpand,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.size(38.dp),
            ) {
                Icon(Icons.Rounded.Cast, contentDescription = stringResource(R.string.cd_open_cast_controls), modifier = Modifier.size(20.dp))
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 10.dp),
            ) {
                Text(
                    state.mediaTitle ?: stringResource(R.string.casting),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.deviceName ?: stringResource(R.string.connected),
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            FilledTonalIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.playing) stringResource(R.string.pause) else stringResource(R.string.play),
                )
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_stop_casting))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CastExpandedController(
    state: CastPlaybackState,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onStop: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val playCorner by animateDpAsState(
        targetValue = if (state.playing) 24.dp else 36.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "castPlayCorner",
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    ) {
                        Text(
                            state.deviceName ?: stringResource(R.string.cast_device),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.cd_disconnect_device))
                    }
                }
                Spacer(Modifier.height(48.dp))
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(120.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Cast,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    state.mediaTitle ?: stringResource(R.string.casting),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.deviceName ?: stringResource(R.string.cast_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                val duration = state.durationMs.coerceAtLeast(1L)
                var scrub by remember { mutableFloatStateOf(state.positionMs.toFloat()) }
                var dragging by remember { mutableStateOf(false) }
                var seeking by remember { mutableStateOf(false) }
                LaunchedEffect(state.positionMs, duration, dragging, seeking) {
                    // Do not snap back to the old position while the seek is in flight; the remote
                    // player has not reported the new one yet.
                    if (seeking) {
                        if (kotlin.math.abs(state.positionMs - scrub) < 2_000) seeking = false
                        return@LaunchedEffect
                    }
                    if (!dragging) scrub = state.positionMs.toFloat()
                }
                LaunchedEffect(seeking) {
                    if (!seeking) return@LaunchedEffect
                    kotlinx.coroutines.delay(3_000L)
                    seeking = false
                }
                Slider(
                    value = scrub.coerceIn(0f, duration.toFloat()),
                    enabled = state.durationMs > 0L,
                    onValueChange = {
                        dragging = true
                        scrub = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        seeking = true
                        onSeek(scrub.toLong())
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatDuration(scrub.toLong()), style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = onPlayPause,
                        shape = RoundedCornerShape(playCorner),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.playing) stringResource(R.string.pause) else stringResource(R.string.play),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.cd_stop_playback))
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
