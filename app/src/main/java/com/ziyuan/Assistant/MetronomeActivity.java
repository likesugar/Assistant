package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 节拍器 (参考 SimpleBeat, 引擎=预生成波形+AudioTrack流式) */
public class MetronomeActivity extends Activity {
    private int bpm = 80, notes = 4, sub = 1;
    private String audioKey = MetroConstant.AudioDefault;
    private boolean booster = false, playing = false;
    private boolean accentFirst = false;
    private TextView tvBpm, btnPlay;
    private MetroAudioManager audioManager;

    private File bgDir;
    private final List<File> bgFiles = new ArrayList<>();
    private MediaPlayer bgPlayer;
    private File bgPlayingFile;
    private TextView bgPlayingView;
    private LinearLayout bgList;
    private TextView bgEmpty;
    private boolean bgDownloading = false;
    private int bgVol = 40;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private boolean dark() { return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false); }
    private boolean oled() { return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("oled", true); }
    private int color(int d, int l) { return dark() ? d : l; }

    private TextView chip(String text, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
        t.setTextColor(color(0xFFE8C87A, 0xFF1F2329));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_metronome);
        audioManager = new MetroAudioManager(this);
        bpm = getSharedPreferences("settings", MODE_PRIVATE).getInt("metroBpm", 120);
        notes = getSharedPreferences("settings", MODE_PRIVATE).getInt("metroNotes", 4);
        audioKey = getSharedPreferences("settings", MODE_PRIVATE).getString("metroAudio", MetroConstant.AudioDefault);
        bpm = getSharedPreferences("settings", MODE_PRIVATE).getInt("metroBpm", 80);
        notes = getSharedPreferences("settings", MODE_PRIVATE).getInt("metroNotes", 4);
        sub = getSharedPreferences("settings", MODE_PRIVATE).getInt("metroSub", 1);
        bgVol = getSharedPreferences("settings", MODE_PRIVATE).getInt("metroBgVol", 40);
        accentFirst = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("metroAccent", false);
        tvBpm = (TextView) findViewById(R.id.tvBpm);
        btnPlay = (TextView) findViewById(R.id.btnPlay);
        findViewById(R.id.metroMenu).setOnClickListener(v -> startActivity(new Intent(this, MetroSettingsActivity.class)));
        buildBpmPresets();
        btnPlay.setOnClickListener(v -> togglePlay());
        ((SeekBar) findViewById(R.id.sbBgVol)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) {
                if (f) setBgVol(p);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        ((SeekBar) findViewById(R.id.sbBgVol)).setProgress(bgVol);

        bindNumEdit((android.widget.EditText) findViewById(R.id.tvBeats), notes, 1, 8, this::setNotes);
        bindNumEdit((android.widget.EditText) findViewById(R.id.tvSub), sub, 1, 4, this::setSub);
        bindNumEdit((android.widget.EditText) findViewById(R.id.tvBpm), bpm, 30, 252, this::setBpm);
        findViewById(R.id.tvBpm).setOnClickListener(v -> {
            View row = findViewById(R.id.bpmPresets);
            row.setVisibility(row.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        refreshTop();
        buildDots();
        buildBgList();
        themeAll();
        tvBpm.setText(String.valueOf(bpm));
        uiTick.postDelayed(tickRun, 80);
    }

    private void refreshTop() {
        ((TextView) findViewById(R.id.tvBpm)).setText(String.valueOf(bpm));
        ((TextView) findViewById(R.id.tvBeats)).setText(String.valueOf(notes));
        ((TextView) findViewById(R.id.tvSub)).setText(String.valueOf(sub));
    }

    private String marking(int t) {
        if (t < 60) return "广板";
        if (t < 66) return "小广板";
        if (t < 76) return "柔板";
        if (t < 108) return "行板";
        if (t < 120) return "中板";
        if (t < 168) return "快板";
        if (t < 200) return "急板";
        return "最急板";
    }

    private static final int[] PRESET_BPM = {150, 160, 170, 180, 190, 200};
    private static final String[] PRESET_NAME = {"轻松", "慢跑", "节奏", "快跑", "冲刺", "间歇"};
    private final List<TextView> presetChips = new ArrayList<>();

    private void buildBpmPresets() {
        LinearLayout row = (LinearLayout) findViewById(R.id.bpmPresets);
        for (int i = 0; i < PRESET_BPM.length; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int pad = dp(3);
            clp.setMargins(pad, 0, pad, 0);
            cell.setLayoutParams(clp);
            TextView v = new TextView(this);
            v.setText(String.valueOf(PRESET_BPM[i]));
            v.setTextSize(15);
            v.setGravity(android.view.Gravity.CENTER);
            TextView n = new TextView(this);
            n.setText(PRESET_NAME[i]);
            n.setTextSize(12);
            n.setGravity(android.view.Gravity.CENTER);
            n.setTag("sub");
            cell.addView(v);
            cell.addView(n);
            final int val = PRESET_BPM[i];
            cell.setOnClickListener(vv -> setBpm(val));
            row.addView(cell);
            presetChips.add((TextView) cell.getTag() == null ? v : v);
            cell.setTag(v);
            presetChips.set(i, v);
        }
        refreshPresets();
    }

    private void refreshPresets() {
        for (int i = 0; i < presetChips.size(); i++) {
            View cell = (View) presetChips.get(i).getParent();
            boolean sel = bpm == PRESET_BPM[i];
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setCornerRadius(dp(12));
            if (sel) {
                gd.setColor(0xFF1E4D3A);
                presetChips.get(i).setTextColor(0xFF5FD08A);
                ((TextView) ((ViewGroup) cell).getChildAt(1)).setTextColor(0xFF5FD08A);
            } else {
                gd.setColor(dark() ? (oled() ? 0xFF16181D : 0xFF232B38) : 0xFFFFFFFF);
                presetChips.get(i).setTextColor(color(0xFFE8ECF4, 0xFF1F2329));
                ((TextView) ((ViewGroup) cell).getChildAt(1)).setTextColor(color(0xFF9AA3B2, 0xFF6B7280));
            }
            cell.setBackground(gd);
        }
    }

    private interface NumApply { void apply(int v); }

    private void bindNumEdit(final android.widget.EditText et, int init, final int min, final int max, final NumApply apply) {
        et.setText(String.valueOf(init));
        et.setFocusableInTouchMode(true);
        et.setOnEditorActionListener((v, actionId, ev) -> {
            commitNum(et, min, max, apply);
            return true;
        });
        et.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) commitNum(et, min, max, apply); });
    }

    private void commitNum(android.widget.EditText et, int min, int max, NumApply apply) {
        try {
            int v = Integer.parseInt(et.getText().toString().trim());
            if (v < min) v = min;
            if (v > max) v = max;
            apply.apply(v);
            et.setText(String.valueOf(v));
        } catch (Throwable ignored) {
            et.setText(String.valueOf(min));
        }
    }

    private void setSub(int v) {
        sub = Math.max(1, Math.min(4, v));
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("metroSub", sub).apply();
        refreshTop(); buildDots(); push(playing);
    }

    private String displayName(String k) {
        if (MetroConstant.AudioDefault.equals(k)) return "默认";
        if (MetroConstant.AudioClap.equals(k)) return "拍手";
        if (MetroConstant.AudioPluck.equals(k)) return "弹拨";
        if (MetroConstant.AudioRisset.equals(k)) return "鼓点";
        if (MetroConstant.AudioSine.equals(k)) return "正弦";
        if (MetroConstant.AudioSquare.equals(k)) return "方波";
        return k;
    }

    private void showAudioPicker() {
        final List<String> ks = audioManager.getAudioList();
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
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("metroAudio", audioKey).apply();
                push(playing);
                d.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void setBpm(int v) {
        bpm = Math.max(30, Math.min(252, v));
        refreshPresets();
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("metroBpm", bpm).apply();
        refreshTop();
        push(playing);
    }

    private void setNotes(int v) {
        notes = Math.max(1, Math.min(8, v));
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("metroNotes", notes).apply();
        refreshTop(); buildDots(); push(playing);
    }

    private final android.content.BroadcastReceiver settingsRx = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            android.content.SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
            booster = p.getBoolean("metroBooster", false);
            accentFirst = p.getBoolean("metroAccent", false);
            audioKey = p.getString("metroAudio", MetroConstant.AudioDefault);
            if (i.getBooleanExtra("bg_refresh", false)) refreshBgList();
            if (playing) push(true);
        }
    };
    private final android.content.BroadcastReceiver tianyiRx = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if ("tianyi.hr".equals(i.getAction())) {
                TextView hr = (TextView) findViewById(R.id.tvHr);
                TextView pc = (TextView) findViewById(R.id.tvPace);
                TextView ds = (TextView) findViewById(R.id.tvDist);
                int b = i.getIntExtra("hr", 0);
                hr.setText(b > 0 ? String.valueOf(b) : "--");
                double pace = i.getDoubleExtra("pace", 0);
                double dist = i.getDoubleExtra("dist", 0);
                boolean fix = i.getBooleanExtra("gpsFix", false);
                boolean conn = i.getBooleanExtra("hrConn", false);
                ((TextView) ((ViewGroup) hr.getParent()).getChildAt(1)).setText(conn ? "心率·已连" : "心率·未连");
                ((TextView) ((ViewGroup) pc.getParent()).getChildAt(1)).setText(fix ? "配速·已定位" : "配速·未定位");
                if (pace > 0) {
                    int m = (int) (pace / 60), sec = (int) (pace % 60);
                    pc.setText(m + "'" + String.format("%02d", sec) + "\"");
                } else pc.setText("--'--\"");
                ds.setText(String.format("%.2fkm", dist));
                return;
            }
            TextView tv = (TextView) findViewById(R.id.tvTianyi);
            String t = i.getStringExtra("text");
            tv.setText(t == null || t.isEmpty() ? "" : "🎤 " + t);
            tv.setVisibility(t == null || t.isEmpty() ? View.GONE : View.VISIBLE);
        }
    };
    private final android.os.Handler rpt = new android.os.Handler();
    private Runnable rptRun;
    private void bindRepeat(View v, int delta) {
        v.setOnLongClickListener(vv -> {
            if (rptRun != null) rpt.removeCallbacks(rptRun);
            rptRun = new Runnable() {
                @Override public void run() { setBpm(bpm + delta); rpt.postDelayed(this, 80); }
            };
            rpt.postDelayed(rptRun, 350);
            return true;
        });
    }
    private void cancelRepeat() { if (rptRun != null) rpt.removeCallbacks(rptRun); }

    private final List<View> dots = new ArrayList<>();
    private final android.os.Handler uiTick = new android.os.Handler();
    private final Runnable tickRun = new Runnable() {
        @Override public void run() {
            if (playing) {
                int b = MetronomeService.currentBeat(notes * sub);
                for (int i = 0; i < dots.size(); i++) {
                    View d = dots.get(i);
                    if (i == b) d.setBackgroundResource(i == 0 ? R.drawable.bg_dot_red : R.drawable.bg_dot_green);
                    else d.setBackgroundResource(R.drawable.bg_dot_off);
                }
                uiTick.postDelayed(this, 60);
            }
        }
    };

    private void buildDots() {
        LinearLayout row = (LinearLayout) findViewById(R.id.dotsRow);
        row.removeAllViews();
        dots.clear();
        int n = notes * sub;
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(16), dp(16));
            lp.rightMargin = dp(10);
            d.setLayoutParams(lp);
            d.setBackgroundResource(R.drawable.bg_dot_off);
            row.addView(d);
            dots.add(d);
        }
    }

    private void togglePlay() {
        playing = !playing;
        findViewById(R.id.metroCardDots).setVisibility(playing ? View.VISIBLE : View.GONE);
        push(playing);
        applyKeepScreen();
        if (!playing) {
            uiTick.removeCallbacks(tickRun);
            for (View d : dots) d.setBackgroundResource(R.drawable.bg_dot_off);
        } else {
            uiTick.removeCallbacks(tickRun);
            uiTick.postDelayed(tickRun, 60);
        }
    }

    private boolean keepScreen = false;

    private void applyKeepScreen() {
        if (playing && keepScreen) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void showMenu() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("节拍器")
            .setItems(new String[]{bgDownloading ? "下载背景音..." : "下载背景音"}, (d, w) -> showDownloadDialog())
            .show();
    }

    public static void notifyBgRefresh(android.content.Context c) {
        c.sendBroadcast(new Intent(MetronomeService.ACTION_UPDATE).setPackage(c.getPackageName()).putExtra("bg_refresh", true));
    }

    public static void notifySettingChanged(android.content.Context c) {
        c.sendBroadcast(new Intent(MetronomeService.ACTION_UPDATE).setPackage(c.getPackageName()).putExtra("settings_changed", true));
    }

    // ---------- 背景音 ----------

    private android.media.audiofx.LoudnessEnhancer bgBoostFx;

    private void setBgVol(int v) {
        bgVol = v;
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("metroBgVol", v).apply();
        if (bgPlayer != null) {
            float f = Math.min(1f, v / 100f);
            try { bgPlayer.setVolume(f, f); } catch (Throwable ignored) {}
            try {
                if (bgBoostFx != null) { bgBoostFx.release(); bgBoostFx = null; }
                if (v > 100) {
                    bgBoostFx = new android.media.audiofx.LoudnessEnhancer(bgPlayer.getAudioSessionId());
                    bgBoostFx.setTargetGain((int) ((v - 100) / 100f * 6000)); // 最多 +6dB
                    bgBoostFx.setEnabled(true);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void deleteBg(File f) {
        if (bgPlayer != null && bgPlayingFile != null && bgPlayingFile.equals(f)) stopBgPlay();
        if (f.delete()) { refreshBgList(); Toast.makeText(this, "已删除: " + f.getName().replaceAll("\\.[^.]+$", ""), Toast.LENGTH_SHORT).show(); }
        else Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
    }

    private void buildBgList() {
        bgDir = new File(getFilesDir(), "bg_sounds");
        if (!bgDir.exists()) bgDir.mkdirs();
        bgList = (LinearLayout) findViewById(R.id.bgList);
        bgEmpty = (TextView) findViewById(R.id.bgEmpty);
        refreshBgList();
    }

    private void refreshBgList() {
        bgFiles.clear();
        File[] fs = bgDir.listFiles();
        if (fs != null) { Arrays.sort(fs); for (File f : fs) if (f.length() > 1024) bgFiles.add(f); }
        bgList.removeAllViews();
        bgEmpty.setVisibility(bgFiles.isEmpty() ? View.VISIBLE : View.GONE);
        for (final File f : bgFiles) {
            TextView t = new TextView(this);
            String name = f.getName().replaceAll("\\.[^.]+$", "");
            t.setText("▷  " + name);
            t.setTextSize(15);
            t.setTextColor(color(0xFFE8ECF4, 0xFF1F2329));
            t.setPadding(dp(14), dp(10), dp(14), dp(10));
            t.setOnClickListener(v -> toggleBgPlay(f, t));
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView del = new TextView(this);
            del.setText("🗑");
            del.setTextSize(15);
            del.setPadding(dp(12), dp(10), dp(6), dp(10));
            del.setOnClickListener(v -> deleteBg(f));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_chip_off);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(8);
            row.setLayoutParams(rlp);
            row.addView(t);
            row.addView(del);
            bgList.addView(row);
        }
        ScrollView sc = (ScrollView) findViewById(R.id.bgScroll);
        LinearLayout card = (LinearLayout) findViewById(R.id.metroCardBg);
        card.setVisibility(View.VISIBLE);
        findViewById(R.id.bgScroll).setVisibility(bgFiles.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout volRow = (LinearLayout) ((ViewGroup) findViewById(R.id.sbBgVol).getParent());
        volRow.setVisibility(bgFiles.isEmpty() ? View.GONE : View.VISIBLE);
        if (bgFiles.size() > 4) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4 * 46));
            sc.setLayoutParams(lp);
        } else {
            sc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void toggleBgPlay(File f, TextView view) {
        if (bgPlayer != null && (bgPlayingFile == f || f.equals(bgPlayingFile))) {
            stopBgPlay();
            return;
        }
        stopBgPlay();
        bgPlayingFile = f;
        try {
            bgPlayer = new MediaPlayer();
            bgPlayer.setDataSource(f.getAbsolutePath());
            bgPlayer.setLooping(true);
            float v = Math.min(1f, bgVol / 100f);
            bgPlayer.setVolume(v, v);
            setBgVol(bgVol);
            bgPlayer.prepare();
            bgPlayer.start();
            bgPlayingView = view;
            view.setText("❚❚  " + f.getName().replaceAll("\\.[^.]+$", ""));
            view.setTextColor(0xFFE8C87A);
            Toast.makeText(this, "背景音循环播放中, 再点停止", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            stopBgPlay();
            Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopBgPlay() {
        if (bgPlayer != null) {
            try { bgPlayer.stop(); bgPlayer.release(); } catch (Throwable ignored) {}
            bgPlayer = null;
        }
        if (bgPlayingView != null) {
            bgPlayingView.setText("▷  " + bgPlayingView.getText().toString().replaceAll("^[^ ]+ +", ""));
            bgPlayingView.setTextColor(color(0xFFE8ECF4, 0xFF1F2329));
            bgPlayingView = null;
        }
    }

    private void showDownloadDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        et.setHint("粘贴音频下载地址 (http...mp3/wav/ogg/m4a)");
        et.setTextSize(14);
        int pd = dp(20);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(pd, dp(10), pd, 0);
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
        if (bgDownloading) return;
        bgDownloading = true;
        Toast.makeText(this, "开始下载...", Toast.LENGTH_SHORT).show();
        bgDir = new File(getFilesDir(), "bg_sounds");
        if (!bgDir.exists()) bgDir.mkdirs();
        String clean = url.split("#")[0].split("\\?")[0];
        String name = clean.substring(clean.lastIndexOf('/') + 1);
        try { name = java.net.URLDecoder.decode(name, "UTF-8"); } catch (Throwable ignored) {}
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        if (stem.matches("[0-9a-fA-F]{16,}")) {
            name = "背景音_" + new java.text.SimpleDateFormat("HHmmss", java.util.Locale.CHINA).format(new java.util.Date());
        }
        if (name.indexOf('\uFFFD') >= 0 || name.matches(".*%[0-9A-Fa-f]{2}.*")) {
            // 解码乱码兜底: 用时间命名
            name = "背景音_" + new java.text.SimpleDateFormat("HHmmss", java.util.Locale.CHINA).format(new java.util.Date());
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty()) name = "背景音_" + System.currentTimeMillis() % 100000;
        final String fname = name;
        final File out = new File(bgDir, fname);
        new Thread(() -> {
            boolean ok = fetch(url, out);
            runOnUiThread(() -> {
                bgDownloading = false;
                refreshBgList();
                Toast.makeText(this, ok ? "已加入背景音: " + fname : "下载失败, 检查地址或网络", Toast.LENGTH_SHORT).show();
            });
        }, "bg-dl").start();
    }


    private boolean fetch(String url, File out) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (c.getResponseCode() != 200) { c.disconnect(); return false; }
            InputStream in = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(out);
            byte[] b = new byte[16384]; int r;
            while ((r = in.read(b)) > 0) fo.write(b, 0, r);
            fo.close(); in.close(); c.disconnect();
            return out.length() > 1024;
        } catch (Throwable e) {
            return false;
        }
    }

    private int timerMin = 0;

    private void showTimerPicker() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setHint("分钟数, 0=不限");
        et.setText(String.valueOf(timerMin));
        new android.app.AlertDialog.Builder(this)
            .setTitle("定时停止")
            .setView(et)
            .setPositiveButton("确定", (d, w) -> {
                try { timerMin = Math.max(0, Integer.parseInt(et.getText().toString().trim())); } catch (Throwable ignored) {}
                push(playing);
                Toast.makeText(this, timerMin > 0 ? timerMin + " 分钟后自动停止" : "已取消定时", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void push(boolean start) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("metroBpm", bpm).putInt("metroNotes", notes).putString("metroAudio", audioKey).apply();
        Intent it = new Intent(this, MetronomeService.class);
        it.setAction(start ? MetronomeService.ACTION_START : (playing ? MetronomeService.ACTION_UPDATE : MetronomeService.ACTION_STOP));
        it.putExtra(MetronomeService.EXTRA_BPM, bpm);
        it.putExtra(MetronomeService.EXTRA_NOTES, notes);
        it.putExtra(MetronomeService.EXTRA_AUDIO, audioKey);
        it.putExtra(MetronomeService.EXTRA_BOOSTER, booster);
        it.putExtra(MetronomeService.EXTRA_ACCENT, accentFirst);
        it.putExtra(MetronomeService.EXTRA_SUB, sub);
        it.putExtra(MetronomeService.EXTRA_TIMER, timerMin);
        if (start) startForegroundService(it); else startService(it);
        btnPlay.setText(playing ? "■ 停止" : "▶ 开始打拍");
    }

    private void themeAll() {
        boolean dk = dark();
        findViewById(R.id.metroRoot).setBackgroundColor(dk ? (oled() ? 0xFF000000 : 0xFF11151D) : 0xFFEEF4FF);
        int card = dk ? (oled() ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3;
        findViewById(R.id.metroCardBeats).setBackgroundResource(card);
        findViewById(R.id.metroCardBeats).setBackgroundResource(card);
        findViewById(R.id.metroCardBg).setBackgroundResource(card);
        findViewById(R.id.metroCardDots).setBackgroundResource(card);
        int title = color(0xFFE8ECF4, 0xFF1F2329);
        int sub = color(0xFF9AA3AE, 0xFF8A94A6);
        int accent = color(0xFFE8C87A, 0xFFB8860B);
        for (int id : new int[]{R.id.tvBpm, R.id.tvBeats, R.id.tvSub}) {
            TextView tv = (TextView) findViewById(id);
            tv.setTextColor(title);
            android.graphics.drawable.GradientDrawable box = new android.graphics.drawable.GradientDrawable();
            box.setCornerRadius(dp(20));
            box.setColor(dk ? (oled() ? 0xFF16181D : 0xFF232B38) : 0xFFFFFFFF);
            tv.setBackground(box);
        }
                android.widget.ImageView gear = (android.widget.ImageView) findViewById(R.id.metroMenu);
        gear.setColorFilter(color(0xFFE8ECF4, 0xFF1F2329));
        for (int id : new int[]{R.id.btnPlay}) ((TextView) findViewById(id)).setTextColor(accent);
        for (View v : new View[]{findViewById(R.id.metroCardBeats), findViewById(R.id.metroCardBg)}) {
            ((ViewGroup) v).setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        }
        scanTags(findViewById(R.id.metroRoot), title, sub);
    }

    private void scanTags(ViewGroup vg, int title, int sub) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            Object tag = c.getTag();
            if (tag instanceof String) {
                if ("title".equals(tag) && c instanceof TextView) ((TextView) c).setTextColor(title);
                else if ("sub".equals(tag) && c instanceof TextView) ((TextView) c).setTextColor(sub);
            }
            if (c instanceof ViewGroup) scanTags((ViewGroup) c, title, sub);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(settingsRx, new android.content.IntentFilter(MetronomeService.ACTION_UPDATE));
        registerReceiver(tianyiRx, new android.content.IntentFilter("tianyi.sub"));
        registerReceiver(tianyiRx, new android.content.IntentFilter("tianyi.hr"));
        android.content.SharedPreferences pp = getSharedPreferences("settings", MODE_PRIVATE);
        findViewById(R.id.sportCard).setVisibility(pp.getBoolean("metroTianyiFollow", false) ? View.VISIBLE : View.GONE);
        android.content.SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        keepScreen = p.getBoolean("metroKeepScreen", false);
        booster = p.getBoolean("metroBooster", false);
        accentFirst = p.getBoolean("metroAccent", false);
        timerMin = p.getInt("metroTimer", 0);
        audioKey = p.getString("metroAudio", MetroConstant.AudioDefault);
        boolean svc = MetronomeService.isRunning();
        if (svc != playing) {
            playing = svc;
            findViewById(R.id.metroCardDots).setVisibility(playing ? View.VISIBLE : View.GONE);
            btnPlay.setText(playing ? "■ 停止" : "▶ 开始打拍");
            if (playing) uiTick.post(tickRun); else { uiTick.removeCallbacks(tickRun); for (View d : dots) d.setBackgroundResource(R.drawable.bg_dot_off); }
        }
        refreshBgList();
        applyKeepScreen();
    }

    @Override
    protected void onPause() {
        try { unregisterReceiver(settingsRx); } catch (Throwable ignored) {}
        try { unregisterReceiver(tianyiRx); } catch (Throwable ignored) {}
        cancelRepeat();
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // 页面关闭不打断: 服务继续后台打拍; 背景音跟随页面销毁
        cancelRepeat();
        stopBgPlay();
        super.onDestroy();
    }
}
