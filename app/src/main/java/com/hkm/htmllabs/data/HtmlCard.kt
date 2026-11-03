package com.hkm.htmllabs.data

data class HtmlCard(
    val id: String,
    val title: String,
    val htmlContent: String,
    val tags: List<HtmlTag> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val previewHash: String = "",
    val gradientPresetId: Int = 0,
    val searchIndex: String = "",
    val isPinned: Boolean = false
)
