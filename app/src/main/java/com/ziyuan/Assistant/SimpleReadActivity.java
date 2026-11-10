package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** 「简单阅读」原生 TXT 阅读器。书架 + 阅读器, 与反编译的 com.example.txtreader 行为对齐:
 *  导入本地 TXT(自动识别 UTF-8/GBK/UTF-16) -> 书架 -> 真分页阅读(字号/行距/字距/边距/
 *  字色/背景色/亮度/日夜/翻页模式/章节目录/进度记忆)。纯系统 API, 兼容 minSdk26~targetSdk36。 */
public class SimpleReadActivity extends Activity implements ReadPageView.Host, SensorEventListener {

    private static final int REQ_IMPORT = 7301;
    private static final int BG_SHELF = 0xFF12141C;
    private static final int CARD_BG = 0xFF22263A;

    private FrameLayout root, shelf, reader;
    private LinearLayout bookList, menuPanel, catalogPanel;
    private TextView emptyHint, loading, progressLabel, bookTitle;
    private SeekBar seek;
    private ReadPageView page;
    private ReadPageView.Cfg cfg;
    private ReadBook current;
    private ArrayList<ReadBook> books = new ArrayList<ReadBook>();
    private final Set<String> checked = new HashSet<String>();
    private boolean selecting, menuShown, catalogShown;
    private LinearLayout selectBar;
    private SensorManager sensor;
    private long lastShake;
    private float lastAx, lastAy, lastAz;
    private final android.os.Handler ui = new android.os.Handler();

    private final int[] BG_COLORS = {
        0xFFEFE3CE, 0xFFE6E6E6, 0xFFF5F5F5, 0xFFD8E0DB,
        0xFFFFFFFF, 0xFF2B2B2B, 0xFF141414, 0xFF0D5661
    };
    private final int[] FG_COLORS = {
        0xFF0D5661, 0xFF2B2B2B, 0xFF3000BA, 0xFF141414,
        0xFF5A595B, 0xFFA5A5A5, 0xFFFFFFFF, 0xFF7FA87F
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = ReadPageView.Cfg.load(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG_SHELF);
        buildShelf();
        buildReader();
        setContentView(root);
        refreshBooks();
        applyBrightness();
        sensor = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    // ---------------- 书架 ----------------
    private void buildShelf() {
        shelf = new FrameLayout(this);
        shelf.setBackgroundColor(BG_SHELF);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(18), dp(16), dp(10));
        TextView t = new TextView(this);
        t.setText("简单阅读"); t.setTextColor(0xFFFFFFFF);
        t.setTextSize(22); t.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(smallBtn("关于", new View.OnClickListener() {
            public void onClick(View v) { showAbout(); }
        }));
        col.addView(bar);
        TextView sub = new TextView(this);
        sub.setText("身体和灵魂总有一个要在路上");
        sub.setTextColor(0xFF8A93A8); sub.setTextSize(12);
        sub.setPadding(dp(16), 0, dp(16), dp(10));
        col.addView(sub);

        final EditText search = new EditText(this);
        search.setHint("搜索书名…"); search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0xFF6B7391); search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(pill(0xFF1B1F2E));
        search.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(dp(16), 0, dp(16), dp(10));
        col.addView(search, sp);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { refreshBooks(s.toString()); }
            public void afterTextChanged(android.text.Editable s) {}
        });

        selectBar = new LinearLayout(this);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setBackgroundColor(0xFF2A2F46);
        selectBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        selectBar.setVisibility(View.GONE);
        selectBar.addView(smallBtn("全选", new View.OnClickListener() {
            public void onClick(View v) { for (ReadBook x : books) checked.add(x.id); refreshBooks(); }
        }));
        selectBar.addView(smallBtn("取消", new View.OnClickListener() {
            public void onClick(View v) { stopSelect(); }
        }));
        View filler = new View(this);
        selectBar.addView(filler, new LinearLayout.LayoutParams(0, 1, 1f));
        selectBar.addView(smallBtn("删除", new View.OnClickListener() {
            public void onClick(View v) { confirmDelete(); }
        }));
        col.addView(selectBar);

        ScrollView sv = new ScrollView(this);
        bookList = new LinearLayout(this);
        bookList.setOrientation(LinearLayout.VERTICAL);
        bookList.setPadding(dp(12), dp(4), dp(12), dp(90));
        sv.addView(bookList);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        emptyHint = new TextView(this);
        emptyHint.setText("还没有书\n点右下角「导入 TXT」，选择手机里的 .txt 文件");
        emptyHint.setTextColor(0xFF767E96); emptyHint.setTextSize(14);
        emptyHint.setGravity(Gravity.CENTER); emptyHint.setPadding(dp(24), dp(60), dp(24), dp(60));
        emptyHint.setVisibility(View.GONE);
        col.addView(emptyHint);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(180), dp(46));
        flp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        flp.bottomMargin = dp(22);
        TextView fab = new TextView(this);
        fab.setText("＋ 导入 TXT"); fab.setTextColor(0xFF12141C);
        fab.setTextSize(15); fab.setTypeface(Typeface.DEFAULT_BOLD);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(pill(0xFFFFCB05));
        fab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFiles(); } });
        shelf.addView(col, new FrameLayout.LayoutParams(-1, -1));
        shelf.addView(fab, flp);
        root.addView(shelf, new FrameLayout.LayoutParams(-1, -1));
    }

    private void refreshBooks() { refreshBooks(""); }

    private void refreshBooks(String query) {
        books = ReadBook.loadAll(this);
        bookList.removeAllViews();
        int shown = 0;
        for (final ReadBook bk : books) {
            if (query.length() > 0 && !bk.name.toLowerCase().contains(query.toLowerCase())) continue;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(pill(CARD_BG));
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.setMargins(0, 0, 0, dp(10));

            final TextView mark = new TextView(this);
            mark.setText(checked.contains(bk.id) ? "✓" : "📖");
            mark.setTextColor(checked.contains(bk.id) ? 0xFFFFCB05 : 0xFFFFFFFF);
            mark.setTextSize(20); mark.setGravity(Gravity.CENTER);
            mark.setBackground(pill(checked.contains(bk.id) ? 0xFF3A3F5C : 0xFF303650));
            row.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(8), 0);
            TextView nm = new TextView(this);
            nm.setText(bk.name); nm.setTextColor(0xFFFFFFFF); nm.setTextSize(15);
            nm.setTypeface(Typeface.DEFAULT_BOLD); nm.setSingleLine(true);
            info.addView(nm);
            TextView meta = new TextView(this);
            meta.setText(String.format("已读 %.1f%%  ·  %s  ·  %s",
                    bk.progress * 100, humanSize(bk.size), humanTime(bk.lastRead)));
            meta.setTextColor(0xFF8992A9); meta.setTextSize(11);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            if (selecting) {
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (checked.contains(bk.id)) checked.remove(bk.id); else checked.add(bk.id);
                        refreshBooks();
                    }
                });
            } else {
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { openBook(bk); }
                });
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { checked.add(bk.id); startSelect(); return true; }
                });
            }
            bookList.addView(row, rp);
        }
        emptyHint.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        emptyHint.setText(query.length() > 0 ? "没有匹配的书" : "还没有书\n点右下角「导入 TXT」，选择手机里的 .txt 文件");
    }

    private void startSelect() { selecting = true; selectBar.setVisibility(View.VISIBLE); refreshBooks(); }
    private void stopSelect() { selecting = false; checked.clear(); selectBar.setVisibility(View.GONE); refreshBooks(); }

    private void confirmDelete() {
        if (checked.isEmpty()) { toast("请先选择文件"); return; }
        final ArrayList<String> ids = new ArrayList<String>(checked);
        new AlertDialog.Builder(this).setTitle("确认删除")
            .setMessage("共删除 " + ids.size() + " 个文件（只移出书架）")
            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    ReadBook.remove(SimpleReadActivity.this, ids);
                    toast("删除成功"); stopSelect();
                }
            }).setNegativeButton("取消", null).show();
    }

    // ---------------- 阅读器 ----------------
    private void buildReader() {
        reader = new FrameLayout(this);
        reader.setVisibility(View.GONE);
        page = new ReadPageView(this);
        page.setHost(this);
        reader.addView(page, new FrameLayout.LayoutParams(-1, -1));

        loading = new TextView(this);
        loading.setText("文件载入中，请稍后"); loading.setTextColor(0xFF666666);
        loading.setTextSize(15); loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(0xFFE6E6E6); loading.setVisibility(View.GONE);
        reader.addView(loading, new FrameLayout.LayoutParams(-1, -1));

        // 顶栏
        final LinearLayout top = new LinearLayout(this);
        top.setId(0x7101);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xEE22243A);
        top.setPadding(dp(12), dp(10), dp(12), dp(10));
        top.addView(smallBtn("‹ 书架", new View.OnClickListener() {
            public void onClick(View v) { closeBook(); }
        }));
        bookTitle = new TextView(this);
        bookTitle.setTextColor(0xFFFFFFFF); bookTitle.setTextSize(14);
        bookTitle.setSingleLine(true);
        top.addView(bookTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(smallBtn("章节", new View.OnClickListener() {
            public void onClick(View v) { showCatalog(); }
        }));
        top.setVisibility(View.GONE);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2);
        tp.gravity = Gravity.TOP;
        reader.addView(top, tp);

        // 底栏(进度 + 设置)
        LinearLayout bottom = new LinearLayout(this);
        bottom.setId(0x7102);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(0xEE22243A);
        bottom.setPadding(dp(12), dp(8), dp(12), dp(10));
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.addView(smallBtn("上一章", new View.OnClickListener() {
            public void onClick(View v) { page.gotoChapter(page.chapterIndex() - 1); }
        }));
        seek = new SeekBar(this); seek.setMax(1000);
        pr.addView(seek, new LinearLayout.LayoutParams(0, -2, 1f));
        pr.addView(smallBtn("下一章", new View.OnClickListener() {
            public void onClick(View v) { page.gotoChapter(page.chapterIndex() + 1); }
        }));
        bottom.addView(pr);
        progressLabel = new TextView(this);
        progressLabel.setTextColor(0xFFB9C0D4); progressLabel.setTextSize(11);
        progressLabel.setGravity(Gravity.CENTER);
        bottom.addView(progressLabel);
        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setGravity(Gravity.CENTER);
        for (String s : new String[]{"字-", "字+", "亮度", "设置", "隐藏"}) {
            final String k = s;
            acts.addView(smallBtn(s, new View.OnClickListener() {
                public void onClick(View v) { onQuick(k); }
            }));
        }
        bottom.addView(acts);
        bottom.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2);
        bp.gravity = Gravity.BOTTOM;
        reader.addView(bottom, bp);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (fromUser && current != null) page.gotoProgress(v / 1000f);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        buildMenu();
        buildCatalog();
        root.addView(reader, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildMenu() {
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setBackgroundColor(0xF222243A);
        menuPanel.setPadding(dp(12), dp(10), dp(12), dp(18));
        menuPanel.setVisibility(View.GONE);
        ScrollView sv = new ScrollView(this);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        box.addView(settingRow("字号", "字-", "字+", "字号", new Stepper() {
            public void step(int d) { cfg.fontSize = clamp(cfg.fontSize + d * 2, 12, 40); }
        }));
        box.addView(settingRow("行距", "距-", "距+", "行距", new Stepper() {
            public void step(int d) { cfg.lineSpace = clamp(cfg.lineSpace + d * 2, 0, 40); }
        }));
        box.addView(settingRow("字距", "距-", "距+", "字距", new Stepper() {
            public void step(int d) { cfg.letter = clamp(cfg.letter + d, 0, 6); }
        }));
        box.addView(settingRow("边距", "边-", "边+", "边距", new Stepper() {
            public void step(int d) { cfg.padding = clamp(cfg.padding + d * 2, 4, 60); }
        }));
        box.addView(settingRow("段首缩进", "章-", "章+", "缩进", new Stepper() {
            public void step(int d) { cfg.indent = clamp(cfg.indent + d, 0, 6); }
        }));
        box.addView(settingRow("亮度", "亮-", "亮+", "亮度", new Stepper() {
            public void step(int d) {
                cfg.brightness = Math.max(0.15f, Math.min(1f, cfg.brightness + d * 0.1f));
                applyBrightness();
            }
        }));

        box.addView(sectionLabel("字体色"));
        box.addView(colorRow(FG_COLORS, true));
        box.addView(sectionLabel("背景色"));
        box.addView(colorRow(BG_COLORS, false));
        box.addView(sectionLabel("模式"));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.addView(smallBtn("白天 / 夜间", new View.OnClickListener() {
            public void onClick(View v) {
                cfg.theme = cfg.theme == 0 ? 1 : 0;
                cfg.applyTheme(); commitCfg();
            }
        }));
        modes.addView(smallBtn("左右 / 上下", new View.OnClickListener() {
            public void onClick(View v) { cfg.pageMode = cfg.pageMode == 0 ? 1 : 0; commitCfg(); toast(cfg.pageMode == 0 ? "左右翻页" : "上下翻页"); }
        }));
        modes.addView(smallBtn("动画", new View.OnClickListener() {
            public void onClick(View v) { cfg.anim = (cfg.anim + 1) % 3; commitCfg(); toast(new String[]{"平移", "淡入", "无动画"}[cfg.anim]); }
        }));
        box.addView(modes);
        box.addView(smallBtn("收起菜单", new View.OnClickListener() {
            public void onClick(View v) { toggleMenu(false); }
        }));
        sv.addView(box);
        menuPanel.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1, -2);
        mp.gravity = Gravity.BOTTOM;
        menuPanel.setLayoutParams(mp);
        reader.addView(menuPanel);
    }

    private interface Stepper { void step(int d); }

    private View settingRow(final String label, String minus, String plus, final String tag, final Stepper st) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        TextView l = new TextView(this);
        l.setText(label); l.setTextColor(0xFFE8ECF4); l.setTextSize(13);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(smallBtn(minus, new View.OnClickListener() {
            public void onClick(View v) { st.step(-1); commitCfg(); }
        }));
        r.addView(smallBtn(plus, new View.OnClickListener() {
            public void onClick(View v) { st.step(1); commitCfg(); }
        }));
        return r;
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(0xFF8A93A8); t.setTextSize(11);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private View colorRow(final int[] colors, final boolean fg) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (final int c : colors) {
            TextView v = new TextView(this);
            v.setBackgroundColor(c);
            v.setText("  ");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            v.setLayoutParams(lp);
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    if (fg) cfg.fontColor = c; else cfg.bgColor = c;
                    commitCfg();
                }
            });
            r.addView(v);
        }
        return r;
    }

    private void buildCatalog() {
        catalogPanel = new LinearLayout(this);
        catalogPanel.setOrientation(LinearLayout.VERTICAL);
        catalogPanel.setBackgroundColor(0xF222243A);
        catalogPanel.setPadding(dp(12), dp(10), dp(12), dp(12));
        catalogPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(250), -1);
        p.gravity = Gravity.RIGHT;
        catalogPanel.setLayoutParams(p);
        reader.addView(catalogPanel);
    }

    private void showCatalog() {
        if (current == null || current.chapters.isEmpty()) { toast("当前文章没有设置章节或者章节未完全加载"); return; }
        catalogPanel.removeAllViews();
        catalogPanel.addView(sectionLabel("章节 · 共 " + current.chapters.size() + " 章"));
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int cur = page.chapterIndex();
        for (int i = 0; i < current.chapters.size(); i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText((i + 1) + ". " + current.chapters.get(i).title);
            t.setTextColor(i == cur ? 0xFFFFCB05 : 0xFFE8ECF4);
            t.setTextSize(13); t.setPadding(dp(6), dp(9), dp(6), dp(9));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { page.gotoChapter(idx); catalogPanel.setVisibility(View.GONE); catalogShown = false; }
            });
            box.addView(t);
        }
        sv.addView(box);
        catalogPanel.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        catalogPanel.setVisibility(View.VISIBLE);
        catalogShown = true;
    }

    private void onQuick(String k) {
        if ("字-".equals(k)) cfg.fontSize = clamp(cfg.fontSize - 2, 12, 40);
        else if ("字+".equals(k)) cfg.fontSize = clamp(cfg.fontSize + 2, 12, 40);
        else if ("亮度".equals(k)) { cfg.brightness = cfg.brightness > 0.6f ? 0.3f : 1f; applyBrightness(); }
        else if ("设置".equals(k)) { toggleMenu(true); return; }
        else if ("隐藏".equals(k)) { toggleMenu(false); return; }
        commitCfg();
    }

    private final Runnable cfgTask = new Runnable() {
        public void run() { if (current != null) page.applyCfg(cfg); }
    };
    private void commitCfg() {
        cfg.save(this);
        ui.removeCallbacks(cfgTask);
        ui.postDelayed(cfgTask, 180);   // 合并连续点击, 避免每下都重排全书
    }

    private void toggleMenu(boolean show) {
        menuShown = show;
        reader.findViewById(0x7101).setVisibility(show ? View.VISIBLE : View.GONE);
        reader.findViewById(0x7102).setVisibility(show ? View.VISIBLE : View.GONE);
        menuPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) { catalogPanel.setVisibility(View.GONE); catalogShown = false; }
    }

    // ---------------- 打开/关闭 ----------------
    private void openBook(final ReadBook bk) {
        shelf.setVisibility(View.GONE);
        reader.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        loading.setTextColor(cfg.fontColor);
        loading.setBackgroundColor(cfg.bgColor);
        bookTitle.setText(bk.name);
        ui.post(new Runnable() {
            public void run() {
                if (!bk.prepare(SimpleReadActivity.this)) {
                    toast("%1$s这本书不存在".replace("%1$s", bk.name));
                    closeBook(); return;
                }
                current = bk;
                page.open(bk, cfg, bk.progress);
                loading.setVisibility(View.GONE);
                seek.setProgress((int) (page.progress() * 1000));
            }
        });
    }

    private void closeBook() {
        saveProgress();
        toggleMenu(false);
        reader.setVisibility(View.GONE);
        shelf.setVisibility(View.VISIBLE);
        current = null;
        refreshBooks();
    }

    private void saveProgress() {
        if (current == null) return;
        current.progress = page.progress();
        current.lastRead = System.currentTimeMillis();
        current.save(this);
        cfg.save(this);
    }

    // ---------------- 导入 ----------------
    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(i, REQ_IMPORT); }
        catch (Throwable e) { toast("未找到相关程序"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMPORT || res != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        int ok = 0;
        for (Uri u : uris) {
            try {
                InputStream in = getContentResolver().openInputStream(u);
                byte[] raw = ReadBook.readAll(in);
                if (raw.length > 30 * 1024 * 1024) { toast("文件过大，跳过"); continue; }
                ReadBook bk = new ReadBook();
                bk.id = "b" + System.currentTimeMillis() + "_" + ok;
                bk.name = ReadBook.displayName(this, u);
                bk.lastRead = System.currentTimeMillis();
                bk.progress = 0;
                bk.writeBytes(this, raw);
                bk.save(this);
                ok++;
            } catch (Throwable e) { toast("导入失败: " + u.getLastPathSegment()); }
        }
        toast(ok > 0 ? "共导入 " + ok + " 个文件" : "没有可导入的文件了");
        refreshBooks();
    }

    // ---------------- Host ----------------
    public void onMenuTap() { toggleMenu(!menuShown); }
    public void onLoading(boolean show) { if (loading != null) loading.setVisibility(show ? View.VISIBLE : View.GONE); }
    public void onProgress(float p, int chapter) {
        if (seek != null) seek.setProgress((int) (p * 1000));
        if (progressLabel != null) {
            String ch = current != null && chapter >= 0 && chapter < current.chapters.size()
                    ? current.chapters.get(chapter).title : "";
            progressLabel.setText(String.format("%.2f%%   %s", p * 100, ch));
        }
    }
    public void onBookEnd(boolean atEnd) { toast(atEnd ? "寡人已阅" : "已经是第一页"); }

    // ---------------- 亮度 / 摇一摇 / 生命周期 ----------------
    private void applyBrightness() {
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = Math.max(0.05f, Math.min(1f, cfg.brightness));
            getWindow().setAttributes(lp);
        } catch (Throwable ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        applyBrightness();
        if (sensor != null) sensor.registerListener(this, sensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
    }
    @Override protected void onPause() { saveProgress(); if (sensor != null) sensor.unregisterListener(this); super.onPause(); }
    @Override protected void onDestroy() { saveProgress(); if (sensor != null) sensor.unregisterListener(this); super.onDestroy(); }

    public void onSensorChanged(SensorEvent e) {
        if (current == null) return;
        float ax = e.values[0], ay = e.values[1], az = e.values[2];
        long now = System.currentTimeMillis();
        if (now - lastShake < 1200) { lastAx = ax; lastAy = ay; lastAz = az; return; }
        float d = Math.abs(ax - lastAx) + Math.abs(ay - lastAy) + Math.abs(az - lastAz);
        if (d > 22) { lastShake = now; toggleMenu(!menuShown); toast("摇一摇唤出菜单"); }
        lastAx = ax; lastAy = ay; lastAz = az;
    }
    public void onAccuracyChanged(Sensor s, int a) {}

    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); }

    @Override public void onBackPressed() {
        if (catalogShown) { catalogPanel.setVisibility(View.GONE); catalogShown = false; return; }
        if (menuShown) { toggleMenu(false); return; }
        if (reader.getVisibility() == View.VISIBLE) { closeBook(); return; }
        if (selecting) { stopSelect(); return; }
        super.onBackPressed();
    }

    // ---------------- 工具 ----------------
    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("关于 · 简单阅读")
            .setMessage("这是一个只支持 txt 文件的小清新（误）阅读器。\n\n"
                + "使用帮助：\n"
                + "· 首页点「导入 TXT」选手机里的文件，长按卡片可多选删除\n"
                + "· 阅读界面点中间区域唤出设置菜单，点左右（或上下）翻页\n"
                + "· 摇一摇或点菜单区域可以唤出设置菜单\n"
                + "· 亮度、字号、行距、字距、边距、字色、背景色、章节目录、进度记忆\n\n"
                + "身体和灵魂总有一个要在路上")
            .setPositiveButton("完成", null).show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static String humanSize(long n) {
        if (n < 1024) return n + "B";
        if (n < 1024 * 1024) return String.format("%.1fKB", n / 1024f);
        return String.format("%.1fMB", n / 1024f / 1024f);
    }
    private static String humanTime(long t) {
        if (t <= 0) return "未读";
        return new java.text.SimpleDateFormat("MM月dd日", java.util.Locale.getDefault()).format(new java.util.Date(t));
    }

    private android.graphics.drawable.GradientDrawable pill(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(10));
        return g;
    }
    private TextView smallBtn(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(0xFFE8ECF4); t.setTextSize(12);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(10), dp(7), dp(10), dp(7));
        t.setBackground(pill(0xFF333A55));
        t.setOnClickListener(l);
        return t;
    }
}
