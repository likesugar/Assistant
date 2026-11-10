package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * 宝可梦肉鸽(PokéRogue): 页面打开即 https://pokerogue.net/
 * 独立全屏 WebView, 与 PokeRogueActivity(幸存者 pokebonk)、PokeCatchActivity(Poke'Catch)完全分开。
 * 纯标准移动 UA 打开网页游戏, 不注入任何站点专用 CSS/UA 标记。
 * 沉浸式: 隐藏状态栏 + 导航栏, 带 DecorView 挂载守卫(同 PokeCatchActivity)。
 */
public class PokeRogueWebActivity extends Activity {

    private static final String URL_HOME = "https://pokerogue.net/";
    private WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B12);   // 与站点深色背景一致, 避免加载瞬间白闪
        web = makeWeb();
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        web.loadUrl(URL_HOME);
        applyImmersive();
    }

    private WebView makeWeb() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);       // 存档/语言用 localStorage
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);   // 游戏音效
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 标准移动 UA
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h,
                                                     android.net.http.SslError e) { h.proceed(); }
        });
        // 联机/存档走 Supabase, 跨域需第三方 Cookie
        try { CookieManager.getInstance().setAcceptThirdPartyCookies(w, true); } catch (Throwable ignored) {}
        return w;
    }

    /** 隐藏状态栏 + 导航栏(沉浸式 sticky)。 */
    private void applyImmersive() {
        android.view.Window win = getWindow();
        if (win == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            win.setDecorFitsSystemWindows(false);
            if (win.getDecorView() != null && win.getDecorView().isAttachedToWindow()) {
                android.view.WindowInsetsController c = win.getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } else {
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyImmersive();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersive();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack(); return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override protected void onDestroy() {
        try { if (web != null) web.destroy(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
