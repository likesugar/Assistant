package com.ziyuan.Assistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/** 局域网聊天中枢: UDP广播(端口24688)互联, 无需任何服务器; 日志留最近500条 */
public class ChatHub {

    public static final int UDP_PORT = 24688;
    public interface Listener { void onMessage(JSONObject o); }

    private static volatile boolean running = false;
    private static MulticastSocket rx;
    private static String selfId = "";
    public static String selfId() { return selfId; }
    private static final java.util.List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<JSONObject> log = java.util.Collections.synchronizedList(new java.util.ArrayList<JSONObject>());
    private static Context ctx;

    public static void init(Context c) {
        ctx = c;
        // 每台设备唯一ID: 收到自己的广播直接丢弃, 防重复
        android.content.SharedPreferences sp = c.getSharedPreferences("chat", Context.MODE_PRIVATE);
        selfId = sp.getString("uuid", null);
        if (selfId == null) {
            selfId = java.util.UUID.randomUUID().toString();
            sp.edit().putString("uuid", selfId).apply();
        }
        loadLog();
    }

    public static synchronized void start() {
        if (running) return;
        try {
            rx = new MulticastSocket(UDP_PORT);
            rx.setBroadcast(true);
            running = true;
            Thread t = new Thread(new Runnable() { public void run() {
                byte[] buf = new byte[4096];
                while (running) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        rx.receive(p);
                        String s = new String(p.getData(), 0, p.getLength(), "UTF-8");
                        JSONObject o = new JSONObject(s);
                        if (selfId.equals(o.optString("uid"))) continue; // 自己广播的, 丢弃
                        if (!seenOnce(o)) continue; // 双广播/重发去重
                        append(o);
                        notifyUi(o);
                        autoFetchFile(o); // 文件消息自动接收落地, 供本机/电脑下载
                    } catch (Throwable e) { if (!running) break; }
                }
            }});
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {}
    }

    public static synchronized void stop() {
        running = false;
        try { if (rx != null) rx.close(); } catch (Throwable ignored) {}
    }

    public static void send(String nick, String msg) { sendObj(nick, msg, 0, null, 0); }

    /** 带文件附件信息的广播 */
    public static void sendObj(String nick, String msg, long fts, String fname, long fsize) {
        try {
            JSONObject o = new JSONObject();
            o.put("nick", nick == null ? "匿名" : nick);
            o.put("msg", msg);
            o.put("ts", System.currentTimeMillis());
            o.put("uid", selfId);
            if (fname != null) {
                o.put("fname", fname);
                o.put("fts", fts);
                o.put("fsize", fsize);
                o.put("fip", LanShareServer.localIp());
                o.put("fport", LanShareServer.getPort());
            }
            byte[] b = o.toString().getBytes("UTF-8");
            DatagramSocket out = new DatagramSocket();
            out.setBroadcast(true);
            // 全网广播 + 子网定向广播双保险
            try { out.send(new DatagramPacket(b, b.length, InetAddress.getByName("255.255.255.255"), UDP_PORT)); } catch (Throwable ignored) {}
            String ip = LanShareServer.localIp();
            if (ip != null && ip.contains(".")) {
                try {
                    String sub = ip.substring(0, ip.lastIndexOf('.') + 1) + "255";
                    out.send(new DatagramPacket(b, b.length, InetAddress.getByName(sub), UDP_PORT));
                } catch (Throwable ignored) {}
            }
            out.close();
            append(o);
            notifyUi(o);
        } catch (Throwable ignored) {}
    }

    public static void addListener(Listener l) { if (l != null) listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }
    private static void notifyUi(final JSONObject o) {
        for (Listener l : listeners) try { l.onMessage(o); } catch (Throwable ignored) {}
    }

    private static final java.util.Set<String> seen = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>() {
        protected boolean removeEldest(java.util.Map.Entry<String, Boolean> e) { return size() > 400; }
    });

    /** 同 nick+ts+msg 只收一次 */
    private static boolean seenOnce(JSONObject o) {
        String key = o.optString("nick") + "|" + o.optLong("ts", 0) + "|" + o.optString("msg");
        synchronized (seen) { if (seen.contains(key)) return false; seen.add(key); }
        return true;
    }

    private static void append(JSONObject o) {
        synchronized (log) {
            String key = o.optString("nick") + "|" + o.optLong("ts", 0) + "|" + o.optString("msg");
            for (JSONObject p : log) {
                String k2 = p.optString("nick") + "|" + p.optLong("ts", 0) + "|" + p.optString("msg");
                if (k2.equals(key)) return; // 日志里已有, 不重复
            }
            log.add(o);
            while (log.size() > 500) log.remove(0);
        }
        saveLog();
    }

    /** 文件服务: ts → 本地文件 (接收方经HTTP拉取) */
    private static final java.util.Map<Long, java.io.File> serveFiles = java.util.Collections.synchronizedMap(new java.util.HashMap<Long, java.io.File>());
    public static void serveFile(long ts, java.io.File f) { serveFiles.put(ts, f); }
    public static java.io.File getServeFile(long ts) { return serveFiles.get(ts); }

    /** 按ts查消息(匹配消息ts或文件fts) */
    public static JSONObject findByTs(long ts) {
        synchronized (log) {
            for (JSONObject o : log) if (o.optLong("ts", 0) == ts) return o;
            for (JSONObject o : log) if (o.optLong("fts", 0) == ts) return o;
        }
        return null;
    }

    /** 确保某ts的文件本地可用: 没有就从消息里的源地址拉 */
    public static java.io.File ensureFile(long ts) {
        java.io.File f = serveFiles.get(ts);
        if (f != null && f.exists()) return f;
        JSONObject o = findByTs(ts);
        if (o == null || o.optString("fname", "").isEmpty()) return null;
        final String fip = o.optString("fip", "");
        final int fport = o.optInt("fport", 0);
        final String fname = o.optString("fname", "file");
        if (fip.isEmpty() || fport <= 0) return null;
        try {
            java.io.File dir = new java.io.File(ctx.getExternalFilesDir(null), "聊天文件");
            if (!dir.exists()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, ts + "_" + fname);
            if (!out.exists()) {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("http://" + fip + ":" + fport + "/chatfile?ts=" + ts).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(60000);
                java.io.InputStream is = c.getInputStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                byte[] b = new byte[8192]; int n;
                while ((n = is.read(b)) > 0) fos.write(b, 0, n);
                is.close(); fos.close();
            }
            serveFiles.put(ts, out);
            return out;
        } catch (Throwable e) { return null; }
    }

    /** 收到文件消息自动下载落地 → 注册到文件服务(本机/电脑可下载) */
    private static void autoFetchFile(final JSONObject o) {
        final String fname = o.optString("fname", "");
        if (fname.isEmpty()) return;
        final long fts = o.optLong("fts", 0);
        if (serveFiles.containsKey(fts)) return;
        final String fip = o.optString("fip", "");
        final int fport = o.optInt("fport", 0);
        if (fip.isEmpty() || fport <= 0) return;
        new Thread(new Runnable() { public void run() {
            try {
                java.io.File dir = new java.io.File(ctx.getExternalFilesDir(null), "聊天文件");
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir, fts + "_" + fname);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("http://" + fip + ":" + fport + "/chatfile?ts=" + fts).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(60000);
                java.io.InputStream is = c.getInputStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                byte[] b = new byte[8192]; int n;
                while ((n = is.read(b)) > 0) fos.write(b, 0, n);
                is.close(); fos.close();
                serveFile(fts, out);
            } catch (Throwable ignored) {}
        }}).start();
    }

    /** 自上次之后的全部消息(PC轮询) */
    public static JSONArray since(long ts) {
        JSONArray a = new JSONArray();
        synchronized (log) {
            for (JSONObject o : log) if (o.optLong("ts", 0) > ts) a.put(o);
        }
        return a;
    }

    public static JSONArray all() { return since(-1); }

    private static void loadLog() {
        try {
            java.io.File f = new java.io.File(ctx.getFilesDir(), "chat_log.json");
            if (!f.exists()) return;
            byte[] b = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            fis.read(b); fis.close();
            JSONArray a = new JSONArray(new String(b, "UTF-8"));
            synchronized (log) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String key = o.optString("nick") + "|" + o.optLong("ts", 0) + "|" + o.optString("msg");
                    boolean dup = false;
                    for (JSONObject p : log) {
                        String k2 = p.optString("nick") + "|" + p.optLong("ts", 0) + "|" + p.optString("msg");
                        if (k2.equals(key)) { dup = true; break; }
                    }
                    if (!dup) log.add(o);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void saveLog() {
        try {
            JSONArray a = new JSONArray();
            synchronized (log) { for (JSONObject o : log) a.put(o); }
            java.io.File f = new java.io.File(ctx.getFilesDir(), "chat_log.json");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(a.toString().getBytes("UTF-8")); fos.close();
        } catch (Throwable ignored) {}
    }
}
