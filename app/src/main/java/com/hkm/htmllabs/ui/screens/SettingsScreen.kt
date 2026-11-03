package com.hkm.htmllabs.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import com.hkm.htmllabs.R
import com.hkm.htmllabs.data.AppSettings
import com.hkm.htmllabs.data.CloudBackupManager
import com.hkm.htmllabs.data.HtmlCard
import com.hkm.htmllabs.data.HtmlRepository
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

    BackHandler {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onClose()
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onBackground
                )

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onClose()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    )
                ) {
                    Text("Done")
                }
            }

            Spacer(modifier = Modifier.padding(top = 10.dp))

            statusMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.padding(top = 14.dp))
            }

            SectionLabel("APPEARANCE", R.drawable.ic_theme)
            SettingCard {
                SettingSummary(
                    title = "Theme",
                    subtitle = "Switch between system, light, and dark mode."
                )
                Spacer(modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSettings.ThemeMode.entries.forEach { mode ->
                        val selected = themeMode == mode
                        Button(
                            onClick = {
                                themeMode = mode
                                AppSettings.setThemeMode(context, mode)
                                onThemeModeChanged(mode)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) colorScheme.primary else colorScheme.secondary,
                                contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSecondary
                            )
                        ) {
                            Text(
                                when (mode) {
                                    AppSettings.ThemeMode.SYSTEM -> "System"
                                    AppSettings.ThemeMode.LIGHT -> "Light"
                                    AppSettings.ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.padding(top = 18.dp))
                ToggleSettingRow(
                    title = "HTML preview on cards",
                    subtitle = "Show a static preview instead of the gradient cover.",
                    checked = previewMode,
                    onCheckedChange = {
                        previewMode = it
                        onCardPreviewModeChanged(it)
                    }
                )
            }

            Spacer(modifier = Modifier.padding(top = 20.dp))

            SectionLabel("VIEWER", R.drawable.ic_browser)
            SettingCard {
                ToggleSettingRow(
                    title = "Open HTML in app",
                    subtitle = "Use the built-in browser with fullscreen, download support, and navigation controls.",
                    checked = openInApp,
                    onCheckedChange = {
                        openInApp = it
                        AppSettings.setOpenInApp(context, it)
                    }
                )
            }

            Spacer(modifier = Modifier.padding(top = 20.dp))

            SectionLabel("CLOUD BACKUP", R.drawable.ic_cloud)
            BackupSectionCard {
                SummaryPill(
                    title = if (localToken.isNullOrBlank()) "No token saved" else CloudBackupManager.maskToken(localToken!!),
                    subtitle = if (lastCloudBackupAt.isNullOrBlank()) {
                        "Create or add a token, then back up ${cards.size} cards."
                    } else {
                        "Last backup: $lastCloudBackupAt"
                    }
                )
                BackupAction(
                    iconRes = R.drawable.ic_key_plus,
                    title = if (localToken.isNullOrBlank()) "Create backup token" else "Create new token",
                    subtitle = "Generate a fresh token and reveal it one time.",
                    onClick = { showReplaceDialog = true }
                )
                BackupAction(
                    iconRes = R.drawable.ic_key,
                    title = "Use existing token",
                    subtitle = "Save a token you already have on this device.",
                    onClick = { showUseExistingTokenDialog = true }
                )
                BackupAction(
                    iconRes = R.drawable.ic_cloud_upload,
                    title = "Backup now",
                    subtitle = "Upload ${cards.size} cards with the saved token.",
                    enabled = !localToken.isNullOrBlank() && !isBusy,
                    onClick = {
                        val token = localToken ?: return@BackupAction
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
                BackupAction(
                    iconRes = R.drawable.ic_cloud_download,
                    title = "Restore from token",
                    subtitle = "Bring cards back onto this device from a backup token.",
                    enabled = !isBusy,
                    onClick = { showRestoreDialog = true }
                )
                BackupAction(
                    iconRes = R.drawable.ic_delete,
                    title = "Delete current remote backup",
                    subtitle = "Remove the backup tied to the saved token.",
                    enabled = !localToken.isNullOrBlank() && !isBusy,
                    onClick = { showDeleteCloudBackupDialog = true }
                )
                BackupAction(
                    iconRes = R.drawable.ic_close,
                    title = "Clear local token",
                    subtitle = "Forget the token on this device only.",
                    enabled = !localToken.isNullOrBlank() && !isBusy,
                    onClick = {
                        AppSettings.clearCloudBackupToken(context)
                        localToken = null
                        statusMessage = "Local token removed from this device"
                    }
                )
            }

            Spacer(modifier = Modifier.padding(top = 20.dp))

            SectionLabel("LOCAL BACKUP", R.drawable.ic_folder)
            BackupSectionCard {
                SummaryPill(
                    title = "Files on this device",
                    subtitle = "Export everything as JSON, restore from JSON, or save one HTML file back to storage."
                )
                BackupAction(
                    iconRes = R.drawable.ic_backup,
                    title = "Export backup file",
                    subtitle = "Save all current cards to one JSON backup.",
                    onClick = onExportLocalBackup
                )
                BackupAction(
                    iconRes = R.drawable.ic_import,
                    title = "Import backup file",
                    subtitle = "Replace current cards using a local JSON backup.",
                    onClick = onImportLocalBackup
                )
                BackupAction(
                    iconRes = R.drawable.ic_export_file,
                    title = "Export one HTML file",
                    subtitle = "Choose a card and save just that HTML back to your device.",
                    enabled = cards.isNotEmpty(),
                    onClick = { showExportHtmlDialog = true }
                )
            }

            Spacer(modifier = Modifier.padding(top = 20.dp))

            SectionLabel("ABOUT", R.drawable.ic_info)
            SettingCard {
                Text(
                    text = "HTML Labs",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = "Version 1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                )
                Spacer(modifier = Modifier.padding(top = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Developed by ",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                    Text(
                        text = "Khanh Minh",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://hoangkhanhminh.pages.dev"))
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SectionLabel(text: String, iconRes: Int) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colorScheme.onBackground.copy(alpha = 0.55f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onBackground.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun BackupSectionCard(content: @Composable ColumnScope.() -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SettingSummary(title: String, subtitle: String) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface
    )
    Spacer(modifier = Modifier.padding(top = 4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurface.copy(alpha = 0.65f)
    )
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.onPrimary,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = colorScheme.onSecondary,
                uncheckedTrackColor = colorScheme.secondary
            )
        )
    }
}

@Composable
private fun SummaryPill(title: String, subtitle: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.secondary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSecondary
            )
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSecondary.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun BackupAction(
    iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.onSecondary.copy(alpha = 0.09f)
            ) {
                Box(
                    modifier = Modifier.padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = if (enabled) colorScheme.onSecondary else colorScheme.onSecondary.copy(alpha = 0.45f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) colorScheme.onSecondary else colorScheme.onSecondary.copy(alpha = 0.45f)
                )
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) colorScheme.onSecondary.copy(alpha = 0.72f) else colorScheme.onSecondary.copy(alpha = 0.32f)
                )
            }
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
