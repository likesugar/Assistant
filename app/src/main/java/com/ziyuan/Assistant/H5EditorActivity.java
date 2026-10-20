package com.ziyuan.Assistant;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/** HTML5 编辑器 — 代码编辑 + 运行预览 + 插件(vConsole/Vue)注入 (HBuilderX 风格) */
public class H5EditorActivity extends Activity {
    private int idx;
    private JSONObject proj;
    private EditText code;
    private WebView web;
    private TextView tabHtml, tabPrev;
    private boolean dark = true;
    private int bg = 0xFF121212, cardBg = 0xFF1E1E20, txtMain = 0xFFE8EAED, txtSub = 0xFF9AA0A6, blue = 0xFF4FC3F7;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        idx = getIntent().getIntExtra("idx", 0);
        try { proj = H5Store.projects(this).optJSONObject(idx); } catch (Throwable t) { proj = null; }
        if (proj == null) { finish(); return; }
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false)) { /* dark default anyway */ }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        // 顶栏: ← 项目名 | 插件选择
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(12), dp(12), dp(12));
        TextView back = txt("←", 20, txtMain, false);
        back.setPadding(dp(6), 0, dp(12), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView name = txt(proj.optString("name", "项目"), 17, txtMain, true);
        name.setEllipsize(TextUtils.TruncateAt.END); name.setMaxLines(1);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView vc = txt("vC", 13, pluginOn("vconsole") ? 0xFF4FC3F7 : txtSub, true);
        vc.setPadding(dp(10), dp(4), dp(10), dp(4));
        vc.setOnClickListener(v -> {
            togglePlugin("vconsole");
            vc.setTextColor(pluginOn("vconsole") ? 0xFF4FC3F7 : txtSub);
            Toast.makeText(this, "vConsole " + (pluginOn("vconsole") ? "已开启" : "已关闭"), Toast.LENGTH_SHORT).show();
        });
        top.addView(vc);
        TextView vue = txt("Vue", 13, pluginOn("vue") ? 0xFF4FC3F7 : txtSub, true);
        vue.setPadding(dp(10), dp(4), dp(6), dp(4));
        vue.setOnClickListener(v -> {
            togglePlugin("vue");
            vue.setTextColor(pluginOn("vue") ? 0xFF4FC3F7 : txtSub);
            Toast.makeText(this, "Vue " + (pluginOn("vue") ? "已开启" : "已关闭"), Toast.LENGTH_SHORT).show();
        });
        top.addView(vue);
        root.addView(top);
        // 工具行: 代码/预览 tab + 运行
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPadding(dp(10), 0, dp(10), 0);
        tabHtml = tab("代码");
        tabPrev = tab("预览");
        tools.addView(tabHtml); tools.addView(tabPrev);
        TextView run = txt("▶ 运行", 13, 0xFFFFFFFF, true);
        run.setGravity(Gravity.CENTER);
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(0xFF2E7D32); rg.setCornerRadius(dp(14));
        run.setBackground(rg);
        run.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -2, 1f);
        rlp.gravity = Gravity.END; rlp.width = -2; rlp.weight = 0;
        rlp.leftMargin = dp(10);
        run.setLayoutParams(rlp);
        run.setOnClickListener(v -> runPreview());
        tools.addView(run);
        root.addView(tools, -1, dp(40));
        // 代码区
        code = new EditText(this);
        code.setText(proj.optString("html", ""));
        code.setTextSize(13);
        code.setTextColor(0xFFD4D4D4);
        code.setBackgroundColor(0xFF17181A);
        code.setGravity(Gravity.TOP);
        code.setTypeface(Typeface.MONOSPACE);
        code.setMinLines(6);
        code.setHorizontallyScrolling(true);
        code.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        code.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(code, new LinearLayout.LayoutParams(-1, 0, 1f));
        // 预览区 (默认隐藏)
        web = new WebView(this);
        android.webkit.WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        web.setBackgroundColor(0xFFFFFFFF);
        View pv = web;
        root.addView(pv, new LinearLayout.LayoutParams(-1, 0, 1f));
        pv.setVisibility(View.GONE);
        // 底部状态
        TextView status = txt("HTML · vConsole/Vue 插件点顶部 vC/Vue 切换", 11, txtSub, false);
        status.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(status);
        setContentView(root);
        hideNav();
        // tab 切换
        tabHtml.setOnClickListener(v -> { code.setVisibility(View.VISIBLE); pv.setVisibility(View.GONE); selTab(true); });
        tabPrev.setOnClickListener(v -> { runPreview(); code.setVisibility(View.GONE); pv.setVisibility(View.VISIBLE); selTab(false); });
        selTab(true);
    }

    private void selTab(boolean htmlTab) {
        tabHtml.setTextColor(htmlTab ? blue : txtSub);
        tabPrev.setTextColor(htmlTab ? txtSub : blue);
    }

    private TextView tab(String s) {
        TextView t = txt(s, 14, txtSub, true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        return t;
    }

    private void runPreview() {
        // 保存
        proj = optProj();
        if (proj == null) return;
        try { proj.put("html", code.getText().toString()); } catch (Throwable ignored) {}
        H5Store.saveProject(this, idx, proj);
        String html = code.getText().toString();
        // 注入插件 (head 前置)
        StringBuilder inject = new StringBuilder();
        if (pluginOn("vconsole")) {
            String vc = H5Store.asset(this, "vconsole.min.js");
            inject.append("<script>").append(vc).append("</script>\n");
        }
        if (pluginOn("vue")) {
            String vue = H5Store.asset(this, "vue.global.prod.js");
            inject.append("<script>").append(vue).append("</script>\n");
        }
        String out;
        if (html.contains("<head>")) out = html.replace("<head>", "<head>\n" + inject);
        else out = inject + html;
        web.loadDataWithBaseURL(null, out, "text/html", "UTF-8", null);
    }

    private JSONObject optProj() {
        try { return H5Store.projects(this).optJSONObject(idx); } catch (Throwable t) { return proj; }
    }

    private boolean pluginOn(String k) {
        JSONObject pl = proj == null ? null : proj.optJSONObject("plugins");
        return pl != null && pl.optBoolean(k, false);
    }

    private void togglePlugin(String k) {
        try {
            JSONObject pl = proj.optJSONObject("plugins");
            if (pl == null) { pl = new JSONObject(); proj.put("plugins", pl); }
            pl.put(k, !pl.optBoolean(k, false));
            H5Store.saveProject(this, idx, proj);
        } catch (Throwable ignored) {}
    }

    // ===== 隐藏系统导航栏 =====
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
