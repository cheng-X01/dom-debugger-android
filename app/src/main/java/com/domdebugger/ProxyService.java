package com.domdebugger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 代理服务：使用 OkHttp 加载网页内容
 * Android 原生应用不受 CORS 限制，可以直接请求任意网页
 */
public class ProxyService {

    private final OkHttpClient client;

    public ProxyService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /**
     * 加载指定 URL 的内容
     */
    public ProxyResult fetch(String url) {
        ProxyResult result = new ProxyResult();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                byte[] bytes = response.body().bytes();
                String contentType = response.header("Content-Type", "text/html; charset=utf-8");

                // 尝试解码为文本
                String text = decodeContent(bytes, contentType);

                result.ok = true;
                result.url = response.request().url().toString(); // 最终 URL（跟随重定向后）
                result.contentType = contentType;
                result.content = text;
                result.length = text.length();
            } else {
                result.ok = false;
                result.error = "HTTP " + response.code() + ": " + response.message();
            }
        } catch (IOException e) {
            result.ok = false;
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Unable to resolve host") || e.getMessage().contains("No address associated with hostname")) {
                    result.error = "DNS 解析失败: 无法解析域名「" + extractHostname(url) + "」，请检查网址是否正确";
                } else if (e.getMessage().contains("timeout") || e.getMessage().contains("Timed out")) {
                    result.error = "连接超时: 目标服务器「" + extractHostname(url) + "」无响应";
                } else {
                    result.error = "网络错误: " + e.getMessage();
                }
            } else {
                result.error = "网络错误";
            }
        }

        return result;
    }

    /**
     * 解码内容（尝试多种编码）
     */
    private String decodeContent(byte[] bytes, String contentType) {
        // 从 Content-Type 提取编码
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            String[] parts = contentType.split("charset=");
            if (parts.length > 1) {
                String charsetName = parts[1].split(";")[0].trim();
                try {
                    charset = Charset.forName(charsetName);
                } catch (Exception ignored) {
                }
            }
        }

        // 尝试解码
        try {
            return new String(bytes, charset);
        } catch (Exception e1) {
            try {
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e2) {
                try {
                    return new String(bytes, Charset.forName("GBK"));
                } catch (Exception e3) {
                    return new String(bytes, StandardCharsets.ISO_8859_1);
                }
            }
        }
    }

    /**
     * 从 URL 提取主机名
     */
    private String extractHostname(String url) {
        try {
            int start = url.indexOf("://") + 3;
            int end = url.indexOf("/", start);
            if (end == -1) end = url.length();
            String host = url.substring(start, end);
            // 移除端口
            int portIndex = host.indexOf(":");
            if (portIndex > 0) host = host.substring(0, portIndex);
            return host;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 代理结果
     */
    public static class ProxyResult {
        public boolean ok;
        public String url;
        public String contentType;
        public String content;
        public int length;
        public String error;
    }
}