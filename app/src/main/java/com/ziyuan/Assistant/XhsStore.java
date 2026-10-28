package com.ziyuan.Assistant.xhs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** 设置（对齐原版 AppSettings 默认值）+ 任务历史（JSON 文件持久化，替代 Room）+ Cookie 状态 */
public final class XhsStore {
    private static final String SP = "xhs_settings";
    private static final String TASKS_FILE = "xhs_tasks.json";

    private final SharedPreferences sp;
    private final File tasksFile;

    public XhsStore(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
        tasksFile = new File(c.getApplicationContext().getFilesDir(), TASKS_FILE);
    }

    // ---------- 设置（键名/默认值对齐原版 AppSettings） ----------
    public boolean createLivePhotos() { return sp.getBoolean("createLivePhotos", true); }
    public void setCreateLivePhotos(boolean v) { sp.edit().putBoolean("createLivePhotos", v).apply(); }
    public boolean useCustomNaming() { return sp.getBoolean("useCustomNamingFormat", false); }
    public void setUseCustomNaming(boolean v) { sp.edit().putBoolean("useCustomNamingFormat", v).apply(); }
    public String namingTemplate() { return sp.getString("customNamingTemplate", XhsNaming.DEFAULT_TEMPLATE); }
    public void setNamingTemplate(String v) { sp.edit().putString("customNamingTemplate", v).apply(); }
    public boolean debugNotification() { return sp.getBoolean("debugNotificationEnabled", false); }
    public void setDebugNotification(boolean v) { sp.edit().putBoolean("debugNotificationEnabled", v).apply(); }
    public boolean selectiveDownload() { return sp.getBoolean("selectiveDownload", false); }
    public void setSelectiveDownload(boolean v) { sp.edit().putBoolean("selectiveDownload", v).apply(); }
    public boolean showMediaResolution() { return sp.getBoolean("showMediaResolution", false); }
    public void setShowMediaResolution(boolean v) { sp.edit().putBoolean("showMediaResolution", v).apply(); }
    public boolean keepScreenOn() { return sp.getBoolean("keepScreenOn", false); }
    public void setKeepScreenOn(boolean v) { sp.edit().putBoolean("keepScreenOn", v).apply(); }
    public boolean showClipboardBubble() { return sp.getBoolean("showClipboardBubble", true); }
    public void setShowClipboardBubble(boolean v) { sp.edit().putBoolean("showClipboardBubble", v).apply(); }
    public boolean autoReadClipboard() { return sp.getBoolean("autoReadClipboard", false); }
    public void setAutoReadClipboard(boolean v) { sp.edit().putBoolean("autoReadClipboard", v).apply(); }
    public boolean manualInputLinks() { return sp.getBoolean("manualInputLinks", false); }
    public void setManualInputLinks(boolean v) { sp.edit().putBoolean("manualInputLinks", v).apply(); }
    public String customStorageDir() { return sp.getString("customStorageDir", null); }
    public String customStorageName() { return sp.getString("customStorageName", null); }
    public void setCustomStorage(String dir, String name) {
        sp.edit().putString("customStorageDir", dir).putString("customStorageName", name).apply();
    }
    public void clearCustomStorage() { setCustomStorage(null, null); }
    public boolean checkExistingFiles() { return sp.getBoolean("checkExistingFilesBeforeSave", true); }
    public void setCheckExistingFiles(boolean v) { sp.edit().putBoolean("checkExistingFilesBeforeSave", v).apply(); }

    /** 默认保存目录：应用专属 files/xhs（直写免权限）；自定义目录优先 */
    public File mediaDir(Context c) {
        String custom = customStorageDir();
        if (custom != null && !custom.trim().isEmpty()) {
            File f = new File(custom);
            f.mkdirs();
            if (f.canWrite()) return f;
        }
        File f = new File(c.getExternalFilesDir(null), "xhs");
        f.mkdirs();
        return f;
    }

    // ---------- 任务 ----------
    public static class Task {
        public String id;            // noteId 或 时间戳
        public String url;
        public String title;
        public String author;
        public String description;
        public String coverUrl;
        public String status;        // pending / running / done / failed / stopped
        public int progress;         // 0-100
        public long totalBytes, doneBytes;
        public long created;
        public String error;
        public List<String> files = new ArrayList<String>();
        public String noteJson;      // 解析结果（复制文案/选择性下载用）

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("id", nz(id)); o.put("url", nz(url)); o.put("title", nz(title));
                o.put("author", nz(author)); o.put("description", nz(description)); o.put("cover", nz(coverUrl));
                o.put("status", nz(status)); o.put("progress", progress);
                o.put("total", totalBytes); o.put("done", doneBytes); o.put("created", created);
                o.put("error", nz(error));
                JSONArray f = new JSONArray();
                for (String s : files) f.put(s);
                o.put("files", f);
                if (noteJson != null) o.put("note", new JSONObject(noteJson));
                return o;
            } catch (Throwable t) { return new JSONObject(); }
        }

        public static Task fromJson(JSONObject o) {
            Task t = new Task();
            t.id = o.optString("id");
            t.url = o.optString("url");
            t.title = o.optString("title");
            t.author = o.optString("author");
            t.description = o.optString("description");
            t.coverUrl = o.optString("cover");
            t.status = o.optString("status", "done");
            t.progress = o.optInt("progress");
            t.totalBytes = o.optLong("total");
            t.doneBytes = o.optLong("done");
            t.created = o.optLong("created");
            t.error = o.optString("error");
            JSONArray f = o.optJSONArray("files");
            if (f != null) for (int i = 0; i < f.length(); i++) t.files.add(f.optString(i));
            JSONObject n = o.optJSONObject("note");
            t.noteJson = n == null ? null : n.toString();
            return t;
        }

        private static String nz(String s) { return s == null ? "" : s; }
    }

    public synchronized List<Task> loadTasks() {
        List<Task> out = new ArrayList<Task>();
        try {
            if (!tasksFile.exists()) return out;
            JSONObject root = new JSONObject(readFile(tasksFile));
            JSONArray arr = root.optJSONArray("tasks");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Task.fromJson(o));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public synchronized void saveTasks(List<Task> tasks) {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Task t : tasks) arr.put(t.toJson());
            root.put("tasks", arr);
            writeFile(tasksFile, root.toString());
        } catch (Throwable ignored) { }
    }

    private static String readFile(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
        fis.close();
        return bos.toString("UTF-8");
    }

    private static void writeFile(File f, String s) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(s.getBytes("UTF-8"));
        fos.close();
    }
}
