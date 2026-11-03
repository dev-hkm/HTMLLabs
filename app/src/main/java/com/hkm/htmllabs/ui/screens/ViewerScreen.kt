package com.hkm.htmllabs.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hkm.htmllabs.R
import com.hkm.htmllabs.data.HtmlCard
import com.hkm.htmllabs.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ViewerScreen(card: HtmlCard, onClose: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var hasError by remember { mutableStateOf<String?>(null) }
    var fileReady by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf(card.title) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var localPageUrl by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    val activity = context.findActivity()

    LaunchedEffect(card.id, card.htmlContent) {
        withContext(Dispatchers.IO) {
            val cardDir = File(context.filesDir, "htmllabs/${card.id}")
            if (!cardDir.exists()) cardDir.mkdirs()
            val htmlFile = File(cardDir, "index.html")
            htmlFile.writeText(card.htmlContent)
            localPageUrl = htmlFile.toURI().toString()
            fileReady = true
        }
    }

    DisposableEffect(isFullscreen, activity, view) {
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        if (isFullscreen) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        val webView = webViewRef.value
        when {
            isFullscreen -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isFullscreen = false
            }
            webView != null && webView.canGoBack() -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                webView.goBack()
            }
            else -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClose()
            }
        }
    }

    DisposableEffect(card.id) {
        onDispose {
            webViewRef.value?.destroy()
            webViewRef.value = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (!isFullscreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onClose()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close",
                                tint = colorScheme.onSurface
                            )
                        }
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { webViewRef.value?.goBack() }, enabled = canGoBack) {
                            Text("<", color = if (canGoBack) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.35f))
                        }
                        IconButton(onClick = { webViewRef.value?.goForward() }, enabled = canGoForward) {
                            Text(">", color = if (canGoForward) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.35f))
                        }
                        IconButton(onClick = { webViewRef.value?.reload() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_refresh),
                                contentDescription = "Refresh",
                                tint = colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_fullscreen),
                                contentDescription = "Fullscreen",
                                tint = colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { HtmlUtils.openHtmlInExternalBrowser(context, card) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_open),
                                contentDescription = "Open in browser",
                                tint = colorScheme.onSurface
                            )
                        }
                    }

                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { (loadProgress.coerceIn(0, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            color = colorScheme.primary,
                            trackColor = colorScheme.secondary
                        )
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background),
                factory = { ctx ->
                    val blobBridge = BlobDownloadBridge(ctx)
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            defaultTextEncodingName = "UTF-8"
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportZoom(true)
                            setSupportMultipleWindows(false)
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        addJavascriptInterface(blobBridge, "HTMLLabsDownloader")

                        setDownloadListener(
                            DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                handleDownload(
                                    context = ctx,
                                    webView = this,
                                    url = url,
                                    userAgent = userAgent,
                                    contentDisposition = contentDisposition,
                                    mimeType = mimeType
                                )
                            }
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                if (newProgress >= 100) isLoading = false
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) pageTitle = title
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val target = request?.url ?: return false
                                val scheme = target.scheme?.lowercase().orEmpty()
                                return if (scheme in listOf("http", "https", "file", "about", "data", "blob")) {
                                    false
                                } else {
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, target))
                                    }.onFailure {
                                        Toast.makeText(
                                            ctx,
                                            "Cannot open this link from the in-app viewer",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                hasError = null
                                isLoading = true
                                loadProgress = 0
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                if (pageTitle.isBlank()) pageTitle = card.title
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                hasError = "Error $errorCode: $description"
                                isLoading = false
                            }
                        }

                        webViewRef.value = this
                    }
                },
                update = { webView ->
                    if (fileReady && localPageUrl != null && webView.url != localPageUrl) {
                        webView.loadUrl(localPageUrl!!)
                    }
                }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            ) {
                IconButton(onClick = { isFullscreen = false }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fullscreen_exit),
                        contentDescription = "Exit fullscreen",
                        tint = colorScheme.onSurface
                    )
                }
            }
        }

        hasError?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = err,
                    color = colorScheme.onBackground.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

private fun handleDownload(
    context: Context,
    webView: WebView,
    url: String?,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
) {
    if (url.isNullOrBlank()) {
        Toast.makeText(context, "Download link is empty", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = runCatching { Uri.parse(url) }.getOrNull()
    val scheme = uri?.scheme?.lowercase().orEmpty()

    when {
        scheme == "blob" -> {
            triggerBlobDownload(
                webView = webView,
                blobUrl = url,
                fallbackMimeType = mimeType
            )
        }
        scheme == "data" -> {
            saveDataUrlToDownloads(context, url, mimeType)
        }
        scheme in listOf("http", "https") -> {
            if (uri != null) {
                enqueueHttpDownload(context, uri, url, userAgent, contentDisposition, mimeType)
            } else {
                Toast.makeText(context, "Invalid download link", Toast.LENGTH_SHORT).show()
            }
        }
        else -> {
            try {
                if (uri != null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } else {
                    throw IllegalArgumentException("Invalid download URI")
                }
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "This download type is not supported in the in-app viewer",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

private fun enqueueHttpDownload(
    context: Context,
    uri: Uri,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
) {
    try {
        val request = DownloadManager.Request(uri).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
            mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            setTitle(fileName)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Downloading file...", Toast.LENGTH_SHORT).show()
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No download handler found", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Could not start download", Toast.LENGTH_SHORT).show()
    }
}

private fun saveDataUrlToDownloads(context: Context, dataUrl: String, fallbackMimeType: String?) {
    try {
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex == -1) throw IllegalArgumentException("Invalid data URL")
        val metadata = dataUrl.substring(5, commaIndex)
        val payload = dataUrl.substring(commaIndex + 1)
        val mime = metadata.substringBefore(';').ifBlank { fallbackMimeType ?: "application/octet-stream" }
        val isBase64 = metadata.contains(";base64")
        val bytes = if (isBase64) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            Uri.decode(payload).toByteArray()
        }
        val extension = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mime)
            ?.let { ".$it" }
            ?: ".bin"
        val fileName = "download-${System.currentTimeMillis()}$extension"
        writeBytesToDownloads(context, fileName, mime, bytes)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not save data download", Toast.LENGTH_SHORT).show()
    }
}

private fun triggerBlobDownload(webView: WebView, blobUrl: String, fallbackMimeType: String?) {
    val script = """
        (function() {
          try {
            fetch('$blobUrl')
              .then(function(response) { return response.blob(); })
              .then(function(blob) {
                var reader = new FileReader();
                reader.onloadend = function() {
                  window.HTMLLabsDownloader.onBlobReady(
                    reader.result || '',
                    blob.type || '${fallbackMimeType ?: ""}',
                    'download-' + Date.now()
                  );
                };
                reader.readAsDataURL(blob);
              })
              .catch(function(error) {
                window.HTMLLabsDownloader.onBlobError(String(error));
              });
          } catch (error) {
            window.HTMLLabsDownloader.onBlobError(String(error));
          }
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}

private fun writeBytesToDownloads(
    context: Context,
    fileName: String,
    mimeType: String,
    bytes: ByteArray
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            Toast.makeText(context, "Downloaded file saved", Toast.LENGTH_SHORT).show()
            return
        }
    }

    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDir.exists()) downloadsDir.mkdirs()
    val outputFile = File(downloadsDir, fileName)
    FileOutputStream(outputFile).use { it.write(bytes) }
    Toast.makeText(context, "Downloaded file saved", Toast.LENGTH_SHORT).show()
}

private class BlobDownloadBridge(private val context: Context) {
    @JavascriptInterface
    fun onBlobReady(dataUrl: String?, mimeType: String?, fileNameBase: String?) {
        Handler(Looper.getMainLooper()).post {
            val safeName = (fileNameBase ?: "download-${System.currentTimeMillis()}").trim()
            val finalName = if (safeName.contains('.')) safeName else {
                val ext = android.webkit.MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType ?: "application/octet-stream")
                    ?.let { ".$it" }
                    ?: ".bin"
                safeName + ext
            }
            try {
                val commaIndex = dataUrl?.indexOf(',') ?: -1
                if (commaIndex == -1) throw IllegalArgumentException("Invalid blob payload")
                val payload = dataUrl!!.substring(commaIndex + 1)
                val bytes = Base64.decode(payload, Base64.DEFAULT)
                writeBytesToDownloads(
                    context = context,
                    fileName = finalName,
                    mimeType = mimeType ?: "application/octet-stream",
                    bytes = bytes
                )
            } catch (_: Exception) {
                Toast.makeText(context, "Could not save blob download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun onBlobError(message: String?) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                message ?: "Could not download blob file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
