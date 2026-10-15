package com.ziyuan.Assistant;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** BLE 心率: 标准服务 0x180D/0x2A37; 支持全量扫描(设备选择页) + 直连已保存地址 + 过滤自动扫描 */
public class HeartRateManager {
    public static final UUID SVC = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID CHR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public interface Listener { void onHR(int bpm); }
    public interface ScanListener { void onDevices(List<BluetoothDevice> devices); }

    private static HeartRateManager inst;
    public static synchronized HeartRateManager get(Context c) {
        if (inst == null) inst = new HeartRateManager(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private Listener listener;
    private BluetoothGatt gatt;
    private boolean scanning = false;
    public int lastHr = 0;
    private boolean hrSeen = false;
    public String connectedAddr = null;

    private final Map<String, BluetoothDevice> found = new LinkedHashMap<>();
    private ScanListener scanListener;

    private HeartRateManager(Context c) { ctx = c; }

    public void setListener(Listener l) { listener = l; }
    public int lastHr() { return lastHr; }
    public boolean isConnected() { return gatt != null && hrSeen; }

    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    /** 全量扫描(设备选择页用, 不加过滤) */
    public void scanAll(ScanListener l) {
        BluetoothAdapter ad = adapter();
        if (ad == null || !ad.isEnabled() || ad.getBluetoothLeScanner() == null) { if (l != null) l.onDevices(new ArrayList<>()); return; }
        stopScan();
        found.clear();
        scanListener = l;
        scanning = true;
        try {
            ad.getBluetoothLeScanner().startScan(
                Collections.<ScanFilter>emptyList(),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb);
        } catch (Throwable ignored) { scanning = false; }
    }

    /** 过滤心率服务扫描(无保存地址时自动连) */
    public void startAutoScan() {
        BluetoothAdapter ad = adapter();
        if (ad == null || !ad.isEnabled() || ad.getBluetoothLeScanner() == null) return;
        stopScan();
        scanning = true;
        try {
            ad.getBluetoothLeScanner().startScan(
                Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SVC)).build()),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb);
        } catch (Throwable ignored) { scanning = false; }
    }

    private final ScanCallback scanCb = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult r) {
            BluetoothDevice d = r.getDevice();
            if (d == null || d.getAddress() == null) return;
            String key = d.getAddress();
            boolean isNew = !found.containsKey(key);
            found.put(key, d);
            if (scanListener != null && (isNew || "scanAll".equals("scanAll"))) {
                if (isNew) scanListener.onDevices(new ArrayList<>(found.values()));
            }
            if (!scanAllMode && d.getUuids() != null) { /* 不依赖广播UUID */ }
        }
        @Override public void onScanFailed(int e) { scanning = false; }
    };
    private boolean scanAllMode = false;

    public void stopScan() {
        scanning = false;
        try {
            BluetoothAdapter ad = adapter();
            if (ad != null && ad.getBluetoothLeScanner() != null) ad.getBluetoothLeScanner().stopScan(scanCb);
        } catch (Throwable ignored) {}
        scanListener = null;
    }

    /** 直连指定地址 */
    public boolean connect(String addr) {
        BluetoothAdapter ad = adapter();
        if (ad == null) return false;
        BluetoothDevice d = ad.getRemoteDevice(addr);
        if (d == null) return false;
        stopScan();
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Throwable ignored) {}
        try { gatt = d.connectGatt(ctx, false, gattCb); return gatt != null; } catch (Throwable t) { return false; }
    }

    private final BluetoothGattCallback gattCb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int st, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedAddr = g.getDevice().getAddress();
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                lastHr = 0; hrSeen = false;
                if (connectedAddr != null && connectedAddr.equals(g.getDevice().getAddress())) connectedAddr = null;
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
                hrSeen = true;
                if (listener != null) listener.onHR(hr);
            }
        }
    };

    public void stop() {
        stopScan();
        try { if (gatt != null) { gatt.disconnect(); gatt.close(); } } catch (Throwable ignored) {}
        gatt = null; connectedAddr = null; lastHr = 0; hrSeen = false;
    }
}
