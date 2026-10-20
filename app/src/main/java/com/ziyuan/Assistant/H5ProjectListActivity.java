package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** HTML5 编辑器 — 项目列表页 (HBuilderX 手机版风格, 深色) */
public class H5ProjectListActivity extends Activity {
    private LinearLayout list;
    private org.json.JSONArray projects;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int bg = 0xFF121212, txtMain = 0xFFE8EAED, txtSub = 0xFF9AA0A6;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        // 顶栏: 标题 + ⚙
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(18), dp(16), dp(18));
        TextView gear = txt("⚙", 22, txtMain, false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.gravity = Gravity.END;
        TextView spacer = txt("", 1, txtMain, false);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(spacer);
        top.addView(gear);
        gear.setOnClickListener(v -> Toast.makeText(this, "H5 编辑器 · vConsole/Vue 插件在项目内选择", Toast.LENGTH_SHORT).show());
        root.addView(top);
        // 项目列表
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), 0, dp(10), dp(90));
        sv.addView(list, new LinearLayout.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        // FAB +
        android.widget.FrameLayout fl = new android.widget.FrameLayout(this);
        TextView fab = txt("＋", 24, 0xFFFFFFFF, false);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(0xFF2C2C2E); fg.setShape(GradientDrawable.OVAL);
        fab.setBackground(fg);
        fab.setElevation(dp(6));
        fl.addView(fab, new android.widget.FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM | Gravity.END));
        ((android.widget.FrameLayout.LayoutParams) fab.getLayoutParams()).setMargins(0, 0, dp(20), dp(24));
        fab.setOnClickListener(v -> {
            android.widget.EditText et = new android.widget.EditText(this);
            et.setHint("项目名称");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("新建项目")
                    .setView(et)
                    .setPositiveButton("创建", (d, w) -> {
                        String n = et.getText().toString().trim();
                        if (n.length() == 0) return;
                        H5Store.createProject(this, n);
                        render();
                    })
                    .setNegativeButton("取消", null).show();
        });
        root.addView(fl, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        render();
    }

    private void render() {
        list.removeAllViews();
        projects = H5Store.projects(this);
        if (projects.length() == 0) {
            TextView e = txt("暂无项目\n点右下角 ＋ 新建", 14, 0xFF9AA0A6, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(80), 0, 0);
            list.addView(e);
            return;
        }
        for (int i = 0; i < projects.length(); i++) {
            final int idx = i;
            JSONObject p = projects.optJSONObject(i);
            if (p == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable cg = new GradientDrawable();
            cg.setColor(0xFF1E1E20); cg.setCornerRadius(dp(12));
            row.setBackground(cg);
            // 图标
            TextView ic = txt("</>", 18, 0xFF4FC3F7, true);
            ic.setGravity(Gravity.CENTER);
            GradientDrawable ig = new GradientDrawable();
            ig.setColor(0xFF26323C); ig.setCornerRadius(dp(10));
            ic.setBackground(ig);
            row.addView(ic, dp(52), dp(52));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView name = txt(p.optString("name", "项目"), 16, txtMainColor(), true);
            name.setPadding(dp(14), 0, 0, 0);
            name.setEllipsize(TextUtils.TruncateAt.END); name.setMaxLines(1);
            left.addView(name);
            String plug = (p.optJSONObject("plugins") != null && p.optJSONObject("plugins").optBoolean("vconsole") ? "vConsole " : "")
                    + (p.optJSONObject("plugins") != null && p.optJSONObject("plugins").optBoolean("vue") ? "Vue " : "");
            TextView sub = txt("files/" + p.optString("name", "") + (plug.length() > 0 ? "  ·  " + plug.trim() : ""), 12, 0xFF9AA0A6, false);
            sub.setPadding(dp(14), dp(2), 0, 0);
            sub.setEllipsize(TextUtils.TruncateAt.END); sub.setMaxLines(1);
            left.addView(sub);
            TextView ver = txt("v1.0", 12, 0xFF9AA0A6, false);
            ver.setPadding(dp(14), dp(2), 0, 0);
            left.addView(ver);
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(v -> {
                Intent it = new Intent(this, H5EditorActivity.class);
                it.putExtra("idx", idx);
                startActivity(it);
            });
            row.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("删除项目")
                        .setMessage(p.optString("name", ""))
                        .setPositiveButton("删除", (d, w) -> { H5Store.removeProject(this, idx); render(); })
                        .setNegativeButton("取消", null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            row.setLayoutParams(lp);
            list.addView(row);
        }
    }

    private int txtMainColor() { return 0xFFE8EAED; }
    private TextView txt(String s, int sz, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
