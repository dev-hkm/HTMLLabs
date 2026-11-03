package com.hkm.htmllabs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class GradientChoiceOption(
    val key: Int,
    val start: Color,
    val end: Color
)

@Composable
fun GradientPickerDialog(
    title: String,
    subtitle: String,
    options: List<GradientChoiceOption>,
    selectedKey: Int? = null,
    onDismiss: () -> Unit,
    onSelected: (GradientChoiceOption) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = subtitle,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option.key == selectedKey
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                            modifier = Modifier.clickable { onSelected(option) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 78.dp, height = 48.dp)
                                    .background(
                                        brush = Brush.linearGradient(listOf(option.start, option.end)),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            )
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
            ) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = colorScheme.surface
    )
}
