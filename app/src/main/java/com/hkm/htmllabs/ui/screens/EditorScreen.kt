package com.hkm.htmllabs.ui.screens

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkm.htmllabs.R
import com.hkm.htmllabs.data.HtmlCard
import com.hkm.htmllabs.data.HtmlTag
import com.hkm.htmllabs.ui.components.GradientChoiceOption
import com.hkm.htmllabs.ui.components.GradientPickerDialog
import com.hkm.htmllabs.ui.theme.applePressable
import com.hkm.htmllabs.utils.HtmlUtils
import com.hkm.htmllabs.utils.TagPresets

@Composable
fun EditorScreen(
    card: HtmlCard?,
    onSave: (String, String, List<HtmlTag>) -> Unit,
    onCancel: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val tagOptions = remember {
        TagPresets.gradients.map {
            GradientChoiceOption(it.id, it.start, it.end)
        }
    }
    val initialGradient = remember(card) {
        card?.tags?.firstOrNull()?.let {
            TagPresets.findGradient(it.startColorHex, it.endColorHex)
        } ?: TagPresets.defaultGradient()
    }
    var title by remember { mutableStateOf(card?.title ?: "") }
    var htmlContent by remember { mutableStateOf(card?.htmlContent ?: "") }
    var tagInput by remember { mutableStateOf("") }
    var selectedTagGradientKey by remember { mutableIntStateOf(initialGradient.id) }
    var tags by remember { mutableStateOf(card?.tags ?: emptyList()) }
    var showTagColorPicker by remember { mutableStateOf(false) }
    var editingTagIndex by remember { mutableStateOf<Int?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val content = HtmlUtils.readTextFromUri(context, it)
                htmlContent = content
                if (title.isEmpty() || title == "Untitled") {
                    title = HtmlUtils.guessDisplayTitle(HtmlUtils.getFileName(context, it), content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(htmlContent) {
        if (title.isEmpty() || title == "Untitled") {
            val extractedTitle = HtmlUtils.extractTitle(htmlContent)
            if (extractedTitle != "Untitled") {
                title = extractedTitle
            }
        }
    }

    fun selectedTagGradient(): TagPresets.TagGradient {
        return TagPresets.gradients.firstOrNull { it.id == selectedTagGradientKey }
            ?: TagPresets.defaultGradient()
    }

    fun addTagFromInput() {
        val labels = HtmlUtils.extractTagLabels(tagInput)
        if (labels.isEmpty()) return
        val gradient = selectedTagGradient()
        val existingKeys = tags.map { HtmlUtils.normalizeForSearch(it.label) }.toSet()
        val merged = tags.toMutableList()
        labels.forEach { label ->
            if (!existingKeys.contains(HtmlUtils.normalizeForSearch(label))) {
                merged += HtmlTag(
                    label = label,
                    startColorHex = gradient.start.value.toLong(),
                    endColorHex = gradient.end.value.toLong()
                )
            }
        }
        tags = merged
        tagInput = ""
    }

    if (showTagColorPicker) {
        val currentSelection = editingTagIndex
            ?.let { index -> tags.getOrNull(index) }
            ?.let { tag -> TagPresets.findGradient(tag.startColorHex, tag.endColorHex).id }
            ?: selectedTagGradientKey

        GradientPickerDialog(
            title = if (editingTagIndex == null) "Tag color" else "Edit tag color",
            subtitle = if (editingTagIndex == null) {
                "Choose a gradient for new tags."
            } else {
                "Choose a new gradient for this tag."
            },
            options = tagOptions,
            selectedKey = currentSelection,
            onDismiss = {
                showTagColorPicker = false
                editingTagIndex = null
            },
            onSelected = { option ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val index = editingTagIndex
                if (index == null) {
                    selectedTagGradientKey = option.key
                } else {
                    tags = tags.mapIndexed { tagIndex, tag ->
                        if (tagIndex == index) {
                            tag.copy(
                                startColorHex = option.start.value.toLong(),
                                endColorHex = option.end.value.toLong()
                            )
                        } else {
                            tag
                        }
                    }
                }
                showTagColorPicker = false
                editingTagIndex = null
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (card == null) "New HTML" else "Edit HTML",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onBackground
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .width(40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorScheme.primary)
                            .applePressable(pressedScale = 0.92f) {
                                importLauncher.launch(arrayOf("text/html", "text/*", "*/*"))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_import),
                            contentDescription = "Import HTML",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onCancel()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondary,
                            contentColor = colorScheme.onSecondary
                        )
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            addTagFromInput()
                            if (title.isNotBlank() && htmlContent.isNotBlank()) {
                                onSave(title, htmlContent, tags)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        ),
                        enabled = title.isNotBlank() && htmlContent.isNotBlank()
                    ) {
                        Text("Save", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            EditorField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Title...",
                textStyle = MaterialTheme.typography.titleMedium.copy(color = colorScheme.onSurface)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                                cursorBrush = SolidColor(colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (tagInput.isBlank()) {
                                        Text(
                                            "Add a tag...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colorScheme.onSurface.copy(alpha = 0.32f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                showTagColorPicker = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.secondary,
                                contentColor = colorScheme.onSecondary
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(selectedTagGradient().start, selectedTagGradient().end)
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Color")
                        }

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                addTagFromInput()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            )
                        ) {
                            Text("Add")
                        }
                    }

                    Text(
                        text = "Tap a tag chip to recolor it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface.copy(alpha = 0.58f)
                    )

                    if (tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEachIndexed { index, tag ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            brush = Brush.linearGradient(
                                                listOf(Color(tag.startColorHex), Color(tag.endColorHex))
                                            )
                                        )
                                        .clickable {
                                            editingTagIndex = index
                                            showTagColorPicker = true
                                        }
                                        .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tag.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "x",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.clickable {
                                            tags = tags.toMutableList().also { it.removeAt(index) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.padding(16.dp)) {
                    BasicTextField(
                        value = htmlContent,
                        onValueChange = { htmlContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        textStyle = TextStyle(
                            color = colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        cursorBrush = SolidColor(colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (htmlContent.isEmpty()) {
                                    Text(
                                        text = "<!DOCTYPE html>\n<html>\n<head>\n  <title>My Page</title>\n</head>\n<body>\n  \n</body>\n</html>",
                                        style = TextStyle(
                                            color = colorScheme.onSurface.copy(alpha = 0.3f),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.surface
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = textStyle,
                cursorBrush = SolidColor(colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = textStyle,
                            color = colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}
