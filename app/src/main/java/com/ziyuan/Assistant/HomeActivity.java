package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.ClipboardManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.os.Build;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;


/** 首页：工具箱（纯黑/浅蓝主题切换 · 全屏）——小工具首页复刻版 */
public class HomeActivity extends Activity {

    // ---------- 主题（纯黑 / 浅蓝） ----------
    private void updateMedStatus() {
        try {
            TextView st = findViewById(R.id.medStatus);
            if (st == null) return;
            org.json.JSONArray plans = MedPlanActivity.load(this);
            if (plans.length() == 0) {
                st.setText("🌱 还没有计划，点进去添加");
                return;
            }
            String next = null;
            String now = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
            for (int i = 0; i < plans.length(); i++) {
                org.json.JSONObject o = plans.optJSONObject(i);
                if (o == null) continue;
                org.json.JSONArray ts = o.optJSONArray("times");
                if (ts == null) continue;
                for (int t = 0; t < ts.length(); t++) {
                    String tt = ts.optString(t);
                    if (tt.compareTo(now) >= 0 && (next == null || tt.compareTo(next) < 0)) next = tt;
                }
            }
            if (next == null) {
                // 今天都过了, 找全天最早
                for (int i = 0; i < plans.length(); i++) {
                    org.json.JSONObject o = plans.optJSONObject(i);
                    org.json.JSONArray ts = o == null ? null : o.optJSONArray("times");
                    if (ts == null) continue;
                    for (int t = 0; t < ts.length(); t++) {
                        String tt = ts.optString(t);
                        if (next == null || tt.compareTo(next) < 0) next = tt;
                    }
                }
                if (next != null) { st.setText("💊 " + plans.length() + " 个计划 · 明天 " + next); return; }
            }
            st.setText("💊 " + plans.length() + " 个计划 · 下次 " + (next == null ? "--:--" : next));
        } catch (Throwable ignored) {}
    }

    private void setupCalendar() {
        TapGuard.guard(findViewById(R.id.calendarCard));
        findViewById(R.id.calendarCard).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, CalendarActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardMed));
        findViewById(R.id.cardMed).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, MedPlanActivity.class)); }
        });
    }

    private final android.os.Handler calTick = new android.os.Handler();
    private final Runnable calTickRun = new Runnable() { public void run() {
        calTick.postDelayed(this, 30_000);
    }};

    private float swX, swY; private boolean swDone;


    private float dp2(float v) { return v * getResources().getDisplayMetrics().density; }

    private LinearLayout panBox;
    private int pageIdx = 0; // 0=首页 1=媒体 2=设置
    private FrameLayout pageHost;
    private android.widget.ScrollView favScroll;
    private android.widget.ScrollView aboutScroll;
    private LinearLayout favBox;
    private SettingsPageView pageSettings;
    private boolean pagesBuilt = false;
    private CapsuleBottomBar bottomBar;
    private String lastCapsule = null;
    private static final int[] MEDIA_CARDS = {R.id.cardPlayer, R.id.cardDownload, R.id.cardVideoDl, R.id.cardSpider, R.id.cardXhs, R.id.cardLive};

    /** 三态导航: 0=首页 1=媒体 2=设置(内嵌页) */
    private void switchToPage(int p) {
        if (pageIdx == p) return;
        applyPage(p);
    }

    /** 无守卫的页面应用：0=首页 1=媒体 2=设置（设置已内嵌为本页） */
    private void applyPage(int p) {
        if (!pagesBuilt) buildPages();
        pageIdx = p;
        if (bottomBar != null) bottomBar.setActiveTag(p == 0 ? "home" : p == 1 ? "media" : p == 2 ? "pan" : p == 4 ? "fav" : "set");
        if (p == 3 && pageSettings != null) pageSettings.refresh();
        pageHost.getChildAt(0).setVisibility(p == 0 ? View.VISIBLE : View.GONE);
        pageHost.getChildAt(1).setVisibility(p == 1 ? View.VISIBLE : View.GONE);
        pageHost.getChildAt(2).setVisibility(p == 2 ? View.VISIBLE : View.GONE);
        pageHost.getChildAt(3).setVisibility(p == 3 ? View.VISIBLE : View.GONE);
        // 懒加载页按 tag 定位(添加顺序不固定)
        for (int i = 4; i < pageHost.getChildCount(); i++) {
            View c = pageHost.getChildAt(i);
            String tg = c.getTag() != null ? c.getTag().toString() : "";
            if ("fav".equals(tg)) c.setVisibility(p == 4 ? View.VISIBLE : View.GONE);
            else if ("about".equals(tg)) c.setVisibility(p == 5 ? View.VISIBLE : View.GONE);
        }
        if (p == 4) buildFavPage();
        // 页面切换过渡：淡入 + 轻微上滑
        try {
            View col = pageHost;
            col.setAlpha(0f);
            col.setTranslationY(26f);
            col.animate().alpha(1f).translationY(0f).setDuration(230).setInterpolator(
                    new android.view.animation.DecelerateInterpolator(1.4f)).start();
        } catch (Throwable ignored) { }
    }

    /** 把单列卡片流拆成 首页/媒体 两个子页 + 内嵌设置页，装入统一页容器 */
    private void buildPages() {
        pagesBuilt = true;
        android.view.ViewGroup toolColumn = (android.view.ViewGroup) findViewById(R.id.toolColumn);
        ScrollView homeScroll = null;
        for (int i = 0; i < toolColumn.getChildCount(); i++) {
            View c = toolColumn.getChildAt(i);
            if (c instanceof ScrollView) { homeScroll = (ScrollView) c; break; }
        }
        if (homeScroll != null) homeScroll.setVerticalScrollBarEnabled(false);
        LinearLayout cardsBox = (LinearLayout) homeScroll.getChildAt(0);
        // 媒体页
        ScrollView mediaScroll = new ScrollView(this);
        mediaScroll.setFillViewport(true);
        mediaScroll.setVerticalScrollBarEnabled(false);
        LinearLayout mediaBox = new LinearLayout(this);
        mediaBox.setOrientation(LinearLayout.VERTICAL);
        float den = getResources().getDisplayMetrics().density;
        mediaBox.setPadding((int) (18 * den), (int) (20 * den), (int) (18 * den), (int) (50 * den));
        boolean first = true;
        for (int id : MEDIA_CARDS) {
            View card = findViewById(id);
            int idx = -1;
            for (int i = 0; i < cardsBox.getChildCount(); i++) if (cardsBox.getChildAt(i) == card) { idx = i; break; }
            if (idx < 0) continue;
            cardsBox.removeView(card);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
            if (first) { lp.topMargin = 0; first = false; }
            String resName = getResources().getResourceEntryName(id);
            View wrapped = wrapWithHeart(card, "m:" + resName);
            wrapped.setLayoutParams(lp);
            mediaBox.addView(wrapped);
        }
        mediaBox.addView(makeCard("网络流", "🌐", "netstream", mediaBox));
        mediaBox.addView(makeCard("本地文件", "📁", "localfile", mediaBox));
        mediaScroll.addView(mediaBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        // 页容器
        pageHost = new FrameLayout(this);
        int homeIdx = toolColumn.indexOfChild(homeScroll);
        toolColumn.removeView(homeScroll);
        pageHost.addView(homeScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageHost.addView(mediaScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // 排盘页(第2页): 三张排盘卡片
        ScrollView panScroll = new ScrollView(this);
        panScroll.setFillViewport(true);
        panScroll.setVerticalScrollBarEnabled(false);
        panBox = new LinearLayout(this);
        panBox.setOrientation(LinearLayout.VERTICAL);
        panBox.setPadding((int) (18 * den), (int) (20 * den), (int) (18 * den), (int) (50 * den));
        panBox.addView(panCard("我的api设置", "🔑", "api"));
        panBox.addView(panCard("小六壬排盘", "🔮", "mystic-six-ren"));
        panBox.addView(panCard("六爻排盘", "☯️", "liu-yao"));
        panBox.addView(panCard("梅花易数", "⚡", "meihua"));
        panBox.addView(panCard("奇门遁甲", "🌀", "qimen"));
        panBox.addView(panCard("八字命理排盘", "🌟", "bazi"));
        panScroll.addView(panBox, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        panScroll.setVisibility(View.GONE);
        pageHost.addView(panScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pageSettings = new SettingsPageView(this);
        pageSettings.setVisibility(View.GONE);
        pageHost.addView(pageSettings, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        toolColumn.addView(pageHost, homeIdx, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        // 首页剩余卡片加💖
        for (int i = 0; i < cardsBox.getChildCount(); i++) {
            View c = cardsBox.getChildAt(i);
            if (!"card".equals(String.valueOf(c.getTag()))) continue;
            if ("1".equals(c.getTag(R.id.tag_hearted))) continue;
            c.setTag(R.id.tag_hearted, "1");
            String resName = c.getId() != View.NO_ID ? getResources().getResourceEntryName(c.getId()) : ("hc" + i);
            android.view.ViewGroup.LayoutParams olp = c.getLayoutParams();
            View wrapped = wrapWithHeart(c, "h:" + resName);
            cardsBox.addView(wrapped, i, olp);
        }
        // buildPages 晚于首跑的主题遍历, 这里补一次, 保证排盘卡跟随明暗
        applyTraversal(toolColumn, getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false));
        // 卡片改为左右两列
        twoColumnify(cardsBox);
        twoColumnify(mediaBox);
        twoColumnify(panBox);
    }

    /** 排盘页卡片(以首页第一卡为标准: 28dp图标块+15sp粗标题+14dp内边距) */
    private View panCard(String title, String emoji, final String key) {
        return makeCard(title, emoji, key, panBox);
    }

    private View makeCard(String title, String emoji, final String key, final LinearLayout box) {
        LinearLayout card = new LinearLayout(this);
        card.setTag("card");
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) dp2(14), (int) dp2(14), (int) dp2(14), (int) dp2(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (box == null || box.getChildCount() == 0) ? 0 : (int) dp2(14); // 第一卡对齐其他页
        card.setLayoutParams(lp);
        card.setBackgroundResource(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false)
                ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView icon = new TextView(this);
        icon.setTag("icon");
        icon.setText(emoji);
        icon.setTextSize(15);
        icon.setGravity(android.view.Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams((int) dp2(28), (int) dp2(28)));
        TextView t = new TextView(this);
        t.setTag("title");
        t.setText(title);
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(getResources().getColor(R.color.textMain));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = (int) dp2(10);
        row.addView(t, tlp);
        card.addView(row);
        card.setClickable(true);
        if ("api".equals(key)) {
            TapGuard.guard(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(HomeActivity.this, PanApiActivity.class)); }
            });
        } else if ("netstream".equals(key)) {
            TapGuard.guard(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showUrlInput(); }
            });
        } else if ("localfile".equals(key)) {
            TapGuard.guard(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { pickLocal(); }
            });
        } else if ("mystic-six-ren".equals(key) || "liu-yao".equals(key) || "meihua".equals(key)) {
            TapGuard.guard(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent it = new Intent(HomeActivity.this, PanViewerActivity.class);
                    it.putExtra("key", key); it.putExtra("title", title);
                    startActivity(it);
                }
            });
        } else if ("bazi".equals(key)) {
            TapGuard.guard(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent it = new Intent(HomeActivity.this, PanViewerActivity.class);
                    it.putExtra("key", "bazi"); it.putExtra("title", title);
                    startActivity(it);
                }
            });
        } else if ("qimen".equals(key)) {
            TapGuard.guard(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent it = new Intent(HomeActivity.this, PanViewerActivity.class);
                    it.putExtra("key", "qimen"); it.putExtra("title", title);
                    startActivity(it);
                }
            });
        }
        // 💖改成与首页卡一致的悬浮覆盖层(右侧), 这样标题可被限定为固定宽度并真正跑马灯滚动, 对齐也统一
        return wrapWithHeart(card, key);
    }


    private void collectCards(android.view.ViewGroup vg, java.util.List<View> out) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c instanceof android.view.ViewGroup) collectCards((android.view.ViewGroup) c, out);
            if (c.getTag() != null && "card".equals(c.getTag().toString())) out.add(c);
        }
    }

    @Override
    public void onBackPressed() {
        // 收藏页/关于页: 返回到第一个可见页, 不退出应用
        if (pageIdx == 4 || pageIdx == 5) {
            android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
            int first = !sp.getBoolean("hideHome", false) ? 0
                    : !sp.getBoolean("hideMedia", false) ? 1
                    : !sp.getBoolean("hidePan", false) ? 2 : 3;
            switchToPage(first);
            return;
        }
        super.onBackPressed();
    }

    private void syncBarTag() {
        if (bottomBar == null) return;
        bottomBar.setActiveTag(pageIdx == 0 ? "home" : pageIdx == 1 ? "media" : pageIdx == 2 ? "pan" : pageIdx == 4 ? "fav" : pageIdx == 5 ? "fav" : "set");
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private final java.util.HashMap<String, String> TITLES = new java.util.HashMap<>();
    private final java.util.HashMap<String, View> CARD_VIEWS = new java.util.HashMap<>();
    private final java.util.HashMap<String, java.util.List<android.widget.TextView>> HEART_VIEWS = new java.util.HashMap<>();
    private final java.util.HashMap<String, Boolean> FAV_STATE = new java.util.HashMap<>();
    private final java.util.HashMap<String, String> ICONS = new java.util.HashMap<>();
    private String k2icon(String key) {
        if (key.startsWith("m:")) return "📺";
        if (key.startsWith("h:")) return "🏠";
        String[] info = CARD_INFO.get(key);
        return info != null ? info[1] : "📌";
    }

    private void registerHeart(String key, android.widget.TextView heart) {
        java.util.List<android.widget.TextView> l = HEART_VIEWS.get(key);
        if (l == null) { l = new java.util.ArrayList<>(); HEART_VIEWS.put(key, l); }
        l.add(heart);
        boolean f = (getSharedPreferences("settings", MODE_PRIVATE).getString("favCards", "") + ",").contains("," + key + ",");
        heart.setText(f ? "💖" : "🤍");
    }

    private void toggleFav(String key) {
        android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String k : sp.getString("favCards", "").split(",")) if (!k.isEmpty()) set.add(k);
        boolean nowFav;
        if (set.contains(key)) { set.remove(key); nowFav = false; }
        else { set.add(key); nowFav = true; }
        StringBuilder sb = new StringBuilder();
        for (String k : set) { if (sb.length() > 0) sb.append(','); sb.append(k); }
        sp.edit().putString("favCards", sb.toString()).apply();
        java.util.List<android.widget.TextView> l = HEART_VIEWS.get(key);
        if (l != null) for (android.widget.TextView h : l) h.setText(nowFav ? "💖" : "🤍");
        android.widget.Toast.makeText(this, nowFav ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
        if (pageIdx == 4) buildFavPage();
    }

    private void collectTextViews(android.view.ViewGroup vg, java.util.List<TextView> out) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c instanceof TextView) out.add((TextView) c);
            else if (c instanceof android.view.ViewGroup) collectTextViews((android.view.ViewGroup) c, out);
        }
    }

    private View wrapWithHeart(final View card, final String key) {
        if (card.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) card.getParent()).removeView(card);
        if (!ICONS.containsKey(key) || !TITLES.containsKey(key)) {
            String bestIcon = "", bestTitle = "";
            if (card instanceof android.view.ViewGroup) {
                java.util.ArrayList<TextView> tvs2 = new java.util.ArrayList<>();
                collectTextViews((android.view.ViewGroup) card, tvs2);
                for (TextView tv : tvs2) {
                    String x = tv.getText().toString().trim();
                    if (x.isEmpty() || x.length() > 12) continue;
                    boolean hasText = false;
                    for (char ch : x.toCharArray()) if ((ch >= 0x4E00 && ch <= 0x9FFF) || Character.isLetterOrDigit(ch) && ch < 128) { hasText = true; break; }
                    if (hasText) { if (x.length() > bestTitle.length()) bestTitle = x; }
                    else if (bestIcon.isEmpty()) bestIcon = x; // 纯emoji=图标
                }
            }
            if (!ICONS.containsKey(key)) ICONS.put(key, bestIcon.isEmpty() ? k2icon(key) : bestIcon);
            if (!TITLES.containsKey(key)) {
                String name = key.startsWith("m:") || key.startsWith("h:") ? key.substring(2) : key;
                bestTitle = bestTitle.isEmpty() ? (CN_NAMES.containsKey(name) ? CN_NAMES.get(name) : (name.startsWith("card") ? name.substring(4) : name)) : bestTitle;
                if (bestTitle.isEmpty() || bestTitle.startsWith("hc")) bestTitle = "卡片";
                TITLES.put(key, bestTitle);
            }
        }
        CARD_VIEWS.put(key, card);
        android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isFav = (sp.getString("favCards", "") + ",").contains("," + key + ",");
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        android.view.ViewGroup.LayoutParams clp = card.getLayoutParams();
        wrap.addView(card, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.TextView heart = new android.widget.TextView(this);
        heart.setText(isFav ? "💖" : "🤍");
        heart.setTextSize(16);
        heart.setPadding((int) dp2(10), (int) dp2(4), (int) dp2(10), (int) dp2(4));
        android.widget.FrameLayout.LayoutParams hlp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL);
        registerHeart(key, heart);
        TapGuard.guard(heart);
        heart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggleFav(key); }
        });
        wrap.addView(heart, hlp);
        wrap.setTag("card");
        return wrap;
    }

    private void openPanCard(String key, String title) {
        if ("api".equals(key)) { startActivity(new Intent(this, PanApiActivity.class)); return; }
        if ("netstream".equals(key)) { showUrlInput(); return; }
        if ("localfile".equals(key)) { pickLocal(); return; }
        Intent it = new Intent(this, PanViewerActivity.class);
        it.putExtra("key", key); it.putExtra("title", title);
        startActivity(it);
    }

    public void openAboutPage() {
        if (aboutScroll == null) {
            aboutScroll = new android.widget.ScrollView(this);
            aboutScroll.setFillViewport(true);
            aboutScroll.setVerticalScrollBarEnabled(false);
            aboutScroll.setTag("about");
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding((int) dp2(18), (int) dp2(20), (int) dp2(18), (int) dp2(110));
            boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
            int txt = dark ? 0xFFFFFFFF : 0xFF1F2329;
            TextView h = new TextView(this);
            h.setText("关于 · 功能说明");
            h.setTextSize(20);
            h.setTypeface(null, android.graphics.Typeface.BOLD);
            h.setTextColor(txt);
            box.addView(h);
            String[][] sec = {
                {"🏠 首页", "聊天室 / 日历 / 用药提醒 / 娱乐中心 / 网页转应用 等工具卡；每张卡右侧 🤍 可收藏到 💖 页。"},
                {"📺 媒体页", "播放器 / 下载器 / 视频下载 / 资源嗅探(剪贴板链接直进抖音B站YouTube) / 小红书 / 动态 / 直播；网络流(输入链接)与本地文件(选择视频)直接进播放器。"},
                {"🔮 排盘页", "我的api设置(AI配置+GitHub配置备份) / 小六壬(时间·时刻·数字·随机四种起课，道家/江氏双流派，六宫含空亡，地支/x星/六神/六亲与原版逐宫对齐，AI解析调用你的AI配置) / 六爻 / 梅花易数 / 奇门遁甲 / 八字命理——全部离线可收藏。"},
                {"💖 收藏页", "所有页面卡片右侧 🤍 一键收藏/取消；点收藏卡直接触发原功能。"},
                {"⚙️ 设置页", "平台账号登录(哔哩哔哩/抖音/YouTube/自定义，超4行卡内滚动) / 明暗模式切换 / 配置文件上传与下载(AI配置+收藏+下载历史+胶囊显示开关，独立卡片) / 剪贴板开关 / 下载通知 / 清除全部记录 / 关于。"},
                {"🌍 主题", "右上角 🌙/☀️ 切换纯黑/浅蓝两套主题，全页面跟随。"},
                {"⬇️ 底部胶囊", "首页/媒体/排盘/💖收藏/设置/播放器，可在设置页隐藏任意项(设置与💖常驻)；左右滑动切换页面。"}
            };
            for (String[] x : sec) {
                TextView t1 = new TextView(this);
                t1.setText(x[0]);
                t1.setTextSize(16);
                t1.setTypeface(null, android.graphics.Typeface.BOLD);
                t1.setTextColor(dark ? 0xFFE8C87A : 0xFFB8860B);
                t1.setPadding(0, (int) dp2(18), 0, (int) dp2(6));
                box.addView(t1);
                TextView t2 = new TextView(this);
                t2.setText(x[1]);
                t2.setTextSize(14);
                t2.setLineSpacing(0, 1.3f);
                t2.setTextColor(txt);
                box.addView(t2);
            }
            aboutScroll.addView(box, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            pageHost.addView(aboutScroll, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        applyAboutTheme();
        switchToPage(5);
    }

    private void applyAboutTheme() {
        if (aboutScroll == null) return;
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        LinearLayout box = (LinearLayout) aboutScroll.getChildAt(0);
        int txt = dark ? 0xFFFFFFFF : 0xFF1F2329;
        for (int i = 0; i < box.getChildCount(); i++) {
            View c = box.getChildAt(i);
            if (c instanceof TextView) {
                TextView t = (TextView) c;
                if (i == 0) t.setTextColor(txt);
                else if ((i % 2) == 1) t.setTextColor(dark ? 0xFFE8C87A : 0xFFB8860B);
                else t.setTextColor(txt);
            }
        }
    }

    private void buildFavPage() {
        if (favBox != null) {}
        if (favScroll == null) {
            favScroll = new android.widget.ScrollView(this);
            favScroll.setFillViewport(true);
            favScroll.setVerticalScrollBarEnabled(false);
            favBox = new LinearLayout(this);
            favBox.setOrientation(LinearLayout.VERTICAL);
            favBox.setPadding((int) dp2(18), (int) dp2(10), (int) dp2(18), (int) dp2(50)); // 左右留边+底部避开胶囊
            favScroll.setTag("fav");
            favScroll.addView(favBox, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            pageHost.addView(favScroll, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        favBox.removeAllViews();
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        int txt = dark ? 0xFFFFFFFF : 0xFF1F2329;
        String favs = getSharedPreferences("settings", MODE_PRIVATE).getString("favCards", "");
        if (favs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无收藏卡片\n点卡片右侧的 🤍 收藏");
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding((int) dp2(20), (int) dp2(80), (int) dp2(20), 0);
            empty.setTextColor(txt);
            favBox.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        int idx = 0;
        for (String k : favs.split(",")) {
            final String key = k;
            String title;
            if (k.startsWith("m:") || k.startsWith("h:")) {
                String name = k.substring(2);
                String tt = TITLES.containsKey(k) ? TITLES.get(k) : "";
                boolean emojiOnly = !tt.isEmpty();
                for (char ch : tt.toCharArray()) if ((ch >= 0x4E00 && ch <= 0x9FFF) || (ch < 128 && Character.isLetterOrDigit(ch))) { emojiOnly = false; break; }
                title = (!emojiOnly && !tt.isEmpty()) ? tt : (CN_NAMES.containsKey(name) ? CN_NAMES.get(name) : (name.startsWith("card") ? name.substring(4) : "卡片"));
            } else {
                String[] info = CARD_INFO.get(k);
                title = info != null ? info[0] : k;
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setTag("card");
            card.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
            card.setPadding((int) dp2(16), (int) dp2(14), (int) dp2(16), (int) dp2(14));
            if (k.startsWith("m:") || k.startsWith("h:")) card.setMinimumHeight((int) dp2(52));
            TextView tv = new TextView(this);
            String ico = ICONS.containsKey(k) ? ICONS.get(k) : k2icon(k);
            tv.setText(ico + " " + title);
            tv.setTextSize(15);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextColor(txt);
            tv.setTag("title");
            card.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView hv = new TextView(this);
            registerHeart(key, hv);
            hv.setText("💖"); // 在收藏列表内必然已收藏
            hv.setTextSize(16);
            hv.setPadding((int) dp2(8), 0, 0, 0);
            TapGuard.guard(hv);
            hv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toggleFav(key); }
            });
            card.addView(hv);
            TapGuard.guard(card);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    View oc = CARD_VIEWS.get(key);
                    if (oc != null) oc.performClick(); // 直接触发原卡功能, 不跳页
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = idx == 0 ? (int) dp2(20) : (int) dp2(14);
            if (key.startsWith("m:") || key.startsWith("h:")) {
                favBox.addView(card, lp);
            } else {
                String[] info = CARD_INFO.get(key);
                View pc = makeCard(info != null ? info[0] : key, info != null ? info[1] : "📌", key, favBox);
                favBox.addView(pc, lp);
            }
            idx++;
        }
        applyTraversal(favScroll, getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false));
        twoColumnify(favBox);
    }

    /** 把竖直卡片容器重排为左右两列：每两卡一行，行内各占一半宽，奇数补空位保持左对齐 */
    private void twoColumnify(LinearLayout box) {
        if (box == null) return;
        int n = box.getChildCount();
        if (n == 0) return;
        java.util.ArrayList<View> kids = new java.util.ArrayList<View>();
        for (int i = 0; i < n; i++) kids.add(box.getChildAt(i));
        box.removeAllViews();
        int gap = (int) dp2(10);
        for (int i = 0; i < kids.size(); i += 2) {
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = (i == 0) ? 0 : gap;
            row.setLayoutParams(rlp);
            for (int c = 0; c < 2; c++) {
                int ki = i + c;
                if (ki < kids.size()) {
                    View v = kids.get(ki);
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
                    if (c == 0) clp.rightMargin = gap / 2; else clp.leftMargin = gap / 2;
                    v.setLayoutParams(clp);
                    compactForTwoCol(v);
                    row.addView(v);
                } else {
                    View sp = new View(this);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 1, 1f);
                    slp.leftMargin = gap / 2;
                    row.addView(sp, slp);
                }
            }
            // 行内等高: 布局完成后把同行两张卡拉到同一高度，保证左右严格对齐
            row.post(new Runnable() {
                public void run() {
                    int h = 0;
                    for (int k = 0; k < row.getChildCount(); k++) {
                        View c = row.getChildAt(k);
                        if (isCardView(c)) h = Math.max(h, c.getHeight());
                    }
                    if (h <= 0) return;
                    for (int k = 0; k < row.getChildCount(); k++) {
                        View c = row.getChildAt(k);
                        if (!isCardView(c)) continue;
                        if (!(c.getLayoutParams() instanceof LinearLayout.LayoutParams)) continue;
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) c.getLayoutParams();
                        if (lp.height != h) { lp.height = h; c.setLayoutParams(lp); }
                    }
                }
            });
            box.addView(row);
        }
        // 布局完成后统一启动跑马灯: 标题超长(>4字)用 MARQUEE, 需在已 measure 后 setSelected 才真正滚动
        box.post(new Runnable() { public void run() { startMarquee(box); } });
    }

    /** 遍历容器, 对所有已设为 MARQUEE 的标题重新触发横向滚动 */
    private void startMarquee(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof TextView) {
                TextView tv = (TextView) c;
                if (tv.getEllipsize() == android.text.TextUtils.TruncateAt.MARQUEE) tv.setSelected(true);
            } else if (c instanceof android.view.ViewGroup) {
                startMarquee((android.view.ViewGroup) c);
            }
        }
    }

    /** 是否是卡片视图(直接卡片或带💖的包裹层) */
    private boolean isCardView(View v) {
        return "card".equals(String.valueOf(v.getTag())) || v instanceof android.widget.FrameLayout;
    }

    /** 两列紧凑化: 统一字号/内边距，右侧给悬浮💖让位，所有卡片同高同宽 */
    private void compactForTwoCol(View v) {
        // 💖包裹层(FrameLayout)只是容器: 内边距必须落在内层真卡上, 否则包裹层和内层各加一层 padding,
        // 卡片会被撑高变窄(网络流/本地文件是裸卡, 才显得"特别大")
        boolean wrapped = v instanceof android.widget.FrameLayout;
        android.view.ViewGroup card;
        if (wrapped) {
            v.setPadding(0, 0, 0, 0);
            if (((android.view.ViewGroup) v).getChildCount() == 0) return;
            View c0 = ((android.view.ViewGroup) v).getChildAt(0);
            if (!(c0 instanceof android.view.ViewGroup)) return;
            card = (android.view.ViewGroup) c0;
            android.view.ViewGroup.LayoutParams wlp = c0.getLayoutParams();
            if (wlp instanceof android.widget.FrameLayout.LayoutParams) {
                ((android.widget.FrameLayout.LayoutParams) wlp).gravity =
                        android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL;
                c0.setLayoutParams(wlp);
            }
            // 悬浮💖收紧，给标题让出宽度
            android.view.ViewGroup wg = (android.view.ViewGroup) v;
            if (wg.getChildCount() > 1 && wg.getChildAt(1) instanceof TextView) {
                TextView h = (TextView) wg.getChildAt(1);
                h.setTextSize(15f);
                h.setPadding((int) dp2(4), 0, (int) dp2(8), 0);
            }
        } else {
            if (!(v instanceof android.view.ViewGroup)) return;
            card = (android.view.ViewGroup) v;
        }
        // 右侧 30dp 固定给悬浮💖, 标题滚动时不会钻到爱心下面
        card.setPadding((int) dp2(12), (int) dp2(10), (int) dp2(wrapped ? 32 : 12), (int) dp2(10));
        card.setMinimumHeight((int) dp2(46));
        if (card instanceof LinearLayout) ((LinearLayout) card).setGravity(android.view.Gravity.CENTER_VERTICAL);
        compactText(card);
    }

    /** 两列下的卡内文字: 主标题 15sp(超过4字跑马灯)、副标题 10.5sp 单行、emoji 图标 24dp */
    private void compactText(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof TextView) {
                TextView tv = (TextView) c;
                CharSequence cs = tv.getText();
                String s = cs == null ? "" : cs.toString().trim();
                boolean pureSym = true; // 纯 emoji/符号 = 图标
                for (int p = 0; p < s.length(); p++) {
                    char ch = s.charAt(p);
                    if ((ch >= 0x4E00 && ch <= 0x9FFF) || (ch < 128 && Character.isLetterOrDigit(ch))) { pureSym = false; break; }
                }
                android.view.ViewGroup.LayoutParams lp = tv.getLayoutParams();
                if (pureSym) {
                    // 只有带固定方框的纯符号才是圆形图标块; 爱心等其它符号保持原样, 避免大小不一
                    boolean boxed = lp != null && lp.width > 0 && lp.width <= (int) dp2(32)
                            && lp.height > 0 && lp.height <= (int) dp2(32);
                    if (boxed) {
                        lp.width = (int) dp2(24); lp.height = (int) dp2(24);
                        tv.setLayoutParams(lp);
                        tv.setTextSize(13.5f);
                        tv.setGravity(android.view.Gravity.CENTER);
                    }
                    continue;
                }
                boolean sub = "sub".equals(String.valueOf(tv.getTag()));
                tv.setTextSize(sub ? 10.5f : 15f);
                tv.setSingleLine(true);
                if (sub) {
                    tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                } else {
                    int n = visibleTextCount(s);
                    if (n >= 4) {
                        // 满4字: 把标题宽度锁在约4个字, 超出部分用跑马灯横向滚动(开启横向滚动才会真正滚动)
                        // 右侧已固定留 32dp 给悬浮💖, 滚动文字不会钻到爱心下面
                        if (lp instanceof LinearLayout.LayoutParams) {
                            LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp;
                            llp.width = -2; llp.weight = 0;
                            if (llp.leftMargin > dp2(10)) llp.leftMargin = (int) dp2(7);
                            tv.setLayoutParams(llp);
                        }
                        tv.setMaxWidth((int) dp2(64));
                        tv.setHorizontallyScrolling(true);
                        tv.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                        tv.setMarqueeRepeatLimit(-1);
                        tv.setSelected(true);
                    } else {
                        tv.setMaxWidth(Integer.MAX_VALUE);
                        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    }
                }
                if (lp instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp;
                    if (llp.leftMargin > dp2(10)) llp.leftMargin = (int) dp2(7);
                    if (llp.topMargin > dp2(6)) llp.topMargin = (int) dp2(3);
                    tv.setLayoutParams(llp);
                }
            } else if (c instanceof android.view.ViewGroup) {
                android.view.ViewGroup.LayoutParams lp = c.getLayoutParams();
                if (lp != null && lp.width > 0 && lp.width <= (int) dp2(32) && lp.height > 0 && lp.height <= (int) dp2(32)) {
                    lp.width = (int) dp2(24); lp.height = (int) dp2(24);
                    c.setLayoutParams(lp);
                }
                compactText((android.view.ViewGroup) c);
            }
        }
    }

    /** 统计可见文字(中文/字母/数字)个数, emoji 与空白不计, 用于判断是否启用跑马灯 */
    private int visibleTextCount(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int p = 0; p < s.length(); p++) {
            char ch = s.charAt(p);
            if ((ch >= 0x4E00 && ch <= 0x9FFF) || (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) n++;
        }
        return n;
    }

    private static final java.util.HashMap<String, String> CN_NAMES = new java.util.HashMap<String, String>() {{
        put("cardPlayer", "播放器"); put("cardDownload", "下载器"); put("cardVideoDl", "视频下载");
        put("cardSpider", "资源嗅探"); put("cardXhs", "小红书");
        put("cardLive", "直播"); put("cardCalendar", "日历"); put("cardChat", "聊天室");
        put("cardMedicine", "用药提醒"); put("cardTapper", "点击器"); put("cardShortcut", "创建快捷键");
        put("cardWebShell", "网页转应用"); put("cardFun", "娱乐中心"); put("cardBili", "B站登录");
    }};

    private static final java.util.HashMap<String, String[]> CARD_INFO = new java.util.HashMap<String, String[]>() {{
        put("api", new String[]{"我的api设置", "🔑"});
        put("mystic-six-ren", new String[]{"小六壬排盘", "🔮"});
        put("liu-yao", new String[]{"六爻排盘", "☯️"});
        put("meihua", new String[]{"梅花易数", "⚡"});
        put("qimen", new String[]{"奇门遁甲", "🌀"});
        put("bazi", new String[]{"八字命理", "🌟"});
        put("netstream", new String[]{"网络流", "🌐"});
        put("localfile", new String[]{"本地文件", "📁"});
    }};

    public void refreshGlobalTheme() { applyTheme(); }

    private void applyTheme() {
        boolean dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        findViewById(R.id.toolRoot).setBackgroundColor(0x00000000); // 胶囊悬浮透明: 背景透出页面, 不另设实色条
        // 底部胶囊导航（SmoothBottomBar 风格）
        android.view.ViewGroup root = (android.view.ViewGroup) findViewById(R.id.toolRoot);
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            if (root.getChildAt(i) instanceof CapsuleBottomBar) root.removeViewAt(i);
        }
        android.content.SharedPreferences sp0 = getSharedPreferences("settings", MODE_PRIVATE);
        boolean hideHome = sp0.getBoolean("hideHome", false);
        boolean hideMedia = sp0.getBoolean("hideMedia", false);
        boolean hidePan = sp0.getBoolean("hidePan", false);
        boolean hidePlayer = sp0.getBoolean("hidePlayer", false);
        bottomBar = new CapsuleBottomBar(this, dark, new CapsuleBottomBar.OnTag() {
                public void onTag(String tag) {
                    if ("player".equals(tag)) {
                        showPlayChoice();
                        syncBarTag();
                    } else if ("fav".equals(tag)) {
                        buildFavPage();
                        switchToPage(4);
                    } else if ("home".equals(tag)) switchToPage(0);
                    else if ("media".equals(tag)) switchToPage(1);
                    else if ("pan".equals(tag)) switchToPage(2);
                    else if ("set".equals(tag)) switchToPage(3);
                }
            }, hideHome, hideMedia, hidePan, hidePlayer);
        bottomBar.setTag("bottombar");
        root.addView(bottomBar);
        lastCapsule = hideHome + "|" + hideMedia + "|" + hidePan + "|" + hidePlayer;
        findViewById(R.id.toolColumn).setBackgroundColor(dark ? 0xFF000000 : 0xFFEEF4FF);
        applyTraversal((android.view.ViewGroup) findViewById(R.id.toolColumn), dark);
    }

    private void applyTraversal(android.view.ViewGroup vg, boolean dark) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            String t = c.getTag() == null ? "" : c.getTag().toString();
            if (c instanceof TextView) {
                if (t.equals("title")) ((TextView) c).setTextColor(dark ? 0xFFFFFFFF : 0xFF1F2329);
                else if (t.equals("sub")) ((TextView) c).setTextColor(dark ? 0xFF9AA3AE : 0xFF8A94A6);
                else if (t.equals("icon")) c.setBackgroundResource(dark ? R.drawable.bg_btn_md3_dark : R.drawable.bg_btn_md3);
                else if (t.equals("chip")) {
                    ((TextView) c).setBackgroundResource(dark ? R.drawable.bg_chip_dark : R.drawable.bg_chip_off);
                    ((TextView) c).setTextColor(dark ? 0xFF9AA3AE : 0xFF8A94A6);
                }
            }
            // 只给真实卡片(LinearLayout)设边框; 💖包裹层(FrameLayout)跳过, 避免两层描边叠加导致粗细/亮度不一致
            if (t.equals("card") && !(c instanceof android.widget.FrameLayout)) {
                c.setBackgroundResource(dark ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3);
            }
            if (c instanceof android.view.ViewGroup) applyTraversal((android.view.ViewGroup) c, dark);
        }
    }

    // ---------- 沉浸全屏 ----------
    private void applyImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    private float swX0, swY0; private boolean swTracking;
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                swX0 = ev.getX(); swY0 = ev.getY(); swTracking = true; break;
            case android.view.MotionEvent.ACTION_UP: {
                if (swTracking) {
                    swTracking = false;
                    float dx = ev.getX() - swX0, dy = ev.getY() - swY0;
                    int edge = (int) (getResources().getDisplayMetrics().widthPixels * 0.08f);
                    if (Math.abs(dx) > 150 && Math.abs(dx) > Math.abs(dy) * 1.5f
                            && swX0 > edge && swX0 < getResources().getDisplayMetrics().widthPixels - edge) {
                        java.util.ArrayList<Integer> pages = new java.util.ArrayList<>();
                        android.content.SharedPreferences spv = getSharedPreferences("settings", MODE_PRIVATE);
                        if (!spv.getBoolean("hideHome", false)) pages.add(0);
                        if (!spv.getBoolean("hideMedia", false)) pages.add(1);
                        if (!spv.getBoolean("hidePan", false)) pages.add(2);
                        pages.add(4); // 收藏页固定可滑到
                        pages.add(3);
                        int pi = pages.indexOf(pageIdx);
                        if (pi < 0) pi = 0;
                        int target = pages.get((pi + (dx < 0 ? 1 : pages.size() - 1)) % pages.size());
                        switchToPage(target);
                    }
                }
                break;
            }
            case android.view.MotionEvent.ACTION_CANCEL: swTracking = false; break;
        }
        return super.dispatchTouchEvent(ev);
    }
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecManager.init(getApplicationContext());
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.File dir = getExternalFilesDir(null);
                    if (dir == null) dir = getFilesDir();
                    java.io.File f = new java.io.File(dir, "crash.txt");
                    java.io.FileWriter fw = new java.io.FileWriter(f, true);
                    fw.append("\n==== " + new java.util.Date().toString() + " thread=" + t.getName() + " ====\n");
                    fw.append(android.util.Log.getStackTraceString(e));
                    fw.close();
                } catch (Throwable e2) { }
                Thread.setDefaultUncaughtExceptionHandler(null);
                throw new RuntimeException(e);
            }
        });
        setContentView(R.layout.activity_toolbox);
        Immersive.hide(this);

        // 启动即创建通知渠道 + 申请通知权限(Android 13+)
        try { MedPlanActivity.ensureChannel(this); } catch (Throwable ignored) {}
        try { MedWatchService.ensure(this); } catch (Throwable ignored) {}
        if (android.os.Build.VERSION.SDK_INT >= 33
            && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 990);
        }

        if (getIntent() != null && getIntent().getBooleanExtra("goto_play", false)) showPlayChoice();
        setupCalendar();
        applyTheme();
        applyImmersive();

        // 初始页=第一个未被隐藏的页
        android.content.SharedPreferences spi = getSharedPreferences("settings", MODE_PRIVATE);
        if (!spi.getBoolean("hideHome", false)) applyPage(0);
        else if (!spi.getBoolean("hideMedia", false)) applyPage(1);
        else if (!spi.getBoolean("hidePan", false)) applyPage(2);
        else applyPage(4); // 收藏页(胶囊💖)


        // 复刻壳：卡片 → 嗅探弹窗；剪贴板有链接则直接进嗅探
        TapGuard.guard(findViewById(R.id.cardPlayer));
        findViewById(R.id.cardPlayer).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String clip = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("clipboard", true) ? clipUrl() : null;
                if (clip != null && !supportedSite(clip)) clip = null;
                if (clip != null) {
                    Intent i = new Intent(HomeActivity.this, SniffActivity.class);
                    i.putExtra("input", clip);
                    startActivity(i);
                } else {
                    showSniffDialog();
                }
            }
        });
        TapGuard.guard(findViewById(R.id.cardDownload));
        findViewById(R.id.cardDownload).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, RecordActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardVideoDl));
        findViewById(R.id.cardVideoDl).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showVdlDialog(); }
        });
        TapGuard.guard(findViewById(R.id.cardXhs));
        findViewById(R.id.cardXhs).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, XhsActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardLive));
        findViewById(R.id.cardLive).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent lv = new Intent(HomeActivity.this, DynaActivity.class);
                lv.putExtra("mode", "live");
                startActivity(lv);
            }
        });
        // ===== 跳过广告: 点卡片进入管理页(页内确认开启) =====

        TapGuard.guard(findViewById(R.id.cardTapper));
        findViewById(R.id.cardTapper).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, TapperActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardMetronome));
        findViewById(R.id.cardMetronome).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, MetronomeActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardSpider));
        findViewById(R.id.cardSpider).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, SpiderActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardChat));
        findViewById(R.id.cardChat).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, ChatActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardWeb2Apk));
        findViewById(R.id.cardWeb2Apk).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, Web2ApkActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardGkd));
        findViewById(R.id.cardGkd).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, GkdActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardH5Ed));
        findViewById(R.id.cardH5Ed).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, H5ProjectListActivity.class)); }
        });
        TapGuard.guard(findViewById(R.id.cardEnt));
        findViewById(R.id.cardEnt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(HomeActivity.this, EntertainmentActivity.class)); }
        });

        TapGuard.guard(findViewById(R.id.cardShortcut));
        findViewById(R.id.cardShortcut).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(HomeActivity.this, ShortcutActivity.class)); }
                catch (Throwable t) {
                    android.widget.Toast.makeText(HomeActivity.this, "打开失败: " + t, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        android.content.SharedPreferences spc = getSharedPreferences("settings", MODE_PRIVATE);
        String sig = spc.getBoolean("hideHome", false) + "|" + spc.getBoolean("hideMedia", false) + "|" + spc.getBoolean("hidePan", false) + "|" + spc.getBoolean("hidePlayer", false);
        if (!sig.equals(lastCapsule)) { applyTheme(); } // 下载配置/确认胶囊后回首页自动刷新
        if (pageIdx == 4) { buildFavPage(); applyPage(4); }
        if (bottomBar != null) bottomBar.setActiveTag(pageIdx == 0 ? "home" : pageIdx == 1 ? "media" : pageIdx == 2 ? "pan" : pageIdx == 4 ? "fav" : "set"); // 滑块对齐当前页
        if (pageIdx == 2 && pageSettings != null) pageSettings.refresh(); // 平台账号登录返回, 刷新登录态
        calTick.post(calTickRun);
        updateMedStatus();
        try { MedPlanActivity.checkAndNotifyDue(this); } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        calTick.removeCallbacks(calTickRun);
    }

    /** 🗄️ 局域网共享弹窗：开关 + 电脑访问地址 */
    public void showLanDialog() {
        boolean on = LanShareServer.isRunning();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFFFFFFFF);
        String url = "http://" + LanShareServer.localIp() + ":" + LanShareServer.getPort();
        if (on) {
            status.setText("已开启，电脑浏览器访问：\n" + url + "\n（可查看并打开记录中的链接）");
        } else {
            status.setText("已关闭。开启后，同一 WiFi 下\n电脑浏览器可访问并打开记录中的链接。");
        }
        box.addView(status);

        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, pad / 2, 0, 0);
        TextView swLabel = new TextView(this);
        swLabel.setText("局域网共享");
        swLabel.setTextSize(16);
        swLabel.setTextColor(0xFFFFFFFF);
        swRow.addView(swLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        final android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(on);
        swRow.addView(sw);
        box.addView(swRow);

        sw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (sw.isChecked()) {
                    if (android.os.Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
                        && !getSharedPreferences("settings", MODE_PRIVATE).getBoolean("permAsked", false)) {
                        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("permAsked", true).apply();
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 900);
                    }
                    try { LiveProxy.start(); } catch (Throwable ignored) {}
                    LanShareServer.start();  // 同步绑定：返回时端口必就绪
                    startService(new Intent(HomeActivity.this, LanShareService.class));
                    status.setText("已开启，电脑浏览器访问：\nhttp://" + LanShareServer.localIp() + ":" + LanShareServer.getPort());
                } else {
                    stopService(new Intent(HomeActivity.this, LanShareService.class));
                    status.setText("已关闭。");
                }
            }
        });

        new GlassDialog.Builder(this)
            .setTitle("🗄️ 局域网共享")
            .setView(box)
            .setPositiveButton("完成", null)
            .show();
    }

    /** 只允许抖音/B站/YouTube 直进 */
    private boolean supportedSite(String u) {
        if (u == null) return false;
        return u.contains("douyin.com") || u.contains("b23.tv") || u.contains("bilibili.com")
                || u.contains("youtube.com") || u.contains("youtu.be");
    }

    /** 播放入口选择：网络流 / 本地文件 */
    private void showPlayChoice() {
        new GlassDialog.Builder(this)
            .setTitle("播放")
            .setItems(new String[]{"网络流（输入链接）", "本地文件（选择视频）"}, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (w == 0) showUrlInput();
                    else pickLocal();
                }
            }).show();
    }

    private void showUrlInput() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("http(s)://");
        et.setSingleLine(true);
        new GlassDialog.Builder(this)
            .setTitle("网络流")
            .setView(et)
            .setPositiveButton("播放", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    playAny(et.getText().toString().trim());
                }
            }).setNegativeButton("取消", null).show();
    }

    private static final int REQ_PICK_LOCAL = 9911;

    private void pickLocal() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            i.setType("video/*");
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            startActivityForResult(android.content.Intent.createChooser(i, "选择视频"), REQ_PICK_LOCAL);
        } catch (Throwable e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void playAny(String url) {
        if (url == null || url.length() == 0) return;
        Intent i = new Intent(this, NativePlayerActivity.class);
        i.putExtra("url", url);
        i.putExtra("title", "播放");
        i.putExtra("kernel", "native");
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == REQ_PICK_LOCAL && resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
            Intent i = new Intent(this, NativePlayerActivity.class);
            i.putExtra("url", uri.toString());
            i.putExtra("title", "本地视频");
            i.putExtra("kernel", "native");
            i.setData(uri);
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 剪贴板里抽 http 链接，没有返回 null */
    private String clipUrl() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemAt(0) == null) return null;
            CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
            if (t == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(t.toString());
            return m.find() ? m.group() : null;
        } catch (Throwable e) { return null; }
    }

    static String dyAutoResolve(String text, String savedId) {
        if (text == null || text.length() == 0) {
            return savedId.length() > 0 ? "https://live.douyin.com/" + savedId : null;
        }
        String t = text.trim();
        if (t.matches("[0-9]{6,20}") || (t.matches("[0-9A-Za-z]{6,24}") && t.matches(".*[A-Za-z].*") && t.matches(".*[0-9].*")))
            return "https://live.douyin.com/" + t;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("live\\.douyin\\.com/([0-9A-Za-z]{6,24})").matcher(t);
        if (m.find()) return "https://live.douyin.com/" + m.group(1);
        m = java.util.regex.Pattern.compile("(?:search/|keyword=)([0-9]{6,20}|(?=.*[A-Za-z])(?=.*[0-9])[0-9A-Za-z]{6,24})").matcher(t);
        if (m.find()) return "https://live.douyin.com/" + m.group(1);
        return null;
    }

    private void showSniffDialog() {
        android.app.Dialog d = new android.app.Dialog(this);
        d.setContentView(R.layout.dialog_sniff);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setWindowAnimations(R.style.DialogPopAnim);
            w.setDimAmount(0.4f);
        }
        boolean dk = Theme.dark(HomeActivity.this);
        View dlgRoot = d.findViewById(R.id.dialogRoot);
        if (dlgRoot != null) dlgRoot.setBackgroundResource(dk ? R.drawable.bg_dialog_dark : R.drawable.bg_dialog_light);
        final EditText et = d.findViewById(R.id.et_dialog_url);
        // 抖音直播开关(记忆)
        final TextView dyT = d.findViewById(R.id.tv_douyin_live);
        final android.content.SharedPreferences spf = getSharedPreferences("settings", MODE_PRIVATE);
        final android.text.TextWatcher repaint = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int q) {}
            public void onTextChanged(CharSequence c, int a, int b, int q) {}
            public void afterTextChanged(android.text.Editable e) {
                boolean on = spf.getBoolean("douyin_live", false);
                dyT.setText("抖音直播 " + (on ? "开" : "关"));
                dyT.setTextColor(on ? 0xFF25D0A5 : 0xAAFFFFFF);
            }
        };
        repaint.afterTextChanged(null);
        // 开关开着: 自动识别剪贴板里的抖音号或自动填上次直播间
        if (spf.getBoolean("douyin_live", false)) {
            String clipText = null;
            try {
                ClipboardManager cm0 = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm0 != null && cm0.getPrimaryClip() != null && cm0.getPrimaryClip().getItemAt(0) != null
                        && cm0.getPrimaryClip().getItemAt(0).getText() != null)
                    clipText = cm0.getPrimaryClip().getItemAt(0).getText().toString().trim();
            } catch (Throwable ignored) {}
            String auto = dyAutoResolve(clipText, spf.getString("dyid", ""));
            if (auto != null) {
                et.setText(auto);
                et.setHint("");
                // 自动开始嗅探
                final android.app.Dialog dAuto = d;
                dAuto.findViewById(R.id.btn_go).postDelayed(new Runnable() {
                    public void run() {
                        try { if (dAuto.isShowing()) dAuto.findViewById(R.id.btn_go).performClick(); } catch (Throwable ignored) {}
                    }
                }, 400);
            }
        }
        dyT.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                spf.edit().putBoolean("douyin_live", !spf.getBoolean("douyin_live", false)).apply();
                repaint.afterTextChanged(null);
                if (spf.getBoolean("douyin_live", false)) {
                    String saved = spf.getString("dyid", "");
                    if (saved.length() > 0) et.setHint("已记忆直播间 " + saved);
                }
            }
        });
        d.findViewById(R.id.btn_paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null && cm.getPrimaryClip() != null
                            && cm.getPrimaryClip().getItemAt(0) != null
                            && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                        String pasted = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                        boolean dyOn0 = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("douyin_live", false);
                        if (dyOn0) {
                            String auto = dyAutoResolve(pasted, getSharedPreferences("settings", MODE_PRIVATE).getString("dyid", ""));
                            if (auto != null) { pasted = auto; getSharedPreferences("settings", MODE_PRIVATE).edit().putString("dyid", auto.replace("https://live.douyin.com/","")).apply(); }
                        }
                        et.setText(pasted);
                        if (pasted.startsWith("https://live.douyin.com/")) {
                            final android.app.Dialog dP = d;
                            d.findViewById(R.id.btn_go).postDelayed(new Runnable() {
                                public void run() {
                                    try { if (dP.isShowing()) dP.findViewById(R.id.btn_go).performClick(); } catch (Throwable ignored) {}
                                }
                            }, 300);
                        }
                    }
                } catch (Exception e) { }
            }
        });
        d.findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); }
        });
        d.findViewById(R.id.btn_go).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String input = et.getText().toString().trim();
                boolean dyOn = spf.getBoolean("douyin_live", false);
                if (dyOn) {
                    String auto = dyAutoResolve(input, spf.getString("dyid", ""));
                    if (auto != null) {
                        input = auto;
                        String id = input.replace("https://live.douyin.com/", "");
                        spf.edit().putString("dyid", id).apply();
                    }
                }
                Intent i = new Intent(HomeActivity.this, SniffActivity.class);
                i.putExtra("input", input);
                startActivity(i);
                d.dismiss();
            }
        });
        d.show();
    }

    /** 视频下载专用弹窗: [进入]仅进页, [下载]带链接直开 */
    private void showVdlDialog() {
        android.app.Dialog d = new android.app.Dialog(this);
        d.setContentView(R.layout.dialog_vdl);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setWindowAnimations(R.style.DialogPopAnim);
            w.setDimAmount(0.4f);
        }
        boolean dk = Theme.dark(HomeActivity.this);
        View dlgRoot = d.findViewById(R.id.dialogRoot);
        if (dlgRoot != null) dlgRoot.setBackgroundResource(dk ? R.drawable.bg_dialog_dark : R.drawable.bg_dialog_light);
        final EditText et = d.findViewById(R.id.et_vdl_url);
        d.findViewById(R.id.btn_vdl_paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null && cm.getPrimaryClip() != null
                            && cm.getPrimaryClip().getItemAt(0) != null
                            && cm.getPrimaryClip().getItemAt(0).getText() != null) {
                        String pasted = cm.getPrimaryClip().getItemAt(0).getText().toString().trim();
                        boolean dyOn0 = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("douyin_live", false);
                        if (dyOn0) {
                            String auto = dyAutoResolve(pasted, getSharedPreferences("settings", MODE_PRIVATE).getString("dyid", ""));
                            if (auto != null) { pasted = auto; getSharedPreferences("settings", MODE_PRIVATE).edit().putString("dyid", auto.replace("https://live.douyin.com/","")).apply(); }
                        }
                        et.setText(pasted);
                        if (pasted.startsWith("https://live.douyin.com/")) {
                            final android.app.Dialog dP = d;
                            d.findViewById(R.id.btn_go).postDelayed(new Runnable() {
                                public void run() {
                                    try { if (dP.isShowing()) dP.findViewById(R.id.btn_go).performClick(); } catch (Throwable ignored) {}
                                }
                            }, 300);
                        }
                    }
                } catch (Exception e) { }
            }
        });
        d.findViewById(R.id.btn_vdl_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, VideoDlActivity.class));
                d.dismiss();
            }
        });
        d.findViewById(R.id.btn_vdl_go).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(HomeActivity.this, VideoDlActivity.class);
                i.putExtra("url", et.getText().toString().trim());
                startActivity(i);
                d.dismiss();
            }
        });
        d.show();
    }


}
