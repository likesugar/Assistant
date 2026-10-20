package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 项目界面 — WebCat 同款: 顶栏◀项目名 + 新建文件/导入文件按钮行 + 文件列表(文件夹在前, 名称+大小) */
public class H5ProjectActivity extends Activity {
    private String projName;
    private File projDir, curDir;
    private LinearLayout list;
    private static final int REQ_IMPORT = 61;
    private static final int bg = 0xFF121212, card = 0xFF1B1C1F, txtMain = 0xFFE8EAED, txtSub = 0xFF9AA0A6, blue = 0xFF4FC3F7;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        projName = getIntent().getStringExtra("name");
        projDir = H5Store.projectDir(this, projName);
        curDir = projDir;
        if (projDir == null || !projDir.exists()) { finish(); return; }
        android.widget.FrameLayout rootF = new android.widget.FrameLayout(this);
        rootF.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(14), dp(12), dp(10));
        TextView back = txt("←", 20, txtMain, false);
        back.setPadding(dp(10), 0, dp(10), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView name = txt(projName, 18, txtMain, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top, -1, -2);
        // 按钮行: 新建文件 / 导入文件 (图1 描边按钮)
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(dp(12), dp(4), dp(12), dp(10));
        TextView b1 = outlineBtn("新建文件");
        b1.setOnClickListener(v -> newFileDialog());
        TextView b2 = outlineBtn("导入文件");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        bp.rightMargin = dp(10);
        b2.setLayoutParams(bp);
        b2.setOnClickListener(v -> importFile());
        btns.addView(b1, bp);
        btns.addView(b2);
        root.addView(btns, -1, -2);
        // 面包屑路径
        final TextView crumb = txt("", 12, blue, false);
        crumb.setPadding(dp(14), 0, dp(14), dp(6));
        crumb.setOnClickListener(v -> { curDir = projDir; render(); });
        root.addView(crumb, -1, -2);
        // 文件列表
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), 0, dp(12), dp(80));
        sv.addView(list, new LinearLayout.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        rootF.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(rootF);
        render();
    }

    private TextView outlineBtn(String s) {
        TextView t = txt(s, 14, blue, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, dp(10));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(card);
        gd.setStroke(dp(1), 0xFF3A3D42, 0, 0);
        gd.setCornerRadius(dp(20));
        t.setBackground(gd);
        return t;
    }

    private void render() {
        list.removeAllViews();
        curDir = curDir == null ? projDir : curDir;
        String rel = projDir.equals(curDir) ? "" : projDir.toPath().relativize(curDir.toPath()).toString() + "/";
        crumbSet(rel);
        File[] fs = curDir.listFiles();
        if (fs == null || fs.length == 0) {
            TextView empty = txt("暂无文件，点击上方「新建文件」", 13, txtSub, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            list.addView(empty, -1, -2);
            return;
        }
        List<File> dirs = new ArrayList<>(), files = new ArrayList<>();
        for (File f : fs) (f.isDirectory() ? dirs : files).add(f);
        Collections.sort(dirs, (a, b2) -> a.getName().compareToIgnoreCase(b2.getName()));
        Collections.sort(files, (a, b2) -> a.getName().compareToIgnoreCase(b2.getName()));
        for (File f : dirs) addRow(f, rel, true);
        for (File f : files) addRow(f, rel, false);
    }

    private void crumbSet(String rel) {
        ((TextView) ((LinearLayout) list.getParent().getParent()).getChildAt(2)).setText(rel);
    }

    private void addRow(final File f, String rel, boolean isDir) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(card);
        gd.setCornerRadius(dp(12));
        row.setBackground(gd);
        // 图标
        TextView icon = txt(isDir ? "📁" : fileIcon(f.getName()), 17, blue, false);
        icon.setGravity(Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams ip = new android.widget.LinearLayout.LayoutParams(dp(34), dp(34));
        GradientDrawable igd = new GradientDrawable();
        igd.setColor(0xFF20242B);
        igd.setCornerRadius(dp(8));
        icon.setBackground(igd);
        row.addView(icon, ip);
        // 名称 + 小字
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView name = txt(f.getName(), 15, txtMain, false);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        mid.addView(name, -2, -2);
        String sub = isDir ? "文件夹" : sizeStr(f.length()) + " · " + ext(f.getName());
        TextView small = txt(sub, 11, txtSub, false);
        mid.addView(small, -2, -2);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        mp.leftMargin = dp(11);
        row.addView(mid, mp);
        row.setOnClickListener(v -> {
            if (f.isDirectory()) { curDir = f; render(); return; }
            if (H5Store.isTextFile(f.getName())) {
                Intent i = new Intent(this, H5EditorActivity.class);
                i.putExtra("name", projName);
                i.putExtra("path", projDir.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/'));
                startActivity(i);
            } else {
                Toast.makeText(this, "该类型暂不支持编辑", Toast.LENGTH_SHORT).show();
            }
        });
        row.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("删除")
                    .setMessage(f.getName())
                    .setPositiveButton("删除", (d, w) -> { delRec(f); render(); })
                    .setNegativeButton("取消", null).show();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        list.addView(row);
    }

    private void delRec(File f) {
        if (f.isDirectory()) { File[] fs = f.listFiles(); if (fs != null) for (File x : fs) delRec(x); }
        f.delete();
    }

    private void newFileDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setTextColor(txtMain);
        et.setHintTextColor(txtSub);
        et.setHint("如 index.html 或 js/app.js");
        box.addView(et, -1, -2);
        final TextView tip = txt("子目录会自动创建", 11, txtSub, false);
        tip.setPadding(0, dp(8), 0, 0);
        box.addView(tip, -1, -2);
        new android.app.AlertDialog.Builder(this)
                .setTitle("新建文件")
                .setView(box)
                .setPositiveButton("创建", (d, w) -> {
                    String p = et.getText().toString().trim().replace("\\", "/");
                    if (p.length() == 0) return;
                    File f = new File(projDir, p);
                    f.getParentFile().mkdirs();
                    try {
                        if (!f.exists() && f.getName().contains(".")) f.createNewFile();
                        if (H5Store.isHtml(f.getName()) && f.length() == 0)
                            H5Store.writeFile(f, H5Store.DEFAULT_HTML);
                    } catch (Throwable ignored) {}
                    render();
                })
                .setNegativeButton("取消", null).show();
    }

    private void importFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_IMPORT && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String name = H5Store.queryName(this, uri);
            if (name == null) name = "import_" + System.currentTimeMillis();
            H5Store.importFrom(this, curDir, uri, name);
            render();
            Toast.makeText(this, "已导入 " + name, Toast.LENGTH_SHORT).show();
        }
    }

    private String fileIcon(String n) {
        String e = ext(n);
        if (e.equals("html") || e.equals("htm")) return "🌐";
        if (e.equals("css")) return "🎨";
        if (e.equals("js") || e.equals("json")) return "📜";
        if (e.equals("png") || e.equals("jpg") || e.equals("jpeg") || e.equals("gif") || e.equals("webp") || e.equals("svg")) return "🖼";
        if (e.equals("mp4") || e.equals("mp3") || e.equals("m3u8")) return "🎬";
        return "📄";
    }
    private String ext(String n) { int i = n.lastIndexOf('.'); return i < 0 ? "" : n.substring(i + 1).toLowerCase(); }
    private String sizeStr(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.1f KB", b / 1024f);
        return String.format("%.2f MB", b / 1048576f);
    }
    private TextView txt(String s, int sz, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
