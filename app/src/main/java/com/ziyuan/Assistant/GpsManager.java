package com.ziyuan.Assistant;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/** GPS 实时配速 + 距离累计 (秒级定位) */
public class GpsManager {
    public interface Listener { void onUpdate(double speedMps, double distKm, double paceSecPerKm); }

    private final Context ctx;
    private final Listener listener;
    private LocationManager lm;
    private Location last;
    public double distKm = 0;
    public double lastPace = 0;
    public volatile boolean gotFix = false;
    private boolean lastWasGps = false;
    public long lastGpsTime = 0;
    public double drKm = 0; // 死推累计
    public String lastDiag = "";

    public GpsManager(Context c, Listener l) { ctx = c; listener = l; }

    private final LocationListener ll = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            boolean isGps = LocationManager.GPS_PROVIDER.equals(loc.getProvider());
            String prov = loc.getProvider();
            float acc = loc.getAccuracy();
            float spd = loc.getSpeed();
            if (isGps) {
                gotFix = true;
                lastGpsTime = System.currentTimeMillis();
                if (last != null && acc < 20 && last.getAccuracy() < 20) {
                    float dt = Math.max(0.5f, Math.min(5f, (loc.getTime() - last.getTime()) / 1000f));
                    float d = last.distanceTo(loc);
                    if (spd > 1.2f && spd < 70f) d = Math.min(d, spd * dt);
                    if (d < 30f) distKm += d / 1000.0;
                }
                last = loc;
                if (spd > 0.7) {
                    double pace = 1000.0 / spd;
                    lastPace = lastPace <= 0 ? pace : lastPace * 0.6 + pace * 0.4;
                }
            } else if (last == null) {
                last = loc; // 只补初始位置
            }
            lastDiag = "prov=" + prov + " acc=" + (Math.round(acc)) + "m spd=" + String.format("%.1f", spd);
            if (listener != null) listener.onUpdate(lastPace > 0 ? (float) (1000.0 / lastPace) : 0, distKm, lastPace);
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    public void start() {
        try {
            lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try { if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 1000, 0, ll, ctx.getMainLooper()); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public void stop() {
        try { if (lm != null) lm.removeUpdates(ll); } catch (Throwable ignored) {}
        lm = null; last = null;
    }
}
