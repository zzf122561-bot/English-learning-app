package com.xuesui.englishapp.dictionary.web

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.xuesui.englishapp.dictionary.data.DictionaryFontScale
import com.xuesui.englishapp.dictionary.engine.MdictResource
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
internal fun configureSecureSettings(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        javaScriptCanOpenWindowsAutomatically = false
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        domStorageEnabled = false
        databaseEnabled = false
        setSupportMultipleWindows(false)
    }
    webView.setBackgroundColor(Color.TRANSPARENT)
}

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class SecureDictionaryWebViewClient(
    private val resourceReader: (String) -> MdictResource?,
    private val onResourceError: (String) -> Unit,
    private val onInternalLookup: (String) -> Unit,
    private val onAudioAction: (DictionaryAudioAction) -> Unit,
    private val onNavigationBlocked: (String) -> Unit,
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val path = when (val decision = DictionaryWebSecurityPolicy.intercept(url, request.isForMainFrame)) {
            InterceptDecision.AllowGeneratedMainDocument -> return null
            is InterceptDecision.ReadControlledResource -> decision.path
            InterceptDecision.Blocked -> return blockedResponse()
        }
        val resourceResult = runCatching { resourceReader(path) }
        resourceResult.exceptionOrNull()?.let { onResourceError(it.message ?: "MDD 资源读取失败") }
        val resource = resourceResult.getOrNull() ?: return notFoundResponse()
        if (resource.bytes.size > DictionaryWebSecurityPolicy.MAX_RESOURCE_BYTES) return tooLargeResponse()
        return WebResourceResponse(
            resource.mediaType ?: "application/octet-stream",
            null,
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "Content-Security-Policy" to DICTIONARY_CONTENT_SECURITY_POLICY,
                "X-Content-Type-Options" to "nosniff",
            ),
            ByteArrayInputStream(resource.bytes),
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return dispatchNavigation(DictionaryWebSecurityPolicy.navigation(url), url)
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return true
        return dispatchNavigation(DictionaryWebSecurityPolicy.navigation(url), url)
    }

    private fun dispatchNavigation(decision: NavigationDecision, url: String): Boolean =
        dispatchDictionaryNavigation(decision, onInternalLookup, onAudioAction) { onNavigationBlocked(url) }

    private fun blockedResponse() = response(403, "Blocked")
    private fun notFoundResponse() = response(404, "Not Found")
    private fun tooLargeResponse() = response(413, "Resource Too Large")

    private fun response(status: Int, reason: String) = WebResourceResponse(
        "text/plain",
        "UTF-8",
        status,
        reason,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )
}

@Composable
internal fun SecureDictionaryWebView(
    html: String,
    modifier: Modifier = Modifier,
    resourceReader: (String) -> MdictResource?,
    onResourceError: (String) -> Unit,
    onInternalLookup: (String) -> Unit,
    onAudioAction: (DictionaryAudioAction) -> Unit,
    onNavigationBlocked: (String) -> Unit,
    fontLevel: Int,
) {
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            configureSecureSettings(this)
            webViewClient = SecureDictionaryWebViewClient(
                resourceReader = resourceReader,
                onResourceError = onResourceError,
                onInternalLookup = onInternalLookup,
                onAudioAction = onAudioAction,
                onNavigationBlocked = onNavigationBlocked,
            )
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }
    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { view ->
            val document = secureHtmlDocument(html)
            val zoom = DictionaryFontScale.textZoom(fontLevel)
            view.settings.textZoom = zoom
            val renderKey = document.hashCode() to zoom
            if (view.tag != renderKey) {
                view.tag = renderKey
                view.loadDataWithBaseURL(
                    DictionaryWebSecurityPolicy.BASE_URL,
                    document,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

internal fun dispatchDictionaryNavigation(
    decision: NavigationDecision,
    onInternalLookup: (String) -> Unit,
    onAudioAction: (DictionaryAudioAction) -> Unit,
    onBlocked: () -> Unit,
): Boolean = when (decision) {
    is NavigationDecision.SameDocumentAnchor -> false
    NavigationDecision.AllowControlledInternal -> false
    NavigationDecision.AllowInlineScript -> false
    is NavigationDecision.InternalLookup -> true.also { onInternalLookup(decision.query) }
    is NavigationDecision.PlayAudio -> true.also { onAudioAction(decision.action) }
    NavigationDecision.Blocked -> true.also { onBlocked() }
}

internal fun secureHtmlDocument(body: String): String = """
    <!doctype html><html><head>
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="$DICTIONARY_CONTENT_SECURITY_POLICY">
    <style>body{margin:14px;color:#14213D;background:#F4F7FB;font-family:sans-serif;line-height:1.55}a{color:#2F5D8C}</style>
    </head><body>$body</body></html>
""".trimIndent()

internal const val DICTIONARY_CONTENT_SECURITY_POLICY =
    "default-src 'none'; img-src https://dictionary.local; style-src https://dictionary.local 'unsafe-inline'; " +
        "script-src https://dictionary.local 'unsafe-inline'; media-src https://dictionary.local; " +
        "font-src https://dictionary.local"
