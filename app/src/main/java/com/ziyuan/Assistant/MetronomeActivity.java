package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** 节拍器 (参考 SimpleBeat, 引擎=预生成波形+AudioTrack流式) */
public class MetronomeActivity extends Activity {
    private int bpm = 120, notes = 4;
    private String audioKey = MetroConstant.AudioDefault;
    private boolean booster = false, playing = false;
    private TextView tvBpm, btnPlay;
    private final List<TextView> noteChips = new ArrayList<>();
    private final List<TextView> audioChips = new ArrayList<>();
    private MetroAudioManager audioManager;

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
        buildDots();
        themeAll();
        tvBpm.setText(String.valueOf(bpm));
        uiTick.postDelayed(tickRun, 80);
    }

    private void buildChips() {
        LinearLayout notesRow = (LinearLayout) findViewById(R.id.notesRow);
        LinearLayout audioRow = (LinearLayout) findViewById(R.id.audioRow);
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
        LinearLayout aRowA = new LinearLayout(this), aRowB = new LinearLayout(this);
        aRowA.setOrientation(LinearLayout.HORIZONTAL); aRowB.setOrientation(LinearLayout.HORIZONTAL);
        aRowB.setPadding(0, dp(8), 0, 0);
        List<String> ks = audioManager.getAudioList();
        for (int idx = 0; idx < ks.size(); idx++) {
            String k = ks.get(idx);
            TextView t = chip(displayName(k), 14);
            t.setOnClickListener(v -> { audioKey = k; refreshChips(); push(false); });
            (idx < 3 ? aRowA : aRowB).addView(t);
            audioChips.add(t);
            t.setTag(k);
        }
        audioRow.setOrientation(LinearLayout.VERTICAL);
        audioRow.addView(aRowA); audioRow.addView(aRowB);
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
        for (TextView t : audioChips) styleChip(t, t.getTag().equals(audioKey));
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
        String[] items = {
            (keepScreen ? "关闭" : "打开") + "屏幕常亮",
            (booster ? "关闭" : "打开") + "音量增强",
            "设置定时器" + (timerMin > 0 ? " (" + timerMin + "分钟)" : "")
        };
        new android.app.AlertDialog.Builder(this)
            .setTitle("节拍器")
            .setItems(items, (d, w) -> {
                if (w == 0) { keepScreen = !keepScreen; applyKeepScreen(); }
                else if (w == 1) { booster = !booster; push(playing); }
                else showTimerPicker();
            })
            .show();
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
        findViewById(R.id.metroCardAudio).setBackgroundResource(card);
        findViewById(R.id.metroCardDots).setBackgroundResource(card);
        int title = color(0xFFE8ECF4, 0xFF1F2329);
        int sub = color(0xFF9AA3AE, 0xFF8A94A6);
        int accent = color(0xFFE8C87A, 0xFFB8860B);
        for (int id : new int[]{R.id.tvBpm}) ((TextView) findViewById(id)).setTextColor(title);
        for (int id : new int[]{R.id.metroBack, R.id.btnPlay}) ((TextView) findViewById(id)).setTextColor(accent);
        for (View v : new View[]{findViewById(R.id.metroCardBpm), findViewById(R.id.metroCardNotes), findViewById(R.id.metroCardAudio)}) {
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
        // 页面关闭不打断: 服务继续后台打拍
        cancelRepeat();
        super.onDestroy();
    }
}
