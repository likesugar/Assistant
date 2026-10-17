package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** 跳过广告管理页 (参考 GuoXiCheng/SKIP): 无障碍开关确认 + 自定义规则 + 严格模式 + 布局检查 */
public class SkipAdActivity extends Activity {
    private Switch sw;
    private TextView status, tvSvc;
    private LinearLayout rulesBox;

    private boolean enabled() { return HomeActivity.skipAdEnabled(this); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        getWindow().setNavigationBarColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        getWindow().setStatusBarColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        if (android.os.Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
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
        title.setPadding(0, dp, 0, dp);
        root.addView(title);

        // ===== 无障碍开关卡 =====
        LinearLayout card = card(dark);
        TextView lab = new TextView(this);
        lab.setText("无障碍开关"); lab.setTextSize(16); lab.setTextColor(fg); lab.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(lab, llp);
        sw = new Switch(this);
        sw.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Throwable ignored) { }
        });
        row.addView(sw);
        card.addView(row);
        status = new TextView(this);
        status.setTextSize(12);
        status.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
        card.addView(status);
        tvSvc = new TextView(this);
        tvSvc.setTextSize(11); tvSvc.setTextColor(sub);
        tvSvc.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);
        card.addView(tvSvc);
        TextView tip = new TextView(this);
        tip.setText("开启后自动点击「跳过 / 跳過 / 关闭广告 / Skip」按钮，支持「跳过 3」等带倒计时按钮；点击失败自动改用坐标手势。");
        tip.setTextSize(12); tip.setTextColor(sub);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        tip.setLayoutParams(tlp);
        card.addView(tip);
        root.addView(card);

        // ===== 严格模式 =====
        LinearLayout cardS = card(dark);
        cardS.addView(rowSwitch("严格模式", "只匹配精确的「跳过 / Skip」类文本, 避免误点",
                getSharedPreferences("settings", MODE_PRIVATE).getBoolean("skipAdStrict", false),
                on -> getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("skipAdStrict", on).apply()));
        root.addView(cardS);

        // ===== 布局检查 =====
        LinearLayout cardC = card(dark);
        TextView c1 = new TextView(this);
        c1.setText("布局检查 (10 秒)"); c1.setTextSize(15); c1.setTextColor(fg); c1.setTypeface(null, Typeface.BOLD);
        cardC.addView(c1);
        TextView c2 = new TextView(this);
        c2.setText("点开始后 10 秒内切到广告页：候选按钮会画红框，点红框=直接跳过，长按红框=存为该应用的自定义规则");
        c2.setTextSize(12); c2.setTextColor(sub);
        c2.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, (int) (10 * getResources().getDisplayMetrics().density));
        cardC.addView(c2);
        TextView btnCk = new TextView(this);
        btnCk.setText("▶ 开始布局检查");
        btnCk.setTextSize(14); btnCk.setTextColor(0xFFFFFFFF);
        btnCk.setGravity(Gravity.CENTER);
        btnCk.setTypeface(null, Typeface.BOLD);
        btnCk.setBackgroundResource(R.drawable.bg_btn_md3);
        btnCk.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, (int) (10 * getResources().getDisplayMetrics().density));
        btnCk.setOnClickListener(v -> {
            if (!SkipAdService.isRunning()) {
                Toast(this, "请先开启无障碍开关");
                return;
            }
            SkipAdService.startCheck(10000);
            Toast(this, "10 秒内切到广告页, 红框即候选按钮");
        });
        cardC.addView(btnCk);
        root.addView(cardC);

        // ===== 自定义规则 =====
        LinearLayout cardR = card(dark);
        TextView r0 = new TextView(this);
        r0.setText("自定义规则"); r0.setTextSize(15); r0.setTextColor(fg); r0.setTypeface(null, Typeface.BOLD);
        cardR.addView(r0);
        rulesBox = new LinearLayout(this);
        rulesBox.setOrientation(LinearLayout.VERTICAL);
        cardR.addView(rulesBox);
        TextView btnAdd = new TextView(this);
        btnAdd.setText("＋ 添加规则 (包名 + 关键词)");
        btnAdd.setTextSize(14); btnAdd.setTextColor(0xFF2F7CF6); btnAdd.setTypeface(null, Typeface.BOLD);
        btnAdd.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, 0);
        btnAdd.setOnClickListener(v -> showAddRule());
        cardR.addView(btnAdd);
        root.addView(cardR);

        setContentView(root);
        refresh();
    }

    private LinearLayout card(boolean dark) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (14 * getResources().getDisplayMetrics().density);
        c.setLayoutParams(lp);
        return c;
    }

    private interface BoolCB { void accept(boolean v); }

    private View rowSwitch(String title, String sub, boolean on, BoolCB cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(title); t.setTextSize(15);
        t.setTextColor(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false) ? 0xFFFFFFFF : 0xFF1F2329);
        t.setTypeface(null, Typeface.BOLD);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch s = new Switch(this);
        s.setChecked(on);
        s.setOnCheckedChangeListener((b, v) -> cb.accept(v));
        row.addView(s);
        TextView d = new TextView(this);
        d.setText(sub); d.setTextSize(11);
        d.setTextColor(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false) ? 0xFF9AA3AE : 0xFF8A94A6);
        d.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, 0);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        wrap.addView(d);
        return wrap;
    }

    private void showAddRule() {
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, 0);
        final EditText etP = new EditText(this); etP.setHint("应用包名 (如 com.example.app)");
        final EditText etK = new EditText(this); etK.setHint("按钮文字关键词 (如 跳过广告)");
        if (dark) { etP.setTextColor(0xFFF2F2F2); etP.setHintTextColor(0xFF777777); etP.setBackgroundResource(R.drawable.bg_input_dark);
                    etK.setTextColor(0xFFF2F2F2); etK.setHintTextColor(0xFF777777); etK.setBackgroundResource(R.drawable.bg_input_dark); }
        box.addView(etP); box.addView(etK);
        new android.app.AlertDialog.Builder(this)
                .setTitle("添加自定义规则")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    SkipAdService.addRule(etP.getText().toString().trim(), etK.getText().toString().trim());
                    refresh();
                })
                .setNegativeButton("取消", null).show();
    }

    private void Toast(Activity a, String m) {
        android.widget.Toast.makeText(a, m, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void refresh() {
        boolean on = enabled();
        sw.setChecked(on);
        if (on) {
            status.setText("● 已开启，运行中");
            status.setTextColor(0xFF34C759);
        } else {
            status.setText("○ 未开启，点上方开关去系统无障碍设置打开");
            status.setTextColor(0xFFF5A623);
        }
        try {
            String raw = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            tvSvc.setText("检测到无障碍服务: " + (raw == null || raw.isEmpty() ? "无" : raw));
        } catch (Throwable t) { }
        rulesBox.removeAllViews();
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("settings", MODE_PRIVATE).getString("skipAdRules", "[]"));
            boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
            for (int i = 0; i < arr.length(); i++) {
                final int idx = i;
                final JSONObject o = arr.getJSONObject(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
                TextView t = new TextView(this);
                t.setText(o.optString("p") + " · 「" + o.optString("k") + "」");
                t.setTextSize(13); t.setTextColor(dark ? 0xFFE8ECF4 : 0xFF1F2329);
                row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView del = new TextView(this);
                del.setText("删除"); del.setTextSize(13); del.setTextColor(0xFFFF3B30); del.setTypeface(null, Typeface.BOLD);
                del.setPadding((int) (12 * getResources().getDisplayMetrics().density), 0, 0, 0);
                del.setOnClickListener(v -> {
                    try {
                        JSONArray na = new JSONArray();
                        for (int j = 0; j < arr.length(); j++) if (j != idx) na.put(arr.getJSONObject(j));
                        getSharedPreferences("settings", MODE_PRIVATE).edit()
                                .putString("skipAdRules", na.toString()).apply();
                        refresh();
                    } catch (Throwable ignored) { }
                });
                row.addView(del);
                rulesBox.addView(row);
            }
            if (arr.length() == 0) {
                TextView e = new TextView(this);
                e.setText("暂无自定义规则"); e.setTextSize(12); e.setTextColor(0xFF8A94A6);
                e.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
                rulesBox.addView(e);
            }
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refresh();
    }
}
