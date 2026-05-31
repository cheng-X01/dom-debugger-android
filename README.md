# DOM Debugger Android

Android 原生版本的 DOM 调试工具，**无需代理，无 CORS 限制**。

## 特性

- 🚀 **无 CORS 限制** — 使用 OkHttp 直接请求网页，无需任何代理
- 📱 **原生体验** — WebView + 原生请求拦截，流畅稳定
- 🔍 **完整功能** — DOM 树查看、元素检查、样式编辑、HTML 编辑

## 构建 APK

### GitHub Actions 自动构建

推送代码后，GitHub Actions 会自动构建 APK（约 2-3 分钟）。

1. 进入 **Actions** 标签页
2. 点击最新的成功构建
3. 在 **Artifacts** 区域下载 `dom-debugger-debug`

### 本地构建

```bash
# 需要 JDK 17
./gradlew assembleDebug
# APK 位于 app/build/outputs/apk/debug/
```

## 安装使用

1. 安装 APK
2. 输入网址（如 `example.com`）
3. 点击「加载」— 无需代理，直接加载

## 技术原理

- **WebViewAssetLoader** — 加载本地 HTML
- **shouldInterceptRequest** — 拦截 `/api/proxy` 请求
- **OkHttp** — 原生 HTTP 客户端，无 CORS 限制