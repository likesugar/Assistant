package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 后台录制/下载保活(1.0.61):
 * 1. CPU 锁(PARTIAL_WAKE_LOCK) + Wi-Fi 锁(WIFI_MODE_FULL_HIGH_PERF) — 息屏后 CPU 与 Wi-Fi 都不休眠,
 *    这是"息屏断流"的根因: 只锁 CPU 时 Wi-Fi 仍会进入省电, TCP 长连接被丢。
 * 2. 引用计数 + 定时续期 — 录制/下载服务每拍 renew(), 任务结束 release(), 不误释放仍在使用任务的锁。
 * 3. 电池优化白名单: 系统 Doze 会掐后台网络; 通知上提供一键设置入口(需用户确认, 系统不允许静默加白)。
 */
public final class BgKeep {
    private static final String CPU_TAG = "Assistant:bgcpu";
    private static final String WIFI_TAG = "Assistant:bgwifi";
    private static final long HOLD_MS = 12L * 60 * 60 * 1000; // 最长 12 小时, 每拍续期
    private static int refs = 0;
    private static PowerManager.WakeLock cpu;
    private static WifiManager.WifiLock wifi;

    private BgKeep() {}

    /** 持有锁(可重复调用, 引用计数); renew() 会把超时重新推到 12 小时后 */
    public static synchronized void acquire(Context c) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        refs++;
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                if (cpu == null) {
                    cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, CPU_TAG);
                    cpu.setReferenceCounted(false);
                }
                if (!cpu.isHeld()) cpu.acquire(HOLD_MS);
            }
        } catch (Throwable ignored) {}
        try {
            WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                if (wifi == null) {
                    wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_TAG);
                    wifi.setReferenceCounted(false);
                }
                if (!wifi.isHeld()) wifi.acquire();
            }
        } catch (Throwable ignored) {}
    }

    /** 任务进行中每拍调用: 续期, 防止 12 小时超时后锁自动释放 */
    public static void renew(Context c) { acquireRenew(c); }

    private static synchronized void acquireRenew(Context c) {
        if (c == null) return;
        if (refs > 0) acquireLocked(c.getApplicationContext());
    }

    private static void acquireLocked(Context app) {
        // 非引用计数锁: 先释放再取, CPU 锁超时重新推到 12 小时后(锁中间只断极短一瞬); Wi-Fi 锁无超时
        try { if (cpu != null && cpu.isHeld()) cpu.release(); } catch (Throwable ignored) {}
        try { if (cpu != null) cpu.acquire(HOLD_MS); } catch (Throwable ignored) {}
        try { if (wifi != null && wifi.isHeld()) wifi.release(); } catch (Throwable ignored) {}
        try { if (wifi != null) wifi.acquire(); } catch (Throwable ignored) {}
    }

    /** 任务结束释放一层引用; 引用归零才真正释放 */
    public static synchronized void release() {
        if (refs <= 0) return;
        refs--;
        if (refs > 0) return;
        try { if (cpu != null && cpu.isHeld()) cpu.release(); } catch (Throwable ignored) {}
        try { if (wifi != null && wifi.isHeld()) wifi.release(); } catch (Throwable ignored) {}
        cpu = null; wifi = null;
    }

    /** 屏幕是否熄灭: 息屏时看门狗放宽无流量阈值, 避免把正常省电间隙误判为断流 */
    public static boolean screenOff(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && !pm.isInteractive();
        } catch (Throwable ignored) { return false; }
    }

    /** 是否已加入电池优化白名单 */
    public static boolean ignoredOptimizations(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 23) return true;
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Throwable ignored) { return true; }
    }

    /** 通知上的「息屏不断流」按钮跳转到系统白名单设置(用户确认, 不静默申请) */
    public static PendingIntent batteryOptIntent(Context c) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 23) {
                i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(android.net.Uri.parse("package:" + c.getPackageName()));
            } else {
                i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) fl |= PendingIntent.FLAG_IMMUTABLE;
            return PendingIntent.getActivity(c, 8801, i, fl);
        } catch (Throwable ignored) { return null; }
    }

    /** 给持续通知追加「息屏不断流」动作(未加白名单时才显示) */
    public static Notification.Builder withBatteryAction(Context c, Notification.Builder b) {
        if (!ignoredOptimizations(c)) {
            PendingIntent pi = batteryOptIntent(c);
            if (pi != null) {
                try { b.addAction(android.R.drawable.ic_lock_idle_charging, "息屏不断流", pi); } catch (Throwable ignored) {}
            }
        }
        return b;
    }
}
