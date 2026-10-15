package com.ziyuan.Assistant;

import android.app.Activity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 节拍器设置 (Kr0oked 风格: 分区卡片, 去掉显示/关于, 换成我们的内容) */
public class MetroSettingsActivity extends Activity {
    private android.content.SharedPreferences prefs;
    private int timerMin = 0;
    private String audioKey = MetroConstant.AudioDefault;
    private MetroAudioManager mgr;

    private String displayName(String k) {
        if (MetroConstant.AudioDefault.equals(k)) return "默认";
        if (MetroConstant.AudioClap.equals(k)) return "拍手";
        if (MetroConstant.AudioPluck.equals(k)) return "弹拨";
        if (MetroConstant.AudioRisset.equals(k)) return "鼓点";
        if (MetroConstant.AudioSine.equals(k)) return "正弦";
        if (MetroConstant.AudioSquare.equals(k)) return "方波";
        return k;
    }

    private void showAudioDialog() {
        final java.util.List<String> ks = mgr.getAudioList();
        String[] names = new String[ks.size()];
        int cur = 0;
        for (int i = 0; i < ks.size(); i++) {
            names[i] = displayName(ks.get(i));
            if (ks.get(i).equals(audioKey)) cur = i;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("音色")
            .setSingleChoiceItems(names, cur, (d, w) -> {
                audioKey = ks.get(w);
                prefs.edit().putString("metroAudio", audioKey).apply();
                MetronomeActivity.notifySettingChanged(this);
                d.dismiss();
                recreate();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        timerMin = prefs.getInt("metroTimer", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(40));

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("← 返回");
        back.setTextSize(16);
        back.setTag("accent");
        back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(18);
        title.setTag("title");
        top.addView(back);
        TextView spacer = new TextView(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(title);
        root.addView(top);

        // ===== 节拍器 =====
        root.addView(sectionTitle("节拍器"));
        LinearLayout card1 = card();
        card1.addView(switchRow("增强第一个拍子", "每小节第一拍额外加重", prefs.getBoolean("metroAccent", false), (on) -> {
            prefs.edit().putBoolean("metroAccent", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        audioKey = prefs.getString("metroAudio", MetroConstant.AudioDefault);
        mgr = new MetroAudioManager(this);
        card1.addView(choiceRow("音色", displayName(audioKey), v -> showAudioDialog()));

        root.addView(card1);

        // ===== 我的 =====
        root.addView(sectionTitle("我的"));
        LinearLayout card2 = card();
        card2.addView(choiceRow("下载背景音", "粘贴音频地址, 存入节拍器背景音", v -> showDownloadDialog()));
        card2.addView(switchRow("屏幕常亮", "打拍时保持屏幕常亮", prefs.getBoolean("metroKeepScreen", false), (on) -> {
            prefs.edit().putBoolean("metroKeepScreen", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card2.addView(switchRow("音量增强", "所有拍子加大音量", prefs.getBoolean("metroBooster", false), (on) -> {
            prefs.edit().putBoolean("metroBooster", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card2.addView(choiceRow("定时器", timerMin > 0 ? timerMin + " 分钟后自动停止" : "不限制", v -> showTimerDialog()));
        root.addView(card2);

        // ===== 洛天依 =====
        root.addView(sectionTitle("洛天依"));
        LinearLayout card3 = card();
        card3.addView(choiceRow("连接设备", hrStatus(), v -> startActivity(new android.content.Intent(this, BleScanActivity.class))));
        card3.addView(choiceRow("年龄", "用于算最大心率 (220-年龄), 当前 " + prefs.getInt("metroAge", 30) + " 岁", v -> showAgeDialog()));
        card3.addView(switchRow("随机鼓励语 + 字幕", "每 35~55 秒随机一句鼓励, 顶部同步字幕", prefs.getBoolean("metroTianyiCheer", true), (on) -> {
            prefs.edit().putBoolean("metroTianyiCheer", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card3.addView(switchRow("阶段提示 + 打拍自动变速", "热身→燃脂→冲刺→放松, BPM 随阶段渐变", prefs.getBoolean("metroTianyiStage", true), (on) -> {
            prefs.edit().putBoolean("metroTianyiStage", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card3.addView(switchRow("心率跟随自动变速", "连手表/手环, 按心率区间自动调步频", prefs.getBoolean("metroTianyiFollow", false), (on) -> {
            if (on) requestPerms();
            prefs.edit().putBoolean("metroTianyiFollow", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        root.addView(card3);

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sc.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sc);
        applyTheme(root);
    }

    private boolean dark() { return prefs.getBoolean("dark", false); }
    private boolean oled() { return prefs.getBoolean("oled", true); }
    private int color(int d, int l) { return dark() ? d : l; }

    private void applyTheme(ViewGroup vg) {
        boolean dk = dark();
        int title = color(0xFFE8ECF4, 0xFF1F2329);
        int sub = color(0xFF9AA3B2, 0xFF6B7280);
        int accent = color(0xFFE8C87A, 0xFF7C4DFF);
        int card = dk ? (oled() ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3;
        int bg = dk ? (oled() ? 0xFF000000 : 0xFF14181F) : 0xFFF6F7FB;
        vg.setBackgroundColor(bg);
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            Object tag = c.getTag();
            if (tag instanceof String) {
                if ("title".equals(tag) && c instanceof TextView) ((TextView) c).setTextColor(title);
                else if ("sub".equals(tag) && c instanceof TextView) ((TextView) c).setTextColor(sub);
                else if ("accent".equals(tag) && c instanceof TextView) ((TextView) c).setTextColor(accent);
            }
            if (c instanceof LinearLayout) {
                if (c.getBackground() != null && c.getBackground().getConstantState() != null
                    && c.getBackground().getConstantState().toString().contains("bg_card")) c.setBackgroundResource(card);
                else applyTheme((ViewGroup) c);
            }
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView sectionTitle(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(14);
        tv.setTag("sub");
        tv.setPadding(dp(4), dp(18), dp(4), dp(8));
        return tv;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card_md3);
        c.setPadding(dp(16), dp(4), dp(16), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        c.setLayoutParams(lp);
        return c;
    }

    private interface OnSwitch { void on(boolean v); }

    private View switchRow(String title, String sub, boolean on, OnSwitch cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(16);
        t1.setTag("title");
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(12);
        t2.setTag("sub");
        texts.addView(t1);
        texts.addView(t2);
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, v) -> cb.on(v));
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.leftMargin = dp(12);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(sw, swLp);
        return row;
    }

    private View choiceRow(String title, String value, View.OnClickListener cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(16);
        t1.setTag("title");
        TextView t2 = new TextView(this);
        t2.setText(value);
        t2.setTextSize(12);
        t2.setTag("sub");
        row.addView(t1);
        row.addView(t2);
        row.setOnClickListener(cb);
        return row;
    }

    private void showDownloadDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        et.setHint("粘贴音频下载地址 (http...mp3/wav/ogg/m4a)");
        et.setTextSize(14);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        int pd = (int) (getResources().getDisplayMetrics().density * 20);
        wrap.setPadding(pd, pd / 2, pd, 0);
        wrap.addView(et);
        new android.app.AlertDialog.Builder(this)
            .setTitle("下载背景音")
            .setView(wrap)
            .setPositiveButton("下载", (d, w) -> {
                String url = et.getText().toString().trim();
                if (url.startsWith("http")) downloadBg(url);
                else Toast.makeText(this, "地址无效", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void downloadBg(String url) {
        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show();
        final File dir = new File(getFilesDir(), "bg_sounds");
        if (!dir.exists()) dir.mkdirs();
        String clean = url.split("#")[0].split("\\?")[0];
        String name = clean.substring(clean.lastIndexOf('/') + 1);
        try { name = java.net.URLDecoder.decode(name, "UTF-8"); } catch (Throwable ignored) {}
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        if (stem.matches("[0-9a-fA-F]{16,}") || name.indexOf('\uFFFD') >= 0) {
            name = "背景音_" + new java.text.SimpleDateFormat("HHmmss", java.util.Locale.CHINA).format(new java.util.Date());
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty()) name = "背景音_" + System.currentTimeMillis() % 100000;
        final File out = new File(dir, name);
        final String fname = name;
        new Thread(() -> {
            boolean ok = false;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (c.getResponseCode() == 200) {
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(out);
                    byte[] b = new byte[16384]; int r;
                    while ((r = in.read(b)) > 0) fo.write(b, 0, r);
                    fo.close(); in.close(); c.disconnect();
                    ok = out.length() > 1024;
                }
            } catch (Throwable ignored) {}
            final boolean fok = ok;
            runOnUiThread(() -> {
                Toast.makeText(this, fok ? "已加入背景音: " + fname : "下载失败, 检查地址或网络", Toast.LENGTH_SHORT).show();
                if (fok) MetronomeActivity.notifyBgRefresh(this);
            });
        }, "bg-dl-set").start();
    }

    private String hrStatus() {
        HeartRateManager m = HeartRateManager.get(this);
        String saved = prefs.getString("metroHrAddr", "");
        if (m.isConnected()) return "已连接 (" + m.lastHr() + " bpm)";
        return saved.isEmpty() ? "未连接" : "已保存设备";
    }

    private void showAgeDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(prefs.getInt("metroAge", 30)));
        et.setTextSize(16);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        int pd = (int) (getResources().getDisplayMetrics().density * 20);
        wrap.setPadding(pd, pd / 2, pd, 0);
        wrap.addView(et);
        new android.app.AlertDialog.Builder(this)
            .setTitle("年龄 (5-100)")
            .setView(wrap)
            .setPositiveButton("确定", (d, w) -> {
                try {
                    int v = Integer.parseInt(et.getText().toString().trim());
                    v = Math.max(5, Math.min(100, v));
                    prefs.edit().putInt("metroAge", v).apply();
                    MetronomeActivity.notifySettingChanged(this);
                    recreate();
                } catch (Throwable ignored) {}
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void requestPerms() {
        java.util.List<String> ps = new java.util.ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != 0) ps.add("android.permission.BLUETOOTH_SCAN");
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != 0) ps.add("android.permission.BLUETOOTH_CONNECT");
        } else {
            if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != 0) ps.add("android.permission.ACCESS_FINE_LOCATION");
        }
        if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != 0) ps.add("android.permission.ACCESS_FINE_LOCATION");
        if (!ps.isEmpty()) requestPermissions(ps.toArray(new String[0]), 1);
    }

    private void showTimerDialog() {
        final String[] opts = {"不限制", "5 分钟", "10 分钟", "15 分钟", "30 分钟", "60 分钟"};
        final int[] mins = {0, 5, 10, 15, 30, 60};
        int cur = 0;
        for (int i = 0; i < mins.length; i++) if (mins[i] == timerMin) cur = i;
        new android.app.AlertDialog.Builder(this)
            .setTitle("定时器")
            .setSingleChoiceItems(opts, cur, (d, w) -> {
                timerMin = mins[w];
                prefs.edit().putInt("metroTimer", timerMin).apply();
                MetronomeActivity.notifySettingChanged(this);
                Toast.makeText(this, timerMin > 0 ? "打拍 " + timerMin + " 分钟后自动停止" : "已取消定时", Toast.LENGTH_SHORT).show();
                d.dismiss();
                recreate();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
