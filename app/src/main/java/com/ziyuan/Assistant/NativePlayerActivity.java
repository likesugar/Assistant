package com.ziyuan.Assistant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import xyz.doikki.videoplayer.ijk.IjkPlayer;
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
        // 双击暂停 / 长按倍速：同样在 Activity 层监听（子 View 会消费触摸，root 的 OnTouchListener 收不到）
        try {
            if (gd != null) {
                gd.onTouchEvent(ev);
                if ((ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)
                        && longPressSpeed) {
                    longPressSpeed = false;
                    try { videoView.setSpeed(playSpeed); } catch (Throwable ignored) {}  // 松开恢复设定倍速
                }
            }
        } catch (Throwable ignored) { }
        return super.dispatchTouchEvent(ev);
    }

    private VideoView videoView;
    private StandardVideoController controller;
    private String url;
    private String title;
    private boolean backgroundPlay = false;
    private GestureDetector gd;

    private float playSpeed = 1.0f;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};
    private boolean longPressSpeed = false;   // 长按临时倍速中
    private TopRightButtonsComponent floatBtns;

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
        // 播放内核自动选择：Media3 ExoPlayer 优先（默认硬解省电、HLS 兼容好）；
        // 仅 RTMP/RTSP 拉流与冷门老格式(MPEG-2/WMV/RMVB 等)自动切 Ijk（FFmpeg 全能软解兜底）
        if (needsIjkKernel(url)) {
            // IjkPlayer 内核（FFmpeg）；子类 setOptions 里开启 MediaCodec 硬件解码，省电不发热
            videoView.setPlayerFactory(new IjkPlayerFactory() {
                @Override
                public IjkPlayer createPlayer(android.content.Context context) {
                    return new IjkPlayer(context) {
                        @Override
                        public void setOptions() {
                            super.setOptions();
                            try {
                                if (mMediaPlayer != null) {
                                    mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                                    mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
                                    mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
                                }
                            } catch (Throwable ignored) {}
                        }
                    };
                }
            });
        }
        controller = new StandardVideoController(this);
        controller.addDefaultControlComponent(title != null ? title : "资源嗅探", url != null && url.contains(".m3u8"));
        videoView.setVideoController(controller);
        // 双击播放/暂停统一由 Activity 层手势处理（先于子 View 触发，最可靠）。
        // 必须禁用 GestureVideoController 默认双击，否则 Activity 先 pause、控制器随后 togglePlay
        // 立即 resume，两次切换相互抵消，表现为“双击失效”。
        try { controller.setDoubleTapTogglePlayEnabled(false); } catch (Throwable ignored) { }
        controller.setOnKernelSwitchListener(new StandardVideoController.OnKernelSwitchListener() {
            @Override
            public void onKernelSwitch() { toggleBackgroundPlay(); }
        });
        // 控制层左上角：width: height:（随控制条显隐）
        controller.addControlComponent(new SizeComponent(videoView));
        // 控制层右上角：小窗 + 倍速 横排一行（随控制条显隐）
        floatBtns = new TopRightButtonsComponent(this);
        floatBtns.onSpeed = new View.OnClickListener() {
            @Override public void onClick(View v) { showSpeedMenu(); }
        };
        if (Build.VERSION.SDK_INT >= 26
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            floatBtns.onPip = new View.OnClickListener() {
                @Override public void onClick(View v) { enterPip(); }
            };
        }
        controller.addControlComponent(floatBtns);

        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupGestures();       // 双击暂停 / 长按倍速

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

    /**
     * 是否需要 Ijk 内核（FFmpeg 全能软解）：
     * 1) RTMP/RTSP 拉流 —— Media3 未集成对应模块，只有 Ijk 支持；
     * 2) 冷门老格式（MPEG-2/WMV/RMVB/AVI/ASF/VOB 等）—— 系统硬解不支持，Ijk 软解兜底。
     * 其余一律使用 Media3 ExoPlayer（默认硬解，省电低发热）。
     */
    static boolean needsIjkKernel(String url) {
        if (url == null || url.length() == 0) return false;
        String u = url.toLowerCase();
        if (u.startsWith("rtmp://") || u.startsWith("rtmps://") || u.startsWith("rtsp://")) return true;
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        String[] coldExts = {".wmv", ".rmvb", ".rm", ".mpg", ".mpeg", ".mpe", ".vob", ".asf", ".avi", ".swf", ".3gp", ".m2ts"};
        for (String ext : coldExts) {
            if (path.endsWith(ext)) return true;
        }
        return false;
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
    private void showSpeedMenu() {
        PopupMenu pm = new PopupMenu(this, floatBtns != null ? floatBtns.speedBtn : null);
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
        if (floatBtns != null && floatBtns.speedBtn != null) floatBtns.speedBtn.setText(speedLabel(s));
        try { videoView.setSpeed(s); } catch (Throwable ignored) { }
    }

    // ---------- 手势：双击暂停 / 长按倍速 ----------
    private void setupGestures() {
        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                try {
                    if (videoView.isPlaying()) videoView.pause();
                    else videoView.resume();
                } catch (Throwable ignored) {}
                return true;
            }
            @Override
            public void onLongPress(MotionEvent e) {
                try { longPressSpeed = true; videoView.setSpeed(2.0f);
                      Toast.makeText(NativePlayerActivity.this, "2x 快进中，松开恢复", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static String speedLabel(float s) {
        if (s == (int) s) return ((int) s) + "x";
        return s + "x";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- 画中画 ----------
    private void enterPip() {
        if (Build.VERSION.SDK_INT >= 26
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
            } catch (Throwable e) {
                Toast.makeText(this, "画中画不可用（可能在系统设置中被关闭）", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "此设备不支持画中画", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // 切到别的 App / 按 Home：自动进入画中画小窗，边看边干别的
        if (isFinishing()) return;
        enterPip();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiP) {
        super.onPictureInPictureModeChanged(isInPiP);
        // 小窗内隐藏浮动按钮，避免 UI 错位
        if (floatBtns != null && floatBtns.getView() != null) {
            int vis = isInPiP ? View.INVISIBLE : View.VISIBLE;
            floatBtns.getView().setVisibility(vis);
            floatBtns.speedBtn.setVisibility(vis);
            if (floatBtns.pipBtn != null) floatBtns.pipBtn.setVisibility(vis);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 画中画模式下不暂停（小窗继续播）
        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode()) return;
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
