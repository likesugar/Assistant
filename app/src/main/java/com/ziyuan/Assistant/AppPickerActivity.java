package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 应用选择页（图1式）：图标+名称+包名+活动列表按钮，可开关系统应用 */
public class AppPickerActivity extends Activity {

    private LinearLayout list;
    private TextView tvCount, tvToggle;
    private java.util.List<android.content.pm.PackageInfo> all = new java.util.ArrayList<android.content.pm.PackageInfo>();
    private java.util.List<android.content.pm.PackageInfo> shown = new java.util.ArrayList<android.content.pm.PackageInfo>();
    private boolean showSystem = false;
    private boolean loaded = false;
    private boolean hideNoActs = true;    // 隐藏无可列出活动的包
    private boolean actsScanned = false;  // 活动数是否已扫描（点开"无活动:显"才扫）
    private boolean onlyExported = false; // 仅可直启(exported)活动
    private boolean hasRoot = detectRoot();

    private static boolean detectRoot() {
        try {
            for (String p : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su", "/su/bin/su"}) {
                if (new java.io.File(p).exists()) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
    private String searchText = "";
    private android.content.pm.PackageManager pm;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        pm = getPackageManager();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.c(AppPickerActivity.this, 0xFF000000, 0xFFEEF4FF));

        // 顶栏：标题+数量 | 系统应用开关
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("应用选择");
        title.setTextColor(Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF1F2329));
        title.setTextSize(18);
        title.getPaint().setFakeBoldText(true);
        tcol.addView(title);
        tvCount = new TextView(this);
        tvCount.setTextColor(Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
        tvCount.setTextSize(13);
        tcol.addView(tvCount);
        top.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        tvToggle = new TextView(this);
        tvToggle.setTextSize(13);
        tvToggle.setPadding(dp(12), dp(8), dp(12), dp(8));
        tvToggle.setGravity(Gravity.CENTER);
        tvToggle.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            showSystem = !showSystem;
            applyFilter();
        }});
        top.addView(tvToggle);
        root.addView(top);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(dp(14), 0, dp(14), dp(8));
        EditText etSearch = new EditText(this);
        etSearch.setHint("搜索应用/包名");
        etSearch.setBackground(null);
        etSearch.setTextColor(Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF1F2329));
        etSearch.setHintTextColor(Theme.c(AppPickerActivity.this, 0xFFB8C0CC, 0xFF9AA5B4));
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        row2.addView(etSearch, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView tg2 = new TextView(this);
        tg2.setText("无活动:隐");
        tg2.setTextSize(12);
        tg2.setTextColor(0xFF3D7BFF);
        tg2.setPadding(dp(10), dp(6), dp(10), dp(6));
        tg2.setBackgroundColor(Theme.c(AppPickerActivity.this, 0xFF1B222B, 0xFFE7EDF8));
        tg2.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            hideNoActs = !hideNoActs;
            tg2.setText(hideNoActs ? "无活动:隐" : "无活动:显");
            tg2.setTextColor(hideNoActs ? 0xFF3D7BFF : Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
            if (!hideNoActs && !actsScanned) {
                tg2.setText("扫描中…");
                new Thread(new Runnable() { public void run() {
                    for (android.content.pm.PackageInfo pi : all) {
                        if (actCount.containsKey(pi.packageName)) continue;
                        try {
                            android.content.pm.ActivityInfo[] acts = pm.getPackageInfo(pi.packageName, android.content.pm.PackageManager.GET_ACTIVITIES).activities;
                            actCount.put(pi.packageName, acts == null ? 0 : acts.length);
                        } catch (Throwable t) { actCount.put(pi.packageName, 0); }
                    }
                    actsScanned = true;
                    runOnUiThread(new Runnable() { public void run() {
                        tg2.setText("无活动:显");
                        applyFilter();
                    }});
                } }).start();
                return;
            }
            applyFilter();
        }});
        row2.addView(tg2);
        final TextView tg3 = new TextView(this);
        // 有Root: 默认仅可直启:关(全部活动都可用); 无Root: 默认开
        onlyExported = !hasRoot;
        tg3.setTextSize(12);
        tg3.setTextColor(Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
        tg3.setPadding(dp(10), dp(6), dp(10), dp(6));
        tg3.setBackgroundColor(Theme.c(AppPickerActivity.this, 0xFF1B222B, 0xFFE7EDF8));
        LinearLayout.LayoutParams t3p = new LinearLayout.LayoutParams(-2, -2);
        t3p.leftMargin = dp(8);
        tg3.setLayoutParams(t3p);
        tg3.setText(onlyExported ? "仅可直启:开" : "仅可直启:关");
        tg3.setTextColor(onlyExported ? 0xFF3D7BFF : Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
        tg3.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            onlyExported = !onlyExported;
            tg3.setText(onlyExported ? "仅可直启:开" : "仅可直启:关");
            tg3.setTextColor(onlyExported ? 0xFF3D7BFF : Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
            applyFilter();
        }});
        row2.addView(tg3);
        root.addView(row2);
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void onTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void afterTextChanged(android.text.Editable e) {
                searchText = e.toString().trim().toLowerCase();
                applyFilter();
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(4), dp(10), dp(16));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        applyFilter();   // 初始显示"加载中…"，不用等触碰开关

        new Thread(new Runnable() { public void run() { loadApps(); runOnUiThread(new Runnable() { public void run() { loaded = true; applyFilter(); } }); } }).start();
    }

    private final java.util.HashMap<String, Integer> actCount = new java.util.HashMap<String, Integer>();
    private final java.util.HashMap<String, Bitmap> iconCache = new java.util.HashMap<String, Bitmap>();

    private void loadApps() {
        try { all = pm.getInstalledPackages(android.content.pm.PackageManager.GET_ACTIVITIES); } catch (Throwable e) { all = new java.util.ArrayList<android.content.pm.PackageInfo>(); }
        // 一次性批量拿到活动数（单次系统调用）; 图标不预载, 照参照物逐行异步加载
        for (android.content.pm.PackageInfo pi : all) {
            try {
                android.content.pm.ActivityInfo[] acts = pi.activities;
                actCount.put(pi.packageName, acts == null ? 0 : acts.length);
            } catch (Throwable t) { actCount.put(pi.packageName, 0); }
        }
        actsScanned = true;
        java.util.Collections.sort(all, new java.util.Comparator<android.content.pm.PackageInfo>() {
            public int compare(android.content.pm.PackageInfo a, android.content.pm.PackageInfo b) {
                return labelOf(a).compareToIgnoreCase(labelOf(b));
            }
        });
    }

    private void applyFilter() {
        list.removeAllViews();
        if (!loaded) {
            TextView loading = new TextView(this);
            loading.setText("加载中…");
            loading.setTextColor(Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
            loading.setTextSize(14);
            loading.setGravity(Gravity.CENTER);
            loading.setPadding(0, dp(40), 0, 0);
            list.addView(loading);
            tvCount.setText("加载中…");
            return;
        }
        shown.clear();
        for (android.content.pm.PackageInfo pi : all) {
            if (!showSystem && (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (hideNoActs) {
                Integer cnt = actCount.get(pi.packageName);
                if (cnt != null && cnt == 0) continue;
            }
            if (searchText.length() > 0) {
                String l = labelOf(pi).toLowerCase();
                if (!l.contains(searchText) && !pi.packageName.toLowerCase().contains(searchText)) continue;
            }
            shown.add(pi);
        }
        tvCount.setText("共" + shown.size() + "个（系统应用" + (showSystem ? "已显示" : "已隐藏") + "）");
        tvToggle.setText(showSystem ? "隐藏系统应用" : "显示系统应用");
        tvToggle.setTextColor(showSystem ? 0xFF3D7BFF : Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
        tvToggle.setBackgroundColor(Theme.c(AppPickerActivity.this, 0xFF1B222B, 0xFFE7EDF8));
        renderList();
    }

    private int renderGen = 0;   // 渲染代次：新渲染请求会取消旧的分批任务

    private void renderList() {
        list.removeAllViews();
        renderGen++;
        final int gen = renderGen;
        // 首屏只渲染10个，其余分批慢渲染
        final int first = Math.min(10, shown.size());
        for (int i = 0; i < first; i++) renderRow(shown.get(i));
        if (first < shown.size()) {
            final int[] next = {first};
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable[] chunk = new Runnable[1];
            chunk[0] = new Runnable() { public void run() {
                if (gen != renderGen) return;   // 已有新渲染请求，作废
                int end = Math.min(next[0] + 20, shown.size());
                for (int i = next[0]; i < end; i++) renderRow(shown.get(i));
                next[0] = end;
                if (end < shown.size()) h.postDelayed(chunk[0], 40);
            }};
            h.postDelayed(chunk[0], 60);
        }
    }

    private void renderRow(final android.content.pm.PackageInfo pi) {
        {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFF161A20);
            int pad = dp(10);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = dp(8);
            row.setLayoutParams(rlp);

            final ImageView ic = new ImageView(this);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Bitmap icb = iconCache.get(pi.packageName);
            if (icb != null) {
                ic.setImageBitmap(icb);
            } else {
                ic.setBackgroundColor(Theme.c(AppPickerActivity.this, 0xFF242B34, 0xFFE3E9F5));
                final String fp = pi.packageName, fg = pi.packageName + "@" + renderGen;
                ic.setTag(fg);
                new Thread(new Runnable() { public void run() {
                    Bitmap bm = iconCache.get(fp);
                    if (bm == null) {
                        android.content.pm.PackageInfo px = pkgInfoByPkg(fp);
                        if (px == null) return;
                        bm = iconToBmp(px);
                        iconCache.put(fp, bm);
                    }
                    Bitmap fb = bm;
                    runOnUiThread(new Runnable() { public void run() {
                        if (fg.equals(ic.getTag())) ic.setImageBitmap(iconCache.get(fp));
                    }});
                } }).start();
            }
            row.addView(ic, new LinearLayout.LayoutParams(dp(46), dp(46)));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
            mlp.leftMargin = dp(12);
            mid.setLayoutParams(mlp);
            TextView nm = new TextView(this);
            nm.setText(labelOf(pi));
            nm.setTextColor(Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF1F2329));
            nm.setTextSize(15);
            nm.getPaint().setFakeBoldText(true);
            mid.addView(nm);
            TextView pkg = new TextView(this);
            pkg.setText(pi.packageName);
            pkg.setTextColor(Theme.c(AppPickerActivity.this, 0xFFF2F4F8, 0xFF6B7280));
            pkg.setTextSize(12);
            mid.addView(pkg);
            row.addView(mid);

            TextView actsBtn = new TextView(this);
            actsBtn.setText("活动列表");
            actsBtn.setTextColor(0xFFCFE3F5);
            actsBtn.setTextSize(13);
            actsBtn.setGravity(Gravity.CENTER);
            actsBtn.setBackgroundColor(0xFF33627E);
            actsBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
            actsBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showActivities(pi); } });
            row.addView(actsBtn, new LinearLayout.LayoutParams(-2, -2));

            // 点行 = 直达入口活动
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Intent li = pm.getLaunchIntentForPackage(pi.packageName);
                if (li != null && li.getComponent() != null) {
                    finishWith(pi.packageName, li.getComponent().getClassName());
                } else {
                    showActivities(pi);
                }
            }});
            list.addView(row);
        }
    }

    private void showActivities(final android.content.pm.PackageInfo pi) {
        // 图1式：独立活动选择列表页（每个活动一张卡, 点"详情"回传）
        try { startActivityForResult(new Intent(this, ActivityListActivity.class)
                .putExtra("pkg", pi.packageName).putExtra("name", labelOf(pi)), 7003); }
        catch (Throwable e) { toast("无法打开活动列表"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7003 && res == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);   // 转发给 ShortcutEditActivity
            finish();
        }
    }

    private void finishWith(String pkg, String cls) {
        Intent out = new Intent();
        out.putExtra("pkg", pkg);
        out.putExtra("cls", cls);
        out.putExtra("name", labelOf(pkg.equals("") ? null : pkgInfoByPkg(pkg)));
        setResult(RESULT_OK, out);
        finish();
    }

    private android.content.pm.PackageInfo pkgInfoByPkg(String pkg) {
        for (android.content.pm.PackageInfo pi : all) if (pi.packageName.equals(pkg)) return pi;
        return null;
    }

    private String labelOf(android.content.pm.PackageInfo pi) {
        try { return String.valueOf(pi.applicationInfo.loadLabel(pm)); }
        catch (Throwable e) { return pi.packageName; }
    }

    private Bitmap iconToBmp(android.content.pm.PackageInfo pi) {
        try {
            android.graphics.drawable.Drawable d = pi.applicationInfo.loadIcon(pm);
            int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
            int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            d.setBounds(0, 0, w, h);
            d.draw(cv);
            return bm;
        } catch (Throwable e) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    private void toast(String s2) { Toast.makeText(this, s2, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
