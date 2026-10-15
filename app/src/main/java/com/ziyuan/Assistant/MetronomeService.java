package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** 节拍器前台服务: 离开页面/息屏持续打拍 */
public class MetronomeService extends Service {
    public final static String ACTION_START = "metro.start";
    public final static String ACTION_UPDATE = "metro.update";
    public final static String ACTION_STOP = "metro.stop";
    public final static String ACTION_TIMER_STOP = "metro.timer_stop";
    public final static String EXTRA_BPM = "bpm";
    public final static String EXTRA_NOTES = "notes";
    public final static String EXTRA_AUDIO = "audio";
    public final static String EXTRA_BOOSTER = "booster";
    public final static String EXTRA_ACCENT = "accent_first";
    public final static String EXTRA_SUB = "sub";
    public final static String EXTRA_TIMER = "timerMin";
    private final static String CH_ID = "metro_channel";

    private static MetroEngine engine;

    /** 供 UI 轮询当前拍 (播放中) */
    public static boolean isRunning() {
        MetronomeService sv = inst;
        return sv != null && sv.engine != null && sv.engine.isPlaying();
    }

    public static int currentBeat(int notes) {
        MetroEngine e = engine;
        return (e != null && e.isPlaying()) ? e.currentTick(notes) : -1;
    }
    private static MetronomeService inst;
    private TianyiController tianyi;
    private boolean tianyiOn = false, gpsOn = true;
    private GpsManager gpsMgr;
    private StepCadenceManager stepMgr;
    private MetroAudioManager audioManager;
    private final android.os.Handler stopHandler = new android.os.Handler();

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        audioManager = new MetroAudioManager(this);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "节拍器", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNote(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CH_ID) : new Notification.Builder(this);
        android.content.Intent back = new android.content.Intent(this, MetronomeActivity.class);
        back.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0, back,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        return b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("节拍器")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        int bpm = intent.getIntExtra(EXTRA_BPM, 120);
        int notes = intent.getIntExtra(EXTRA_NOTES, 4);
        String audioKey = intent.getStringExtra(EXTRA_AUDIO);
        if (audioKey == null) audioKey = MetroConstant.AudioDefault;
        boolean booster = intent.getBooleanExtra(EXTRA_BOOSTER, false);
        boolean accentFirst = intent.getBooleanExtra(EXTRA_ACCENT, false);
        android.content.SharedPreferences tp = getSharedPreferences("settings", MODE_PRIVATE);
tianyiOn = tp.getBoolean("metroTianyiCheer", true) || tp.getBoolean("metroTianyiStage", true)
            || tp.getBoolean("metroTianyiGps", true);
        gpsOn = tp.getBoolean("metroTianyiGps", true);
        if (intent.getBooleanExtra("settings_changed", false)) {
            android.content.SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
            audioKey = p.getString("metroAudio", MetroConstant.AudioDefault);
            booster = p.getBoolean("metroBooster", false);
            accentFirst = p.getBoolean("metroAccent", false);
        }
        int sub = intent.getIntExtra(EXTRA_SUB, 1);
        String text = bpm + " BPM · " + notes + "拍";
        if (ACTION_STOP.equals(action)) {
            final TianyiController t = tianyi;
            if (t != null && t.isRunning()) {
                if (engine != null) engine.pause(); // 节拍先停
                t.onPaused(); // 再播语音
                final MetroEngine e = engine;
                tianyi = null;
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    t.stop();
                    if (e != null) e.close();
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                }, 1800);
                return START_NOT_STICKY;
            }
            if (tianyi != null) { tianyi.stop(); tianyi = null; }
            if (gpsMgr != null) { gpsMgr.stop(); gpsMgr = null; }
        if (stepMgr != null) { stepMgr.stop(); stepMgr = null; }
            if (engine != null) engine.close();
            engine = null;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (engine == null) {
            if (!ACTION_START.equals(action)) { stopSelf(); return START_NOT_STICKY; } // 停止后不因改参数复活
            engine = new MetroEngine();
            engine.start();
        }
        try {
            if (ACTION_START.equals(action) || engine.isPlaying() == false) {
                engine.setAudioData(audioManager.getAudio(audioKey));
                engine.setBpm(bpm); engine.setNotes(notes); engine.setBooster(booster); engine.setAccentFirst(accentFirst); engine.setSubdiv(sub);
            } else {
                engine.setBpm(bpm); engine.setNotes(notes); engine.setBooster(booster); engine.setAccentFirst(accentFirst); engine.setSubdiv(sub);
                try { engine.setAudioData(audioManager.getAudio(audioKey)); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(0x1, buildNote(text), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(0x1, buildNote(text));
        }
        boolean introPending = false;
        if (tianyiOn) {
            if (tianyi == null || !tianyi.isRunning()) {
                android.content.SharedPreferences tsp = getSharedPreferences("settings", MODE_PRIVATE);
                if (gpsOn) {
                    gpsMgr = new GpsManager(this, null);
                    gpsMgr.start();
                    stepMgr = new StepCadenceManager();
                    stepMgr.start(this);
                }
                tianyi = new TianyiController(this, engine,
                    tsp.getBoolean("metroTianyiCheer", true),
                    tsp.getBoolean("metroTianyiStage", true));
                tianyi.gpsMgr = gpsMgr; tianyi.stepMgr = stepMgr;
                if (tsp.getBoolean("metroTianyiStage", true)) {
                    tianyi.introDone = () -> { MetroEngine en = engine; if (en != null) en.play(); };
                    introPending = true; // 开场语音播完才响节拍
                }
                tianyi.start(); // introDone 必须在 start 前挂好(开场语音在 start 里播)
            }
        } else if (tianyi != null) {
            tianyi.stop(); tianyi = null;
            if (gpsMgr != null) { gpsMgr.stop(); gpsMgr = null; }
        if (stepMgr != null) { stepMgr.stop(); stepMgr = null; }
            if (stepMgr != null) { stepMgr.stop(); stepMgr = null; }
        }
        if (!introPending) engine.play();
        final int timerMin = intent.getIntExtra(EXTRA_TIMER, 0);
        stopHandler.removeCallbacksAndMessages(null);
        if (timerMin > 0) {
            stopHandler.postDelayed(() -> {
                if (engine != null) { engine.close(); engine = null; }
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }, timerMin * 60_000L);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (tianyi != null) { tianyi.stop(); tianyi = null; }
        if (gpsMgr != null) { gpsMgr.stop(); gpsMgr = null; }
        if (stepMgr != null) { stepMgr.stop(); stepMgr = null; }
        inst = null;
        if (engine != null) { engine.close(); engine = null; } super.onDestroy();
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
