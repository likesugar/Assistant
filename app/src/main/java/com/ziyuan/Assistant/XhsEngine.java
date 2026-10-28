package com.ziyuan.Assistant.xhs;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 下载调度中枢：解析→（选择性）下载→任务历史。单例，UI 订阅刷新 */
public final class XhsEngine {
    public interface Listener { void onChanged(); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new ArrayList<Listener>();
    private static final Map<String, AtomicBoolean> CANCELS = new LinkedHashMap<String, AtomicBoolean>();
    private static final List<XhsStore.Task> TASKS = new ArrayList<XhsStore.Task>();
    private static XhsStore STORE;
    private static Context CTX;

    private XhsEngine() {}

    public static void init(Context c) {
        if (STORE != null) return;
        CTX = c.getApplicationContext();
        STORE = new XhsStore(CTX);
        restore();
    }

    public static XhsStore store() { return STORE; }
    public static List<XhsStore.Task> tasks() { return TASKS; }

    public static void addListener(Listener l) { if (!LISTENERS.contains(l)) LISTENERS.add(l); }
    public static void removeListener(Listener l) { LISTENERS.remove(l); }
    private static void notifyUi() { MAIN.post(new Runnable() { public void run() {
        for (Listener l : LISTENERS) { try { l.onChanged(); } catch (Throwable ignored) { } }
    }}); }

    // ---------- 提交 ----------
    public static XhsStore.Task enqueue(Context c, String rawText, boolean infoOnly) {
        List<String> links = XhsParser.extractLinks(rawText, null);
        if (links.isEmpty()) return null;
        String link = links.get(0);
        for (XhsStore.Task t : TASKS) if (link.equals(t.url) && !("done".equals(t.status) || "failed".equals(t.status))) return t;
        final XhsStore.Task t = new XhsStore.Task();
        t.id = "t" + System.currentTimeMillis();
        t.url = link;
        t.description = infoOnly ? rawText : XhsParser.extractShareTitle(rawText);
        t.status = "pending";
        t.created = System.currentTimeMillis();
        TASKS.add(0, t);
        persist();
        notifyUi();
        runTask(t, rawText);
        return t;
    }

    private static void runTask(final XhsStore.Task task, final String rawText) {
        POOL.execute(new Runnable() { public void run() {
            AtomicBoolean cancel = CANCELS.get(task.id);
            if (cancel == null) { cancel = new AtomicBoolean(false); CANCELS.put(task.id, cancel); }
            try {
                task.status = "running";
                task.error = null;
                notifyUi();
                String url = task.url;
                if (XhsParser.isShortUrl(url)) {
                    final String u0 = url;
                    url = XhsNet.resolveShort(url);
                    if (url == null || url.isEmpty()) throw new Exception("短链解析失败");
                    task.url = url;
                }
                String html = XhsNet.fetchHtml(url);
                if (html == null || html.isEmpty()) throw new Exception("页面获取失败");
                XhsParser.Note note = XhsParser.parse(html, XhsParser.extractPostId(url), url);
                if (note == null || note.items.isEmpty()) throw new Exception("解析失败：未找到媒体");
                task.title = XhsNaming.sanitize(note.title);
                if (task.title.isEmpty() && nz(note.body).length() > 0)
                    task.title = XhsNaming.sanitize(nz(note.body).length() > 30 ? nz(note.body).substring(0, 30) : nz(note.body));
                task.author = note.authorName;
                if (task.description == null || task.description.isEmpty()) task.description = note.body;
                task.noteJson = noteToJson(note);
                task.status = "selecting";
                task.progress = 0;
                if (STORE.selectiveDownload()) {
                    persist(); notifyUi();          // 等 UI 弹勾选
                } else {
                    downloadItemsList(task, note.items);
                }
            } catch (Throwable e) {
                if (isCancel(e)) { task.status = "stopped"; }
                else { task.status = "failed"; task.error = e.getMessage() == null ? e.toString() : e.getMessage(); }
                persist(); notifyUi();
            }
        }});
    }

    /** 用户勾选后继续 */
    public static void continueSelective(final XhsStore.Task t, final List<XhsParser.Media> chosen) {
        if (chosen == null || chosen.isEmpty()) { remove(t); return; }
        t.status = "running";
        notifyUi();
        POOL.execute(new Runnable() { public void run() {
            try {
                XhsParser.Note note = noteFromJson(t.noteJson);
                downloadItemsList(t, chosen);
            } catch (Throwable e) {
                if (isCancel(e)) t.status = "stopped";
                else { t.status = "failed"; t.error = e.getMessage() == null ? e.toString() : e.getMessage(); }
                persist(); notifyUi();
            }
        }});
    }

    private static void downloadItemsList(final XhsStore.Task task, List<XhsParser.Media> items) throws Exception {
        String custom = STORE.customStorageDir();
        boolean useStore = custom != null && !custom.trim().isEmpty();
        String relDir = (nz(task.author).isEmpty() ? "" : task.author + "-") + nz(task.title);
        relDir = XhsNaming.sanitize(relDir);
        if (relDir.isEmpty()) relDir = "XHS_" + task.id;
        File dir = new File(STORE.mediaDir(CTX), relDir);
        int index = 0;
        boolean checkExisting = STORE.checkExistingFiles();
        for (XhsParser.Media m : items) {
            index++;
            AtomicBoolean cancel = CANCELS.get(task.id);
            if (cancel != null && cancel.get()) throw new Exception("cancelled");
            boolean isVideo = "video".equals(m.kind);
            boolean isLive = "live".equals(m.kind);
            String base = STORE.useCustomNaming()
                    ? XhsNaming.apply(STORE.namingTemplate(), namingNote(noteFromJson(task.noteJson)), index, task.created)
                    : XhsNaming.sanitize((index > 1 ? task.title + "_" + index : task.title));
            if (isLive) base = base + "_live";
            task.status = "running";
            XhsNet.Progress cb = new XhsNet.Progress() {
                public void onProgress(long done, long total) {
                    task.totalBytes = total; task.doneBytes = done;
                    task.progress = total > 0 ? (int) (done * 100 / total) : 0;
                    notifyUi();
                }
            };
            if (isVideo || isLive) {
                String name = base + ".mp4";
                String saved = saveOne(useStore, true, dir, relDir, name, m.url, cb, cancel, checkExisting);
                if (saved != null) task.files.add(saved);
                if (isLive && m.previewUrl != null && !m.previewUrl.isEmpty()) {
                    String img = base + ".jpg";
                    String s2 = saveOne(useStore, false, dir, relDir, img, m.previewUrl, null, cancel, checkExisting);
                    if (s2 != null) task.files.add(s2);
                }
            } else {
                String ext = XhsNet.probeExt(m.url, m.url.toLowerCase().contains(".png") ? ".png" : ".jpg");
                String name = base + ext;
                String saved = saveOne(useStore, false, dir, relDir, name, m.url, cb, cancel, checkExisting);
                if (saved != null) task.files.add(saved);
            }
        }
        task.status = "done";
        task.progress = 100;
        persist(); notifyUi();
    }

    /** 返回保存位置字符串（content:// 或文件路径）；重复跳过返回 null */
    private static String saveOne(boolean useStore, boolean isVideo, File dir, String relDir, String name,
                                  String url, XhsNet.Progress cb, AtomicBoolean cancel, boolean checkExisting) throws Exception {
        if (useStore) {
            if (checkExisting && XhsSink.existsStore(CTX, isVideo, "Pictures/XHS下载/" + relDir, name)) return null;
            String rel = (isVideo ? "Movies/" : "Pictures/") + "XHS下载/" + relDir;
            return XhsSink.saveStore(CTX, isVideo, rel, name, url, cb, cancel);
        }
        if (!dir.exists()) dir.mkdirs();
        if (checkExisting && uniqueFile(dir, name) == null) return null;
        File dest = new File(dir, name);
        XhsSink.saveFile(dest, url, cb, cancel);
        return dest.getAbsolutePath();
    }

    /** checkExisting：存在同名返回 null 表示跳过，否则返回目标（重命名 _1/_2） */
    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!STORE.checkExistingFiles()) return f;
        if (!f.exists()) return f;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        int i = 1;
        while (new File(dir, base + "_" + i + ext).exists()) i++;
        return new File(dir, base + "_" + i + ext);
    }

    private static boolean isCancel(Throwable e) {
        return e instanceof java.io.IOException && "cancelled".equals(e.getMessage());
    }

    // ---------- 序列化 / 历史 ----------
    public static void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (XhsStore.Task t : TASKS) arr.put(t.toJson());
            JSONObject o = new JSONObject();
            o.put("tasks", arr);
            CTX.getSharedPreferences("xhs_tasks", Context.MODE_PRIVATE)
              .edit().putString("data", o.toString()).apply();
        } catch (Throwable ignored) { }
    }

    private static void restore() {
        try {
            String s = CTX.getSharedPreferences("xhs_tasks", Context.MODE_PRIVATE).getString("data", null);
            if (s == null) return;
            JSONObject o = new JSONObject(s);
            JSONArray arr = o.optJSONArray("tasks");
            if (arr == null) return;
            for (int i = arr.length() - 1; i >= 0; i--) {
                XhsStore.Task t = XhsStore.Task.fromJson(arr.optJSONObject(i));
                if (t != null) {
                    if ("running".equals(t.status) || "pending".equals(t.status)) t.status = "stopped";
                    TASKS.add(0, t);
                }
            }
        } catch (Throwable ignored) { }
    }

    public static XhsStore.Task findTask(String id) {
        for (XhsStore.Task t : TASKS) if (id.equals(t.id)) return t;
        return null;
    }

    public static void cancel(String id) {
        AtomicBoolean c = CANCELS.get(id);
        if (c == null) { c = new AtomicBoolean(true); CANCELS.put(id, c); } else c.set(true);
        XhsStore.Task t = findTask(id);
        if (t != null && ("running".equals(t.status) || "pending".equals(t.status))) {
            t.status = "stopped";
            persist(); notifyUi();
        }
    }

    public static void retry(final XhsStore.Task t) {
        t.status = "pending";
        t.error = null;
        persist(); notifyUi();
        POOL.execute(new Runnable() { public void run() {
            try {
                if (t.noteJson != null && !t.noteJson.isEmpty()) {
                    XhsParser.Note note = noteFromJson(t.noteJson);
                    if (!note.items.isEmpty()) { downloadItemsList(t, note.items); return; }
                }
                runTaskSync(t);
            } catch (Throwable e) {
                if (isCancel(e)) t.status = "stopped";
                else { t.status = "failed"; t.error = e.getMessage() == null ? e.toString() : e.getMessage(); }
                persist(); notifyUi();
            }
        }});
    }

    private static void runTaskSync(XhsStore.Task task) throws Exception {
        task.status = "running"; notifyUi();
        String url = task.url;
        if (XhsParser.isShortUrl(url)) url = XhsNet.resolveShort(url);
        String html = XhsNet.fetchHtml(url);
        XhsParser.Note note = XhsParser.parse(html, XhsParser.extractPostId(url), url);
        if (note == null || note.items.isEmpty()) throw new Exception("解析失败");
        task.title = XhsNaming.sanitize(note.title);
        task.author = note.authorName;
        task.noteJson = noteToJson(note);
        downloadItemsList(task, note.items);
    }

    public static void remove(XhsStore.Task t) {
        CANCELS.remove(t.id);
        TASKS.remove(t);
        persist(); notifyUi();
    }

    public static void clearDone() {
        Iterator<XhsStore.Task> it = TASKS.iterator();
        while (it.hasNext()) {
            XhsStore.Task t = it.next();
            if ("done".equals(t.status) || "failed".equals(t.status)) { CANCELS.remove(t.id); it.remove(); }
        }
        persist(); notifyUi();
    }

    // ---------- Note JSON ----------
    public static String noteToJson(XhsParser.Note n) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", nz(n.noteId)); o.put("title", nz(n.title)); o.put("body", nz(n.body));
            o.put("authorName", nz(n.authorName)); o.put("authorId", nz(n.authorId));
            o.put("publishTime", nz(n.publishTime)); o.put("canonicalUrl", nz(n.canonicalUrl));
            JSONArray arr = new JSONArray();
            for (XhsParser.Media m : n.items) {
                JSONObject mo = new JSONObject();
                mo.put("kind", nz(m.kind)); mo.put("url", nz(m.url));
                mo.put("previewUrl", nz(m.previewUrl)); mo.put("liveVideoUrl", nz(m.liveVideoUrl));
                mo.put("width", m.width); mo.put("height", m.height);
                arr.put(mo);
            }
            o.put("items", arr);
            return o.toString();
        } catch (Throwable t) { return "{}"; }
    }

    public static XhsParser.Note noteFromJson(String s) {
        XhsParser.Note n = new XhsParser.Note();
        if (s == null || s.isEmpty()) return n;
        try {
            JSONObject o = new JSONObject(s);
            n.noteId = o.optString("id"); n.title = o.optString("title");
            n.body = o.optString("body");
            n.authorName = o.optString("authorName"); n.authorId = o.optString("authorId");
            n.publishTime = o.optString("publishTime"); n.canonicalUrl = o.optString("canonicalUrl");
            JSONArray arr = o.optJSONArray("items");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject mo = arr.optJSONObject(i);
                if (mo == null) continue;
                XhsParser.Media m = new XhsParser.Media();
                m.kind = mo.optString("kind", "image"); m.url = mo.optString("url");
                m.previewUrl = mo.optString("previewUrl"); m.liveVideoUrl = mo.optString("liveVideoUrl");
                m.width = mo.optInt("width"); m.height = mo.optInt("height");
                n.items.add(m);
            }
        } catch (Throwable ignored) { }
        return n;
    }

    private static XhsNaming.Note namingNote(XhsParser.Note n) {
        return new XhsNaming.Note(nz(n.title), nz(n.authorName), nz(n.authorId), nz(n.noteId), nz(n.publishTime));
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
