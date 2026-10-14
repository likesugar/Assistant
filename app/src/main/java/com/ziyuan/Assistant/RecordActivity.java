package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 录制下载页：一列任务，右侧 ⋮ 弹菜单（开始/播放/结束录制/取消） */
public class RecordActivity extends Activity {

    private Handler handler;
    private Runnable tick;
    private LinearLayout list;
    private TextView tvEmpty;
    private FrameLayout root;
    private LinearLayout tabBar;
    private LinearLayout col;
    private TextView[] tabBtns;
    private View[] tabLines;
    private int curTab = 0;  // 0全部 1视频 2录制 3下载
    private java.util.ArrayList<HistoryStore.Item> hist;

    private void placeSegBar() {
        LinearLayout seg = tabBar;
        if (seg.getParent() == root) root.removeView(seg);
        if (seg.getParent() == col) col.removeView(seg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.leftMargin = dp2(14); lp.rightMargin = dp2(14); lp.bottomMargin = dp2(18);
        root.addView(seg, lp); // 永远钉在屏幕底部
    }

    private void restyleTabs() {
        for (int i = 0; i < tabBtns.length; i++) {
            boolean sel = i == curTab;
            tabBtns[i].setTextColor(sel ? Color.WHITE : Theme.sub(this));
            tabBtns[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabBtns[i].setBackgroundResource(sel ? R.drawable.bg_seg_sel : 0);
            tabLines[i].setBackgroundColor(0x00000000);
        }
    }

    private int dp2(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String dlInfo(DlManager.DlJob j) {
        String sz = fmtSize(j.doneBytes) + (j.total > 0 && j.total > j.doneBytes ? " / " + fmtSize(j.total) : "");
        return "下载大小: " + sz + "\n状态: " + j.state;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DlManager.init(this);
        try { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION); } catch (Throwable ignored) {}
        hist = HistoryStore.load(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg(this));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(24, 40, 24, 24);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, 20);
        TextView title = new TextView(this);
        title.setText("下载");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Theme.text(this));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, -2);
        hlp.weight = 1;
        title.setLayoutParams(hlp);
        head.addView(title);
        TextView recNow = new TextView(this);
        recNow.setText("📋 读取剪贴板录制");
        recNow.setTextColor(Theme.dark(this) ? 0xFF9FC2FF : Theme.accent());
        recNow.setTextSize(14);
        recNow.setPadding(20, 12, 20, 12);
        recNow.setBackgroundColor(Theme.dark(this) ? 0xFF1A2C46 : 0xFFE1EAFB);
        recNow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String u = null;
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemAt(0) != null
                            && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                        u = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                    }
                } catch (Throwable e) { }
                if (u == null || !u.startsWith("http")) {
                    Toast.makeText(RecordActivity.this, "剪贴板里没有有效链接", Toast.LENGTH_SHORT).show();
                    return;
                }
                RecManager.startRecJob(u, u);
                rebuild();
                Toast.makeText(RecordActivity.this, "已开始录制", Toast.LENGTH_SHORT).show();
            }
        });
        head.addView(recNow);
        col.addView(head);

        // MD3 胶囊顶栏：全部/视频/录制/下载
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundResource(Theme.segBarRes(this));
        tabBar.setPadding(dp2(4), dp2(4), dp2(4), dp2(4));
        final String[] tabs = {"全部", "视频", "录制", "下载"};
        tabBtns = new TextView[tabs.length];
        tabLines = new View[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            LinearLayout tc = new LinearLayout(this);
            tc.setOrientation(LinearLayout.VERTICAL);
            tc.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tc.setLayoutParams(tlp);
            tc.setPadding(dp2(2), 0, dp2(2), 0);
            TextView tb = new TextView(this);
            tb.setText(tabs[i]);
            tb.setTextSize(14);
            tb.setGravity(Gravity.CENTER);
            tb.setTypeface(Typeface.DEFAULT_BOLD);
            tb.setBackgroundResource(R.drawable.bg_seg_sel);
            tb.setPadding(0, dp2(10), 0, dp2(10));
            tb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { curTab = idx; restyleTabs(); rebuild(); }
            });
            View line = new View(this);
            line.setBackgroundColor(0x00000000);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.setMargins(dp2(2), 0, dp2(2), 0);
            tc.addView(tb, blp);
            tc.addView(line, new LinearLayout.LayoutParams(-1, 1));
            tabBtns[i] = tb;
            tabLines[i] = line;
            tabBar.addView(tc);
        }
        restyleTabs();
        placeSegBar();

        tvEmpty = new TextView(this);
        tvEmpty.setText("没有数据(^▽^)");
        tvEmpty.setTextColor(Theme.sub(this));
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, 120, 0, 0);
        col.addView(tvEmpty);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        // 丝滑过渡: 行增删淡入淡出+位移, 行位变化平滑移动
        android.animation.LayoutTransition lt = new android.animation.LayoutTransition();
        lt.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        list.setLayoutTransition(lt);
        col.addView(list);

        sv.addView(col);
        root.addView(sv);
        placeSegBar(); // 必须在 sv 之后加入, 保证胶囊栏浮在滚动层之上可点击
        setContentView(root);
        Immersive.hide(this);

        handler = new Handler();
        tick = new Runnable() {
            public void run() {
                rebuild();
                handler.postDelayed(this, 500);
            }
        };
    }

    @Override
    protected void onResume() { super.onResume(); handler.post(tick); }

    @Override
    protected void onPause() { super.onPause(); handler.removeCallbacks(tick); }

    private static String fmtDur(long s) {
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private static String fmtSize(long b) {
        if (b >= 1048576) return String.format(java.util.Locale.US, "%.2fMB", b / 1048576.0);
        return String.format(java.util.Locale.US, "%.0fKB", b / 1024.0);
    }

    private final java.util.ArrayList<String> rowOrder = new java.util.ArrayList<String>();  // 按任务id固定行位置
    private final java.util.HashMap<String, TextView> rowInfo = new java.util.HashMap<String, TextView>();

    private void rebuild() {
        // 每次重建前刷新历史记录, 合并完成的文件立即可见(免重进)
        try { hist = HistoryStore.load(this); } catch (Throwable ignored) {}
        // key -> [type(0录制/1下载), job, live]
        final java.util.LinkedHashMap<String, Object[]> meta = new java.util.LinkedHashMap<String, Object[]>();
        for (RecManager.RecJob j : RecManager.recJobs.values()) meta.put("R" + j.id, new Object[]{0, j, (Boolean) j.active});
        for (RecManager.RecJob j : RecManager.stoppedJobs.values()) {
            if (j.savedToHistory) continue;   // 已入历史记录，卡片去重（避免两条相同）
            if (!meta.containsKey("R" + j.id)) meta.put("R" + j.id, new Object[]{0, j, Boolean.FALSE});
        }
        for (DlManager.DlJob j : DlManager.jobs()) meta.put("D" + j.id, new Object[]{1, j, Boolean.FALSE});
        if (hist != null) for (int i = 0; i < hist.size(); i++) meta.put("H" + i, new Object[]{2, hist.get(i), Boolean.FALSE});

        // 分类：录制中/下载中 → 下载；录制完成/暂停 → 录制；下载完成 → 视频
        java.util.HashMap<String, String> cat = new java.util.HashMap<String, String>();
        for (java.util.Map.Entry<String, Object[]> e : meta.entrySet()) {
            int type = (Integer) e.getValue()[0];
            boolean live = (Boolean) e.getValue()[2];
            if (type == 2) { cat.put(e.getKey(), ((HistoryStore.Item) e.getValue()[1]).type); continue; }
            if (type == 1) {
                DlManager.DlJob dj = (DlManager.DlJob) e.getValue()[1];
                cat.put(e.getKey(), (dj.active || dj.paused) ? "下载" : "视频");
            } else {
                cat.put(e.getKey(), live ? "下载" : "录制");
            }
        }
        String want = new String[]{"全部", "视频", "录制", "下载"}[curTab];

        // 稳定顺序
        java.util.ArrayList<String> order = new java.util.ArrayList<String>(rowOrder);
        order.retainAll(meta.keySet());
        for (String k : meta.keySet()) if (!order.contains(k)) order.add(k);
        rowOrder.clear(); rowOrder.addAll(order);

        // 当前tab要显示的
        java.util.ArrayList<String> shown = new java.util.ArrayList<String>();
        for (String k : order) if (want.equals("全部") || want.equals(cat.get(k))) shown.add(k);
        tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);

        // 已显示行 == 当前应显示行 → 只刷文字
        java.util.ArrayList<String> curRows = new java.util.ArrayList<String>();
        for (int i = 0; i < list.getChildCount(); i++) {
            Object t = list.getChildAt(i).getTag();
            if (t instanceof String) curRows.add((String) t);
        }
        if (curRows.equals(shown)) {
            for (String key : shown) {
                Runnable u = rowUpdaters.get(key);
                if (u != null) u.run();
            }
            return;
        }
        list.removeAllViews();
        rowUpdaters.clear();
        for (String key : shown) {
            Object[] m = meta.get(key);
            java.util.concurrent.atomic.AtomicReference<TextView> infoRef = new java.util.concurrent.atomic.AtomicReference<TextView>();
            LinearLayout row;
            if ((Integer) m[0] == 0) {
                RecManager.RecJob j = (RecManager.RecJob) m[1];
                final boolean live = (Boolean) m[2];
                addRow(list, j, live, infoRef);
                row = (LinearLayout) list.getChildAt(list.getChildCount() - 1);
                final RecManager.RecJob fj = j;
                rowUpdaters.put(key, new Runnable() { public void run() { if (infoRef.get() != null) infoRef.get().setText(buildInfo(fj, fj.active)); } });
            } else if ((Integer) m[0] == 1) {
                final DlManager.DlJob j = (DlManager.DlJob) m[1];
                addDlRow(list, j, infoRef);
                row = (LinearLayout) list.getChildAt(list.getChildCount() - 1);
                final TextView infoDl = infoRef.get();
                rowUpdaters.put(key, new Runnable() { public void run() { if (infoDl != null) infoDl.setText(dlInfo(j)); } });
            } else {
                final HistoryStore.Item it = (HistoryStore.Item) m[1];
                final int hidx = Integer.parseInt(key.substring(1));
                addHistRow(list, it, hidx, infoRef);
                row = (LinearLayout) list.getChildAt(list.getChildCount() - 1);
            }
            row.setTag(key);
        }
    }

    private final java.util.HashMap<String, Runnable> rowUpdaters = new java.util.HashMap<String, Runnable>();

    private void addDlRow(LinearLayout parent, final DlManager.DlJob j, final java.util.concurrent.atomic.AtomicReference<TextView> infoRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(Theme.rowRes(this));
        row.setPadding(dp2(12), dp2(14), dp2(12), dp2(14));
        LinearLayout.LayoutParams rowLp0 = (LinearLayout.LayoutParams) row.getLayoutParams();
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(dp2(4), dp2(6), dp2(4), dp2(6));
        row.setLayoutParams(rowLp);
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(140, 90);
        tp.rightMargin = 24;
        thumb.setLayoutParams(tp);
        thumb.setBackgroundColor(Theme.dark(this) ? 0xFF161B26 : 0xFFE7EDF8);
        TextView play = new TextView(this);
        play.setText("⇣");
        play.setTextColor(Theme.sub(this));
        play.setGravity(Gravity.CENTER);
        thumb.addView(play, new FrameLayout.LayoutParams(-1, -1));
        row.addView(thumb);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2);
        mp.weight = 1;
        mid.setLayoutParams(mp);
        TextView tvName = new TextView(this);
        tvName.setText(j.title);
        tvName.setTextColor(Theme.text(this));
        tvName.setTextSize(16);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        mid.addView(tvName);
        TextView tvInfo = new TextView(this);
        tvInfo.setText(dlInfo(j));
        tvInfo.setTextColor(Theme.sub(this));
        tvInfo.setTextSize(13);
        tvInfo.setLineSpacing(4, 1);
        mid.addView(tvInfo);
        row.addView(mid);
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Theme.sub(this));
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showDlMenu(v, j); }
        });
        row.addView(more);
        parent.addView(row);
        infoRef.set(tvInfo);
    }

    private void addHistRow(LinearLayout parent, final HistoryStore.Item it, final int idx, final java.util.concurrent.atomic.AtomicReference<TextView> infoRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(Theme.rowRes(this));
        row.setPadding(dp2(12), dp2(14), dp2(12), dp2(14));
        LinearLayout.LayoutParams rowLp0 = (LinearLayout.LayoutParams) row.getLayoutParams();
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(dp2(4), dp2(6), dp2(4), dp2(6));
        row.setLayoutParams(rowLp);
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(140, 90);
        tp.rightMargin = 24;
        thumb.setLayoutParams(tp);
        thumb.setBackgroundColor(Theme.dark(this) ? 0xFF161B26 : 0xFFE7EDF8);
        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(Theme.sub(this));
        play.setGravity(Gravity.CENTER);
        thumb.addView(play, new FrameLayout.LayoutParams(-1, -1));
        row.addView(thumb);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2);
        mp.weight = 1;
        mid.setLayoutParams(mp);
        TextView tvName = new TextView(this);
        tvName.setText(it.title);
        tvName.setTextColor(Theme.text(this));
        tvName.setTextSize(16);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        mid.addView(tvName);
        TextView tvInfo = new TextView(this);
        tvInfo.setText("类型: " + it.type + "\n状态: 已完成");
        tvInfo.setTextColor(Theme.sub(this));
        tvInfo.setTextSize(13);
        tvInfo.setLineSpacing(4, 1);
        mid.addView(tvInfo);
        row.addView(mid);
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Theme.sub(this));
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PopupMenu pm = new PopupMenu(RecordActivity.this, v);
                pm.getMenu().add("播放");
                pm.getMenu().add("删除");
                pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(android.view.MenuItem m2) {
                        String t = m2.getTitle().toString();
                        if (t.equals("播放")) {
                            try { playInApp(it.path, it.title); } catch (Throwable e) { Toast.makeText(RecordActivity.this, "打不开", Toast.LENGTH_SHORT).show(); }
                        } else if (t.equals("删除")) {
                            HistoryStore.removeAt(RecordActivity.this, idx);
                            hist = HistoryStore.load(RecordActivity.this);
                            rowOrder.clear();
                            rebuild();
                        }
                        return true;
                    }
                });
                pm.show();
            }
        });
        row.addView(more);
        parent.addView(row);
        infoRef.set(tvInfo);
    }

    private void playInApp(String path, String title) {
        Intent i = new Intent(this, NativePlayerActivity.class);
        i.putExtra("url", path);
        i.putExtra("title", title == null ? "播放" : title);
        i.putExtra("kernel", "native");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void showDlMenu(View anchor, final DlManager.DlJob j) {
        PopupMenu pm = new PopupMenu(this, anchor);
        if (j.hls) {
            if (j.active) {
                pm.getMenu().add("暂停");
                pm.getMenu().add("结束(合并MP4)");
            } else if (j.paused) {
                pm.getMenu().add("继续录制");
                pm.getMenu().add("结束(合并MP4)");
            } else if (j.done) {
                pm.getMenu().add("播放");
            }
        } else {
            if (j.active) pm.getMenu().add("取消");
            if (j.done) pm.getMenu().add("播放");
        }
        pm.getMenu().add("删除");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) {
                String t = it.getTitle().toString();
                if (t.equals("暂停")) DlManager.pauseHls(j.id);
                else if (t.equals("开始")) DlManager.resumeHls(j.id);
                else if (t.equals("结束(合并MP4)")) DlManager.finishHls(j.id);
                else if (t.equals("取消")) DlManager.cancel(j.id);
                else if (t.equals("播放")) {
                    String pth = "file://" + j.file.getAbsolutePath();
                    if (j.hls && j.done) {
                        // done HLS 实体在相册，取历史最新一条播放
                        java.util.ArrayList<HistoryStore.Item> hh = HistoryStore.load(RecordActivity.this);
                        if (!hh.isEmpty()) pth = hh.get(0).path;
                    }
                    try { playInApp(pth, j.title); } catch (Throwable e) { Toast.makeText(RecordActivity.this, "打不开", Toast.LENGTH_SHORT).show(); }
                } else if (t.equals("删除")) { DlManager.cancel(j.id); }
                rebuild();
                return true;
            }
        });
        pm.show();
    }

    // 按抖音流地址后缀标注画质：优先原画 > 蓝光 > 高清，其余不标
    private static String qualify(RecManager.RecJob j) {
        String u = (j.url != null ? j.url : "") + " " + (j.name != null ? j.name : "");
        String q = null;
        if (u.contains("_or4")) q = "原画";
        else if (u.contains("_uhd")) q = "蓝光";
        else if (u.contains("_hd")) q = "高清";
        return q == null ? j.name : (u.contains("douyin") ? "抖音·" : "") + q + " " + j.name;
    }

    private String buildInfo(RecManager.RecJob j, boolean live) {
        long secs = j.secs + (live && j.startTs > 0 ? (System.currentTimeMillis() - j.startTs) / 1000 : 0);
        return "录制时长: " + fmtDur(secs)
            + "\n录制大小: " + fmtSize(j.bytes)
            + "\n录制状态: " + (live ? "录制中" : (j.state != null ? j.state : "暂停录制"));
    }

    private void addRow(LinearLayout parent, final RecManager.RecJob j, final boolean live, final java.util.concurrent.atomic.AtomicReference<TextView> infoRef) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(Theme.rowRes(this));
        row.setPadding(dp2(12), dp2(14), dp2(12), dp2(14));
        LinearLayout.LayoutParams rowLp0 = (LinearLayout.LayoutParams) row.getLayoutParams();
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(dp2(4), dp2(6), dp2(4), dp2(6));
        row.setLayoutParams(rowLp);

        // 左侧占位缩略块（▶）
        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(140, 90);
        tp.rightMargin = 24;
        thumb.setLayoutParams(tp);
        thumb.setBackgroundColor(Theme.dark(this) ? 0xFF161B26 : 0xFFE7EDF8);
        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(Theme.sub(this));
        play.setGravity(Gravity.CENTER);
        thumb.addView(play, new FrameLayout.LayoutParams(-1, -1));
        row.addView(thumb);

        // 中间信息
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2);
        mp.weight = 1;
        mid.setLayoutParams(mp);

        TextView tvName = new TextView(this);
        tvName.setText(qualify(j));
        tvName.setTextColor(Theme.text(this));
        tvName.setTextSize(16);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        mid.addView(tvName);

        TextView tvInfo = new TextView(this);
        tvInfo.setText(buildInfo(j, live));
        if (infoRef != null) infoRef.set(tvInfo);
        tvInfo.setTextColor(Theme.sub(this));
        tvInfo.setTextSize(13);
        tvInfo.setLineSpacing(4, 1);
        mid.addView(tvInfo);
        row.addView(mid);

        // 右侧 ⋮ 菜单
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Theme.sub(this));
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RecManager.RecJob cur = RecManager.recJobs.get(j.id);
                boolean nowLive = cur != null && cur.active;  // 点击时解析，避免行复用后菜单过期
                showMenu(v, cur != null ? cur : j, nowLive);
            }
        });
        row.addView(more);

        parent.addView(row);
    }

    private void showMenu(View anchor, RecManager.RecJob j, boolean live) {
        PopupMenu pm = new PopupMenu(this, anchor);
        if (live) {
            pm.getMenu().add("暂停").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(android.view.MenuItem it) { RecManager.recStop(j.id); rebuild(); return true; }
            });
        } else {
            pm.getMenu().add("打开").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(android.view.MenuItem it) { play(j); return true; }
            });
            pm.getMenu().add("继续录制").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
                public boolean onMenuItemClick(android.view.MenuItem it) { RecManager.recContinue(j.id); rebuild(); return true; }
            });
        }
        pm.getMenu().add("结束录制(转MP4)").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) {
                RecManager.recFinish(j.id);
                rebuild();
                return true;
            }
        });
        pm.getMenu().add("复制下载地址").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", j.url != null ? j.url : ""));
                    Toast.makeText(RecordActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) { Toast.makeText(RecordActivity.this, "复制失败", Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        pm.getMenu().add("打开所在目录").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) { openDir(); return true; }
        });
        pm.getMenu().add("播放视频").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) { playWithCopy(j); return true; }
        });
        pm.getMenu().add("取消").setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it) { RecManager.recCancel(j.id); rebuild(); return true; }
        });
        pm.show();
    }

    /** 复制下载地址 + 内置播放器播放（优先流地址，无则播最新合并成片） */
    private void playWithCopy(RecManager.RecJob j) {
        String addr = (j != null && j.url != null && !j.url.isEmpty()) ? j.url : null;
        if (addr == null) {
            java.io.File f = latestMerged();
            if (f != null) addr = f.getAbsolutePath();
        }
        if (addr == null) { Toast.makeText(this, "暂无视频", Toast.LENGTH_SHORT).show(); return; }
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("dl", addr));
            Toast.makeText(this, "下载地址已复制", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}
        playInApp(addr, j != null && j.name != null ? j.name : "播放");
    }

    private java.io.File latestMerged() {
        try {
            java.io.File root = new java.io.File(getExternalFilesDir(null), "录制合并");
            java.io.File best = null;
            java.util.ArrayDeque<java.io.File> stack = new java.util.ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                java.io.File d = stack.pop();
                java.io.File[] fs = d.listFiles();
                if (fs == null) continue;
                for (java.io.File f : fs) {
                    if (f.isDirectory()) stack.push(f);
                    else if (f.getName().toLowerCase().endsWith(".mp4")
                            && (best == null || f.lastModified() > best.lastModified())) best = f;
                }
            }
            return best;
        } catch (Throwable e) { return null; }
    }

    private void openDir() {
        // 1) MT 管理器 OpenFileActivity（data+type 都给）
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setClassName("bin.mt.plus", "bin.mt.plus.OpenFileActivity");
            i.setDataAndType(android.net.Uri.parse("file:///storage/emulated/0/Movies/录制"), "resource/folder");
            i.putExtra("path", "/storage/emulated/0/Movies/录制");
            i.putExtra("com.bin.mt.plus.path", "/storage/emulated/0/Movies/录制");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}
        // 2) MT MainLightIcon
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setClassName("bin.mt.plus", "bin.mt.plus.MainLightIcon");
            i.setDataAndType(android.net.Uri.parse("file:///storage/emulated/0/Movies/录制"), "resource/folder");
            i.putExtra("path", "/storage/emulated/0/Movies/录制");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}
        // 3) 系统目录选择器，直接定位到 Movies/录制
        try {
            android.net.Uri dir = android.net.Uri.parse(
                "content://com.android.externalstorage.documents/document/primary:Movies/录制");
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, dir);
            startActivity(i);
            return;
        } catch (Throwable ignored) {}
        // 兜底：拉起 MT 主界面
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("bin.mt.plus");
            if (i != null) { startActivity(i); Toast.makeText(this, "进 Movies/录制 目录", Toast.LENGTH_LONG).show(); return; }
        } catch (Throwable ignored) {}
        Toast.makeText(this, "请到 Movies/录制 目录查看", Toast.LENGTH_LONG).show();
    }

    private void play(RecManager.RecJob j) {
        try {
            if (j.storeUri == null) { Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show(); return; }
            Intent it = new Intent(this, HomeActivity.class);
            it.putExtra("autoUrl", j.storeUri.toString());
            startActivity(it);
        } catch (Throwable t) {
            Toast.makeText(this, "打开失败: " + t, Toast.LENGTH_SHORT).show();
        }
    }
}
