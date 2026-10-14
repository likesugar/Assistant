package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

/** 用药看门狗前台服务: app在后台/杀进程后仍每20秒补挂到点未确认的服药通知 */
public class MedWatchService extends Service {
    private static final String CH = "med_watch";
    private final Handler h = new Handler();
    private int idle = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        try {
            NotificationChannel nc = new NotificationChannel(CH, "用药提醒守护", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(nc);
        } catch (Throwable ignored) {}
        // 必须先 startForeground 再收摊, 否则 ForegroundServiceDidNotStartInTime 崩溃
        startForeground(20003, buildNote("用药提醒守护中"));
        if (!MedPlanActivity.inWatchWindow(this)) {
            stopForeground(true);
            stopSelf();
            return;
        }
        h.postDelayed(tick, 3000);
    }

    private final Runnable tick = new Runnable() { public void run() {
        boolean active = false;
        try {
            active = MedPlanActivity.inWatchWindow(MedWatchService.this)
                && MedPlanActivity.load(MedWatchService.this).length() > 0;
            if (active) {
                MedPlanActivity.checkAndNotifyDue(MedWatchService.this);
                MedPlanActivity.armTick(MedWatchService.this);
                getSharedPreferences("medplan", 0).edit().putString("watch_last",
                    android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()).apply();
            }
        } catch (Throwable ignored) {}
        if (!active) { // 窗口外(距最近提醒超过1小时或无计划)守护通知不显示, 服务收摊; 闹钟到点会自动重拉
            stopForeground(true);
            stopSelf();
            return;
        }
        h.postDelayed(this, 20000);
    }};

    private Notification buildNote(String s) {
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("小工具").setContentText(s)
         .setSmallIcon(getApplicationInfo().icon).setOngoing(true);
        return b.build();
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    public static void ensure(Context c) {
        try {
            if (!MedPlanActivity.inWatchWindow(c)) return; // 窗口外不起服务, 免得触发改前5秒规则
            Intent i = new Intent(c, MedWatchService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {}
    }
}
