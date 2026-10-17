package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.EditText;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 跳过广告管理页 (1:1 移植 Giftedcat/AdSkipHelper MainActivity 交互) */
public class SkipAdActivity extends Activity {
    private TextView tvState;
    private LinearLayout rulesBoxP;

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

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.setPadding(0, (int) (30 * getResources().getDisplayMetrics().density), 0, 0);
        TextView lab = new TextView(this);
        lab.setText("服务当前状态：");
        lab.setTextSize(15); lab.setTextColor(fg);
        stateRow.addView(lab);
        tvState = new TextView(this);
        tvState.setText("off");
        tvState.setTextSize(15); tvState.setTextColor(fg); tvState.setTypeface(null, android.graphics.Typeface.BOLD);
        stateRow.addView(tvState, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView btnOpen = new TextView(this);
        btnOpen.setText("去开启");
        btnOpen.setTextSize(15); btnOpen.setTextColor(0xFF2F7CF6); btnOpen.setTypeface(null, android.graphics.Typeface.BOLD);
        btnOpen.setPadding((int) (16 * getResources().getDisplayMetrics().density), (int) (8 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density), (int) (8 * getResources().getDisplayMetrics().density));
        btnOpen.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
        btnOpen.setOnClickListener(v -> showOpenAccessibilityDialog());
        stateRow.addView(btnOpen);
        root.addView(stateRow);

        // ===== 应用名单 (手动添加包名, 添加了的才自动跳过; 不添加=全部) =====
        TextView pickLab = new TextView(this);
        pickLab.setText("☐ 跳过广告的应用名单 (自行添加)");
        pickLab.setTextSize(14); pickLab.setTextColor(fg); pickLab.setTypeface(null, android.graphics.Typeface.BOLD);
        pickLab.setPadding(0, (int) (20 * getResources().getDisplayMetrics().density), 0, (int) (8 * getResources().getDisplayMetrics().density));
        root.addView(pickLab);

        LinearLayout cardP = new LinearLayout(this);
        cardP.setOrientation(LinearLayout.VERTICAL);
        cardP.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
        int cp = (int) (14 * getResources().getDisplayMetrics().density);
        cardP.setPadding(cp, cp, cp, cp);
        rulesBoxP = new LinearLayout(this);
        rulesBoxP.setOrientation(LinearLayout.VERTICAL);
        cardP.addView(rulesBoxP);
        TextView btnAddPkg = new TextView(this);
        btnAddPkg.setText("＋ 添加应用 (输入包名)");
        btnAddPkg.setTextSize(14); btnAddPkg.setTextColor(0xFF2F7CF6); btnAddPkg.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAddPkg.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, 0);
        btnAddPkg.setOnClickListener(v -> showAddPkgDialog());
        cardP.addView(btnAddPkg);
        root.addView(cardP);

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(root);
        refreshPkgList();
        setContentView(sc);
    }

    private void showAddPkgDialog() {
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        final EditText et = new EditText(this);
        et.setHint("应用包名 (如 com.mfcloudcalculate.networkdisk)");
        if (dark) { et.setTextColor(0xFFF2F2F2); et.setHintTextColor(0xFF777777); et.setBackgroundResource(R.drawable.bg_input_dark); }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, 0);
        box.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("添加应用")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String pkg = et.getText().toString().trim();
                    if (pkg.isEmpty()) return;
                    try {
                        org.json.JSONArray a = new org.json.JSONArray(
                                getSharedPreferences("settings", MODE_PRIVATE).getString("skipAdPkgs", "[]"));
                        for (int i = 0; i < a.length(); i++) if (pkg.equals(a.getString(i))) return;
                        a.put(pkg);
                        getSharedPreferences("settings", MODE_PRIVATE)
                                .edit().putString("skipAdPkgs", a.toString()).apply();
                        refreshPkgList();
                    } catch (Throwable ignored) { }
                })
                .setNegativeButton("取消", null).show();
    }

    private void refreshPkgList() {
        rulesBoxP.removeAllViews();
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        try {
            org.json.JSONArray a = new org.json.JSONArray(
                    getSharedPreferences("settings", MODE_PRIVATE).getString("skipAdPkgs", "[]"));
            for (int i = 0; i < a.length(); i++) {
                final int idx = i;
                final String pkg = a.getString(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
                TextView t = new TextView(this);
                t.setText(pkg); t.setTextSize(13); t.setTextColor(dark ? 0xFFE8ECF4 : 0xFF1F2329);
                t.setSingleLine(true);
                row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView del = new TextView(this);
                del.setText("删除"); del.setTextSize(13); del.setTextColor(0xFFFF3B30); del.setTypeface(null, android.graphics.Typeface.BOLD);
                del.setPadding((int) (12 * getResources().getDisplayMetrics().density), 0, 0, 0);
                del.setOnClickListener(v -> {
                    try {
                        org.json.JSONArray na = new org.json.JSONArray();
                        for (int j = 0; j < a.length(); j++) if (j != idx) na.put(a.getString(j));
                        getSharedPreferences("settings", MODE_PRIVATE)
                                .edit().putString("skipAdPkgs", na.toString()).apply();
                        refreshPkgList();
                    } catch (Throwable ignored) { }
                });
                row.addView(del);
                rulesBoxP.addView(row);
            }
            if (a.length() == 0) {
                TextView e = new TextView(this);
                e.setText("未添加。不添加 = 所有应用都自动跳过");
                e.setTextSize(12); e.setTextColor(0xFF8A94A6);
                e.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
                rulesBoxP.addView(e);
            }
        } catch (Throwable ignored) { }
    }

    private void showCfgDialog() {
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        final EditText et = new EditText(this);
        et.setMinLines(6);
        et.setText(getSharedPreferences("settings", MODE_PRIVATE).getString("skipAdCfg", ""));
        if (dark) { et.setTextColor(0xFFF2F2F2); et.setHintTextColor(0xFF777777); et.setBackgroundResource(R.drawable.bg_input_dark); }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, 0);
        box.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("自定义配置 (URL / JSON / 李跳跳规则)")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    getSharedPreferences("settings", MODE_PRIVATE)
                            .edit().putString("skipAdCfg", et.getText().toString().trim()).apply();
                    recreate();
                })
                .setNegativeButton("取消", null).show();
    }

    /** 打开无障碍权限提示 (AdSkipHelper 同款) */
    private void showOpenAccessibilityDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("依次点击：无障碍 -> 已下载应用 -> Assistant")
                .setPositiveButton("取消", null)
                .setNeutralButton("好的", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Intent intent_abs = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent_abs.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent_abs);
                    }
                })
                .create();
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvState.setText(AdSkipService.isServiceRunning() ? "on" : "off");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) tvState.setText(AdSkipService.isServiceRunning() ? "on" : "off");
    }
}
