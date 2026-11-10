package com.ziyuan.Assistant;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;

/** 奇门遁甲查看器: 界面内置在 assets/pans/qimen/, 经虚拟域 https://pan.local/ 伺服(ES module/CORS 正常) */
public class PanViewerActivity extends Activity {

    private WebView web;
    private static int dp2(int v) { return (int) (v * android.content.res.Resources.getSystem().getDisplayMetrics().density); }
    private String key;
    // H5 单页视图层级上报(仅作状态记录/调试): 返回退出决策已改由 __jlBack() 的同步返回值决定,
    // 见 onBackPressed()。保留接口以免 H5 侧上报时因异步层级产生竞态。
    private volatile int navDepth = 0;

    @Override protected void onCreate(Bundle b) {
            sanitizeIntPrefs(this);
        super.onCreate(b);
        key = getIntent().getStringExtra("key");
        // 排盘页白名单: 奇门遁甲 + 江氏小六壬 + 六爻排盘 + 梅花易数(其余旧版本残留 key 直接返回, 不加载空资源白屏)
        if (!"qimen".equals(key) && !"jiang-liuren".equals(key) && !"liu-yao".equals(key) && !"meihua".equals(key)) { finish(); return; }
        String title = getIntent().getStringExtra("title");
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF141A22 : 0xFFF6F8FC);
        // 顶栏已按要求去掉, 返回走系统返回键/手势

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE); // 排盘模板更新频繁, 禁缓存
        // 跟随应用明暗: 暗色时 WebView 算法性反色
        try {
            if (dark) {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ws.setForceDark(WebSettings.FORCE_DARK_ON);
                } else {
                    ws.setForceDark(WebSettings.FORCE_DARK_AUTO);
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) ws.setAlgorithmicDarkeningAllowed(dark);
        } catch (Throwable ignored) {}
        web.setWebChromeClient(new android.webkit.WebChromeClient()); // 启用 confirm()/alert() 等 JS 对话框
        web.setWebViewClient(new WebViewClient() {
            public WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (!u.startsWith("https://pan.local/")) return null;
                try {
                    String rel = u.substring("https://pan.local/".length());
                    if (rel.isEmpty()) rel = "index.html";
                    int q = rel.indexOf('?'); if (q >= 0) rel = rel.substring(0, q);
                    String mime = "application/octet-stream";
                    String s = rel.toLowerCase();
                    if (s.endsWith(".html") || s.endsWith(".htm")) mime = "text/html";
                    else if (s.endsWith(".js") || s.endsWith(".mjs")) mime = "text/javascript";
                    else if (s.endsWith(".css")) mime = "text/css";
                    else if (s.endsWith(".json")) mime = "application/json";
                    else if (s.endsWith(".png")) mime = "image/png";
                    else if (s.endsWith(".jpg") || s.endsWith(".jpeg")) mime = "image/jpeg";
                    else if (s.endsWith(".gif")) mime = "image/gif";
                    else if (s.endsWith(".svg")) mime = "image/svg+xml";
                    else if (s.endsWith(".webp")) mime = "image/webp";
                    else if (s.endsWith(".ico")) mime = "image/x-icon";
                    else if (s.endsWith(".woff")) mime = "font/woff";
                    else if (s.endsWith(".woff2")) mime = "font/woff2";
                    else if (s.endsWith(".ttf")) mime = "font/ttf";
                    else if (s.endsWith(".otf")) mime = "font/otf";
                    else if (s.endsWith(".mp3")) mime = "audio/mpeg";
                    else if (s.endsWith(".mp4")) mime = "video/mp4";
                    if (s.endsWith(".html") || s.endsWith(".htm")) {
                        // 注入运行时 API Key: 索引页读 assets 后插入 process.env
                        byte[] html = readAsset("pans/" + key + "/" + rel);
                        String apiKey = getSharedPreferences("settings", MODE_PRIVATE).getString("aiKey", "");
                        String baseUrl = getSharedPreferences("settings", MODE_PRIVATE).getString("aiBase", "");
                        String model = getSharedPreferences("settings", MODE_PRIVATE).getString("aiModel", "");
                        android.content.SharedPreferences s2 = getSharedPreferences("settings", MODE_PRIVATE);
                        String role = s2.getString("aiRole", "");
                        String sysIn = s2.getString("aiSys", "");
                        String system = (role.isEmpty() ? "" : role + " ") + sysIn;
                        String[] kv = {
                            "API_KEY", apiKey, "BASE_URL", baseUrl, "MODEL", model,
                            "TEMP", s2.getInt("aiTemp", 70) == 0 ? "0" : String.valueOf(s2.getInt("aiTemp", 70) / 100.0),
                            "TOP_P", String.valueOf(s2.getInt("aiTopP", 100) / 100.0),
                            "MAX_TOKENS", s2.getString("aiTokens", "2048"),
                            "FREQ", String.valueOf(s2.getInt("aiFreq", 0) / 100.0),
                            "PRES", String.valueOf(s2.getInt("aiPres", 0) / 100.0),
                            "SYSTEM", system,
                        };
                        StringBuilder js = new StringBuilder("<script>window.process=window.process||{};"
                                + "window.process.env=window.process.env||{};");
                        for (int i = 0; i < kv.length; i += 2) {
                            String ev = kv[i + 1] == null ? "" : kv[i + 1].replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
                            js.append("window.process.env.").append(kv[i]).append("='").append(ev).append("';");
                        }
                        js.append("</script>");
                        // 屏显错误钩子: 排盘页白屏时直接把 JS 错误打到页面上
                        String errHook = "<script>window.addEventListener('error',function(ev){"
                                + "var d=document.createElement('div');d.style.cssText='position:fixed;bottom:0;left:0;right:0;z-index:99999;"
                                + "background:#900;color:#fff;font:12px monospace;padding:8px;white-space:pre-wrap;max-height:40%;overflow:auto';"
                                + "d.textContent='JSERR: '+(ev.message||'')+' @'+(ev.filename||'')+':'+(ev.lineno||0);"
                                + "document.body&&document.body.appendChild(d);});"
                                + "window.addEventListener('unhandledrejection',function(ev){"
                                + "var d=document.createElement('div');d.style.cssText='position:fixed;bottom:0;left:0;right:0;z-index:99999;"
                                + "background:#906;color:#fff;font:12px monospace;padding:8px;white-space:pre-wrap;max-height:40%;overflow:auto';"
                                + "d.textContent='REJECT: '+(ev.reason&&(ev.reason.stack||ev.reason.message)||ev.reason);"
                                + "document.body&&document.body.appendChild(d);});</script>";
                        String inject = js.toString() + errHook;
                        String page = new String(html, "UTF-8");
                        if (page.contains("<head>")) page = page.replaceFirst("<head>", "<head>" + inject);
                        else page = inject + page;
                        HashMap<String, String> h2 = new HashMap<>();
                        h2.put("Access-Control-Allow-Origin", "*");
                        return new WebResourceResponse("text/html", "utf-8", 200, "OK", h2,
                                new ByteArrayInputStream(page.getBytes("UTF-8")));
                    }
                    InputStream is = getAssets().open("pans/" + key + "/" + rel);
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Access-Control-Allow-Origin", "*");
                    return new WebResourceResponse(mime, null, 200, "OK", h, is);
                } catch (Throwable e) {
                    String rel2 = req.getUrl().toString().replace("https://pan.local/", "");
                    android.util.Log.e("PanViewer", "asset miss: pans/" + key + "/" + rel2 + " : " + e);
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(("not found: pans/" + key + "/" + rel2 + " (" + e + ")").getBytes()));
                }
            }
        });
        // 奇门遁甲页: 注入原生剪贴板(WebView Clipboard API 不可用)
        if ("qimen".equals(key)) {
            web.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void set(String text) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("qimen", text));
                }
            }, "__acl");
        }
        web.setVerticalScrollBarEnabled(true);
        web.setBackgroundColor(Color.TRANSPARENT);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        // 排盘页复制能力: 注入 __copy JS 接口, 由 H5 页内"复制排盘结果"按钮调用(原生底部复制条已移除)
        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void done(String text) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("pan", text));
                runOnUiThread(() -> android.widget.Toast.makeText(PanViewerActivity.this, "已复制排盘内容", android.widget.Toast.LENGTH_SHORT).show());
            }
        }, "__copy");
        // 江氏小六壬单页 H5 上报视图层级(0首页/1起课页/2结果页), 供系统返回键逐层回退
        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void setDepth(int d) { navDepth = d; }
        }, "__nav");
        setContentView(root);
        Immersive.hide(this);
        web.loadUrl("https://pan.local/index.html");
    }

    private static void sanitizeIntPrefs(android.content.Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("settings", MODE_PRIVATE);
        String[][] kv = {{"aiTemp","70"},{"aiTopP","100"},{"aiFreq","0"},{"aiPres","0"}};
        for (String[] e2 : kv) {
            try { sp.getInt(e2[0], Integer.parseInt(e2[1])); }
            catch (Throwable t) {
                try { int v = (int) Double.parseDouble(String.valueOf(sp.getAll().get(e2[0]))); sp.edit().putInt(e2[0], v).apply(); }
                catch (Throwable t2) { sp.edit().putInt(e2[0], Integer.parseInt(e2[1])).apply(); }
            }
        }
    }
    private byte[] readAsset(String path) throws Exception {
        InputStream in = getAssets().open(path);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    // WebView 初始 about:blank 会让 canGoBack() 为 true, 直接 goBack() 只是退到空白页(白屏),
    // 用户需再右滑一次才 finish —— 即"右滑两次才能退出"。只把真实的排盘页面算作可回退历史。
    private boolean hasRealBackHistory() {
        if (web == null || !web.canGoBack()) return false;
        try {
            android.webkit.WebBackForwardList list = web.copyBackForwardList();
            for (int i = list.getCurrentIndex() - 1; i >= 0; i--) {
                String u = list.getItemAtIndex(i).getUrl();
                if (u != null && u.startsWith("https://pan.local/")) return true;
            }
        } catch (Throwable ignored) { return true; }
        return false;
    }

    @Override public void onBackPressed() {
        if (web == null) { finish(); return; }
        if ("jiang-liuren".equals(key) || "liu-yao".equals(key) || "meihua".equals(key)) {
            // 单页 H5 是视图切换(无浏览历史): 返回动作交给 H5 __jlBack() 逐层回退,
            // 由 H5 同步返回 'stay'(已回退一层, 留在页面) 或 'exit'(已在首页, 退出 Activity)。
            // 不再依赖 __nav.setDepth 的异步回写: 快速连续右滑时它会读到旧层级而吞掉一次返回。
            web.evaluateJavascript(
                "(function(){try{return window.__jlBack?window.__jlBack():'exit'}catch(e){return 'exit'}})()",
                value -> {
                    if (value == null || !value.contains("stay")) {
                        if (!isFinishing()) finish();
                    }
                });
            return;
        }
        if (hasRealBackHistory()) web.goBack(); else finish();
    }

    // 交互后系统导航栏可能重新弹出, 重新应用沉浸式隐藏(隐藏底部导航栏)
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
