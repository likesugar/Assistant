package com.ziyuan.Assistant;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** HTML5 编辑器: 项目=真实磁盘目录 (外部私有目录/HBuilderX/<名>/), 支持任意文件/子目录/相对引用 */
public class H5Store {
    private static final String PREF = "h5ed";
    public static final String DEFAULT_HTML = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n<title>我的页面</title>\n<link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n<h1>我的项目</h1>\n<script src=\"script.js\"></script>\n</body>\n</html>";

    public static android.content.SharedPreferences sp(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static File root(Context c) {
        File d = new File(c.getExternalFilesDir(null), "HBuilderX");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    public static List<String> projects(Context c) {
        List<String> out = new ArrayList<>();
        File[] fs = root(c).listFiles();
        if (fs != null) { Arrays.sort(fs); for (File f : fs) if (f.isDirectory()) out.add(f.getName()); }
        Collections.reverse(out);
        return out;
    }
    public static boolean createProject(Context c, String name) {
        File d = new File(root(c), name);
        if (d.exists()) return false;
        d.mkdirs();
        writeFile(new File(d, "index.html"), DEFAULT_HTML);
        writeFile(new File(d, "style.css"), "body{font-family:sans-serif;}\n");
        writeFile(new File(d, "script.js"), "console.log('hello');\n");
        return true;
    }
    public static boolean removeProject(Context c, String name) {
        return deleteRecur(new File(root(c), name));
    }
    private static boolean deleteRecur(File f) {
        if (f == null) return false;
        if (f.isDirectory()) { File[] fs = f.listFiles(); if (fs != null) for (File x : fs) deleteRecur(x); }
        return f.delete();
    }
    public static File projectDir(Context c, String name) { return new File(root(c), name); }
    /** 递归列项目内文件 (相对路径, 文件夹优先) */
    public static List<String> listTree(File dir) {
        List<String> out = new ArrayList<>();
        walk(dir, "", out);
        return out;
    }
    private static void walk(File dir, String prefix, List<String> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs, (a, b) -> {
            boolean ad = a.isDirectory(), bd = b.isDirectory();
            if (ad != bd) return ad ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : fs) {
            String rel = prefix.length() == 0 ? f.getName() : prefix + "/" + f.getName();
            if (f.isDirectory()) { out.add(rel + "/"); walk(f, rel, out); }
            else out.add(rel);
        }
    }
    public static String asset(android.content.Context c, String name) {
        try {
            java.io.InputStream in = c.getAssets().open(name);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable t) { return ""; }
    }
    public static String readFile(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable t) { return ""; }
    }
    public static String queryName(Context c, android.net.Uri uri) {
        android.database.Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0) { String n = cur.getString(i); if (n != null && n.length() > 0) return n; }
            }
        } catch (Throwable ignored) {}
        return "import_" + System.currentTimeMillis();
    }
    public static void writeFile(File f, String content) {        try {
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            FileOutputStream out = new FileOutputStream(f);
            out.write(content.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {}
    }
    public static void writeFileBytes(File f, byte[] data) {
        try {
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            FileOutputStream out = new FileOutputStream(f);
            out.write(data);
            out.close();
        } catch (Throwable ignored) {}
    }
    public static void importFrom(Context c, File projDir, android.net.Uri uri, String displayName) {
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            writeFileBytes(new File(projDir, displayName), bo.toByteArray());
        } catch (Throwable ignored) {}
    }
    public static boolean isTextFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".css") || n.endsWith(".js")
                || n.endsWith(".json") || n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".xml")
                || n.endsWith(".svg") || n.endsWith(".xlt");
    }
    public static boolean isHtml(String name) { String n = name.toLowerCase(); return n.endsWith(".html") || n.endsWith(".htm"); }
}
