package com.hkm.htmllabs.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.htmllabs.R
import com.hkm.htmllabs.data.AppSettings
import com.hkm.htmllabs.data.CloudBackupManager
import com.hkm.htmllabs.data.HtmlCard
import com.hkm.htmllabs.data.HtmlRepository
import com.hkm.htmllabs.ui.theme.AppMotion
import com.hkm.htmllabs.ui.theme.AppleSegmentedControl
import com.hkm.htmllabs.ui.theme.appleEntrance
import com.hkm.htmllabs.ui.theme.applePressable
import com.hkm.htmllabs.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: HtmlRepository,
    cards: List<HtmlCard>,
    cardPreviewEnabled: Boolean,
    onExportLocalBackup: () -> Unit,
    onImportLocalBackup: () -> Unit,
    onExportSingleHtml: (HtmlCard) -> Unit,
    onCardPreviewModeChanged: (Boolean) -> Unit,
    onThemeModeChanged: (AppSettings.ThemeMode) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    var openInApp by remember { mutableStateOf(AppSettings.isOpenInApp(context)) }
    var previewMode by remember { mutableStateOf(cardPreviewEnabled) }
    var themeMode by remember { mutableStateOf(AppSettings.getThemeMode(context)) }
    var localToken by remember { mutableStateOf(AppSettings.getCloudBackupToken(context)) }
    var lastCloudBackupAt by remember { mutableStateOf(AppSettings.getLastCloudBackupAt(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var revealedToken by remember { mutableStateOf<String?>(null) }
    var showUseExistingTokenDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var showDeleteCloudBackupDialog by remember { mutableStateOf(false) }
    var showExportHtmlDialog by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val reduceMotion = rememberReduceMotion()
    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (revealedToken != null) {
        OneTimeTokenDialog(
            token = revealedToken!!,
            onCopy = {
                copyToClipboard(context, it)
                Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { revealedToken = null }
        )
    }

    if (showUseExistingTokenDialog) {
        TokenInputDialog(
            title = "Use existing token",
            confirmLabel = "Save token",
            saveTokenToggleVisible = false,
            onDismiss = { showUseExistingTokenDialog = false },
            onConfirm = { token, _ ->
                val normalized = token.trim()
                if (!CloudBackupManager.isValidToken(normalized)) {
                    statusMessage = "Token must match hkm- plus 36 letters/numbers"
                    return@TokenInputDialog
                }
                AppSettings.setCloudBackupToken(context, normalized)
                localToken = normalized
                showUseExistingTokenDialog = false
                statusMessage = "Token saved on this device"
            }
        )
    }

    if (showRestoreDialog) {
        TokenInputDialog(
            title = "Restore from cloud",
            confirmLabel = "Restore now",
            saveTokenToggleVisible = true,
            onDismiss = { showRestoreDialog = false },
            onConfirm = { token, saveToken ->
                val normalized = token.trim()
                if (!CloudBackupManager.isValidToken(normalized)) {
                    statusMessage = "Token must match hkm- plus 36 letters/numbers"
                    return@TokenInputDialog
                }
                showRestoreDialog = false
                isBusy = true
                scope.launch {
                    val (status, backupData) = CloudBackupManager.restoreBackup(normalized)
                    if (status.success && backupData != null) {
                        repository.restoreFromBackup(backupData.items)
                        if (saveToken) {
                            AppSettings.setCloudBackupToken(context, normalized)
                            localToken = normalized
                        }
                        status.updatedAt?.takeIf { it.isNotBlank() }?.let {
                            AppSettings.setLastCloudBackupAt(context, it)
                            lastCloudBackupAt = it
                        }
                        statusMessage = "Restored ${backupData.items.size} cards from backup"
                    } else {
                        statusMessage = status.message
                    }
                    isBusy = false
                }
            }
        )
    }

    if (showReplaceDialog) {
        ReplaceTokenDialog(
            hasExistingToken = !localToken.isNullOrBlank(),
            onDismiss = { showReplaceDialog = false },
            onConfirm = { deleteOldBackup ->
                val oldToken = localToken
                showReplaceDialog = false
                isBusy = true
                scope.launch {
                    var deleteMessage: String? = null
                    if (deleteOldBackup && !oldToken.isNullOrBlank()) {
                        val deleteStatus = CloudBackupManager.deleteBackup(oldToken)
                        if (!deleteStatus.success) deleteMessage = deleteStatus.message
                    }
                    val newToken = CloudBackupManager.generateToken()
                    AppSettings.setCloudBackupToken(context, newToken)
                    localToken = newToken
                    revealedToken = newToken
                    statusMessage = deleteMessage ?: "New token generated for this device"
                    isBusy = false
                }
            }
        )
    }

    if (showDeleteCloudBackupDialog) {
        ConfirmDialog(
            title = "Delete cloud backup?",
            body = "This removes the current token's remote backup. Your cards on this device and local backup files stay untouched.",
            confirmLabel = "Delete",
            onDismiss = { showDeleteCloudBackupDialog = false },
            onConfirm = {
                val token = localToken
                showDeleteCloudBackupDialog = false
                if (token.isNullOrBlank()) {
                    statusMessage = "No local token saved on this device"
                    return@ConfirmDialog
                }
                isBusy = true
                scope.launch {
                    val status = CloudBackupManager.deleteBackup(token)
                    statusMessage = status.message
                    isBusy = false
                }
            }
        )
    }

    if (showExportHtmlDialog) {
        SingleHtmlExportDialog(
            cards = cards,
            onDismiss = { showExportHtmlDialog = false },
            onSelect = { card ->
                showExportHtmlDialog = false
                onExportSingleHtml(card)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = statusBarTop + 10.dp,
                bottom = navBarBottom + 36.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Scrolling Header
            item(key = "settings_top_bar") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surface,
                        border = BorderStroke(0.8.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .size(42.dp)
                            .applePressable(pressedScale = 0.92f) { onClose() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close Settings",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = "Settings",
                        color = colorScheme.onBackground,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (statusMessage != null) {
                item(key = "settings_status_banner") {
                    AnimatedVisibility(
                        visible = statusMessage != null,
                        enter = AppMotion.softExpandEnter,
                        exit = AppMotion.softExpandExit
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, colorScheme.primary.copy(alpha = 0.18f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // 1. APPEARANCE (Tonser 24dp Grouped Card + Sliding Segmented Pill)
            item(key = "settings_appearance_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appleEntrance(index = 0, reduceMotion = reduceMotion)
                ) {
                    SettingsSectionHeader("Appearance")
                    Spacer(Modifier.height(8.dp))
                    SettingsCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SettingsIconBadge(iconRes = R.drawable.ic_theme)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Theme Mode",
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = when (themeMode) {
                                            AppSettings.ThemeMode.SYSTEM -> "Follow system settings"
                                            AppSettings.ThemeMode.LIGHT -> "Always light"
                                            AppSettings.ThemeMode.DARK -> "Always dark"
                                        },
                                        color = colorScheme.onSurface.copy(alpha = 0.62f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            AppleSegmentedControl(
                                options = AppSettings.ThemeMode.entries,
                                selected = themeMode,
                                label = { mode ->
                                    when (mode) {
                                        AppSettings.ThemeMode.SYSTEM -> "System"
                                        AppSettings.ThemeMode.LIGHT -> "Light"
                                        AppSettings.ThemeMode.DARK -> "Dark"
                                    }
                                },
                                onSelect = { mode ->
                                    themeMode = mode
                                    AppSettings.setThemeMode(context, mode)
                                    onThemeModeChanged(mode)
                                }
                            )
                        }

                        SettingsDivider()

                        SettingsRow(
                            iconRes = R.drawable.ic_eye,
                            title = "HTML preview on cards",
                            subtitle = "Show a static preview instead of the gradient cover",
                            trailing = {
                                Switch(
                                    checked = previewMode,
                                    onCheckedChange = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        previewMode = it
                                        onCardPreviewModeChanged(it)
                                    },
                                    colors = tonserSwitchColors()
                                )
                            },
                            onClick = {
                                val next = !previewMode
                                previewMode = next
                                onCardPreviewModeChanged(next)
                            }
                        )
                    }
                }
            }

            // 2. VIEWER
            item(key = "settings_viewer_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appleEntrance(index = 1, reduceMotion = reduceMotion)
                ) {
                    SettingsSectionHeader("Viewer")
                    Spacer(Modifier.height(8.dp))
                    SettingsCard {
                        SettingsRow(
                            iconRes = R.drawable.ic_browser,
                            title = "Open HTML in app",
                            subtitle = "Built-in browser with fullscreen, download support, and controls",
                            trailing = {
                                Switch(
                                    checked = openInApp,
                                    onCheckedChange = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        openInApp = it
                                        AppSettings.setOpenInApp(context, it)
                                    },
                                    colors = tonserSwitchColors()
                                )
                            },
                            onClick = {
                                val next = !openInApp
                                openInApp = next
                                AppSettings.setOpenInApp(context, next)
                            }
                        )
                    }
                }
            }

            // 3. CLOUD BACKUP
            item(key = "settings_cloud_backup_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appleEntrance(index = 2, reduceMotion = reduceMotion)
                ) {
                    SettingsSectionHeader("Cloud Backup")
                    Spacer(Modifier.height(8.dp))
                    SettingsCard {
                        // Token status banner
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.secondary.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = if (localToken.isNullOrBlank()) {
                                        "No token saved"
                                    } else {
                                        CloudBackupManager.maskToken(localToken!!)
                                    },
                                    color = colorScheme.onSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (lastCloudBackupAt.isNullOrBlank()) {
                                        "Create or add a token, then back up ${cards.size} cards"
                                    } else {
                                        "Last backup: $lastCloudBackupAt"
                                    },
                                    color = colorScheme.onSecondary.copy(alpha = 0.68f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        SettingsDivider()

                        SettingsRow(
                            iconRes = R.drawable.ic_key_plus,
                            title = if (localToken.isNullOrBlank()) "Create backup token" else "Create new token",
                            subtitle = "Generate a fresh token and reveal it one time",
                            onClick = { showReplaceDialog = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            iconRes = R.drawable.ic_key,
                            title = "Use existing token",
                            subtitle = "Save a token you already have on this device",
                            onClick = { showUseExistingTokenDialog = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            iconRes = R.drawable.ic_cloud_upload,
                            title = "Backup now",
                            subtitle = "Upload ${cards.size} cards with the saved token",
                            enabled = !localToken.isNullOrBlank() && !isBusy,
                            onClick = {
                                val token = localToken ?: return@SettingsRow
                                isBusy = true
                                scope.launch {
                                    val status = CloudBackupManager.uploadBackup(cards, token)
                                    if (status.success) {
                                        status.updatedAt?.takeIf { it.isNotBlank() }?.let {
                                            AppSettings.setLastCloudBackupAt(context, it)
                                            lastCloudBackupAt = it
                                        }
                                        statusMessage = "Uploaded ${cards.size} cards to backup"
                                    } else {
                                        statusMessage = status.message
                                    }
                                    isBusy = false
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsRow(
                            iconRes = R.drawable.ic_cloud_download,
                            title = "Restore from token",
                            subtitle = "Bring cards back onto this device from a backup token",
                            enabled = !isBusy,
                            onClick = { showRestoreDialog = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            iconRes = R.drawable.ic_delete,
                            title = "Delete current remote backup",
                            subtitle = "Remove the backup tied to the saved token",
                            enabled = !localToken.isNullOrBlank() && !isBusy,
                            onClick = { showDeleteCloudBackupDialog = true }
                        )
                        SettingsDivider()
                        SettingsRow(
                            iconRes = R.drawable.ic_close,
                            title = "Clear local token",
                            subtitle = "Forget the token on this device only",
                            enabled = !localToken.isNullOrBlank() && !isBusy,
                            onClick = {
                                AppSettings.clearCloudBackupToken(context)
                                localToken = null
                                statusMessage = "Local token removed from this device"
                            }
                        )
                    }
                }
            }

            // 4. LOCAL BACKUP
            item(key = "settings_local_backup_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appleEntrance(index = 3, reduceMotion = reduceMotion)
                ) {
                    SettingsSectionHeader("Local Backup")
                    Spacer(Modifier.height(8.dp))
                    SettingsCard {
                        SettingsRow(
                            iconRes = R.drawable.ic_backup,
                            title = "Export backup file",
                            subtitle = "Save all current cards to one JSON backup",
                            onClick = onExportLocalBackup
                        )
                        SettingsDivider()
                        SettingsRow(
                            iconRes = R.drawable.ic_import,
                            title = "Import backup file",
                            subtitle = "Replace current cards using a local JSON backup",
                            onClick = onImportLocalBackup
                        )
                        SettingsDivider()
                        SettingsRow(
                            iconRes = R.drawable.ic_export_file,
                            title = "Export one HTML file",
                            subtitle = "Choose a card and save just that HTML back to storage",
                            enabled = cards.isNotEmpty(),
                            onClick = { showExportHtmlDialog = true }
                        )
                    }
                }
            }

            // 5. ABOUT
            item(key = "settings_about_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appleEntrance(index = 4, reduceMotion = reduceMotion)
                ) {
                    SettingsSectionHeader("About")
                    Spacer(Modifier.height(8.dp))
                    SettingsCard {
                        SettingsRow(
                            iconRes = R.drawable.ic_info,
                            title = "HTML Labs",
                            subtitle = "Version 1.0",
                            trailing = null,
                            onClick = {}
                        )
                        SettingsDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .applePressable(pressedScale = 0.98f) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://hoangkhanhminh.pages.dev"))
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            SettingsIconBadge(iconRes = R.drawable.ic_open)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Developed by Khanh Minh",
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "hoangkhanhminh.pages.dev",
                                    color = colorScheme.primary,
                                    fontSize = 12.sp,
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top status bar & bottom navigation bar seamless fade
        if (statusBarTop > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 14.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.background.copy(alpha = 0.94f),
                                colorScheme.background.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        if (navBarBottom > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navBarBottom + 14.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colorScheme.background.copy(alpha = 0.72f),
                                colorScheme.background.copy(alpha = 0.94f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun tonserSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    uncheckedTrackColor = MaterialTheme.colorScheme.secondary,
    uncheckedBorderColor = Color.Transparent
)

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(0.8.dp, colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        thickness = 0.8.dp,
        modifier = Modifier.padding(start = 70.dp, end = 16.dp)
    )
}

@Composable
private fun SettingsIconBadge(iconRes: Int) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.secondary.copy(alpha = 0.65f),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = {
        Text(
            text = "›",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    },
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .applePressable(pressedScale = 0.98f, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.48f }
    ) {
        SettingsIconBadge(iconRes = iconRes)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colorScheme.onSurface.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun SingleHtmlExportDialog(
    cards: List<HtmlCard>,
    onDismiss: () -> Unit,
    onSelect: (HtmlCard) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export one HTML file", color = colorScheme.onSurface) },
        text = {
            if (cards.isEmpty()) {
                Text(
                    text = "There are no HTML cards to export yet.",
                    color = colorScheme.onSurface.copy(alpha = 0.75f)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cards.forEach { card ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colorScheme.secondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(card) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.padding(top = 4.dp))
                                Text(
                                    text = "Save this card back to storage as a standalone .html file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSecondary.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary,
                    contentColor = colorScheme.onSecondary
                )
            ) { Text("Close") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surface
    )
}

@Composable
private fun OneTimeTokenDialog(
    token: String,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup token", color = colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This token is shown only once in full. Save it somewhere safe before closing.",
                    color = colorScheme.onSurface.copy(alpha = 0.75f)
                )
                Text(text = token, color = colorScheme.onSurface)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCopy(token) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) { Text("Copy token") }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary,
                    contentColor = colorScheme.onSecondary
                )
            ) { Text("I saved it") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surface
    )
}

@Composable
private fun TokenInputDialog(
    title: String,
    confirmLabel: String,
    saveTokenToggleVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var tokenInput by remember { mutableStateOf("") }
    var saveToken by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = colorScheme.secondary) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
                        BasicTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSecondary),
                            cursorBrush = SolidColor(colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (tokenInput.isBlank()) {
                                    Text(
                                        "hkm-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSecondary.copy(alpha = 0.3f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
                if (saveTokenToggleVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = saveToken, onCheckedChange = { saveToken = it })
                        Text(
                            text = "Save this token on this device after restore",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tokenInput, saveToken) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary,
                    contentColor = colorScheme.onSecondary
                )
            ) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surface
    )
}

@Composable
private fun ReplaceTokenDialog(
    hasExistingToken: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var deleteOldBackup by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (hasExistingToken) "Create new token?" else "Create backup token?",
                color = colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (hasExistingToken) {
                        "A new token will replace the local one on this device. The old backup can still be restored unless you delete it now."
                    } else {
                        "We'll generate a new backup token and show it once in full. Save it before closing."
                    },
                    color = colorScheme.onSurface.copy(alpha = 0.75f)
                )
                if (hasExistingToken) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteOldBackup, onCheckedChange = { deleteOldBackup = it })
                        Text(
                            text = "Also delete backup tied to the current token",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deleteOldBackup) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) { Text(if (hasExistingToken) "Replace token" else "Create token") }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary,
                    contentColor = colorScheme.onSecondary
                )
            ) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surface
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = colorScheme.onSurface) },
        text = { Text(body, color = colorScheme.onSurface.copy(alpha = 0.75f)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary,
                    contentColor = colorScheme.onSecondary
                )
            ) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surface
    )
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("HTML Labs backup token", value))
}
