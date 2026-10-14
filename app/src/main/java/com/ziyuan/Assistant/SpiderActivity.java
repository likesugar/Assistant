package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 爬虫·主页 (图1复刻): 收藏站点圆形卡片横排(秀人网第一) + 底部胶囊条
 * 胶囊: ⭐收藏当前页(收藏后"上去"到主页卡片) / 点"主页"弹输入框跳转
 * 详情页注入"⬇下载全部", 整本存 Pictures/美女/
 */
public class SpiderActivity extends Activity {

    private static final String PREF = "spider_favs";
    private static final String[] SITE_NAMES = {"秀人网", "ATG"};
    private static final String[] SITE_URLS = {"https://axiuren.com/", "http://ok.atg678yes.live/"};
    private static final int[] SITE_COLORS = {0xFFd65db1, 0xFF315CDE, 0xFF9c8e7d, 0xFFa56bce, 0xFF1FA855, 0xFFe67e22};

    private WebView web;
    private LinearLayout homeRoot;
    private LinearLayout cardsRow;
    private boolean dark;
    private String currentUrl = "";
    private final ExecutorService pool = Executors.newFixedThreadPool(6);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // WebView(不显示, 浏览态才上屏)
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new And(), "And");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) { h.proceed(); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public void onPageFinished(WebView v, String u) { inject(v, u); }
        });

        buildHome();
    }

    // ---------------- 主页(图1) ----------------

    private void buildHome() {
        int bg = dark ? 0xFF000000 : 0xFFFFFFFF;
        int tx = dark ? 0xFFFFFFFF : 0xFF000000;

        homeRoot = new LinearLayout(this);
        homeRoot.setOrientation(LinearLayout.VERTICAL);
        homeRoot.setBackgroundColor(bg);

        // 卡片区: 横向滚动圆形收藏
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.HORIZONTAL);
        pad.setPadding(40, 80, 40, 20);
        cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        cardsRow.setGravity(Gravity.CENTER_VERTICAL);
        pad.addView(cardsRow);
        hs.addView(pad);
        homeRoot.addView(hs, new LinearLayout.LayoutParams(-1, -2, 1));

        // 底部胶囊条
        LinearLayout pillBg = new LinearLayout(this);
        pillBg.setOrientation(LinearLayout.VERTICAL);
        pillBg.setGravity(Gravity.BOTTOM);
        pillBg.setBackgroundColor(bg);
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pillBg2 = new GradientDrawable();
        pillBg2.setColor(dark ? 0xFF1C1C1E : 0xFFf2f3f5);
        pillBg2.setCornerRadius(dip(24));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, dip(48));
        plp.setMargins(dip(20), dip(8), dip(20), dip(28));
        pill.setBackground(pillBg2);
        pill.setPadding(dip(18), 0, dip(18), 0);

        TextView star = new TextView(this);
        star.setText("⭐");
        star.setTextSize(20);
        star.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { favCurrent(); } });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.rightMargin = dip(12);
        pill.addView(star, slp);

        TextView home = new TextView(this);
        home.setText("主页");
        home.setTextSize(16);
        home.setTextColor(dark ? 0xFFAAAAAA : 0xFF666666);
        home.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showJumpDialog(); } });
        pill.addView(home, new LinearLayout.LayoutParams(0, -2, 1));

        pillBg.addView(pill, plp);
        homeRoot.addView(pillBg, new LinearLayout.LayoutParams(-1, -2));

        setContentView(homeRoot);
        refreshCards();
    }

    private void refreshCards() {
        cardsRow.removeAllViews();
        JSONArray favs = loadFavs();
        int n = favs.length();
        for (int i0 = 0; i0 < n; i0++) {
            final int i = i0;
            JSONObject o = favs.optJSONObject(i);
            if (o == null) continue;
            final String name = o.optString("name", "站");
            final String url = o.optString("url", "");
            if (url.isEmpty()) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dip(2), 0, dip(24), 0);
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { browse(url); } });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) { editFav(i, name, url); return true; }
            });
            TextView av = new TextView(this);
            av.setText(name.length() > 2 ? name.substring(0, 2) : name);
            av.setTextColor(0xFFFFFFFF);
            av.setTextSize(22);
            av.setGravity(Gravity.CENTER);
            GradientDrawable c = new GradientDrawable();
            c.setShape(GradientDrawable.OVAL);
            c.setColor(o.has("color") ? o.optInt("color", SITE_COLORS[0]) : SITE_COLORS[i % SITE_COLORS.length]);
            av.setBackground(c);
            card.addView(av, new LinearLayout.LayoutParams(dip(74), dip(74)));
            TextView lb = new TextView(this);
            lb.setText(name);
            lb.setTextSize(15);
            lb.setTextColor(dark ? 0xFFEEEEEE : 0xFF000000);
            lb.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
            llp.topMargin = dip(12);
            card.addView(lb, llp);
            cardsRow.addView(card, new LinearLayout.LayoutParams(-2, -2));
        }
    }

    /** 长按收藏卡: 编辑名称/图标色/删除 */
    private void editFav(final int idx, final String name, final String url) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(24), dip(10), dip(24), 0);
        final EditText et = new EditText(this);
        et.setText(name);
        et.setSelection(et.getText().length());
        box.addView(et);
        LinearLayout colorsRow = new LinearLayout(this);
        colorsRow.setGravity(Gravity.CENTER);
        colorsRow.setPadding(0, dip(10), 0, dip(6));
        final String[] chosen = { null };
        JSONArray favs0 = loadFavs();
        JSONObject cur = favs0.optJSONObject(idx);
        final int curColor = cur != null ? cur.optInt("color", 0) : 0;
        for (int ci = 0; ci < SITE_COLORS.length; ci++) {
            final int col = SITE_COLORS[ci];
            TextView dot = new TextView(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(col);
            if (curColor == col) d.setStroke(dip(3), dark ? 0xFFFFFFFF : 0xFF000000);
            dot.setBackground(d);
            dot.setPadding(dip(6), dip(6), dip(6), dip(6));
            dot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                chosen[0] = String.valueOf(col);
                // 高亮选中
                LinearLayout pr = (LinearLayout) v.getParent();
                for (int j = 0; j < pr.getChildCount(); j++) {
                    GradientDrawable gd = (GradientDrawable) pr.getChildAt(j).getBackground();
                    gd.setStroke(0, 0);
                }
                GradientDrawable sel = (GradientDrawable) v.getBackground();
                sel.setStroke(dip(3), dark ? 0xFFFFFFFF : 0xFF000000);
            }});
            colorsRow.addView(dot, new LinearLayout.LayoutParams(dip(38), dip(38)));
        }
        box.addView(colorsRow);
        TextView tip = new TextView(this);
        tip.setText("长按删除请点下方「删除」");
        tip.setTextSize(11);
        tip.setTextColor(0xFF888888);
        box.addView(tip);
        new AlertDialog.Builder(this)
            .setTitle("编辑收藏")
            .setView(box)
            .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dg, int w) {
                    JSONArray favs = loadFavs();
                    JSONObject o = favs.optJSONObject(idx);
                    if (o == null) return;
                    try { o.put("name", et.getText().toString().trim()); } catch (Throwable ignored) {}
                    if (chosen[0] != null) try { o.put("color", Integer.parseInt(chosen[0])); } catch (Throwable ignored) {}
                    saveFavs(favs);
                    refreshCards();
                    toast("已保存");
                }
            })
            .setNeutralButton("删除", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dg, int w) { delFav(url); }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showJumpDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dip(24), dip(10), dip(24), 0);
        final EditText et = new EditText(this);
        et.setHint("输入网址，如 axiuren.com/21446.html");
        et.setInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
        et.setTextSize(15);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
            .setTitle("跳转")
            .setView(box)
            .setPositiveButton("前往", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String s = et.getText().toString().trim();
                    if (s.isEmpty()) return;
                    if (!s.startsWith("http")) s = "https://" + s;
                    browse(s);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void favCurrent() {
        if (currentUrl.isEmpty()) { toast("先浏览一个网页再收藏"); return; }
        try {
            JSONArray favs = loadFavs();
            for (int i = 0; i < favs.length(); i++)
                if (favs.optJSONObject(i) != null && favs.optJSONObject(i).optString("url", "").equals(currentUrl)) {
                    toast("已在收藏里"); return;
                }
            String host = "";
            try { host = new URL(currentUrl).getHost().replace("www.", ""); } catch (Throwable ignored) {}
            JSONObject o = new JSONObject();
            o.put("name", host.isEmpty() ? "站" : host);
            o.put("url", currentUrl);
            favs.put(o);
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("json", favs.toString()).apply();
            toast("已收藏，主页可看");
        } catch (Throwable ignored) {}
    }

    private JSONArray loadFavs() {
        try {
            String j = getSharedPreferences(PREF, MODE_PRIVATE).getString("json", "");
            if (!j.isEmpty()) return new JSONArray(j);
        } catch (Throwable ignored) {}
        JSONArray a = new JSONArray();
        for (int i = 0; i < SITE_NAMES.length; i++) {
            try { JSONObject o = new JSONObject(); o.put("name", SITE_NAMES[i]); o.put("url", SITE_URLS[i]); a.put(o); } catch (Throwable ignored) {}
        }
        return a;
    }

    private void saveFavs(JSONArray a) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("json", a.toString()).apply();
    }

    private void delFav(String url) {
        try {
            JSONArray src = loadFavs();
            JSONArray out = new JSONArray();
            for (int i = 0; i < src.length(); i++) {
                JSONObject o = src.optJSONObject(i);
                if (o == null || !url.equals(o.optString("url"))) out.put(o);
            }
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("json", out.toString()).apply();
            refreshCards();
            toast("已删除(长按卡片)");
        } catch (Throwable ignored) {}
    }

    // ---------------- 浏览态 ----------------

    private void browse(String url) {
        currentUrl = url;
        if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) web.getParent()).removeView(web);
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        TextView back = new TextView(this);
        back.setText("🏠 主页");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x66000000);
        bg.setCornerRadius(dip(18));
        back.setBackground(bg);
        back.setPadding(dip(14), dip(8), dip(14), dip(8));
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { buildHome(); } });
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        blp.setMargins(dip(10), dip(50), 0, 0);
        root.addView(back, blp);
        TextView ai = new TextView(this);
        ai.setText("🤖 AI");
        ai.setTextSize(13); ai.setTextColor(0xFFFFFFFF); ai.setTypeface(null, android.graphics.Typeface.BOLD);
        ai.setGravity(Gravity.CENTER);
        GradientDrawable ag = new GradientDrawable();
        ag.setColor(0xCC315CDE); ag.setCornerRadius(dip(18));
        ai.setBackground(ag);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        alp.setMargins(dip(12), 0, 0, dip(20));
        root.addView(ai, alp);
        ai.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivity(new android.content.Intent(SpiderActivity.this, AiChatActivity.class)); } });
        setContentView(root);
        web.loadUrl(url);
    }

    // ---------------- 下载按钮注入 ----------------

    private void inject(WebView v, String u) {
        if (u == null || u.startsWith("file:")) return;
        if (u.contains("axiuren.com") && u.matches(".*axiuren\\.com/\\d+\\.html.*")) { injectAxiuren(v); return; }
        if (u.contains("atg678yes") && u.contains("thread-")) { injectAtg(v); return; }
        // 通用: 所有页面注入, 收集页面内大图
        String collect =
            " var imgs=[];" +
            " [].forEach.call(document.querySelectorAll('img'),function(i){" +
            "  var s=i.getAttribute('data-src')||i.getAttribute('data-original')||i.src; if(!s)return;" +
            "  if(s.indexOf('data:')==0)return;" +
            "  if(s.indexOf('//')<0){s=location.protocol+'//'+location.host+(s.charAt(0)=='/'?s:'/'+s);}" +
            "  var w=i.naturalWidth||800; if(w<150&&i.naturalWidth>0)return;" +
            "  if(/logo|icon|avatar|btn|banner|ads?\\./i.test(s))return;" +
            "  if(imgs.indexOf(s)<0)imgs.push(s);" +
            " });" +
            " [].forEach.call(document.querySelectorAll('video,source'),function(v){var s=v.src||v.getAttribute('src'); if(s)imgs.push(s);});" +
            " var t=(document.title||'页面').trim().substring(0,50);" +
            " return JSON.stringify({name:t,imgs:imgs});";
        v.evaluateJavascript(btnJs(collect, "e67e22"), null);
    }

    /** Discuz论坛(atg): 帖子内附件图全部收集, 下载带登录Cookie */
    private void injectAtg(WebView v) {
        String collect =
            " var imgs=[];" +
            " [].forEach.call(document.querySelectorAll('img'),function(i){" +
            "  var s=i.getAttribute('src')||i.getAttribute('file')||''; if(!s)return;" +
            "  if(s.indexOf('attach')<0&&s.indexOf('forum.php?mod=image')<0)return;" +
            "  if(s.indexOf('//')<0){s=location.protocol+'//'+location.host+(s.charAt(0)=='/'?s:'/'+s);}" +
            "  if(imgs.indexOf(s)<0)imgs.push(s);" +
            " });" +
            " var t=(document.title||'帖子').trim().substring(0,50);" +
            " var lk=document.querySelector('.locked')||document.querySelector('[class*=hidden]')||document.body;" +
            " var txt=(lk.innerText||'').substring(0,4000);" +
            " return JSON.stringify({name:t,imgs:imgs,txt:txt});";
        v.evaluateJavascript(btnJs(collect, "1FA855"), null);
    }

    private String btnJs(String collectJs, String color) {
        return "(function(){" +
            "try{" +
            "window.__collect=function(){" + collectJs + "};" +
            "var old=window.__dlbtn; if(old)old.remove();" +
            "var b=document.createElement('div');" +
            "b.id='dlbtn'; b.textContent='⬇ 下载全部';" +
            "b.style.cssText='position:fixed;right:12px;bottom:60px;z-index:99999;background:#" + color + ";color:#fff;padding:10px 16px;border-radius:24px;font-size:14px;font-weight:bold;box-shadow:0 4px 12px rgba(0,0,0,.4);opacity:.92';" +
            "b.onclick=function(){var d=window.__collect();And.download(d)};" +
            "document.body.appendChild(b); window.__dlbtn=b;" +
            "}catch(e){}}" +
            ")();";
    }

    private void injectAxiuren(WebView v) {
        // 自动探测: 扫页面全部编号式图片URL, 按文件夹聚合, 选样本最多的文件夹推断编号规则(位数/扩展名), 不写死图床域名
        String collect =
            " var h=document.documentElement.innerHTML;" +
            " var folders={}; var re=/https?:\\/\\/[^\"'\\s\\\\]+\\/([^\"'\\s\\\\]*)/gi; var mm;" +
            " var re2=/^(https?:\\/\\/[^\"'\\s\\\\]+\\/)([^\"'\\s\\\\]*?)(\\d{2,6})\\.(webp|jpg|jpeg|png)$/i;" +
            " var lines=h.split(/['\"\\s]/);" +
            " for(var li=0;li<lines.length;li++){" +
            "  var u=lines[li]; var mm2=u.match(re2);" +
            "  if(mm2){var key=mm2[1]; if(!folders[key])folders[key]={n:0,pad:mm2[3].length,ext:mm2[4].toLowerCase()};" +
            "  folders[key].n++;}}" +
            " var cands=[];for(var k2 in folders)cands.push(k2);" +
            " cands.sort(function(a,b){return folders[b].n-folders[a].n;});" +
            " var folder=cands[0]||null; var urls=[];" +
            " var t=(document.querySelector('h1')||{textContent:document.title}).textContent;" +
            " var pm=t.match(/(\\d+)\\s*P/i); var n=pm?parseInt(pm[1]):0;" +
            " if(n<=0)n=120;" +
            " if(folder){var pad=folders[folder].pad; var ext=folders[folder].ext;" +
            "  for(var i=1;i<=n;i++){var s='00000000'+i; s=s.substring(s.length-Math.max(pad,(''+i).length)); urls.push(folder+s+'.'+ext);}}" +
            " [].forEach.call(document.querySelectorAll('video,source'),function(v){var s=v.src||v.getAttribute('src'); if(s)urls.push(s);});" +
            " var vm=h.match(/https?:\\/\\/[^\"'\\s\\\\]+\\.mp4[^\"'\\s\\\\]*/g);" +
            " if(vm)for(var k3=0;k3<vm.length;k3++){if(urls.indexOf(vm[k3])<0)urls.push(vm[k3]);}" +
            " return JSON.stringify({name:t.trim().substring(0,60),imgs:urls});";
        v.evaluateJavascript(btnJs(collect, "c0392b"), null);
    }

    private class And {

        /** 下载整本: payload={name, imgs:[url...]} */
        @JavascriptInterface
        public void download(String payload) {
            try {
                JSONObject o = new JSONObject(payload);
                String name = o.optString("name", "图集").replace('/', '_').replace(':', '_');
                JSONArray arr = o.optJSONArray("imgs");
                final String txt = o.optString("txt", "");
                final java.io.File dir0 = dlDir(name);
                if (arr == null || arr.length() == 0) {
                    if (txt.length() > 20) {
                        final String fname = name;
                        pool.execute(new Runnable() { public void run() {
                            try {
                                java.io.File f = new java.io.File(dir0, "帖子信息.txt");
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                                fos.write(txt.getBytes("UTF-8")); fos.close();
                                toast("已保存: 爬虫/" + fname + "/帖子信息.txt");
                            } catch (Throwable e) { toast("保存失败: " + e); }
                        }});
                    } else toast("没抓到图片链接");
                    return;
                }
                final String fname = name;
                final int total = arr.length();
                final java.io.File dir = dir0;
                if (txt.length() > 20) pool.execute(new Runnable() { public void run() {
                    try {
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(new java.io.File(dir, "帖子信息.txt"));
                        fos.write(txt.getBytes("UTF-8")); fos.close();
                    } catch (Throwable ignored) {}
                }});
                toast("开始下载 " + total + " 个(6线程) → 爬虫/" + fname);
                final java.util.concurrent.atomic.AtomicInteger okC = new java.util.concurrent.atomic.AtomicInteger();
                final java.util.concurrent.atomic.AtomicInteger doneC = new java.util.concurrent.atomic.AtomicInteger();
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(total);
                final JSONArray farr = arr;
                for (int i = 0; i < total; i++) {
                    final int idx = i;
                    pool.execute(new Runnable() { public void run() {
                        String url;
                        try { url = farr.getString(idx); } catch (Throwable e) { latch.countDown(); return; }
                        String ext = "jpg";
                        int dot = url.lastIndexOf('.');
                        if (dot > 0 && dot > url.lastIndexOf('/')) {
                            String e2 = url.substring(dot + 1).toLowerCase();
                            if (e2.length() >= 2 && e2.length() <= 5) ext = e2;
                        }
                        String fn = String.format(java.util.Locale.US, "%s_%03d.%s", fname, idx + 1, ext);
                        if (saveFile(url, dir, fn)) okC.incrementAndGet();
                        int d = doneC.incrementAndGet();
                        latch.countDown();
                        if (d % 5 == 0 || d == total) toast("进度 " + d + "/" + total);
                    }});
                }
                new Thread(new Runnable() { public void run() {
                    try { latch.await(); } catch (InterruptedException ignored) {}
                    toast("完成: 成功 " + okC.get() + "/" + total + " (爬虫/" + fname + ")");
                }}).start();
            } catch (Throwable e) { toast("解析失败: " + e); }
        }
    }

    private void toast(String s) {
        runOnUiThread(new Runnable() { public void run() {
            Toast.makeText(SpiderActivity.this, s, Toast.LENGTH_SHORT).show();
        }});
    }

    /** 下载目录 /storage/emulated/0/Android/data/com.ziyuan.Assistant/爬虫/{专辑}/ */
    private java.io.File dlDir(String album) {
        java.io.File base = new java.io.File(getExternalFilesDir(null).getParentFile(), "爬虫");
        java.io.File d = new java.io.File(base, album == null || album.length() == 0 ? "未命名" : album);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 文件直写 /Android/data/com.ziyuan.Assistant/爬虫/{专辑}/ (图片+mp4等媒体通用) */
    private boolean saveFile(String url, java.io.File dir, String fileName) {
        InputStream is = null; OutputStream os = null;
        try { trustAll(); } catch (Throwable ignored) {}
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000); c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
            String ck = CookieManager.getInstance().getCookie(url);
            if (ck != null) c.setRequestProperty("Cookie", ck);
            if (url.contains("ecmm.cc")) c.setRequestProperty("Referer", "https://axiuren.com/");
            if (url.contains("atg678yes")) c.setRequestProperty("Referer", currentUrl == null ? "http://ok.atg678yes.live/" : currentUrl);
            if (c.getResponseCode() != 200) return false;
            is = c.getInputStream();
            java.io.File out = new java.io.File(dir, fileName);
            os = new java.io.FileOutputStream(out);
            byte[] buf = new byte[8192]; int n; long sz = 0;
            while ((n = is.read(buf)) > 0) { os.write(buf, 0, n); sz += n; }
            os.flush();
            return sz >= 5000; // 太小=错误页
        } catch (Throwable e) { return false; }
        finally { try { if (is != null) is.close(); } catch (Throwable ignored) {} try { if (os != null) os.close(); } catch (Throwable ignored) {} }
    }

    // 图床证书问题全放行
    private static void trustAll() throws Exception {
        javax.net.ssl.TrustManager[] tm = new javax.net.ssl.TrustManager[]{ new javax.net.ssl.X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] a, String t) {}
            public void checkServerTrusted(java.security.cert.X509Certificate[] a, String t) {}
            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
        }};
        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
        sc.init(null, tm, new java.security.SecureRandom());
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
    }

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onBackPressed() {
        // 浏览态→主页; 主页→退出
        if (web.getParent() != null && web.canGoBack() && !currentUrl.isEmpty()) {
            // 优先回主页而不是网页历史, 网页内返回交给系统默认不可见
            buildHome();
        } else if (web.getParent() != null) {
            buildHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try { pool.shutdownNow(); } catch (Throwable ignored) {}
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }
}
