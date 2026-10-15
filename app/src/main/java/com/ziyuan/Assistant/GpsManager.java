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

    public GpsManager(Context c, Listener l) { ctx = c; listener = l; }

    private final LocationListener ll = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            if (last != null && loc.getAccuracy() < 30 && last.getAccuracy() < 30) {
                float sp = loc.getSpeed();
                if (sp > 1.0f) {
                    float dt = Math.max(0.5f, Math.min(5f, (loc.getTime() - last.getTime()) / 1000f));
                    float d = Math.min(last.distanceTo(loc), sp * dt);
                    distKm += d / 1000.0;
                }
            }
            gotFix = true;
            last = loc;
            float sp = loc.getSpeed();
            double pace = sp > 0.7 ? 1000.0 / sp : 0; // 秒/公里
            lastPace = pace;
            if (listener != null) listener.onUpdate(sp, distKm, pace);
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
