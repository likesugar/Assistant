package com.ziyuan.Assistant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/** B站登录页：登录成功（出现 SESSDATA）后自动把 cookies 存本地并关闭 */
public class BiliLoginActivity extends Activity {

    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean saved = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Immersive.hide(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView tip = new TextView(this);
        tip.setText("登录B站后自动返回（用于获取最高画质）");
        tip.setTextColor(0xFF1F2329);
        tip.setTextSize(13);
        tip.setPadding(24, 32, 24, 16);
        root.addView(tip);

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return false;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        webView.loadUrl("https://m.bilibili.com/login-entry?gourl=https%3A%2F%2Fm.bilibili.com%2F");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndFinish();
                if (!isFinishing()) handler.postDelayed(this, 800);
            }
        }, 1200);
    }

    void checkAndFinish() {
        String cookie = CookieManager.getInstance().getCookie("https://bilibili.com");
        if (cookie != null && cookie.contains("SESSDATA=") && !saved) {
            saved = true;
            SharedPreferences sp = getSharedPreferences("bili", MODE_PRIVATE);
            sp.edit().putString("cookies", cookie).apply();
            Toast2.show(this, "B站登录成功，已保存凭证");
            finish();
        }
    }

    static class Toast2 {
        static void show(Activity a, String m) {
            android.widget.Toast.makeText(a, m, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }
}
