package com.hkm.htmllabs.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import com.hkm.htmllabs.R
import com.hkm.htmllabs.data.HtmlCard
import com.hkm.htmllabs.ui.theme.AppMotion
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
    // Defer WebView instantiation until the Hero Morph animation settles so opening is 120Hz silky smooth
    var isWebViewMounted by remember(card.id) { mutableStateOf(false) }

    LaunchedEffect(card.id) {
        kotlinx.coroutines.delay(260L)
        isWebViewMounted = true
    }

    // Mini-browser interactive states (permissions, file upload, JS dialogs, custom video fullscreen)
    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGeolocationOrigin by remember { mutableStateOf<String?>(null) }
    var pendingGeolocationCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var jsAlertState by remember { mutableStateOf<Pair<String, JsResult>?>(null) }
    var jsConfirmState by remember { mutableStateOf<Pair<String, JsResult>?>(null) }
    var jsPromptState by remember { mutableStateOf<Triple<String, String, JsPromptResult>?>(null) }
    var jsPromptInput by remember { mutableStateOf("") }

    val activity = context.findActivity()

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val req = pendingPermissionRequest
        if (req != null) {
            val allGranted = results.values.all { it }
            if (allGranted) {
                req.grant(req.resources)
            } else {
                req.deny()
            }
            pendingPermissionRequest = null
        }
        val geoOrigin = pendingGeolocationOrigin
        val geoCallback = pendingGeolocationCallback
        if (geoOrigin != null && geoCallback != null) {
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            geoCallback.invoke(geoOrigin, granted, false)
            pendingGeolocationOrigin = null
            pendingGeolocationCallback = null
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            ?: result.data?.clipData?.let { clip ->
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            }
        callback.onReceiveValue(uris)
    }

    val assetLoader = remember(context) {
        val labsRoot = File(context.filesDir, "htmllabs").apply { if (!exists()) mkdirs() }
        WebViewAssetLoader.Builder()
            .addPathHandler("/labs/", WebViewAssetLoader.InternalStoragePathHandler(context, labsRoot))
            .build()
    }

    LaunchedEffect(card.id, card.htmlContent) {
        withContext(Dispatchers.IO) {
            val cardDir = File(context.filesDir, "htmllabs/${card.id}")
            if (!cardDir.exists()) cardDir.mkdirs()
            val htmlFile = File(cardDir, "index.html")
            htmlFile.writeText(card.htmlContent)
            // Serve over HTTPS appassets origin so WebRTC/getUserMedia, MediaRecorder,
            // AudioWorklet, ES modules, and crypto.subtle have a Secure Context.
            localPageUrl = "https://appassets.androidplatform.net/labs/${card.id}/index.html"
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

    BackHandler(enabled = isFullscreen || canGoBack || customVideoView != null) {
        val webView = webViewRef.value
        when {
            customVideoView != null -> {
                customViewCallback?.onCustomViewHidden()
                customVideoView = null
                customViewCallback = null
            }
            isFullscreen -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                isFullscreen = false
            }
            webView != null && webView.canGoBack() -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                webView.goBack()
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

            if (isWebViewMounted) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isFullscreen) Modifier else Modifier.navigationBarsPadding())
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
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            @Suppress("DEPRECATION")
                            allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            allowUniversalAccessFromFileURLs = true
                            setGeolocationEnabled(true)
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
                            setSupportMultipleWindows(true)
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

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                if (request == null) return
                                val neededPermissions = mutableListOf<String>()
                                request.resources.forEach { res ->
                                    when (res) {
                                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                            if (ContextCompat.checkSelfPermission(
                                                    ctx,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                neededPermissions.add(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                            if (ContextCompat.checkSelfPermission(
                                                    ctx,
                                                    Manifest.permission.CAMERA
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                neededPermissions.add(Manifest.permission.CAMERA)
                                            }
                                        }
                                    }
                                }
                                if (neededPermissions.isEmpty()) {
                                    request.grant(request.resources)
                                } else {
                                    pendingPermissionRequest = request
                                    mediaPermissionLauncher.launch(neededPermissions.toTypedArray())
                                }
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                if (origin == null || callback == null) return
                                val hasFine = ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasFine || hasCoarse) {
                                    callback.invoke(origin, true, false)
                                } else {
                                    pendingGeolocationOrigin = origin
                                    pendingGeolocationCallback = callback
                                    mediaPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallbackArg: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = filePathCallbackArg
                                val intent = runCatching {
                                    fileChooserParams?.createIntent()
                                }.getOrNull() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                }
                                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                return try {
                                    fileChooserLauncher.launch(intent)
                                    true
                                } catch (_: Exception) {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = null
                                    Toast.makeText(ctx, "No file picker available", Toast.LENGTH_SHORT).show()
                                    false
                                }
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                val tempWebView = WebView(ctx).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            req: WebResourceRequest?
                                        ): Boolean {
                                            val targetUrl = req?.url?.toString()
                                            if (!targetUrl.isNullOrBlank()) {
                                                view?.loadUrl(targetUrl)
                                            }
                                            destroy()
                                            return true
                                        }
                                    }
                                }
                                transport.webView = tempWebView
                                resultMsg.sendToTarget()
                                return true
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customVideoView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customVideoView = view
                                customViewCallback = callback
                                isFullscreen = true
                            }

                            override fun onHideCustomView() {
                                customVideoView = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                                isFullscreen = false
                            }

                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result == null) return false
                                jsAlertState = (message ?: "") to result
                                return true
                            }

                            override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (result == null) return false
                                jsConfirmState = (message ?: "") to result
                                return true
                            }

                            override fun onJsPrompt(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                defaultValue: String?,
                                result: JsPromptResult?
                            ): Boolean {
                                if (result == null) return false
                                jsPromptInput = defaultValue ?: ""
                                jsPromptState = Triple(message ?: "", defaultValue ?: "", result)
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url ?: return null
                                return assetLoader.shouldInterceptRequest(reqUrl)
                            }

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

        customVideoView?.let { videoView ->
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        (videoView.parent as? ViewGroup)?.removeView(videoView)
                        addView(videoView)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
            )
        }

        AnimatedVisibility(
            visible = isFullscreen && customVideoView == null,
            enter = AppMotion.floatingBarEnter,
            exit = AppMotion.floatingBarExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier.background(
                    colorScheme.surface.copy(alpha = 0.92f),
                    RoundedCornerShape(16.dp)
                )
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

        jsAlertState?.let { (message, result) ->
            AlertDialog(
                onDismissRequest = {
                    result.cancel()
                    jsAlertState = null
                },
                title = { Text(pageTitle, color = colorScheme.onSurface) },
                text = { Text(message, color = colorScheme.onSurface.copy(alpha = 0.85f)) },
                confirmButton = {
                    Button(
                        onClick = {
                            result.confirm()
                            jsAlertState = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) { Text("OK") }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = colorScheme.surface
            )
        }

        jsConfirmState?.let { (message, result) ->
            AlertDialog(
                onDismissRequest = {
                    result.cancel()
                    jsConfirmState = null
                },
                title = { Text(pageTitle, color = colorScheme.onSurface) },
                text = { Text(message, color = colorScheme.onSurface.copy(alpha = 0.85f)) },
                confirmButton = {
                    Button(
                        onClick = {
                            result.confirm()
                            jsConfirmState = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) { Text("OK") }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            result.cancel()
                            jsConfirmState = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondary,
                            contentColor = colorScheme.onSecondary
                        )
                    ) { Text("Cancel") }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = colorScheme.surface
            )
        }

        jsPromptState?.let { (message, _, result) ->
            AlertDialog(
                onDismissRequest = {
                    result.cancel()
                    jsPromptState = null
                },
                title = { Text(pageTitle, color = colorScheme.onSurface) },
                text = {
                    Column {
                        if (message.isNotBlank()) {
                            Text(
                                text = message,
                                color = colorScheme.onSurface.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = jsPromptInput,
                            onValueChange = { jsPromptInput = it },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            result.confirm(jsPromptInput)
                            jsPromptState = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) { Text("OK") }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            result.cancel()
                            jsPromptState = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.secondary,
                            contentColor = colorScheme.onSecondary
                        )
                    ) { Text("Cancel") }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = colorScheme.surface
            )
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
