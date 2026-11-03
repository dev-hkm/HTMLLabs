package com.hkm.htmllabs

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import com.hkm.htmllabs.data.AppSettings
import com.hkm.htmllabs.data.BackupManager
import com.hkm.htmllabs.data.HtmlCard
import com.hkm.htmllabs.data.HtmlRepository
import com.hkm.htmllabs.ui.components.HtmlCardItem
import com.hkm.htmllabs.ui.components.GradientChoiceOption
import com.hkm.htmllabs.ui.components.GradientPickerDialog
import com.hkm.htmllabs.ui.screens.EditorScreen
import com.hkm.htmllabs.ui.screens.SettingsScreen
import com.hkm.htmllabs.ui.screens.ViewerScreen
import com.hkm.htmllabs.ui.theme.HTMLLabsTheme
import com.hkm.htmllabs.utils.GradientPresets
import com.hkm.htmllabs.utils.HtmlUtils
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var repository: HtmlRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HtmlRepository(this)
        enableEdgeToEdge()

        setContent {
            var themeMode by remember { mutableStateOf(AppSettings.getThemeMode(this)) }
            val prefersDark = when (themeMode) {
                AppSettings.ThemeMode.SYSTEM -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                AppSettings.ThemeMode.LIGHT -> false
                AppSettings.ThemeMode.DARK -> true
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (prefersDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (prefersDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }
            HTMLLabsTheme(themeMode = themeMode) {
                MainScreen(
                    onThemeModeChanged = { newMode ->
                        AppSettings.setThemeMode(this, newMode)
                        themeMode = newMode
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
    @Composable
    fun MainScreen(
        onThemeModeChanged: (AppSettings.ThemeMode) -> Unit
    ) {
        val view = LocalView.current
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val colorScheme = MaterialTheme.colorScheme
        val isDarkTheme = colorScheme.background.luminance() < 0.5f
        var showEditor by remember { mutableStateOf(false) }
        var editingCard by remember { mutableStateOf<HtmlCard?>(null) }
        var showSettings by remember { mutableStateOf(false) }
        var showViewer by remember { mutableStateOf(false) }
        var viewingCard by remember { mutableStateOf<HtmlCard?>(null) }
        var showSearch by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var colorPickerCard by remember { mutableStateOf<HtmlCard?>(null) }
        var pendingHtmlExportCard by remember { mutableStateOf<HtmlCard?>(null) }
        var previewCards by remember { mutableStateOf(AppSettings.isCardPreviewEnabled(context)) }
        var draggedCard by remember { mutableStateOf<HtmlCard?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var dragStartIndex by remember { mutableStateOf(-1) }
        val cards = repository.getCards()
        var dragCards by remember { mutableStateOf(cards) }
        val visibleCards = if (draggedCard != null && searchQuery.isBlank()) dragCards else cards
        val filteredCards by remember(visibleCards, searchQuery) {
            derivedStateOf {
                visibleCards.filter { HtmlUtils.matchesSearch(it, searchQuery) }
            }
        }
        val quickGradientOptions = remember(isDarkTheme) {
            (0 until GradientPresets.presetCount()).map { presetId ->
                val gradient = GradientPresets.getGradient(presetId, isDarkTheme)
                GradientChoiceOption(
                    key = presetId,
                    start = gradient.start,
                    end = gradient.end
                )
            }
        }

        val gridState = rememberLazyGridState()

        LaunchedEffect(cards, draggedCard) {
            if (draggedCard == null) dragCards = cards
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let {
                scope.launch {
                    val success = BackupManager.exportBackup(context, it, cards)
                    Toast.makeText(
                        context,
                        if (success) "Backup exported successfully" else "Export failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val importBackupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let {
                scope.launch {
                    val backupData = BackupManager.importBackup(context, it)
                    if (backupData != null) {
                        repository.restoreFromBackup(backupData.items)
                        Toast.makeText(
                            context,
                            "Backup restored: ${backupData.items.size} items",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val htmlExportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/html")
        ) { uri ->
            val card = pendingHtmlExportCard
            if (uri == null || card == null) {
                pendingHtmlExportCard = null
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val success = BackupManager.exportHtmlCard(context, uri, card)
                Toast.makeText(
                    context,
                    if (success) "\"${card.title}\" exported" else "HTML export failed",
                    Toast.LENGTH_SHORT
                ).show()
                pendingHtmlExportCard = null
            }
        }

        val htmlImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                var importedCount = 0
                uris.forEach { uri ->
                    try {
                        val content = HtmlUtils.readTextFromUri(context, uri)
                        if (content.isBlank()) return@forEach
                        repository.addCard(
                            HtmlCard(
                                id = UUID.randomUUID().toString(),
                                title = HtmlUtils.guessDisplayTitle(
                                    HtmlUtils.getFileName(context, uri),
                                    content
                                ),
                                htmlContent = content
                            )
                        )
                        importedCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                Toast.makeText(
                    context,
                    if (importedCount > 0) {
                        "Imported $importedCount file${if (importedCount > 1) "s" else ""}"
                    } else {
                        "No valid HTML/TXT files found"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        LaunchedEffect(showSearch) {
            if (!showSearch) {
                searchQuery = ""
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HTML Labs",
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onBackground
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                htmlImportLauncher.launch(
                                    arrayOf("text/html", "text/plain", "text/*", "*/*")
                                )
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_import),
                                contentDescription = "Import HTML",
                                tint = colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                showSearch = !showSearch
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = if (showSearch) R.drawable.ic_close else R.drawable.ic_search),
                                contentDescription = if (showSearch) "Close search" else "Search",
                                tint = colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                showSettings = true
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = "Settings",
                                tint = colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(240)),
                    exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(220))
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isBlank()) {
                                        Text(
                                            text = "Search title, tags, content...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorScheme.onSurface.copy(alpha = 0.42f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }

                if (cards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No HTML files yet\nTap + to create one",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else if (filteredCards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No cards match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 116.dp)
                    ) {
                        items(filteredCards, key = { it.id }) { card ->
                            val isDragging = searchQuery.isBlank() && draggedCard?.id == card.id

                            HtmlCardItem(
                                card = card,
                                showPreview = previewCards,
                                isDarkTheme = isDarkTheme,
                                onOpenClick = {
                                    if (AppSettings.isOpenInApp(context)) {
                                        viewingCard = card
                                        showViewer = true
                                    } else {
                                        HtmlUtils.openHtmlInExternalBrowser(context, card)
                                    }
                                },
                                onEditClick = {
                                    editingCard = card
                                    showEditor = true
                                },
                                onDeleteClick = {
                                    scope.launch { repository.deleteCard(card.id) }
                                },
                                onPinClick = {
                                    scope.launch { repository.setPinned(card.id, !card.isPinned) }
                                },
                                onGradientLongPress = {
                                    colorPickerCard = card
                                },
                                modifier = Modifier
                                    .alpha(if (isDragging) 0.5f else 1f)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .pointerInput(Unit) {
                                        if (searchQuery.isNotBlank()) return@pointerInput
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                draggedCard = card
                                                dragOffset = Offset.Zero
                                                dragStartIndex = dragCards.indexOfFirst { it.id == card.id }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                                val targetIndex = calculateGridTargetIndex(
                                                    dragStartIndex,
                                                    dragOffset,
                                                    dragCards.size
                                                )
                                                val currentIndex = dragCards.indexOfFirst { it.id == card.id }
                                                if (targetIndex != currentIndex && targetIndex in dragCards.indices) {
                                                    val mutableCards = dragCards.toMutableList()
                                                    mutableCards.removeAt(currentIndex)
                                                    mutableCards.add(targetIndex, card)
                                                    dragCards = mutableCards
                                                }
                                            },
                                            onDragEnd = {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                if (dragCards != cards) {
                                                    scope.launch { repository.reorderCards(dragCards) }
                                                }
                                                draggedCard = null
                                                dragOffset = Offset.Zero
                                                dragStartIndex = -1
                                            },
                                            onDragCancel = {
                                                draggedCard = null
                                                dragOffset = Offset.Zero
                                                dragStartIndex = -1
                                                dragCards = cards
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }

            FloatingActionButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    editingCard = null
                    showEditor = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .size(64.dp),
                shape = RoundedCornerShape(24.dp),
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp)
                )
            }

            colorPickerCard?.let { card ->
                GradientPickerDialog(
                    title = "Quick color",
                    subtitle = "Choose a gradient for ${card.title}",
                    options = quickGradientOptions,
                    selectedKey = card.gradientPresetId,
                    onDismiss = { colorPickerCard = null },
                    onSelected = { option ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        scope.launch {
                            repository.updateCard(card.copy(gradientPresetId = option.key))
                        }
                        colorPickerCard = null
                    }
                )
            }

            AnimatedVisibility(
                visible = showEditor,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                EditorScreen(
                    card = editingCard,
                    onSave = { title, content, tags ->
                        scope.launch {
                            if (editingCard != null) {
                                repository.updateCard(
                                    editingCard!!.copy(
                                        title = title,
                                        htmlContent = content,
                                        tags = tags
                                    )
                                )
                            } else {
                                repository.addCard(
                                    HtmlCard(
                                        id = UUID.randomUUID().toString(),
                                        title = title,
                                        htmlContent = content,
                                        tags = tags
                                    )
                                )
                            }
                            showEditor = false
                            editingCard = null
                        }
                    },
                    onCancel = {
                        showEditor = false
                        editingCard = null
                    }
                )
            }

            AnimatedVisibility(
                visible = showViewer && viewingCard != null,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + scaleIn(initialScale = 0.92f) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + scaleOut(targetScale = 0.92f) + fadeOut()
            ) {
                viewingCard?.let { card ->
                    ViewerScreen(
                        card = card,
                        onClose = {
                            showViewer = false
                            viewingCard = null
                        },
                        onEdit = {
                            showViewer = false
                            editingCard = card
                            viewingCard = null
                            showEditor = true
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = showSettings,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                SettingsScreen(
                    repository = repository,
                    cards = cards,
                    cardPreviewEnabled = previewCards,
                    onExportLocalBackup = {
                        exportLauncher.launch(BackupManager.generateBackupFileName())
                    },
                    onImportLocalBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    onExportSingleHtml = { card ->
                        pendingHtmlExportCard = card
                        htmlExportLauncher.launch(BackupManager.generateHtmlFileName(card.title))
                    },
                    onCardPreviewModeChanged = {
                        AppSettings.setCardPreviewEnabled(context, it)
                        previewCards = it
                    },
                    onThemeModeChanged = onThemeModeChanged,
                    onClose = { showSettings = false }
                )
            }
        }
    }

    private fun calculateGridTargetIndex(startIndex: Int, offset: Offset, totalItems: Int): Int {
        if (startIndex < 0 || totalItems == 0) return startIndex
        val columns = 2
        val columnShift = (offset.x / 170f).toInt()
        val rowShift = (offset.y / 155f).toInt()
        val startRow = startIndex / columns
        val startColumn = startIndex % columns
        val row = (startRow + rowShift).coerceAtLeast(0)
        val column = (startColumn + columnShift).coerceIn(0, columns - 1)
        return (row * columns + column).coerceIn(0, totalItems - 1)
    }
}
