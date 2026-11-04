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
    private ScrollView sv;
    private LinearLayout tabBar;
    private LinearLayout col;
    private TextView[] tabBtns;
    private View[] tabLines;
    private int curTab = 0;  // 0全部 1视频 2录制 3下载
    private java.util.ArrayList<HistoryStore.Item> hist;
    private int lastHistCount = -1;

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

    private static String dlSizeStr(DlManager.DlJob j) {
        if (j.m3u8) return fmtSize(j.doneBytes);   // total 是分片数(非字节), 进度已在 state 里以 "下载中 12/120" 呈现
        return fmtSize(j.doneBytes) + (j.total > 0 && j.total > j.doneBytes ? " / " + fmtSize(j.total) : "");
    }

    private static String dlInfo(DlManager.DlJob j) {
        return "下载大小: " + dlSizeStr(j) + "\n状态: " + j.state;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DlManager.init(this);
        try { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION); } catch (Throwable ignored) {}
        hist = HistoryStore.load(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg(this));

        ScrollView sv2 = new ScrollView(this);
        sv2.setFillViewport(true);
        sv = sv2;
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // 底部留出悬浮胶囊栏的高度(≈44dp)+边距, 最后一张卡片不再被挡
        col.setPadding(24, 40, 24, dp2(80));

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
                RecManager.startRecJob(u, null);
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
                handler.postDelayed(this, nextDelay());
            }
        };
    }

    /**
     * 自适应轮询间隔:
     *  - 有任务正在跑(下载中/录制中): 1s  —— 速度与进度(含 mm:ss 计时)既实时又不抖
     *  - 只有暂停/等待中的任务:        3s  —— 状态不再变化, 慢速兜底
     *  - 全部空闲(仅历史/已完成):      5s  —— 几乎只做一次无变化的 diff, 省电省 CPU
     */
    private long nextDelay() {
        boolean live = false, waiting = false, merging = false;
        for (RecManager.RecJob j : RecManager.recJobs.values()) {
            if (j.active) { live = true; break; }
            if (j.state != null && j.state.startsWith("合并视频")) merging = true;
        }
        if (!live) {
            for (DlManager.DlJob j : DlManager.jobs()) {
                if (j.active) { live = true; break; }
                if (j.paused) waiting = true;
            }
        }
        if (live) return 1000;
        if (merging) return 500;     // 合并阶段快刷, 才能看到 0→100 的推进
        if (waiting) return 3000;
        return 5000;
    }

    @Override
    protected void onResume() { super.onResume(); handler.post(tick); }

    @Override
    protected void onPause() { super.onPause(); handler.removeCallbacks(tick); }

    @Override
    protected void onDestroy() { super.onDestroy(); THUMB_REQ.clear(); THUMB_TRY.clear(); }

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
        // 历史按需刷新: 仅条目数变化时重新解析(避免每轮全量 load 卡顿)
        int hc = HistoryStore.count(this);
        if (hc != lastHistCount) { try { hist = HistoryStore.load(this); } catch (Throwable ignored) {} lastHistCount = hc; }
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

        // 已显示行 == 当前应显示行 → 只原地刷文字(无增删零开销)
        java.util.ArrayList<String> curKeys = new java.util.ArrayList<String>();
        for (int i = 0; i < list.getChildCount(); i++) {
            Object t = list.getChildAt(i).getTag();
            if (t instanceof String) curKeys.add((String) t);
        }
        if (curKeys.equals(shown)) {
            for (String key : shown) {
                Runnable u = rowUpdaters.get(key);
                if (u != null) u.run();
            }
            return;
        }
        // 行集合变化 → 增量 diff: 复用已有行视图, 只删掉消失的、新建出现的、其余挪位。
        // 不再整列 removeAllViews 重建——滚动位置不丢、无闪烁, LayoutTransition 平滑过渡
        final int sy = sv.getScrollY();
        java.util.HashMap<String, View> byKey = new java.util.HashMap<String, View>();
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            Object t = v.getTag();
            if (t instanceof String) byKey.put((String) t, v);
        }
        for (String k : curKeys) {
            if (!shown.contains(k)) { list.removeView(byKey.get(k)); rowUpdaters.remove(k); }
        }
        for (int i = 0; i < shown.size(); i++) {
            String key = shown.get(i);
            View cur = list.getChildAt(i);
            View v = byKey.get(key);
            if (v == null) { v = makeRow(key, meta.get(key)); byKey.put(key, v); }
            if (v != cur) {
                // Android 的 addView 不接受已有 parent 的子视图: 同列挪位/新建追加后
                // 插入前先从原位置摘下, 再插到目标位置
                if (v.getParent() != null) list.removeView(v);
                list.addView(v, i);
            }
            Runnable u = rowUpdaters.get(key);
            if (u != null) u.run();
        }
        if (sv.getScrollY() != sy) sv.post(new Runnable() { public void run() { sv.scrollTo(0, sy); } });
    }

    /** 创建一行并追加到列尾, 登记其原地刷新器(标题+信息都跟随任务状态更新); 返回该行 */
    private View makeRow(String key, Object[] m) {
        java.util.concurrent.atomic.AtomicReference<TextView> infoRef = new java.util.concurrent.atomic.AtomicReference<TextView>();
        View row;
        if ((Integer) m[0] == 0) {
            RecManager.RecJob j = (RecManager.RecJob) m[1];
            addRow(list, j, (Boolean) m[2], infoRef);
            row = list.getChildAt(list.getChildCount() - 1);
            final RecManager.RecJob fj = j;
            final TextView nameTv = row.findViewWithTag("rowName");
            final TextView durTv = row.findViewWithTag("rowDur");
            final TextView sizeTv = row.findViewWithTag("rowSize");
            final TextView stateTv = row.findViewWithTag("rowState");
            final FrameLayout thumbBox = row.findViewWithTag("thumbBox");
            rowUpdaters.put(key, new Runnable() { public void run() {
                boolean lv = fj.active;   // 行复用后实时读状态, 不用创建时的快照
                setIfDiff(nameTv, qualify(fj));
                setIfDiff(durTv, "录制时长: " + durStr(fj, lv));
                setIfDiff(sizeTv, "录制大小: " + sizeStr(fj, lv));
                setIfDiff(stateTv, "录制状态: " + stateStr(fj, lv));
                refreshThumb(thumbBox, videoPathIn(fj.file));   // 首片落地/合并成MP4自动刷新
            } });
        } else if ((Integer) m[0] == 1) {
            final DlManager.DlJob j = (DlManager.DlJob) m[1];
            addDlRow(list, j, infoRef);
            row = list.getChildAt(list.getChildCount() - 1);
            final TextView sizeTv = row.findViewWithTag("rowSize");
            final TextView stateTv = row.findViewWithTag("rowState");
            final FrameLayout thumbBox = row.findViewWithTag("thumbBox");
            rowUpdaters.put(key, new Runnable() { public void run() {
                setIfDiff(sizeTv, "下载大小: " + dlSizeStr(j));
                setIfDiff(stateTv, "状态: " + j.state);
                refreshThumb(thumbBox, videoPathIn(j.file));
            } });
        } else {
            final HistoryStore.Item it = (HistoryStore.Item) m[1];
            final int hidx = Integer.parseInt(key.substring(1));
            addHistRow(list, it, hidx, infoRef);
            row = list.getChildAt(list.getChildCount() - 1);
            final TextView typeTv = row.findViewWithTag("rowType");
            final TextView stateTv = row.findViewWithTag("rowState");
            final TextView nameH = row.findViewWithTag("rowName");
            final FrameLayout thumbBox = row.findViewWithTag("thumbBox");
            rowUpdaters.put(key, new Runnable() { public void run() {
                // 行复用时按当前索引重取历史条目, 避免删除后错位显示旧内容
                if (hist == null || hidx >= hist.size()) return;
                HistoryStore.Item cur = hist.get(hidx);
                setIfDiff(nameH, cur.title);
                setIfDiff(typeTv, "类型: " + cur.type);
                setIfDiff(stateTv, "状态: 已完成");
                refreshThumb(thumbBox, cur.path);
            } });
        }
        row.setTag(key);
        return row;
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
        thumb.setTag("thumbBox");   // 刷新器据此在分片/成片路径变化时重取首帧
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
        setThumb(thumb, videoPathIn(j.file));   // 首帧截图, 失败保持⇣占位
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
        tvName.setTag("rowName");
        mid.addView(tvName);
        mid.addView(infoLine("下载大小: " + dlSizeStr(j), "rowSize"));
        mid.addView(infoLine("状态: " + j.state, "rowState"));
        row.addView(mid);
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Theme.sub(this));
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        TapGuard.guard(more);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showDlMenu(v, j); }
        });
        row.addView(more);
        parent.addView(row);
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
        thumb.setTag("thumbBox");   // 刷新器据此在分片/成片路径变化时重取首帧
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
        setThumb(thumb, it.path);   // 首帧截图, 失败保持▶占位
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
        tvName.setTag("rowName");
        mid.addView(tvName);
        mid.addView(infoLine("类型: " + it.type, "rowType"));
        mid.addView(infoLine("状态: 已完成", "rowState"));
        row.addView(mid);
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Theme.sub(this));
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        TapGuard.guard(more);
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 点击时按当前索引重取条目: 历史列表增删后行视图被复用, 闭包里的 it/idx 可能已过期
                HistoryStore.Item cur = (hist != null && idx < hist.size()) ? hist.get(idx) : it;
                PopupMenu pm = new PopupMenu(RecordActivity.this, v);
                pm.getMenu().add("打开");
                pm.getMenu().add("分享");
                pm.getMenu().add("选取");
                pm.getMenu().add("属性");
                pm.getMenu().add("删除");
                pm.getMenu().add("打开为");
                pm.getMenu().add("重命名");
                pm.getMenu().add("重新下载");
                pm.getMenu().add("打开下载页");
                pm.getMenu().add("复制下载地址");
                pm.getMenu().add("打开所在目录");
                pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(android.view.MenuItem m2) {
                        String t = m2.getTitle().toString();
                        HistoryStore.Item cur = (hist != null && idx < hist.size()) ? hist.get(idx) : it;
                        if (t.equals("打开")) {
                            try { playInApp(cur.path, cur.title); } catch (Throwable e) { toast("打不开"); }
                        } else if (t.equals("分享")) shareItem(cur);
                        else if (t.equals("选取")) openFileList(cur.path, true);
                        else if (t.equals("属性")) showProperties(cur);
                        else if (t.equals("删除")) confirmDelete(cur, idx);
                        else if (t.equals("打开为")) openAs(cur);
                        else if (t.equals("重命名")) renameItem(cur, idx);
                        else if (t.equals("重新下载")) reDownload(cur);
                        else if (t.equals("打开下载页")) openSourcePage(cur);
                        else if (t.equals("复制下载地址")) copySourceUrl(cur);
                        else if (t.equals("打开所在目录")) openFileList(cur.path, false);
                        return true;
                    }
                });
                pm.show();
            }
        });
        row.addView(more);
        parent.addView(row);
    }

    private void playInApp(String path, String title) {
        Intent i = new Intent(this, NativePlayerActivity.class);
        i.putExtra("url", path);
        i.putExtra("title", title == null ? "播放" : title);
        i.putExtra("kernel", "native");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    /** 历史路径 → 本地 File（相册 content:// 返回 null） */
    private static java.io.File fileOf(String path) {
        if (path == null || path.startsWith("content://")) return null;
        String p = path.startsWith("file://") ? path.substring(7) : path;
        return new java.io.File(p);
    }

    /** 路径 → 可外发/外部应用可读的 Uri */
    private android.net.Uri uriOf(String path) {
        if (path == null) return null;
        if (path.startsWith("content://")) return android.net.Uri.parse(path);
        java.io.File f = fileOf(path);
        if (f == null || !f.exists()) return null;
        return FileListActivity.uriFor(this, f);
    }

    private String mimeOfItem(HistoryStore.Item it) {
        String n = it.title == null ? "" : it.title;
        String m = FileListActivity.mimeOf(n);
        if ("*/*".equals(m) && it.path != null) m = FileListActivity.mimeOf(it.path);
        return m;
    }

    private void shareItem(HistoryStore.Item it) {
        try {
            android.net.Uri u = uriOf(it.path);
            if (u == null) { toast("文件不存在"); return; }
            String mime = mimeOfItem(it);
            Intent sh = new Intent(Intent.ACTION_SEND);
            sh.setType(mime);
            sh.putExtra(Intent.EXTRA_STREAM, u);
            sh.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sh.setClipData(new android.content.ClipData("文件", new String[]{mime}, new android.content.ClipData.Item(u)));
            startActivity(Intent.createChooser(sh, "分享"));
        } catch (Throwable e) { toast("没有可分享的应用"); }
    }

    private void openAs(HistoryStore.Item it) {
        try {
            android.net.Uri u = uriOf(it.path);
            if (u == null) { toast("文件不存在"); return; }
            Intent v = new Intent(Intent.ACTION_VIEW);
            v.setDataAndType(u, mimeOfItem(it));
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(v, "打开为"));
        } catch (Throwable e) { toast("没有可打开的应用"); }
    }

    /** 打开所在目录 / 选取：图2 文件列表页；相册文件回退到录制目录 */
    private void openFileList(String path, boolean pick) {
        java.io.File f = fileOf(path);
        if (f == null || !f.exists()) {
            f = new java.io.File(getExternalFilesDir(null), "录制合并");
            if (!f.isDirectory()) f = getExternalFilesDir(null);
            path = null;
            pick = false;
        }
        Intent i = new Intent(this, FileListActivity.class);
        i.putExtra("dir", f.isDirectory() ? f.getAbsolutePath() : f.getParent());
        if (path != null) i.putExtra("select", f.getAbsolutePath());
        i.putExtra("selectMode", pick);
        try { startActivity(i); } catch (Throwable e) { toast("打不开文件列表"); }
    }

    /** 属性对话框：名称/路径/大小/类型/时长/分辨率/修改时间/来源地址 */
    private void showProperties(final HistoryStore.Item it) {
        new Thread(new Runnable() { public void run() {
            final StringBuilder sb = new StringBuilder();
            sb.append("名称：").append(it.title == null ? "-" : it.title).append("\n");
            sb.append("类型：").append(it.type == null ? "-" : it.type).append("\n");
            sb.append("路径：").append(it.path == null ? "-" : it.path).append("\n");
            java.io.File f = fileOf(it.path);
            if (f != null && f.exists()) {
                sb.append("大小：").append(fmtSize(f.length())).append("\n");
                sb.append("修改时间：").append(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", f.lastModified())).append("\n");
            }
            String mime = mimeOfItem(it);
            sb.append("MIME：").append(mime).append("\n");
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                android.media.MediaMetadataRetriever mm = new android.media.MediaMetadataRetriever();
                try {
                    if (it.path != null && it.path.startsWith("content://")) mm.setDataSource(RecordActivity.this, android.net.Uri.parse(it.path));
                    else if (f != null) mm.setDataSource(f.getAbsolutePath());
                    String dur = mm.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (dur != null) {
                        long ms = Long.parseLong(dur);
                        sb.append("媒体时长：").append(fmtDur(ms / 1000)).append("\n");
                    }
                    String w = mm.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    String h = mm.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    if (w != null && h != null) sb.append("分辨率：").append(w).append(" × ").append(h).append("\n");
                    String br = mm.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE);
                    if (br != null) sb.append("码率：").append(fmtCompact(Long.parseLong(br) / 8)).append("B/s\n");
                } catch (Throwable ignored) {}
                finally { try { mm.release(); } catch (Throwable ignored) {} }
            }
            if (it.url != null) sb.append("来源地址：").append(it.url).append("\n");
            runOnUiThread(new Runnable() { public void run() {
                try {
                    new android.app.AlertDialog.Builder(RecordActivity.this)
                        .setTitle("属性")
                        .setMessage(sb.toString().trim())
                        .setPositiveButton("确定", null)
                        .setNeutralButton("复制路径", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) { copyText(it.path); }
                        }).show();
                } catch (Throwable ignored) {}
            } });
        } }).start();
    }

    private void copyText(String s) {
        if (s == null) return;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("地址", s));
        } catch (Throwable ignored) {}
    }

    private void copySourceUrl(HistoryStore.Item it) {
        if (it.url == null || it.url.isEmpty()) { toast("该记录没有来源地址"); return; }
        copyText(it.url);   // 静默复制，不弹 Toast
    }

    private void openSourcePage(HistoryStore.Item it) {
        if (it.url == null || it.url.isEmpty()) { toast("该记录没有来源地址"); return; }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) { toast("打不开来源页面"); }
    }

    private void reDownload(HistoryStore.Item it) {
        if (it.url == null || it.url.isEmpty()) { toast("该记录没有来源地址，无法重新下载"); return; }
        try { DlManager.start(it.url); rebuild(); }
        catch (Throwable e) { toast("无法开始下载"); }
    }

    private void confirmDelete(final HistoryStore.Item it, final int idx) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确定删除「" + (it.title == null ? "该文件" : it.title) + "」？\n将同时删除本地文件与这条记录。")
            .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (it.path != null && it.path.startsWith("content://")) {
                        try { getContentResolver().delete(android.net.Uri.parse(it.path), null, null); } catch (Throwable ignored) {}
                    } else {
                        java.io.File f = fileOf(it.path);
                        if (f != null) { try { f.delete(); } catch (Throwable ignored) {} }
                    }
                    HistoryStore.removeAt(RecordActivity.this, idx);
                    lastHistCount = -1;          // 强制下次 rebuild 重新解析历史
                    rowOrder.clear();
                    rebuild();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void renameItem(final HistoryStore.Item it, final int idx) {
        final java.io.File f = fileOf(it.path);
        if (f == null || !f.isFile()) { toast("相册文件暂不支持重命名"); return; }
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setText(it.title == null ? f.getName() : it.title);
        et.setSingleLine(true);
        et.setPadding(32, 24, 32, 24);
        new android.app.AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(et)
            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) { toast("名称不能为空"); return; }
                    String oldExt = "";
                    int dot = it.title != null ? it.title.lastIndexOf('.') : -1;
                    if (dot > 0) oldExt = it.title.substring(dot);
                    if (oldExt.length() > 0 && !name.toLowerCase().endsWith(oldExt.toLowerCase())) name += oldExt;
                    java.io.File dst = new java.io.File(f.getParentFile(), name);
                    if (dst.exists()) { toast("同名文件已存在"); return; }
                    if (!f.renameTo(dst)) { toast("重命名失败"); return; }
                    String newPath = "file://" + dst.getAbsolutePath();
                    HistoryStore.updateAt(RecordActivity.this, idx, name, newPath);
                    hist = HistoryStore.load(RecordActivity.this);
                    lastHistCount = HistoryStore.count(RecordActivity.this);
                    rebuild();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showDlMenu(View anchor, final DlManager.DlJob j) {
        PopupMenu pm = new PopupMenu(this, anchor);
        if (j.hls && !j.m3u8) {
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

    /** 紧凑单位: 2.25M / 766K, 不带 B */
    private static String fmtCompact(long b) {
        if (b >= 1048576) return String.format(java.util.Locale.US, "%.2fM", b / 1048576.0);
        if (b >= 1024) return String.format(java.util.Locale.US, "%.0fK", b / 1024.0);
        return String.valueOf(b);
    }

    private static String durStr(RecManager.RecJob j, boolean live) {
        long secs = j.secs + (live && j.startTs > 0 ? (System.currentTimeMillis() - j.startTs) / 1000 : 0);
        return fmtDur(secs);
    }

    private static String sizeStr(RecManager.RecJob j, boolean live) {
        String size = fmtCompact(j.bytes);
        if (live && j.bps > 0) size += "  ↓" + fmtCompact(j.bps) + "/s";   // 实时写入速率
        return size;
    }

    private static String stateStr(RecManager.RecJob j, boolean live) {
        String state = live ? "录制中" : (j.state != null ? j.state : "暂停录制");
        if (!live && j.state != null && j.state.startsWith("合并视频")) {   // 合并阶段: 0-100% 由 ffmpeg 输出时间推进
            state = "合并视频 " + j.mergePct + "%";
        }
        return state;
    }

    private String buildInfo(RecManager.RecJob j, boolean live) {
        return "录制时长: " + durStr(j, live)
            + "\n录制大小: " + sizeStr(j, live)
            + "\n录制状态: " + stateStr(j, live);
    }

    /** 信息行 TextView 工厂 */
    private TextView infoLine(String text, String tag) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTag(tag);
        tv.setTextColor(Theme.sub(this));
        tv.setTextSize(13);
        tv.setLineSpacing(4, 1);
        return tv;
    }

    /** 值变化才 setText, 避免无效重绘 */
    private static void setIfDiff(TextView v, String t) {
        if (v != null && !t.equals(v.getText().toString())) v.setText(t);
    }

    // ---- 首帧缩略图: MediaMetadataRetriever 后台解码 + 内存缓存 ----
    static final java.util.HashMap<String, android.graphics.Bitmap> THUMB_CACHE = new java.util.HashMap<String, android.graphics.Bitmap>();
    private static final java.util.HashMap<android.widget.ImageView, String> THUMB_REQ = new java.util.HashMap<android.widget.ImageView, String>();
    private static final java.util.HashMap<android.widget.ImageView, Long> THUMB_TRY = new java.util.HashMap<android.widget.ImageView, Long>();
    static final java.util.concurrent.ExecutorService THUMB_POOL = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** 从录制/下载目录里挑一个可解码的视频文件: 优先成品 mp4, 否则最早的分片 */
    private static String videoPathIn(java.io.File dir) {
        try {
            if (dir == null) return null;
            if (dir.isFile()) return dir.getAbsolutePath();
            if (!dir.isDirectory()) return null;
            java.io.File mp4 = null, seg = null;
            for (java.io.File f : dir.listFiles()) {
                if (f.length() <= 0) continue;
                String n = f.getName().toLowerCase();
                if (n.endsWith(".mp4") && (mp4 == null || f.lastModified() > mp4.lastModified())) mp4 = f;
                if (n.startsWith("seg") && n.endsWith(".ts") && (seg == null || RecManager.segIndex(f) < RecManager.segIndex(seg))) seg = f;
            }
            return mp4 != null ? mp4.getAbsolutePath() : (seg != null ? seg.getAbsolutePath() : null);
        } catch (Throwable e) { return null; }
    }

    /**
     * 每轮卡片刷新时调用: 行视图是增量复用的, 建卡时文件可能还没生成,
     * 之后分片落地/合并成 MP4/历史删除导致索引移位, 都会让首帧路径变化。
     * 已成功显示则跳过; 路径变化立即重取; 同路径仍未成功(下载中文件未写完)每 3s 重试一次。
     */
    private void refreshThumb(FrameLayout box, String path) {
        if (box == null || path == null || path.isEmpty()) return;
        android.widget.ImageView iv = box.findViewWithTag("thumbIv");
        boolean samePath = iv != null && path.equals(iv.getContentDescription());
        if (samePath) {
            if (iv.getVisibility() == View.VISIBLE) return;   // 该路径首帧已显示
            Long last = THUMB_TRY.get(iv);
            if (last != null && System.currentTimeMillis() - last < 3000) return;  // 限流, 防任务堆积
        }
        setThumb(box, path);
    }

    /** 缩略块显示视频首帧; 解码失败或未就绪时保持原 ▶/⇣ 占位图标 */
    private void setThumb(final FrameLayout thumb, final String path) {
        if (path == null || path.isEmpty()) return;
        android.widget.ImageView iv = (android.widget.ImageView) thumb.findViewWithTag("thumbIv");
        if (iv == null) {
            iv = new android.widget.ImageView(this);
            iv.setTag("thumbIv");
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            thumb.addView(iv, 0, new FrameLayout.LayoutParams(-1, -1));   // 压在占位图标下层
        }
        final android.widget.ImageView fiv = iv;
        boolean samePath = path.equals(fiv.getContentDescription());
        fiv.setContentDescription(path);   // 当前已请求的首帧文件路径
        THUMB_TRY.put(fiv, System.currentTimeMillis());
        if (!samePath) {
            // 换了文件: 清掉旧画面, 恢复占位图标, 避免显示上一个文件的首帧
            fiv.setImageBitmap(null);
            fiv.setVisibility(View.INVISIBLE);
            for (int i = 0; i < thumb.getChildCount(); i++) {
                View c = thumb.getChildAt(i);
                if (c != fiv) c.setVisibility(View.VISIBLE);
            }
        }
        synchronized (THUMB_CACHE) {
            android.graphics.Bitmap bm = THUMB_CACHE.get(path);
            if (bm != null) {
                fiv.setImageBitmap(bm); fiv.setVisibility(View.VISIBLE);
                for (int i = 0; i < thumb.getChildCount(); i++) {
                    View c = thumb.getChildAt(i);
                    if (c != fiv) c.setVisibility(View.GONE);   // 隐藏 ▶ / ⇣ 占位图标
                }
                return;
            }
        }
        fiv.setVisibility(View.INVISIBLE);
        THUMB_REQ.put(fiv, path);
        THUMB_POOL.execute(new Runnable() { public void run() {
            android.graphics.Bitmap bm = decodeThumb(RecordActivity.this, path);
            if (bm != null) synchronized (THUMB_CACHE) {
                if (THUMB_CACHE.size() > 96) THUMB_CACHE.clear();   // 简单上限, 防长列表撑爆内存
                THUMB_CACHE.put(path, bm);
            }
            runOnUiThread(new Runnable() { public void run() {
                if (!path.equals(THUMB_REQ.get(fiv))) return;   // 行已复用到别的条目, 丢弃过期结果
                android.graphics.Bitmap b2;
                synchronized (THUMB_CACHE) { b2 = THUMB_CACHE.get(path); }
                if (b2 != null) {
                    fiv.setImageBitmap(b2); fiv.setVisibility(View.VISIBLE);
                    for (int i = 0; i < thumb.getChildCount(); i++) {
                        View c = thumb.getChildAt(i);
                        if (c != fiv) c.setVisibility(View.GONE);
                    }
                }
            } });
        } });
    }

    /** 取视频 0ms 处关键帧并居中裁剪到缩略块尺寸(140x90 px), 失败返回 null */
    static android.graphics.Bitmap decodeThumb(Context c, String path) {
        android.media.MediaMetadataRetriever mm = new android.media.MediaMetadataRetriever();
        try {
            String p = path.startsWith("file://") ? path.substring(7) : path;
            if (path.startsWith("content://")) mm.setDataSource(c, android.net.Uri.parse(path));
            else mm.setDataSource(p);
            android.graphics.Bitmap f = mm.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (f == null) return null;
            int W = 140, H = 90;
            float sc = Math.max(W / (float) f.getWidth(), H / (float) f.getHeight());
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(f,
                Math.max(1, Math.round(f.getWidth() * sc)), Math.max(1, Math.round(f.getHeight() * sc)), true);
            int x = Math.max(0, (scaled.getWidth() - W) / 2), y = Math.max(0, (scaled.getHeight() - H) / 2);
            return android.graphics.Bitmap.createBitmap(scaled, x, y, Math.min(W, scaled.getWidth()), Math.min(H, scaled.getHeight()));
        } catch (Throwable e) { return null; }
        finally { try { mm.release(); } catch (Throwable ignored) {} }
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
        thumb.setTag("thumbBox");   // 刷新器据此在分片/成片路径变化时重取首帧
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
        setThumb(thumb, videoPathIn(j.file));   // 首帧截图, 失败保持▶占位

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
        tvName.setTag("rowName");
        mid.addView(tvName);

        // 时长/大小/状态三行独立 TextView, 各自 diff 才刷新
        mid.addView(infoLine("录制时长: " + durStr(j, live), "rowDur"));
        mid.addView(infoLine("录制大小: " + sizeStr(j, live), "rowSize"));
        mid.addView(infoLine("录制状态: " + stateStr(j, live), "rowState"));
        row.addView(mid);

        // 右侧 ⋮ 菜单
        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextColor(Theme.sub(this));
        more.setTextSize(22);
        more.setGravity(Gravity.CENTER);
        more.setPadding(24, 24, 24, 24);
        TapGuard.guard(more);
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
            // 静默复制，不再弹 Toast
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
