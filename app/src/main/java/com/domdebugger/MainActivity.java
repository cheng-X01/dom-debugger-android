package com.domdebugger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * DOM Debugger Android 版本
 * 使用 WebView 加载本地 HTML，通过 OkHttp 拦截请求绕过 CORS
 */
public class MainActivity extends Activity {

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ProxyService proxyService;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化代理服务
        proxyService = new ProxyService();

        // 初始化 AssetLoader（用于加载本地 HTML）
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        // 创建 WebView
        webView = new WebView(this);
        setContentView(webView);

        // 配置 WebView
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

        // 设置 WebViewClient 拦截请求
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // 拦截 /api/proxy 请求
                if (url.contains("/api/proxy")) {
                    return handleProxyRequest(request);
                }

                // 拦截 /api/status 请求
                if (url.contains("/api/status")) {
                    return handleStatusRequest();
                }

                // 其他请求交给 AssetLoader 处理
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        // 加载本地 HTML
        webView.loadUrl("https://appassets.androidplatform.net/assets/dom-debugger-android.html");
    }

    /**
     * 处理代理请求
     */
    private WebResourceResponse handleProxyRequest(WebResourceRequest request) {
        String targetUrl = request.getUrl().getQueryParameter("url");
        if (targetUrl == null || targetUrl.isEmpty()) {
            return createJsonResponse(400, "{\"ok\":false,\"error\":\"缺少 url 参数\"}");
        }

        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            return createJsonResponse(400, "{\"ok\":false,\"error\":\"url 必须以 http:// 或 https:// 开头\"}");
        }

        try {
            ProxyService.ProxyResult result = proxyService.fetch(targetUrl);
            if (result.ok) {
                String json = "{\"ok\":true,\"url\":\"" + escapeJson(result.url) + "\",\"contentType\":\"" + escapeJson(result.contentType) + "\",\"content\":\"" + escapeJson(result.content) + "\",\"length\":" + result.length + "}";
                return createJsonResponse(200, json);
            } else {
                return createJsonResponse(502, "{\"ok\":false,\"error\":\"" + escapeJson(result.error) + "\"}");
            }
        } catch (Exception e) {
            return createJsonResponse(500, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * 处理状态检查请求
     */
    private WebResourceResponse handleStatusRequest() {
        return createJsonResponse(200, "{\"status\":\"ok\",\"message\":\"Android 原生代理已启用（无需 CORS）\"}");
    }

    /**
     * 创建 JSON 响应
     */
    private WebResourceResponse createJsonResponse(int statusCode, String json) {
        ByteArrayInputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return new WebResourceResponse(
                "application/json",
                "utf-8",
                statusCode,
                "OK",
                null,
                stream
        );
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}