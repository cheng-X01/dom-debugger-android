package com.domdebugger;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * DOM Debugger Android - WebView + OkHttp, no CORS
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DOMDebugger";

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ProxyService proxyService;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        proxyService = new ProxyService();

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                try {
                    if (url.contains("/api/proxy")) {
                        return handleProxyRequest(request);
                    }
                    if (url.contains("/api/status")) {
                        return handleStatusRequest();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "shouldInterceptRequest error", e);
                }
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/dom-debugger-android.html");
    }

    private WebResourceResponse handleProxyRequest(WebResourceRequest request) {
        String targetUrl = request.getUrl().getQueryParameter("url");
        if (targetUrl == null || targetUrl.isEmpty()) {
            return jsonResp(400, errorJson("missing url param"));
        }
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            return jsonResp(400, errorJson("url must start with http:// or https://"));
        }

        Log.d(TAG, "Proxying: " + targetUrl);

        try {
            ProxyService.ProxyResult r = proxyService.fetch(targetUrl);
            if (r.ok) {
                JSONObject json = new JSONObject();
                json.put("ok", true);
                json.put("url", r.url);
                json.put("contentType", r.contentType);
                json.put("content", r.content);
                json.put("length", r.length);
                Log.d(TAG, "Proxy success: " + r.length + " chars");
                return jsonResp(200, json.toString());
            } else {
                Log.e(TAG, "Proxy failed: " + r.error);
                return jsonResp(502, errorJson(r.error));
            }
        } catch (Exception e) {
            Log.e(TAG, "Proxy exception", e);
            return jsonResp(500, errorJson(e.getMessage()));
        }
    }

    private WebResourceResponse handleStatusRequest() {
        try {
            JSONObject json = new JSONObject();
            json.put("status", "ok");
            json.put("message", "Android native proxy active");
            return jsonResp(200, json.toString());
        } catch (Exception e) {
            return jsonResp(200, "{\"status\":\"ok\"}");
        }
    }

    private String errorJson(String msg) {
        try {
            JSONObject json = new JSONObject();
            json.put("ok", false);
            json.put("error", msg != null ? msg : "unknown error");
            return json.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }

    private WebResourceResponse jsonResp(int code, String json) {
        return new WebResourceResponse(
            "application/json",
            "utf-8",
            code,
            code == 200 ? "OK" : "Error",
            null,
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}