package com.ziyuan.Assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;

/** 历史记录持久化：完成后进 SharedPreferences，下载页 tab 显示 */
public class HistoryStore {

    public static class Item {
        public String type;   // "视频" / "录制"
        public String title;
        public String path;   // 播放地址（file:// 或 content://）
        public String url;    // 来源地址（重新下载/复制地址/打开下载页）；旧记录为 null
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("dl_history", Context.MODE_PRIVATE);
    }

    /** 历史条目数(供下载页按需刷新: 仅数量变化才重新解析) */
    public static int count(Context c) {
        try { return sp(c).getInt("count", 0); } catch (Throwable ignored) { return 0; }
    }

    public static void add(Context c, String type, String title, String path) {
        add(c, type, title, path, null);
    }

    /** 记录一条完成项, url 为可重新下载的来源地址(可空) */
    public static void add(Context c, String type, String title, String path, String url) {
        try {
            SharedPreferences s = sp(c);
            int n = s.getInt("count", 0);
            SharedPreferences.Editor e = s.edit();
            e.putString("t" + n, type + "\u0001" + title + "\u0001" + path
                    + "\u0001" + (url == null ? "" : url));
            e.putInt("count", n + 1);
            e.apply();
        } catch (Throwable ignored) {}
    }

    public static void clearAll(Context c) {
        try { sp(c).edit().clear().apply(); } catch (Throwable ignored) {}
    }

    /** 文件是否仍存在(软件外删除的记录不再显示): file:// 直接 stat, content:// 打开一次探测 */
    public static boolean exists(Context c, Item it) {
        try {
            if (it.path == null || it.path.length() == 0) return false;
            if (it.path.startsWith("content://")) {
                android.os.ParcelFileDescriptor fd = null;
                try {
                    fd = c.getContentResolver().openFileDescriptor(android.net.Uri.parse(it.path), "r");
                    return fd != null;
                } finally { try { if (fd != null) fd.close(); } catch (Throwable ignored) {} }
            }
            String p = it.path.startsWith("file://") ? it.path.substring(7) : it.path;
            return new java.io.File(p).exists();
        } catch (Throwable t) { return false; }
    }

    /** 新→旧(文件已在软件外被删除的条目自动过滤, 不再显示) */
    public static ArrayList<Item> load(Context c) {
        ArrayList<Item> out = new ArrayList<Item>();
        try {
            SharedPreferences s = sp(c);
            int n = s.getInt("count", 0);
            for (int i = n - 1; i >= 0; i--) {
                String raw = s.getString("t" + i, null);
                if (raw == null) continue;
                String[] p = raw.split("\u0001", -1);   // -1: 保留末尾空段(旧记录无 url)
                if (p.length < 3) continue;
                Item it = new Item();
                it.type = p[0]; it.title = p[1]; it.path = p[2];
                it.url = p.length > 3 && p[3].length() > 0 ? p[3] : null;
                if (!exists(c, it)) continue;   // 软件外已删除: 跳过
                out.add(it);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** 按记录匹配删除(过滤显示后 index 会错位, 不能再用 removeAt) */
    public static void removeItem(Context c, Item target) {
        if (target == null) return;
        try {
            SharedPreferences s = sp(c);
            int n = s.getInt("count", 0);
            int real = -1;
            for (int i = n - 1; i >= 0; i--) {
                String raw = s.getString("t" + i, null);
                if (raw != null && rawMatches(raw, target)) { real = i; break; }
            }
            if (real < 0) return;
            SharedPreferences.Editor e = s.edit();
            for (int i = real; i < n - 1; i++) {
                e.putString("t" + i, s.getString("t" + (i + 1), null));
            }
            e.remove("t" + (n - 1));
            e.putInt("count", Math.max(0, n - 1));
            e.apply();
        } catch (Throwable ignored) {}
    }

    /** 按记录匹配更新标题与路径(重命名用) */
    public static void updateItem(Context c, Item target, String title, String path) {
        if (target == null) return;
        try {
            SharedPreferences s = sp(c);
            int n = s.getInt("count", 0);
            for (int i = n - 1; i >= 0; i--) {
                String raw = s.getString("t" + i, null);
                if (raw != null && rawMatches(raw, target)) {
                    String[] p = raw.split("\u0001", -1);
                    String type = p.length > 0 ? p[0] : "视频";
                    String url = p.length > 3 ? p[3] : "";
                    s.edit().putString("t" + i, type + "\u0001" + title + "\u0001" + path + "\u0001" + url).apply();
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean rawMatches(String raw, Item it) {
        try {
            String[] p = raw.split("\u0001", -1);
            if (p.length < 3) return false;
            return eq(p[1], it.title) && eq(p[2], it.path)
                    && (p.length < 4 ? it.url == null : eq(p[3], it.url == null ? "" : it.url));
        } catch (Throwable t) { return false; }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 重命名后更新条目(保留来源 url)。index 为 load 顺序（新→旧） */
    public static void updateAt(Context c, int index, String title, String path) {
        try {
            SharedPreferences s = sp(c);
            int n = s.getInt("count", 0);
            int real = n - 1 - index;
            if (real < 0 || real >= n) return;
            String raw = s.getString("t" + real, null);
            if (raw == null) return;
            String[] p = raw.split("\u0001", -1);
            String type = p.length > 0 ? p[0] : "视频";
            String url = p.length > 3 ? p[3] : "";
            s.edit().putString("t" + real, type + "\u0001" + title + "\u0001" + path + "\u0001" + url).apply();
        } catch (Throwable ignored) {}
    }

    public static void removeAt(Context c, int index) {  // index 为 load 顺序（新→旧）
        try {
            SharedPreferences s = sp(c);
            int n = s.getInt("count", 0);
            int real = n - 1 - index;
            SharedPreferences.Editor e = s.edit();
            for (int i = real; i < n - 1; i++) {
                e.putString("t" + i, s.getString("t" + (i + 1), null));
            }
            e.remove("t" + (n - 1));
            e.putInt("count", Math.max(0, n - 1));
            e.apply();
        } catch (Throwable ignored) {}
    }
}
