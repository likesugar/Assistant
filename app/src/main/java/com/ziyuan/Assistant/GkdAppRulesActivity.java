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
        root.addView(fl, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        rebuild();
    }

    private void rebuild() {
        list.removeAllViews();
        // 诊断行: 解析状态直接可见
        String dbg = "诊断: subRaw=" + GkdStore.subRaw(this).length() + "B root=" + (GkdStore.subRoot(this) != null ? "OK" : "FAIL")
                + (GkdStore.lastParseError != null ? " err=" + GkdStore.lastParseError : "");
        TextView dbgV = txt(dbg, 11, 0xFFFF7043, false);
        dbgV.setPadding(dp(6), dp(2), 0, dp(2));
        list.addView(dbgV);
        JSONArray gl = GkdStore.globalGroups(this);
        if (gl != null && gl.length() > 0) {
            list.addView(section("全局规则"));
            for (int i = 0; i < gl.length(); i++) {
                JSONObject g = gl.optJSONObject(i);
                if (g == null) continue;
                String name = g.optString("name", "全局" + i);
                boolean def = g.optBoolean("enable", true);
                boolean on = GkdStore.effGlobalOn(this, pkg, name, def);
                list.addView(groupRow("⊕ " + name, g.optString("desc", ""), on, v -> {
                    boolean nv = !GkdStore.effGlobalOn(this, pkg, name, def);
                    GkdStore.setGlobalOv(this, pkg, name, nv);
                    rebuild();
                }));
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
                }));
            }
        }
        JSONArray cs = GkdStore.customGroups(this, pkg);
        if (cs != null && cs.length() > 0) {
            list.addView(section("自定义规则"));
            for (int i = 0; i < cs.length(); i++) {
                JSONObject cu = cs.optJSONObject(i);
                if (cu == null) continue;
                list.addView(groupRow("✎ " + cu.optString("name", "自定义"), cu.optString("matches", ""), true, null));
            }
        }
        if (list.getChildCount() <= 1) {
            TextView empty = txt("该应用无订阅规则\n点右下角 + 添加自定义规则", 14, txtMain, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            list.addView(empty);
        }
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
    }

    private TextView section(String s) {
        TextView t = txt(s, 13, txtSub, true);
        t.setPadding(dp(6), dp(14), 0, dp(6));
        return t;
    }

    private View groupRow(String title, String desc, boolean on, View.OnClickListener toggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardBg); g.setCornerRadius(dp(12));
        row.setBackground(g);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = txt(title, 15, txtMain, true);
        head.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(on);
        if (toggle != null) sw.setOnClickListener(toggle);
        else sw.setEnabled(false);
        head.addView(sw);
        row.addView(head);
        if (desc.length() > 0) {
            TextView dv = txt(desc, 12, txtSub, false);
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
