package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** 平台动态/开播监控管理页：目标增删 + 轮询间隔 + 启停。 */
public class DynaActivity extends Activity {
    private SharedPreferences sp;
    private LinearLayout listBox, favListBox;
    private TextView status;
    private EditText input, itvInput;
    private String curPlatform = "bili";
    private final List<TextView> itvChips = new ArrayList<>();
    private final List<TextView> pfChips = new ArrayList<>();
    private final android.os.Handler timer = new android.os.Handler();
    private Button toggle;
    private static final String[] PLATFORMS = {"bili", "douyin", "douyu", "huya", "weibo"};
    private static final String[] PLABELS = {"B站", "抖音", "斗鱼", "虎牙", "微博"};
    private static final String[] PHINTS = {"uid（space.bilibili.com/数字）", "直播间号（live.douyin.com/数字）",
            "房间号", "房间号（www.huya.com/数字）", "uid（weibo.com/u/数字）"};
    private static final int[] PLAT_COLORS = {0xFFFB7299, 0xFF161823, 0xFFFF5D23, 0xFFFF7700, 0xFFE6162D};
    private static final int[] ITVS = {1, 3, 5, 10};
    private String[] PFS = PLATFORMS, PFL = PLABELS, PFH = PHINTS;
    private int[] PFC = PLAT_COLORS;
    private boolean dark, liveMode;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(DynaService.SP, 0);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        liveMode = "live".equals(getIntent().getStringExtra("mode"));
        Immersive.hide(this);
        // 底部白边：导航栏/状态栏颜色随主题，暗色下纯黑
        getWindow().setNavigationBarColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        getWindow().setStatusBarColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false); // 解除系统强制导航栏对比度(否则暗色下被垫白)
        }
        // 窗口底色随主题，杜绝任何区域露出白底
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(dark ? 0xFF000000 : 0xFFEEF4FF));
        if (liveMode) {
            PFS = new String[]{"bili", "douyin", "douyu", "huya"};
            PFL = new String[]{"B站", "抖音", "斗鱼", "虎牙"};
            PFH = new String[]{PHINTS[0], PHINTS[1], PHINTS[2], PHINTS[3]};
            PFC = new int[]{PLAT_COLORS[0], PLAT_COLORS[1], PLAT_COLORS[2], PLAT_COLORS[3]};
        }
        int bg = dark ? 0xFF000000 : 0xFFEEF4FF;
        int fg = dark ? 0xFFFFFFFF : 0xFF1F2329;
        int sub = dark ? 0xFF9AA3AE : 0xFF8A94A6;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(18), dp(20), dp(18), dp(40));

        TextView title = new TextView(this);
        title.setText(liveMode ? "🔴 开播通知" : "📡 平台动态监控");
        title.setTextSize(22); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextColor(fg);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(13); status.setTextColor(sub);
        status.setPadding(0, dp(6), 0, dp(4));
        root.addView(status);

        TextView hint = new TextView(this);
        hint.setText(liveMode ? "检测开播状态，变化时发系统通知" : "检测动态更新与开播，变化时发系统通知");
        hint.setTextSize(11); hint.setTextColor(sub);
        root.addView(hint);

        // 查询间隔（一行，可自定义）
        LinearLayout itvRow = new LinearLayout(this);
        itvRow.setOrientation(LinearLayout.HORIZONTAL);
        itvRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView itvLab = new TextView(this);
        itvLab.setText("查询间隔："); itvLab.setTextSize(15); itvLab.setTextColor(fg);
        itvRow.addView(itvLab);
        itvInput = new EditText(this);
        itvInput.setTextSize(15); itvInput.setTextColor(fg);
        itvInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        itvInput.setSingleLine(true);
        itvInput.setText(String.valueOf(sp.getInt("interval", 3)));
        itvInput.setBackgroundResource(dark ? R.drawable.bg_input_dark : R.drawable.bg_input_flat);
        itvInput.setMinWidth(dp(56));
        itvInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable s) {
                try {
                    int v = Integer.parseInt(s.toString());
                    if (v < 1) v = 1; if (v > 120) v = 120;
                    if (v != sp.getInt("interval", 3)) sp.edit().putInt("interval", v).apply();
                } catch (Throwable ignored) { }
            }
        });
        itvRow.addView(itvInput);
        TextView itvUnit = new TextView(this);
        itvUnit.setText(" 分钟"); itvUnit.setTextSize(15); itvUnit.setTextColor(fg);
        itvRow.addView(itvUnit);
        root.addView(rowTop(itvRow, 10));

        // 监控目标（一行）
        LinearLayout pfRow = new LinearLayout(this);
        pfRow.setOrientation(LinearLayout.HORIZONTAL);
        pfRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView pfLab = new TextView(this);
        pfLab.setText("监控目标："); pfLab.setTextSize(15); pfLab.setTextColor(fg);
        pfRow.addView(pfLab);
        pfRow.addView(gap(2));
        for (int i = 0; i < PFS.length; i++) {
            final String p = PFS[i];
            TextView chip = new TextView(this);
            chip.setText(PFL[i]);
            chip.setTextSize(13);
            chip.setPadding(dp(12), dp(4), dp(12), dp(4));
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { curPlatform = p; refreshPlatformRow(); refreshInputHint(); }
            });
            pfChips.add(chip);
            pfRow.addView(chip);
            pfRow.addView(gap(6));
        }
        root.addView(rowTop(pfRow, 14));

        input = new EditText(this);
        input.setTextSize(14);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setTextColor(fg);
        input.setHintTextColor(sub);
        input.setBackgroundResource(dark ? R.drawable.bg_input_dark : R.drawable.bg_input_flat);
        root.addView(rowTop(input, 8));

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView tip = new TextView(this);
        tip.setTextSize(11); tip.setTextColor(sub);
        tip.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        addRow.addView(tip);
        Button addBtn = new Button(this);
        addBtn.setText("添加"); addBtn.setTextSize(13);
        addBtn.setBackgroundResource(dark ? R.drawable.bg_btn_md3_dark : R.drawable.bg_btn_md3);
        addBtn.setTextColor(Color.WHITE);
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { addTarget(); }
        });
        addRow.addView(addBtn);
        root.addView(rowTop(addRow, 8));

        // 目标列表 / 收藏列表：单列表 + 按钮切换（5行高度滚动框）
        final TextView tabMon = new TextView(this);
        final TextView tabFav = new TextView(this);
        tabMon.setText("监控列表"); tabFav.setText("收藏列表");
        tabMon.setTextSize(14); tabFav.setTextSize(14);
        tabMon.setPadding(dp(16), dp(6), dp(16), dp(6));
        tabFav.setPadding(dp(16), dp(6), dp(16), dp(6));
        tabMon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { switchList("m", tabMon, tabFav); }
        });
        tabFav.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { switchList("f", tabMon, tabFav); }
        });

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        favListBox = new LinearLayout(this);
        favListBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        listWrap.addView(listBox, new ViewGroup.LayoutParams(-1, -2));
        listWrap.addView(favListBox, new ViewGroup.LayoutParams(-1, -2));
        ScrollView listScroll = new ScrollView(this);
        listScroll.addView(listWrap, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setGravity(Gravity.CENTER_VERTICAL);
        tabsRow.addView(tabMon);
        tabsRow.addView(gap(8));
        tabsRow.addView(tabFav);
        LinearLayout listBlock = new LinearLayout(this);
        listBlock.setOrientation(LinearLayout.VERTICAL);
        listBlock.addView(tabsRow);
        listBlock.addView(listScroll, new ViewGroup.LayoutParams(-1, dp(310)));
        root.addView(rowTop(listBlock, 6));
        switchList("m", tabMon, tabFav);

        // 启停按钮 + 右侧说明小框
        toggle = new Button(this);
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        toggle.setTextSize(15);
        toggle.setTextColor(Color.WHITE);
        toggle.setBackgroundResource(dark ? R.drawable.bg_btn_md3_dark : R.drawable.bg_btn_md3);
        toggle.setPadding(0, dp(12), 0, dp(12));
        toggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean on = !DynaService.svcOn;
                sp.edit().putBoolean("on", on).apply();
                if (on) DynaService.ensure(DynaActivity.this);
                else stopService(new Intent(DynaActivity.this, DynaService.class));
                refreshStatus(); refreshToggle();
                // startForegroundService 是异步的，1秒后校正一次按钮状态
                timer.postDelayed(new Runnable() { public void run() { refreshStatus(); refreshToggle(); } }, 1200);
                Toast.makeText(DynaActivity.this, on ? "监控已启动" : "监控已停止", Toast.LENGTH_SHORT).show();
            }
        });
        toggleRow.addView(toggle);
        LinearLayout.LayoutParams tglp = (LinearLayout.LayoutParams) toggle.getLayoutParams();
        tglp.rightMargin = dp(10);
        toggle.setLayoutParams(tglp);
        TextView note = new TextView(this);
        note.setText("▶ 启动后按查询间隔自动轮询\n☆ 收藏的房间开播会显示在通知栏\n✕ 删除监控目标");
        note.setTextSize(11);
        note.setTextColor(dark ? 0xFF9AA3AE : 0xFF8A94A6);
        note.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
        note.setPadding(dp(10), dp(8), dp(10), dp(8));
        note.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.2f));
        toggleRow.addView(note);
        root.addView(rowTop(toggleRow, 18));

        ScrollView sc = new ScrollView(this);
        sc.addView(root);
        setContentView(sc);
        refreshAll();
    }

    private TextView subLabel(String s, int fg, int sub, int top) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(14); t.setTextColor(sub);
        t.setPadding(0, dp(top), 0, dp(4));
        return t;
    }

    private TextView chipItv(final int m) {
        TextView chip = new TextView(this);
        chip.setText(m + "分钟");
        chip.setTextSize(13);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        chip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sp.edit().putInt("interval", m).apply(); refreshIntervalRow(); refreshStatus(); }
        });
        return chip;
    }

    private View gap(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1));
        return v;
    }

    private LinearLayout rowTop(View child, int top) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        box.addView(child, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(box, lp);
        return wrap;
    }

    private void switchList(String which, TextView tabMon, TextView tabFav) {
        boolean mon = "m".equals(which);
        listBox.setVisibility(mon ? View.VISIBLE : View.GONE);
        favListBox.setVisibility(mon ? View.GONE : View.VISIBLE);
        tabMon.setBackgroundResource(mon ? R.drawable.bg_pill_active
                : (dark ? R.drawable.bg_pill_idle_dark : R.drawable.bg_pill_idle));
        tabFav.setBackgroundResource(!mon ? R.drawable.bg_pill_active
                : (dark ? R.drawable.bg_pill_idle_dark : R.drawable.bg_pill_idle));
        tabMon.setTextColor(mon ? 0xFFFFFFFF : (dark ? 0xFF9AA3AE : 0xFF8A94A6));
        tabFav.setTextColor(!mon ? 0xFFFFFFFF : (dark ? 0xFF9AA3AE : 0xFF8A94A6));
        if (mon) buildList(); else buildFavList();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Immersive.hide(this);
            // 焦点恢复后重申导航栏颜色，防系统改回
            getWindow().setNavigationBarColor(dark ? 0xFF000000 : 0xFFEEF4FF);
            if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAll();
        timer.postDelayed(tick, listRefreshMs());
    }

    @Override protected void onPause() {
        super.onPause();
        timer.removeCallbacks(tick);
    }

    /** 列表刷新周期跟随查询间隔(分钟), 最短5秒 */
    private long listRefreshMs() {
        return Math.max(5000L, sp.getInt("interval", 3) * 60L * 1000L);
    }

    private final Runnable tick = new Runnable() {
        public void run() { refreshStatus(); refreshToggle(); buildList(); buildFavList(); timer.postDelayed(this, listRefreshMs()); }
    };

    private void refreshAll() {
        refreshIntervalRow(); refreshPlatformRow(); refreshInputHint();
        refreshStatus(); refreshToggle(); buildList(); buildFavList();
    }

    private void refreshInputHint() {
        if (input == null) return;
        for (int j = 0; j < PFS.length; j++)
            if (PFS[j].equals(curPlatform)) input.setHint(PFH[j]);
    }

    private void refreshStatus() {
        JSONArray ts = DynaService.targets(sp);
        boolean on = DynaService.svcOn;
        status.setText(on ? "运行中 · " + ts.length() + " 个目标 · 每" + sp.getInt("interval", 3) + "分钟检测"
                : "已停止 · " + ts.length() + " 个目标");
    }

    private void refreshToggle() {
        if (toggle == null) return;
        boolean on = DynaService.svcOn;
        toggle.setText(on ? "⏹ 停止监控" : "▶ 启动监控");
    }

    private void refreshIntervalRow() {
        int cur = sp.getInt("interval", 3);
        for (int i = 0; i < itvChips.size(); i++) {
            int m = ITVS[i];
            TextView c = itvChips.get(i);
            boolean sel = m == cur;
            c.setBackgroundResource(sel ? R.drawable.bg_pill_active
                    : (dark ? R.drawable.bg_pill_idle_dark : R.drawable.bg_pill_idle));
            c.setTextColor(sel ? 0xFFFFFFFF : (dark ? 0xFF9AA3AE : 0xFF8A94A6));
        }
    }

    private void refreshPlatformRow() {
        for (int i = 0; i < pfChips.size(); i++) {
            boolean sel = PFS[i].equals(curPlatform);
            TextView c = pfChips.get(i);
            c.setBackgroundResource(sel ? R.drawable.bg_pill_active
                    : (dark ? R.drawable.bg_pill_idle_dark : R.drawable.bg_pill_idle));
            c.setTextColor(sel ? 0xFFFFFFFF : (dark ? 0xFF9AA3AE : 0xFF8A94A6));
        }
    }

    private void addTarget() {
        String id = input.getText().toString().trim()
                .replaceAll("^https?://[^/]+/", "").replaceAll("[^0-9A-Za-z_-]", "");
        if (id.isEmpty()) { Toast.makeText(this, "请输入ID", Toast.LENGTH_SHORT).show(); return; }
        JSONArray ts = DynaService.targets(sp);
        String myKind = liveMode ? "l" : "d";
        for (int i = 0; i < ts.length(); i++) {
            JSONObject t = ts.optJSONObject(i);
            if (t != null && id.equals(t.optString("id")) && curPlatform.equals(t.optString("p"))
                    && myKind.equals(t.optString("k", ""))) {
                Toast.makeText(this, "该目标已存在", Toast.LENGTH_SHORT).show(); return;
            }
        }
        try {
            JSONObject t = new JSONObject();
            t.put("p", curPlatform); t.put("id", id);
            if (liveMode) t.put("k", "l"); else t.put("k", "d");
            ts.put(t);
            sp.edit().putString("targets", ts.toString()).apply();
            input.setText("");
            refreshStatus(); buildList();
        } catch (Exception e) { }
    }

    private void removeTarget(int idx) {
        try {
            JSONArray ts = DynaService.targets(sp);
            JSONArray ns = new JSONArray();
            for (int i = 0; i < ts.length(); i++) if (i != idx) ns.put(ts.get(i));
            sp.edit().putString("targets", ns.toString()).apply();
            refreshStatus(); buildList();
        } catch (Exception e) { }
    }

    private void buildList() {
        listBox.removeAllViews();
        JSONArray ts = DynaService.targets(sp);
        int fg = dark ? 0xFFFFFFFF : 0xFF1F2329;
        int sub = dark ? 0xFF9AA3AE : 0xFF8A94A6;
        if (ts.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有目标，选平台后输入ID添加");
            empty.setTextSize(13); empty.setTextColor(sub);
            empty.setPadding(dp(4), dp(8), 0, dp(8));
            listBox.addView(empty);
            return;
        }
        for (int i = 0; i < ts.length(); i++) {
            final int idx = i;
            JSONObject t = ts.optJSONObject(i);
            if (t == null) continue;
            String tk = t.optString("k", "");
            if (liveMode ? !"l".equals(tk) : "l".equals(tk)) continue;
            String p = t.optString("p", ""), id = t.optString("id", "");
            String[] info = DynaService.infoOf(sp, p, id);
            String name = info != null && !info[0].isEmpty() ? info[0] : id;
            String label = p;
            for (int j = 0; j < PFS.length; j++) if (PFS[j].equals(p)) label = PFL[j];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
            row.setPadding(dp(14), dp(10), dp(10), dp(10));

            TextView tag = new TextView(this);
            int color = 0xFF666666;
            for (int j = 0; j < PFS.length; j++) if (PFS[j].equals(p)) color = PFC[j];
            tag.setText(label);
            tag.setTextSize(11); tag.setTextColor(Color.WHITE);
            tag.setBackgroundColor(color);
            tag.setPadding(dp(6), dp(3), dp(6), dp(3));
            row.addView(tag);

            TextView txt = new TextView(this);
            String disp = name;
            if (liveMode) {
                String s = info != null ? info[1] : "";
                if ("1".equals(s)) { disp += " · 开播中"; txt.setTextColor(0xFF34C759); }
                else if ("0".equals(s)) { disp += " · 未开播"; txt.setTextColor(fg); }
                else { disp += " · 检测中"; txt.setTextColor(fg); }
            } else {
                txt.setTextColor(fg);
            }
            txt.setText(disp);
            txt.setTextSize(14);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.leftMargin = dp(10);
            txt.setLayoutParams(tp);
            row.addView(txt);

            final String fp = p, fid = id;
            TextView fav = new TextView(this);
            fav.setText(isFav(fp, fid) ? "★" : "☆");
            fav.setTextSize(15);
            fav.setTextColor(isFav(fp, fid) ? 0xFFFFB300 : sub);
            fav.setPadding(dp(10), dp(4), dp(4), dp(4));
            fav.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toggleFav(fp, fid); }
            });
            row.addView(fav);

            TextView del = new TextView(this);
            del.setText("✕");
            del.setTextSize(15); del.setTextColor(sub);
            del.setPadding(dp(12), dp(4), dp(6), dp(4));
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { removeTarget(idx); }
            });
            row.addView(del);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            listBox.addView(row, lp);
        }
    }

    private boolean isFav(String p, String id) {
        JSONArray fs = DynaService.favs(sp);
        for (int i = 0; i < fs.length(); i++) {
            JSONObject f = fs.optJSONObject(i);
            if (f != null && p.equals(f.optString("p")) && id.equals(f.optString("id"))) return true;
        }
        return false;
    }

    private void toggleFav(String p, String id) {
        JSONArray fs = DynaService.favs(sp);
        JSONArray ns = new JSONArray();
        boolean had = false;
        for (int i = 0; i < fs.length(); i++) {
            JSONObject f = fs.optJSONObject(i);
            if (f != null && p.equals(f.optString("p")) && id.equals(f.optString("id"))) { had = true; continue; }
            ns.put(f);
        }
        if (!had) {
            try { JSONObject f = new JSONObject(); f.put("p", p); f.put("id", id); ns.put(f); } catch (Throwable ignored) {}
        }
        sp.edit().putString("fav", ns.toString()).apply();
        buildList(); buildFavList();
        Toast.makeText(this, had ? "已取消收藏" : "已收藏", Toast.LENGTH_SHORT).show();
    }

    private void buildFavList() {
        if (favListBox == null) return;
        int fg = dark ? 0xFFFFFFFF : 0xFF1F2329;
        int sub = dark ? 0xFF9AA3AE : 0xFF8A94A6;
        favListBox.removeAllViews();
        JSONArray fs = DynaService.favs(sp);
        if (fs.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("点行尾 ☆ 收藏，收藏房间开播会显示在通知栏");
            empty.setTextSize(12); empty.setTextColor(sub);
            empty.setPadding(dp(2), dp(8), 0, dp(8));
            favListBox.addView(empty);
            return;
        }
        for (int i = 0; i < fs.length(); i++) {
            final int idx = i;
            JSONObject f = fs.optJSONObject(i);
            if (f == null) continue;
            final String p = f.optString("p", ""), id = f.optString("id", "");
            String[] info = DynaService.infoOf(sp, p, id);
            String name = info != null && !info[0].isEmpty() ? info[0] : id;
            String label = p;
            for (int j = 0; j < PFS.length; j++) if (PFS[j].equals(p)) label = PFL[j];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
            row.setPadding(dp(10), dp(10), dp(6), dp(10));

            TextView tag = new TextView(this);
            int color = 0xFF666666;
            for (int j = 0; j < PFS.length; j++) if (PFS[j].equals(p)) color = PFC[j];
            tag.setText(label);
            tag.setTextSize(10); tag.setTextColor(Color.WHITE);
            tag.setBackgroundColor(color);
            tag.setPadding(dp(5), dp(2), dp(5), dp(2));
            row.addView(tag);

            TextView txt = new TextView(this);
            String s = info != null ? info[1] : "";
            String disp = name;
            if ("1".equals(s)) { disp += " · 开播中"; txt.setTextColor(0xFF34C759); }
            else if ("0".equals(s)) { disp += " · 未开播"; txt.setTextColor(fg); }
            else { disp += " · 检测中"; txt.setTextColor(fg); }
            txt.setText(disp);
            txt.setTextSize(12);
            txt.setSingleLine(true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            tp.leftMargin = dp(6);
            txt.setLayoutParams(tp);
            row.addView(txt);

            TextView del = new TextView(this);
            del.setText("✕");
            del.setTextSize(13); del.setTextColor(sub);
            del.setPadding(dp(8), dp(4), dp(2), dp(4));
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    JSONArray fs2 = DynaService.favs(sp);
                    JSONArray ns = new JSONArray();
                    for (int k = 0; k < fs2.length(); k++) if (k != idx) ns.put(fs2.opt(k));
                    sp.edit().putString("fav", ns.toString()).apply();
                    buildList(); buildFavList();
                }
            });
            row.addView(del);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            favListBox.addView(row, lp);
        }
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
