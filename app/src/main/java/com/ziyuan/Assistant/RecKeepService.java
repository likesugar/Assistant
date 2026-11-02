package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

/** 录制保活前台服务：有录制任务时通知栏/状态栏实时显示每路 时长/大小/状态；空闲自停 */
public class RecKeepService extends Service {
    private static final String CH = "rec_keep";
    private static final String CH_P = "rec_progress";
    private final Handler h = new Handler();

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            android.app.NotificationChannel nc = new android.app.NotificationChannel(CH, "录制保活", android.app.NotificationManager.IMPORTANCE_MIN);
            nc.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(nc);
            // 进度渠道: DEFAULT 才会在状态栏出图标 (与下载进度渠道同经验)
            android.app.NotificationChannel pc = new android.app.NotificationChannel(CH_P, "录制进度", android.app.NotificationManager.IMPORTANCE_DEFAULT);
            pc.setShowBadge(false);
            pc.setSound(null, null);
            pc.enableVibration(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(pc);
        } catch (Throwable ignored) {}
        startForeground(21001, buildNote("录制进行中…"));
        h.postDelayed(tick, 1000);
    }

    private final Runnable tick = new Runnable() { public void run() {
        boolean busy = false;
        try {
            StringBuilder sb = new StringBuilder();
            for (RecManager.RecJob j : RecManager.recJobs.values()) {
                if (j == null) continue;
                busy = true;
                String st = j.state != null ? j.state : "录制中";
                sb.append("• ").append(j.name != null ? j.name : "录制任务")
                  .append("  ").append(fmtDur(curSecs(j)))
                  .append("  ").append(sizeStr(j.bytes))
                  .append("  ").append(st).append('\n');
            }
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (busy) {
                Notification.Builder nb = noteBuilder("录制进行中");
                try {
                    nb.setStyle(new android.app.Notification.BigTextStyle().bigText(sb.toString().trim()));
                } catch (Throwable ignored) {}
                nm.notify(21001, nb.build());
            }
        } catch (Throwable ignored) {}
        if (busy) {
            h.postDelayed(tick, 1000);
        } else {
            stopSelf();   // 无录制任务: 自动停服务、撤通知
        }
    } };

    private static long curSecs(RecManager.RecJob j) {
        long s = j.secs;
        if (j.startTs > 0) s += (System.currentTimeMillis() - j.startTs) / 1000;
        return s;
    }

    private static String fmtDur(long s) {
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    private static String sizeStr(long b) {
        if (b >= 1L << 30) return String.format("%.2fGB", b / 1073741824.0);
        if (b >= 1L << 20) return String.format("%.2fM", b / 1048576.0);
        if (b >= 1024) return String.format("%.0fK", b / 1024.0);
        return b + "B";
    }

    private Notification buildNote(String s) {
        return noteBuilder(s).build();
    }

    private Notification.Builder noteBuilder(String s) {
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_P)
                : new Notification.Builder(this);
        b.setContentTitle(s)
         .setSmallIcon(android.R.drawable.stat_sys_download)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setContentIntent(android.app.PendingIntent.getActivity(this, 0,
             new Intent(this, HomeActivity.class),
             android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
        return b;
    }

    @Override
    public void onDestroy() {
        try { h.removeCallbacks(tick); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
