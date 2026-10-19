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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.graphics.drawable.Drawable;

/** GKD(去广告) 复刻页 — 四Tab: 仪表盘/应用列表/订阅/设置 (源: gkd-kit/gkd) */
public class GkdActivity extends Activity {
    private static final int[] TABS = {0, 1, 2, 3};
    private static final String[] TAB_NAMES = {"首页", "订阅", "应用", "设置"};
    private static final String[] TAB_ICONS = {"🏠", "☰", "▦", "⚙"};
    private LinearLayout[] pages = new LinearLayout[4];
    private LinearLayout[] tabItems = new LinearLayout[4];
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

        // 底部Tab栏 1:1: 图标+文字+选中胶囊
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(cardBg);
        tabs.setPadding(0, dp(6), 0, dp(6));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView ic = txt(TAB_ICONS[i], 17, txtSub, false);
            ic.setGravity(Gravity.CENTER);
            TextView lb = txt(TAB_NAMES[i], 11, txtSub, false);
            item.addView(ic); item.addView(lb);
            item.setPadding(dp(20), dp(4), dp(20), dp(4));
            item.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            item.setOnClickListener(v -> switchTab(idx));
            tabs.addView(item);
            tabItems[i] = item;
        }
        root.addView(tabs, -1, dp(60));
        setContentView(root);

        for (int i = 0; i < 4; i++) {
            pages[i] = new LinearLayout(this);
            pages[i].setOrientation(LinearLayout.VERTICAL);
            body.addView(pages[i], new LinearLayout.LayoutParams(-1, -1));
        }
        buildDashboard(); buildAppList(); buildSubs(); buildSettings();
        switchTab(0);
    }

    // ===== Tab1 首页(仪表盘) 1:1 图1 =====
    private TextView a11yStatus, statLine, recentLine;
    private TextView buildDashCard(LinearLayout p, String icon, String title, String sub) {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView iv = txt(icon, 18, 0xFFFFFFFF, false);
        iv.setGravity(Gravity.CENTER);
        GradientDrawable ig = new GradientDrawable();
        ig.setColor(0xFF1E6FA8); ig.setShape(GradientDrawable.OVAL);
        iv.setBackground(ig);
        head.addView(iv, dp(44), dp(44));
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        TextView tv = txt(title, 16, txtMain, true); tv.setPadding(dp(14), 0, 0, 0);
        TextView sv = txt(sub, 13, txtSub, false); sv.setPadding(dp(14), dp(2), 0, 0);
        hl.addView(tv); hl.addView(sv);
        head.addView(hl, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = txt("›", 20, txtSub, false);
        head.addView(arrow);
        c.addView(head);
        p.addView(c);
        return sv;
    }
    private void buildDashboard() {
        LinearLayout p = pages[0];
        // 无障碍卡(官方 Privilege 卡位)
        LinearLayout c0 = card();
        a11yStatus = txt("", 13, txtSub, false);
        LinearLayout h0 = new LinearLayout(this);
        h0.setOrientation(LinearLayout.HORIZONTAL); h0.setGravity(Gravity.CENTER_VERTICAL);
        TextView i0 = txt("♿", 18, 0xFFFFFFFF, false); i0.setGravity(Gravity.CENTER);
        GradientDrawable g0 = new GradientDrawable(); g0.setColor(0xFF1E6FA8); g0.setShape(GradientDrawable.OVAL);
        i0.setBackground(g0);
        h0.addView(i0, dp(44), dp(44));
        LinearLayout l0 = new LinearLayout(this); l0.setOrientation(LinearLayout.VERTICAL);
        TextView tt0 = txt("无障碍权限", 16, txtMain, true); tt0.setPadding(dp(14), 0, 0, 0);
        a11yStatus.setPadding(dp(14), dp(2), 0, 0);
        l0.addView(tt0); l0.addView(a11yStatus);
        h0.addView(l0, new LinearLayout.LayoutParams(0, -2, 1f));
        c0.addView(h0);
        TextView btn = bigBtn("去开启无障碍");
        btn.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Throwable t) { Toast.makeText(this, "打不开设置", Toast.LENGTH_SHORT).show(); }
        });
        c0.addView(btn);
        p.addView(c0);

        // 触发记录卡 1:1: 卡内列真实条目(最近3条), 点击弹全量
        LinearLayout pr = new LinearLayout(this); pr.setOrientation(LinearLayout.VERTICAL);
        TextView rs = buildDashCard(pr, "🕘", "触发记录", "规则误触可定位关闭");
        rs.setTextColor(blue);
        triggerBody = txt("", 12, txtSub, false);
        triggerBody.setPadding(dp(18), dp(8), dp(6), 0);
        pr.addView(triggerBody);
        pr.setOnClickListener(v -> {
            String logs = GkdStore.logs(this);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("触发记录")
                    .setMessage(logs.isEmpty() ? "暂无触发记录" : logs)
                    .setPositiveButton("清空", (d, w) -> { GkdStore.clearLogs(this); refreshDashboard(); })
                    .setNegativeButton("关闭", null).show();
        });
        p.addView(pr, new LinearLayout.LayoutParams(-1, -2));
        refreshDashboard();
    }
    private TextView triggerBody;

    private void refreshDashboard() {
        // 异步统计, 避免阻塞 UI
        final LinearLayout p = pages[0];
        new Thread(() -> {
            final int[] st = GkdStore.stats(this);
            final String subName = GkdStore.subName(this);
            final String logs = GkdStore.logs(this);
            final String recent = logs.isEmpty() ? "暂无触发" : logs.split("\n")[0];
            runOnUiThread(() -> {
                // 触发记录卡内条目
                if (triggerBody != null) {
                    String[] ls = logs.isEmpty() ? new String[0] : logs.split("\n");
                    StringBuilder tb = new StringBuilder();
                    for (int i = 0; i < ls.length && i < 3; i++) tb.append("· ").append(ls[i]).append(i < 2 && i < ls.length - 1 ? "\n" : "");
                    triggerBody.setText(tb.length() == 0 ? "· 暂无触发" : tb.toString());
                    triggerBody.setTextColor(ls.length == 0 ? txtSub : txtMain);
                }
                // 移除旧概览卡重建
                for (int i = p.getChildCount() - 1; i >= 0; i--) {
                    View ch = p.getChildAt(i);
                    if (ch.getTag() != null && "ov".equals(ch.getTag())) p.removeView(ch);
                }
                LinearLayout ov = new LinearLayout(this);
                ov.setTag("ov");
                ov.setOrientation(LinearLayout.VERTICAL);
                LinearLayout c = card();
                LinearLayout head = new LinearLayout(this);
                head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
                TextView iv = txt("📊", 18, 0xFFFFFFFF, false); iv.setGravity(Gravity.CENTER);
                GradientDrawable ig = new GradientDrawable(); ig.setColor(0xFF1E6FA8); ig.setShape(GradientDrawable.OVAL);
                iv.setBackground(ig);
                head.addView(iv, dp(44), dp(44));
                LinearLayout hl = new LinearLayout(this); hl.setOrientation(LinearLayout.VERTICAL);
                TextView tv = txt("数据概览", 16, txtMain, true); tv.setPadding(dp(14), 0, 0, 0);
                TextView sv = txt("已开启 " + (GkdStore.subRaw(this).isEmpty() ? 0 : 1) + " 条订阅", 13, txtSub, false);
                sv.setPadding(dp(14), dp(2), 0, 0);
                hl.addView(tv); hl.addView(sv);
                head.addView(hl, new LinearLayout.LayoutParams(0, -2, 1f));
                head.addView(txt("›", 20, txtSub, false));
                c.addView(head);
                TextView line1 = txt("📊 " + st[0] + "全局/" + st[1] + "应用/" + st[2] + "规则/" + st[3] + "触发", 14, txtMain, true);
                line1.setPadding(dp(4), dp(10), 0, 0);
                c.addView(line1);
                if (subName != null) {
                    TextView line0 = txt("订阅: " + subName + " · " + (GkdStore.subUrl(this).contains("gkd-kit/subscription") ? "官方源" : "自定义源"), 12, txtSub, false);
                    line0.setPadding(dp(4), dp(6), 0, 0);
                    line0.setMaxLines(1); line0.setEllipsize(TextUtils.TruncateAt.END);
                    c.addView(line0);
                }
                TextView line2 = txt("最近触发: " + recent, 13, blue, true);
                line2.setPadding(dp(4), dp(4), 0, 0);
                line2.setMaxLines(2); line2.setEllipsize(TextUtils.TruncateAt.END);
                c.addView(line2);
                ov.addView(c);
                p.addView(ov, 1);
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshA11y();
        refreshDashboard();
    }

    private void refreshA11y() {
        if (a11yStatus == null) return;
        boolean on = GkdAccessibilityService.running;
        a11yStatus.setText(on ? "✓ 服务运行中 · 引擎已就绪" : "未开启 · 开启后规则引擎才能生效");
        a11yStatus.setTextColor(on ? green : txtSub);
    }

    // ===== Tab 应用 1:1 图2 =====
    private java.util.List<android.content.pm.ApplicationInfo> appInfos;
    private java.util.List<android.content.pm.ApplicationInfo> shown;
    private Map<String, int[]> infoMap;
    private ListView lv;
    private String query = "";
    private boolean onlySub = false;
    private void buildAppList() {
        LinearLayout p = pages[2];
        // 顶栏: 应用 + 筛选
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = txt("应用", 22, txtMain, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView fBtn = txt(onlySub ? "◧" : "▤", 18, onlySub ? blue : txtSub, false);
        fBtn.setPadding(dp(12), 0, dp(12), 0);
        fBtn.setOnClickListener(v -> { onlySub = !onlySub; applyFilter(); });
        top.addView(fBtn);
        TextView sBtn = txt("🔍", 16, txtSub, false);
        sBtn.setPadding(dp(12), 0, dp(4), 0);
        sBtn.setOnClickListener(v -> {
            android.widget.EditText et = new android.widget.EditText(this);
            et.setHint("搜索应用名/包名");
            et.setText(query);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("搜索")
                    .setView(et)
                    .setPositiveButton("确定", (d, w) -> { query = et.getText().toString().trim(); applyFilter(); })
                    .setNegativeButton("清除", (d, w) -> { query = ""; applyFilter(); })
                    .show();
        });
        top.addView(sBtn);
        top.setPadding(dp(6), dp(4), dp(6), dp(8));
        p.addView(top);
        appInfos = new ArrayList<>();
        try {
            for (android.content.pm.ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                if (getPackageManager().getLaunchIntentForPackage(ai.packageName) != null) appInfos.add(ai);
            }
        } catch (Throwable ignored) {}
        shown = new ArrayList<>(appInfos);
        lv = new ListView(this);
        lv.setDivider(null);
        final Map<String, android.graphics.drawable.Drawable> iconCache = new HashMap<>();
        lv.setAdapter(new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) {
                android.content.pm.ApplicationInfo ai = shown.get(i);
                LinearLayout row;
                if (cv instanceof LinearLayout) { row = (LinearLayout) cv; }
                else {
                    row = new LinearLayout(GkdActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(10), dp(8), dp(10), dp(8));
                    android.widget.ImageView ic = new android.widget.ImageView(GkdActivity.this);
                    ic.setId(101);
                    GradientDrawable clip = new GradientDrawable();
                    clip.setCornerRadius(dp(12));
                    ic.setClipToOutline(true);
                    ic.setBackground(clip);
                    row.addView(ic, dp(46), dp(46));
                    LinearLayout left = new LinearLayout(GkdActivity.this);
                    left.setId(102);
                    left.setOrientation(LinearLayout.VERTICAL);
                    row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView st = new TextView(GkdActivity.this);
                    st.setId(103);
                    st.setTextSize(16);
                    st.setPadding(dp(8), 0, dp(8), 0);
                    row.addView(st);
                }
                android.widget.ImageView ic = row.findViewById(101);
                Drawable dw = iconCache.get(ai.packageName);
                if (dw == null) {
                    try { dw = getPackageManager().getApplicationIcon(ai.packageName); } catch (Throwable t) { dw = getResources().getDrawable(android.R.drawable.sym_def_app_icon); }
                    iconCache.put(ai.packageName, dw);
                }
                ic.setImageDrawable(dw);
                LinearLayout left = row.findViewById(102);
                TextView name = (TextView) left.getChildAt(0);
                TextView pk = (TextView) left.getChildAt(1);
                if (name == null) {
                    name = txt("", 15, txtMain, true); name.setPadding(dp(12), 0, 0, 0); left.addView(name);
                    pk = txt("", 12, txtSub, false); pk.setPadding(dp(12), dp(1), 0, 0); left.addView(pk);
                }
                name.setText(getPackageManager().getApplicationLabel(ai).toString());
                int[] info = infoMap != null ? infoMap.get(ai.packageName) : null;
                String sub;
                if (info == null) sub = "无订阅规则";
                else {
                    sub = info[0] + "全局/" + info[1] + "组规则";
                    if (info[1] > 0 && (info[2] != info[1])) sub += "/" + info[2] + "启用/" + info[3] + "关闭";
                }
                boolean on = GkdStore.isAppEnabled(GkdActivity.this, ai.packageName);
                if (on) sub += " · 已启用";
                pk.setText(sub);
                TextView st = row.findViewById(103);
                st.setTextColor(on ? green : txtSub);
                st.setText(on ? "✓" : "⃠");
                row.setOnClickListener(vv -> {
                    Intent ri = new Intent(GkdActivity.this, GkdAppRulesActivity.class);
                    ri.putExtra("pkg", ai.packageName);
                    ri.putExtra("name", getPackageManager().getApplicationLabel(ai).toString());
                    startActivity(ri);
                });
                return row;
            }
        });
        p.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void applyFilter() {
        shown = new ArrayList<>();
        final String q = query.toLowerCase();
        final boolean os = onlySub;
        new Thread(() -> {
            Map<String, int[]> im = new HashMap<>();
            java.util.List<android.content.pm.ApplicationInfo> out = new ArrayList<>();
            for (android.content.pm.ApplicationInfo ai : appInfos) {
                int[] info = GkdStore.appGroupInfo(this, ai.packageName);
                if (info != null) im.put(ai.packageName, info);
                if (os && info == null) continue;
                if (q.length() > 0) {
                    String n = getPackageManager().getApplicationLabel(ai).toString().toLowerCase();
                    if (!n.contains(q) && !ai.packageName.toLowerCase().contains(q)) continue;
                }
                out.add(ai);
            }
            final java.util.List<android.content.pm.ApplicationInfo> fout = out;
            runOnUiThread(() -> {
                infoMap = im;
                shown = fout;
                if (lv != null) ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
            });
        }).start();
    }

    // ===== Tab3 订阅 =====
    private EditText subInput;
    private TextView subStatus;
    private String raw = "";
    private void buildSubs() {
        LinearLayout p = pages[1];
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
            org.json.JSONArray as = root.optJSONArray("apps");
            org.json.JSONArray gl = root.optJSONArray("globalGroups");
            int na = as != null ? as.length() : 0;
            int ng = gl != null ? gl.length() : 0;
            int total = 0;
            if (as != null) for (int i = 0; i < as.length(); i++) {
                JSONObject a = as.optJSONObject(i);
                org.json.JSONArray ag = a == null ? null : a.optJSONArray("groups");
                if (ag != null) total += ag.length();
            }
            info += "\n覆盖应用 " + na + " · 应用规则组 " + total + " · 全局组 " + ng
                    + "\n订阅名: " + root.optString("name", "-")
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
            LinearLayout it = tabItems[i];
            GradientDrawable cap = new GradientDrawable();
            cap.setColor(i == idx ? 0x338090A0 : 0x00000000);
            cap.setCornerRadius(dp(18));
            it.setBackground(cap);
            TextView lb = (TextView) it.getChildAt(1);
            lb.setTextColor(i == idx ? txtMain : txtSub);
            lb.setTypeface(null, i == idx ? Typeface.BOLD : Typeface.NORMAL);
            ((TextView) it.getChildAt(0)).setTextColor(i == idx ? blue : txtSub);
        }
        if (idx == 0) refreshDashboard();
    }

    private void refreshDash() { refreshA11y(); }

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
