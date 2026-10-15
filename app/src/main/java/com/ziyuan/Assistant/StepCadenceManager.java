package com.ziyuan.Assistant;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** 计步传感器: 实时步频(步/分钟, EMA平滑) + 死推用累计步数 */
public class StepCadenceManager {
    private SensorManager sm;
    private boolean active = false;
    private float base = -1;          // 开机累计步基线
    public float steps = 0;           // 本次运动步数
    public float cadence = 0;         // 当前步频 步/分钟
    private long lastStepTime = 0;

    private final SensorEventListener sel = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (e.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
            float total = e.values[0];
            if (base < 0) { base = total; return; }
            float now = total - base;
            if (now < steps) { base = total; now = 0; } // 重启归零保护
            float delta = now - steps;
            long t = System.currentTimeMillis();
            if (delta > 0 && lastStepTime > 0) {
                float dtMin = (t - lastStepTime) / 60000f;
                if (dtMin > 0.005f) {
                    float inst = delta / dtMin; // 步/分钟
                    inst = Math.max(60f, Math.min(240f, inst));
                    cadence = cadence <= 0 ? inst : cadence * 0.7f + inst * 0.3f;
                }
            }
            if (delta > 0) lastStepTime = t;
            steps = now;
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    public void start(Context c) {
        if (active) return;
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s == null) return;
        active = sm.registerListener(sel, s, SensorManager.SENSOR_DELAY_UI);
    }

    public void stop() {
        if (sm != null) { try { sm.unregisterListener(sel); } catch (Throwable ignored) {} }
        sm = null; active = false; base = -1; steps = 0; cadence = 0; lastStepTime = 0;
    }

    /** 死推: 用当前步频和估计步幅算这段步数对应的米数 */
    public float deadReckonMeters(float strideM, long sinceMs) {
        if (cadence <= 0 || lastStepTime == 0) return 0;
        float min = sinceMs / 60000f;
        return cadence * min * strideM;
    }
}
