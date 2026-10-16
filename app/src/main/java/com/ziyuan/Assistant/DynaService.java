package com.ziyuan.Assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/** 平台动态/开播监控：参照 aio-dynamic-push 移植。轮询公开 API，状态落盘去重，有变化发通知。 */
public class DynaService extends Service {
    public static final String SP = "dyna";
    public static final String CH = "dynafg2";
    public static final String CHL = "dynafg_low";
    public static final String CHE = "dynaevt2";
    private volatile boolean running = false;
    private Thread worker;

    public static boolean isOn(android.content.Context c) {
        return c.getSharedPreferences(SP, 0).getBoolean("on", false);
    }

    public static void ensure(android.content.Context c) {
        if (isOn(c)) {
            Intent i = new Intent(c, DynaService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    public static volatile boolean svcOn = false;

    @Override public int onStartCommand(Intent i, int f, int id) {
        svcOn = true;
        startFg();
        synchronized (this) {
            if (!running) {
                running = true;
                worker = new Thread(new Runnable() { public void run() { loop(); } }, "dyna-poll");
                worker.start();
            }
        }
        return START_STICKY;
    }

    private void startFg() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.deleteNotificationChannel("dyna"); // 清理旧渠道
            nm.createNotificationChannel(new NotificationChannel(CHL, "动态监控常驻", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(CH, "动态监控常驻", NotificationManager.IMPORTANCE_DEFAULT));
        }
        // 起前台先用低重要性(状态栏不出图标)，updateFg 每轮按开播状态切换
        Notification.Builder b = fgBuilder(CHL, "检测关注平台的动态与开播");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(0xD91, b.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(0xD91, b.build());
        }
        updateFg(getSharedPreferences(SP, 0));
    }

    @Override public void onDestroy() {
        running = false;
        svcOn = false;
        try { stopForeground(true); } catch (Throwable ignored) {}
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0xD91); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void loop() {
        SharedPreferences sp = getSharedPreferences(SP, 0);
        while (running && sp.getBoolean("on", false)) {
            try { pollAll(); } catch (Throwable t) { }
            updateFg(sp);
            // 分片睡眠，便于及时响应停止
            int remain = sp.getInt("interval", 3) * 60;
            for (int s = 0; s < remain && running && sp.getBoolean("on", false); s++) {
                try { Thread.sleep(1000); } catch (Exception e) { return; }
            }
        }
        stopSelf();
    }

    // ---------- 数据 ----------
    /** targets: [{"p":"bili","id":"123"}, ...] */
    static JSONArray targets(SharedPreferences sp) {
        try { return new JSONArray(sp.getString("targets", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private JSONObject state;
    private void saveState(SharedPreferences sp) { sp.edit().putString("state", state.toString()).apply(); }

    private void pollAll() throws Exception {
        SharedPreferences sp = getSharedPreferences(SP, 0);
        String raw = sp.getString("state", "{}");
        state = new JSONObject(raw);
        JSONArray ts = targets(sp);
        for (int i = 0; i < ts.length(); i++) {
            if (!running) return;
            JSONObject t = ts.getJSONObject(i);
            String p = t.optString("p", ""), id = t.optString("id", ""), kind = t.optString("k", "");
            try {
                if ("bili".equals(p)) checkBili(sp, id, kind);
                else if ("douyin".equals(p)) checkDouyin(sp, id);
                else if ("douyu".equals(p)) checkDouyu(sp, id);
                else if ("huya".equals(p)) checkHuya(sp, id);
            } catch (Throwable e) { }
            Thread.sleep(1200);
        }
        saveState(sp);
    }

    private String key(String p, String id) { return p + "|" + id; }

    /** 记录目标昵称与开播状态，供列表显示；jump=可选深链(开播房间) */
    private void saveInfo(SharedPreferences sp, String p, String id, String name, String st) {
        saveInfo(sp, p, id, name, st, null);
    }

    private void saveInfo(SharedPreferences sp, String p, String id, String name, String st, String jump) {
        try {
            JSONObject info = new JSONObject(sp.getString("info", "{}"));
            JSONObject o = new JSONObject();
            o.put("n", name); o.put("s", st);
            if (jump != null) o.put("j", jump);
            info.put(key(p, id), o);
            sp.edit().putString("info", info.toString()).apply();
        } catch (Throwable ignored) {}
    }

    /** 列表读取：返回 {昵称, 开播状态(1/0/空)}，无记录返回 null */
    public static String[] infoOf(SharedPreferences sp, String p, String id) {
        try {
            JSONObject o = new JSONObject(sp.getString("info", "{}")).optJSONObject(p + "|" + id);
            if (o == null) return null;
            return new String[]{o.optString("n", id), o.optString("s", "")};
        } catch (Throwable e) { return null; }
    }

    /** 收藏目标（开播常驻栏显示用） */
    public static JSONArray favs(SharedPreferences sp) {
        try { return new JSONArray(sp.getString("fav", "[]")); } catch (Throwable e) { return new JSONArray(); }
    }

    /** 刷新前台常驻通知：显示收藏列表中正在开播的房间 */
    private void updateFg(SharedPreferences sp) {
        try {
            JSONArray fs = favs(sp);
            StringBuilder sb = new StringBuilder();
            String jump = null;
            for (int i = 0; i < fs.length(); i++) {
                JSONObject f = fs.optJSONObject(i);
                if (f == null) continue;
                String[] info = infoOf(sp, f.optString("p", ""), f.optString("id", ""));
                if (info != null && "1".equals(info[1])) {
                    if (sb.length() > 0) sb.append("　");
                    sb.append("🔴 ").append(info[0]);
                    if (jump == null) {
                        String pj = storedJump(sp, f.optString("p", ""), f.optString("id", ""));
                        if (pj == null) pj = roomUrl(f.optString("p", ""), f.optString("id", ""));
                        jump = pj;
                    }
                }
            }
            if (sb.length() == 0) {
                int n = DynaService.targets(getSharedPreferences("dyna", MODE_PRIVATE)).length();
                sb.append("监控 ").append(n).append(" 个目标 · 无收藏开播");
            }
            String text = sb.toString();
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            // 有开播才用 DEFAULT 渠道(状态栏出图标)；无开播走 LOW 渠道(状态栏不出图标，只在下拉栏里)
            Notification.Builder b = fgBuilder(sb.length() > 0 ? CH : CHL, text);
            // 有开播→点击直达对应平台App首个开播房间；无开播→进本应用页
            b.setContentIntent(jump != null ? fgPi(jump) : fgPi());
            nm.notify(0xD91, b.build());
        } catch (Throwable ignored) { }
    }

    /** 房间链接: 平台+roomId → 可深链的 URL */
    private String roomUrl(String p, String id) {
        if (id == null || id.isEmpty()) return null;
        if ("bili".equals(p)) return "https://live.bilibili.com/" + id; // 兜底, 正常走 info.j 的 room_id 深链
        if ("douyu".equals(p)) return "https://www.douyu.com/" + id;
        if ("huya".equals(p)) return "https://www.huya.com/" + id;
        return null;
    }

    private String storedJump(SharedPreferences sp, String p, String id) {
        try {
            JSONObject o = new JSONObject(sp.getString("info", "{}")).optJSONObject(p + "|" + id);
            String j = o == null ? null : o.optString("j", null);
            return (j == null || j.isEmpty()) ? null : j;
        } catch (Throwable e) { return null; }
    }

    private Notification.Builder fgBuilder(String channel, String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        b.setSmallIcon(getApplicationInfo().icon).setContentTitle("动态监控运行中")
                .setContentText(text).setOngoing(true).setContentIntent(fgPi());
        return b;
    }

    /** 通知点击 → 跳转对应平台App（有装App用App深链，没装走浏览器） */
    private PendingIntent fgPi(String jump) {
        try {
            if (jump == null || jump.isEmpty()) jump = "https://live.bilibili.com/";
            Intent in = new Intent(Intent.ACTION_VIEW, Uri.parse(jump));
            String host = Uri.parse(jump).getHost() == null ? "" : Uri.parse(jump).getHost();
            String pkg = host.contains("bilibili") ? "tv.danmaku.bili"
                    : host.contains("douyin") ? "com.ss.android.ugc.aweme"
                    : host.contains("douyu") ? "com.duowan.kiwi"
                    : host.contains("huya") ? "com.duowan.mobile" : null;
            if (pkg != null) {
                try {
                    if (getPackageManager().getLaunchIntentForPackage(pkg) != null) {
                        // 优先原生scheme(B站https深链在部分ROM不触发App内跳转)
                        Intent nat = null;
                        if (host.contains("bilibili") && jump.matches("https://live\\.bilibili\\.com/\\d+.*")) {
                            String rid = jump.substring("https://live.bilibili.com/".length()).replaceAll("[^0-9].*", "");
                            if (!rid.isEmpty()) nat = new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://live/" + rid));
                        }
                        if (nat == null) nat = new Intent(Intent.ACTION_VIEW, Uri.parse(jump));
                        nat.setPackage(pkg);
                        if (!getPackageManager().queryIntentActivities(nat, 0).isEmpty()) in = nat;
                        else in.setPackage(pkg); // App装了但scheme不识别→https限包名, 系统会走App的AppLinks
                    }
                } catch (Throwable ignored) {}
            }
            return PendingIntent.getActivity(this, jump.hashCode(), in,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        } catch (Throwable e) { return fgPi(); }
    }

    /** 通知点击 → 进应用开播通知页 */
    private PendingIntent fgPi() {
        try {
            Intent in = new Intent(this, DynaActivity.class);
            in.putExtra("mode", "live");
            return PendingIntent.getActivity(this, 0xD92, in,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        } catch (Throwable e) { return null; }
    }

    /** 首次记录返回 false(不推送)；有变化返回 true 并已更新 */
    private boolean changed(SharedPreferences sp, String k, String cur) {
        String old = state.optString(k, null);
        try { state.put(k, cur); } catch (Throwable ignored) {}
        return old != null && !old.equals(cur);
    }

    /** 开播类通知 → "开播提醒"渠道 */
    private void notify(String title, String text, String jump) { notifyCh(title, text, jump, CHE, "开播提醒"); }
    private void notifyCh(String title, String text, String jump, String chId, String chName) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(chId, chName, NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, chId) : new Notification.Builder(this);
        b.setSmallIcon(getApplicationInfo().icon).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true)
                .setContentIntent(fgPi(jump));
        nm.notify(("dyna" + title).hashCode(), b.build());
    }

    // ---------- HTTP ----------
    private static String http(String url, String method, String body, String cookie, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        if (referer != null) { c.setRequestProperty("Referer", referer); }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while (in != null && (n = in.read(buf)) > 0) bo.write(buf, 0, n);
        try { in.close(); } catch (Exception ignored) {}
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---------- B站：动态 + 开播 ----------
    private String buvid3(SharedPreferences sp) {
        String b3 = sp.getString("buvid3", null);
        if (b3 != null) return b3;
        try {
            JSONObject r = new JSONObject(http("https://api.bilibili.com/x/frontend/finger/spi", "GET", null, null, null));
            b3 = r.getJSONObject("data").getString("b_3");
            sp.edit().putString("buvid3", b3).apply();
        } catch (Throwable e) { }
        return b3;
    }

    private void checkBili(SharedPreferences sp, String uid, String kind) throws Exception {
        String b3 = buvid3(sp);
        String cookie = b3 != null ? "buvid3=" + b3 + ";" : null;
        // 开播(批量接口单uid也能用)。"d"=纯动态目标跳过开播检测
        if (!"d".equals(kind)) {
        try {
            String body = "{\"uids\":[" + uid + "]}";
            String resp = http("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids", "POST", body, cookie, "https://space.bilibili.com");
            JSONObject r = new JSONObject(resp);
            if (r.optInt("code", -1) == 0) {
                JSONObject d = r.getJSONObject("data");
                Iterator<String> it = d.keys();
                while (it.hasNext()) {
                    JSONObject u = d.getJSONObject(it.next());
                    String uname = u.optString("uname", uid);
                    String st = String.valueOf(u.optInt("live_status", 0));
                    saveInfo(sp, "bili", uid, uname, st,
                            "1".equals(st) && !u.optString("room_id", "").isEmpty()
                                    ? "https://live.bilibili.com/" + u.optString("room_id") : null);
                    if (changed(sp, key("bililive", uid), st) && "1".equals(st)) {
                        String rid = u.optString("room_id", "");
                        notify("【B站】【" + uname + "】开播了", u.optString("title", ""),
                                rid.isEmpty() ? null : "https://live.bilibili.com/" + rid);
                    }
                }
            }
        } catch (Throwable e) { }
        }
    }

    // ---------- 抖音：开播 ----------
    private void checkDouyin(SharedPreferences sp, String webRid) throws Exception {
        String ttwid = sp.getString("ttwid", null);
        if (ttwid == null) {
            try {
                String body = "{\"region\":\"cn\",\"aid\":1768,\"needFid\":false,\"service\":\"www.ixigua.com\","
                        + "\"migrate_info\":{\"ticket\":\"\",\"source\":\"node\"},\"cbUrlProtocol\":\"https\",\"union\":true}";
                String resp = http("https://ttwid.bytedance.com/ttwid/union/register/", "POST", body, null, "https://www.ixigua.com");
                // 从响应头拿 set-cookie：HttpURLConnection 取不到，改从返回体兜底；再退回固定探测
                java.net.HttpURLConnection hc = (java.net.HttpURLConnection) new java.net.URL(
                        "https://ttwid.bytedance.com/ttwid/union/register/").openConnection();
                hc.setConnectTimeout(12000); hc.setDoOutput(true);
                hc.setRequestMethod("POST");
                hc.setRequestProperty("Content-Type", "application/json");
                hc.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                String sc = hc.getHeaderField("set-cookie");
                if (sc == null) sc = hc.getHeaderField("Set-Cookie");
                if (sc != null && sc.contains("ttwid=")) {
                    ttwid = sc.split("ttwid=")[1].split(";")[0];
                    sp.edit().putString("ttwid", ttwid).apply();
                }
                try { hc.getInputStream().close(); } catch (Throwable ignored) {}
            } catch (Throwable e) { }
            if (ttwid == null) return;
        }
        String url = "https://live.douyin.com/webcast/room/web/enter/?aid=6383&device_platform=web&enter_from=web_live"
                + "&cookie_enabled=true&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome"
                + "&browser_version=124.0.0.0&web_rid=" + webRid;
        JSONObject r = new JSONObject(http(url, "GET", null, "ttwid=" + ttwid, "https://live.douyin.com"));
        if (r.optInt("status_code", -1) != 0) return;
        JSONObject d = r.optJSONObject("data");
        if (d == null) return;
        JSONArray rooms = d.optJSONArray("data");
        if (rooms == null || rooms.length() == 0) return;
        String roomStatus = String.valueOf(d.optInt("room_status", -1));
        String uname = d.optJSONObject("user") != null ? d.getJSONObject("user").optString("nickname", webRid) : webRid;
        saveInfo(sp, "douyin", webRid, uname, "0".equals(roomStatus) ? "1" : "0");
        if (changed(sp, key("dylive", webRid), roomStatus) && "0".equals(roomStatus)) {
            JSONObject room = rooms.getJSONObject(0);
            notify("【抖音】【" + uname + "】开播了", room.optString("title", ""), "https://live.douyin.com/" + webRid);
        }
    }

    // ---------- 斗鱼：开播 ----------
    private void checkDouyu(SharedPreferences sp, String roomId) throws Exception {
        JSONObject r = new JSONObject(http("https://www.douyu.com/betard/" + roomId, "GET", null, null, null));
        JSONObject room = r.optJSONObject("room");
        if (room == null) return;
        String uname = room.optString("nickname", roomId);
        String st = String.valueOf(room.optInt("show_status", 0));
        saveInfo(sp, "douyu", roomId, uname, st);
        if (changed(sp, key("douyu", roomId), st) && "1".equals(st)) {
            notify("【斗鱼】【" + uname + "】开播了", room.optString("room_name", ""), "https://www.douyu.com/" + roomId);
        }
    }

    // ---------- 虎牙：开播（页面脚本取流信息） ----------
    private void checkHuya(SharedPreferences sp, String roomId) throws Exception {
        String html = http("https://www.huya.com/" + roomId, "GET", null, null, null);
        int i = html.indexOf("stream: ");
        String st;
        JSONObject room = null;
        if (i < 0) st = "0";
        else {
            try {
                String js = html.substring(i + 8).split("};")[0].replace("undefined", "null");
                JSONObject r = new JSONObject(js);
                JSONArray data = r.optJSONArray("data");
                if (data == null || data.length() == 0) { st = "0"; }
                else {
                    JSONObject d0 = data.getJSONObject(0);
                    boolean live = d0.optJSONArray("gameStreamInfoList") != null
                            && d0.getJSONArray("gameStreamInfoList").length() > 0;
                    room = d0.optJSONObject("gameLiveInfo");
                    st = live ? "1" : "0";
                }
            } catch (Throwable e) { return; }
        }
        String uname = room != null ? room.optString("nick", roomId) : roomId;
        saveInfo(sp, "huya", roomId, uname, st);
        if (changed(sp, key("huya", roomId), st) && "1".equals(st)) {
            String title = room != null ? room.optString("roomName", "") : "";
            notify("【虎牙】【" + uname + "】开播了", title, "https://www.huya.com/" + roomId);
        }
    }
}
