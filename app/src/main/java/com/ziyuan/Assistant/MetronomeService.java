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
    // ===== 法特莱克训练 (跑步/步行循环, 阶段切换邦戈鼓提示) =====
    public final static String EXTRA_FARTLEK = "fartlek";
    public final static String ACT_FK = "fartlek.tick";
    private final android.os.Handler fkHandler = new android.os.Handler();
    private boolean fkOn = false;
    private String fkPhase = "run";
    private int fkRun = 60, fkWalk = 30, fkCycles = 5, fkRemain = 0, fkCycle = 1, fkElapsed = 0, fkTotal = 0;
    private android.media.AudioTrack fkCueTrack;

    /** 播放切换提示音: 直接复用邦戈鼓 wav 的 PCM(强/弱), 与节拍引擎同路线 */
    private void fkCue(boolean hard) {
        try {
            byte[] beat = hard ? audioManager.getAudio(MetroConstant.AudioBongo).getDownbeat()
                               : audioManager.getAudio(MetroConstant.AudioBongo).getUpbeat();
            if (fkCueTrack != null) { try { fkCueTrack.stop(); fkCueTrack.release(); } catch (Throwable ignored) {} }
            int min = android.media.AudioTrack.getMinBufferSize(MetroConstant.SampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT);
            fkCueTrack = new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC,
                    MetroConstant.SampleRate, android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min, beat.length), android.media.AudioTrack.MODE_STATIC);
            fkCueTrack.write(beat, 0, beat.length);
            fkCueTrack.play();
        } catch (Throwable t) { }
    }

    private android.media.MediaPlayer fkVoice;

    /** 阶段语音播报: 资产 tianyi/xxx.wav (与洛天依语音同机制) */
    private void fkVoicePlay(String id) {
        try {
            if (fkVoice != null) { try { fkVoice.stop(); fkVoice.release(); } catch (Throwable ignored) {} fkVoice = null; }
            android.content.res.AssetFileDescriptor fd = getAssets().openFd("tianyi/" + id + ".wav");
            fkVoice = new android.media.MediaPlayer();
            fkVoice.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            fkVoice.prepare();
            fkVoice.start();
        } catch (Throwable t) { }
    }

    private void fkBroadcast(String phase) {
        android.content.Intent i = new android.content.Intent(ACT_FK);
        i.putExtra("phase", phase);
        i.putExtra("remain", fkRemain);
        i.putExtra("cycle", fkCycle);
        i.putExtra("cycles", fkCycles);
        i.putExtra("elapsed", fkElapsed);
        i.putExtra("total", fkTotal);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void startFartlek() {
        android.content.SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        fkRun = Math.max(5, p.getInt("fartlekRun", 60));
        fkWalk = Math.max(5, p.getInt("fartlekWalk", 30));
        fkCycles = Math.max(1, p.getInt("fartlekCycles", 5));
        fkOn = true; fkPhase = "run"; fkCycle = 1; fkElapsed = 0;
        fkTotal = (fkRun + fkWalk) * fkCycles; fkRemain = fkRun;
        if (engine != null) engine.play();
        fkCue(true);
        fkVoicePlay("paobu"); // 跑步开始
        fkBroadcast("run");
        fkHandler.removeCallbacks(fkTick);
        fkHandler.postDelayed(fkTick, 1000);
    }

    private void stopFartlek() {
        fkOn = false;
        fkHandler.removeCallbacks(fkTick);
        fkBroadcast("done");
    }

    private final Runnable fkTick = new Runnable() {
        @Override public void run() {
            if (!fkOn) return;
            fkElapsed++; fkRemain--;
            if (fkRemain <= 0) {
                if ("run".equals(fkPhase)) {
                    fkPhase = "walk"; fkRemain = fkWalk;
                    if (engine != null) engine.pause();
                    fkCue(false);
                    fkVoicePlay("kuaizou"); // 快走开始
                } else {
                    fkCycle++;
                    if (fkCycle > fkCycles) {
                        stopFartlek();
                        if (engine != null) engine.pause();
                        fkVoicePlay("wancheng"); // 训练完成
                        return;
                    }
                    fkPhase = "run"; fkRemain = fkRun;
                    if (engine != null) engine.play();
                    fkCue(true);
                    fkVoicePlay("paobu"); // 跑步开始
                }
            }
            fkBroadcast(fkPhase);
            fkHandler.postDelayed(this, 1000);
        }
    };

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
        // startForegroundService 后必须尽快 startForeground, 否则 5 秒超时系统杀进程闪退
        if (intent == null) {
            try { startForeground(0x1, buildNote("节拍器")); stopSelf(); } catch (Throwable ignored) {}
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_START.equals(action) || ACTION_UPDATE.equals(action)) {
            try {
                String t0 = intent.getIntExtra(EXTRA_BPM, 120) + " BPM · " + intent.getIntExtra(EXTRA_NOTES, 4) + "拍";
                if (Build.VERSION.SDK_INT >= 29)
                    startForeground(0x1, buildNote(t0), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                else startForeground(0x1, buildNote(t0));
            } catch (Throwable ignored) { }
        }
        int bpm = intent.getIntExtra(EXTRA_BPM, 120);
        int notes = intent.getIntExtra(EXTRA_NOTES, 4);
        String audioKey = intent.getStringExtra(EXTRA_AUDIO);
        if (audioKey == null) audioKey = MetroConstant.AudioDefault;
        boolean booster = intent.getBooleanExtra(EXTRA_BOOSTER, false);
        boolean accentFirst = intent.getBooleanExtra(EXTRA_ACCENT, false);
        android.content.SharedPreferences tp = getSharedPreferences("settings", MODE_PRIVATE);
tianyiOn = tp.getBoolean("metroTianyiCheer", true) || tp.getBoolean("metroTianyiStage", false)
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
            stopFartlek();
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
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(0x1, buildNote(text), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(0x1, buildNote(text));
            }
        } catch (Throwable ignored) { }
        boolean fk = intent.getBooleanExtra(EXTRA_FARTLEK, false);
        if (fk && !fkOn) startFartlek();
        else if (fk && fkOn) {
            // 参数变更: 重读配置, 当前阶段按时继续
            android.content.SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
            fkRun = Math.max(5, p.getInt("fartlekRun", 60));
            fkWalk = Math.max(5, p.getInt("fartlekWalk", 30));
            fkCycles = Math.max(1, p.getInt("fartlekCycles", 5));
        } else if (!fk && fkOn) stopFartlek();
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
                    tsp.getBoolean("metroTianyiStage", false));
                tianyi.gpsMgr = gpsMgr; tianyi.stepMgr = stepMgr;
                if (tsp.getBoolean("metroTianyiStage", false)) {
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
        stopFartlek();
        if (fkCueTrack != null) { try { fkCueTrack.stop(); fkCueTrack.release(); } catch (Throwable ignored) {} fkCueTrack = null; }
        if (fkVoice != null) { try { fkVoice.stop(); fkVoice.release(); } catch (Throwable ignored) {} fkVoice = null; }
        if (tianyi != null) { tianyi.stop(); tianyi = null; }
        if (gpsMgr != null) { gpsMgr.stop(); gpsMgr = null; }
        if (stepMgr != null) { stepMgr.stop(); stepMgr = null; }
        inst = null;
        if (engine != null) { engine.close(); engine = null; } super.onDestroy();
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
