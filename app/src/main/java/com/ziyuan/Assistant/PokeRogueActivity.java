package com.ziyuan.Assistant;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 宝可梦肉鸽: 独立双页容器。
 * 「首页」= https://pokebonk.com/ (游戏)  「攻略」= https://pokebonk-moves.app.workbuddy.host/
 * 两个独立 WebView 叠放保活, 底部胶囊两页签只切可见性 —— 切走再切回不重载, 游戏进度/攻略滚动位置保留。
 * 与首页其它卡片完全独立, 不复用娱乐/网页转应用等逻辑。
 */
public class PokeRogueActivity extends Activity {

    private static final String URL_HOME  = "https://pokebonk.com/";
    private static final String URL_GUIDE = "https://pokebonk-moves.app.workbuddy.host/";

    private WebView webHome, webGuide;
    private TextView tabHome, tabGuide;
    private boolean guideLoaded = false;
    private int current = 0;              // 0=首页 1=攻略
    private boolean dark = true;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? 0xFF000000 : 0xFFFFFFFF);

        // 两个独立 WebView 全屏叠放, 切页签只切可见性, 互不重载
        webHome  = makeWeb();
        webGuide = makeWeb();
        root.addView(webHome, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webGuide, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webGuide.setVisibility(View.GONE);

        // 底部悬浮胶囊: 首页 / 攻略
        FrameLayout capsule = new FrameLayout(this);
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ dark ? 0x1FFFFFFF : 0x73FFFFFF, dark ? 0x0AFFFFFF : 0x59EFF5FF });
        cbg.setCornerRadius(dp(22));
        cbg.setStroke(dp(1), dark ? 0x40FFFFFF : 0x66FFFFFF);
        capsule.setBackground(cbg);
        capsule.setElevation(dp(18));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        tabHome = makeTab("首页");
        tabGuide = makeTab("攻略");
        tabHome.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectTab(0); }
        });
        tabGuide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectTab(1); }
        });
        row.addView(tabHome);
        row.addView(tabGuide);
        capsule.addView(row);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = dp(16);
        root.addView(capsule, clp);

        setContentView(root);
        webHome.loadUrl(URL_HOME);
        selectTab(0);
    }

    private WebView makeWeb() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h,
                                                     android.net.http.SslError e) { h.proceed(); }
        });
        try { CookieManager.getInstance().setAcceptThirdPartyCookies(w, true); } catch (Throwable ignored) {}
        return w;
    }

    private TextView makeTab(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24), dp(9), dp(24), dp(9));
        return t;
    }

    private void selectTab(int t) {
        current = t;
        webHome.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        webGuide.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        if (t == 1 && !guideLoaded) { guideLoaded = true; webGuide.loadUrl(URL_GUIDE); } // 攻略懒加载一次
        styleTab(tabHome, t == 0);
        styleTab(tabGuide, t == 1);
    }

    private void styleTab(TextView t, boolean on) {
        if (on) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(16));
            g.setColor(dark ? 0xFF3D6DFF : 0xFF1677FF);
            t.setBackground(g);
            t.setTextColor(0xFFFFFFFF);
        } else {
            t.setBackground(null);
            t.setTextColor(dark ? 0xFF8A94A6 : 0xFF7C8694);
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            WebView w = current == 0 ? webHome : webGuide;
            if (w != null && w.canGoBack()) { w.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override protected void onDestroy() {
        try { if (webHome != null) webHome.destroy(); } catch (Throwable ignored) {}
        try { if (webGuide != null) webGuide.destroy(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
