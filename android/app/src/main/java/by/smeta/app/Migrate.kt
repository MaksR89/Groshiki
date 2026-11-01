package by.smeta.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@SuppressLint("SetJavaScriptEnabled")
suspend fun pullOldBook(context: Context): String = suspendCancellableCoroutine { cont ->
    val web = WebView(context)
    val done = AtomicBoolean(false)
    fun finish(value: String) {
        if (!done.compareAndSet(false, true)) return
        Handler(Looper.getMainLooper()).post {
            web.destroy()
            if (cont.isActive) cont.resume(value)
        }
    }
    val loader = WebViewAssetLoader.Builder()
        .setDomain("appassets.androidplatform.net")
        .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()
    web.settings.javaScriptEnabled = true
    web.addJavascriptInterface(object {
        @JavascriptInterface fun onBook(raw: String?) = finish(raw.orEmpty())
    }, "GroshikiMigrate")
    web.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return loader.shouldInterceptRequest(request.url)
        }
    }
    Handler(Looper.getMainLooper()).postDelayed({ finish("") }, 2500)
    web.loadUrl("https://appassets.androidplatform.net/migrate.html")
    cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { web.destroy() } }
}
