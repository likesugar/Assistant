package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
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
        if (MetroConstant.AudioBongo.equals(k)) return "邦戈鼓(强/弱)";
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
        card1.addView(switchRow("音量增强", "所有拍子加大音量", prefs.getBoolean("metroBooster", false), (on) -> {
            prefs.edit().putBoolean("metroBooster", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card1.addView(choiceRow("定时器", timerMin > 0 ? timerMin + " 分钟后自动停止" : "不限制", v -> showTimerDialog()));

        root.addView(card1);

        // ===== 训练法 =====
        root.addView(sectionTitle("训练法"));
        LinearLayout cardF = card();
        cardF.addView(switchRow("法特莱克跑训练器", "打拍页顶部显示训练横幅, 跑步打拍/步行静音, 阶段切换邦戈鼓提示", prefs.getBoolean("fartlekOn", false), (on) -> {
            prefs.edit().putBoolean("fartlekOn", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        cardF.addView(choiceRow("走路 (FITT 原则)", "按表格两档: 普通 / 进阶", v -> showFittDialog()));
        cardF.addView(choiceRow("训练参数",
                "跑步 " + prefs.getInt("fartlekRun", 60) + "s · 步行 " + prefs.getInt("fartlekWalk", 30)
                        + "s · " + prefs.getInt("fartlekCycles", 5) + " 轮", v -> showFartlekDialog()));
        root.addView(cardF);

        // ===== 我的 =====
        root.addView(sectionTitle("我的"));
        LinearLayout card2 = card();
        card2.addView(choiceRow("下载背景音", "粘贴音频地址, 存入节拍器背景音", v -> showDownloadDialog()));
        card2.addView(switchRow("屏幕常亮", "打拍时保持屏幕常亮", prefs.getBoolean("metroKeepScreen", false), (on) -> {
            prefs.edit().putBoolean("metroKeepScreen", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card2.addView(switchRow("天气", "打拍页显示百度天气生活指数 (开启才联网)", prefs.getBoolean("metroLifeService", false), (on) -> {
            prefs.edit().putBoolean("metroLifeService", on).apply();
        }));
        root.addView(card2);

        // ===== 洛天依 =====
        root.addView(sectionTitle("洛天依"));
        LinearLayout card3 = card();
        card3.addView(choiceRow("马费通跑法", mafText(), v -> showMafDialog()));
        card3.addView(switchRow("配速", "GPS 实时配速 + 距离 + 步频 + 死推", prefs.getBoolean("metroTianyiGps", true), (on) -> {
            prefs.edit().putBoolean("metroTianyiGps", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card3.addView(switchRow("随机鼓励语 + 字幕", "每 35~55 秒随机一句鼓励, 顶部同步字幕", prefs.getBoolean("metroTianyiCheer", true), (on) -> {
            prefs.edit().putBoolean("metroTianyiCheer", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        card3.addView(switchRow("阶段提示 + 打拍自动变速", "热身→燃脂→冲刺→放松, BPM 随阶段渐变", prefs.getBoolean("metroTianyiStage", false), (on) -> {
            prefs.edit().putBoolean("metroTianyiStage", on).apply();
            MetronomeActivity.notifySettingChanged(this);
        }));
        root.addView(card3);

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sc.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sc);
        applyTheme(root);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
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

    /** 走路 FITT 原则预设 (普通/进阶, 按表格) */
    private void showFittDialog() {
        boolean dark = prefs.getBoolean("dark", false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(p, (int) (8 * getResources().getDisplayMetrics().density), p, 0);

        String[][] rows = {
            {"基础", "一周5~7次 · 轻松微微出汗 (3-5公里/小时)\n快走 · 每次20~30分钟 · 总计150-200分钟", "115", "25"},
            {"进阶", "一周5~7次 · 中等~高强度 (5-7公里/小时)\n快走/负重走/上坡走 · 每次20~30分钟", "140", "25"},
        };
        int[][] colors = { {0xFF1F8838, dark ? 0xFF1F2B22 : 0xFFEAF6EE}, {0xFFB3382C, dark ? 0xFF2B1F1F : 0xFFFBECEA} };
        for (int i = 0; i < 2; i++) {
            final String[] r = rows[i];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(colors[i][1]);
            bg.setCornerRadius((int) (12 * getResources().getDisplayMetrics().density));
            bg.setStroke(2, colors[i][0]);
            card.setBackground(bg);
            card.setPadding(p, p, p, p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = (int) (10 * getResources().getDisplayMetrics().density);
            card.setLayoutParams(lp);
            TextView t = new TextView(this);
            t.setText(r[0] + "档 · 方式：" + (i == 0 ? "快走" : "快走/负重走/上坡走"));
            t.setTextSize(15); t.setTypeface(null, android.graphics.Typeface.BOLD); t.setTextColor(colors[i][0]);
            card.addView(t);
            TextView d = new TextView(this);
            d.setText(r[1]);
            d.setTextSize(13); d.setTextColor(dark ? 0xFFC8CDD4 : 0xFF444B55);
            d.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, (int) (10 * getResources().getDisplayMetrics().density));
            card.addView(d);
            TextView go = new TextView(this);
            go.setText("✓ 应用这一档 (步频节拍 " + r[2] + " BPM · " + r[3] + " 分钟自动停止)");
            go.setTextSize(13); go.setTypeface(null, android.graphics.Typeface.BOLD); go.setTextColor(0xFF2F7CF6);
            go.setOnClickListener(v -> {
                prefs.edit()
                        .putInt("metroBpm", Integer.parseInt(r[2]))
                        .putInt("metroTimer", Integer.parseInt(r[3]))
                        .apply();
                MetronomeActivity.notifySettingChanged(this);
                recreate();
            });
            card.addView(go);
            box.addView(card);
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("走路 · FITT 原则 (频率/强度/方式/时间)")
                .setView(box)
                .setNegativeButton("关闭", null).show();
    }

    private void showFartlekDialog() {
        android.content.SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        final EditText[] ets = new EditText[3];
        String[] labs = {"跑步秒数", "步行秒数", "循环次数"};
        int[] vals = {p.getInt("fartlekRun", 60), p.getInt("fartlekWalk", 30), p.getInt("fartlekCycles", 5)};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        for (int i = 0; i < 3; i++) {
            TextView l = new TextView(this); l.setText(labs[i]); l.setTextSize(12);
            box.addView(l);
            EditText e = new EditText(this);
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            e.setText(String.valueOf(vals[i]));
            box.addView(e); ets[i] = e;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("训练参数")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        p.edit()
                                .putInt("fartlekRun", Math.max(5, Integer.parseInt(ets[0].getText().toString())))
                                .putInt("fartlekWalk", Math.max(5, Integer.parseInt(ets[1].getText().toString())))
                                .putInt("fartlekCycles", Math.max(1, Integer.parseInt(ets[2].getText().toString())))
                                .apply();
                    } catch (Throwable ignored) {}
                    recreate();
                })
                .setNegativeButton("取消", null).show();
    }

    private android.widget.Switch findSwitchInRow(View row) {
        if (row instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) row;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof android.widget.Switch) return (android.widget.Switch) c;
                if (c instanceof android.view.ViewGroup) {
                    android.widget.Switch r = findSwitchInRow(c);
                    if (r != null) return r;
                }
            }
        }
        return null;
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

    private String mafText() {
        int age = prefs.getInt("metroAge", 0);
        return age > 0 ? "已算过: 年龄 " + age + ", MAF " + (180 - age) : "未输入年龄";
    }

    private void showMafDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int cur = prefs.getInt("metroAge", 0);
        et.setHint("输入年龄");
        if (cur > 0) et.setText(String.valueOf(cur));
        et.setTextSize(16);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        int pd = (int) (getResources().getDisplayMetrics().density * 20);
        wrap.setPadding(pd, pd / 2, pd, 0);
        wrap.addView(et);
        new android.app.AlertDialog.Builder(this)
            .setTitle("马费通跑法 (MAF 180-年龄)")
            .setView(wrap)
            .setPositiveButton("计算", (d, w) -> {
                try {
                    int age = Math.max(5, Math.min(100, Integer.parseInt(et.getText().toString().trim())));
                    prefs.edit().putInt("metroAge", age).apply();
                    int maf = 180 - age;
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("你的 MAF 训练心率")
                        .setMessage("年龄 " + age + " 岁\n\nMAF 心率 = 180 - " + age + " = " + maf + " bpm\n\n日常有氧跑不要超过 " + maf + "，热身阶段可以低 10 拍（" + (maf - 10) + " 左右）起步")
                        .setPositiveButton("知道了", (d2, w2) -> recreate())
                        .show();
                    MetronomeActivity.notifySettingChanged(this);
                } catch (Throwable ignored) {}
            })
            .setNegativeButton("取消", null)
            .show();
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
