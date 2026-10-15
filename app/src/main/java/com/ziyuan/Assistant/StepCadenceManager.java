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
    private boolean useDetector = false;
    public float steps = 0;           // 本次运动步数
    public float cadence = 0;         // 当前步频 步/分钟
    private long lastStepTime = 0;

    private final SensorEventListener sel = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (e.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                // 每次事件=1步
                long t = System.currentTimeMillis();
                float delta = e.values.length > 0 && e.values[0] > 0 ? e.values[0] : 1;
                steps += delta;
                if (lastStepTime > 0) {
                    float dtMin = (t - lastStepTime) / 60000f;
                    if (dtMin > 0.005f) {
                        float inst = delta / dtMin;
                        inst = Math.max(60f, Math.min(240f, inst));
                        cadence = cadence <= 0 ? inst : cadence * 0.7f + inst * 0.3f;
                    }
                }
                lastStepTime = t;
                return;
            }
            if (e.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
            float total = e.values[0];
            if (base < 0) { base = total; return; }
            float now = total - base;
            if (now < steps) { base = total; now = 0; }
            float delta = now - steps;
            long t = System.currentTimeMillis();
            if (delta > 0 && lastStepTime > 0) {
                float dtMin = (t - lastStepTime) / 60000f;
                if (dtMin > 0.005f) {
                    float inst = delta / dtMin;
                    inst = Math.max(60f, Math.min(240f, inst));
                    cadence = cadence <= 0 ? inst : cadence * 0.7f + inst * 0.3f;
                }
            }
            if (delta > 0) lastStepTime = t;
            steps = now;
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    public boolean hasSensor = false;

    public void start(Context c) {
        if (active) return;
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return;
        Sensor counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        Sensor detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        useDetector = counter == null && detector != null;
        Sensor s = counter != null ? counter : detector;
        if (s == null) return;
        hasSensor = true;
        active = sm.registerListener(sel, s, SensorManager.SENSOR_DELAY_UI);
    }

    public void stop() {
        if (sm != null) { try { sm.unregisterListener(sel); } catch (Throwable ignored) {} }
        sm = null; active = false; base = -1; steps = 0; cadence = 0; lastStepTime = 0;
    }

    /** 死推: 用当前步频和估计步幅算这段步数对应的米数 */
    public String diag() {
        if (sm == null) return "无服务";
        if (!hasSensor) return "无传感器";
        if (!active) return "未注册";
        return "步数" + Math.round(steps) + (lastStepTime == 0 ? "·等步" : "·计时中");
    }

    public float deadReckonMeters(float strideM, long sinceMs) {
        if (cadence <= 0 || lastStepTime == 0) return 0;
        float min = sinceMs / 60000f;
        return cadence * min * strideM;
    }
}
