package com.hkm.htmllabs.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object HtmlPreviewCache {
    private const val CACHE_DIR = "html_previews"
    private const val PREVIEW_WIDTH = 400
    private const val PREVIEW_HEIGHT = 300
    
    fun getCacheFile(context: Context, cardId: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        cacheDir.mkdirs()
        return File(cacheDir, "$cardId.png")
    }
    
    fun getContentHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
    
    suspend fun generatePreview(context: Context, cardId: String, htmlContent: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val bitmap = Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.BLACK)
                
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    isClickable = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
                
                webView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(PREVIEW_WIDTH, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(PREVIEW_HEIGHT, android.view.View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT)
                
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                
                Thread.sleep(1000)
                
                webView.draw(canvas)
                
                val cacheFile = getCacheFile(context, cardId)
                FileOutputStream(cacheFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 85, fos)
                }
                
                bitmap.recycle()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    fun clearCache(context: Context, cardId: String) {
        try {
            getCacheFile(context, cardId).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun clearAllCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
