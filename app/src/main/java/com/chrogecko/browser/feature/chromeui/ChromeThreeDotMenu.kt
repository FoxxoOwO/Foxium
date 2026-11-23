package com.chrogecko.browser.feature.chromeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.chrogecko.browser.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrogecko.browser.feature.userscripts.UserScriptMenuCommand

enum class MenuAction {
    Back,
    Forward,
    Reload,
    ToggleBookmark,
    PageInfo,
    NewTab,
    NewIncognitoTab,
    History,
    RecentTabs,
    Bookmarks,
    Downloads,
    Extensions,
    Userscripts,
    Cast,
    Settings,
    FindInPage,
    Translate,
    Share,
    Print,
    AddToHomeScreen,
    ToggleSiteDarkMode,
    ToggleDesktopSite,
    ReaderMode,
}

@Composable
fun ChromeThreeDotMenu(
    activeTab: BrowserTab?,
    bookmarked: Boolean,
    siteDarkMode: Boolean,
    desktopMode: Boolean,
    addonCount: Int,
    canTranslate: Boolean,
    readerAvailable: Boolean,
    readerActive: Boolean,
    extensionBadges: String? = null,
    menuCommands: List<UserScriptMenuCommand>,
    onMenuCommand: (UserScriptMenuCommand) -> Unit,
    onAction: (MenuAction) -> Unit,
    onToggleSiteDarkMode: () -> Unit,
    onToggleDesktopMode: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QuickAction(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    label = stringResource(R.string.menu_forward),
                    enabled = activeTab?.state?.canGoForward == true,
                    active = false,
                ) { onAction(MenuAction.Forward) }
                QuickAction(
                    icon = if (bookmarked) Icons.Rounded.Star else Icons.Outlined.StarBorder,
                    label = stringResource(if (bookmarked) R.string.menu_saved else R.string.menu_bookmark),
                    enabled = activeTab != null,
                    active = bookmarked,
                ) { onAction(MenuAction.ToggleBookmark) }
                QuickAction(
                    icon = Icons.Outlined.Download,
                    label = stringResource(R.string.downloads_title),
                    enabled = activeTab != null,
                    active = false,
                ) { onAction(MenuAction.Downloads) }
                QuickAction(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.menu_page_info),
                    enabled = activeTab != null,
                    active = false,
                ) { onAction(MenuAction.PageInfo) }
                QuickAction(
                    icon = Icons.Rounded.Refresh,
                    label = stringResource(R.string.menu_reload),
                    enabled = activeTab != null,
                    active = false,
                ) { onAction(MenuAction.Reload) }
            }
        }

        MenuSectionCard {
            MenuRow(
                title = stringResource(R.string.new_tab),
                icon = Icons.Outlined.AddBox,
                onClick = { onAction(MenuAction.NewTab) },
            )
            MenuCustomIconRow(
                title = stringResource(R.string.new_incognito_tab),
                onClick = { onAction(MenuAction.NewIncognitoTab) },
                leadingIcon = {
                    IncognitoBadgeIcon(
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        MenuSectionCard {
            MenuRow(stringResource(R.string.history_title), Icons.Rounded.History) { onAction(MenuAction.History) }
            MenuRow(stringResource(R.string.recent_tabs_title), Icons.Outlined.Devices) { onAction(MenuAction.RecentTabs) }
            MenuRow(stringResource(R.string.bookmarks_title), Icons.Outlined.Bookmarks) { onAction(MenuAction.Bookmarks) }
            MenuRow(stringResource(R.string.downloads_title), Icons.Outlined.Download) { onAction(MenuAction.Downloads) }
            MenuRow(
                title = stringResource(R.string.extensions_title),
                icon = Icons.Outlined.Extension,
                badge = extensionBadges ?: if (addonCount > 0) "$addonCount" else null,
            ) { onAction(MenuAction.Extensions) }
            MenuRow(stringResource(R.string.userscripts_title), Icons.Rounded.Code) { onAction(MenuAction.Userscripts) }
        }

        if (menuCommands.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.menu_userscript_actions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 2.dp),
                    )
                    menuCommands.forEach { command ->
                        MenuRow(
                            title = command.label,
                            icon = Icons.Rounded.Terminal,
                        ) { onMenuCommand(command) }
                    }
                }
            }
        }

        MenuSectionCard {
            MenuRow(stringResource(R.string.menu_cast), Icons.Rounded.Cast) { onAction(MenuAction.Cast) }
            if (canTranslate) {
                MenuRow(stringResource(R.string.menu_translate), Icons.Rounded.Translate) { onAction(MenuAction.Translate) }
            }
            if (readerAvailable) {
                MenuRow(
                    title = stringResource(if (readerActive) R.string.menu_hide_reader else R.string.menu_show_reader),
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                ) { onAction(MenuAction.ReaderMode) }
            }
            MenuRow(stringResource(R.string.menu_find_in_page), Icons.Rounded.FindInPage) { onAction(MenuAction.FindInPage) }
            MenuRow(stringResource(R.string.menu_print), Icons.Outlined.Print) { onAction(MenuAction.Print) }
            MenuRow(stringResource(R.string.menu_add_to_home_screen), Icons.AutoMirrored.Outlined.AddToHomeScreen) {
                onAction(MenuAction.AddToHomeScreen)
            }
            MenuRow(stringResource(R.string.menu_share), Icons.Outlined.Share) { onAction(MenuAction.Share) }
        }

        MenuSectionCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = activeTab != null) { onToggleSiteDarkMode() }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.menu_dark_mode_site),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = siteDarkMode,
                    onCheckedChange = { onToggleSiteDarkMode() },
                    enabled = activeTab != null,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = activeTab != null) { onToggleDesktopMode(!desktopMode) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.DesktopWindows,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.menu_desktop_site),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Checkbox(
                    checked = desktopMode,
                    onCheckedChange = { onToggleDesktopMode(it) },
                    enabled = activeTab != null,
                )
            }
        }

        MenuSectionCard {
            MenuRow(stringResource(R.string.settings_title), Icons.Outlined.Settings) { onAction(MenuAction.Settings) }
        }
    }
}

@Composable
private fun MenuSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MenuRow(
    title: String,
    icon: ImageVector,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!badge.isNullOrBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuCustomIconRow(
    title: String,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            leadingIcon()
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
