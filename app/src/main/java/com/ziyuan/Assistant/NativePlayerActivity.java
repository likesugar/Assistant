package com.ziyuan.Assistant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.ijk.IjkPlayerFactory;
import xyz.doikki.videoplayer.player.BaseVideoView;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videocontroller.StandardVideoController;

/** 单内核播放器：统一使用 IjkPlayer（FFmpeg 内核），普通 MP4 / m3u8 / flv / 抖音直播均走 Ijk */
public class NativePlayerActivity extends Activity {

    /** 双指缩放/拖动：在 Activity 层拦截，避免被控制器自身的触摸处理吃掉 */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        try { if (ZoomHelper.handle(this, ev)) return true; } catch (Throwable ignored) { }
        return super.dispatchTouchEvent(ev);
    }

    private VideoView videoView;
    private StandardVideoController controller;
    private String url;
    private String title;
    private boolean backgroundPlay = false;

    private TextView speedBtn;
    private float playSpeed = 1.0f;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            initPlayerPage(savedInstanceState);
        } catch (Throwable e) {
            showCrash(e);
        }
    }

    void showCrash(final Throwable e) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            java.io.File f = new java.io.File(dir, "crash.txt");
            java.io.FileWriter fw = new java.io.FileWriter(f, true);
            fw.append("\n==== " + new java.util.Date().toString() + " (player) ====\n");
            fw.append(android.util.Log.getStackTraceString(e));
            fw.close();
        } catch (Throwable e2) { }
        new GlassDialog.Builder(this)
                .setTitle("播放页闪退原因")
                .setCancelable(false)
                .setMessage(android.util.Log.getStackTraceString(e))
                .setPositiveButton("好", null)
                .show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    void initPlayerPage(Bundle savedInstanceState) {
        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        videoView = new VideoView(this);
        // 统一使用 IjkPlayer 内核（FFmpeg），所有地址都走 Ijk，不再回退系统 MediaPlayer
        videoView.setPlayerFactory(IjkPlayerFactory.create());
        controller = new StandardVideoController(this);
        controller.addDefaultControlComponent(title != null ? title : "资源嗅探", url != null && url.contains(".m3u8"));
        videoView.setVideoController(controller);
        controller.setOnKernelSwitchListener(new StandardVideoController.OnKernelSwitchListener() {
            @Override
            public void onKernelSwitch() { toggleBackgroundPlay(); }
        });
        // 控制层左上角：width: height:（随控制条显隐）
        controller.addControlComponent(new SizeComponent(videoView));

        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addSpeedButton(root);

        setContentView(root);
        Immersive.hide(this);
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // 旧地址过期(403)时热替换为同一路直播的最新地址
        try { String f = Or4Helper.freshFor(url); if (f != null) url = f; } catch (Throwable ignored) {}
        videoView.setUrl(url, douyinHeaders());
        videoView.start();
        // 倍速在准备完成后才真正生效（BaseVideoView.setSpeed 仅在播放态生效）
        videoView.addOnStateChangeListener(new BaseVideoView.OnStateChangeListener() {
            @Override public void onPlayerStateChanged(int playerState) { }
            @Override public void onPlayStateChanged(int playState) {
                if (playState == VideoView.STATE_PREPARED || playState == VideoView.STATE_PLAYING) {
                    try { videoView.setSpeed(playSpeed); } catch (Throwable ignored) { }
                }
            }
        });
        showPlayNote();

    }

    // ---------- 播放通知 ----------
    private void showPlayNote() {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel("xgj_player", "播放通知", android.app.NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
            android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(this, "xgj_player")
                    : new android.app.Notification.Builder(this);
            Intent back = new Intent(this, NativePlayerActivity.class)
                    .putExtra("url", url).putExtra("title", title);
            b.setSmallIcon(android.R.drawable.ic_media_play)
             .setContentTitle(title != null && !title.isEmpty() ? title : "正在播放")
             .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
             .setContentIntent(android.app.PendingIntent.getActivity(this, 5201, back,
                     android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
            nm.notify(5200, b.build());
        } catch (Throwable ignored) { }
    }

    private void cancelPlayNote() {
        try { ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(5200); } catch (Throwable ignored) { }
    }

    /** 耳机按钮：切后台播放模式。开启后退到桌面/切应用不停播 */
    void toggleBackgroundPlay() {
        backgroundPlay = !backgroundPlay;
        if (controller != null) controller.setBackgroundPlaySelected(backgroundPlay);
    }

    Map<String, String> douyinHeaders() {
        Map<String, String> hdrs = null;
        String extRef = getIntent() == null ? null : getIntent().getStringExtra("referer");
        if (extRef != null && !extRef.isEmpty()) {
            hdrs = new HashMap<String, String>();
            hdrs.put("Referer", extRef);
            hdrs.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        } else if (url != null && (url.contains("douyincdn") || url.contains("douyin"))) {
            hdrs = new HashMap<String, String>();
            hdrs.put("Referer", "https://live.douyin.com/");
            hdrs.put("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        }
        return hdrs;
    }

    // ---------- 倍速 ----------
    /** 右上角浮动倍速按钮（常驻，不受控制条显隐影响） */
    private void addSpeedButton(FrameLayout root) {
        speedBtn = new TextView(this);
        speedBtn.setText(speedLabel(playSpeed));
        speedBtn.setTextColor(0xFFFFFFFF);
        speedBtn.setTextSize(13);
        speedBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        speedBtn.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.topMargin = dp(44);
        lp.rightMargin = dp(12);
        root.addView(speedBtn, lp);
        speedBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSpeedMenu(); }
        });
    }

    private void showSpeedMenu() {
        PopupMenu pm = new PopupMenu(this, speedBtn);
        for (int i = 0; i < SPEEDS.length; i++) {
            pm.getMenu().add(0, i, i, speedLabel(SPEEDS[i]));
        }
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem item) {
                setPlaySpeed(SPEEDS[item.getItemId()]);
                return true;
            }
        });
        pm.show();
    }

    private void setPlaySpeed(float s) {
        playSpeed = s;
        if (speedBtn != null) speedBtn.setText(speedLabel(s));
        try { videoView.setSpeed(s); } catch (Throwable ignored) { }
    }

    private static String speedLabel(float s) {
        if (s == (int) s) return ((int) s) + "x";
        return s + "x";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!backgroundPlay) { videoView.pause(); cancelPlayNote(); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!backgroundPlay) { videoView.resume(); showPlayNote(); }
    }

    @Override
    protected void onDestroy() {
        cancelPlayNote();
        ZoomHelper.clear();
        try { videoView.release(); } catch (Throwable e) { }
        super.onDestroy();
    }
}
