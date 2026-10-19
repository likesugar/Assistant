package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** GKD 复刻: 应用规则组管理页 — 订阅栏/全局规则/应用专属规则/自定义组 (源: gkd-kit/gkd AppGroupListPage) */
public class GkdAppRulesActivity extends Activity {
    private String pkg, appName;
    private boolean dark;
    private int bg, cardBg, txtMain, txtSub, blue = 0xFF3B82F6, green = 0xFF22C55E;
    private LinearLayout list;
    private TextView subBar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pkg = getIntent().getStringExtra("pkg");
        appName = getIntent().getStringExtra("name");
        if (appName == null) appName = pkg;
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        bg = dark ? 0xFF121417 : 0xFFF6F7F9;
        cardBg = dark ? 0xFF1E2228 : 0xFFFFFFFF;
        txtMain = dark ? 0xFFE8EAED : 0xFF1A1C1E;
        txtSub = dark ? 0xFF9AA0A6 : 0xFF5F6368;

        // 最外层 FrameLayout: 内容列 + FAB 悬浮层 (垂直 LinearLayout 里 match_parent 悬浮层会挤掉 ScrollView)
        android.widget.FrameLayout rootF = new android.widget.FrameLayout(this);
        rootF.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(14), dp(14), dp(14));
        TextView back = txt("←", 20, txtMain, false);
        back.setPadding(dp(8), 0, dp(12), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        top.addView(txt(appName, 18, txtMain, true));
        root.addView(top, -1, dp(56));
        // 订阅链接栏
        subBar = txt("", 13, blue, true);
        subBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        subBar.setEllipsize(TextUtils.TruncateAt.END);
        subBar.setGravity(Gravity.CENTER_VERTICAL);
        String sn = GkdStore.subName(this);
        subBar.setText((sn == null ? "未添加订阅" : sn) + "  ›");
        subBar.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("订阅")
                    .setMessage(GkdStore.subName(this) + "\n" + GkdStore.subUrl(this))
                    .setPositiveButton("好", null).show();
        });
        root.addView(subBar, -1, dp(44));
        // 规则列表
        ScrollView sc = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(4), dp(12), dp(80));
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        // FAB +
        TextView fab = txt("＋", 22, 0xFFFFFFFF, false);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(blue); fg.setShape(GradientDrawable.OVAL);
        fab.setBackground(fg);
        fab.setElevation(dp(4));
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        fl.addView(fab, new android.widget.FrameLayout.LayoutParams(dp(56), dp(56),
                Gravity.BOTTOM | Gravity.END));
        ((android.widget.FrameLayout.LayoutParams) fab.getLayoutParams()).setMargins(0, 0, dp(20), dp(24));
        fab.setOnClickListener(v -> addCustomDialog());
        rootF.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        rootF.addView(fl, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(rootF);
        rebuild();
    }

    private void rebuild() {
        list.removeAllViews();
        JSONArray subs = GkdStore.subs(this);
        int en = 0, tot = 0;
        for (int i = 0; i < subs.length(); i++) {
            JSONObject o = subs.optJSONObject(i);
            if (o != null && o.optBoolean("enabled", true)) { en++; tot += subLen(o); }
        }
        String dbg = "诊断: 订阅" + en + "条 规则" + tot + "组 root=" + (GkdStore.subRoot(this) != null ? "OK" : "FAIL");
        TextView dbgV = txt(dbg, 11, 0xFFFF7043, false);
        dbgV.setPadding(dp(6), dp(2), 0, dp(2));
        list.addView(dbgV);
        // 订阅数据缺失时自动补拉 (AIsouler 内置自动下载)
        if (GkdStore.autoFillAsync(this, this::rebuild)) {
            TextView filling = txt("⏳ 正在下载订阅规则…", 13, blue, true);
            filling.setPadding(dp(6), dp(6), 0, 0);
            list.addView(filling);
        }
        JSONArray gl = GkdStore.globalGroups(this);
        if (gl != null && gl.length() > 0) {
            list.addView(section("全局规则"));
            for (int i = 0; i < gl.length(); i++) {
                JSONObject g = gl.optJSONObject(i);
                if (g == null) continue;
                String name = g.optString("name", "全局" + i);
                boolean def = g.optBoolean("enable", true);
                boolean on = GkdStore.effGlobalOn(this, pkg, name, def);
                list.addView(groupRow(name + " - 全局", g.optString("desc", ""), on, v -> {
                    boolean nv = !GkdStore.effGlobalOn(this, pkg, name, def);
                    GkdStore.setGlobalOv(this, pkg, name, nv);
                    rebuild();
                }, true));
            }
        }
        JSONArray ag = GkdStore.appGroupsRaw(this, pkg);
        if (ag != null && ag.length() > 0) {
            list.addView(section("应用规则"));
            for (int i = 0; i < ag.length(); i++) {
                JSONObject g = ag.optJSONObject(i);
                if (g == null) continue;
                final int key = g.optInt("key", i);
                String name = g.optString("name", "规则组" + key);
                boolean def = g.optBoolean("enable", true);
                boolean on = GkdStore.effGroupOn(this, pkg, key, def);
                list.addView(groupRow(name, g.optString("desc", ""), on, v -> {
                    boolean nv = !GkdStore.effGroupOn(this, pkg, key, def);
                    GkdStore.setGroupOv(this, pkg, key, nv);
                    rebuild();
                }, false));
            }
        }
        JSONArray cs = GkdStore.customGroups(this, pkg);
        if (cs != null && cs.length() > 0) {
            list.addView(section("自定义规则"));
            for (int i = 0; i < cs.length(); i++) {
                final int ci = i;
                JSONObject cu = cs.optJSONObject(i);
                if (cu == null) continue;
                final boolean con = cu.optBoolean("enabled", true);
                View row = groupRow("✎ " + cu.optString("name", "自定义"), cu.optString("matches", ""), con, v -> {
                    GkdStore.setCustomEnabled(this, pkg, ci, !con);
                    rebuild();
                }, false);
                row.setOnLongClickListener(v -> {
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("删除自定义规则")
                            .setMessage(cu.optString("name", ""))
                            .setPositiveButton("删除", (d, w) -> { GkdStore.removeCustom(this, pkg, ci); rebuild(); })
                            .setNegativeButton("取消", null).show();
                    return true;
                });
                list.addView(row);
            }
        }
        if (list.getChildCount() <= 1) {
            TextView empty = txt("该应用无订阅规则\n点右下角 + 添加自定义规则", 14, txtMain, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            list.addView(empty);
        }
    }

    private int subLen(JSONObject o) {
        try {
            JSONObject r = new JSONObject(o.optString("data", "{}"));
            int t = 0;
            org.json.JSONArray as = r.optJSONArray("apps");
            if (as != null) for (int i = 0; i < as.length(); i++) {
                JSONObject a = as.optJSONObject(i);
                org.json.JSONArray gs = a == null ? null : a.optJSONArray("groups");
                if (gs != null) t += gs.length();
            }
            return t;
        } catch (Throwable t2) { return 0; }
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

    private void addCustomDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText nameEt = new EditText(this);
        nameEt.setHint("规则组名称");
        box.addView(nameEt);
        final EditText mEt = new EditText(this);
        mEt.setHint("GKD 选择器, 如 [text*=\"跳过\"]");
        mEt.setTextSize(13);
        box.addView(mEt);
        new android.app.AlertDialog.Builder(this)
                .setTitle("添加自定义规则组")
                .setView(box)
                .setPositiveButton("添加", (d, w) -> {
                    String n = nameEt.getText().toString().trim();
                    String m = mEt.getText().toString().trim();
                    if (n.length() == 0 || m.length() == 0) { Toast.makeText(this, "名称和选择器都要填", Toast.LENGTH_SHORT).show(); return; }
                    try { GkdSelector.parse(m); } catch (Throwable t) { Toast.makeText(this, "选择器语法错误: " + t.getMessage(), Toast.LENGTH_LONG).show(); return; }
                    GkdStore.addCustom(this, pkg, n, m);
                    rebuild();
                })
                .setNegativeButton("取消", null).show();
                // 抓取当前界面树: 切到目标弹窗界面后点此按钮, 把文本截图发开发者即可写精准规则
                TextView snap = txt("📷 查看已抓快照 (弹窗界面时下拉通知点「抓取界面快照」)", 13, blue, true);
                snap.setPadding(dp(4), dp(12), 0, 0);
                snap.setOnClickListener(v2 -> {
                    String tree = GkdStore.sp(this).getString("last_snap", "");
                    long t = GkdStore.sp(this).getLong("last_snap_t", 0);
                    if (tree.length() == 0) { tree = "还没有快照\n开启常驻通知后, 在弹窗界面下拉通知点「📷 抓取界面快照」"; }
                    else tree = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date(t)) + "\n" + tree;
                    android.widget.ScrollView sv2 = new android.widget.ScrollView(this);
                    TextView tv2 = txt(tree, 11, txtMain, false);
                    sv2.addView(tv2);
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("界面快照 (截图发开发者)")
                            .setView(sv2)
                            .setPositiveButton("关闭", null).show();
                });
                box.addView(snap);
    }

    private TextView section(String s) {
        TextView t = txt(s, 13, txtSub, true);
        t.setPadding(dp(6), dp(14), 0, dp(6));
        return t;
    }

    private View groupRow(String title, String desc, boolean on, View.OnClickListener toggle, boolean isGlobal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardBg); g.setCornerRadius(dp(12));
        row.setBackground(g);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (isGlobal) {
            TextView ic = txt("⊘", 15, txtSub, false);
            ic.setPadding(dp(2), 0, dp(10), 0);
            head.addView(ic);
        }
        TextView tv = txt(title, 16, txtMain, false);
        head.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(on);
        if (toggle != null) sw.setOnClickListener(toggle);
        else sw.setEnabled(false);
        head.addView(sw);
        row.addView(head);
        if (desc.length() > 0) {
            TextView dv = txt(desc, 13, txtSub, false);
            dv.setPadding(0, dp(4), 0, 0);
            dv.setMaxLines(2);
            dv.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(dv);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView txt(String s, int sz, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
