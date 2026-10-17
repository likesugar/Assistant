package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** 跳过广告管理页 (参考 GuoXiCheng/SKIP 交互): 进入页面 → 页内确认打开无障碍开关 */
public class SkipAdActivity extends Activity {
    private Switch sw;
    private TextView status, tvOn;

    private boolean enabled() {
        try {
            String e = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return e != null && (e.contains(getPackageName() + "/.SkipAdService")
                    || e.contains("com.ziyuan.Assistant.SkipAdService"));
        } catch (Throwable t) { return false; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        getWindow().setNavigationBarColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        getWindow().setStatusBarColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        if (BuildCheck.is29()) getWindow().setNavigationBarContrastEnforced(false);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(dark ? 0xFF000000 : 0xFFEEF4FF));

        int fg = dark ? 0xFFFFFFFF : 0xFF1F2329;
        int sub = dark ? 0xFF9AA3AE : 0xFF8A94A6;
        int dp = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp, dp, dp, dp);

        TextView back = new TextView(this);
        back.setText("←  返回");
        back.setTextSize(14); back.setTextColor(sub);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = new TextView(this);
        title.setText("跳过广告");
        title.setTextSize(24); title.setTextColor(fg); title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp, 0, 0);
        root.addView(title);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp;
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView lab = new TextView(this);
        lab.setText("无障碍开关");
        lab.setTextSize(16); lab.setTextColor(fg); lab.setTypeface(null, Typeface.BOLD);
        lab.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(lab);
        sw = new Switch(this);
        sw.setClickable(false); // 状态只能去系统设置改, 整卡点击引导过去
        row.addView(sw);
        card.setOnClickListener(v -> {
            if (!enabled()) {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (Throwable ignored) { }
            }
        });
        card.addView(row);

        status = new TextView(this);
        status.setTextSize(12);
        status.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
        card.addView(status);

        TextView tip = new TextView(this);
        tip.setText("开启后自动点击「跳过 / 跳過 / 关闭广告 / Skip」按钮，"
                + "支持「跳过 3」等带倒计时的按钮；点击失败时自动改用坐标手势点击。"
                + "关闭本页不影响服务运行，可在系统无障碍设置中关闭。");
        tip.setTextSize(12); tip.setTextColor(sub);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        tip.setLayoutParams(tlp);
        card.addView(tip);

        root.addView(card);
        setContentView(root);
        refresh();
    }

    private void refresh() {
        boolean on = enabled();
        sw.setChecked(on);
        if (on) {
            status.setText("● 已开启，运行中");
            status.setTextColor(0xFF34C759);
        } else {
            status.setText("○ 未开启，点上方卡片去系统无障碍设置打开");
            status.setTextColor(0xFFF5A623);
        }
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    private static class BuildCheck { static boolean is29() { return android.os.Build.VERSION.SDK_INT >= 29; } }
}
