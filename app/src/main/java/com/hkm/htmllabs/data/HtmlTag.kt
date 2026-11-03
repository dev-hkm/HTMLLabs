package com.hkm.htmllabs.data

data class HtmlTag(
    val label: String,
    val startColorHex: Long = DEFAULT_COLOR_HEX,
    val endColorHex: Long = startColorHex
) {
    val colorHex: Long
        get() = startColorHex

    companion object {
        const val DEFAULT_COLOR_HEX: Long = 0xFF4C7DFF
    }
}
