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

/** 排盘查看器: 界面直接内置在 assets/pans/<key>/, 经虚拟域 https://pan.local/ 伺服(ES module/CORS 正常) */
public class PanViewerActivity extends Activity {

    private WebView web;
    private static int dp2(int v) { return (int) (v * android.content.res.Resources.getSystem().getDisplayMetrics().density); }
    private String key;

    @Override protected void onCreate(Bundle b) {
            sanitizeIntPrefs(this);
        super.onCreate(b);
        key = getIntent().getStringExtra("key");
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
        // qimen 排盘页: 注入原生剪贴板(WebView Clipboard API 不可用)
        if ("qimen".equals(key) || "bazi".equals(key)) {
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
        // 常驻"复制"按钮: 复制当前排盘页全文 (小六壬/梅花/六爻/奇门/八字通用)
        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void done(String text) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("pan", text));
                runOnUiThread(() -> android.widget.Toast.makeText(PanViewerActivity.this, "已复制排盘内容", android.widget.Toast.LENGTH_SHORT).show());
            }
        }, "__copy");
        android.widget.Button cpBtn = new android.widget.Button(this);
        cpBtn.setText("复制排盘");
        cpBtn.setTextSize(14);
        cpBtn.setTextColor(0xFFFFFFFF);
        cpBtn.setBackground(new android.graphics.drawable.GradientDrawable() {{
            setColor(0xCC2A3142); setCornerRadius(dp2(14));
        }});
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp2(40));
        ((android.graphics.drawable.GradientDrawable) cpBtn.getBackground()).setAlpha(230);
        clp.setMargins(dp2(16), dp2(8), dp2(16), dp2(12));
        cpBtn.setOnClickListener(v -> web.evaluateJavascript(
            "(function(){var t=document.body?document.body.innerText:'';__copy.done(t);})()", null));
        root.addView(cpBtn, clp);
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

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else finish();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
