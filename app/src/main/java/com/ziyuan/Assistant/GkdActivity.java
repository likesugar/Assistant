package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** GKD(去广告) 复刻页 — 四Tab: 仪表盘/应用列表/订阅/设置 (源: gkd-kit/gkd) */
public class GkdActivity extends Activity {
    private static final int[] TABS = {0, 1, 2, 3};
    private static final String[] TAB_NAMES = {"仪表盘", "应用列表", "订阅", "设置"};
    private LinearLayout[] pages = new LinearLayout[4];
    private TextView[] tabViews = new TextView[4];
    private LinearLayout body;
    private boolean dark;
    private int bg, cardBg, txtMain, txtSub, blue = 0xFF3B82F6, green = 0xFF22C55E;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        bg = dark ? 0xFF121417 : 0xFFF6F7F9;
        cardBg = dark ? 0xFF1E2228 : 0xFFFFFFFF;
        txtMain = dark ? 0xFFE7EAF0 : 0xFF1A1D23;
        txtSub = dark ? 0xFF8A93A0 : 0xFF6B7280;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(14), dp(16), dp(14));
        top.setBackgroundColor(cardBg);
        TextView back = txt("←", 18, txtMain, true);
        back.setOnClickListener(v -> finish());
        TextView title = txt("GKD 去广告", 18, txtMain, true);
        title.setPadding(dp(14), 0, 0, 0);
        top.addView(back); top.addView(title);
        root.addView(top, -1, dp(52));

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部Tab栏
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(cardBg);
        tabs.setPadding(0, dp(8), 0, dp(8));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = txt(TAB_NAMES[i], 13, txtSub, false);
            t.setGravity(Gravity.CENTER);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            t.setOnClickListener(v -> switchTab(idx));
            tabs.addView(t);
            tabViews[i] = t;
        }
        root.addView(tabs, -1, dp(56));
        setContentView(root);

        for (int i = 0; i < 4; i++) {
            pages[i] = new LinearLayout(this);
            pages[i].setOrientation(LinearLayout.VERTICAL);
            body.addView(pages[i], new LinearLayout.LayoutParams(-1, -1));
        }
        buildDashboard(); buildAppList(); buildSubs(); buildSettings();
        switchTab(0);
    }

    // ===== Tab1 仪表盘 =====
    private TextView a11yStatus, hitStatus;
    private void buildDashboard() {
        LinearLayout p = pages[0];
        LinearLayout c1 = card();
        TextView t1 = txt("无障碍服务", 16, txtMain, true); c1.addView(t1);
        a11yStatus = txt("", 13, txtSub, false); a11yStatus.setPadding(0, dp(6), 0, 0); c1.addView(a11yStatus);
        TextView btn = bigBtn("去开启无障碍");
        btn.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Throwable t) { Toast.makeText(this, "打不开设置", Toast.LENGTH_SHORT).show(); }
        });
        c1.addView(btn);
        p.addView(c1);

        LinearLayout c2 = card();
        c2.addView(txt("运行情况", 16, txtMain, true));
        hitStatus = txt("", 13, txtSub, false);
        hitStatus.setPadding(0, dp(6), 0, 0); c2.addView(hitStatus);
        p.addView(c2);

        LinearLayout c3 = card();
        c3.addView(txt("最近日志", 16, txtMain, true));
        TextView lg = txt(GkdStore.logs(this).isEmpty() ? "暂无日志" : GkdStore.logs(this), 12, txtSub, false);
        lg.setPadding(0, dp(6), 0, 0); lg.setMaxLines(8); lg.setEllipsize(TextUtils.TruncateAt.END);
        c3.addView(lg);
        p.addView(c3);
    }

    // ===== Tab2 应用列表 =====
    private java.util.List<android.content.pm.ApplicationInfo> appInfos;
    private ListView lv;
    private void buildAppList() {
        LinearLayout p = pages[1];
        TextView hint = txt("勾选要开启自动跳广告的应用（GKD 规则将在这些应用内生效）", 12, txtSub, false);
        hint.setPadding(dp(4), dp(2), dp(4), dp(8));
        p.addView(hint);
        appInfos = new ArrayList<>();
        try {
            for (android.content.pm.ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                if (getPackageManager().getLaunchIntentForPackage(ai.packageName) != null) appInfos.add(ai);
            }
        } catch (Throwable ignored) {}
        lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(new BaseAdapter() {
            public int getCount() { return appInfos.size(); }
            public Object getItem(int i) { return appInfos.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                android.content.pm.ApplicationInfo ai = appInfos.get(i);
                LinearLayout row = new LinearLayout(GkdActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                TextView name = txt(getPackageManager().getApplicationLabel(ai).toString(), 14, txtMain, false);
                TextView pk = txt(ai.packageName, 11, txtSub, false);
                LinearLayout left = new LinearLayout(GkdActivity.this);
                left.setOrientation(LinearLayout.VERTICAL);
                left.addView(name); left.addView(pk);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(left);
                android.widget.CheckBox cb = new android.widget.CheckBox(GkdActivity.this);
                cb.setChecked(GkdStore.isAppEnabled(GkdActivity.this, ai.packageName));
                cb.setOnCheckedChangeListener((b, on) -> GkdStore.setAppEnabled(GkdActivity.this, ai.packageName, on));
                row.addView(cb);
                return row;
            }
        });
        p.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    // ===== Tab3 订阅 =====
    private EditText subInput;
    private TextView subStatus;
    private String raw = "";
    private void buildSubs() {
        LinearLayout p = pages[2];
        LinearLayout c = card();
        c.addView(txt("规则订阅", 16, txtMain, true));
        subInput = new EditText(this);
        subInput.setText(GkdStore.subUrl(this));
        subInput.setTextSize(12);
        subInput.setTextColor(txtMain);
        subInput.setSingleLine(false);
        subInput.setMaxLines(3);
        subInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable eb = new GradientDrawable();
        eb.setColor(dark ? 0xFF171A1F : 0xFFEEF1F5); eb.setCornerRadius(dp(8));
        eb.setStroke(1, dark ? 0xFF3A4149 : 0xFFD7DDE4);
        subInput.setBackground(eb);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.topMargin = dp(8);
        subInput.setLayoutParams(ilp);
        c.addView(subInput);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView dl = bigBtn("下载/更新订阅");
        dl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        dl.setOnClickListener(v -> doDownload());
        TextView clr = bigBtn("删除订阅");
        clr.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        GradientDrawable g2 = new GradientDrawable();
        g2.setColor(dark ? 0xFF2A2F36 : 0xFFE9EDF2); g2.setCornerRadius(dp(10));
        clr.setBackground(g2); clr.setTextColor(txtMain);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp2.leftMargin = dp(10);
        clr.setLayoutParams(lp2);
        clr.setOnClickListener(v -> { GkdStore.sp(this).edit().remove("sub_raw").remove("sub_time").apply(); refreshSubStatus(); Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show(); });
        row.addView(dl); row.addView(clr);
        c.addView(row);
        subStatus = txt("", 12, txtSub, false);
        subStatus.setPadding(0, dp(10), 0, 0);
        c.addView(subStatus);
        p.addView(c);
        refreshSubStatus();
    }

    private void doDownload() {
        final String url = subInput.getText().toString().trim();
        if (url.length() == 0) { Toast.makeText(this, "先填订阅地址", Toast.LENGTH_SHORT).show(); return; }
        subStatus.setText("下载中…");
        new Thread(() -> {
            String err = null;
            try {
                String raw = GkdStore.downloadSub(GkdActivity.this, url);
                JSONObject root = new JSONObject(GkdStore.stripJson5(raw));
                if (root.optJSONArray("groups") == null && root.optJSONArray("globalGroups") == null && root.optJSONArray("apps") == null)
                    throw new Exception("内容不是GKD订阅规则");
            } catch (Throwable t) { err = t.getMessage(); }
            final String e = err;
            runOnUiThread(() -> {
                if (e != null) { subStatus.setText("下载失败: " + e); return; }
                refreshSubStatus();
                Toast.makeText(this, "订阅已更新", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void refreshSubStatus() {
        // 清理 vc351 误存的旧错误地址订阅
        String saved = GkdStore.subUrl(this);
        if (saved.contains("@gkd-kit/subscribe/")) {
            GkdStore.sp(this).edit().putString("sub_url", GkdStore.DEFAULT_SUB).remove("sub_raw").remove("sub_time").apply();
            subInput.setText(GkdStore.DEFAULT_SUB);
            raw = "";
        } else {
            raw = GkdStore.subRaw(this);
        }
        if (raw.isEmpty()) { subStatus.setText("未添加订阅\n官方订阅地址默认已填好，点【下载/更新订阅】即可拉取 GKD 官方规则库"); return; }
        String info = "已加载 · " + new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(GkdStore.sp(this).getLong("sub_time", 0)));
        try {
            JSONObject root = new JSONObject(GkdStore.stripJson5(raw));
            org.json.JSONArray gs = root.optJSONArray("groups");
            org.json.JSONArray as = root.optJSONArray("apps");
            int ng = gs != null ? gs.length() : 0, na = as != null ? as.length() : 0;
            info += "\n应用规则组 " + ng + " · 覆盖应用 " + na + "\n订阅名: " + root.optString("name", "-")
                    + " v" + root.optString("version", "-");
        } catch (Throwable ignored) {}
        subStatus.setText(info);
    }

    // ===== Tab4 设置 =====
    private void buildSettings() {
        LinearLayout p = pages[3];
        LinearLayout c = card();
        c.addView(txt("关于", 16, txtMain, true));
        TextView a = txt("GKD 复刻版 · 规则引擎与订阅格式 1:1 对齐 gkd-kit/gkd\n订阅 JSON5 兼容官方规则库，无障碍服务后台匹配自动点击。\n\n注意：应用列表里没勾选的应用不会触发任何规则。", 12, txtSub, false);
        a.setPadding(0, dp(6), 0, 0);
        c.addView(a);
        p.addView(c);
    }

    // ===== 通用 =====
    private void switchTab(int idx) {
        for (int i = 0; i < 4; i++) {
            pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            tabViews[i].setTextColor(i == idx ? blue : txtSub);
            tabViews[i].setTypeface(null, i == idx ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (idx == 0) refreshDash();
    }

    @Override
    protected void onResume() { super.onResume(); refreshDash(); }

    private void refreshDash() {
        boolean on = GkdAccessibilityService.running;
        a11yStatus.setText(on ? "● 运行中 — 规则匹配已生效" : "○ 未开启 — 开启后才能自动跳广告");
        a11yStatus.setTextColor(on ? green : txtSub);
        int apps = GkdStore.enabledApps(this).size();
        hitStatus.setText("启用应用 " + apps + " 个 · 累计命中 " + GkdAccessibilityService.hitCount + " 次"
                + (GkdStore.subRaw(this).isEmpty() ? "\n⚠ 还没下载订阅，规则库为空" : ""));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(cardBg); g.setCornerRadius(dp(12));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView bigBtn(String s) {
        TextView t = txt(s, 14, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(blue); g.setCornerRadius(dp(10));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        t.setLayoutParams(lp);
        t.setPadding(0, dp(10), 0, dp(10));
        return t;
    }

    private TextView txt(String s, int sz, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
