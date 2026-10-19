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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GKD 复刻: 订阅存储/下载 + 规则索引 + 日志 */
public class GkdStore {
    private static final String PREF = "gkd";

    private static volatile JSONObject cachedRoot; private static String cachedRawSig = "";
    /** 订阅解析一次全局缓存 (卡顿根因: 每行重解析 545KB) */
    public static JSONObject subRoot(Context c) {
        String raw = subRaw(c);
        if (raw.length() == 0) { cachedRoot = null; cachedRawSig = ""; return null; }
        if (cachedRoot != null && raw.length() == cachedRawSig.length() && raw.equals(cachedRawSig)) return cachedRoot;
        try {
            cachedRoot = new JSONObject(stripJson5(raw));
            cachedRawSig = raw;
        } catch (Throwable t) { cachedRoot = null; }
        return cachedRoot;
    }
    public static final String DEFAULT_SUB = "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files/dist/gkd.json5";

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
            JSONObject root = subRoot(c); if (root == null) return null;
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

    /** JSON5 -> JSON: 去注释 + 单引号转双引号 + 裸 key 加引号 */
    static String stripJson5(String s) {
        StringBuilder b = new StringBuilder(s.length() + 64);
        boolean inStr = false, inLine = false, inBlock = false;
        char q = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i), nx = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (inLine) { if (ch == '\n') { inLine = false; b.append(ch); } continue; }
            if (inBlock) { if (ch == '*' && nx == '/') { inBlock = false; i++; } continue; }
            if (inStr) {
                if (ch == '\\' && i + 1 < s.length()) {
                    char n2 = s.charAt(i + 1);
                    if (n2 == '\'') { b.append('\''); } // JSON5 的 \' 在 JSON 里就是 '
                    else { b.append('\\').append(n2); }
                    i++;
                    continue;
                }
                if (ch == q) { inStr = false; b.append('"'); continue; } // 单引号收尾转双
                if (q == '\'' && ch == '"') { b.append('\\').append('"'); continue; }
                b.append(ch); continue;
            }
            if (ch == '"' || ch == '\'') { inStr = true; q = ch; b.append('"'); continue; }
            if (ch == '/' && nx == '/') { inLine = true; continue; }
            if (ch == '/' && nx == '*') { inBlock = true; i++; continue; }
            b.append(ch);
        }
        // 裸 key: ({,后) 标识符: -> "标识符":
        return b.toString().replaceAll("(\\{|,)(\\s*)([A-Za-z_$][A-Za-z0-9_$]*)\\s*:", "$1$2\"$3\":");
    }

    public static void log(Context c, String pkg, String groupName) {
        try {
            SharedPreferences p = sp(c);
            String old = p.getString("logs", "");
            String line = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date()) + " " + (groupName == null ? "规则" : groupName) + " · " + pkg;
            p.edit().putString("logs", line + "\n" + old).apply();
        } catch (Throwable ignored) {}
    }

    /** 仪表盘统计: [全局组, 本机启用应用数, 订阅覆盖的启用应用规则组总数, 触发次数] */
    public static int[] stats(Context c) {
        int[] out = new int[4];
        try {
            JSONObject root = subRoot(c);
            if (root == null) return out;
            JSONArray gl = root.optJSONArray("globalGroups");
            if (gl != null) for (int i = 0; i < gl.length(); i++) {
                JSONObject g = gl.optJSONObject(i);
                if (g != null && g.optBoolean("enable", true)) out[0]++;
            }
            JSONArray apps = root.optJSONArray("apps");
            Set<String> enabled = new HashSet<>();
            JSONArray eas = new JSONArray(sp(c).getString("apps", "[]"));
            for (int i = 0; i < eas.length(); i++) {
                JSONObject o = eas.optJSONObject(i);
                if (o != null && o.optBoolean("enable", false)) enabled.add(o.optString("id"));
            }
            if (apps != null) for (int i = 0; i < apps.length(); i++) {
                JSONObject a = apps.optJSONObject(i);
                if (a == null || !enabled.contains(a.optString("id"))) continue;
                out[1]++;
                JSONArray gs = a.optJSONArray("groups");
                if (gs != null) for (int j = 0; j < gs.length(); j++) {
                    JSONObject g = gs.optJSONObject(j);
                    if (g != null && g.optBoolean("enable", true)) out[2]++;
                }
            }
            out[3] = logs(c).isEmpty() ? 0 : logs(c).split("\n").length;
        } catch (Throwable ignored) {}
        return out;
    }

    public static String subName(Context c) {
        try { JSONObject r = subRoot(c); return r == null ? null : r.optString("name", null); }
        catch (Throwable t) { return null; }
    }

    /** 应用列表副标题: {全局数, 组数, 启用组, 关闭组} 未覆盖返回 null */
    public static int[] appGroupInfo(Context c, String pkg) {
        try {
            JSONObject root = subRoot(c);
            if (root == null) return null;
            int global = 0;
            JSONArray gl = root.optJSONArray("globalGroups");
            if (gl != null) for (int i = 0; i < gl.length(); i++) {
                JSONObject g = gl.optJSONObject(i);
                if (g != null && g.optBoolean("enable", true)) global++;
            }
            JSONArray apps = root.optJSONArray("apps");
            if (apps != null) for (int i = 0; i < apps.length(); i++) {
                JSONObject a = apps.optJSONObject(i);
                if (a == null || !pkg.equals(a.optString("id"))) continue;
                JSONArray gs = a.optJSONArray("groups");
                int n = gs == null ? 0 : gs.length(), on = 0;
                if (gs != null) for (int j = 0; j < gs.length(); j++) {
                    JSONObject g = gs.optJSONObject(j);
                    if (g != null && g.optBoolean("enable", true)) on++;
                }
                return new int[]{global, n, on, n - on};
            }
            return null;
        } catch (Throwable t) { return null; }
    }

    public static String logs(Context c) { return sp(c).getString("logs", ""); }
    public static void clearLogs(Context c) { sp(c).edit().putString("logs", "").apply(); }
}
