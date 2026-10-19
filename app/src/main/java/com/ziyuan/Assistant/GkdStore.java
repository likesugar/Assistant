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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GKD 复刻: 订阅存储/下载 + 规则索引 + 日志 */
public class GkdStore {
    private static final String PREF = "gkd";

    public static final String DEFAULT_SUB = "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files/dist/gkd.json5";
    public static final String AISOULER_SUB = "https://registry.npmmirror.com/@aisouler/gkd_subscription/latest/files/dist/AIsouler_gkd.json5";

    // ===== 多订阅模型 (官方订阅页 1:1): subs_json=[{url,name,author,version,enabled,time,data}] =====
    public static JSONArray subs(Context c) {
        try {
            JSONArray s = new JSONArray(sp(c).getString("subs_json", "[]"));
            if (s.length() == 0) {
                String oldRaw = sp(c).getString("sub_raw", "");
                if (oldRaw.length() > 0) {
                    JSONObject o = new JSONObject();
                    o.put("url", sp(c).getString("sub_url", DEFAULT_SUB));
                    o.put("enabled", true);
                    JSONObject r = new JSONObject(stripJson5(oldRaw));
                    o.put("data", r.toString());
                    o.put("name", r.optString("name"));
                    o.put("author", r.optString("author"));
                    o.put("version", r.optInt("version"));
                    s.put(o);
                } else {
                    s.put(new JSONObject().put("url", AISOULER_SUB).put("enabled", true).put("name", "AIsouler的GKD订阅"));
                }
                sp(c).edit().putString("subs_json", s.toString()).apply();
            }
            return s;
        } catch (Throwable t) { return new JSONArray(); }
    }
    public static void saveSubs(Context c, JSONArray s) { sp(c).edit().putString("subs_json", s.toString()).apply(); cachedMerge = null; }
    public static void addSub(Context c, String url) {
        JSONArray s = subs(c);
        s.put(new JSONObject().put("url", url).put("enabled", true).put("name", url));
        saveSubs(c, s);
        try { refreshSub(c, s.length() - 1); } catch (Throwable ignored) {}
    }
    public static void removeSub(Context c, int idx) {
        JSONArray out = new JSONArray();
        JSONArray s = subs(c);
        for (int i = 0; i < s.length(); i++) if (i != idx) out.put(s.optJSONObject(i));
        saveSubs(c, out);
    }
    public static void setSubEnabled(Context c, int idx, boolean on) {
        JSONArray s = subs(c);
        JSONObject o = s.optJSONObject(idx);
        if (o != null) { try { o.put("enabled", on); } catch (Throwable ignored) {} saveSubs(c, s); }
    }
    public static String refreshSub(Context c, int idx) throws Exception {
        JSONArray s = subs(c);
        JSONObject o = s.optJSONObject(idx);
        if (o == null) throw new Exception("订阅不存在");
        String url = o.optString("url");
        String raw = httpGet(url);
        JSONObject root = new JSONObject(stripJson5(raw));
        if (root.optJSONArray("groups") == null && root.optJSONArray("globalGroups") == null && root.optJSONArray("apps") == null)
            throw new Exception("内容不是GKD订阅规则");
        o.put("data", root.toString());
        o.put("name", root.optString("name", url));
        o.put("author", root.optString("author", ""));
        o.put("version", root.optInt("version", 0));
        o.put("time", System.currentTimeMillis());
        saveSubs(c, s);
        return o.optString("name");
    }
    public static String httpGet(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000); con.setReadTimeout(30000);
        con.setRequestProperty("User-Agent", "Assistant-GKD/1.0");
        InputStream in = con.getResponseCode() < 400 ? con.getInputStream() : con.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static volatile JSONObject cachedMerge;
    /** 合并所有启用订阅: globalGroups concat + apps by id 合并(key 重写 si*100000+key) + 顶层 groups concat */
    public static JSONObject subRoot(Context c) {
        try {
            JSONArray subs = subs(c);
            if (cachedMerge != null) return cachedMerge;
            JSONObject merged = new JSONObject();
            JSONArray gl = new JSONArray(), apps = new JSONArray(), grps = new JSONArray();
            Map<String, JSONObject> appById = new HashMap<>();
            boolean any = false;
            Set<String> gNames = new HashSet<>();
            for (int si = 0; si < subs.length(); si++) {
                JSONObject o = subs.optJSONObject(si);
                if (o == null || !o.optBoolean("enabled", true)) continue;
                String data = o.optString("data", "");
                if (data.length() == 0) continue;
                JSONObject r;
                try { r = new JSONObject(data); } catch (Throwable t) { continue; }
                any = true;
                JSONArray g0 = r.optJSONArray("globalGroups");
                if (g0 != null) for (int i = 0; i < g0.length(); i++) {
                    JSONObject g = g0.optJSONObject(i);
                    if (g == null) continue;
                    try {
                        String nm = g.optString("name", "全局" + i);
                        if (gNames.contains(nm)) g.put("name", nm + "(" + (si + 1) + ")");
                        gNames.add(g.optString("name"));
                    } catch (Throwable ignored) {}
                    gl.put(g);
                }
                JSONArray aps = r.optJSONArray("apps");
                if (aps != null) for (int i = 0; i < aps.length(); i++) {
                    JSONObject a = aps.optJSONObject(i);
                    if (a == null) continue;
                    String id = a.optString("id");
                    JSONArray gs = a.optJSONArray("groups");
                    if (gs != null) for (int j = 0; j < gs.length(); j++) {
                        JSONObject g = gs.optJSONObject(j);
                        if (g != null) { try { g.put("key", si * 100000 + g.optInt("key", j)); } catch (Throwable ignored) {} }
                    }
                    JSONObject exist = appById.get(id);
                    if (exist == null) { appById.put(id, a); apps.put(a); }
                    else {
                        JSONArray eg = exist.optJSONArray("groups");
                        if (eg == null) { eg = new JSONArray(); exist.put("groups", eg); }
                        if (gs != null) for (int j = 0; j < gs.length(); j++) { JSONObject g = gs.optJSONObject(j); if (g != null) eg.put(g); }
                    }
                }
                JSONArray gss = r.optJSONArray("groups");
                if (gss != null) for (int i = 0; i < gss.length(); i++) { JSONObject g = gss.optJSONObject(i); if (g != null) grps.put(g); }
            }
            if (!any) return null;
            merged.put("globalGroups", gl);
            merged.put("apps", apps);
            merged.put("groups", grps);
            cachedMerge = merged;
            return merged;
        } catch (Throwable t) { return null; }
    }

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

    // ===== per-app 开关覆盖 + 自定义组 (复刻官方应用规则管理页) =====
    /** ov JSON: {pkg:{ov:{"G组名"/"组key":bool}, custom:[{name,matches}]}} */
    private static JSONObject ovAll(Context c) {
        try { return new JSONObject(sp(c).getString("ov", "{}")); } catch (Throwable t) { return new JSONObject(); }
    }
    private static void saveOv(Context c, JSONObject all) { sp(c).edit().putString("ov", all.toString()).apply(); }
    private static JSONObject ovPkg(Context c, String pkg) {
        try { return ovAll(c).optJSONObject(pkg); } catch (Throwable t) { return null; }
    }
    private static void setOv(Context c, String pkg, String key, boolean on) {
        try {
            JSONObject all = ovAll(c);
            JSONObject p = all.optJSONObject(pkg);
            if (p == null) { p = new JSONObject(); all.put(pkg, p); }
            JSONObject o = p.optJSONObject("ov");
            if (o == null) { o = new JSONObject(); p.put("ov", o); }
            o.put(key, on);
            saveOv(c, all);
        } catch (Throwable ignored) {}
    }
    public static Boolean ovOf(Context c, String pkg, String key) {
        try {
            JSONObject o = ovPkg(c, pkg) == null ? null : ovPkg(c, pkg).optJSONObject("ov");
            if (o == null || !o.has(key)) return null;
            return o.getBoolean(key);
        } catch (Throwable t) { return null; }
    }
    public static boolean effGlobalOn(Context c, String pkg, String name, boolean def) {
        Boolean v = ovOf(c, pkg, "G" + name);
        return v == null ? def : v;
    }
    public static boolean effGroupOn(Context c, String pkg, int key, boolean def) {
        Boolean v = ovOf(c, pkg, String.valueOf(key));
        return v == null ? def : v;
    }
    public static void setGlobalOv(Context c, String pkg, String name, boolean on) { setOv(c, pkg, "G" + name, on); }
    public static void setGroupOv(Context c, String pkg, int key, boolean on) { setOv(c, pkg, String.valueOf(key), on); }

    public static JSONArray globalGroups(Context c) { try { JSONObject r = subRoot(c); return r == null ? null : r.optJSONArray("globalGroups"); } catch (Throwable t) { return null; } }
    public static JSONArray appGroupsRaw(Context c, String pkg) {
        try {
            JSONObject r = subRoot(c); if (r == null) return null;
            JSONArray apps = r.optJSONArray("apps");
            if (apps != null) for (int i = 0; i < apps.length(); i++) {
                JSONObject a = apps.optJSONObject(i);
                if (a != null && pkg.equals(a.optString("id"))) return a.optJSONArray("groups");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void addCustom(Context c, String pkg, String name, String matches) {
        try {
            JSONObject all = ovAll(c);
            JSONObject p = all.optJSONObject(pkg);
            if (p == null) { p = new JSONObject(); all.put(pkg, p); }
            JSONArray cs = p.optJSONArray("custom");
            if (cs == null) { cs = new JSONArray(); p.put("custom", cs); }
            cs.put(new JSONObject().put("name", name).put("matches", matches));
            saveOv(c, all);
        } catch (Throwable ignored) {}
    }
    public static JSONArray customGroups(Context c, String pkg) {
        try {
            JSONObject p = ovPkg(c, pkg);
            return p == null ? null : p.optJSONArray("custom");
        } catch (Throwable t) { return null; }
    }

    // ===== 应用白名单(屏蔽匹配) + 触发计数 =====
    public static java.util.Set<String> blockedApps(Context c) {
        return sp(c).getStringSet("blocked_apps", new HashSet<>());
    }
    public static boolean isBlocked(Context c, String pkg) { return blockedApps(c).contains(pkg); }
    public static void toggleBlocked(Context c, String pkg) {
        Set<String> s = new HashSet<>(blockedApps(c));
        if (!s.remove(pkg)) s.add(pkg);
        sp(c).edit().putStringSet("blocked_apps", s).apply();
    }
    /** 每包触发次数 (从日志统计) */
    public static Map<String, Integer> triggerCounts(Context c) {
        Map<String, Integer> m = new HashMap<>();
        try {
            for (String line : logs(c).split("\n")) {
                String[] parts = line.split(" · ");
                if (parts.length >= 3) { String p = parts[2]; Integer v = m.get(p); m.put(p, v == null ? 1 : v + 1); }
            }
        } catch (Throwable ignored) {}
        return m;
    }
    /** 每包最近触发时间戳(行序用) */
    public static Map<String, Long> lastTrigger(Context c) {
        Map<String, Long> m = new HashMap<>();
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA);
            String[] lines = logs(c).split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String[] parts = lines[i].split(" · ");
                if (parts.length >= 3 && !m.containsKey(parts[2])) {
                    try { m.put(parts[2], f.parse(parts[0]).getTime()); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return m;
    }

    /** 取某包启用的规则组(含 per-app 覆盖开关与自定义组) */
    public static JSONArray enabledRules(Context c, String pkg) {
        if (isBlocked(c, pkg)) return null; // 白名单(屏蔽匹配)
        if (!isAppEnabled(c, pkg)) return null;
        try {
            JSONObject root = subRoot(c); if (root == null) return null;
            JSONArray global = root.optJSONArray("globalGroups");
            JSONArray groups = root.optJSONArray("groups");
            JSONArray out = new JSONArray();
            // 应用专属组: effGroupOn 尊重 per-app 覆盖
            JSONArray agr = appGroupsRaw(c, pkg);
            if (agr != null) for (int j = 0; j < agr.length(); j++) {
                JSONObject g = agr.optJSONObject(j);
                if (g == null) continue;
                if (effGroupOn(c, pkg, g.optInt("key", j), g.optBoolean("enable", true))) out.put(g);
            }
            if (groups != null) for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.optJSONObject(i);
                if (g != null && g.optBoolean("enable", true)) out.put(g);
            }
            // 全局组: effGlobalOn 尊重 per-app 覆盖
            if (global != null) {
                for (int i = 0; i < global.length(); i++) {
                    JSONObject g = global.optJSONObject(i);
                    if (g == null) continue;
                    String name = g.optString("name", "全局" + i);
                    if (!effGlobalOn(c, pkg, name, g.optBoolean("enable", true))) continue;
                    JSONObject g2 = new JSONObject(g.toString());
                    g2.put("rules", g2.optJSONArray("rules") != null ? g2.optJSONArray("rules") : g2.optJSONArray("groups"));
                    out.put(g2);
                }
            }
            // 自定义组
            JSONArray cs = customGroups(c, pkg);
            if (cs != null) for (int i = 0; i < cs.length(); i++) {
                JSONObject cu = cs.optJSONObject(i);
                if (cu == null) continue;
                JSONObject g2 = new JSONObject();
                g2.put("name", cu.optString("name", "自定义"));
                JSONArray ms = new JSONArray(); ms.put(cu.optString("matches", ""));
                JSONObject r2 = new JSONObject(); r2.put("matches", ms);
                JSONArray rs = new JSONArray(); rs.put(r2);
                g2.put("rules", rs);
                out.put(g2);
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
        try {
            JSONArray s = subs(c);
            for (int i = 0; i < s.length(); i++) {
                JSONObject o = s.optJSONObject(i);
                if (o != null && o.optBoolean("enabled", true) && o.optString("data", "").length() > 0)
                    return o.optString("name", null);
            }
            return null;
        } catch (Throwable t) { return null; }
    }
    public static int enabledSubCount(Context c) {
        int n = 0;
        JSONArray s = subs(c);
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && o.optBoolean("enabled", true) && o.optString("data", "").length() > 0) n++;
        }
        return n;
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
                    if (g != null && effGroupOn(c, pkg, g.optInt("key", j), g.optBoolean("enable", true))) on++;
                }
                return new int[]{global, n, on, n - on};
            }
            return null;
        } catch (Throwable t) { return null; }
    }

    public static String logs(Context c) { return sp(c).getString("logs", ""); }
    public static void clearLogs(Context c) { sp(c).edit().putString("logs", "").apply(); }
}
