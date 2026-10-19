package com.ziyuan.Assistant;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** GKD 官方触发记录页 1:1: 应用分组 + 蓝竖线 + 毫秒时间 + Activity + 订阅名/版本徽标 + 规则名 key/index */
public class GkdLogActivity extends Activity {
    private LinearLayout list;
    private boolean dark;
    private int bg, cardBg, txtMain, txtSub, blue = 0xFF3B82F6;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        bg = dark ? 0xFF121417 : 0xFFF6F7F9;
        cardBg = dark ? 0xFF1E2228 : 0xFFFFFFFF;
        txtMain = dark ? 0xFFE8EAED : 0xFF1A1C1E;
        txtSub = dark ? 0xFF9AA0A6 : 0xFF5F6368;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(14), dp(14), dp(14));
        TextView back = txt("←", 20, txtMain, false);
        back.setPadding(dp(8), 0, dp(12), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        top.addView(txt("触发记录", 18, txtMain, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clr = txt("🗑", 18, txtSub, false);
        clr.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setMessage("清空全部触发记录?")
                .setPositiveButton("清空", (d, w) -> { GkdStore.clearLogV2(this); rebuild(); })
                .setNegativeButton("取消", null).show());
        top.addView(clr);
        root.addView(top, -1, dp(56));
        ScrollView sc = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(20));
        sc.addView(list, new LinearLayout.LayoutParams(-1, -2));
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        rebuild();
    }

    private void rebuild() {
        list.removeAllViews();
        JSONArray arr = GkdStore.logV2(this);
        if (arr.length() == 0) {
            TextView e = txt("暂无触发记录", 14, txtMain, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(80), 0, 0);
            list.addView(e);
            return;
        }
        // 按 pkg 分组 (保序)
        Map<String, List<JSONObject>> byPkg = new LinkedHashMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            String k = e.optString("pkg");
            List<JSONObject> l = byPkg.get(k);
            if (l == null) { l = new ArrayList<>(); byPkg.put(k, l); }
            l.add(e);
        }
        for (Map.Entry<String, List<JSONObject>> en : byPkg.entrySet()) {
            String pkg = en.getKey();
            String appName = pkg;
            try { appName = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); } catch (Throwable ignored) {}
            // 组头: 蓝点 + 应用名 + ›
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(2), dp(10), 0, dp(6));
            TextView dot = txt("•", 18, blue, true);
            head.addView(dot);
            TextView hn = txt(appName, 17, blue, true);
            hn.setPadding(dp(6), 0, 0, 0);
            head.addView(hn, new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(txt("›", 18, txtSub, false));
            list.addView(head);
            // 条目: 左蓝竖线 + [时间 / Activity / 订阅名 v徽标 / 规则名 key=0,index=0]
            for (JSONObject e : en.getValue()) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(6), 0, 0, dp(6));
                View bar = new View(this);
                GradientDrawable bg2 = new GradientDrawable();
                bg2.setColor(blue);
                bar.setBackground(bg2);
                row.addView(bar, dp(3), -1);
                LinearLayout body = new LinearLayout(this);
                body.setOrientation(LinearLayout.VERTICAL);
                body.setPadding(dp(10), 0, 0, 0);
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm:ss SSS", java.util.Locale.CHINA);
                body.addView(txt(f.format(new java.util.Date(e.optLong("t"))), 13, txtSub, false));
                if (e.has("act")) {
                    TextView ac = txt(e.optString("act"), 15, txtMain, true);
                    ac.setMaxLines(1); ac.setEllipsize(TextUtils.TruncateAt.START);
                    body.addView(ac);
                }
                // 订阅名 + 版本徽标 (同一行)
                LinearLayout sl = new LinearLayout(this);
                sl.setOrientation(LinearLayout.HORIZONTAL);
                sl.setGravity(Gravity.CENTER_VERTICAL);
                String sub = e.optString("sub", "");
                TextView stv = txt(sub, 13, txtMain, false);
                sl.addView(stv);
                if (e.optInt("ver", 0) > 0) {
                    TextView badge = txt(" v" + e.optInt("ver"), 11, 0xFFE8EAED, false);
                    badge.setPadding(dp(6), dp(1), dp(6), dp(1));
                    GradientDrawable bgd = new GradientDrawable();
                    bgd.setColor(0xFF5B4B8A); bgd.setCornerRadius(dp(6));
                    badge.setBackground(bgd);
                    sl.addView(badge);
                }
                body.addView(sl);
                // 规则行: ⊕(全局)/⚙(应用) + 名称 + key=0, index=0
                String icon = e.optBoolean("gl") ? "⊕" : "⚙";
                TextView rl = txt(icon + " " + e.optString("grp", "规则") + "   key=" + e.optInt("key", 0) + ", index=0", 13, txtMain, false);
                rl.setPadding(0, dp(1), 0, 0);
                body.addView(rl);
                row.addView(body, new LinearLayout.LayoutParams(0, -2, 1f));
                list.addView(row);
            }
        }
    }

    private TextView txt(String s, int sz, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
