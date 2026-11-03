package com.hkm.htmllabs.utils

import androidx.compose.ui.graphics.Color

object TagPresets {
    data class TagGradient(
        val id: Int,
        val start: Color,
        val end: Color
    )

    val gradients = listOf(
        TagGradient(0, Color(0xFF4C7DFF), Color(0xFF7C5CFF)),
        TagGradient(1, Color(0xFF00A38C), Color(0xFF0FC3B4)),
        TagGradient(2, Color(0xFFE56B6F), Color(0xFFFF9472)),
        TagGradient(3, Color(0xFFF0A202), Color(0xFFFFC444)),
        TagGradient(4, Color(0xFF8C6BFF), Color(0xFFC084FC)),
        TagGradient(5, Color(0xFF3FA34D), Color(0xFF74C365)),
        TagGradient(6, Color(0xFFEF476F), Color(0xFFFF7096)),
        TagGradient(7, Color(0xFF118AB2), Color(0xFF4CC9F0)),
        TagGradient(8, Color(0xFF7F5AF0), Color(0xFF2CB67D)),
        TagGradient(9, Color(0xFFFF6B6B), Color(0xFFFFC75F)),
        TagGradient(10, Color(0xFF277DA1), Color(0xFF43AA8B)),
        TagGradient(11, Color(0xFF9D4EDD), Color(0xFFFF4D8D)),
        TagGradient(12, Color(0xFF355C7D), Color(0xFFC06C84)),
        TagGradient(13, Color(0xFF0F766E), Color(0xFF34D399)),
        TagGradient(14, Color(0xFFD97706), Color(0xFFF97316)),
        TagGradient(15, Color(0xFF2563EB), Color(0xFF06B6D4)),
        TagGradient(16, Color(0xFFDC2626), Color(0xFFF59E0B)),
        TagGradient(17, Color(0xFF1D4ED8), Color(0xFF9333EA)),
        TagGradient(18, Color(0xFF0EA5E9), Color(0xFF10B981)),
        TagGradient(19, Color(0xFFE11D48), Color(0xFF7C3AED))
    )

    fun defaultGradient(): TagGradient = gradients.first()

    fun findGradient(startColorHex: Long, endColorHex: Long): TagGradient {
        return gradients.firstOrNull {
            it.start.value.toLong() == startColorHex && it.end.value.toLong() == endColorHex
        } ?: TagGradient(
            id = -1,
            start = Color(startColorHex),
            end = Color(endColorHex)
        )
    }
}
