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
    private int targetCad = 0;
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
        et.setFocusableInTouchMode(false); // 一次点击即获得焦点进入编辑
        et.setCursorVisible(false);
        et.setOnClickListener(vv -> {
            et.setFocusableInTouchMode(true);
            et.requestFocus();
            et.setCursorVisible(true);
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        et.setOnEditorActionListener((v, actionId, ev) -> {
            commitNum(et, min, max, apply);
            return true;
        });
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitNum(et, min, max, apply);
                et.setFocusableInTouchMode(false);
                et.setCursorVisible(false);
            }
        });
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
        targetCad = p.getInt("metroTargetCad", 0);
            audioKey = p.getString("metroAudio", MetroConstant.AudioDefault);
            if (i.getBooleanExtra("bg_refresh", false)) refreshBgList();
            if (playing) push(true);
        }
    };
    private final android.content.BroadcastReceiver tianyiRx = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if ("tianyi.hr".equals(i.getAction())) {
                TextView pc = (TextView) findViewById(R.id.tvPace);
                TextView ds = (TextView) findViewById(R.id.tvDist);
                double pace = i.getDoubleExtra("pace", 0);
                double dist = i.getDoubleExtra("dist", 0);
                boolean fix = i.getBooleanExtra("gpsFix", false);
                ((TextView) ((ViewGroup) pc.getParent()).getChildAt(1)).setText("配速");
                if (pace > 0) {
                    int m = (int) (pace / 60), sec = (int) (pace % 60);
                    pc.setText(m + "'" + String.format("%02d", sec) + "\"");
                } else pc.setText("--'--\"");
                ds.setText(dist < 1.0 ? String.format("%.0fm", dist * 1000) : String.format("%.2fkm", dist));
                int cad = i.getIntExtra("cad", 0);
                TextView tvC = (TextView) findViewById(R.id.tvCad);
                int target = targetCad > 0 ? targetCad : i.getIntExtra("bpm", 0);
                boolean editing = tvC instanceof android.widget.EditText && tvC.hasFocus();
                if (!editing) {
                    android.widget.EditText et = (android.widget.EditText) tvC;
                    String want = (cad > 0 ? Math.round(cad) : "--") + "/" + target;
                    if (!et.getText().toString().equals(want)) et.setText(want);
                }
                ((TextView) ((ViewGroup) tvC.getParent()).getChildAt(1)).setText("步频");
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
        ensureLocPerm();
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
        if (f.delete()) refreshBgList();
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
        card.setVisibility(bgFiles.isEmpty() ? View.GONE : View.VISIBLE);
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
        } catch (Throwable e) {
            stopBgPlay();
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
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void downloadBg(String url) {
        if (bgDownloading) return;
        bgDownloading = true;
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
                if (!ok) refreshBgList();
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
        findViewById(R.id.sportCard).setVisibility(pp.getBoolean("metroTianyiGps", true) ? View.VISIBLE : View.GONE);
        android.content.SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        keepScreen = p.getBoolean("metroKeepScreen", false);
        booster = p.getBoolean("metroBooster", false);
        accentFirst = p.getBoolean("metroAccent", false);
        targetCad = p.getInt("metroTargetCad", 0);
        timerMin = p.getInt("metroTimer", 0);
        audioKey = p.getString("metroAudio", MetroConstant.AudioDefault);
        boolean svc = MetronomeService.isRunning();
        if (svc != playing) {
            playing = svc;
            findViewById(R.id.metroCardDots).setVisibility(playing ? View.VISIBLE : View.GONE);
            btnPlay.setText(playing ? "■ 停止" : "▶ 开始打拍");
            if (playing) refreshLifeCard(); else findViewById(R.id.lifeCard).setVisibility(View.GONE);
            if (playing) uiTick.post(tickRun); else { uiTick.removeCallbacks(tickRun); for (View d : dots) d.setBackgroundResource(R.drawable.bg_dot_off); }
        }
        refreshBgList();
        hideNavBar();
        applyKeepScreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideNavBar();
    }

    private void ensureLocPerm() {
        String P = "android.permission.ACCESS_FINE_LOCATION";
        if (android.os.Build.VERSION.SDK_INT >= 29 && checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") != 0) {
            requestPermissions(new String[]{"android.permission.ACTIVITY_RECOGNITION", P}, 3);
            return;
        }
        if (checkSelfPermission(P) == 0) { ensureLocService(); return; }
        if (shouldShowRequestPermissionRationale(P)) {
            // 拒过一次: 再弹系统申请框
            requestPermissions(new String[]{P}, 2);
        } else {
            // 永久拒绝(不再询问): 弹窗引导去应用设置页
            new android.app.AlertDialog.Builder(this)
                .setTitle("需要定位权限")
                .setMessage("配速/距离/步频需要定位权限。\n\n点击去设置 → 权限 → 位置信息 → 允许")
                .setPositiveButton("去设置", (d, w) -> startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", getPackageName(), null))))
                .setNegativeButton("取消", null)
                .show();
        }
    }

    // ===== 生活服务 (百度天气生活指数, 开关开启才联网) =====
    private boolean lifeFetched = false;

    private void refreshLifeCard() {
        boolean on = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("metroLifeService", false);
        android.widget.LinearLayout lc = findViewById(R.id.lifeCard);
        if (!on || !playing) { lc.setVisibility(View.GONE); return; }
        lc.setVisibility(View.VISIBLE);
        if (lifeFetched) return;
        lifeFetched = true;
        new Thread(() -> {
            try {
                // 1. IP 定位城市
                java.net.HttpURLConnection c1 = (java.net.HttpURLConnection) new java.net.URL("https://whois.pconline.com.cn/ipJson.jsp?json=true").openConnection();
                c1.setConnectTimeout(6000); c1.setReadTimeout(6000);
                String city = "";
                java.io.BufferedReader r1 = new java.io.BufferedReader(new java.io.InputStreamReader(c1.getInputStream(), "GBK"));
                String line; StringBuilder sb = new StringBuilder();
                while ((line = r1.readLine()) != null) sb.append(line);
                r1.close();
                java.util.regex.Matcher mc = java.util.regex.Pattern.compile("\"city\":\"([^\"]+?)市?\"").matcher(sb);
                if (mc.find()) city = mc.group(1);
                if (city.length() == 0) { uiTick.post(() -> lifeFetched = false); return; }
                // 2. 百度天气页
                String q = java.net.URLEncoder.encode(city + "天气", "UTF-8");
                java.net.HttpURLConnection c2 = (java.net.HttpURLConnection) new java.net.URL("https://www.baidu.com/s?wd=" + q).openConnection();
                c2.setConnectTimeout(8000); c2.setReadTimeout(8000);
                c2.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                java.io.BufferedReader r2 = new java.io.BufferedReader(new java.io.InputStreamReader(c2.getInputStream(), "UTF-8"));
                StringBuilder b2 = new StringBuilder();
                while ((line = r2.readLine()) != null) b2.append(line);
                r2.close();
                String html = b2.toString();
                // 3. 温度
                String temp = "";
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\"temperature\":\"(-?\d+)\"").matcher(html);
                if (mt.find()) temp = mt.group(1) + "°";
                // 4. 生活指数: 按 item_name 切窗口取 level + icon
                java.util.Map<String, String[]> items = new java.util.LinkedHashMap<>();
                java.util.regex.Matcher mn = java.util.regex.Pattern.compile("\"item_name\":\"([^\"]+)\"").matcher(html);
                java.util.List<int[]> pos = new java.util.ArrayList<>();
                java.util.List<String> names = new java.util.ArrayList<>();
                while (mn.find()) { pos.add(new int[]{mn.start(), mn.end()}); names.add(mn.group(1)); }
                String[] want = {"穿衣", "运动", "紫外线", "旅游", "化妆", "钓鱼", "感冒", "洗车"};
                for (String w : want) {
                    for (int i = 0; i < names.size(); i++) {
                        if (!names.get(i).equals(w)) continue;
                        int from = Math.max(0, pos.get(i)[0] - 1600), to = Math.min(html.length(), pos.get(i)[1] + 1600);
                        String seg = html.substring(from, to);
                        String[] out = {w, ""};
                        java.util.regex.Matcher ml = java.util.regex.Pattern.compile("\"item_level\":\"([^\"]+)\"").matcher(seg);
                        if (ml.find()) out[1] = ml.group(1);
                        java.util.regex.Matcher mi = java.util.regex.Pattern.compile("\"item_icon\":\"([^\"]+)\"").matcher(seg);
                        if (mi.find()) out = new String[]{w, out[1], mi.group(1)};
                        items.put(w, out);
                        break;
                    }
                }
                final String fTemp = temp; final String fCity = city;
                uiTick.post(() -> buildLifeGrid(fCity, fTemp, items));
            } catch (Throwable e) {
                uiTick.post(() -> lifeFetched = false);
            }
        }, "life-fetch").start();
    }

    private void buildLifeGrid(String city, String temp, java.util.Map<String, String[]> items) {
        TextView t1 = findViewById(R.id.lifeTitle), t2 = findViewById(R.id.lifeTemp);
        t1.setText("生活服务");
        t2.setText(city + (temp.length() > 0 ? "  " + temp : ""));
        android.widget.LinearLayout grid = findViewById(R.id.lifeGrid);
        grid.removeAllViews();
        if (items.isEmpty()) { lifeFetched = false; return; }
        int col = 0;
        android.widget.LinearLayout row = null;
        for (String w : new String[]{"穿衣", "运动", "紫外线", "旅游", "化妆", "钓鱼", "感冒", "洗车"}) {
            String[] it = items.get(w);
            if (it == null) continue;
            if (col % 4 == 0) {
                row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                grid.addView(row, new android.widget.LinearLayout.LayoutParams(-1, -2));
            }
            android.widget.LinearLayout cell = new android.widget.LinearLayout(this);
            cell.setOrientation(android.widget.LinearLayout.VERTICAL);
            cell.setGravity(View.CENTER);
            cell.setBackgroundResource(R.drawable.bg_input_flat);
            int pd = dp(10); cell.setPadding(pd, dp(12), pd, dp(10));
            android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            cp.setMargins(dp(4), dp(4), dp(4), dp(4));
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(30), dp(30)));
            cell.addView(iv);
            TextView tv = new TextView(this);
            tv.setText(it[1].length() > 0 ? it[1] : w);
            tv.setTextColor(0xFF1F2329); tv.setTextSize(13); tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tv.setGravity(View.CENTER); tv.setSingleLine(true);
            android.widget.LinearLayout.LayoutParams tp = new android.widget.LinearLayout.LayoutParams(-2, -2);
            tp.setMargins(0, dp(8), 0, 0);
            cell.addView(tv, tp);
            TextView tv2 = new TextView(this);
            tv2.setText(w); tv2.setTextColor(0xFF9AA0A8); tv2.setTextSize(12); tv2.setGravity(View.CENTER);
            android.widget.LinearLayout.LayoutParams tp2 = new android.widget.LinearLayout.LayoutParams(-2, -2);
            tp2.setMargins(0, dp(2), 0, 0);
            cell.addView(tv2, tp2);
            row.addView(cell, cp);
            col++;
            // 异步加载图标
            if (it.length > 2 && it[2].startsWith("http")) {
                String url = it[2];
                new Thread(() -> {
                    try {
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                        c.setConnectTimeout(6000); c.setReadTimeout(6000);
                        final android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(c.getInputStream());
                        if (bm != null) uiTick.post(() -> iv.setImageBitmap(bm));
                    } catch (Throwable ignored) {}
                }, "life-icon").start();
            }
        }
    }

    /** 定位总开关没开: 按图1样式弹提醒 (深色圆角+橙色按钮) */
    private void ensureLocService() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        boolean on = false;
        try {
            on = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
              || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Throwable ignored) {}
        if (on) return;
        int pd = dp(24);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_dialog_dark_round);
        box.setPadding(pd, dp(26), pd, dp(10));
        TextView tvTitle = new TextView(this);
        tvTitle.setText("提醒");
        tvTitle.setTextColor(0xFFF2F2F2);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(tvTitle);
        TextView tvMsg = new TextView(this);
        tvMsg.setText("该功能需使用定位服务，是否开启？");
        tvMsg.setTextColor(0xFFC9C9C9);
        tvMsg.setTextSize(16.5f);
        tvMsg.setLineSpacing(dp(4), 1f);
        android.widget.LinearLayout.LayoutParams mp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(22), 0, dp(26));
        box.addView(tvMsg, mp);
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        TextView bNo = new TextView(this);
        bNo.setText("取消");
        bNo.setTextColor(0xFFFF6A1E);
        bNo.setTextSize(17.5f);
        bNo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bNo.setGravity(android.view.Gravity.CENTER);
        TextView bYes = new TextView(this);
        bYes.setText("确定");
        bYes.setTextColor(0xFFFF6A1E);
        bYes.setTextSize(17.5f);
        bYes.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bYes.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams bp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
        bp.setMargins(0, dp(10), 0, dp(10));
        row.addView(bNo, bp);
        View div = new View(this);
        div.setBackgroundColor(0xFF4A4A4A);
        row.addView(div, new android.widget.LinearLayout.LayoutParams(dp(1), -2));
        row.addView(bYes, bp);
        box.addView(row);
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(box);
        dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        android.view.WindowManager.LayoutParams wp = dlg.getWindow().getAttributes();
        wp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82);
        wp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        dlg.getWindow().setAttributes(wp);
        bNo.setOnClickListener(v -> dlg.dismiss());
        bYes.setOnClickListener(v -> {
            dlg.dismiss();
            try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Throwable ignored) {}
        });
        dlg.show();
    }

    private void hideNavBar() {
        android.view.WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(android.view.WindowInsets.Type.navigationBars());
            c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
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
