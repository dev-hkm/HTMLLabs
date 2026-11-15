package com.hkm.htmllabs.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.hkm.htmllabs.ui.theme.AppMotion
import com.hkm.htmllabs.ui.theme.applePressable

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
                        val itemScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.04f else 1f,
                            animationSpec = AppMotion.softPopSpring(),
                            label = "gradient_scale"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) colorScheme.primary else Color.Transparent,
                            animationSpec = tween(AppMotion.Fast, easing = AppMotion.AppleSilk),
                            label = "gradient_border"
                        )
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = itemScale
                                    scaleY = itemScale
                                }
                                .border(2.dp, borderColor, RoundedCornerShape(18.dp))
                                .padding(3.dp)
                                .applePressable(pressedScale = 0.94f) { onSelected(option) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 74.dp, height = 46.dp)
                                    .background(
                                        brush = Brush.linearGradient(listOf(option.start, option.end)),
                                        shape = RoundedCornerShape(15.dp)
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
