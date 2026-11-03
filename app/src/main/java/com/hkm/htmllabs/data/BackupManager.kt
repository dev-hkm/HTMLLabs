package com.hkm.htmllabs.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object BackupManager {
    private const val BACKUP_VERSION = 4
    private const val APP_NAME = "HTML Labs"

    data class BackupData(
        val backupVersion: Int,
        val appName: String,
        val exportedAt: String,
        val items: List<HtmlCard>
    )

    fun createBackupJson(cards: List<HtmlCard>): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val jsonObject = JSONObject().apply {
            put("backupVersion", BACKUP_VERSION)
            put("appName", APP_NAME)
            put("exportedAt", timestamp)

            val itemsArray = JSONArray()
            cards.forEach { card ->
                val cardJson = JSONObject().apply {
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
                    put("gradientPresetId", card.gradientPresetId)
                    put("isPinned", card.isPinned)
                }
                itemsArray.put(cardJson)
            }
            put("items", itemsArray)
        }

        return jsonObject.toString(2)
    }

    fun parseBackupJson(jsonString: String): BackupData? {
        return try {
            val jsonObject = JSONObject(jsonString)

            val version = jsonObject.optInt("backupVersion", 0)
            if (version <= 0 || version > BACKUP_VERSION) {
                return null
            }

            val appName = jsonObject.optString("appName", "")
            val exportedAt = jsonObject.optString("exportedAt", "")

            val itemsArray = jsonObject.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<HtmlCard>()

            for (i in 0 until itemsArray.length()) {
                val cardJson = itemsArray.getJSONObject(i)

                val card = HtmlCard(
                    id = cardJson.optString("id", UUID.randomUUID().toString()),
                    title = cardJson.optString("title", "Untitled"),
                    htmlContent = cardJson.optString("htmlContent", ""),
                    tags = cardJson.optJSONArray("tags").toTagList(),
                    timestamp = cardJson.optLong("timestamp", System.currentTimeMillis()),
                    sortOrder = cardJson.optInt("sortOrder", i),
                    previewHash = cardJson.optString("previewHash", ""),
                    gradientPresetId = cardJson.optInt("gradientPresetId", 0),
                    isPinned = cardJson.optBoolean("isPinned", false)
                )

                items.add(card)
            }

            BackupData(version, appName, exportedAt, items)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportBackup(context: Context, uri: Uri, cards: List<HtmlCard>): Boolean {
        return try {
            val json = createBackupJson(cards)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportHtmlCard(context: Context, uri: Uri, card: HtmlCard): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(card.htmlContent.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importBackup(context: Context, uri: Uri): BackupData? {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: return null

            parseBackupJson(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US).format(Date())
        return "html-labs-backup-$timestamp.json"
    }

    fun generateHtmlFileName(title: String): String {
        val safeTitle = title
            .trim()
            .ifEmpty { "untitled" }
            .replace(Regex("[^a-zA-Z0-9-_ ]"), "")
            .replace(Regex("\\s+"), "-")
            .lowercase(Locale.US)
            .take(48)
            .ifEmpty { "untitled" }
        return "$safeTitle.html"
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
