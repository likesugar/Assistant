package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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

        // ===== 应用名单大框 (多选, 勾选的才自动跳过; 不选=全部) =====
        TextView pickLab = new TextView(this);
        pickLab.setText("☐ 请勾选跳过广告的应用 (多选)");
        pickLab.setTextSize(14); pickLab.setTextColor(fg); pickLab.setTypeface(null, android.graphics.Typeface.BOLD);
        pickLab.setPadding(0, (int) (20 * getResources().getDisplayMetrics().density), 0, (int) (8 * getResources().getDisplayMetrics().density));
        root.addView(pickLab);

        java.util.Set<String> sel = new java.util.HashSet<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(
                    getSharedPreferences("settings", MODE_PRIVATE).getString("skipAdPkgs", "[]"));
            for (int i = 0; i < a.length(); i++) sel.add(a.getString(i));
        } catch (Throwable ignored) { }

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        java.util.List<android.content.pm.ResolveInfo> apps = new java.util.ArrayList<>();
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            Intent qi = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            apps = pm.queryIntentActivities(qi, android.content.pm.PackageManager.MATCH_ALL);
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.Iterator<android.content.pm.ResolveInfo> it = apps.iterator();
            while (it.hasNext()) {
                android.content.pm.ResolveInfo r = it.next();
                if (r.activityInfo.packageName.equals(getPackageName()) || !seen.add(r.activityInfo.packageName)) it.remove();
            }
        } catch (Throwable ignored) { }

        LinearLayout rowG = null;
        int col = 0;
        int gp = (int) (8 * getResources().getDisplayMetrics().density);
        for (final android.content.pm.ResolveInfo r : apps) {
            if (col % 2 == 0) {
                rowG = new LinearLayout(this);
                rowG.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = gp;
                grid.addView(rowG, rlp);
            }
            final String pkg = r.activityInfo.packageName;
            final boolean checked = sel.contains(pkg);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
            cell.setPadding(gp, gp, gp, gp);
            android.widget.ImageView ic = new android.widget.ImageView(this);
            try { ic.setImageDrawable(r.loadIcon(getPackageManager())); } catch (Throwable ignored) { }
            ic.setLayoutParams(new LinearLayout.LayoutParams((int) (30 * getResources().getDisplayMetrics().density),
                    (int) (30 * getResources().getDisplayMetrics().density)));
            cell.addView(ic);
            TextView name = new TextView(this);
            name.setText(String.valueOf(r.loadLabel(getPackageManager())));
            name.setTextSize(12); name.setTextColor(fg);
            name.setSingleLine(true);
            name.setPadding(gp, 0, 0, 0);
            cell.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setChecked(checked);
            cb.setOnCheckedChangeListener((b, v) -> {
                java.util.Set<String> cur = new java.util.HashSet<>();
                try {
                    org.json.JSONArray a = new org.json.JSONArray(
                            getSharedPreferences("settings", MODE_PRIVATE).getString("skipAdPkgs", "[]"));
                    for (int i = 0; i < a.length(); i++) cur.add(a.getString(i));
                } catch (Throwable ignored) { }
                if (v) cur.add(pkg); else cur.remove(pkg);
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putString("skipAdPkgs", new org.json.JSONArray(new java.util.ArrayList<>(cur)).toString()).apply();
            });
            cell.addView(cb);
            cell.setOnClickListener(v -> cb.toggle());
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            clp.rightMargin = col % 2 == 0 ? gp : 0;
            cell.setLayoutParams(clp);
            rowG.addView(cell);
            col++;
        }
        if (apps.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("未读取到应用列表");
            e.setTextSize(12); e.setTextColor(sub);
            grid.addView(e);
        }
        root.addView(grid);

        TextView tip = new TextView(this);
        tip.setText("开启后，打开含开屏广告的应用时会自动点击「跳过」按钮。");
        tip.setTextSize(12); tip.setTextColor(sub);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        tip.setLayoutParams(tlp);
        root.addView(tip);

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(root);
        setContentView(sc);
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
