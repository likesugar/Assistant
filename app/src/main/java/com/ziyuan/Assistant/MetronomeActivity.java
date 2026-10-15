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
    private int bpm = 120, notes = 4;
    private String audioKey = MetroConstant.AudioDefault;
    private boolean booster = false, playing = false;
    private TextView tvBpm, btnPlay;
    private final List<TextView> noteChips = new ArrayList<>();
    private MetroAudioManager audioManager;

    private File bgDir;
    private final List<File> bgFiles = new ArrayList<>();
    private MediaPlayer bgPlayer;
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
        bgVol = getSharedPreferences("settings", MODE_PRIVATE).getInt("metroBgVol", 40);
        tvBpm = (TextView) findViewById(R.id.tvBpm);
        btnPlay = (TextView) findViewById(R.id.btnPlay);
        findViewById(R.id.metroBack).setOnClickListener(v -> finish());
        findViewById(R.id.metroMenu).setOnClickListener(v -> showMenu());
        findViewById(R.id.btnBpmDown).setOnClickListener(v -> setBpm(bpm - 1));
        findViewById(R.id.btnBpmUp).setOnClickListener(v -> setBpm(bpm + 1));
        bindRepeat(findViewById(R.id.btnBpmDown), -1);
        bindRepeat(findViewById(R.id.btnBpmUp), 1);
        ((SeekBar) findViewById(R.id.sbBpm)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) { if (f) setBpm(p + MetroConstant.MinBPM); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        btnPlay.setOnClickListener(v -> togglePlay());
        buildChips();
        ((SeekBar) findViewById(R.id.sbBgVol)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) {
                if (f) setBgVol(p);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        ((SeekBar) findViewById(R.id.sbBgVol)).setProgress(bgVol);
        buildDots();
        buildBgList();
        themeAll();
        tvBpm.setText(String.valueOf(bpm));
        uiTick.postDelayed(tickRun, 80);
    }

    private void buildChips() {
        LinearLayout notesRow = (LinearLayout) findViewById(R.id.notesRow);
        LinearLayout rowA = new LinearLayout(this), rowB = new LinearLayout(this);
        rowA.setOrientation(LinearLayout.HORIZONTAL); rowB.setOrientation(LinearLayout.HORIZONTAL);
        rowB.setPadding(0, dp(8), 0, 0);
        int[] nsA = {1, 2, 3, 4}, nsB = {5, 6, 7, 8, 9};
        for (int[] grp : new int[][]{nsA, nsB}) {
            for (int n : grp) {
                TextView t = chip(String.valueOf(n), 14);
                t.setOnClickListener(v -> { notes = n; refreshChips(); buildDots(); push(false); });
                (grp == nsA ? rowA : rowB).addView(t);
                noteChips.add(t);
                t.setTag(n);
            }
        }
        notesRow.setOrientation(LinearLayout.VERTICAL);
        notesRow.addView(rowA); notesRow.addView(rowB);
        refreshChips();
    }

    private String displayName(String k) {
        if (MetroConstant.AudioDefault.equals(k)) return "默认";
        if (MetroConstant.AudioBassDrum.equals(k)) return "底鼓";
        if (MetroConstant.AudioClap.equals(k)) return "拍手";
        if (MetroConstant.AudioClaves.equals(k)) return "响棒";
        if (MetroConstant.AudioRimshot.equals(k)) return "边击";
        if (MetroConstant.AudioJog.equals(k)) return "慢跑";
        return k;
    }

    private void refreshChips() {
        for (TextView t : noteChips) styleChip(t, ((Integer) t.getTag()) == notes);
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
        if (v < MetroConstant.MinBPM) v = MetroConstant.MinBPM;
        if (v > MetroConstant.MaxBPM) v = MetroConstant.MaxBPM;
        bpm = v;
        tvBpm.setText(String.valueOf(bpm));
        ((SeekBar) findViewById(R.id.sbBpm)).setProgress(bpm - MetroConstant.MinBPM);
        push(false);
    }

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
                int b = MetronomeService.currentBeat(notes);
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
        for (int i = 0; i < notes; i++) {
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
        List<String> items = new ArrayList<>(Arrays.asList(
            "音色 (" + displayName(audioKey) + ")",
            bgDownloading ? "下载背景音..." : "下载背景音",
            (keepScreen ? "关闭" : "打开") + "屏幕常亮",
            (booster ? "关闭" : "打开") + "音量增强",
            "设置定时器" + (timerMin > 0 ? " (" + timerMin + "分钟)" : "")
        ));
        new android.app.AlertDialog.Builder(this)
            .setTitle("节拍器")
            .setItems(items.toArray(new String[0]), (d, w) -> {
                if (w == 0) showAudioPicker();
                else if (w == 1) showDownloadDialog();
                else if (w == 2) { keepScreen = !keepScreen; applyKeepScreen(); }
                else if (w == 3) { booster = !booster; push(playing); }
                else showTimerPicker();
            })
            .show();
    }

    // ---------- 背景音 ----------

    private void setBgVol(int v) {
        bgVol = v;
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("metroBgVol", v).apply();
        if (bgPlayer != null) {
            float f = v / 100f;
            try { bgPlayer.setVolume(f, f); } catch (Throwable ignored) {}
        }
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
            t.setBackgroundResource(R.drawable.bg_chip_off);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> toggleBgPlay(f, t));
            bgList.addView(t);
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
        if (bgPlayer != null && bgPlayingView == view) {
            stopBgPlay();
            return;
        }
        stopBgPlay();
        try {
            bgPlayer = new MediaPlayer();
            bgPlayer.setDataSource(f.getAbsolutePath());
            bgPlayer.setLooping(true);
            float v = bgVol / 100f;
            bgPlayer.setVolume(v, v);
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
        if (name.isEmpty()) name = "背景音";
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
        it.putExtra(MetronomeService.EXTRA_TIMER, timerMin);
        if (start) startForegroundService(it); else startService(it);
        btnPlay.setText(playing ? "■ 停止" : "▶ 开始打拍");
    }

    private void themeAll() {
        boolean dk = dark();
        findViewById(R.id.metroRoot).setBackgroundColor(dk ? (oled() ? 0xFF000000 : 0xFF11151D) : 0xFFEEF4FF);
        int card = dk ? (oled() ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3;
        findViewById(R.id.metroCardBpm).setBackgroundResource(card);
        findViewById(R.id.metroCardNotes).setBackgroundResource(card);
        findViewById(R.id.metroCardBg).setBackgroundResource(card);
        findViewById(R.id.metroCardDots).setBackgroundResource(card);
        int title = color(0xFFE8ECF4, 0xFF1F2329);
        int sub = color(0xFF9AA3AE, 0xFF8A94A6);
        int accent = color(0xFFE8C87A, 0xFFB8860B);
        for (int id : new int[]{R.id.tvBpm}) ((TextView) findViewById(id)).setTextColor(title);
        for (int id : new int[]{R.id.metroBack, R.id.btnPlay}) ((TextView) findViewById(id)).setTextColor(accent);
        for (View v : new View[]{findViewById(R.id.metroCardBpm), findViewById(R.id.metroCardNotes), findViewById(R.id.metroCardBg)}) {
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
        applyKeepScreen();
    }

    @Override
    protected void onPause() {
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
