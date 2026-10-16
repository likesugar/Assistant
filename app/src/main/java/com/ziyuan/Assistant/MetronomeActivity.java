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
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        boolean locOn = false;
        try {
            locOn = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                 || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Throwable ignored) {}
        if (!locOn && !playing) { showLocReminder(); return; }
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
            if (playing) refreshLifeCard();
            if (playing) uiTick.post(tickRun); else { uiTick.removeCallbacks(tickRun); for (View d : dots) d.setBackgroundResource(R.drawable.bg_dot_off); }
        }
        refreshBgList();
        refreshLifeCard();
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
        if (checkSelfPermission(P) == 0) return;
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

    // ===== 天气+生活服务 (中国天气网气象源, 默认IP定位城市) =====
    private boolean lifeFetched = false;
    private long lastWeatherAt = 0;

    private void refreshLifeCard() {
        boolean on = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("metroLifeService", false);
        android.widget.LinearLayout lc = findViewById(R.id.lifeCard);
        if (!on) { lc.setVisibility(View.GONE); return; }
        lc.setVisibility(View.VISIBLE);
        long now = android.os.SystemClock.elapsedRealtime();
        if (lifeFetched && now - lastWeatherAt < 10 * 60 * 1000) return;
        lifeFetched = true; lastWeatherAt = now;
        TextView t1 = findViewById(R.id.lifeTitle), t2 = findViewById(R.id.lifeTemp);
        t1.setText("生活服务");
        t2.setText("定位中…");
        t2.setOnClickListener(v -> pickCity());
        new Thread(() -> {
            try {
                android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
                String code = sp.getString("lifeCityCode", "");
                String cname = sp.getString("lifeCityName", "");
                if (code.length() == 0) {
                    cname = ipCity();
                    code = cityCode(cname);
                    sp.edit().putString("lifeCityCode", code).putString("lifeCityName", cname).apply();
                }
                String page = httpGet("http://d1.weather.com.cn/weather_index/" + code + ".html?_=" + System.currentTimeMillis(), "http://www.weather.com.cn/");
                org.json.JSONObject sk = new org.json.JSONObject(extractVar(page, "dataSK"));
                org.json.JSONObject dz = new org.json.JSONObject(extractVar(page, "cityDZ")).getJSONObject("weatherinfo");
                org.json.JSONObject zs = new org.json.JSONObject(extractVar(page, "dataZS")).getJSONObject("zs");
                org.json.JSONObject f0 = new org.json.JSONObject(extractVar(page, "fc")).getJSONArray("f").getJSONObject(0);
                String city = sk.optString("cityname", cname);
                String type = dz.optString("weather", sk.optString("weather", "--"));
                String curT = sk.optString("temp", dz.optString("temp", "--"));
                String mx = f0.optString("fc", "--"), mn = f0.optString("fd", "--");
                String hum = sk.optString("SD", "--");
                String wind = sk.optString("WD", "") + sk.optString("WS", "");
                String press = sk.optString("qy", "--") + "hPa";
                String vis = sk.optString("njd", "--");
                String aqi = "良 " + sk.optString("aqi", "--");
                String[][] indices = new String[8][2];
                String[] want = {"穿衣", "运动", "紫外线", "旅游", "化妆", "钓鱼", "感冒", "洗车"};
                java.util.Iterator<String> it = zs.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!k.endsWith("_name")) continue;
                    String nm = zs.optString(k, "");
                    for (int i = 0; i < want.length; i++) {
                        if (nm.startsWith(want[i]) && indices[i][1] == null) {
                            indices[i][0] = want[i];
                            indices[i][1] = zs.optString(k.substring(0, k.length() - 5) + "_hint", "--");
                        }
                    }
                }
                for (int i = 0; i < 8; i++) if (indices[i][0] == null) { indices[i][0] = want[i]; indices[i][1] = "--"; }
                // 15天趋势: 官方 1-7 天 + 8-15 天两页
                java.util.List<String[]> days = new java.util.ArrayList<>();
                try { parseDays(httpGet("http://www.weather.com.cn/weather/" + code + ".shtml"), false, days); } catch (Throwable ignored) {}
                if (days.size() < 2) { // 页面拿不到 → 官方 d1 fc 兜底
                    days.clear();
                    try {
                        org.json.JSONArray f = new org.json.JSONObject(extractVar(page, "fc")).getJSONArray("f");
                        for (int i = 0; i < f.length(); i++) {
                            org.json.JSONObject o = f.getJSONObject(i);
                            days.add(new String[]{o.optString("fj"), o.optString("fi"), wEmoji(o.optString("fa")),
                                "", o.optString("fc"), o.optString("fd"), wEmoji(o.optString("fb")),
                                o.optString("fe") + o.optString("fg")});
                        }
                    } catch (Throwable ignored2) {}
                }
                final String fcity = city, ftype = type, fcur = curT, fmx = mx, fmn = mn;
                final String fh = hum, fw = wind, fp = press, fv = vis, fa = aqi;
                final String[][] fi = indices;
                final java.util.List<String[]> fdays = days;
                uiTick.post(() -> buildWeather(fcity, ftype, fcur, fmn, fmx, fh, fw, fp, fv, fa, fi, fdays));
            } catch (Throwable e) {
                String msg = e.getMessage(); uiTick.post(() -> { ((TextView) findViewById(R.id.lifeTemp)).setText("获取失败:" + (msg == null || msg.length() == 0 ? e.getClass().getSimpleName() : msg) + " 点击重试"); lifeFetched = false; lastWeatherAt = 0; });
            }
        }, "weather").start();
    }

    private String ipCity() throws Throwable {
        java.net.HttpURLConnection hc = (java.net.HttpURLConnection) new java.net.URL("https://whois.pconline.com.cn/ipJson.jsp?json=true").openConnection();
        hc.setConnectTimeout(8000); hc.setReadTimeout(8000);
        java.io.BufferedReader hr = new java.io.BufferedReader(new java.io.InputStreamReader(hc.getInputStream(), "GBK"));
        StringBuilder hsb = new StringBuilder(); String hline;
        while ((hline = hr.readLine()) != null) hsb.append(hline);
        hr.close();
        java.util.regex.Matcher mc = java.util.regex.Pattern.compile("\"city\":\"([^\"]+?)市?\"").matcher(hsb.toString());
        return mc.find() ? mc.group(1) : "杭州";
    }

    /** 中国天气网搜索: 城市名 → 101 开头的 9 位代码 (双源+后缀重试) */
    private String cityCode(String name) throws Throwable {
        String[] names = {name, name + "市"};
        String[] urls = {"http://toy1.weather.com.cn/search?cityname=",
                         "http://search.weather.com.cn/wap/searchData.php?cityname="};
        for (String nm : names) {
            for (String base : urls) {
                try {
                    String g = httpGet(base + java.net.URLEncoder.encode(nm, "UTF-8"), "http://www.weather.com.cn/");
                    java.util.regex.Matcher mr = java.util.regex.Pattern.compile("(101\\d{6})~[^~]*~([^~]+)~").matcher(g);
                    while (mr.find()) {
                        if (mr.group(2).equals(nm) || mr.group(2).equals(name)) return mr.group(1);
                    }
                    mr.reset();
                    if (mr.find()) return mr.group(1);
                } catch (Throwable ignored) {}
            }
        }
        throw new Exception("no code for " + name);
    }

    private static String wEmoji(String code) {
        if (code == null || code.length() < 2) return "🌥";
        int c;
        try { c = Integer.parseInt(code.replaceAll("[^0-9]", "")); } catch (Throwable e) { return "🌥"; }
        if (c == 0) return "☀️";
        if (c == 1) return "🌤";
        if (c == 2) return "☁️";
        if (c == 3 || c == 8) return "🌦";
        if (c == 4) return "⛈";
        if (c >= 5 && c <= 12) return "🌧";
        if (c >= 13 && c <= 16) return "🌨";
        if (c == 17 || c == 18 || c == 27 || c == 28) return "🌫";
        return "🌥";
    }

    /** 解析天气网页面天数列表: 7天页(class=sky) 与 8-15天页(class=t) */
    private static void parseDays(String html, boolean is15d, java.util.List<String[]> out) {
        String[] blocks = is15d
            ? html.split("(?=<li class=\"t\">)")
            : html.split("(?=<li class=\"sky )");
        for (String b : blocks) {
            if (is15d ? !b.startsWith("<li class=\"t\">") : !b.startsWith("<li class=\"sky ")) continue;
            int end = b.indexOf("</li>");
            if (end > 0) b = b.substring(0, end);
            try {
                String label, date;
                if (is15d) {
                    java.util.regex.Matcher mt = java.util.regex.Pattern.compile("class=\"time\">([^<（]+)（([^）]+)）").matcher(b);
                    if (!mt.find()) continue;
                    label = mt.group(1); date = mt.group(2);
                } else {
                    java.util.regex.Matcher mh = java.util.regex.Pattern.compile("<h1>([^<（]+)（([^）]+)）</h1>").matcher(b);
                    if (!mh.find()) continue;
                    label = mh.group(1); date = mh.group(2);
                }
                java.util.regex.Matcher md = java.util.regex.Pattern.compile("png\\d+ ([dn]\\d{2})").matcher(b);
                String dc = md.find() ? md.group(1) : "d99";
                String nc = md.find() ? md.group(1) : "n99";
                java.util.regex.Matcher mw = java.util.regex.Pattern.compile(is15d ? "class=\"wea\">([^<]+)</span>" : "class=\"wea\">([^<]+)</p>").matcher(b);
                String wea = mw.find() ? mw.group(1) : "";
                String hi, lo, wind;
                if (is15d) {
                    java.util.regex.Matcher mtem = java.util.regex.Pattern.compile("<em>(-?\\d+)℃</em>/(-?\\d+)℃").matcher(b);
                    if (!mtem.find()) continue;
                    hi = mtem.group(1); lo = mtem.group(2);
                    java.util.regex.Matcher mw1 = java.util.regex.Pattern.compile("class=\"wind\">([^<]+)</span>.*?class=\"wind1\">([^<]+)</span>").matcher(b);
                    wind = mw1.find() ? mw1.group(1) + mw1.group(2) : "";
                } else {
                    java.util.regex.Matcher mtem = java.util.regex.Pattern.compile("<span>(-?\\d+)</span>/<i>(-?\\d+)℃</i>").matcher(b);
                    if (!mtem.find()) continue;
                    hi = mtem.group(1); lo = mtem.group(2);
                    java.util.regex.Matcher mw1 = java.util.regex.Pattern.compile("title=\"([^\"]+)\" class=\"[A-Z]+\"").matcher(b);
                    String w1 = mw1.find() ? mw1.group(1) : "";
                    java.util.regex.Matcher mw2 = java.util.regex.Pattern.compile("<i>(.{0,8}级)</i>").matcher(b);
                    wind = w1 + (mw2.find() ? mw2.group(1) : "");
                }
                out.add(new String[]{label, date, wEmoji(dc.substring(1)), wea, hi, lo, wEmoji(nc.substring(1)), wind});
            } catch (Throwable ignored) {}
        }
    }

    private static String extractVar(String page, String name) {
        int i = page.indexOf("var " + name + " ");
        if (i < 0) i = page.indexOf("var " + name + "=");
        if (i < 0) throw new RuntimeException("no " + name);
        int eq = page.indexOf('=', i);
        int end = page.indexOf(";var ", eq);
        if (end < 0) end = page.length();
        return page.substring(eq + 1, end).trim();
    }

    private String httpGet(String u, String referer) throws Throwable {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        if (referer != null) c.setRequestProperty("Referer", referer);
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private String httpGet(String u) throws Throwable { return httpGet(u, null); }

    private void buildWeather(String city, String type, String curT, String mn, String mx, String hum, String wind, String press, String vis, String aqi, String[][] indices, java.util.List<String[]> days) {
        TextView t2 = findViewById(R.id.lifeTemp);
        t2.setText(city + " · 点击切换");
        android.widget.LinearLayout grid = findViewById(R.id.lifeGrid);
        grid.removeAllViews();
        // ===== 7天预报·趋势 (单View自绘, 一列一栏, 可点) =====
        if (days.size() > 1) {
            TextView h15 = new TextView(this);
            h15.setText("7天预报");
            h15.setTextColor(0xFF1F2329); h15.setTextSize(16); h15.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            android.widget.LinearLayout.LayoutParams hp0 = new android.widget.LinearLayout.LayoutParams(-2, -2);
            hp0.setMargins(dp(2), 0, 0, dp(6));
            grid.addView(h15, hp0);
            final int colW = dp(78);
            final int n = Math.min(days.size(), 7);
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
            hsv.setFillViewport(true);
            hsv.setHorizontalScrollBarEnabled(false);
            TrendView tv = new TrendView(this, n, colW, days, idx -> {
                String[] d = days.get(idx);
                new android.app.AlertDialog.Builder(this)
                    .setTitle(d[0] + " " + d[1])
                    .setMessage(d[2] + " " + d[3] + "\n高 " + d[4] + "° / 低 " + d[5] + "°\n夜间 " + d[6] + "\n" + d[7])
                    .setPositiveButton("知道了", null)
                    .show();
            });
            tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(colW * n, dp(300)));
            hsv.addView(tv);
            android.widget.LinearLayout.LayoutParams sp = new android.widget.LinearLayout.LayoutParams(-1, -2);
            grid.addView(hsv, sp);
        }
        // 当前天气卡 (照图2)
        android.widget.LinearLayout cur = new android.widget.LinearLayout(this);
        cur.setOrientation(android.widget.LinearLayout.VERTICAL);
        cur.setBackgroundResource(R.drawable.bg_input_flat);
        int pd = dp(12); cur.setPadding(pd, pd, pd, pd);
        android.widget.LinearLayout big = new android.widget.LinearLayout(this);
        big.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        big.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tw = new TextView(this);
        tw.setText(type + " " + mn + "~" + mx + "°C");
        tw.setTextColor(0xFF1F2329); tw.setTextSize(22); tw.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        big.addView(tw);
        TextView tnow = new TextView(this);
        tnow.setText("  当前 " + curT + "°");
        tnow.setTextColor(0xFF9AA0A8); tnow.setTextSize(15);
        big.addView(tnow);
        cur.addView(big);
        android.widget.LinearLayout meta = new android.widget.LinearLayout(this);
        meta.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        meta.setGravity(android.view.Gravity.CENTER);
        String[][] ms = {{"湿度", hum}, {"风力", wind}, {"气压", press}, {"能见度", vis}, {"空气", aqi}};
        for (String[] m : ms) {
            android.widget.LinearLayout col = new android.widget.LinearLayout(this);
            col.setOrientation(android.widget.LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            TextView v = new TextView(this);
            v.setText(m[1]); v.setTextColor(0xFF1F2329); v.setTextSize(13); v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            v.setGravity(android.view.Gravity.CENTER);
            col.addView(v);
            TextView k = new TextView(this);
            k.setText(m[0]); k.setTextColor(0xFF9AA0A8); k.setTextSize(11); k.setGravity(android.view.Gravity.CENTER);
            col.addView(k);
            meta.addView(col, cp);
        }
        android.widget.LinearLayout.LayoutParams mp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(10), 0, 0);
        cur.addView(meta, mp);
        grid.addView(cur, new android.widget.LinearLayout.LayoutParams(-1, -2));
        // 8格生活指数
        String[][] DATA = indices;
        TextView h8 = new TextView(this);
        h8.setText("生活指数");
        h8.setTextColor(0xFF1F2329); h8.setTextSize(16); h8.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.widget.LinearLayout.LayoutParams hp = new android.widget.LinearLayout.LayoutParams(-2, -2);
        hp.setMargins(dp(2), dp(14), 0, dp(6));
        grid.addView(h8, hp);
        android.widget.LinearLayout grow = null;
        for (int i = 0; i < DATA.length; i++) {
            if (i % 4 == 0) {
                grow = new android.widget.LinearLayout(this);
                grow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                grid.addView(grow, new android.widget.LinearLayout.LayoutParams(-1, -2));
            }
            android.widget.LinearLayout cell = new android.widget.LinearLayout(this);
            cell.setOrientation(android.widget.LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER);
            cell.setBackgroundResource(R.drawable.bg_input_flat);
            int cpd = dp(10); cell.setPadding(cpd, dp(12), cpd, dp(10));
            android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
            cp.setMargins(dp(3), dp(3), dp(3), dp(3));
            TextView tv = new TextView(this);
            tv.setText(DATA[i][0]);
            tv.setTextColor(0xFF1F2329); tv.setTextSize(13); tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tv.setGravity(android.view.Gravity.CENTER); tv.setSingleLine(true);
            cell.addView(tv);
            TextView tv2 = new TextView(this);
            tv2.setText(DATA[i][1]); tv2.setTextColor(0xFF9AA0A8); tv2.setTextSize(12);
            tv2.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams tp2 = new android.widget.LinearLayout.LayoutParams(-2, -2);
            tp2.setMargins(0, dp(4), 0, 0);
            cell.addView(tv2, tp2);
            grow.addView(cell, cp);
        }
    }

    private void pickCity() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("输入城市名, 如 温州");
        et.setSingleLine(true);
        et.setTextSize(15);
        int pd = dp(20);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_dialog_dark_round);
        box.setPadding(pd, dp(24), pd, dp(16));
        TextView ti = new TextView(this);
        ti.setText("选择地区");
        ti.setTextColor(0xFFF2F2F2); ti.setTextSize(19); ti.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(ti);
        android.widget.LinearLayout.LayoutParams ep = new android.widget.LinearLayout.LayoutParams(-1, -2);
        ep.setMargins(0, dp(16), 0, 0);
        et.setTextColor(0xFFF2F2F2); et.setHintTextColor(0xFF777777);
        et.setBackgroundResource(R.drawable.bg_input_dark);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(et, ep);
        android.widget.LinearLayout lr = new android.widget.LinearLayout(this);
        lr.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        box.addView(lr, lp);
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dlg.setContentView(box, new android.view.ViewGroup.LayoutParams(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.84),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        Runnable[] holder = new Runnable[1];
        holder[0] = () -> {
            String q = et.getText().toString().trim();
            if (q.length() == 0) return;
            ti.setText("搜索中…");
            lr.removeAllViews();
            new Thread(() -> {
                try {
                    String[] tries = {q, q + "市", q.endsWith("市") ? q.substring(0, q.length() - 1) : q};
                    java.util.List<String[]> list = new java.util.ArrayList<>();
                    for (String t : tries) {
                        if (t.length() == 0) continue;
                        try {
                            String g = httpGet("http://toy1.weather.com.cn/search?cityname=" + java.net.URLEncoder.encode(t, "UTF-8"), "http://www.weather.com.cn/");
                            java.util.regex.Matcher mr = java.util.regex.Pattern.compile("(101\\d{6})~[^~]*~([^~]+)~").matcher(g);
                            while (mr.find()) {
                                String code = mr.group(1), nm = mr.group(2);
                                boolean dup = false;
                                for (String[] x : list) if (x[0].equals(code)) { dup = true; break; }
                                if (!dup) list.add(new String[]{code, nm});
                            }
                        } catch (Throwable ignored) {}
                        if (!list.isEmpty()) break;
                    }
                    if (list.isEmpty()) throw new Exception("none");
                    uiTick.post(() -> {
                        ti.setText("选择地区");
                        for (String[] o : list) {
                            TextView tv = new TextView(this);
                            tv.setText(o[1]);
                            tv.setTextColor(0xFFF2F2F2); tv.setTextSize(15);
                            tv.setPadding(dp(8), dp(12), dp(8), dp(12));
                            String cd = o[0], cn = o[1];
                            tv.setOnClickListener(v -> {
                                getSharedPreferences("settings", MODE_PRIVATE).edit()
                                    .putString("lifeCityCode", cd)
                                    .putString("lifeCityName", cn).apply();
                                dlg.dismiss();
                                lifeFetched = false; lastWeatherAt = 0;
                                refreshLifeCard();
                            });
                            lr.addView(tv);
                        }
                    });
                } catch (Throwable e) {
                    uiTick.post(() -> ti.setText("没找到, 换个名字试试"));
                }
            }, "geo").start();
        };
        et.setOnEditorActionListener((v, a, e) -> { holder[0].run(); return true; });
        TextView btn = new TextView(this);
        btn.setText("搜索");
        btn.setTextColor(0xFFFF6A1E); btn.setTextSize(17); btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams bp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(0, dp(16), 0, 0);
        btn.setOnClickListener(v -> holder[0].run());
        box.addView(btn, bp);
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

    /** 定位未开提醒: 确定去开定位, 取消不打拍 (最简系统弹窗, 同下载弹窗样式) */
    private void showLocReminder() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("提醒")
            .setMessage("该功能需使用定位服务，是否开启？")
            .setPositiveButton("确定", (d, w) -> {
                try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Throwable ignored) {}
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private static String shortWea(String wea) {
        if (wea == null) return "";
        return wea.replace("转", "/").replace("雨夹雪", "雨雪");
    }

    /** 7天趋势卡: 画布全自绘 (列对位精确, 点击按坐标取列) */
    private class TrendView extends android.view.View {
        private final int cols, colW;
        private final java.util.List<String[]> days;
        private final android.graphics.Paint line = new android.graphics.Paint(), dot = new android.graphics.Paint();
        private final android.graphics.Paint txt = new android.graphics.Paint(), emo = new android.graphics.Paint(), sub = new android.graphics.Paint();
        private final java.util.function.IntConsumer onClick;
        private float downX, downY;
        TrendView(android.content.Context c, int cols, int colW, java.util.List<String[]> days, java.util.function.IntConsumer onClick) {
            super(c);
            this.cols = cols; this.colW = colW; this.days = days; this.onClick = onClick;
            line.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStrokeWidth(dp(2)); line.setAntiAlias(true); line.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            dot.setAntiAlias(true);
            txt.setColor(0xFF1F2329); txt.setAntiAlias(true); txt.setTextAlign(android.graphics.Paint.Align.CENTER);
            txt.setTextSize(dp(13)); txt.setFakeBoldText(true);
            emo.setAntiAlias(true); emo.setTextAlign(android.graphics.Paint.Align.CENTER);
            sub.setColor(0xFF9AA0A8); sub.setAntiAlias(true); sub.setTextAlign(android.graphics.Paint.Align.CENTER);
            sub.setTextSize(dp(10));
        }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            super.onDraw(cv);
            if (days == null || days.isEmpty()) return;
            int n = Math.min(cols, days.size());
            float y = dp(10);
            for (int i = 0; i < n; i++) {
                float cx = i * colW + colW / 2f;
                String[] d = days.get(i);
                cv.drawText(d[0], cx, y + dp(12), txt);
                cv.drawText(d[1], cx, y + dp(28), sub);
            }
            float emojiY = y + dp(52);
            for (int i = 0; i < n; i++) {
                String[] d = days.get(i);
                emo.setTextSize(dp(20));
                cv.drawText(d[2], i * colW + colW / 2f, emojiY, emo);
            }
            // 折线区
            float ly0 = emojiY + dp(16), lh = dp(74);
            float hiMin = Float.MAX_VALUE, hiMax = -Float.MAX_VALUE, loMin = hiMin, loMax = hiMax;
            for (int i = 0; i < n; i++) {
                try { float v = Float.parseFloat(days.get(i)[4]); hiMin = Math.min(hiMin, v); hiMax = Math.max(hiMax, v); } catch (Throwable ignored) {}
                try { float v = Float.parseFloat(days.get(i)[5]); loMin = Math.min(loMin, v); loMax = Math.max(loMax, v); } catch (Throwable ignored) {}
            }
            if (hiMin == Float.MAX_VALUE || loMin == Float.MAX_VALUE) return;
            if (hiMax == hiMin) hiMax += 1;
            if (loMax == loMin) loMax += 1;
            float[] hy = new float[n], lyy = new float[n];
            android.graphics.Path hp = new android.graphics.Path(), lp = new android.graphics.Path();
            for (int i = 0; i < n; i++) {
                float cx = i * colW + colW / 2f;
                try { hy[i] = ly0 + lh * (1 - (Float.parseFloat(days.get(i)[4]) - hiMin) / (hiMax - hiMin)); } catch (Throwable e) { hy[i] = ly0; }
                try { lyy[i] = ly0 + lh * (1 - (Float.parseFloat(days.get(i)[5]) - loMin) / (loMax - loMin)); } catch (Throwable e) { lyy[i] = ly0 + lh; }
                if (i == 0) { hp.moveTo(cx, hy[i]); lp.moveTo(cx, lyy[i]); }
                else { hp.lineTo(cx, hy[i]); lp.lineTo(cx, lyy[i]); }
            }
            line.setColor(0xFFFF8A3C); cv.drawPath(hp, line);
            line.setColor(0xFF5BA8F5); cv.drawPath(lp, line);
            for (int i = 0; i < n; i++) {
                float cx = i * colW + colW / 2f;
                dot.setColor(0xFFFF8A3C); cv.drawCircle(cx, hy[i], dp(3), dot);
                txt.setTextSize(dp(12)); txt.setColor(0xFFE5762A); txt.setFakeBoldText(false);
                cv.drawText(days.get(i)[4] + "°", cx, hy[i] - dp(7), txt);
                dot.setColor(0xFF5BA8F5); cv.drawCircle(cx, lyy[i], dp(3), dot);
                txt.setColor(0xFF4A90D9);
                cv.drawText(days.get(i)[5] + "°", cx, lyy[i] + dp(16), txt);
                txt.setFakeBoldText(true);
            }
            // 夜间 emoji
            float ny = ly0 + lh + dp(26);
            for (int i = 0; i < n; i++) {
                emo.setTextSize(dp(16));
                cv.drawText(days.get(i)[6], i * colW + colW / 2f, ny, emo);
            }
            // 风向风力两行
            float wy = ny + dp(16);
            for (int i = 0; i < n; i++) {
                String w = days.get(i)[7];
                String w1 = w.replaceAll("[<≤]?\\d+级$", "");
                String w2 = w.substring(Math.min(w1.length(), w.length()));
                cv.drawText(w1, i * colW + colW / 2f, wy, sub);
                cv.drawText(w2, i * colW + colW / 2f, wy + dp(13), sub);
            }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) { downX = e.getX(); downY = e.getY(); return true; }
            if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                if (Math.abs(e.getX() - downX) < dp(10) && Math.abs(e.getY() - downY) < dp(10)) {
                    int col = (int) (e.getX() / colW);
                    if (col >= 0 && col < Math.min(cols, days.size()) && onClick != null) onClick.accept(col);
                }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    @Override
    protected void onDestroy() {
        // 页面关闭不打断: 服务继续后台打拍; 背景音跟随页面销毁
        cancelRepeat();
        stopBgPlay();
        super.onDestroy();
    }
}
