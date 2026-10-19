package com.ziyuan.Assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** GKD 复刻: 订阅存储/下载 + 规则索引 + 日志 */
public class GkdStore {
    private static final String PREF = "gkd";
    public static final String DEFAULT_SUB = "https://registry.npmmirror.com/@gkd-kit/subscribe/latest/files/gkd.json5";

    public static SharedPreferences sp(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    /** apps: [{id:"com.x.y", enable:true}], subs: [{url, name, version, data(json raw)}] */
    public static void setAppEnabled(Context c, String pkg, boolean on) {
        try {
            JSONArray apps = new JSONArray(sp(c).getString("apps", "[]"));
            JSONArray out = new JSONArray();
            boolean found = false;
            for (int i = 0; i < apps.length(); i++) {
                JSONObject o = apps.optJSONObject(i);
                if (o != null && pkg.equals(o.optString("id"))) { found = true; if (on) out.put(o.put("enable", true)); }
                else out.put(o);
            }
            if (!found && on) out.put(new JSONObject().put("id", pkg).put("enable", true));
            sp(c).edit().putString("apps", out.toString()).apply();
        } catch (Throwable ignored) {}
    }

    public static boolean isAppEnabled(Context c, String pkg) {
        try {
            JSONArray apps = new JSONArray(sp(c).getString("apps", "[]"));
            for (int i = 0; i < apps.length(); i++) {
                JSONObject o = apps.optJSONObject(i);
                if (o != null && pkg.equals(o.optString("id")) && o.optBoolean("enable")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static List<String> enabledApps(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray apps = new JSONArray(sp(c).getString("apps", "[]"));
            for (int i = 0; i < apps.length(); i++) {
                JSONObject o = apps.optJSONObject(i);
                if (o != null && o.optBoolean("enable")) out.add(o.optString("id"));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** 下载订阅(JSON5宽松: 去注释后按 JSON 解析), 存原始文本 */
    public static String downloadSub(Context c, String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000); con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", "Assistant-GKD/1.0");
        InputStream in = con.getResponseCode() < 400 ? con.getInputStream() : con.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        String raw = bo.toString("UTF-8");
        sp(c).edit().putString("sub_raw", raw).putString("sub_url", url)
                .putLong("sub_time", System.currentTimeMillis()).apply();
        return raw;
    }

    public static String subRaw(Context c) { return sp(c).getString("sub_raw", ""); }
    public static String subUrl(Context c) { return sp(c).getString("sub_url", DEFAULT_SUB); }

    /** 取某包启用的规则组(仅该包在启用列表中才返回) */
    public static JSONArray enabledRules(Context c, String pkg) {
        if (!isAppEnabled(c, pkg)) return null;
        try {
            String raw = subRaw(c); if (raw.length() == 0) return null;
            raw = stripJson5(raw);
            JSONObject root = new JSONObject(raw);
            JSONArray global = root.optJSONArray("globalGroups");
            JSONArray groups = root.optJSONArray("groups");
            JSONArray out = new JSONArray();
            collectAppGroups(groups, root.optJSONArray("apps"), pkg, out);
            // 全局组对所有启用包生效
            if (global != null) {
                for (int i = 0; i < global.length(); i++) {
                    JSONObject g = global.optJSONObject(i);
                    if (g != null && g.optBoolean("enable", true)) {
                        JSONObject g2 = new JSONObject(g.toString());
                        g2.put("rules", g2.optJSONArray("rules") != null ? g2.optJSONArray("rules") : g2.optJSONArray("groups"));
                        out.put(g2);
                    }
                }
            }
            return out;
        } catch (Throwable t) { return null; }
    }

    private static void collectAppGroups(JSONArray groups, JSONArray apps, String pkg, JSONArray out) {
        try {
            if (apps != null) {
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject a = apps.optJSONObject(i);
                    if (a == null || !pkg.equals(a.optString("id"))) continue;
                    JSONArray ag = a.optJSONArray("groups");
                    if (ag != null) for (int j = 0; j < ag.length(); j++) {
                        JSONObject g = ag.optJSONObject(j);
                        if (g != null && g.optBoolean("enable", true)) out.put(g);
                    }
                }
            }
            if (groups != null) for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.optJSONObject(i);
                if (g != null && g.optBoolean("enable", true)) out.put(g);
            }
        } catch (Throwable ignored) {}
    }

    static String stripJson5(String s) {
        StringBuilder b = new StringBuilder();
        boolean inStr = false, inLine = false, inBlock = false;
        char q = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i), nx = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (inLine) { if (ch == '\n') { inLine = false; b.append(ch); } continue; }
            if (inBlock) { if (ch == '*' && nx == '/') { inBlock = false; i++; } continue; }
            if (inStr) { b.append(ch); if (ch == q) inStr = false; continue; }
            if (ch == '"' || ch == '\'') { inStr = true; q = ch; b.append(ch); continue; }
            if (ch == '/' && nx == '/') { inLine = true; continue; }
            if (ch == '/' && nx == '*') { inBlock = true; i++; continue; }
            b.append(ch);
        }
        return b.toString();
    }

    public static void log(Context c, String pkg) {
        try {
            SharedPreferences p = sp(c);
            String old = p.getString("logs", "");
            String line = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date()) + " 命中 " + pkg;
            p.edit().putString("logs", line + "\n" + old).apply();
        } catch (Throwable ignored) {}
    }

    public static String logs(Context c) { return sp(c).getString("logs", ""); }
    public static void clearLogs(Context c) { sp(c).edit().putString("logs", "").apply(); }
}
