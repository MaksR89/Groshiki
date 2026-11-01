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

private const val READ_BOOK = """
(function() {
  var ls = "";
  try { ls = localStorage.getItem("smeta-ledger-v1") || ""; } catch (e) {}
  if (ls) { GroshikiMigrate.onBook(ls); return; }
  try {
    var req = indexedDB.open("smeta");
    req.onsuccess = function() {
      try {
        var tx = req.result.transaction("book", "readonly");
        var get = tx.objectStore("book").get("ledger");
        get.onsuccess = function() {
          var row = get.result;
          GroshikiMigrate.onBook(row ? JSON.stringify({state: row}) : "");
        };
        get.onerror = function() { GroshikiMigrate.onBook(""); };
      } catch (e) { GroshikiMigrate.onBook(""); }
    };
    req.onerror = function() { GroshikiMigrate.onBook(""); };
  } catch (e) { GroshikiMigrate.onBook(""); }
})();
"""

@SuppressLint("SetJavaScriptEnabled")
suspend fun pullOldBook(context: Context): String = suspendCancellableCoroutine { cont ->
    val done = AtomicBoolean(false)
    val handler = Handler(Looper.getMainLooper())
    handler.post {
        val web = WebView(context)
        fun finish(value: String) {
            if (!done.compareAndSet(false, true)) return
            web.destroy()
            if (cont.isActive) cont.resume(value)
        }
        val loader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(object {
            @JavascriptInterface fun onBook(raw: String?) = handler.post { finish(raw.orEmpty()) }
        }, "GroshikiMigrate")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(READ_BOOK, null)
            }
        }
        handler.postDelayed({ finish("") }, 8000)
        web.loadUrl("https://appassets.androidplatform.net/migrate.html")
        cont.invokeOnCancellation { handler.post { if (!done.get()) web.destroy() } }
    }
}
