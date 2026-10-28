package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ziyuan.Assistant.xhs.XhsEngine;
import com.ziyuan.Assistant.xhs.XhsNet;
import com.ziyuan.Assistant.xhs.XhsParser;
import com.ziyuan.Assistant.xhs.XhsStore;

import java.io.File;
import java.util.List;

/** 小红书下载器 · 主页面（复刻 XHS_Downloader_Android 主界面） */
public class XhsActivity extends Activity implements XhsEngine.Listener {

    private boolean dark, oled;
    private int accent;
    private LinearLayout listBody, tabAll, tabDone, tabPending, tabFailed;
    private String query = "", filter = "all";
    private EditText etSearch;
    private final Handler ui = new Handler();
    private LinearLayout bubble;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        XhsEngine.init(this);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        oled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("oled", false);
        accent = dark ? 0xFF7AB8FF : 0xFF1677FF;
        buildUi();
        applyTheme();
    }

    @Override
    protected void onResume() {
        super.onResume();
        XhsEngine.addListener(this);
        onChanged();
        if (XhsEngine.store().autoReadClipboard())
            ui.postDelayed(new Runnable() { public void run() { detectClipboard(); } }, 400);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Android 10+ 无焦点读剪贴板返回空——等窗口焦点到手再读
        if (hasFocus && XhsEngine.store().autoReadClipboard())
            ui.postDelayed(new Runnable() { public void run() { detectClipboard(); } }, 200);
    }

    @Override
    protected void onPause() {
        super.onPause();
        XhsEngine.removeListener(this);
        // 离开页面清去重标记，回来再进时同一条剪贴板也能重新检测
        getSharedPreferences("xhs_settings", MODE_PRIVATE).edit().remove("last_clip").apply();
    }


    @Override public void onChanged() { runOnUiThread(new Runnable() { public void run() { rebuildList(); } }); }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private String nz(String s) { return s == null ? "" : s; }

    // ================= UI 构建 =================
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? (oled ? Color.BLACK : 0xFF141414) : 0xFFEEF4FF);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // 顶栏：标题 + 设置
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView title = new TextView(this);
        title.setText("小红书下载器");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(22);
        gear.setPadding(dp(10), 0, dp(2), 0);
        gear.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(XhsActivity.this, XhsSettingsActivity.class)); }
        });
        top.addView(gear);
        page.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // 搜索栏
        LinearLayout searchWrap = new LinearLayout(this);
        searchWrap.setOrientation(LinearLayout.HORIZONTAL);
        searchWrap.setPadding(dp(16), 0, dp(16), dp(8));
        etSearch = new EditText(this);
        etSearch.setHint("搜索历史记录");
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        etSearch.setBackgroundResource(dark ? R.drawable.bg_input_flat : R.drawable.bg_input);
        etSearch.setPadding(dp(14), dp(10), dp(14), dp(10));
        etSearch.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        etSearch.setHintTextColor(0xFF8A94A6);
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable s) { query = s.toString().trim(); rebuildList(); }
        });
        searchWrap.addView(etSearch, new LinearLayout.LayoutParams(-1, -2));
        page.addView(searchWrap);

        // 标签行：全部 / 待选择 / 失败
        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(16), 0, dp(16), dp(8));
        tabAll = makeTab("全部", "all");
        tabDone = makeTab("已完成", "done");
        tabPending = makeTab("待选择", "pending");
        tabFailed = makeTab("失败", "failed");
        tabs.addView(tabAll);
        tabs.addView(tabDone);
        tabs.addView(tabPending);
        tabs.addView(tabFailed);
        tabsScroll.addView(tabs);
        page.addView(tabsScroll, new LinearLayout.LayoutParams(-1, -2));

        // 任务列表
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listBody = new LinearLayout(this);
        listBody.setOrientation(LinearLayout.VERTICAL);
        listBody.setPadding(dp(16), dp(4), dp(16), dp(96));
        scroll.addView(listBody, new LinearLayout.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, -1, 1f));

        // 底部输入按钮
        TextView inputBtn = new TextView(this);
        inputBtn.setText("＋ 输入链接下载");
        inputBtn.setTextSize(15);
        inputBtn.setTypeface(Typeface.DEFAULT_BOLD);
        inputBtn.setTextColor(Color.WHITE);
        inputBtn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accent);
        bg.setCornerRadius(dp(26));
        inputBtn.setBackground(bg);
        int pad = dp(14);
        inputBtn.setPadding(0, pad, 0, pad);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        flp.bottomMargin = dp(20);
        inputBtn.setLayoutParams(flp);
        inputBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showInputDialog(null); }
        });
        root.addView(inputBtn);

        // 剪贴板气泡
        bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(dark ? 0xFF2A2A2A : Color.WHITE);
        bbg.setCornerRadius(dp(22));
        if (dark) bbg.setStroke(dp(1), 0x33FFFFFF); else bbg.setStroke(dp(1), 0x22000000);
        bubble.setBackground(bbg);
        TextView bt = new TextView(this);
        bt.setText("检测到小红书链接，去下载？");
        bt.setTextSize(13);
        bt.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        bubble.addView(bt);
        TextView go = new TextView(this);
        go.setText("  下载");
        go.setTextSize(13);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setTextColor(accent);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bubble.setVisibility(View.GONE);
                String text = clipboardText();
                if (!text.isEmpty()) submit(text, false);
            }
        });
        bubble.addView(go);
        bubble.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(84);
        bubble.setLayoutParams(blp);
        root.addView(bubble);

        setContentView(root);
    }

    private LinearLayout makeTab(final String label, final String key) {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        t.setOrientation(LinearLayout.HORIZONTAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        t.setTag(tv);
        t.addView(tv);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { filter = key; restyleTab(tabAll, "全部", "all"); restyleTab(tabDone, "已完成", "done"); restyleTab(tabPending, "待选择", "pending"); restyleTab(tabFailed, "失败", "failed"); rebuildList(); }
        });
        restyleTab(t, label, key);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void restyleTab(LinearLayout t, String label, String key) {
        TextView tv = (TextView) t.getTag();
        int count = 0;
        for (XhsStore.Task task : XhsEngine.tasks()) {
            if ("all".equals(key)) count++;
            else if ("failed".equals(key)) { if ("failed".equals(task.status)) count++; }
            else if ("done".equals(key)) { if ("done".equals(task.status)) count++; }
            else { if ("pending".equals(task.status) || "running".equals(task.status) || "stopped".equals(task.status) || "selecting".equals(task.status)) count++; }
        }
        tv.setText(label + (count > 0 ? " (" + count + ")" : ""));
        boolean sel = key.equals(filter);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(sel ? accent : (dark ? 0x22FFFFFF : 0x14000000));
        t.setBackground(g);
        tv.setTextColor(sel ? Color.WHITE : (dark ? 0xAAFFFFFF : 0xFF6B7280));
    }

    // ================= 剪贴板 =================
    private String clipboardText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return "";
            CharSequence s = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            return s == null ? "" : s.toString();
        } catch (Throwable t) { return ""; }
    }

    private void detectClipboard() {
        final String text = clipboardText();
        boolean dbg = XhsEngine.store().debugNotification();
        if (dbg) toast("CB: auto=" + XhsEngine.store().autoReadClipboard() + " len=" + text.length()
                + " last=" + (text.equals(getSharedPreferences("xhs_settings", MODE_PRIVATE).getString("last_clip", ""))));
        if (text.isEmpty() || text.equals(getSharedPreferences("xhs_settings", MODE_PRIVATE).getString("last_clip", ""))) return;
        List<String> links = XhsParser.extractLinks(text, null);
        if (dbg) toast("CB links=" + links.size());
        if (links.isEmpty()) return;
        getSharedPreferences("xhs_settings", MODE_PRIVATE).edit().putString("last_clip", text).apply();
        if (XhsEngine.store().autoReadClipboard()) {
            // 原版行为：自动读取剪贴板=直接开始下载
            submit(text, false);
        } else if (XhsEngine.store().showClipboardBubble()) {
            bubble.setVisibility(View.VISIBLE);
            ui.postDelayed(new Runnable() { public void run() { bubble.setVisibility(View.GONE); } }, 8000);
        }
    }

    // ================= 提交 =================
    private void submit(String text, boolean infoOnly) {
        if (text == null || text.trim().isEmpty()) { toast("请输入链接"); return; }
        List<String> links = XhsParser.extractLinks(text, null);
        if (links.isEmpty()) { toast("未识别到小红书链接"); return; }
        XhsStore.Task t = XhsEngine.enqueue(this, text, infoOnly);
        if (t == null) toast("未识别到小红书链接");
        else toast(infoOnly ? "已加入解析（仅保存信息）" : "已开始解析下载");
    }

    // ================= 列表 =================
    private void rebuildList() {
        restyleTab(tabAll, "全部", "all");
        restyleTab(tabPending, "待选择", "pending");
        restyleTab(tabFailed, "失败", "failed");
        listBody.removeAllViews();
        int shown = 0;
        List<XhsStore.Task> tasks = XhsEngine.tasks();
        for (final XhsStore.Task t : tasks) {
            if (filter.equals("failed") && !"failed".equals(t.status)) continue;
            if (filter.equals("done") && !"done".equals(t.status)) continue;
            if (filter.equals("pending") && !("pending".equals(t.status) || "running".equals(t.status)
                    || "stopped".equals(t.status) || "selecting".equals(t.status))) continue;
            if (!query.isEmpty() && !(nz(t.title) + nz(t.author)).contains(query)) continue;
            listBody.addView(taskCard(t));
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(query.isEmpty() ? "暂无下载记录\n粘贴小红书分享链接开始下载" : "无匹配记录");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(14);
            empty.setTextColor(0xFF8A94A6);
            empty.setPadding(0, dp(80), 0, 0);
            listBody.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View taskCard(final XhsStore.Task t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(dark ? (oled ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        // 状态色标题
        int statusColor;
        String statusText;
        if ("failed".equals(t.status)) { statusColor = 0xFFE5484D; statusText = "失败"; }
        else if ("done".equals(t.status)) { statusColor = accent; statusText = "完成"; }
        else if ("running".equals(t.status)) { statusColor = 0xFF8A94A6; statusText = "下载中"; }
        else if ("stopped".equals(t.status)) { statusColor = 0xFF8A94A6; statusText = "已停止"; }
        else if ("selecting".equals(t.status)) { statusColor = accent; statusText = "待选择"; }
        else { statusColor = 0xFF8A94A6; statusText = "排队中"; }

        TextView title = new TextView(this);
        title.setText(nz(t.title).isEmpty() ? "解析中…" : t.title);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        title.setMaxLines(2);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        String meta = nz(t.author);
        if (!meta.isEmpty()) meta = "@" + meta + " · ";
        meta += statusText;
        if ("running".equals(t.status) && t.progress > 0) meta += " " + t.progress + "%";
        if ("failed".equals(t.status) && !nz(t.error).isEmpty()) meta += " · " + t.error;
        else if ("done".equals(t.status)) meta += " · " + t.files.size() + " 个文件";
        TextView metaV = new TextView(this);
        metaV.setText(meta);
        metaV.setTextSize(12);
        metaV.setTextColor(statusColor);
        metaV.setMaxLines(2);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(4);
        card.addView(metaV, mp);

        if ("running".equals(t.status)) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(t.progress);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(6));
            pp.topMargin = dp(8);
            card.addView(pb, pp);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(-1, -2);
        acp.topMargin = dp(8);
        if ("running".equals(t.status) || "pending".equals(t.status)) {
            actions.addView(miniBtn("取消", new View.OnClickListener() {
                public void onClick(View v) { XhsEngine.cancel(t.id); }
            }));
        } else {
            if ("selecting".equals(t.status))
                actions.addView(miniBtn("选择媒体", new View.OnClickListener() {
                    public void onClick(View v) { showSelectDialog(t); }
                }));
            if ("failed".equals(t.status) || "stopped".equals(t.status))
                actions.addView(miniBtn("重试", new View.OnClickListener() {
                    public void onClick(View v) { XhsEngine.retry(t); }
                }));
            if ("done".equals(t.status) && !t.files.isEmpty()) {
                actions.addView(miniBtn("播放", new View.OnClickListener() {
                    public void onClick(View v) { playTask(t); }
                }));
                actions.addView(miniBtn("打开目录", new View.OnClickListener() {
                    public void onClick(View v) { openFolder(t); }
                }));
            }
            if (!"running".equals(t.status))
                actions.addView(miniBtn("删除", new View.OnClickListener() {
                    public void onClick(View v) { confirmDelete(t); }
                }));
        }
        card.addView(actions, acp);

        // 点卡片：复制文案
        card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!nz(t.description).isEmpty()) copyText(t.description);
        }});
        return card;
    }

    private TextView miniBtn(String text, View.OnClickListener l) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setTextColor(accent);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0x30FFFFFF : (accent & 0x18FFFFFF));
        g.setCornerRadius(dp(12));
        btn.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    // ================= 输入弹窗 =================
    private void showInputDialog(final String preset) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(12), dp(20), 0);
        final EditText et = new EditText(this);
        et.setHint("粘贴小红书分享链接或含链接的文案");
        et.setTextSize(14);
        et.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        et.setHintTextColor(0xFF8A94A6);
        et.setBackgroundResource(dark ? R.drawable.bg_input_flat : R.drawable.bg_input);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP);
        et.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (preset != null) et.setText(preset);
        wrap.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new GlassDialog.Builder(this)
                .setTitle("输入链接")
                .setView(wrap)
                .setPositiveButton("下载", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        submit(et.getText().toString(), false);
                    }
                })
                .setNeutralButton("复制文案", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        copyText(et.getText().toString());
                    }
                })
                .setNegativeButton("仅保存信息", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        submit(et.getText().toString(), true);
                    }
                })
                .show();
    }

    /** 选择性下载：勾选要保存的媒体 */
    private void showSelectDialog(final XhsStore.Task t) {
        XhsParser.Note note = XhsEngine.noteFromJson(t.noteJson);
        final List<XhsParser.Media> items = note.items;
        if (items == null || items.isEmpty()) { toast("没有可选媒体"); return; }
        CharSequence[] names = new CharSequence[items.size()];
        final boolean[] checked = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            XhsParser.Media m = items.get(i);
            String kind = "video".equals(m.kind) ? "视频" : "live".equals(m.kind) ? "实况" : "图片";
            names[i] = kind + " " + (i + 1) + (m.width > 0 ? " · " + m.width + "×" + m.height : "");
            checked[i] = true;
        }
        new GlassDialog.Builder(this)
                .setTitle("选择要下载的媒体")
                .setMultiChoiceItems(names, checked, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(android.content.DialogInterface d, int w, boolean isChecked) { }
                })
                .setPositiveButton("下载", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        List<XhsParser.Media> chosen = new java.util.ArrayList<XhsParser.Media>();
                        for (int i = 0; i < items.size(); i++) if (checked[i]) chosen.add(items.get(i));
                        XhsEngine.continueSelective(t, chosen);
                    }
                })
                .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { XhsEngine.remove(t); }
                })
                .show();
    }

    /** 内置播放器播放任务里的视频（没有视频则播第一个文件） */
    private void playTask(final XhsStore.Task t) {
        if (t.files.isEmpty()) { toast("无文件"); return; }
        String pick = t.files.get(0);
        for (String f : t.files) {
            String low = f.toLowerCase();
            if (low.endsWith(".mp4") || low.endsWith(".mov")) { pick = f; break; }
        }
        try {
            if (pick.startsWith("content://")) { openAny(pick); return; }
            Intent i = new Intent(this, NativePlayerActivity.class);
            i.putExtra("url", pick);
            i.putExtra("title", nz(t.title).isEmpty() ? "播放" : t.title);
            i.putExtra("kernel", "native");
            startActivity(i);
        } catch (Throwable e) { openAny(pick); }
    }

    /** 打开文件/目录（MediaStore 模式存的是 content://，直接列文件并打开） */
    private void openFolder(final XhsStore.Task t) {
        if (t.files.isEmpty()) { toast("无文件"); return; }
        final String first = t.files.get(0);
        if (first.startsWith("content://")) {
            GlassDialog.Builder b = new GlassDialog.Builder(this).setTitle("已保存 " + t.files.size() + " 个文件");
            CharSequence[] names = new CharSequence[t.files.size()];
            for (int i = 0; i < t.files.size(); i++) {
                String u = t.files.get(i);
                String q = Uri.parse(u).getQueryParameter("displayName");
                names[i] = q != null ? q : ("文件 " + (i + 1));
            }
            b.setItems(names, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { openAny(t.files.get(w)); }
            }).show();
            return;
        }
        try {
            GlassDialog.Builder b = new GlassDialog.Builder(this).setTitle("已保存 " + t.files.size() + " 个文件");
            CharSequence[] names = new CharSequence[t.files.size()];
            for (int i = 0; i < t.files.size(); i++) names[i] = new File(t.files.get(i)).getName();
            b.setItems(names, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { openAny(t.files.get(w)); }
            }).show();
            return;
        } catch (Throwable ignored) { }
        toast(first);
    }

    private void openAny(String s) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri u;
            String type;
            if (s.startsWith("content://")) { u = Uri.parse(s); type = s.contains(".mp4") || s.contains(".mov") ? "video/*" : "image/*"; }
            else {
                File f = new File(s);
                u = androidx.core.content.FileProvider.getUriForFile(this, "com.ziyuan.Assistant.fp", f);
                type = (s.endsWith(".mp4") || s.endsWith(".mov")) ? "video/mp4" : "image/*";
            }
            i.setDataAndType(u, type);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Throwable e) { toast("无法打开"); }
    }

    private void copyText(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("xhs", s));
        toast("已复制");
    }

    private void confirmDelete(final XhsStore.Task t) {
        new GlassDialog.Builder(this)
                .setMessage("删除该任务记录？（已下载的文件保留）")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { XhsEngine.remove(t); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void applyTheme() {
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(
                    dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
}
