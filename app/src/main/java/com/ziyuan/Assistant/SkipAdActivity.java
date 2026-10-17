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

        TextView tip = new TextView(this);
        tip.setText("开启后，打开含开屏广告的应用时会自动点击「跳过」按钮。");
        tip.setTextSize(12); tip.setTextColor(sub);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        tip.setLayoutParams(tlp);
        root.addView(tip);

        setContentView(root);
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
}
