package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** HTML5 编辑器 — 项目列表页 (HBuilderX 手机版风格, 项目=真实磁盘目录) */
public class H5ProjectListActivity extends Activity {
    private LinearLayout list;
    private FrameLayout rootF;
    private static final int REQ_IMPORT = 51;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int bg = 0xFF121212, txtMain = 0xFFE8EAED;
        rootF = new FrameLayout(this);
        rootF.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(18), dp(16), dp(18));
        top.addView(txt("HBuilderX", 20, txtMain, true));
        TextView spacer = txt("", 1, txtMain, false);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(spacer);
        TextView gear = txt("⚙", 22, txtMain, false);
        top.addView(gear);
        gear.setOnClickListener(v -> Toast.makeText(this, "长按项目删除 · 插件在项目内选择", Toast.LENGTH_SHORT).show());
        root.addView(top);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), 0, dp(12), dp(90));
        sv.addView(list, new LinearLayout.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        rootF.addView(root, new FrameLayout.LayoutParams(-1, -1));
        // FAB ＋ (新建项目)
        TextView fab = txt("＋", 24, 0xFFFFFFFF, false);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF4FC3F7); gd.setShape(GradientDrawable.OVAL);
        fab.setBackground(gd);
        fab.setElevation(dp(6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        fab.setLayoutParams(flp);
        fab.setOnClickListener(v -> {
            android.widget.EditText et = new android.widget.EditText(this);
            et.setHint("项目名 (如 myweb)");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("新建项目")
                    .setView(et)
                    .setPositiveButton("创建", (d, w) -> {
                        String n = et.getText().toString().trim();
                        if (n.length() == 0) return;
                        if (!H5Store.createProject(this, n)) { Toast.makeText(this, "项目已存在", Toast.LENGTH_SHORT).show(); return; }
                        render();
                    })
                    .setNegativeButton("取消", null).show();
        });
        rootF.addView(fab);
        setContentView(rootF);
        render();
    }

    private void render() {
        list.removeAllViews();
        List<String> ps = H5Store.projects(this);
        if (ps.isEmpty()) {
            TextView empty = txt("还没有项目\n点右下角 ＋ 新建", 14, 0xFF9AA0A6, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            list.addView(empty);
            return;
        }
        for (final String name : ps) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            TextView ic = txt("</>", 14, 0xFFFFFFFF, false);
            ic.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFF2D2F33); g.setCornerRadius(dp(10));
            ic.setBackground(g);
            row.addView(ic, dp(44), dp(44));
            LinearLayout lt = new LinearLayout(this);
            lt.setOrientation(LinearLayout.VERTICAL);
            lt.addView(txt(name, 16, 0xFFE8EAED, true));
            int files = H5Store.listTree(H5Store.projectDir(this, name)).size();
            lt.addView(txt(files + " 个文件", 12, 0xFF9AA0A6, false));
            lt.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(lt);
            row.setOnClickListener(v -> {
                Intent i = new Intent(this, H5ProjectActivity.class);
                i.putExtra("name", name);
                startActivity(i);
            });
            row.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("删除项目")
                        .setMessage(name)
                        .setPositiveButton("删除", (d, w) -> { H5Store.removeProject(this, name); render(); })
                        .setNegativeButton("取消", null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            row.setLayoutParams(lp);
            list.addView(row);
        }
    }

    private TextView txt(String s, int sz, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
