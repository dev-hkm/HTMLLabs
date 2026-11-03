package com.hkm.htmllabs.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hkm.htmllabs.data.HtmlCard
import com.hkm.htmllabs.data.HtmlTag
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

object HtmlUtils {
    fun extractTitle(htmlContent: String): String {
        val titleRegex = "<title>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = titleRegex.find(htmlContent)
        return matchResult?.groupValues?.get(1)?.trim() ?: "Untitled"
    }

    fun extractTagLabels(rawTags: String): List<String> {
        return rawTags
            .split(",", "\n", ";", "#")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { normalizeForSearch(it) }
    }

    fun normalizeForSearch(value: String): String {
        return value
            .lowercase()
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun buildSearchIndex(title: String, tags: List<HtmlTag>, htmlContent: String): String {
        val plainText = htmlContent
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("<[^>]*>"), " ")
        return normalizeForSearch(
            buildString {
                append(title)
                append(' ')
                append(tags.joinToString(" ") { it.label })
                append(' ')
                append(extractTitle(htmlContent))
                append(' ')
                append(plainText)
            }
        )
    }

    fun matchesSearch(card: HtmlCard, query: String): Boolean {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isBlank()) return true
        return normalizedQuery
            .split(' ')
            .filter { it.isNotBlank() }
            .all { token -> card.searchIndex.contains(token) }
    }

    fun readTextFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } ?: ""
    }

    fun guessDisplayTitle(fileName: String?, content: String): String {
        val extractedTitle = extractTitle(content)
        if (extractedTitle != "Untitled") return extractedTitle
        val cleanName = fileName
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()
        return if (cleanName.isNullOrBlank()) "Untitled" else cleanName
    }

    fun getFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    /**
     * Write the card's HTML to a file in cacheDir and launch an external viewer
     * (browser) via Intent.ACTION_VIEW. Falls back gracefully if no app can
     * handle text/html.
     */
    fun openHtmlInExternalBrowser(context: Context, card: HtmlCard) {
        val appContext = context.applicationContext
        try {
            val htmlFile = File(appContext.cacheDir, "${card.id}.html")
            htmlFile.writeText(card.htmlContent)

            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                htmlFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/html")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(appContext, "No app found to open HTML", Toast.LENGTH_SHORT).show()
        }
    }
}
