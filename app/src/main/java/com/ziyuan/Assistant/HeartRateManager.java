package com.ziyuan.Assistant;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import java.util.Collections;
import java.util.UUID;

/** BLE 心率带/手表: 标准 Heart Rate Service (0x180D / 0x2A37) 实时心率 */
public class HeartRateManager {
    public static final UUID SVC = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID CHR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public interface Listener { void onHR(int bpm); }

    private final Context ctx;
    private final Listener listener;
    private BluetoothGatt gatt;
    private boolean scanning = false;
    public int lastHr = 0;

    public HeartRateManager(Context c, Listener l) { ctx = c; listener = l; }

    public void start() {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled() || ad.getBluetoothLeScanner() == null) return;
        scanning = true;
        try {
            ad.getBluetoothLeScanner().startScan(
                Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SVC)).build()),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCb);
        } catch (Throwable ignored) { scanning = false; }
    }

    private final ScanCallback scanCb = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult r) {
            if (!scanning) return;
            stopScan();
            try { gatt = r.getDevice().connectGatt(ctx, false, gattCb); } catch (Throwable ignored) {}
        }
        @Override public void onScanFailed(int e) { scanning = false; }
    };

    private void stopScan() {
        scanning = false;
        try {
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm != null && bm.getAdapter() != null && bm.getAdapter().getBluetoothLeScanner() != null)
                bm.getAdapter().getBluetoothLeScanner().stopScan(scanCb);
        } catch (Throwable ignored) {}
    }

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int st, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices();
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                lastHr = 0;
                try { g.close(); } catch (Throwable ignored) {}
                if (gatt == g) gatt = null;
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int st) {
            BluetoothGattService s = g.getService(SVC);
            if (s == null) return;
            BluetoothGattCharacteristic c = s.getCharacteristic(CHR);
            if (c == null) return;
            try {
                g.setCharacteristicNotification(c, true);
                android.bluetooth.BluetoothGattDescriptor d = c.getDescriptor(CCCD);
                if (d != null) {
                    d.setValue(android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(d);
                }
            } catch (Throwable ignored) {}
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (!CHR.equals(c.getUuid())) return;
            byte[] v = c.getValue();
            if (v == null || v.length < 2) return;
            int hr = (v[0] & 0x01) == 0 ? (v[1] & 0xFF)
                : (int) ((v[1] & 0xFF) | ((v[2] & 0xFF) << 8));
            if (hr > 20 && hr < 250) {
                lastHr = hr;
                if (listener != null) listener.onHR(hr);
            }
        }
    };

    public void stop() {
        stopScan();
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Throwable ignored) {}
        gatt = null;
        lastHr = 0;
    }
}
