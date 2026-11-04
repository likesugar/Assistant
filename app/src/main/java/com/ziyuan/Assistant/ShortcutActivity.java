package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 创建快捷键页：两列小卡片网格（新建快捷键 + 已建快捷键），扁平化深色 */
public class ShortcutActivity extends Activity {

    // ---------- 规范分隔符：全项目唯一（真 \u0001 控制字符） ----------
    public static final String S1 = String.valueOf((char) 1);
    private static final String LIT = "\\u0001";  // 历史字面量形式（\u0001 六字符）
    private static android.content.Context App0;

    private LinearLayout grid;

    // ---------- 生命周期 ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        App0 = getApplicationContext();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color0.bg(this));

        TextView title = new TextView(this);
        title.setText("创建快捷键");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color0.text(this));
        title.setPadding(dp(18), dp(20), dp(18), dp(10));
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(18), 0, dp(18), dp(18));
        sv.addView(grid);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        migrateShortcuts();
        renderGrid();
    }

    // ---------- 分隔符归一 ----------
    private static String[] normSplit(String raw) {
        String r = raw.replace(LIT, S1);
        return r.split(java.util.regex.Pattern.quote(S1), -1);
    }

    private static String normJoin(String[] f) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < f.length; i++) { if (i > 0) sb.append(S1); sb.append(f[i] == null ? "" : f[i]); }
        return sb.toString();
    }

    // ---------- 迁移：兼容历史格式，按名称去重，补发干净 key ----------
    private void migrateShortcuts() {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("home_shortcuts", MODE_PRIVATE);
            int n = sp.getInt("count", 0);
            java.util.ArrayList<String> raws = new java.util.ArrayList<String>();
            for (int i = 0; i < n; i++) {
                String r = sp.getString("s" + i, null);
                if (r != null) raws.add(r);
            }
            java.util.LinkedHashMap<String, String[]> byName = new java.util.LinkedHashMap<String, String[]>();
            boolean dirty = false;
            for (String raw : raws) {
                String[] p3 = normSplit(raw);
                if (p3.length < 3 || p3[0].trim().length() == 0) { dirty = true; continue; }
                String[] f = new String[11];
                for (int j = 0; j < 11; j++) f[j] = j < p3.length ? p3[j] : (j == 7 ? "1" : "");
                // 历史错位：图标路径/key 互换的条目修复
                if (f[9].startsWith("/data") && f[10].length() > 0 && !f[10].startsWith("/data")) {
                    String ip = f[9]; f[9] = f[10]; f[10] = ip;
                }
                if (f[9].startsWith("/data") && f[10].startsWith("/data")) f[10] = "";
                if (f[9].length() == 0 && f[10].startsWith("/data")) { f[9] = f[10]; f[10] = ""; }
                String key = f[10].length() > 0 && !f[10].contains("/") ? f[10]
                    : ("n_" + Integer.toHexString(f[0].hashCode()));
                f[10] = key;
                String canonical = normJoin(f);
                if (!canonical.equals(raw)) dirty = true;
                byName.put(f[0], f);   // 同名保留最后
            }
            if (dirty || byName.size() != n) {
                android.content.SharedPreferences.Editor e = sp.edit();
                for (int i = 0; i < n; i++) e.remove("s" + i);
                int i = 0;
                for (java.util.Map.Entry<String, String[]> en : byName.entrySet()) {
                    String val = normJoin(en.getValue());
                    e.putString("s" + i, val);
                    e.putString("k_" + en.getValue()[10], val);
                    i++;
                }
                e.putInt("count", i);
                e.apply();
                dumpFc2Debug("MIGRATE 归一完成 条目=" + i + " 原条目=" + n);
            }
        } catch (Throwable t) {
            dumpFc2Debug("MIGRATE-ERR " + t);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 8001 && res == RESULT_OK && data != null && data.getStringExtra("pkg") != null) {
            Intent i = new Intent(this, ShortcutEditActivity.class);
            i.putExtra("e0", data.getStringExtra("name") == null ? "" : data.getStringExtra("name"));
            i.putExtra("e1", data.getStringExtra("pkg"));
            i.putExtra("e2", data.getStringExtra("cls") == null ? "" : data.getStringExtra("cls"));
            startActivity(i);
        }
    }

    // ---------- 网格渲染 ----------
    private void renderGrid() {
        grid.removeAllViews();
        java.util.ArrayList<View> cells = new java.util.ArrayList<View>();
        cells.add(smallCard("⚡", "新建快捷键", new View.OnClickListener() {
            public void onClick(View v) {
                // 照 ShortcutCreator 流程: 新建 → 图1应用选择 → (活动列表) → 详情 → 编辑页
                try { startActivityForResult(new Intent(ShortcutActivity.this, AppPickerActivity.class), 8001); }
                catch (Throwable t) { startActivity(new Intent(ShortcutActivity.this, ShortcutEditActivity.class)); }
            }
        }, null));
        android.content.pm.PackageManager pm0 = getPackageManager();
        for (final String[] us : userShortcuts()) {
            final String key = z(us, 10);
            View iconCell;
            // 优先目标应用真实图标，其次自选图，最后首字
            android.graphics.Bitmap bmp = null;
            try { bmp = iconToBmp(pm0.getApplicationIcon(z(us, 1))); } catch (Throwable ignored) {}
            if (bmp == null && z(us, 9).length() > 0) bmp = android.graphics.BitmapFactory.decodeFile(z(us, 9));
            if (bmp != null) iconCell = iconImage(bmp);
            else iconCell = iconText(z(us, 0).length() > 0 ? z(us, 0).substring(0, 1) : "?");
            cells.add(smallCardEx(iconCell, z(us, 0), new View.OnClickListener() {
                public void onClick(View v) { launchShortcut(us); }
            }, new View.OnLongClickListener() {
                public boolean onLongClick(View v) { itemMenu(us, key); return true; }
            }));
        }
        LinearLayout row = null;
        final int gap = dp(12);   // 卡片间距(横向+纵向)
        for (int i = 0; i < cells.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rlp.topMargin = gap;   // 行间距
                grid.addView(row, rlp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 1) lp.leftMargin = gap;
            row.addView(cells.get(i), lp);
            // 奇数收尾: 补等宽占位, 末行单卡片保持半宽, 不撑满整行
            if (i % 2 == 0 && i == cells.size() - 1) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private View smallCard(String icon, String name, View.OnClickListener click, View.OnLongClickListener longClick) {
        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(24);
        ic.setGravity(Gravity.CENTER);
        ic.setTextColor(Color0.text(this));
        return smallCardEx(ic, name, click, longClick);
    }

    private View smallCardEx(View iconCell, String name, View.OnClickListener click, View.OnLongClickListener longClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(pressable(roundBg(Color0.card(this), dp(18))));
        int pad = dp(16);
        card.setPadding(pad, dp(18), pad, dp(18));
        // 图标底座: 56dp 圆角方块, 图标居中
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(pressable(roundBg(Color0.btnBg(this), dp(16))));
        tile.addView(iconCell, new LinearLayout.LayoutParams(-1, -2));
        card.addView(tile, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(13);
        tv.setTextColor(Color0.text(this));
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(10);
        card.addView(tv, tlp);
        card.setOnClickListener(click);
        if (longClick != null) card.setOnLongClickListener(longClick);
        return card;
    }

    /** 圆角纯色背景 */
    private static android.graphics.drawable.GradientDrawable roundBg(int color, float radiusPx) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** 给圆角背景叠加按下涟漪(内容/遮罩同一形状, 圆角不破) */
    private static android.graphics.drawable.RippleDrawable pressable(android.graphics.drawable.GradientDrawable content) {
        return new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x338AB8FF), content, content);
    }

    private View iconText(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(24);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color0.text(this));
        return tv;
    }

    private android.graphics.Bitmap iconToBmp(android.graphics.drawable.Drawable d) {
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bm);
        d.setBounds(0, 0, w, h);
        d.draw(cv);
        return bm;
    }

    private View iconImage(android.graphics.Bitmap bm) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(bm);
        return iv;
    }

    // ---------- 菜单/启动 ----------
    private void itemMenu(final String[] us, final String key) {
        new GlassDialog.Builder(this)
            .setTitle(z(us, 0))
            .setItems(new String[]{"打开", "编辑", "删除"}, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (w == 0) launchShortcut(us);
                    else if (w == 1) {
                        Intent i = new Intent(ShortcutActivity.this, ShortcutEditActivity.class);
                        i.putExtra("key", key);
                        for (int j = 0; j < 10; j++) i.putExtra("e" + j, z(us, j));
                        startActivity(i);
                    } else {
                        deleteByKey(key);
                        renderGrid();
                    }
                }
            }).show();
    }

    private void launchShortcut(String[] us) {
        String pkg = z(us, 1), cls = z(us, 2);
        String data = z(us, 3), extras = z(us, 4);
        int am2 = parseInt(z(us, 5), 0);
        String custom = z(us, 6);
        boolean newTask = !z(us, 7).equals("0");
        boolean root = z(us, 8).equals("1");
        if (root) {
            try {
                String cmd = "am start -n " + pkg + (cls.length() > 0 ? "/" + cls : "");
                Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                if (p2.waitFor() == 0) return;
                Toast.makeText(this, "Root启动失败", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) { Toast.makeText(this, "无Root权限", Toast.LENGTH_SHORT).show(); }
            return;
        }
        try {
            Intent i = new Intent();
            if (am2 == 1) { i.setAction(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER); }
            else if (am2 == 2 && custom.length() > 0) i.setAction(custom);
            else i.setAction(Intent.ACTION_VIEW);
            if (data.length() > 0) { try { i.setData(android.net.Uri.parse(data)); } catch (Throwable ignored) {} }
            if (cls.length() > 0) i.setComponent(new android.content.ComponentName(pkg, cls));
            for (String ln : extras.split("\n")) {
                ln = ln.trim();
                int eq = ln.indexOf('=');
                if (eq <= 0) continue;
                String k = ln.substring(0, eq), v = ln.substring(eq + 1);
                try {
                    if (v.matches("-?\\d+")) i.putExtra(k, Long.parseLong(v));
                    else if (v.equals("true") || v.equals("false")) i.putExtra(k, Boolean.parseBoolean(v));
                    else i.putExtra(k, v);
                } catch (Throwable t) { i.putExtra(k, v); }
            }
            if (newTask) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable t) { }
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return; }
        } catch (Throwable ignored) {}
        Toast.makeText(this, "未能打开目标应用", Toast.LENGTH_LONG).show();
    }

    // ---------- 存储 ----------
    private java.util.ArrayList<String[]> userShortcuts() {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>();
        try {
            android.content.SharedPreferences sp = getSharedPreferences("home_shortcuts", MODE_PRIVATE);
            int n = sp.getInt("count", 0);
            for (int i = 0; i < n; i++) {
                String raw = sp.getString("s" + i, null);
                if (raw == null) continue;
                String[] full = normSplit(raw);
                if (full.length < 3) continue;
                String[] f = new String[11];
                for (int j = 0; j < 11; j++) f[j] = j < full.length ? full[j] : (j == 7 ? "1" : "");
                if (f[9].length() == 0) f[9] = newestIconPath();
                if (f[10].length() == 0) f[10] = "n_" + Integer.toHexString(f[0].hashCode());
                out.add(f);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public static String[] findByKey(android.content.Context c, String key) {
        String raw = c.getSharedPreferences("home_shortcuts", android.content.Context.MODE_PRIVATE)
            .getString("k_" + key, null);
        return raw == null ? null : raw.split(java.util.regex.Pattern.quote(S1));
    }

    public static void saveByKey(android.content.Context c, String key, String rec) {
        try {
            android.content.SharedPreferences sp = c.getSharedPreferences("home_shortcuts", android.content.Context.MODE_PRIVATE);
            int n = sp.getInt("count", 0);
            java.util.ArrayList<String> all = new java.util.ArrayList<String>();
            for (int i = 0; i < n; i++) { String r = sp.getString("s" + i, null); if (r != null) all.add(r); }
            boolean replaced = false;
            String recName = normSplit(rec)[0];
            for (int i = 0; i < all.size(); i++) {
                String[] p3 = normSplit(all.get(i));
                String k = z(p3, 10);
                if (k.equals(key) || (k.length() == 0 && p3[0].equals(recName))) {
                    all.set(i, rec + S1 + key); replaced = true; break;
                }
            }
            if (!replaced) all.add(rec + S1 + key);
            android.content.SharedPreferences.Editor e = sp.edit();
            e.putInt("count", all.size());
            for (int i = 0; i < all.size(); i++) e.putString("s" + i, all.get(i));
            e.putString("k_" + key, rec);
            e.apply();
        } catch (Throwable ignored) {}
    }

    private void deleteByKey(String key) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("home_shortcuts", MODE_PRIVATE);
            int n = sp.getInt("count", 0);
            java.util.ArrayList<String> all = new java.util.ArrayList<String>();
            for (int i = 0; i < n; i++) {
                String r = sp.getString("s" + i, null);
                if (r == null) continue;
                String[] p3 = normSplit(r);
                if (!z(p3, 10).equals(key)) all.add(r);
            }
            android.content.SharedPreferences.Editor e = sp.edit();
            e.putInt("count", all.size());
            for (int i = 0; i < all.size(); i++) e.putString("s" + i, all.get(i));
            for (int i = all.size(); i < n; i++) e.remove("s" + i);
            e.remove("k_" + key);
            e.apply();
        } catch (Throwable ignored) {}
    }

    private static String z(String[] a, int i) { return i < a.length && a[i] != null ? a[i] : ""; }
    private static int parseInt(String s2, int def) { try { return Integer.parseInt(s2); } catch (Throwable e) { return def; } }

    private static String newestIconPath() {
        try {
            if (App0 == null) return "";
            java.io.File idir = new java.io.File(App0.getFilesDir(), "sc_icons");
            java.io.File[] fs = idir.listFiles();
            if (fs == null || fs.length == 0) return "";
            java.io.File newest = fs[0];
            for (java.io.File f : fs) if (f.lastModified() > newest.lastModified()) newest = f;
            return newest.getAbsolutePath();
        } catch (Throwable e) { return ""; }
    }

    public static void dumpFc2Debug(String st) {
        try {
            if (App0 == null || App0.getExternalFilesDir(null) == null) return;
            java.io.File dir = App0.getExternalFilesDir(null);
            java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, "fc2_debug.txt"), true);
            fw.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                + " " + st + "\n----------------\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    // ---------- 主题/尺寸小助手 ----------
    private static class Color0 {
        static int bg(android.content.Context c) { return Theme.dark(c) ? 0xFF000000 : 0xFFEEF4FF; }
        static int card(android.content.Context c) { return Theme.c(c, 0xFF161A20, 0xFFFFFFFF); }
        static int text(android.content.Context c) { return Theme.c(c, 0xFFE8ECF2, 0xFF1F2329); }
        static int btnBg(android.content.Context c) { return Theme.c(c, 0xFF242B34, 0xFFE3E9F5); }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }

}
