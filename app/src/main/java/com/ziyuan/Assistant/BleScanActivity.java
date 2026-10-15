package com.ziyuan.Assistant;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** 连接设备页 (照 luotianyi-running-app): 扫描列表 + 连接 + 重新扫描 */
public class BleScanActivity extends Activity {
    private LinearLayout list;
    private TextView status;
    private final Handler h = new Handler();
    private HeartRateManager hr;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        java.util.List<String> ps = new java.util.ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != 0) ps.add("android.permission.BLUETOOTH_SCAN");
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != 0) ps.add("android.permission.BLUETOOTH_CONNECT");
        } else if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != 0) {
            ps.add("android.permission.ACCESS_FINE_LOCATION");
        }
        if (!ps.isEmpty()) requestPermissions(ps.toArray(new String[0]), 1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pd = dp(20);
        root.setPadding(pd, dp(12), pd, dp(40));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("连接设备");
        title.setTextSize(24);
        title.setTag("title");
        TextView back = new TextView(this);
        back.setText("← 返回");
        back.setTextSize(15);
        back.setTag("accent");
        back.setOnClickListener(v -> finish());
        top.addView(title);
        TextView sp = new TextView(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(back);
        root.addView(top);

        status = new TextView(this);
        status.setText("请开启手环/手表的「心率广播」并靠近手机");
        status.setTextSize(14);
        status.setTag("sub");
        status.setPadding(0, dp(18), 0, dp(10));
        root.addView(status);

        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        root.addView(sc);

        TextView rescan = new TextView(this);
        rescan.setText("重新扫描");
        rescan.setTextSize(17);
        rescan.setTextColor(0xFFFFFFFF);
        rescan.setGravity(Gravity.CENTER);
        rescan.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        rescan.setBackground(round(0xFFF57C2E, dp(28)));
        rescan.setOnClickListener(v -> doScan());
        LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) rescan.getLayoutParams();
        rlp.topMargin = dp(16);
        rescan.setLayoutParams(rlp);
        root.addView(rescan);

        setContentView(root);
        applyTheme(root);

        hr = HeartRateManager.get(this);
        hr.setListener(bpm -> runOnUiThread(() -> {
            Toast.makeText(this, "心率已连接: " + bpm, Toast.LENGTH_SHORT).show();
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("metroHrAddr", hr.connectedAddr).apply();
            setResult(RESULT_OK);
            finish();
        }));
        doScan();
    }

    private void doScan() {
        list.removeAllViews();
        status.setText("正在扫描附近设备...");
        hr.scanAll(devices -> runOnUiThread(() -> render(devices)));
        h.postDelayed(() -> {
            if (list.getChildCount() == 0) status.setText("没扫到设备, 请开启「心率广播」后点重新扫描");
        }, 8000);
    }

    private void render(List<BluetoothDevice> devices) {
        status.setText("正在扫描附近设备... (" + devices.size() + ")");
        list.removeAllViews();
        for (final BluetoothDevice d : devices) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(round(0xFF1E2430, dp(16)));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(12);
            card.setLayoutParams(clp);

            String name = d.getName();
            String shown = (name == null || name.isEmpty())
                ? "未命名设备 " + d.getAddress().substring(d.getAddress().length() - 4).replace(":", ":")
                : name;
            TextView n = new TextView(this);
            n.setText(shown);
            n.setTextSize(16);
            n.setTextColor(0xFFE8ECF4);
            n.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView conn = new TextView(this);
            conn.setText("连接");
            conn.setTextSize(15);
            conn.setTextColor(0xFF4FA3E3);
            conn.setPadding(dp(12), dp(6), 0, dp(6));
            conn.setOnClickListener(v -> {
                status.setText("连接中: " + shown);
                if (!hr.connect(d.getAddress())) {
                    Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show();
                }
            });
            card.addView(n);
            card.addView(conn);
            list.addView(card);
        }
    }

    private android.graphics.drawable.GradientDrawable round(int color, float radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private boolean dark() { return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false); }

    private void applyTheme(ViewGroup vg) {
        int bg = dark() ? 0xFF0B0E13 : 0xFFF6F7FB;
        vg.setBackgroundColor(bg);
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c.getTag() instanceof String) {
                String t = (String) c.getTag();
                if ("title".equals(t)) ((TextView) c).setTextColor(0xFFE8ECF4);
                else if ("sub".equals(t)) ((TextView) c).setTextColor(0xFF9AA3B2);
                else if ("accent".equals(t)) ((TextView) c).setTextColor(0xFFE8C87A);
            }
            if (c instanceof ViewGroup && c.getBackground() == null) applyTheme((ViewGroup) c);
        }
    }

    private int dp(int v) { return (int) (getResources().getDisplayMetrics().density * v); }

    @Override
    protected void onDestroy() {
        hr.stopScan();
        super.onDestroy();
    }
}
