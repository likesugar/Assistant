package com.ziyuan.Assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** HTML5 编辑器数据: 项目存 sp (h5projs), 插件 JS 内置 assets */
public class H5Store {
    private static final String PREF = "h5ed";
    public static final String DEFAULT_HTML = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n<title>我的页面</title>\n<style>\nbody{font-family:sans-serif;padding:16px}\n</style>\n</head>\n<body>\n<div id=\"app\"></div>\n<script>\n// 在这里写你的代码\n</script>\n</body>\n</html>";

    public static SharedPreferences sp(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static JSONArray projects(Context c) {
        try { return new JSONArray(sp(c).getString("projs", "[]")); } catch (Throwable t) { return new JSONArray(); }
    }
    public static void save(Context c, JSONArray a) { sp(c).edit().putString("projs", a.toString()).apply(); }

    public static void createProject(Context c, String name) {
        try {
            JSONArray a = projects(c);
            JSONObject p = new JSONObject();
            p.put("name", name);
            p.put("html", DEFAULT_HTML);
            p.put("plugins", new JSONObject().put("vconsole", false).put("vue", false));
            a.put(p);
            save(c, a);
        } catch (Throwable ignored) {}
    }
    public static void removeProject(Context c, int idx) {
        try {
            JSONArray out = new JSONArray();
            JSONArray a = projects(c);
            for (int i = 0; i < a.length(); i++) if (i != idx) out.put(a.opt(i));
            save(c, out);
        } catch (Throwable ignored) {}
    }
    public static void saveProject(Context c, int idx, JSONObject p) {
        try {
            JSONArray a = projects(c);
            a.put(idx, p);
            save(c, a);
        } catch (Throwable ignored) {}
    }

    public static String asset(Context c, String name) {
        try {
            InputStream in = c.getAssets().open(name);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable t) { return ""; }
    }
}
