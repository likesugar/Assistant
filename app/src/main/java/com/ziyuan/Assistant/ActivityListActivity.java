package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 图1复刻：活动选择列表页 —— 深青顶栏(标题+共X个+搜索)，每个活动一张卡：白底圆角图标+应用名(青)+完整类名+右侧"详情"按钮 */
public class ActivityListActivity extends Activity {

    private String pkg;
    private String appLabel = "";
    private android.content.pm.PackageManager pm;
    private android.content.pm.ActivityInfo[] acts;
    private LinearLayout list;
    private TextView tvCount, tvSearch;
    private LinearLayout searchRow;
    private EditText etSearch;
    private String searchText = "";
    private final java.util.HashMap<String, Bitmap> iconCache = new java.util.HashMap<String, Bitmap>();

    // 配色（图1取色）
    private static final int BAR = 0xFF215870;
    private static final int NAME_TEAL = 0xFF1B6E86;
    private static final int CLS_TEAL = 0xFF16607A;
    private static final int BTN_BLUE = 0xFF4E86A8;
    private static final int CARD_BG = 0xFFEDF3FA;
    private static final int PAGE_BG = 0xFFDDE8F2;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        pm = getPackageManager();
        pkg = getIntent().getStringExtra("pkg");
        appLabel = getIntent().getStringExtra("name");
        if (appLabel == null || appLabel.length() == 0) appLabel = pkg;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE_BG);

        // ===== 顶栏：← 活动选择/共X个  🔍 =====
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(8), dp(14), dp(14), dp(14));
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(22);
        back.setPadding(dp(10), 0, dp(14), 0);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        bar.addView(back);
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("活动选择");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.getPaint().setFakeBoldText(true);
        tcol.addView(title);
        tvCount = new TextView(this);
        tvCount.setTextColor(0xFFD8E6EE);
        tvCount.setTextSize(14);
        tcol.addView(tvCount);
        bar.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        tvSearch = new TextView(this);
        tvSearch.setText("🔍");
        tvSearch.setTextSize(18);
        tvSearch.setPadding(dp(12), dp(6), dp(12), dp(6));
        tvSearch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            searchRow.setVisibility(searchRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }});
        bar.addView(tvSearch);
        root.addView(bar);

        // 搜索行（默认隐藏）
        searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setBackgroundColor(BAR);
        searchRow.setPadding(dp(16), 0, dp(16), dp(10));
        searchRow.setVisibility(View.GONE);
        etSearch = new EditText(this);
        etSearch.setHint("搜索活动名");
        etSearch.setHintTextColor(0xFF9DBECB);
        etSearch.setTextColor(0xFFFFFFFF);
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        etSearch.setBackground(null);
        searchRow.addView(etSearch, new LinearLayout.LayoutParams(-1, -2));
        root.addView(searchRow);
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void onTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void afterTextChanged(android.text.Editable e) { searchText = e.toString().trim().toLowerCase(); render(); }
        });

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(16));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        loadActs();
    }

    private void loadActs() {
        tvCount.setText("读取中…");
        list.removeAllViews();
        new Thread(new Runnable() { public void run() {
            android.content.pm.ActivityInfo[] arr = null;
            try { arr = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_ACTIVITIES).activities; }
            catch (Throwable t) { }
            acts = arr;
            runOnUiThread(new Runnable() { public void run() { render(); } });
        } }).start();
    }

    private void render() {
        list.removeAllViews();
        if (acts == null || acts.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("该包没有可列出的活动");
            empty.setTextColor(NAME_TEAL);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            list.addView(empty);
            tvCount.setText("共0个");
            return;
        }
        java.util.ArrayList<android.content.pm.ActivityInfo> shown = new java.util.ArrayList<android.content.pm.ActivityInfo>();
        for (android.content.pm.ActivityInfo a2 : acts) {
            if (searchText.length() > 0 && !a2.name.toLowerCase().contains(searchText)) continue;
            shown.add(a2);
        }
        tvCount.setText("共" + shown.size() + "个");
        for (int i = 0; i < shown.size(); i++) renderRow(shown.get(i), i);
    }

    private void renderRow(final android.content.pm.ActivityInfo ai, int i) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        card.setPadding(dp(10), dp(12), dp(10), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = i == 0 ? 0 : dp(10);
        card.setLayoutParams(clp);

        // 图标：白圆角方块底
        LinearLayout iwrap = new LinearLayout(this);
        iwrap.setGravity(Gravity.CENTER);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(0xFFFFFFFF);
        ibg.setCornerRadius(dp(12));
        iwrap.setBackground(ibg);
        final ImageView ic = new ImageView(this);
        ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap icb = iconCache.get(pkg);
        if (icb != null) ic.setImageBitmap(icb);
        else {
            ic.setBackgroundColor(0xFFFFFFFF);
            final String fg = pkg + "@" + i;
            ic.setTag(fg);
            new Thread(new Runnable() { public void run() {
                Bitmap bm = iconCache.get(pkg);
                if (bm == null) {
                    try {
                        android.graphics.drawable.Drawable d = pm.getApplicationIcon(pkg);
                        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
                        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
                        bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        Canvas cv = new Canvas(bm);
                        d.setBounds(0, 0, w, h);
                        d.draw(cv);
                        iconCache.put(pkg, bm);
                    } catch (Throwable t) { return; }
                }
                runOnUiThread(new Runnable() { public void run() { if (fg.equals(ic.getTag())) ic.setImageBitmap(iconCache.get(pkg)); }});
            } }).start();
        }
        iwrap.addView(ic, new LinearLayout.LayoutParams(dp(44), dp(44)));
        iwrap.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.addView(iwrap);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1f);
        mlp.leftMargin = dp(12);
        mid.setLayoutParams(mlp);
        TextView nm = new TextView(this);
        nm.setText(appLabel);
        nm.setTextColor(NAME_TEAL);
        nm.setTextSize(16);
        nm.getPaint().setFakeBoldText(true);
        mid.addView(nm);
        TextView cls = new TextView(this);
        cls.setText(ai.name);
        cls.setTextColor(CLS_TEAL);
        cls.setTextSize(15);
        cls.getPaint().setFakeBoldText(true);
        cls.setMaxLines(3);
        mid.addView(cls);
        card.addView(mid);

        TextView detail = new TextView(this);
        detail.setText("详情");
        detail.setTextColor(0xFFCFE3F5);
        detail.setTextSize(15);
        detail.setGravity(Gravity.CENTER);
        GradientDrawable dbg = new GradientDrawable();
        dbg.setColor(BTN_BLUE);
        dbg.setCornerRadius(dp(6));
        detail.setBackground(dbg);
        detail.setPadding(dp(18), dp(14), dp(18), dp(14));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = dp(10);
        detail.setLayoutParams(dlp);
        detail.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finishWith(ai.name); } });
        card.addView(detail);

        list.addView(card);
    }

    @Override
    public void onBackPressed() { finish(); }

    private void finishWith(String cls) {
        if (getIntent().getBooleanExtra("fromEdit", false)) {
            // 图2返回到图3后, 详情 → 重新进入编辑页(带所选活动)
            Intent i = new Intent(this, ShortcutEditActivity.class);
            i.putExtra("e0", appLabel);
            i.putExtra("e1", pkg);
            i.putExtra("e2", cls);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
            return;
        }
        Intent out = new Intent();
        out.putExtra("pkg", pkg);
        out.putExtra("cls", cls);
        out.putExtra("name", appLabel);
        setResult(RESULT_OK, out);
        finish();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
