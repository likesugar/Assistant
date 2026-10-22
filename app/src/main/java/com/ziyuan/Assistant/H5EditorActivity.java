package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

/** HTML5 编辑器 — 左侧文件树 + 多文件编辑 + file:// 运行(相对引用生效) + vConsole/Vue 注入 */
public class H5EditorActivity extends Activity {
    private String projName;
    private File projDir;
    private H5HttpServer server;
    private File curFile;
    private LinearLayout sideTag;
    private io.github.rosemoe.sora.widget.CodeEditor code;
    private TextView curPath;
    private LinearLayout fileList;
    private android.webkit.WebView web;
    private FrameLayout editorPane, previewPane;
    private LinearLayout topBar, tabsBar, symBar;
    private FrameLayout rootFrame;
    private boolean fullPreview = false;
    private TextView fsClose;
    private TextView tabCode, tabPrev;
    private boolean useVc = true, useVue = false;
    private static final int REQ_IMPORT = 51;
    private int bg = 0xFF121212, cardBg = 0xFF1E1E20, txtMain = 0xFFE8EAED, txtSub = 0xFF9AA0A6, blue = 0xFF4FC3F7;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        projName = getIntent().getStringExtra("name");
        projDir = H5Store.projectDir(this, projName);
        if (projDir == null || !projDir.exists()) { finish(); return; }
        buildUi();
        hideNav();
    }

    private void buildUi() {
        rootFrame = new FrameLayout(this);
        FrameLayout rootF = rootFrame;
        rootF.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        // 顶栏: ← 项目名 | ▶ | ⊕新建 | ⬆导入
        topBar = new LinearLayout(this);
        LinearLayout top = topBar;
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(10), dp(10), dp(10));
        TextView back = txt("←", 20, txtMain, false);
        back.setPadding(dp(6), 0, dp(10), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        // ☰ 布局 (收起/展开左侧文件栏)
        top.addView(toolBtn("☰", v -> {
            if (sideTag.getVisibility() == View.VISIBLE) sideTag.setVisibility(View.GONE);
            else sideTag.setVisibility(View.VISIBLE);
        }));
        top.addView(txt("布局", 16, txtMain, true));
        TextView name = txt("", 1, txtMain, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(name);
        // WebCat 同款: 撤销 / 重做 / 运行
        top.addView(toolBtn("↶", v -> code.undo()));
        top.addView(toolBtn("↷", v -> code.redo()));
        top.addView(toolBtn("▶", v -> { saveCur(); runHtml(currentHtml()); }));
        root.addView(top, -1, dp(46));
        // 主区: 左侧文件树 + 右侧编辑/预览
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        // 左侧栏
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(0xFF17181A);
        side.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView sideTitle = txt("文件", 12, txtSub, true);
        sideTitle.setPadding(dp(8), 0, 0, dp(6));
        side.addView(sideTitle);
        fileList = new LinearLayout(this);
        fileList.setOrientation(LinearLayout.VERTICAL);
        side.addView(fileList, new LinearLayout.LayoutParams(-1, -2));
        main.addView(side, dp(190), -1);
        sideTag = side;
        // 右侧: 编辑区 / 预览区 (Tab 切换)
        editorPane = new FrameLayout(this);
        // WebCat 同款编辑器组件: rosemoe sora-editor (行号/高亮/自动缩进)
        code = new io.github.rosemoe.sora.widget.CodeEditor(this);
        code.setTextSize(13 * getResources().getDisplayMetrics().density);
        code.setTypefaceText(Typeface.MONOSPACE);
        code.setPadding(dp(10), dp(10), dp(10), dp(10));
        editorPane.addView(code, new FrameLayout.LayoutParams(-1, -1));
        previewPane = new FrameLayout(this);
        previewPane.setBackgroundColor(0xFFFFFFFF);
        web = new WebView(this);
        android.webkit.WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        // 对齐 WebCat: 不拦 http 明文流/自动播/视频回调和全屏支持
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setDomStorageEnabled(true);
        web.setWebChromeClient(new android.webkit.WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, String url) {
                // 插件离线拦截: <script src="vconsole.min.js"> / vue.global.prod.js 直接可用
                if (url.endsWith("vconsole.min.js")) return assetResp("vconsole.min.js", "application/javascript");
                if (url.endsWith("vue.global.prod.js") || url.endsWith("vue.min.js")) return assetResp("vue.global.prod.js", "application/javascript");
                if (url.endsWith("hls.min.js") || url.endsWith("hls.js")) return assetResp("hls.min.js", "application/javascript");
                return null;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                // vConsole 开关: 注入调试台
                if (useVc) v.evaluateJavascript("try{window.vConsole=new VConsole();}catch(e){}", null);
            }
        });
        previewPane.addView(web, new FrameLayout.LayoutParams(-1, -1));
        editorPane.setVisibility(View.VISIBLE);
        previewPane.setVisibility(View.GONE);
        main.addView(editorPane, new LinearLayout.LayoutParams(0, -1, 1f));
        main.addView(previewPane, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
        // WebCat 同款 Tab 行: 左=文件名, 右=设计/代码/退出
        tabsBar = new LinearLayout(this);
        LinearLayout tabs = tabsBar;
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(cardBg);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(10), 0, dp(6), 0);
        tabFile = txt("index.html", 14, txtMain, true);
        tabFile.setEllipsize(TextUtils.TruncateAt.END);
        tabFile.setMaxLines(1);
        tabs.addView(tabFile, new LinearLayout.LayoutParams(0, -2, 1f));
        tabPrev = tabBtn("设计", false);
        tabs.addView(tabPrev);
        tabCode = tabBtn("代码", true);
        tabs.addView(tabCode);
        tabs.addView(exitBtn());
        root.addView(tabs, -1, dp(44));
        // WebCat 同款: 底部符号快捷条 (插入光标处)
        symBar = new LinearLayout(this);
        LinearLayout sym = symBar;
        sym.setOrientation(LinearLayout.HORIZONTAL);
        sym.setBackgroundColor(0xFF17181A);
        sym.setGravity(Gravity.CENTER);
        String[] syms = {"<", ">", "/", "=", "\"", ":", ";", "(", ")"};
        for (String s2 : syms) {
            TextView st = txt(s2, 16, txtMain, false);
            int pd = dp(10);
            st.setPadding(pd, dp(6), pd, dp(6));
            st.setOnClickListener(v -> {
                int cur = code.getCursor().getLeft();
                code.getText().insert(cur, cur, s2);
            });
            sym.addView(st);
        }
        root.addView(sym, -1, dp(40));
        rootF.addView(root, new FrameLayout.LayoutParams(-1, -1));
        // 当前文件路径条
        curPath = txt("", 11, txtSub, false);
        curPath.setPadding(dp(10), dp(2), dp(6), dp(2));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        curPath.setBackgroundColor(0x88000000);
        rootF.addView(curPath, clp);
        setContentView(rootF);
        // 默认打开 index.html
        File idx = new File(projDir, "index.html");
        openFile(idx.exists() ? idx : firstFile());
        renderFileList();
    }

    private File firstFile() {
        List<String> t = H5Store.listTree(projDir);
        for (String s : t) if (!s.endsWith("/") && H5Store.isHtml(s)) return new File(projDir, s);
        for (String s : t) if (!s.endsWith("/")) return new File(projDir, s);
        return null;
    }

    private TextView tabFile;
    private TextView exitBtn() {
        TextView t = txt("退出", 14, txtMain, false);
        t.setPadding(dp(12), dp(4), dp(12), dp(4));
        t.setOnClickListener(v -> finish());
        return t;
    }
    private TextView toolBtn(String s, View.OnClickListener l) {
        TextView t = txt(s, 16, txtMain, false);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setOnClickListener(l);
        return t;
    }

    private TextView tabBtn(String s, boolean sel) {
        TextView t = txt(s, 14, sel ? txtMain : txtSub, sel);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setOnClickListener(v -> switchTab(s));
        return t;
    }

    private void switchTab(String s) {
        boolean codeTab = s.equals("代码");
        editorPane.setVisibility(codeTab ? View.VISIBLE : View.GONE);
        previewPane.setVisibility(codeTab ? View.GONE : View.VISIBLE);
        tabCode.setTextColor(codeTab ? txtMain : txtSub);
        tabPrev.setTextColor(codeTab ? txtSub : txtMain);
        tabCode.setTypeface(null, codeTab ? Typeface.BOLD : Typeface.NORMAL);
        tabPrev.setTypeface(null, codeTab ? Typeface.BOLD : Typeface.NORMAL);
        if (!codeTab) saveCur();
    }

    private void openFile(File f) {
        if (f == null || !f.exists()) return;
        saveCur();
        curFile = f;
        if (H5Store.isTextFile(f.getName())) {
            code.setText(H5Store.readFile(f));
            tabFile.setText(f.getName());
            switchTab("代码");
        } else {
            saveCur();
            runHtml(f);
        }
        curPath.setText(rel(f));
    }

    private String rel(File f) {
        String p = f.getAbsolutePath(), b = projDir.getAbsolutePath();
        return p.startsWith(b) ? p.substring(b.length() + 1) : f.getName();
    }

    private void saveCur() {
        if (curFile != null && curFile.exists() && H5Store.isTextFile(curFile.getName()))
            H5Store.writeFile(curFile, code.getText().toString());
    }

    private File currentHtml() {
        if (curFile != null && H5Store.isHtml(curFile.getName())) return curFile;
        File idx = new File(projDir, "index.html");
        return idx.exists() ? idx : firstFile();
    }

    private void runHtml(File f) {
        if (f == null) { Toast.makeText(this, "没有 HTML 文件", Toast.LENGTH_SHORT).show(); return; }
        // 对齐 WebCat: 本地 HTTP 服务器加载, 相对引用/明文流/视频拖动原生生效
        if (server != null) server.stop();
        server = new H5HttpServer(H5HttpServer.dirResponder(projDir));
        server.start();
        String rel = projDir.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/');
        enterFullPreview(rel);
    }

    /** ▶ 运行 = 全屏预览（隐藏顶栏/文件树/Tab/符号条，返回键或右上 ✕ 退出） */
    private void enterFullPreview(String rel) {
        fullPreview = true;
        topBar.setVisibility(View.GONE);
        sideTag.setVisibility(View.GONE);
        tabsBar.setVisibility(View.GONE);
        symBar.setVisibility(View.GONE);
        switchTab("预览");
        if (fsClose == null) {
            fsClose = txt("✕", 16, 0xFFE8EAED, true);
            fsClose.setBackgroundColor(0x99000000);
            int pd = dp(12);
            fsClose.setPadding(pd, dp(8), pd, dp(8));
            rootFrame.addView(fsClose, new FrameLayout.LayoutParams(-2, -2));
        }
        FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) fsClose.getLayoutParams();
        fp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        fp.topMargin = dp(16); fp.rightMargin = dp(16);
        fsClose.setLayoutParams(fp);
        fsClose.setVisibility(View.VISIBLE);
        fsClose.setOnClickListener(v -> exitFullPreview());
        web.loadUrl(server.base() + rel);
    }

    private void exitFullPreview() {
        fullPreview = false;
        if (fsClose != null) fsClose.setVisibility(View.GONE);
        topBar.setVisibility(View.VISIBLE);
        sideTag.setVisibility(View.VISIBLE);
        tabsBar.setVisibility(View.VISIBLE);
        symBar.setVisibility(View.VISIBLE);
        switchTab("代码");
        hideNav();
    }

    private WebResourceResponse assetResp(String name, String mime) {
        try {
            String s = H5Store.asset(this, name);
            return new WebResourceResponse(mime, "utf-8", new ByteArrayInputStream(s.getBytes("UTF-8")));
        } catch (Throwable t) { return null; }
    }

    private void renderFileList() {
        fileList.removeAllViews();
        List<String> tree = H5Store.listTree(projDir);
        for (String rel : tree) {
            final boolean isDir = rel.endsWith("/");
            final String name = isDir ? rel.substring(0, rel.length() - 1) : rel;
            TextView t = txt((isDir ? "📁 " : iconOf(name) + " ") + name, 12, isDir ? txtMain : txtSub, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.START);
            t.setPadding(dp(8), dp(6), dp(4), dp(6));
            t.setOnClickListener(v -> {
                if (isDir) return;
                openFile(new File(projDir, name));
            });
            t.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("删除文件")
                        .setMessage(name)
                        .setPositiveButton("删除", (d, w) -> { new File(projDir, name).delete(); renderFileList(); })
                        .setNegativeButton("取消", null).show();
                return true;
            });
            fileList.addView(t);
        }
        if (tree.isEmpty()) fileList.addView(txt("(空项目)", 12, txtSub, false));
    }

    private String iconOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "🌐";
        if (n.endsWith(".css")) return "🎨";
        if (n.endsWith(".js")) return "📜";
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".webp")) return "🖼";
        return "📄";
    }

    private void newFileDialog() {
        EditText et = new EditText(this);
        et.setHint("路径, 如 jc/4/8.html 或 style.css");
        new android.app.AlertDialog.Builder(this)
                .setTitle("新建文件")
                .setView(et)
                .setPositiveButton("创建", (d, w) -> {
                    String p = et.getText().toString().trim();
                    if (p.length() == 0) return;
                    File f = new File(projDir, p);
                    if (f.exists()) { Toast.makeText(this, "已存在", Toast.LENGTH_SHORT).show(); return; }
                    if (H5Store.isHtml(p)) H5Store.writeFile(f, H5Store.DEFAULT_HTML);
                    else H5Store.writeFile(f, "");
                    renderFileList();
                    openFile(f);
                })
                .setNegativeButton("取消", null).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMPORT && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            String dn = "file_" + System.currentTimeMillis();
            try {
                android.database.Cursor c = getContentResolver().query(u, null, null, null, null);
                if (c != null) {
                    int ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (c.moveToFirst() && ix >= 0) dn = c.getString(ix);
                    c.close();
                }
            } catch (Throwable ignored) {}
            H5Store.importFrom(this, projDir, u, dn);
            renderFileList();
            Toast.makeText(this, "已导入 " + dn, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (fullPreview) { exitFullPreview(); return; }
        saveCur();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() { if (server != null) server.stop(); super.onDestroy(); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideNav();
    }
    private void hideNav() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                getWindow().setNavigationBarContrastEnforced(false);
                getWindow().getInsetsController().hide(android.view.WindowInsets.Type.navigationBars());
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } catch (Throwable ignored) {}
    }

    private TextView txt(String s, int sz, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
