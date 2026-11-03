package com.hkm.htmllabs.utils

import androidx.compose.ui.graphics.Color

object GradientPresets {
    data class Gradient(val start: Color, val end: Color)

    private val darkPresets = listOf(
        Gradient(Color(0xFF2A2A2A), Color(0xFF101010)),
        Gradient(Color(0xFF413838), Color(0xFF171212)),
        Gradient(Color(0xFF243146), Color(0xFF101720)),
        Gradient(Color(0xFF3C2947), Color(0xFF18101D)),
        Gradient(Color(0xFF27413C), Color(0xFF11201C)),
        Gradient(Color(0xFF5C2F34), Color(0xFF261114)),
        Gradient(Color(0xFF6A4627), Color(0xFF291707)),
        Gradient(Color(0xFF676329), Color(0xFF2A260C)),
        Gradient(Color(0xFF2F5D48), Color(0xFF0F271D)),
        Gradient(Color(0xFF345F74), Color(0xFF102733)),
        Gradient(Color(0xFF54427A), Color(0xFF20182D)),
        Gradient(Color(0xFF1F6168), Color(0xFF0E2C31)),
        Gradient(Color(0xFF704E6A), Color(0xFF2D1C29)),
        Gradient(Color(0xFF685B4A), Color(0xFF2C2419)),
        Gradient(Color(0xFF5E3D3D), Color(0xFF221111)),
        Gradient(Color(0xFF3E4869), Color(0xFF14192A)),
        Gradient(Color(0xFF5A3A22), Color(0xFF24150A)),
        Gradient(Color(0xFF244B5A), Color(0xFF0D1D24)),
        Gradient(Color(0xFF564429), Color(0xFF21180A)),
        Gradient(Color(0xFF3D3D57), Color(0xFF141421))
    )

    private val lightPresets = listOf(
        Gradient(Color(0xFFF3F5FA), Color(0xFFE0E6F3)),
        Gradient(Color(0xFFF9ECEE), Color(0xFFF1D7DB)),
        Gradient(Color(0xFFEAF2FF), Color(0xFFD7E5FF)),
        Gradient(Color(0xFFF3EDFF), Color(0xFFE4D8FF)),
        Gradient(Color(0xFFEAF7F2), Color(0xFFD6EFE4)),
        Gradient(Color(0xFFFFEFE6), Color(0xFFF9D9C3)),
        Gradient(Color(0xFFFFF6DF), Color(0xFFF3E1A4)),
        Gradient(Color(0xFFE6F8F7), Color(0xFFCDEDEC)),
        Gradient(Color(0xFFFBE8F0), Color(0xFFF2CFE1)),
        Gradient(Color(0xFFEAF0E2), Color(0xFFDCE7C8)),
        Gradient(Color(0xFFF6F0E7), Color(0xFFE9DBC7)),
        Gradient(Color(0xFFEAEAF7), Color(0xFFDADAF0)),
        Gradient(Color(0xFFF0ECFF), Color(0xFFE0D6FF)),
        Gradient(Color(0xFFE7F7FB), Color(0xFFD0EEF7)),
        Gradient(Color(0xFFFFEEF4), Color(0xFFF5D6E3)),
        Gradient(Color(0xFFF1F6E8), Color(0xFFDFEBCF)),
        Gradient(Color(0xFFFFF1E8), Color(0xFFF4DBC7)),
        Gradient(Color(0xFFE6F6FB), Color(0xFFCBE8F5)),
        Gradient(Color(0xFFFFF7E9), Color(0xFFF8E2BE)),
        Gradient(Color(0xFFEFF0FF), Color(0xFFD9DBFF))
    )

    fun getGradient(presetId: Int, isDarkTheme: Boolean): Gradient {
        val presets = if (isDarkTheme) darkPresets else lightPresets
        return presets[Math.abs(presetId) % presets.size]
    }

    fun presetCount(): Int = darkPresets.size

    fun selectBestPreset(cardId: String, adjacentPresetIds: List<Int>): Int {
        val total = presetCount()
        val hash = cardId.hashCode().toLong().and(0xFFFFFFFFL).toInt()
        var selectedId = Math.abs(hash) % total
        var attempts = 0
        while (adjacentPresetIds.contains(selectedId) && attempts < total) {
            selectedId = (selectedId + 1) % total
            attempts++
        }
        return selectedId
    }

    fun nextPresetId(currentId: Int, neighborPresetIds: Collection<Int>): Int {
        val total = presetCount()
        val neighborSet = neighborPresetIds.toHashSet()
        var candidate = ((currentId + 1) % total + total) % total
        var attempts = 0
        while (neighborSet.contains(candidate) && attempts < total) {
            candidate = (candidate + 1) % total
            attempts++
        }
        return candidate
    }
}
