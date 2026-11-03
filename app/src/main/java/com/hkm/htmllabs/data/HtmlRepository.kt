package com.hkm.htmllabs.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.hkm.htmllabs.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.hkm.htmllabs.utils.GradientPresets

class HtmlRepository(private val context: Context) {
    private val htmlCards = mutableStateListOf<HtmlCard>()
    private val storageFile = File(context.filesDir, "html_cards.json")

    init {
        loadCards()
    }

    fun getCards(): List<HtmlCard> = htmlCards.sortedWith(
        compareByDescending<HtmlCard> { it.isPinned }.thenBy { it.sortOrder }
    )

    suspend fun addCard(card: HtmlCard) {
        withContext(Dispatchers.IO) {
            // Shift all existing cards down
            val updatedCards = htmlCards.map { it.copy(sortOrder = it.sortOrder + 1) }
            htmlCards.clear()
            htmlCards.addAll(updatedCards)
            
            // Select gradient preset based on card ID
            val presetId = GradientPresets.selectBestPreset(card.id, emptyList())
            val newHash = HtmlPreviewCache.getContentHash(card.htmlContent)
            
            htmlCards.add(card.copy(
                sortOrder = 0,
                previewHash = newHash,
                gradientPresetId = presetId,
                searchIndex = HtmlUtils.buildSearchIndex(
                    title = card.title,
                    tags = card.tags,
                    htmlContent = card.htmlContent
                )
            ))
            saveCards()
        }
    }

    suspend fun updateCard(card: HtmlCard) {
        withContext(Dispatchers.IO) {
            val index = htmlCards.indexOfFirst { it.id == card.id }
            if (index != -1) {
                val newHash = HtmlPreviewCache.getContentHash(card.htmlContent)
                val oldHash = htmlCards[index].previewHash

                // Invalidate preview if content changed
                if (newHash != oldHash) {
                    HtmlPreviewCache.clearCache(context, card.id)
                }

                // Preserve the incoming gradientPresetId (so callers can
                // change it, e.g. long-press cycling). Only recompute the
                // preview hash.
                htmlCards[index] = card.copy(
                    previewHash = newHash,
                    searchIndex = HtmlUtils.buildSearchIndex(
                        title = card.title,
                        tags = card.tags,
                        htmlContent = card.htmlContent
                    )
                )
                saveCards()
            }
        }
    }

    suspend fun deleteCard(cardId: String) {
        withContext(Dispatchers.IO) {
            htmlCards.removeIf { it.id == cardId }
            saveCards()
        }
    }
    
    suspend fun reorderCards(reorderedCards: List<HtmlCard>) {
        withContext(Dispatchers.IO) {
            htmlCards.clear()
            reorderedCards.forEachIndexed { index, card ->
                htmlCards.add(card.copy(sortOrder = index))
            }
            saveCards()
        }
    }
    
    suspend fun restoreFromBackup(cards: List<HtmlCard>) {
        withContext(Dispatchers.IO) {
            htmlCards.clear()
            htmlCards.addAll(
                cards.mapIndexed { index, card ->
                    val normalizedContent = card.htmlContent
                    card.copy(
                        sortOrder = index,
                        previewHash = HtmlPreviewCache.getContentHash(normalizedContent),
                        searchIndex = HtmlUtils.buildSearchIndex(
                            title = card.title,
                            tags = card.tags,
                            htmlContent = normalizedContent
                        )
                    )
                }
            )
            saveCards()
        }
    }

    suspend fun setPinned(cardId: String, pinned: Boolean) {
        withContext(Dispatchers.IO) {
            val index = htmlCards.indexOfFirst { it.id == cardId }
            if (index != -1) {
                htmlCards[index] = htmlCards[index].copy(isPinned = pinned)
                normalizeSortOrder()
                saveCards()
            }
        }
    }

    private fun loadCards() {
        try {
            if (storageFile.exists()) {
                val jsonString = storageFile.readText()
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    htmlCards.add(
                        HtmlCard(
                            id = jsonObject.getString("id"),
                            title = jsonObject.getString("title"),
                            htmlContent = jsonObject.getString("htmlContent"),
                            tags = jsonObject.optJSONArray("tags").toTagList(),
                            timestamp = jsonObject.getLong("timestamp"),
                            sortOrder = jsonObject.optInt("sortOrder", i),
                            previewHash = jsonObject.optString("previewHash", ""),
                            gradientPresetId = jsonObject.optInt("gradientPresetId", 0),
                            searchIndex = jsonObject.optString("searchIndex", ""),
                            isPinned = jsonObject.optBoolean("isPinned", false)
                        )
                    )
                }
                if (htmlCards.any { it.searchIndex.isBlank() }) {
                    val repairedCards = htmlCards.map { card ->
                        card.copy(
                            searchIndex = HtmlUtils.buildSearchIndex(
                                title = card.title,
                                tags = card.tags,
                                htmlContent = card.htmlContent
                            )
                        )
                    }
                    htmlCards.clear()
                    htmlCards.addAll(repairedCards)
                    saveCards()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCards() {
        try {
            val jsonArray = JSONArray()
            htmlCards.forEach { card ->
                val jsonObject = JSONObject().apply {
                    put("id", card.id)
                    put("title", card.title)
                    put("htmlContent", card.htmlContent)
                    put("tags", JSONArray().apply {
                        card.tags.forEach { tag ->
                            put(JSONObject().apply {
                                put("label", tag.label)
                                put("startColorHex", tag.startColorHex)
                                put("endColorHex", tag.endColorHex)
                                put("colorHex", tag.colorHex)
                            })
                        }
                    })
                    put("timestamp", card.timestamp)
                    put("sortOrder", card.sortOrder)
                    put("previewHash", card.previewHash)
                    put("gradientPresetId", card.gradientPresetId)
                    put("searchIndex", card.searchIndex)
                    put("isPinned", card.isPinned)
                }
                jsonArray.put(jsonObject)
            }
            storageFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizeSortOrder() {
        getCards().forEachIndexed { index, card ->
            val sourceIndex = htmlCards.indexOfFirst { it.id == card.id }
            if (sourceIndex >= 0) htmlCards[sourceIndex] = card.copy(sortOrder = index)
        }
    }

    private fun JSONArray?.toTagList(): List<HtmlTag> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = opt(index)
                when (item) {
                    is JSONObject -> {
                        val label = item.optString("label").trim()
                        if (label.isNotEmpty()) {
                            add(
                                HtmlTag(
                                    label = label,
                                    startColorHex = item.optLong(
                                        "startColorHex",
                                        item.optLong("colorHex", HtmlTag.DEFAULT_COLOR_HEX)
                                    ),
                                    endColorHex = item.optLong(
                                        "endColorHex",
                                        item.optLong(
                                            "startColorHex",
                                            item.optLong("colorHex", HtmlTag.DEFAULT_COLOR_HEX)
                                        )
                                    )
                                )
                            )
                        }
                    }
                    is String -> {
                        val value = item.trim()
                        if (value.isNotEmpty()) {
                            add(HtmlTag(value))
                        }
                    }
                }
            }
        }
    }
}
