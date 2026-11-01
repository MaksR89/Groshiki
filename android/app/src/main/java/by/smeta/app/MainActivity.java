package by.smeta.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQUEST_FILE = 41;
    private static final int REQUEST_SAVE = 42;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingText;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF4EFE6);
        root.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            if (Build.VERSION.SDK_INT >= 30) return WindowInsets.CONSUMED;
            return insets.consumeSystemWindowInsets();
        });
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        setContentView(root);
        root.requestApplyInsets();
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new FileBridge(), "SmetaFile");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQUEST_FILE);
                } catch (ActivityNotFoundException error) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Нечем открыть файл", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!HOST.equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || path.isEmpty() || path.equals("/") || path.equals("/index.html")) path = "/index.html";
                if (path.startsWith("/")) path = path.substring(1);
                String mime = URLConnection.guessContentTypeFromName(path);
                if (path.endsWith(".js")) mime = "text/javascript";
                else if (path.endsWith(".css")) mime = "text/css";
                else if (path.endsWith(".html")) mime = "text/html";
                else if (path.endsWith(".svg")) mime = "image/svg+xml";
                else if (path.endsWith(".woff2")) mime = "font/woff2";
                else if (mime == null) mime = "application/octet-stream";
                String encoding = path.endsWith(".woff2") || path.endsWith(".png") ? null : "utf-8";
                try {
                    InputStream stream = getAssets().open(path);
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Cache-Control", "no-cache");
                    return new WebResourceResponse(mime, encoding, 200, "OK", headers, stream);
                } catch (IOException error) {
                    return null;
                }
            }
        });
        webView.loadUrl("https://appassets.androidplatform.net/");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE) {
            Uri[] uris = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            if (fileCallback != null) fileCallback.onReceiveValue(uris);
            fileCallback = null;
            return;
        }
        if (requestCode == REQUEST_SAVE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingText != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IOException("no stream");
                    out.write(pendingText.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Копия сохранена", Toast.LENGTH_SHORT).show();
                } catch (IOException error) {
                    Toast.makeText(this, "Не записалось", Toast.LENGTH_SHORT).show();
                }
            }
            pendingText = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private final class FileBridge {
        @JavascriptInterface
        public void save(String name, String mime, String text) {
            runOnUiThread(() -> {
                pendingText = text == null ? "" : text;
                String type = mime == null ? "application/octet-stream" : mime.split(";", 2)[0];
                if (type.isEmpty()) type = "application/octet-stream";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(type);
                intent.putExtra(Intent.EXTRA_TITLE, name == null ? "smeta.txt" : name);
                try {
                    startActivityForResult(intent, REQUEST_SAVE);
                } catch (ActivityNotFoundException error) {
                    pendingText = null;
                    Toast.makeText(MainActivity.this, "Некуда сохранить", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
