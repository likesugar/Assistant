package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/** 视频下载（DBdown 图1 复刻：粘贴链接 → yt-dlp 解析/下载 → 任务卡片） */
public class VideoDlActivity extends Activity {

    static class Task {
        String url, title = "解析中…", size = "", res = "";
        long expect = 0;               // 预期总字节数(视频+音频)
        volatile boolean paused = false;   // 用户请求暂停
        String pid;                        // 文件匹配token(BV号/视频id)
        String runId;                      // 每次尝试唯一的进程id(库要求不复用)
        int gen = 0;                       // 代数: 暂停/停止/重开时+1, 旧线程发现换代即退出
        int r416 = 0;                      // 416重试计数
        int percent = -1;            // -1解析中/等待 0-100下载中 100完成 -2失败
        String err; Uri saved; File out;
    }

    private static final ArrayList<Task> TASKS = new ArrayList<Task>();

    // ---------- 任务持久化(重进不丢) ----------
    static void saveTasks(android.content.Context c) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            synchronized (TASKS) {
                for (Task t : TASKS) {
                    if (t.percent != 100 && t.percent != -2) continue;
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("url", t.url); o.put("title", t.title);
                    o.put("size", t.size); o.put("res", t.res);
                    o.put("percent", t.percent); o.put("err", t.err == null ? "" : t.err);
                    o.put("saved", t.saved == null ? "" : t.saved.toString());
                    arr.put(o);
                }
            }
            c.getSharedPreferences("vdl_tasks", 0).edit().putString("list", arr.toString()).apply();
        } catch (Throwable ignored) {}
    }

    static void loadTasks(android.content.Context c) {
        try {
            String raw = c.getSharedPreferences("vdl_tasks", 0).getString("list", "");
            if (raw == null || raw.length() < 2) return;
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = arr.length() - 1; i >= 0; i--) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Task t = new Task();
                t.url = o.optString("url"); t.title = o.optString("title", "已完成");
                t.size = o.optString("size"); t.res = o.optString("res");
                t.percent = o.optInt("percent", 100);
                t.err = o.optString("err", "");
                String sv = o.optString("saved", "");
                if (sv.length() > 0) t.saved = android.net.Uri.parse(sv);
                synchronized (TASKS) { TASKS.add(0, t); }
            }
        } catch (Throwable ignored) {}
    }

    private LinearLayout list;
    private int chip = 0;           // 0全部 1进行中 2已完成
    private LinearLayout[] chips;
    private TextView[] chipTx;
    private EditText input;
    private boolean inited = false;
    private final java.util.HashMap<Task, LinearLayout> cardMap = new java.util.HashMap<Task, LinearLayout>();
    private final java.util.HashMap<Task, TextView> sizeMap = new java.util.HashMap<Task, TextView>();
    private final java.util.HashMap<Task, TextView> titleMap = new java.util.HashMap<Task, TextView>();
    private final java.util.HashMap<Task, TextView> resMap = new java.util.HashMap<Task, TextView>();
    private final java.util.HashMap<Task, Integer> shapeMap = new java.util.HashMap<Task, Integer>();
    private int lastChip = -1;
    private boolean dark;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = Theme.dark(this);
        loadTasks(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? 0xFF000000 : 0xFFF2F6FF);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        col.setPadding(pad, dp(6), pad, pad);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // 头部：视频下载 + 🗑 + ⚙
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("视频下载");
        title.setTextSize(26); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(iconBtn("🗑", new OnClickListener() { public void onClick(View v) {
            for (int i = TASKS.size() - 1; i >= 0; i--) if (TASKS.get(i).percent >= 100 || TASKS.get(i).percent == -2) TASKS.remove(i);
            saveTasks(VideoDlActivity.this);
            render();
        }}));
        head.addView(iconBtn("⚙", new OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(VideoDlActivity.this, SettingsActivity.class));
        }}));
        title.setOnClickListener(new OnClickListener() { public void onClick(View v) { showInputDialog(); }});
        col.addView(head);

        // chips
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(0, dp(18), 0, dp(10));
        hs.addView(chipsRow);
        col.addView(hs);
        String[] names = {"全部", "进行中", "已完成"};
        chips = new LinearLayout[3]; chipTx = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout c = new LinearLayout(this);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(22), dp(8), dp(22), dp(8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.rightMargin = dp(10);
            TextView t = new TextView(this);
            t.setText(names[i]); t.setTextSize(14);
            c.addView(t);
            chipsRow.addView(c, clp);
            chips[i] = c; chipTx[i] = t;
            c.setOnClickListener(new OnClickListener() { public void onClick(View v) { chip = idx; styleChips(); render(); }});
        }
        styleChips();

        // 任务列表
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        setContentView(root);
        Immersive.hide(this);
        // 入口交互与资源嗅探一致: 进入即弹输入窗
        String autoUrl = getIntent() != null ? getIntent().getStringExtra("url") : null;
        if (autoUrl != null) autoUrl = autoUrl.trim();
        if (autoUrl != null && autoUrl.length() > 0) { submit(autoUrl); } // 从首页弹窗带链接进来直接开下
        // 不再自动弹输入窗(输入统一走首页弹窗)
        render();
    }

    private TextView iconBtn(String s, OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(20);
        t.setPadding(dp(12), dp(4), dp(4), dp(4));
        t.setOnClickListener(l);
        return t;
    }

    private void styleChips() {
        for (int i = 0; i < 3; i++) {
            boolean sel = i == chip;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            g.setColor(sel ? 0xFF4A4458 : Color.TRANSPARENT);
            if (!sel) g.setStroke(dp(1), 0xFF3A4252);
            chips[i].setBackground(g);
            chipTx[i].setTextColor(sel ? Color.WHITE : 0xFFAEB6C2);
        }
    }

    private void showInputDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pb = new GradientDrawable();
        pb.setCornerRadius(dp(24)); pb.setColor(dark ? (Theme.oled(this) ? 0xFF000000 : 0xFF181E2A) : 0xFFFFFFFF);
        if (dark && Theme.oled(this)) pb.setStroke(dp(1), 0xFF222222);
        if (!dark) pb.setStroke(dp(1), 0xFFE4EAF5);
        box.setBackground(pb);
        box.setMinimumWidth(dp(300));
        int p2 = dp(6);
        box.setPadding(dp(18), dp(14), p2, dp(14));
        TextView link = new TextView(this);
        link.setText("🔗"); link.setTextSize(16);
        box.addView(link);
        input = new EditText(this);
        input.setHint("粘贴视频链接");
        input.setBackground(null);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(dark ? 0xFFE8ECF4 : 0xFF1F2329);
        input.setHintTextColor(dark ? 0xFF6B7684 : 0xFF9AA3AE);
        input.setPadding(dp(10), 0, 0, 0);
        box.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        final EditText inputF = input;
        TextView go = new TextView(this);
        go.setText("→"); go.setTextSize(20);
        go.setTextColor(dark ? 0xFFB4C5FF : 0xFF315CDE);
        go.setPadding(dp(12), 0, dp(6), 0);
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this).create();
        go.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            String u = input.getText().toString().trim();
            if (u.length() > 0) { dlg.dismiss(); submit(u); }
        }});
        box.addView(go);
        dlg.setView(box, dp(18), dp(24), dp(18), dp(6));
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dlg.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        input.requestFocus();
        // 剪贴板预填: 立即一次 + 窗口拿到焦点后再补填(否则Android10+读到空)
        final Runnable[] fillRef = new Runnable[1];
        fillRef[0] = new Runnable() { public void run() {
            try {
                if (inputF.getText().length() > 0) return;
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemAt(0) != null && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                    String cu = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(cu);
                    if (m.find()) {
                        String cu2 = m.group();
                        cu2 = cu2.replaceAll("[\\u4e00-\\u9fff，。！？、：；【】（）\\u3000-\\u303f\\uff00-\\uffef]+$", "");
                        inputF.setText(cu2);
                    }
                }
            } catch (Throwable ignored) {}
        }};
        fillRef[0].run();
        box.postDelayed(fillRef[0], 400);
        box.postDelayed(fillRef[0], 1000);
    }

    private void submit() { submit(null); }

    private void submit(String urlIn) {
        String u = urlIn != null ? urlIn : input.getText().toString().trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(u);
        if (m.find()) u = m.group(); else { Toast.makeText(this, "请输入有效链接", Toast.LENGTH_SHORT).show(); return; }
        for (Task t : TASKS) if (t.url.equals(u) && t.percent < 100 && t.percent != -2) { Toast.makeText(this, "该链接已在队列", Toast.LENGTH_SHORT).show(); return; }
        if (urlIn == null) input.setText("");
        final Task tk = new Task(); tk.url = u;
        TASKS.add(0, tk);
        render();
        // B站 URL 规范化: m.bilibili.com -> www, 去查询参数只留 BV 号
        try {
            java.util.regex.Matcher bm = java.util.regex.Pattern.compile("bilibili[.]com/video/(BV[0-9A-Za-z]{8,12})").matcher(u);
            if (bm.find()) u = "https://www.bilibili.com/video/" + bm.group(1) + "/";
        } catch (Throwable ignored) {}
        // 抖音URL归一化: 任意形态 -> www.douyin.com/video/<id>
        try {
            java.util.regex.Matcher dm = java.util.regex.Pattern.compile("douyin\\.com/(?:share/)?(?:note|video)/(\\d+)").matcher(u);
            if (dm.find()) u = "https://www.douyin.com/video/" + dm.group(1);
        } catch (Throwable ignored) {}
        // 短链(b23.tv / v.douyin.com)先原生跟随302
        final String fu0 = u;
        if (fu0.contains("b23.tv") || fu0.contains("v.douyin.com")) {
            new Thread(new Runnable() { public void run() {
                String real = fu0;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(fu0).openConnection();
                    c.setInstanceFollowRedirects(false);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                    int code = c.getResponseCode();
                    String loc = c.getHeaderField("Location");
                    int hops = 0;
                    while (loc != null && hops < 5) {
                        real = loc.startsWith("http") ? loc : new java.net.URL(new java.net.URL(real), loc).toString();
                        java.net.HttpURLConnection c2 = (java.net.HttpURLConnection) new java.net.URL(real).openConnection();
                        c2.setInstanceFollowRedirects(false);
                        c2.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Chrome/120 Mobile");
                        code = c2.getResponseCode();
                        loc = c2.getHeaderField("Location");
                        hops++;
                    }
                } catch (Throwable ignored) {}
                final String fu = real;
                runUi(new Runnable() { public void run() { tk.url = fu; runTask(tk); }});
            }}).start();
            return;
        }
        runTask(tk);
    }

    private void runTask(final Task tk) {
        new Thread(new Runnable() { public void run() {
            try {
                // 归一化放在这里: 短链302解析后再进 runTask 时也能生效
                try {
                    java.util.regex.Matcher dm = java.util.regex.Pattern.compile("douyin\\.com/(?:share/)?(?:note|video)/(\\d+)").matcher(tk.url);
                    if (dm.find()) tk.url = "https://www.douyin.com/video/" + dm.group(1);
                } catch (Throwable ignored) {}
                try {
                    java.util.regex.Matcher bt = java.util.regex.Pattern.compile("(BV[0-9A-Za-z]{8,12})").matcher(tk.url);
                    if (bt.find()) tk.pid = bt.group(1);
                    else {
                        java.util.regex.Matcher dt = java.util.regex.Pattern.compile("video/(\\d{10,25})").matcher(tk.url);
                        if (dt.find()) tk.pid = dt.group(1);
                    }
                } catch (Throwable ignored) {}
                if (tk.pid == null) tk.pid = "t" + System.currentTimeMillis();
                final int myGen = ++tk.gen;
                tk.r416 = 0;
                tk.runId = tk.pid + "#" + System.currentTimeMillis();
                ensureEngine();
                File cache = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                File out = new File(cache, "vdl_%(id)s.%(ext)s");
                final String ref = tk.url.contains("douyin.com") ? "https://www.douyin.com/" : "https://www.bilibili.com/";
                // 策略轮询：不同 UA/Cookie 组合，谁成用谁
                String[][] strategies = {
                    // {ua, referer, cookie开关}
                    {"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36", "https://www.bilibili.com/", "1"},
                    {"Mozilla/5.0 (Linux; Android 13; M2102K1C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36", "https://www.bilibili.com/", "1"},
                    {"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36", "https://www.bilibili.com/", "1"},
                    {"Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36", "https://live.douyin.com/", "1"},
                };
                Throwable last = null;
                for (int si = 0; si < strategies.length; si++) {
                    try {
                        final String[] st = strategies[si];
                        // 1) 元数据
                        try {
                            com.yausername.youtubedl_android.YoutubeDLRequest meta =
                                    new com.yausername.youtubedl_android.YoutubeDLRequest(tk.url);
                            meta.addOption("--dump-json");
                            meta.addOption("--no-playlist");
                            meta.addOption("--no-update");
                            if (si == 0) meta.addOption("--verbose");
                            meta.addOption("--user-agent", st[0]);
                            meta.addOption("-4");
                            meta.addOption("--add-headers", "Referer: " + ref);
                            if (st[2].equals("1")) meta.addOption("--cookies", cookies().getAbsolutePath());
                            com.yausername.youtubedl_android.YoutubeDLResponse mr =
                                    com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(meta, null);
                            String[] ls = mr.getOut().trim().split("\n");
                            JSONObject j = new JSONObject(ls[ls.length - 1]);
                            tk.title = j.optString("title", tk.title);
                            try {
                                org.json.JSONArray rf = j.optJSONArray("requested_formats");
                                if (rf != null) for (int fi = 0; fi < rf.length(); fi++) {
                                    JSONObject fo = rf.getJSONObject(fi);
                                    long fs = fo.optLong("filesize");
                                    if (fs <= 0) fs = fo.optLong("filesize_approx");
                                    tk.expect += fs;
                                }
                            } catch (Throwable ignored) {}
                            if (j.optLong("filesize") > 0) tk.size = fmtMB(j.optLong("filesize"));
                            else if (j.optLong("filesize_approx") > 0) tk.size = "~" + fmtMB(j.optLong("filesize_approx"));
                            int w = j.optInt("width"), h = j.optInt("height"), fps = j.optInt("fps", 0);
                            if (h > 0) tk.res = w + " × " + h + (fps > 0 ? " · " + fps + " fps" : "");
                            tk.percent = 0;
                            runUi(new Runnable() { public void run() { render(); }});
                        } catch (Throwable e) {
                            tk.percent = 0;  // 元数据失败继续下
                        }
                        // 2) 下载
                        boolean isBili = tk.url.contains("bilibili.com");
                        final java.util.Set<String> before = new java.util.HashSet<String>();
                        for (File f0 : cache.listFiles()) if (f0.getName().startsWith("vdl_")) before.add(f0.getName());
                        com.yausername.youtubedl_android.YoutubeDLRequest req =
                                new com.yausername.youtubedl_android.YoutubeDLRequest(tk.url);
                        if (isBili) {
                            // B站: 两段下载(视频+音频), 用 ffmpeg-kit 合并, 免 35M ffmpeg CLI
                            req.addOption("-f", "bv*[ext=mp4]/bv*");
                        } else {
                            // 抖音等: 单流取最高档
                            req.addOption("-f", "b");
                        }
                        req.addOption("--no-update");
                        req.addOption("--user-agent", st[0]);
                        req.addOption("-4");
                        req.addOption("--add-headers", "Referer: " + ref);
                            if (st[2].equals("1")) req.addOption("--cookies", cookies().getAbsolutePath());

                    if (tk.gen != myGen) return;
                        req.addOption("-o", out.getAbsolutePath());
                        req.addOption("--restrict-filenames");
                        req.addOption("--no-playlist"); req.addOption("--no-mtime");
                        // 进度监视：轮询 .part 文件大小
                        new Thread(new Runnable() { public void run() {
                            while (tk.percent >= 0 && tk.percent < 100) {
                                try {
                                    long mx = 0;
                                    File cache2 = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                                    for (File f2 : cache2.listFiles()) {
                                        boolean mine = tk.pid != null ? f2.getName().contains(tk.pid) : !before.contains(f2.getName());
                                        if (f2.getName().startsWith("vdl_") && mine) mx += f2.length();
                                    }
                                    if (mx > 0) {
                                        tk.size = fmtMB(mx);
                                        if (tk.expect > 0) {
                                            int pc = (int)(1 + 98L * mx / tk.expect);
                                            if (pc > 99) pc = 99;
                                            if (pc > tk.percent) tk.percent = pc;
                                        } else if (tk.percent < 1) tk.percent = 1;
                                        runUi(new Runnable() { public void run() { render(); }});
                                    }
                                } catch (Throwable ignored) {}
                                try { Thread.sleep(500); } catch (Throwable e) { return; }
                            }
                        }}).start();
                        com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(req, tk.pid + "#" + java.util.UUID.randomUUID(), false, null);
                        File done = null;
                        if (isBili) {
                            File vfile = null;
                            for (File f : cache.listFiles())
                                if (f.getName().startsWith("vdl_") && tk.pid != null && f.getName().contains(tk.pid)
                                    && (f.getName().endsWith(".mp4") || f.getName().endsWith(".mkv"))
                                    && !f.getName().endsWith(".part") && f.length() > 1024)
                                    if (vfile == null || f.lastModified() > vfile.lastModified()) vfile = f;
                            if (vfile == null) throw new Exception("B站视频流下载失败");
                            // 音频段
                            com.yausername.youtubedl_android.YoutubeDLRequest ra =
                                    new com.yausername.youtubedl_android.YoutubeDLRequest(tk.url);
                            ra.addOption("-f", "ba[ext=m4a]/ba/b");
                            ra.addOption("--no-update");
                            ra.addOption("--user-agent", st[0]);
                            ra.addOption("-4");
                            ra.addOption("--add-headers", "Referer: " + ref);
                            if (st[2].equals("1")) ra.addOption("--cookies", cookies().getAbsolutePath());
                            ra.addOption("-o", out.getAbsolutePath());
                            ra.addOption("--restrict-filenames");
                            ra.addOption("--no-playlist"); ra.addOption("--no-mtime");
                            java.util.Set<String> before2 = new java.util.HashSet<String>();
                            for (File f0 : cache.listFiles()) if (f0.getName().startsWith("vdl_")) before2.add(f0.getName());
                            before2.add(vfile.getName());
                            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(ra, tk.pid + "#" + java.util.UUID.randomUUID(), false, null);
                            File afile = null;
                            for (File f : cache.listFiles())
                                if (f.getName().startsWith("vdl_") && tk.pid != null && f.getName().contains(tk.pid)
                                    && (f.getName().endsWith(".m4a") || f.getName().endsWith(".aac"))
                                    && !f.getName().endsWith(".part") && f.length() > 1024)
                                    if (afile == null || f.lastModified() > afile.lastModified()) afile = f;
                            if (afile != null) {
                                String m = new File(cache, "vdl_m_" + System.currentTimeMillis() + ".mp4").getAbsolutePath();
                                com.arthenica.ffmpegkit.FFmpegKit.execute("-y -i \"" + vfile.getAbsolutePath() + "\" -i \"" + afile.getAbsolutePath() + "\" -c copy -avoid_negative_ts make_zero -movflags +faststart \"" + m + "\"");
                                File mf = new File(m);
                                if (mf.length() > 1024) done = mf;
                            }
                            if (done == null) done = vfile;  // 合并失败退回纯视频
                            for (File f : cache.listFiles())
                                if (f.getName().startsWith("vdl_") && tk.pid != null && f.getName().contains(tk.pid)
                                    && f != done && !f.getName().endsWith(".part") && f != vfile && f != afile) f.delete();
                        } else {
                            for (File f : cache.listFiles()) {
                                if (f.getName().startsWith("vdl_")) {
                                    if (done == null || f.lastModified() > done.lastModified()) done = f;
                                }
                            }
                        }
                        if (done == null || done.length() < 1024) throw new Exception("未生成视频文件");
                        tk.out = done;
                        tk.saved = store(done, safeName(tk.title));
                        saveTasks(VideoDlActivity.this);
                        tk.percent = 100;
                        runUi(new Runnable() { public void run() { render(); }});
                        return;  // 成功收工
                    } catch (Throwable e) {
                        String em = e.getMessage() == null ? "" : e.getMessage();
                        if (em.contains("416") && tk.gen == myGen) {
                            tk.r416++;
                            if (tk.r416 <= 2) {
                            // 断点分片与远端Range不匹配: 清.part重试本策略一次
                                try {
                                    for (File f : cache.listFiles()) if (f.getName().endsWith(".part")) f.delete();
                                } catch (Throwable ignored) {}
                                si--; continue;
                            }
                        }
                        if (tk.gen != myGen) return;
                        if (tk.paused) {
                            tk.percent = -3;   // 用户暂停
                            runUi(new Runnable() { public void run() { render(); }});
                            return;
                        }
                        last = e;
                        try {
                            java.io.File dir = getExternalFilesDir(null) != null ? getExternalFilesDir(null).getParentFile() : getFilesDir();
                            java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, "网页诊断.txt"), true);
                            fw.write("\n==== VDL try#" + si + " " + new java.util.Date() + " url=" + tk.url + " ====\n" + e.getMessage() + "\n");
                            fw.close();
                        } catch (Throwable ignored) {}
                    }
                }
                throw last != null ? last : new Exception("全部策略失败");
            } catch (Throwable e) {
                tk.percent = -2;
                tk.err = e.getMessage() == null ? e.toString() : e.getMessage();
                saveTasks(VideoDlActivity.this);
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    String full = sw.toString();
                    for (String fn : new String[]{"网页诊断.txt", "fc2_debug.txt"}) {
                        java.io.File dir = getExternalFilesDir(null) != null ? getExternalFilesDir(null).getParentFile() : getFilesDir();
                        java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, fn), true);
                        fw.write("\n==== VDL " + new java.util.Date() + " url=" + tk.url + " ====\n" + full + "\n");
                        fw.close();
                    }
                } catch (Throwable ignored) {}
                runUi(new Runnable() { public void run() { render(); }});
            }
        }}).start();
    }

    private void vdump(String st) {
        try {
            java.io.File dir = getExternalFilesDir(null) != null ? getExternalFilesDir(null).getParentFile() : getFilesDir();
            java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, "fc2_debug.txt"), true);
            fw.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                    + " YT: " + st + "\n----------------\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    private void ensureEngine() throws Exception {
        synchronized (VideoDlActivity.class) {
            if (!inited) {
                System.setProperty("java.net.preferIPv4Stack", "true");
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(getApplicationContext());
                inited = true;
                // 自定义 yt-dlp 覆盖引擎内置版(设置页更新后)
                try {
                    java.io.File cust = new java.io.File(getFilesDir(), "ytdlp_custom/yt-dlp");
                    if (cust.exists() && cust.length() > 100000) {
                        java.io.File target = new java.io.File(getFilesDir(), "ytdlp/yt-dlp");
                        if (target.exists() && target.lastModified() < cust.lastModified()) {
                            java.io.FileInputStream in2 = new java.io.FileInputStream(cust);
                            java.io.FileOutputStream out2 = new java.io.FileOutputStream(target);
                            byte[] b2 = new byte[8192]; int n2;
                            while ((n2 = in2.read(b2)) > 0) out2.write(b2, 0, n2);
                            in2.close(); out2.close();
                            target.setExecutable(true);
                        }
                    }
                } catch (Throwable ignored) {}
                // EJS 运行时: quickjs 放 yt-dlp 同目录(EJS wiki 支持同目录发现)
                try {
                    java.io.File qsrc = new java.io.File(getApplicationInfo().nativeLibraryDir, "libqjs.so");
                    java.io.File qdst = new java.io.File(getFilesDir(), "ytdlp/qjs");
                    if (qsrc.exists() && (!qdst.exists() || qdst.length() != qsrc.length())) {
                        java.io.FileInputStream qi = new java.io.FileInputStream(qsrc);
                        java.io.FileOutputStream qo = new java.io.FileOutputStream(qdst);
                        byte[] qb = new byte[8192]; int qn;
                        while ((qn = qi.read(qb)) > 0) qo.write(qb, 0, qn);
                        qi.close(); qo.close();
                        qdst.setExecutable(true);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private File cookies() throws Exception {
        android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
        File f = new File(getFilesDir(), "yt_cookies.txt");
        java.io.PrintWriter pw = new java.io.PrintWriter(f, "UTF-8");
        pw.println("# Netscape HTTP Cookie File");
        // youtube 域
        String yraw = cm.getCookie("https://www.youtube.com");
        if (yraw != null) for (String p : yraw.split(";")) {
            String[] kv = p.trim().split("=", 2);
            if (kv.length == 2) pw.println(".youtube.com\tTRUE\t/\tTRUE\t0\t" + kv[0] + "\t" + kv[1]);
        }
        // bilibili 域(含 buvid 预热)
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://www.bilibili.com/").openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.getInputStream();
            for (String sc : c.getHeaderFields().getOrDefault("set-cookie", java.util.Collections.<String>emptyList())) {
                String kv0 = sc.split(";", 2)[0];
                String[] kv = kv0.split("=", 2);
                if (kv.length == 2 && (kv[0].startsWith("buvid") || kv[0].equals("b_nut")))
                    pw.println(".bilibili.com\tTRUE\t/\tTRUE\t0\t" + kv[0] + "\t" + kv[1]);
            }
        } catch (Throwable ignored) {}
        String draw = cm.getCookie("https://www.douyin.com");
        if (draw != null) for (String p : draw.split(";")) {
            String[] kv = p.trim().split("=", 2);
            if (kv.length == 2) pw.println(".douyin.com\tTRUE\t/\tTRUE\t0\t" + kv[0] + "\t" + kv[1]);
        }
        String braw = cm.getCookie("https://www.bilibili.com");
        if (braw != null) for (String p : braw.split(";")) {
            String[] kv = p.trim().split("=", 2);
            if (kv.length == 2) pw.println(".bilibili.com\tTRUE\t/\tTRUE\t0\t" + kv[0] + "\t" + kv[1]);
        }
        pw.close();
        return f;
    }

    private void runUi(final Runnable r) { runOnUiThread(r); }

    // ---------- 渲染 ----------
    private void killYtProcesses(String token) {
        if (token == null) return;
        try {
            java.io.File[] dirs = new java.io.File("/proc").listFiles();
            if (dirs == null) return;
            for (java.io.File d : dirs) {
                String n = d.getName();
                if (!n.matches("\\d+")) continue;
                try {
                    byte[] b = new byte[4096];
                    java.io.InputStream in = new java.io.FileInputStream(new java.io.File(d, "cmdline"));
                    int r = in.read(b); in.close();
                    if (r <= 0) continue;
                    String cmd = new String(b, 0, r);
                    if (cmd.contains(token)) {
                        int pid = Integer.parseInt(n);
                        android.os.Process.killProcess(pid);
                        try { Thread.sleep(150); } catch (Throwable ignored) {}
                        Runtime.getRuntime().exec(new String[]{"kill", "-9", String.valueOf(pid)});
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private int shapeOf(Task tk) {
        if (tk.percent == -3) return 3;
        if (tk.percent == -2) return 4;
        if (tk.percent == 100) return 2;
        if (tk.percent >= 0) return 1;
        return 0;
    }

    private void render() {
        if (list == null) return;
        if (lastChip != chip) { cardMap.clear(); sizeMap.clear(); titleMap.clear(); resMap.clear(); shapeMap.clear(); lastChip = chip; }
        for (final Task tk : TASKS) {
            boolean busy = tk.percent >= 0 && tk.percent < 100;
            boolean done = tk.percent == 100;
            if (chip == 1 && !busy) continue;
            if (chip == 2 && !done) continue;
            Integer sh = shapeMap.get(tk);
            LinearLayout card = cardMap.get(tk);
            if (card == null || sh == null || sh != shapeOf(tk)) {
                if (card != null && card.getParent() != null) ((android.view.ViewGroup) card.getParent()).removeView(card);
                card = buildCard(tk);
                cardMap.put(tk, card);
                shapeMap.put(tk, shapeOf(tk));
            }
            TextView sz = sizeMap.get(tk);
            if (sz != null) sz.setText(tk.percent >= 0 && tk.percent < 100 ? tk.percent + "%" : tk.size);
            TextView tt = titleMap.get(tk);
            if (tt != null) tt.setText(tk.percent == -2 ? "失败: " + tk.err : tk.title);
            TextView rs = resMap.get(tk);
            if (rs != null) rs.setText(tk.percent == 100 ? (tk.res.length() > 0 ? tk.res : "已完成") : (tk.percent == -1 ? "解析中…" : (tk.percent == -2 ? "长按可删除" : (tk.percent == -3 ? "已暂停" : "下载中…"))));
            int target = chipFilteredIndex(tk);
            if (card.getParent() != list) {
                if (card.getParent() != null) ((android.view.ViewGroup) card.getParent()).removeView(card);
                list.addView(card, Math.min(target, list.getChildCount()));
            } else if (list.indexOfChild(card) != target) {
                list.removeView(card);
                list.addView(card, Math.min(target, list.getChildCount()));
            }
        }
        java.util.Iterator<Task> it = cardMap.keySet().iterator();
        while (it.hasNext()) {
            Task t = it.next();
            boolean vis = false;
            for (Task tk : TASKS) if (tk == t) { vis = true; break; }
            boolean busy = t.percent >= 0 && t.percent < 100;
            if (chip == 1 && !busy) vis = false;
            if (chip == 2 && t.percent != 100) vis = false;
            if (!vis) {
                LinearLayout cv = cardMap.get(t);
                if (cv != null && cv.getParent() != null) ((android.view.ViewGroup) cv.getParent()).removeView(cv);
                it.remove(); sizeMap.remove(t); titleMap.remove(t); resMap.remove(t); shapeMap.remove(t);
            }
        }
        refreshEmptyHint();
    }

    private int chipFilteredIndex(Task tk) {
        int idx = 0;
        for (Task t : TASKS) {
            if (t == tk) return idx;
            boolean busy = t.percent >= 0 && t.percent < 100;
            boolean ok = !(chip == 1 && !busy) && !(chip == 2 && t.percent != 100);
            if (ok) idx++;
        }
        return idx;
    }

    private void refreshEmptyHint() {
        int n = 0;
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).getId() != 0xE11) n++;
        if (n == 0) {
            if (list.findViewById(0xE11) == null) {
                TextView empty = new TextView(this);
                empty.setId(0xE11);
                empty.setText("暂无任务，点击标题粘贴链接");
                empty.setTextColor(0xFF6B7684); empty.setTextSize(14);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(60), 0, 0);
                list.addView(empty);
            }
        } else {
            View oldEmpty = list.findViewById(0xE11);
            if (oldEmpty != null) list.removeView(oldEmpty);
        }
    }

    private LinearLayout buildCard(final Task tk) {
        int dp2 = dp(12);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(24));
        g.setColor(Theme.dark(this) && Theme.oled(this) ? 0xFF000000 : 0xFF171C27);
        if (Theme.dark(this) && Theme.oled(this)) g.setStroke(dp(1), 0xFF222222);
        card.setBackground(g);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new TextView(this);
        badge.setText(platformEmoji(tk.url)); badge.setTextSize(20);
        r1.addView(badge);
        r1.addView(new TextView(this), new LinearLayout.LayoutParams(0, 0, 1f));
        TextView size = new TextView(this);
        size.setText(tk.percent >= 0 && tk.percent < 100 ? tk.percent + "%" : tk.size);
        size.setTextSize(14); size.setTextColor(0xFFAEB6C2);
        r1.addView(size);
        sizeMap.put(tk, size);
        TextView more = new TextView(this);
        more.setText("⋮"); more.setTextSize(18);
        more.setTextColor(0xFF8A94A6);
        more.setPadding(dp(12), 0, 0, 0);
        more.setOnClickListener(new OnClickListener() { public void onClick(View v) { taskMenu(tk); }});
        r1.addView(more);
        card.addView(r1, new LinearLayout.LayoutParams(-1, -2));
        TextView tTitle = new TextView(this);
        tTitle.setText(tk.percent == -2 ? "失败: " + tk.err : tk.title);
        tTitle.setTextSize(16); tTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tTitle.setTextColor(tk.percent == -2 ? 0xFFFF7B8A : (dark ? Color.WHITE : Color.WHITE));
        tTitle.setMaxLines(2);
        tTitle.setPadding(0, dp2(10), 0, 0);
        card.addView(tTitle, new LinearLayout.LayoutParams(-1, -2));
        titleMap.put(tk, tTitle);
        if (tk.percent >= 0 && tk.percent < 100) {
            LinearLayout r4 = new LinearLayout(this);
            r4.setGravity(Gravity.CENTER_VERTICAL);
            r4.setPadding(0, dp(6), 0, dp(2));
            TextView pauseBtn = new TextView(this);
            pauseBtn.setText("⏸ 暂停"); pauseBtn.setTextSize(15);
            pauseBtn.setTextColor(0xFFFFB74D);
            pauseBtn.setGravity(Gravity.CENTER);
            GradientDrawable pb0 = new GradientDrawable();
            pb0.setCornerRadius(dp(14)); pb0.setColor(Theme.dark(this) && Theme.oled(this) ? 0xFF111111 : 0xFF232A38);
            pauseBtn.setBackground(pb0);
            pauseBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            pauseBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                tk.paused = true;
                try { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(tk.pid); } catch (Throwable ignored) {}
                try { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(tk.pid + "_a"); } catch (Throwable ignored) {}
                try {
                    String tok = tk.pid;
                    if (tok != null) Runtime.getRuntime().exec(new String[]{"pkill", "-f", tok});
                } catch (Throwable ignored) {}
                killYtProcesses(tk.pid);
            }});
            TextView stopBtn = new TextView(this);
            stopBtn.setText("⏹ 停止"); stopBtn.setTextSize(15);
            stopBtn.setTextColor(0xFFFF7B8A);
            stopBtn.setGravity(Gravity.CENTER);
            GradientDrawable sb0 = new GradientDrawable();
            sb0.setCornerRadius(dp(14)); sb0.setColor(Theme.dark(this) && Theme.oled(this) ? 0xFF111111 : 0xFF232A38);
            stopBtn.setBackground(sb0);
            stopBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            stopBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                tk.paused = true;
                try { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(tk.pid); } catch (Throwable ignored) {}
                try { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(tk.pid + "_a"); } catch (Throwable ignored) {}
                try {
                    String tok = tk.pid;
                    if (tok != null) Runtime.getRuntime().exec(new String[]{"pkill", "-f", tok});
                } catch (Throwable ignored) {}
                killYtProcesses(tk.pid);
                tk.gen++;
                tk.percent = -2;
                tk.err = "已手动停止";
                saveTasks(VideoDlActivity.this);
                runUi(new Runnable() { public void run() { render(); }});
            }});
            r4.addView(pauseBtn, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
            slp.leftMargin = dp(10);
            r4.addView(stopBtn, slp);
            card.addView(r4, new LinearLayout.LayoutParams(-1, -2));
        }
        if (tk.percent == -3) {
            LinearLayout r4 = new LinearLayout(this);
            r4.setGravity(Gravity.CENTER_VERTICAL);
            TextView resumeBtn = new TextView(this);
            resumeBtn.setText("▶ 继续下载"); resumeBtn.setTextSize(15);
            resumeBtn.setTextColor(0xFF9CCC65);
            resumeBtn.setGravity(Gravity.CENTER);
            GradientDrawable rb0 = new GradientDrawable();
            rb0.setCornerRadius(dp(14)); rb0.setColor(Theme.dark(this) && Theme.oled(this) ? 0xFF111111 : 0xFF232A38);
            resumeBtn.setBackground(rb0);
            resumeBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            resumeBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                tk.percent = 0; tk.paused = false;
                tk.gen++;
                runTask(tk);
            }});
            r4.addView(resumeBtn, new LinearLayout.LayoutParams(-1, -2));
            card.addView(r4, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout r3 = new LinearLayout(this);
        r3.setGravity(Gravity.CENTER_VERTICAL);
        r3.setPadding(0, dp2(12), 0, 0);
        TextView res = new TextView(this);
        res.setText(tk.percent == 100 ? (tk.res.length() > 0 ? tk.res : "已完成") : (tk.percent == -1 ? "解析中…" : (tk.percent == -2 ? "长按可删除" : (tk.percent == -3 ? "已暂停" : "下载中…"))));
        res.setTextSize(15); res.setTextColor(0xFFAEB6C2);
        r3.addView(res, new LinearLayout.LayoutParams(0, -2, 1f));
        resMap.put(tk, res);
        if (tk.percent == 100) {
            TextView play = new TextView(this);
            play.setText("▷"); play.setTextSize(22); play.setTextColor(0xFFE8ECF4);
            play.setPadding(dp2(14), 0, dp2(14), 0);
            play.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                try {
                    Intent i = new Intent(VideoDlActivity.this, NativePlayerActivity.class);
                    i.putExtra("url", tk.saved.toString());
                    i.putExtra("title", tk.title);
                    i.putExtra("kernel", "native");
                    startActivity(i);
                } catch (Throwable e) { Toast.makeText(VideoDlActivity.this, "打不开", Toast.LENGTH_SHORT).show(); }
            }});
            r3.addView(play);
            TextView share = new TextView(this);
            share.setText("▶"); share.setTextSize(20); share.setTextColor(0xFFE8ECF4);
            share.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                try {
                    java.io.File vf = new java.io.File(tk.saved.getPath());
                    android.net.Uri cu = androidx.core.content.FileProvider.getUriForFile(VideoDlActivity.this, getPackageName() + ".fp", vf);
                    Intent sh = new Intent(Intent.ACTION_VIEW);
                    sh.setDataAndType(cu, "video/mp4");
                    sh.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(sh);
                } catch (Throwable e) { Toast.makeText(VideoDlActivity.this, "没有可用的播放器", Toast.LENGTH_SHORT).show(); }
            }});
            r3.addView(share);
        }
        card.addView(r3, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void taskMenu(final Task tk) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        java.util.ArrayList<String> its = new java.util.ArrayList<String>();
        if (tk.percent == 100) its.add("播放");
        if (tk.percent == -2 || tk.percent == 100) its.add("删除");
        if (tk.percent == -2) its.add("重试");
        its.add("复制链接");
        b.setItems(its.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) {
                String t = its.get(w);
                if (t.equals("删除")) { TASKS.remove(tk); render(); }
                else if (t.equals("重试")) { tk.percent = -1; tk.err = null; render(); runTask(tk); }
                else if (t.equals("播放")) {
                    Intent i = new Intent(VideoDlActivity.this, NativePlayerActivity.class);
                    i.putExtra("url", tk.saved.toString()); i.putExtra("title", tk.title); i.putExtra("kernel", "native");
                    startActivity(i);
                } else if (t.equals("复制链接")) {
                    try { ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("u", tk.url)); } catch (Throwable ignored) {}
                }
            }
        }).show();
    }

    private String platformEmoji(String u) {
        if (u == null) return "🌐";
        String l = u.toLowerCase();
        if (l.contains("douyin") || l.contains("tiktok") || l.contains("amemv")) return "🎵";
        if (l.contains("bilibili") || l.contains("b23.tv")) return "📺";
        if (l.contains("youtube") || l.contains("youtu.be")) return "▶";
        return "🌐";
    }

    private String fmtMB(long b) {
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }

    private String safeName(String t) {
        String n = t == null || t.trim().isEmpty() ? "视频" : t.trim();
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private Uri store(File f, String name) throws Exception {
        File dir = new File(getExternalFilesDir(null), "视频下载");
        if (!dir.exists()) dir.mkdirs();
        String fn = name.endsWith(".mp4") ? name : name + ".mp4";
        File dst = new File(dir, fn);
        int dup = 1;
        while (dst.exists()) dst = new File(dir, name + "(" + (dup++) + ").mp4");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(dst);
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        byte[] b = new byte[65536]; int r;
        while ((r = fis.read(b)) > 0) fos.write(b, 0, r);
        fis.close(); fos.close();
        f.delete();
        return Uri.fromFile(dst);
    }

    private int dp2(int v) { return dp(v); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
