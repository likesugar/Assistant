package com.ziyuan.Assistant;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.Configuration;
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
 * 胶囊尺寸/样式与首页 CapsuleBottomBar 对齐(去阴影); 横屏隐藏胶囊。
 * 与首页其它卡片完全独立, 不复用娱乐/网页转应用等逻辑。
 */
public class PokeRogueActivity extends Activity {

    private static final String URL_HOME  = "https://pokebonk.com/";
    private static final String URL_GUIDE = "https://pokebonk-moves.app.workbuddy.host/";

    /** 横屏时游戏触摸按键放大系数(对齐官方 App 手感), 竖屏 1.0 原样。 */
    private static final double BTN_SCALE_LANDSCAPE = 1.3;
    /** 独立 CSS 变量 --pbk 叠加放大: 摇杆用 --tcs(游戏内 touchScale 设置) × --pbk, 二者都保留;
     *  固定 px 动作键(game 内不随 --tcs 变化)用 transform:scale(--pbk) 放大。
     *  !important 保证覆盖游戏外链样式(与加载顺序无关); 游戏后续动态创建控件自动套用。
     *  动作键原本无 transform; .tc-btn.on 是按下态 scale(.88), 一并乘上 --pbk 避免按下时缩回原尺寸。 */
    private static final String BTN_CSS =
        ".tc-stick{width:calc(128px*var(--tcs,1)*var(--pbk,1))!important;"
      + "height:calc(128px*var(--tcs,1)*var(--pbk,1))!important;"
      + "margin:calc(-64px*var(--tcs,1)*var(--pbk,1)) 0 0 calc(-64px*var(--tcs,1)*var(--pbk,1))!important}"
      + ".tc-stick>i{width:calc(58px*var(--tcs,1)*var(--pbk,1))!important;"
      + "height:calc(58px*var(--tcs,1)*var(--pbk,1))!important;"
      + "margin:calc(-29px*var(--tcs,1)*var(--pbk,1))!important}"
      + ".tc-jump,.tc-dash,.tc-lock,.tc-inc,.tc-sm,.tc-bag,.tc-pot,.tc-ultb,.tc-aim,.tc-act"
      + "{transform:scale(var(--pbk,1))!important}"
      + ".tc-btn.on{transform:scale(calc(var(--pbk,1)*.88)) translateY(2px)!important}";

    private WebView webHome, webGuide;
    private FrameLayout capsule;
    private View pill;
    private TextView tabHome, tabGuide;
    private int itemW;
    private boolean guideLoaded = false;
    private int current = 0;              // 0=首页 1=攻略
    private boolean dark = true;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyImmersive();
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

        // 底部悬浮胶囊: 首页 / 攻略 (尺寸/样式对齐首页 CapsuleBottomBar, 无阴影)
        int screenW = getResources().getDisplayMetrics().widthPixels;
        itemW = Math.min(dp(78), (screenW - dp(20)) / 6);   // 与首页单格同宽
        int pillBg = dark ? 0xFF3D6DFF : 0xFF1677FF;
        int pillStroke = dark ? 0xFF5A7BD8 : 0xFF0B5BD6;

        capsule = new FrameLayout(this);
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ dark ? 0x1FFFFFFF : 0x73FFFFFF, dark ? 0x0AFFFFFF : 0x59EFF5FF });
        cbg.setCornerRadius(dp(22));
        cbg.setStroke(dp(1), dark ? 0x40FFFFFF : 0x66FFFFFF);
        capsule.setBackground(cbg);   // 不设 elevation / outline shadow: 无阴影

        // 滑动选中块(与首页一致: 高36dp, 宽 itemW-10dp, 圆角16)
        pill = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(16));
        pg.setColor(pillBg);
        pg.setStroke(dp(1), pillStroke);
        pill.setBackground(pg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                itemW - dp(10), dp(36), Gravity.CENTER_VERTICAL | Gravity.START);
        plp.leftMargin = dp(5);
        capsule.addView(pill, plp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        capsule.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabHome = makeTab("首页");
        tabGuide = makeTab("攻略");
        tabHome.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectTab(0); }
        });
        tabGuide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectTab(1); }
        });
        row.addView(tabHome, new LinearLayout.LayoutParams(itemW, dp(42)));
        row.addView(tabGuide, new LinearLayout.LayoutParams(itemW, dp(42)));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = dp(12);   // 与首页一致
        root.addView(capsule, clp);

        setContentView(root);
        webHome.loadUrl(URL_HOME);
        selectTab(0);
        applyOrientation();
    }

    private WebView makeWeb() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        // 对齐官方 Capacitor 壳: 不开 overview(否则整页被缩小, 游戏触摸按键跟着变小);
        // zoomEnabled=false, 缩放由游戏自己的 viewport(user-scalable=no) 管理。
        s.setLoadWithOverviewMode(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 对齐官方壳 capacitor.config.json 的 appendUserAgent, 游戏据此识别「已装 App」分支
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                + " PokeBonkAndroid/1.0.1");
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) {
                if (v == webHome) applyButtonScale();   // 页面就绪即注入放大样式(幂等)
            }
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
        t.setTextSize(12.5f);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void selectTab(int t) {
        current = t;
        webHome.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        webGuide.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        if (t == 1 && !guideLoaded) { guideLoaded = true; webGuide.loadUrl(URL_GUIDE); } // 攻略懒加载一次
        int offText = dark ? 0xFF8A94A6 : 0xFF7C8694;
        styleTab(tabHome, t == 0, offText);
        styleTab(tabGuide, t == 1, offText);
        // 滑动 pill 到对应格
        float to = t * itemW;
        ValueAnimator va = ValueAnimator.ofFloat(pill.getTranslationX(), to);
        va.setDuration(240);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { pill.setTranslationX((Float) a.getAnimatedValue()); }
        });
        va.start();
    }

    private void styleTab(TextView t, boolean on, int offText) {
        t.setTextColor(on ? 0xFFFFFFFF : offText);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    /** 与首页 applyImmersive 一致: 隐藏系统状态栏+导航栏(沉浸式 sticky, 上滑临时呼出自动回落)。
     *  比通用 Immersive.hide(仅导航栏) 更彻底, 保证横屏游戏全屏不被三大键顶起。 */
    private void applyImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /** 横屏隐藏胶囊, 竖屏显示; 配合 Manifest 的 configChanges, 旋转不重建 WebView。
     *  旋转后系统会重新显示导航栏, 故同时重新隐藏。 */
    private void applyOrientation() {
        if (capsule == null) return;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        capsule.setVisibility(landscape ? View.GONE : View.VISIBLE);
        applyImmersive();
        applyButtonScale();   // 横屏放大/竖屏还原, 实时改 CSS 变量, 不重载 WebView
    }

    /** app 级按键放大: 横屏放大游戏触摸摇杆与全部动作键, 竖屏原样。
     *  只改独立 CSS 变量 --pbk, 首次注入样式后切档无需重载, 游戏进度不丢。 */
    private void applyButtonScale() {
        if (webHome == null) return;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        String k = landscape ? String.valueOf(BTN_SCALE_LANDSCAPE) : "1.0";
        String js = "(function(){try{"
              + "document.documentElement.style.setProperty('--pbk'," + k + ");"
              + "var s=document.getElementById('pbk-zoom');"
              + "if(!s){s=document.createElement('style');s.id='pbk-zoom';"
              + "s.textContent='" + BTN_CSS + "';document.head.appendChild(s);}"
              + "}catch(e){}})()";
        try { webHome.evaluateJavascript(js, null); } catch (Throwable ignored) {}
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientation();
    }

    /** 系统导航栏在 WebView 交互/输入法收起/回到前台后会被系统重新显示,
     *  与首页/娱乐页一致: 获得焦点或回前台时重新隐藏, 保证横屏游戏全屏不被三大键顶起。 */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersive();
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
