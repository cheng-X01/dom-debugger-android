package com.domdebugger;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * DOM Debugger Android - WebView + OkHttp, no CORS
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ProxyService proxyService;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏沉浸式
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
                if (url.contains("/api/proxy")) {
                    return handleProxyRequest(request);
                }
                if (url.contains("/api/status")) {
                    return handleStatusRequest();
                }
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/dom-debugger-android.html");
    }

    private WebResourceResponse handleProxyRequest(WebResourceRequest request) {
        String targetUrl = request.getUrl().getQueryParameter("url");
        if (targetUrl == null || targetUrl.isEmpty()) {
            return jsonResp(400, "{\"ok\":false,\"error\":\"missing url param\"}");
        }
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            return jsonResp(400, "{\"ok\":false,\"error\":\"url must start with http/https\"}");
        }
        try {
            ProxyService.ProxyResult r = proxyService.fetch(targetUrl);
            if (r.ok) {
                String json = "{\"ok\":true,\"url\":\"" + esc(r.url) + "\",\"contentType\":\"" + esc(r.contentType) + "\",\"content\":\"" + esc(r.content) + "\",\"length\":" + r.length + "}";
                return jsonResp(200, json);
            } else {
                return jsonResp(502, "{\"ok\":false,\"error\":\"" + esc(r.error) + "\"}");
            }
        } catch (Exception e) {
            return jsonResp(500, "{\"ok\":false,\"error\":\"" + esc(e.getMessage()) + "\"}");
        }
    }

    private WebResourceResponse handleStatusRequest() {
        return jsonResp(200, "{\"status\":\"ok\",\"message\":\"Android native proxy active\"}");
    }

    private WebResourceResponse jsonResp(int code, String json) {
        return new WebResourceResponse("application/json", "utf-8", code, "OK", null, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
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