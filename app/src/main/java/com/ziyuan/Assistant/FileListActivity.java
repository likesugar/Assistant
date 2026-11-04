package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

/** 文件列表页（参考媒体管理器的"打开所在目录"）：面包屑 + 首帧缩略图 + 多选/搜索/分享/删除 */
public class FileListActivity extends Activity {

    private File dir;
    private String selectPath;          // 需要定位/选中的文件
    private boolean selectMode;         // "选取"入口: 预选中并进入多选态
    private boolean pendingSelect = true;

    private LinearLayout rowBox;
    private LinearLayout crumbBox;
    private HorizontalScrollView crumbScroll;
    private ScrollView sv;
    private EditText searchEt;
    private String query = "";
    private int sortBy = 0;             // 0名称 1日期 2大小

    private final ArrayList<File> entries = new ArrayList<File>();
    private final HashSet<String> selected = new HashSet<String>();

    private static final java.util.HashMap<ImageView, String> THUMB_REQ = new java.util.HashMap<ImageView, String>();

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String fmtSize(long b) {
        if (b >= 1073741824L) return String.format(Locale.US, "%.2fGB", b / 1073741824.0);
        if (b >= 1048576) return String.format(Locale.US, "%.2fMB", b / 1048576.0);
        if (b >= 1024) return String.format(Locale.US, "%.0fKB", b / 1024.0);
        return b + "B";
    }

    private static String fmtDate(long ms) {
        try { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(ms)); }
        catch (Throwable e) { return ""; }
    }

    /** 本地文件 → 可外发的 content:// Uri（FileProvider），失败回退 file:// */
    static Uri uriFor(Context c, File f) {
        try { return androidx.core.content.FileProvider.getUriForFile(c, "com.ziyuan.Assistant.fp", f); }
        catch (Throwable e) { return Uri.fromFile(f); }
    }

    static String mimeOf(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".mkv") || n.endsWith(".ts")
                || n.endsWith(".m3u8") || n.endsWith(".avi") || n.endsWith(".flv") || n.endsWith(".webm")) return "video/*";
        if (n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".flac")
                || n.endsWith(".wav") || n.endsWith(".ogg")) return "audio/*";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif")
                || n.endsWith(".webp") || n.endsWith(".bmp")) return "image/*";
        if (n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".json") || n.endsWith(".xml")
                || n.endsWith(".srt") || n.endsWith(".ass")) return "text/plain";
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip") || n.endsWith(".7z") || n.endsWith(".rar")) return "application/zip";
        return "*/*";
    }

    private static boolean isThumbable(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".mkv") || n.endsWith(".ts")
                || n.endsWith(".avi") || n.endsWith(".flv") || n.endsWith(".webm")
                || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".gif") || n.endsWith(".webp");
    }

    private static String glyphFor(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        if (n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".flac")
                || n.endsWith(".wav") || n.endsWith(".ogg")) return "\u266A";   // ♪
        if (n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".mkv") || n.endsWith(".ts")
                || n.endsWith(".avi") || n.endsWith(".flv")) return "\u25B6";   // ▶
        return "?";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String d = getIntent().getStringExtra("dir");
        selectPath = getIntent().getStringExtra("select");
        selectMode = getIntent().getBooleanExtra("selectMode", false);
        File start = d != null ? new File(d) : android.os.Environment.getExternalStorageDirectory();
        if (start.isFile()) start = start.getParentFile();
        if (start == null || !start.isDirectory()) start = getExternalFilesDir(null);
        if (start == null || !start.isDirectory()) start = getFilesDir();
        dir = start;
        buildUi();
        loadDir(dir);
        if (selectMode) toast("已选中该文件，可继续多选、分享或删除");
        Immersive.hide(this);
    }

    @Override
    protected void onDestroy() { super.onDestroy(); THUMB_REQ.clear(); }

    private TextView barBtn(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(20);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(6), dp(10), dp(6));
        return v;
    }

    private void buildUi() {
        LinearLayout all = new LinearLayout(this);
        all.setOrientation(LinearLayout.VERTICAL);
        all.setBackgroundColor(0xFFF5F7FA);

        // 蓝色顶栏：返回 / 标题 / 搜索 / 分享 / 更多
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF1E9FFF);
        bar.setPadding(dp(8), dp(12), dp(8), dp(12));
        TextView back = barBtn("\u2190");   // ←
        back.setTextSize(24);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        bar.addView(back);
        TextView title = new TextView(this);
        title.setText("文件列表");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(10), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView search = barBtn("\uD83D\uDD0D");   // 🔍
        search.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (searchEt.getVisibility() == View.VISIBLE) {
                    searchEt.setVisibility(View.GONE);
                    query = ""; searchEt.setText(""); rebuildList();
                } else {
                    searchEt.setVisibility(View.VISIBLE);
                    searchEt.requestFocus();
                }
            }
        });
        bar.addView(search);
        TextView share = barBtn("\u21EA");   // ⇪
        share.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { shareSelected(); } });
        bar.addView(share);
        final TextView more = barBtn("\u22EE");   // ⋮
        more.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showMore(v); } });
        bar.addView(more);
        all.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        searchEt = new EditText(this);
        searchEt.setHint("搜索当前目录文件名");
        searchEt.setTextSize(14);
        searchEt.setSingleLine(true);
        searchEt.setBackgroundColor(0xFFFFFFFF);
        searchEt.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchEt.setVisibility(View.GONE);
        searchEt.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim().toLowerCase(Locale.US);
                rebuildList();
            }
            public void afterTextChanged(Editable s) {}
        });
        all.addView(searchEt, new LinearLayout.LayoutParams(-1, -2));

        // 面包屑路径
        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setBackgroundColor(0xFFFFFFFF);
        crumbBox = new LinearLayout(this);
        crumbBox.setOrientation(LinearLayout.HORIZONTAL);
        crumbBox.setGravity(Gravity.CENTER_VERTICAL);
        crumbBox.setPadding(dp(10), dp(4), dp(10), dp(4));
        crumbScroll.addView(crumbBox);
        all.addView(crumbScroll, new LinearLayout.LayoutParams(-1, -2));

        sv = new ScrollView(this);
        rowBox = new LinearLayout(this);
        rowBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(rowBox);
        all.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(all);
    }

    private void loadDir(File d) {
        if (d == null || !d.isDirectory()) return;
        dir = d;
        selected.clear();
        if (pendingSelect && selectMode && selectPath != null) {
            File sf = new File(selectPath);
            if (sf.getParentFile() != null && sf.getParent().equals(d.getAbsolutePath())) selected.add(selectPath);
            pendingSelect = false;
        }
        buildCrumbs();
        rebuildList();
        sv.scrollTo(0, 0);
    }

    private void buildCrumbs() {
        crumbBox.removeAllViews();
        ArrayList<File> chain = new ArrayList<File>();
        File c = dir;
        while (c != null) { chain.add(0, c); c = c.getParentFile(); }
        for (int i = 0; i < chain.size(); i++) {
            final File cf = chain.get(i);
            TextView seg = new TextView(this);
            seg.setText(cf.getParent() == null ? "/" : cf.getName());
            seg.setTextColor(i == chain.size() - 1 ? 0xFF1E9FFF : 0xFF667085);
            seg.setTextSize(13);
            seg.setPadding(dp(5), dp(8), dp(5), dp(8));
            seg.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { loadDir(cf); }
            });
            crumbBox.addView(seg);
            if (i < chain.size() - 1) {
                TextView sep = new TextView(this);
                sep.setText("\u203A");
                sep.setTextColor(0xFFB0B8C4);
                sep.setTextSize(15);
                crumbBox.addView(sep);
            }
        }
        crumbScroll.post(new Runnable() {
            public void run() { crumbScroll.fullScroll(View.FOCUS_RIGHT); }
        });
    }

    private final Comparator<File> cmp = new Comparator<File>() {
        public int compare(File a, File b) {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            if (sortBy == 1) return Long.valueOf(b.lastModified()).compareTo(a.lastModified());
            if (sortBy == 2) return Long.valueOf(b.length()).compareTo(a.length());
            return a.getName().compareToIgnoreCase(b.getName());
        }
    };

    private void rebuildList() {
        rowBox.removeAllViews();
        entries.clear();
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (query.length() > 0 && !f.getName().toLowerCase(Locale.US).contains(query)) continue;
                entries.add(f);
            }
        }
        Collections.sort(entries, cmp);
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(query.length() > 0 ? "没有匹配的文件" : "空文件夹");
            empty.setTextColor(0xFF98A2B3);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(80), 0, 0);
            rowBox.addView(empty);
            return;
        }
        int limit = Math.min(entries.size(), 300);
        for (int i = 0; i < limit; i++) rowBox.addView(makeRow(entries.get(i)));
        if (entries.size() > limit) {
            TextView tip = new TextView(this);
            tip.setText("仅显示前 300 项（可用搜索缩小范围）");
            tip.setTextColor(0xFF98A2B3);
            tip.setTextSize(12);
            tip.setGravity(Gravity.CENTER);
            tip.setPadding(0, dp(12), 0, dp(12));
            rowBox.addView(tip);
        }
        if (selectPath != null) {
            final View target = rowBox.findViewWithTag(selectPath);
            if (target != null) sv.post(new Runnable() {
                public void run() { sv.smoothScrollTo(0, Math.max(0, target.getTop() - dp(16))); }
            });
        }
    }

    private View makeRow(final File f) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(selected.contains(f.getAbsolutePath()) ? 0xFFE8F3FF : 0xFFFFFFFF);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setTag(f.getAbsolutePath());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(0, 0, 0, 1);
        row.setLayoutParams(rlp);

        // 左侧图标 / 视频首帧缩略图
        FrameLayout icon = new FrameLayout(this);
        icon.setBackgroundColor(f.isDirectory() ? 0xFF2196F3 : 0xFFE7EDF8);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(52), dp(52));
        ip.rightMargin = dp(12);
        icon.setLayoutParams(ip);
        TextView glyph = new TextView(this);
        glyph.setText(f.isDirectory() ? "\uD83D\uDCC1" : glyphFor(f));   // 📁
        glyph.setTextColor(f.isDirectory() ? Color.WHITE : 0xFF667085);
        glyph.setTextSize(f.isDirectory() ? 22 : 24);
        glyph.setGravity(Gravity.CENTER);
        icon.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        if (!f.isDirectory() && isThumbable(f)) loadThumb(icon, f);
        row.addView(icon);

        // 名称 + 大小/日期
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(f.getName());
        name.setTextColor(0xFF1D2939);
        name.setTextSize(15);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(name);
        TextView sub = new TextView(this);
        String info;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            info = (kids == null ? 0 : kids.length) + "项  " + fmtDate(f.lastModified());
        } else {
            info = fmtSize(f.length()) + "  " + fmtDate(f.lastModified());
        }
        sub.setText(info);
        sub.setTextColor(0xFF98A2B3);
        sub.setTextSize(12);
        sub.setPadding(0, dp(3), 0, 0);
        mid.addView(sub);
        row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

        if (f.isDirectory()) {
            TextView arrow = new TextView(this);
            arrow.setText("\u203A");
            arrow.setTextColor(0xFFB0B8C4);
            arrow.setTextSize(22);
            arrow.setPadding(dp(8), 0, 0, 0);
            row.addView(arrow);
        } else {
            final TextView dot = new TextView(this);
            dot.setTag("selDot");
            dot.setText(selected.contains(f.getAbsolutePath()) ? "\u25CF" : "\u25CB");   // ● / ○
            dot.setTextColor(selected.contains(f.getAbsolutePath()) ? 0xFF1E9FFF : 0xFFD0D5DD);
            dot.setTextSize(18);
            dot.setPadding(dp(10), dp(8), 0, dp(8));
            dot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toggleSelect(f, row, dot); }
            });
            row.addView(dot);
        }

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (f.isDirectory()) loadDir(f);
                else openFile(f);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (!f.isDirectory()) {
                    TextView d = (TextView) row.findViewWithTag("selDot");
                    toggleSelect(f, row, d);
                }
                return true;
            }
        });
        return row;
    }

    private void toggleSelect(File f, View row, TextView dot) {
        String p = f.getAbsolutePath();
        if (selected.contains(p)) selected.remove(p); else selected.add(p);
        boolean on = selected.contains(p);
        row.setBackgroundColor(on ? 0xFFE8F3FF : 0xFFFFFFFF);
        if (dot != null) {
            dot.setText(on ? "\u25CF" : "\u25CB");
            dot.setTextColor(on ? 0xFF1E9FFF : 0xFFD0D5DD);
        }
    }

    /** 后台解码视频/图片首帧，复用 RecordActivity 的解码器与内存缓存 */
    private void loadThumb(final FrameLayout box, final File f) {
        final String path = f.getAbsolutePath();
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setTag("thumbIv");
        box.addView(iv, 0, new FrameLayout.LayoutParams(-1, -1));
        Bitmap cached;
        synchronized (RecordActivity.THUMB_CACHE) { cached = RecordActivity.THUMB_CACHE.get(path); }
        if (cached != null) {
            iv.setImageBitmap(cached); iv.setVisibility(View.VISIBLE);
            for (int i = 0; i < box.getChildCount(); i++) {
                View c = box.getChildAt(i);
                if (c != iv) c.setVisibility(View.GONE);
            }
            return;
        }
        iv.setVisibility(View.INVISIBLE);
        THUMB_REQ.put(iv, path);
        RecordActivity.THUMB_POOL.execute(new Runnable() { public void run() {
            final Bitmap bm = RecordActivity.decodeThumb(FileListActivity.this, path);
            if (bm != null) synchronized (RecordActivity.THUMB_CACHE) {
                if (RecordActivity.THUMB_CACHE.size() > 96) RecordActivity.THUMB_CACHE.clear();
                RecordActivity.THUMB_CACHE.put(path, bm);
            }
            runOnUiThread(new Runnable() { public void run() {
                if (!path.equals(THUMB_REQ.get(iv))) return;
                Bitmap b2;
                synchronized (RecordActivity.THUMB_CACHE) { b2 = RecordActivity.THUMB_CACHE.get(path); }
                if (b2 != null) {
                    iv.setImageBitmap(b2); iv.setVisibility(View.VISIBLE);
                    for (int i = 0; i < box.getChildCount(); i++) {
                        View c = box.getChildAt(i);
                        if (c != iv) c.setVisibility(View.GONE);   // 隐藏 ▶ / ? 占位图标
                    }
                }
            } });
        } });
    }

    private void openFile(File f) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(uriFor(this, f), mimeOf(f.getName()));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "打开为"));
        } catch (Throwable e) { toast("没有可打开此文件的应用"); }
    }

    private void shareSelected() {
        if (selected.isEmpty()) { toast("先点右侧圆点选择文件"); return; }
        ArrayList<Uri> uris = new ArrayList<Uri>();
        String mime = null;
        for (String p : selected) {
            File f = new File(p);
            if (!f.isFile()) continue;
            uris.add(uriFor(this, f));
            String m = mimeOf(f.getName());
            if (mime == null) mime = m;
            else if (!mime.equals(m)) mime = "*/*";
        }
        if (uris.isEmpty()) { toast("没有可分享的文件"); return; }
        final String fmime = mime == null ? "*/*" : mime;
        Intent it;
        if (uris.size() == 1) {
            it = new Intent(Intent.ACTION_SEND);
            it.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            it = new Intent(Intent.ACTION_SEND_MULTIPLE);
            it.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        it.setType(fmime);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData cd = new ClipData("文件", new String[]{fmime}, new ClipData.Item(uris.get(0)));
        for (int i = 1; i < uris.size(); i++) cd.addItem(new ClipData.Item(uris.get(i)));
        it.setClipData(cd);
        try { startActivity(Intent.createChooser(it, "分享文件")); }
        catch (Throwable e) { toast("没有可分享的应用"); }
    }

    private void showMore(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add("全选");
        pm.getMenu().add("取消选择");
        pm.getMenu().add("删除所选");
        pm.getMenu().add("按名称排序");
        pm.getMenu().add("按日期排序");
        pm.getMenu().add("按大小排序");
        pm.getMenu().add("刷新");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem mi) {
                String t = mi.getTitle().toString();
                if (t.equals("全选")) {
                    for (File f : entries) if (f.isFile()) selected.add(f.getAbsolutePath());
                    rebuildList();
                } else if (t.equals("取消选择")) {
                    selected.clear(); rebuildList();
                } else if (t.equals("删除所选")) {
                    deleteSelected();
                } else if (t.equals("按名称排序")) { sortBy = 0; rebuildList(); }
                else if (t.equals("按日期排序")) { sortBy = 1; rebuildList(); }
                else if (t.equals("按大小排序")) { sortBy = 2; rebuildList(); }
                else if (t.equals("刷新")) { rebuildList(); }
                return true;
            }
        });
        pm.show();
    }

    private void deleteSelected() {
        if (selected.isEmpty()) { toast("先选择文件"); return; }
        final int n = selected.size();
        new AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确定删除选中的 " + n + " 项？此操作不可恢复。")
            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    for (String p : selected) deleteRecursive(new File(p));
                    selected.clear();
                    rebuildList();
                    toast("已删除 " + n + " 项");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private static void deleteRecursive(File f) {
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) for (File k : kids) deleteRecursive(k);
            }
            f.delete();
        } catch (Throwable ignored) {}
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
