package com.blw.documentreader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.PathHandler
import com.blw.documentreader.data.DocumentTypes

object OfficeWebView {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, uri: Uri, type: String): WebView {
        val webView = WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }
        val loader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/document/", PathHandler { relative ->
                if (relative != "current") null else {
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                        if (input == null) null
                        else WebResourceResponse("application/octet-stream", null, input)
                    } catch (_: Exception) {
                        // Missing URI grant is displayed by the document renderer.
                        null
                    }
                }
            })
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val requestUri = request?.url ?: return null
                if (requestUri.host != "appassets.androidplatform.net") {
                    // Do not make network requests from document content.
                    return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return loader.shouldInterceptRequest(requestUri)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return request?.url?.host != "appassets.androidplatform.net"
            }
        }
        webView.loadUrl(
            "https://appassets.androidplatform.net/assets/office/reader.html?type=" +
                if (type == DocumentTypes.WORD) "word" else "excel"
        )
        return webView
    }
}
