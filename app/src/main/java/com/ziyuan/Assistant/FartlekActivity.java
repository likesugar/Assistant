package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 法特莱克跑训练器（参照 xuxubo/farklet 移植）：跑步/步行自定义时长+循环次数，
 *  实时倒计时、阶段切换邦戈鼓提示(强=开跑/弱=步行)、进度条+轮次。 */
public class FartlekActivity extends Activity {

    private int runTime = 60, walkTime = 30, cycles = 5;
    private boolean running = false, walking = false;
    private int elapsed = 0, cycle = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            elapsed++;
            int total = totalDuration();
            if (elapsed >= total) {
                elapsed = total; running = false; walking = false;
                play(true); play(true); // 结束双强鼓
                refresh();
                return;
            }
            int cyc = runTime + walkTime;
            int inCycle = elapsed % cyc;
            int newCycle = elapsed / cyc + 1;
            if (inCycle == runTime && !walking) {
                walking = true; cycle = newCycle; play(false); // 弱鼓=步行
            } else if (inCycle == 0 && walking && elapsed > 0) {
                walking = false; cycle = newCycle; play(true); // 强鼓=开跑
            }
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private SoundPool sp;
    private int sidHard = 0, sidSoft = 0;
    private LinearLayout listBox, phaseBox;
    private TextView tvPhase, tvRemain, tvCycle, tvTotal, tvBig;
    private android.widget.ProgressBar bar;
    private EditText inRun, inWalk, inCycles;
    private Button btnStart;
    private boolean dark;

    private int totalDuration() { return (runTime + walkTime) * cycles; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        Immersive.hide(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int bg = dark ? 0xFF000000 : 0xFFEEF4FF;
        int fg = dark ? 0xFFFFFFFF : 0xFF1F2329;
        int sub = dark ? 0xFF9AA3AE : 0xFF8A94A6;
        getWindow().setNavigationBarColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));

        SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        runTime = p.getInt("fkRun", 60); walkTime = p.getInt("fkWalk", 30); cycles = p.getInt("fkCycles", 5);

        sp = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
                new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        try {
            sidHard = sp.load(getAssets().openFd("audios/bongo_drum_hard.mp3"), 1);
            sidSoft = sp.load(getAssets().openFd("audios/bongo_drum_soft.mp3"), 1);
        } catch (Throwable t) { }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(40));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("← 返回"); back.setTextSize(16); back.setTextColor(0xFFE6B84C);
        back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("法特莱克跑训练器"); title.setTextSize(18); title.setTextColor(fg);
        TextView spacer = new TextView(this);
        top.addView(back);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(title);
        root.addView(top);

        // 大倒计时 + 阶段
        tvPhase = new TextView(this);
        tvPhase.setTextSize(16); tvPhase.setTextColor(sub); tvPhase.setGravity(Gravity.CENTER);
        tvPhase.setPadding(0, dp(30), 0, dp(4));
        tvBig = new TextView(this);
        tvBig.setTextSize(64); tvBig.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvBig.setTextColor(fg); tvBig.setGravity(Gravity.CENTER);
        tvCycle = new TextView(this);
        tvCycle.setTextSize(15); tvCycle.setTextColor(sub); tvCycle.setGravity(Gravity.CENTER);
        tvCycle.setPadding(0, dp(8), 0, 0);
        root.addView(tvPhase);
        root.addView(tvBig);
        root.addView(tvCycle);

        bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(10));
        blp.topMargin = dp(20);
        bar.setLayoutParams(blp);
        root.addView(bar);

        // 参数
        root.addView(label("训练设置", sub, 26, 0));
        LinearLayout cardP = card(bg);
        cardP.addView(numRow("跑步 (秒)", runTime, v -> { runTime = clamp(v, 5, 3600); saveP(); }));
        cardP.addView(numRow("步行 (秒)", walkTime, v -> { walkTime = clamp(v, 5, 3600); saveP(); }));
        cardP.addView(numRow("循环次数", cycles, v -> { cycles = clamp(v, 1, 99); saveP(); }));
        root.addView(cardP);

        // 说明
        root.addView(label("提示: 开跑=邦戈鼓(强), 切步行=邦戈鼓(弱), 结束=强鼓两声", sub, 14, 0));

        // 按钮
        btnStart = new Button(this);
        btnStart.setTextSize(16); btnStart.setTextColor(Color.WHITE);
        btnStart.setBackgroundResource(dark ? R.drawable.bg_btn_md3_dark : R.drawable.bg_btn_md3);
        btnStart.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(20);
        btnStart.setLayoutParams(slp);
        btnStart.setOnClickListener(v -> toggle());
        root.addView(btnStart);

        setContentView(root);
        refresh();
    }

    private void toggle() {
        if (running) {
            running = false;
        } else {
            if (elapsed >= totalDuration()) elapsed = 0;
            running = true;
            if (elapsed == 0) { walking = false; cycle = 1; play(true); }
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, 1000);
        } else {
            handler.removeCallbacks(tick);
        }
        refresh();
    }

    private void play(boolean hard) {
        if (sp != null) sp.play(hard ? sidHard : sidSoft, 1f, 1f, 1, 0, 1f);
    }

    private String fmt(int s) {
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private void refresh() {
        int total = totalDuration();
        int cyc = runTime + walkTime;
        int inCycle = elapsed % cyc;
        int remain;
        if (elapsed >= total) remain = 0;
        else if (walking) remain = Math.max(0, walkTime - (inCycle - runTime));
        else remain = Math.max(0, runTime - inCycle);
        tvPhase.setText(running ? (walking ? "🚶 步行阶段" : "🏃 跑步阶段") : (elapsed >= total ? "训练完成" : "已暂停"));
        tvPhase.setTextColor(running ? (walking ? 0xFF34C759 : 0xFFFF6B35) : (dark ? 0xFF9AA3AE : 0xFF8A94A6));
        tvBig.setText(fmt(remain));
        tvCycle.setText("第 " + Math.min(cycle, cycles) + " / " + cycles + " 轮 · 总时长 " + fmt(total)
                + " · 已进行 " + fmt(elapsed));
        bar.setProgress((int) (total > 0 ? elapsed * 1000L / total : 0));
        btnStart.setText(running ? "暂停" : (elapsed > 0 && elapsed < total ? "继续" : "开始"));
        // 运行中锁参数
        inRun.setEnabled(!running); inWalk.setEnabled(!running); inCycles.setEnabled(!running);
    }

    private int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private void saveP() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putInt("fkRun", runTime).putInt("fkWalk", walkTime).putInt("fkCycles", cycles).apply();
        if (!running && elapsed > 0) elapsed = 0;
        cycle = 1; walking = false;
        refresh();
    }

    private TextView label(String t, int color, int topDp, int botDp) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextSize(13); tv.setTextColor(color);
        tv.setPadding(0, dp(topDp), 0, dp(botDp));
        return tv;
    }

    private LinearLayout card(int bg) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
        c.setPadding(dp(16), dp(4), dp(16), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    private View numRow(String name, int val, java.util.function.IntConsumer apply) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        int fg = dark ? 0xFFFFFFFF : 0xFF1F2329;
        TextView tv = new TextView(this);
        tv.setText(name); tv.setTextSize(15); tv.setTextColor(fg);
        row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
        EditText et = new EditText(this);
        et.setText(String.valueOf(val));
        et.setTextSize(15); et.setTextColor(fg);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setBackgroundResource(dark ? R.drawable.bg_input_dark : R.drawable.bg_input_flat);
        et.setMinWidth(dp(76)); et.setGravity(Gravity.CENTER);
        et.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable s) {
                try { apply.accept(Integer.parseInt(s.toString())); }
                catch (Throwable ignored) { }
            }
        });
        row.addView(et);
        if (name.contains("跑步")) inRun = et;
        else if (name.contains("步行")) inWalk = et;
        else inCycles = et;
        return row;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        super.onDestroy();
        running = false;
        handler.removeCallbacks(tick);
        if (sp != null) { sp.release(); sp = null; }
    }
}
