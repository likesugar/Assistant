package com.ziyuan.Assistant;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

/** 通用网页登录：全局 CookieManager（与资源嗅探共享），登录完成即生效 */
public class LoginWebActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Immersive.hide(this);
        setContentView(R.layout.activity_login_web);
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        findViewById(R.id.webRoot).setBackgroundColor(dark ? 0xFF11151D : 0xFFEEF4FF);
        TextView back = findViewById(R.id.webBack);
        TextView done = findViewById(R.id.webDone);
        int accent = dark ? 0xFFB4C5FF : 0xFF315CDE;
        back.setTextColor(accent); done.setTextColor(accent);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(
                    dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                CookieManager.getInstance().flush();
                finish();
            }
        });

        web = findViewById(R.id.webView);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        final String startUrl = getIntent().getStringExtra("url");
        final boolean douyin = "https://www.douyin.com/jingxuan".equals(startUrl);
        final boolean bili = startUrl != null && startUrl.contains("bilibili.com");
        final boolean xhs = startUrl != null && startUrl.contains("xiaohongshu.com");
        final String desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36";
        if (xhs || douyin) {
            // 电脑版 UA（默认 UA 首个括号改 X11; Linux x86_64，去掉 Version/Mobile）
            String ua = android.webkit.WebSettings.getDefaultUserAgent(this)
                    .replaceFirst("\\([^)]*\\)", "(X11; Linux x86_64)")
                    .replace(" Version/4.0", "").replace(" Mobile", "");
            ws.setUserAgentString(ua);
        } else if (!bili) {
            ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13; M2102K1C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }
        final boolean[] desktopFallback = {false};
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) return false;  // 页内自行处理
                try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))); } catch (Throwable ignored) {}
                return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                CookieHosts.add(getApplicationContext(), url);
                // 抖音：手机版新会话常被甩到无登录入口的 /home，自动切电脑版重载（DBdown 同款兜底）
                if (douyin && !desktopFallback[0] && url.startsWith("https://www.douyin.com/home")) {
                    desktopFallback[0] = true;
                    view.getSettings().setUserAgentString(desktopUa);
                    view.loadUrl("https://www.douyin.com/jingxuan");
                    Toast.makeText(LoginWebActivity.this, "手机版未提供登录入口，已切换电脑版", Toast.LENGTH_SHORT).show();
                }
                super.onPageFinished(view, url);
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.loadUrl(getIntent().getStringExtra("url"));
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else { CookieManager.getInstance().flush(); super.onBackPressed(); }
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
