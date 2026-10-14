package com.ziyuan.Assistant;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 完整日历页: 月视图 + 节假日 + 签到 + 提醒管理 */
public class CalendarActivity extends Activity {

    private CalendarCardView card;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        col.setPadding(pad, pad + (int) (24 * getResources().getDisplayMetrics().density), pad, pad);
        android.widget.ScrollView page = new android.widget.ScrollView(this);
        page.setFillViewport(true);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.addView(col, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹"); back.setTextSize(26);
        back.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        back.setPadding(0, 0, (int) (14 * getResources().getDisplayMetrics().density), 0);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); }});
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("日历");
        title.setTextSize(22); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head);

        LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        card = new CalendarCardView(this);
        card.setDark(dark);
        host.addView(card);
        col.addView(host, lp);

        setContentView(root);
        Immersive.hide(this);
        // 药物/日程到点通知: Android 13+ 需运行时授权
        if (android.os.Build.VERSION.SDK_INT >= 33
            && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 901);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}
