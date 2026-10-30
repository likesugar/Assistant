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
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import xyz.doikki.videoplayer.player.AndroidMediaPlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videocontroller.StandardVideoController;

/** 单内核播放器：统一使用系统 MediaPlayer（AndroidMediaPlayer），零 .so 依赖，播放走系统解码器 */
public class NativePlayerActivity extends Activity {

    private VideoView videoView;
    private StandardVideoController controller;
    private String url;
    private String title;
    private boolean backgroundPlay = false;

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
        // 统一使用系统 MediaPlayer（AndroidMediaPlayer），不再依赖 Ijk/FFmpeg 内核
        videoView.setPlayerFactory(AndroidMediaPlayerFactory.create());
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

        setContentView(root);
        Immersive.hide(this);
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "无播放地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        videoView.setUrl(url, douyinHeaders());
        videoView.start();
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
        try { videoView.release(); } catch (Throwable e) { }
        super.onDestroy();
    }
}
