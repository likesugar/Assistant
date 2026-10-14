package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 我的api设置 (1:1 复刻参照 App API 配置页) */
public class PanApiActivity extends Activity {

    private static final String[][] PROVIDERS = {
        {"OpenAI", "OpenAI 官方 API", "O", "https://api.openai.com/v1"},
        {"Anthropic", "Anthropic Claude（兼容 OpenAI 协议）", "A", "https://api.anthropic.com/v1"},
        {"Google Gemini", "Google Gemini（兼容 OpenAI 协议）", "G", "https://generativelanguage.googleapis.com/v1beta/openai"},
        {"DeepSeek", "深度求索，性价比高", "D", "https://api.deepseek.com/v1"},
        {"通义千问", "阿里云通义千问", "通", "https://dashscope.aliyuncs.com/compatible-mode/v1"},
        {"月之暗面", "Moonshot Kimi 大模型", "月", "https://api.moonshot.cn/v1"},
        {"智谱AI", "智谱 BigModel 开放平台", "智", "https://open.bigmodel.cn/api/paas/v4"},
        {"硅基流动", "SiliconFlow 多模型托管", "硅", "https://api.siliconflow.cn/v1"},
        {"自定义", "使用自定义的 API 地址", "自", ""},
    };
    private static final String[] MODELS = {
        "gpt-5-pro", "gpt-5.5", "gemini-3.5-pro", "gemini-3.5-flash",
        "gemini-3-pro", "gemini-3-flash", "gemini-2.5-flash", "gemini-2.5-pro",
        "deepseek-chat", "deepseek-reasoner", "deepseek-v4-pro", "claude-sonnet-4",
    };

    private boolean dark; private float den;
    private int fg, sub, cardBg, fieldBg, gold;
    private EditText eKey, eUrl, eModelParse, eModelChat, eTokens, eCtx, eTimeout, eSys;
    private SeekBar sbTemp, sbTopP, sbFreq, sbPres;
    private TextView tvTemp, tvTopP, tvFreq, tvPres, tvStatus, tvProviderSub;
    private Switch swStream, swEnable;
    private LinearLayout modelChipsParse, modelChipsChat, modelCardsRow;
    private View advBody;
    private String providerIdx = "0";
    private boolean showKey = false;

    @Override protected void onCreate(Bundle b) {
            sanitizeIntPrefs(this);
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        den = getResources().getDisplayMetrics().density;
        fg = dark ? 0xFFE8EEF7 : 0xFF222222;
        sub = dark ? 0xFF9AA3AE : 0xFF8A94A6;
        cardBg = dark ? 0xFF1C2430 : 0xFFFFFFFF;
        fieldBg = dark ? 0xFF232B37 : 0xFFF4F6F9;
        gold = 0xFFC6A87C;

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(dark ? 0xFF141A22 : 0xFFEEF2F8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * den), (int) (14 * den), (int) (16 * den), (int) (30 * den));
        scroll.addView(root);
        setContentView(scroll);

        android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        providerIdx = sp.getString("aiProvider", "0");

        // ===== 状态卡 (原版: 状态+开关+服务商+解析/对话摘要 同一大卡) =====
        LinearLayout statusCard = card(root);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tvStatus = new TextView(this);
        tvStatus.setText("● " + (sp.getString("aiKey", "").isEmpty() ? "未配置" : "已配置"));
        tvStatus.setTextSize(13);
        tvStatus.setTextColor(sp.getString("aiKey", "").isEmpty() ? 0xFFE2564E : 0xFF7CC47F);
        statusRow.addView(tvStatus, new LinearLayout.LayoutParams(0, -2, 1f));
        swEnable = new Switch(this);
        swEnable.setChecked(sp.getBoolean("aiEnabled", true));
        statusRow.addView(swEnable);
        statusCard.addView(statusRow);

        // 服务商行 (内嵌同卡, 内衬更暗一层的圆角)
        LinearLayout provCard = new LinearLayout(this);
        provCard.setOrientation(LinearLayout.VERTICAL);
        provCard.setPadding((int) (12 * den), (int) (12 * den), (int) (12 * den), (int) (12 * den));
        GradientDrawable pgc = new GradientDrawable();
        pgc.setCornerRadius(12 * den); pgc.setColor(dark ? 0xFF2A3140 : 0xFFF0F3F7);
        provCard.setBackground(pgc);
        LinearLayout.LayoutParams provLp = new LinearLayout.LayoutParams(-1, -2);
        provLp.topMargin = (int) (12 * den);
        statusCard.addView(provCard, provLp);
        LinearLayout provRow = new LinearLayout(this);
        provRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        pvIcon = new TextView(this);
        pvIcon.setText(providerLetter());
        pvIcon.setTextSize(18); pvIcon.setTypeface(null, android.graphics.Typeface.BOLD);
        pvIcon.setTextColor(0xFF222222); pvIcon.setGravity(android.view.Gravity.CENTER);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(10 * den); pg.setColor(gold);
        pvIcon.setBackground(pg);
        provRow.addView(pvIcon, new LinearLayout.LayoutParams((int) (44 * den), (int) (44 * den)));
        LinearLayout pvText = new LinearLayout(this);
        pvText.setOrientation(LinearLayout.VERTICAL);
        pvName = new TextView(this);
        pvName.setText(providerName()); pvName.setTextSize(16); pvName.setTypeface(null, android.graphics.Typeface.BOLD); pvName.setTextColor(fg);
        tvProviderSub = new TextView(this);
        tvProviderSub.setText(providerSub()); tvProviderSub.setTextSize(12); tvProviderSub.setTextColor(sub);
        pvText.addView(pvName); pvText.addView(tvProviderSub);
        provRow.addView(pvText, new LinearLayout.LayoutParams(0, -2, 1f) {{ leftMargin = (int) (12 * den); }});
        TextView pvArrow = new TextView(this);
        pvArrow.setText("›"); pvArrow.setTextSize(20); pvArrow.setTextColor(sub);
        provRow.addView(pvArrow);
        provCard.addView(provRow);
        provCard.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickProvider(); } });

        // 解析/对话 摘要 chips (同卡内, 点击定位到下方模型卡)
        LinearLayout sumRow = new LinearLayout(this);
        sumRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(-1, -2);
        sumLp.topMargin = (int) (12 * den);
        statusCard.addView(sumRow, sumLp);
        tvParseSum = summaryBox(sumRow, "解 析", sp.getString("aiModelParse", "gemini-2.5-flash"), 0, true);
        tvChatSum = summaryBox(sumRow, "对 话", sp.getString("aiModelChat", "gemini-2.5-flash"), (int) (8 * den), false);

        // ===== 双模型卡 =====
        modelCardsRow = new LinearLayout(this);
        modelCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(modelCardsRow, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (12 * den); }});
        modelCardsRow.addView(modelCard("解析模型", "用于占卜解读", true), new LinearLayout.LayoutParams(0, -2, 1f) {{ rightMargin = (int) (6 * den); }});
        modelCardsRow.addView(modelCard("对话模型", "已独立配置", false), new LinearLayout.LayoutParams(0, -2, 1f) {{ leftMargin = (int) (6 * den); }});

        // ===== 获取模型横幅 =====
        LinearLayout fetchBanner = card(root);
        LinearLayout fbRow = new LinearLayout(this);
        fbRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView fbIcon = new TextView(this);
        fbIcon.setText("⟳"); fbIcon.setTextSize(18); fbIcon.setTextColor(gold);
        fbRow.addView(fbIcon);
        LinearLayout fbText = new LinearLayout(this);
        fbText.setOrientation(LinearLayout.VERTICAL);
        TextView fbT1 = new TextView(this);
        fbT1.setText("从 API 地址获取最新模型"); fbT1.setTextSize(13); fbT1.setTypeface(null, android.graphics.Typeface.BOLD); fbT1.setTextColor(fg);
        TextView fbT2 = new TextView(this);
        fbT2.setText("调用 GET /models，自动识别 OpenAI 兼容格式"); fbT2.setTextSize(11); fbT2.setTextColor(sub);
        fbText.addView(fbT1); fbText.addView(fbT2);
        fbRow.addView(fbText, new LinearLayout.LayoutParams(0, -2, 1f) {{ leftMargin = (int) (10 * den); }});
        Button manual = new Button(this);
        manual.setText("手动输入"); manual.setTextSize(12);
        manual.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { manualModel(); } });
        fbRow.addView(manual);
        fetchBanner.addView(fbRow);
        fetchBanner.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { fetchModels(); } });

        // ===== 凭据卡 =====
        LinearLayout credCard = card(root);
        LinearLayout credHead = new LinearLayout(this);
        credHead.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView credT = new TextView(this);
        credT.setText("● 凭据"); credT.setTextSize(13); credT.setTypeface(null, android.graphics.Typeface.BOLD); credT.setTextColor(gold);
        credHead.addView(credT, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView credLocal = new TextView(this);
        credLocal.setText("本地存储"); credLocal.setTextSize(11); credLocal.setTextColor(sub);
        credHead.addView(credLocal);
        credCard.addView(credHead);
        TextView labUrl = new TextView(this);
        labUrl.setText("API 地址"); labUrl.setTextSize(12); labUrl.setTextColor(sub); labUrl.setLetterSpacing(0.1f);
        credCard.addView(labUrl, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (10 * den); }});
        eUrl = field(credCard);
        eUrl.setText(sp.getString("aiBase", PROVIDERS[0][3]));
        TextView labKey = new TextView(this);
        labKey.setText("API Key"); labKey.setTextSize(12); labKey.setTextColor(sub); labKey.setLetterSpacing(0.1f);
        credCard.addView(labKey, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (10 * den); }});
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        eKey = field(null); // 挂载在下方 keyRow
        eKey.setHint("sk-..."); eKey.setHintTextColor(sub);
        eKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        eKey.setText(sp.getString("aiKey", ""));
        keyRow.addView(eKey, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView eye = new TextView(this);
        eye.setText("👁"); eye.setTextSize(16); eye.setPadding((int) (10 * den), 0, 0, 0);
        eye.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            showKey = !showKey;
            eKey.setInputType(InputType.TYPE_CLASS_TEXT | (showKey ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        }});
        keyRow.addView(eye);
        credCard.addView(keyRow, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (6 * den); }});
        root.addView(credCard, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (12 * den); }});

        // ===== 测试连接 =====
        LinearLayout testRow = new LinearLayout(this);
        Button test = new Button(this);
        test.setText("▶ 测试连接"); test.setTextSize(15);
        GradientDrawable tg = new GradientDrawable();
        tg.setCornerRadius(14 * den); tg.setColor(gold);
        test.setBackground(tg); test.setTextColor(0xFF222222);
        test.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { testConn(); } });
        testRow.addView(test, new LinearLayout.LayoutParams(0, -2, 1f));
        Button refresh = new Button(this);
        refresh.setText("⟳"); refresh.setTextSize(15);
        GradientDrawable rg2 = new GradientDrawable();
        rg2.setCornerRadius(14 * den); rg2.setColor(cardBg);
        refresh.setBackground(rg2);
        refresh.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { fetchModels(); } });
        testRow.addView(refresh, new LinearLayout.LayoutParams((int) (52 * den), -2) {{ leftMargin = (int) (10 * den); }});
        root.addView(testRow, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (14 * den); }});

        // ===== 高级设置 =====
        LinearLayout advCard = card(root);
        LinearLayout advHead = new LinearLayout(this);
        advHead.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView advT = new TextView(this);
        advT.setText("⚙ 高级设置"); advT.setTextSize(14); advT.setTypeface(null, android.graphics.Typeface.BOLD); advT.setTextColor(fg);
        advHead.addView(advT, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView advArrow = new TextView(this);
        advArrow.setText("⌃"); advArrow.setTextSize(16); advArrow.setTextColor(sub);
        advHead.addView(advArrow);
        advCard.addView(advHead);
        advBody = advBody(advCard, sp);
        advBody.setVisibility(View.GONE); // 默认收起
        advHead.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            advBody.setVisibility(advBody.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }});
        root.addView(advCard, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (14 * den); }});

        // ===== 使用提示 =====
        LinearLayout tipCard = card(root);
        TextView tipT = new TextView(this);
        tipT.setText("ⓘ 使用提示"); tipT.setTextSize(13); tipT.setTypeface(null, android.graphics.Typeface.BOLD); tipT.setTextColor(gold);
        tipCard.addView(tipT);
        TextView tipB = new TextView(this);
        tipB.setText("· 修改后请点「保存」\n· 不同模型对话效果差异较大\n· 测试连接可验证 Key 和地址是否正确");
        tipB.setTextSize(12); tipB.setTextColor(sub);
        tipCard.addView(tipB, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (6 * den); }});
        root.addView(tipCard, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (14 * den); }});

        // ===== 保存 =====
        Button save = new Button(this);
        save.setText("保 存"); save.setTextSize(15);
        GradientDrawable sg = new GradientDrawable();
        sg.setCornerRadius(14 * den); sg.setColor(gold);
        save.setBackground(sg); save.setTextColor(0xFF222222);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveAll(); } });
        root.addView(save, new LinearLayout.LayoutParams(-1, (int) (48 * den)) {{ topMargin = (int) (16 * den); }});
    }

    private static void sanitizeIntPrefs(android.content.Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("settings", MODE_PRIVATE);
        String[][] kv = {{"aiTemp","70"},{"aiTopP","100"},{"aiFreq","0"},{"aiPres","0"}};
        for (String[] e2 : kv) {
            try { sp.getInt(e2[0], Integer.parseInt(e2[1])); }
            catch (Throwable t) {
                try { int v = (int) Double.parseDouble(String.valueOf(sp.getAll().get(e2[0]))); sp.edit().putInt(e2[0], v).apply(); }
                catch (Throwable t2) { sp.edit().putInt(e2[0], Integer.parseInt(e2[1])).apply(); }
            }
        }
    }
    private String providerName() { return PROVIDERS[Integer.parseInt(providerIdx)][0]; }
    private String providerSub() { return PROVIDERS[Integer.parseInt(providerIdx)][1]; }
    private String providerLetter() { return PROVIDERS[Integer.parseInt(providerIdx)][2]; }
    private TextView tvParseSum, tvChatSum;

    /** 解析/对话摘要小盒 (原版: 金色小标签 + 白粗模型名) */
    private TextView summaryBox(LinearLayout parent, String label, String value, int leftMargin, boolean isParse) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (12 * den), (int) (10 * den), (int) (12 * den), (int) (10 * den));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(10 * den); g.setColor(dark ? 0xFF2A3140 : 0xFFF0F3F7);
        box.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = leftMargin;
        parent.addView(box, lp);
        TextView l = new TextView(this);
        l.setText(label); l.setTextSize(11); l.setTextColor(gold);
        box.addView(l);
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(14); v.setTypeface(null, android.graphics.Typeface.BOLD); v.setTextColor(fg);
        v.setSingleLine(true);
        box.addView(v);
        return v;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding((int) (16 * den), (int) (14 * den), (int) (16 * den), (int) (14 * den));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(16 * den); g.setColor(cardBg);
        c.setBackground(g);
        return c;
    }

    private EditText field(LinearLayout parent) {
        EditText e = new EditText(this);
        e.setTextSize(14); e.setTextColor(fg); e.setSingleLine(true);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * den); g.setColor(fieldBg);
        e.setBackground(g);
        e.setPadding((int) (14 * den), (int) (12 * den), (int) (14 * den), (int) (12 * den));
        if (parent != null) parent.addView(e, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (4 * den); }});
        return e;
    }

    private View modelCard(String title, String subtitle, final boolean isParse) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding((int) (14 * den), (int) (12 * den), (int) (14 * den), (int) (12 * den));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(16 * den); g.setColor(cardBg);
        c.setBackground(g);
        TextView dotT = new TextView(this);
        dotT.setText("● " + title); dotT.setTextSize(13); dotT.setTypeface(null, android.graphics.Typeface.BOLD); dotT.setTextColor(gold);
        c.addView(dotT);
        TextView dotS = new TextView(this);
        dotS.setText(subtitle); dotS.setTextSize(11); dotS.setTextColor(sub);
        c.addView(dotS);
        android.content.SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        final EditText em = new EditText(this);
        em.setTextSize(15); em.setTextColor(fg); em.setSingleLine(true);
        em.setText(sp.getString(isParse ? "aiModelParse" : "aiModelChat", sp.getString("aiModel", MODELS[6])));
        GradientDrawable fg2 = new GradientDrawable();
        fg2.setCornerRadius(10 * den); fg2.setColor(fieldBg);
        em.setBackground(fg2);
        em.setPadding((int) (12 * den), (int) (10 * den), (int) (12 * den), (int) (10 * den));
        c.addView(em, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (8 * den); }});
        if (isParse) {
            eModelParse = em;
            em.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c2) {}
                public void onTextChanged(CharSequence s, int a, int b, int c2) {}
                public void afterTextChanged(android.text.Editable s) {
                    if (tvParseSum != null) tvParseSum.setText(s.toString().isEmpty() ? "未设置" : s.toString());
                }
            });
        } else {
            eModelChat = em;
            em.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c2) {}
                public void onTextChanged(CharSequence s, int a, int b, int c2) {}
                public void afterTextChanged(android.text.Editable s) {
                    if (tvChatSum != null) tvChatSum.setText(s.toString().isEmpty() ? "未设置" : s.toString());
                }
            });
        }
        // 快捷选择: 默认收起, 点标题展开/收起
        TextView quickLab = new TextView(this);
        quickLab.setText("快捷选择 ▸"); quickLab.setTextSize(12); quickLab.setTextColor(sub);
        c.addView(quickLab, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (10 * den); }});
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(2);
        grid.setVisibility(View.GONE);
        quickLab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            boolean show = grid.getVisibility() != View.VISIBLE;
            grid.setVisibility(show ? View.VISIBLE : View.GONE);
            quickLab.setText(show ? "快捷选择 ▾" : "快捷选择 ▸");
        }});
        for (final String m : MODELS) {
            TextView ch = chip(m);
            ch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { em.setText(m); } });
            android.widget.GridLayout.LayoutParams glp = new android.widget.GridLayout.LayoutParams();
            glp.setMargins((int) (3 * den), (int) (3 * den), (int) (3 * den), (int) (3 * den));
            glp.width = 0; glp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            grid.addView(ch, glp);
        }
        c.addView(grid, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (4 * den); }});
        return c;
    }

    private TextView chip(String t) {
        TextView c = new TextView(this);
        c.setText(t); c.setTextSize(11); c.setTextColor(fg);
        c.setPadding((int) (10 * den), (int) (5 * den), (int) (10 * den), (int) (5 * den));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * den); g.setColor(fieldBg);
        c.setBackground(g);
        c.setSingleLine(true);
        return c;
    }

    /** 选择服务商 (原版: 卡片列表弹窗, 金框+对勾, 底部关闭) */
    private void pickProvider() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (8 * den), (int) (10 * den), (int) (8 * den), (int) (4 * den));
        TextView title = new TextView(this);
        title.setText("选择服务商"); title.setTextSize(18); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setTextColor(fg);
        title.setPadding((int) (10 * den), (int) (6 * den), (int) (10 * den), (int) (10 * den));
        box.addView(title);
        ScrollView sc = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        box.addView(sc);
        final int cur = Integer.parseInt(providerIdx);
        for (int i = 0; i < PROVIDERS.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int) (10 * den), (int) (12 * den), (int) (10 * den), (int) (12 * den));
            GradientDrawable rg = new GradientDrawable();
            rg.setCornerRadius(14 * den);
            rg.setColor(dark ? 0xFF232B37 : 0xFFF4F6F9);
            if (i == cur) { rg.setStroke((int) (1.5f * den), gold); rg.setColor(dark ? 0xFF3A3222 : 0xFFFAF3E4); }
            row.setBackground(rg);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = (int) (6 * den);
            TextView av = new TextView(this);
            av.setText(PROVIDERS[i][2]); av.setTextSize(16); av.setTypeface(null, android.graphics.Typeface.BOLD);
            av.setTextColor(0xFF222222); av.setGravity(android.view.Gravity.CENTER);
            GradientDrawable ag = new GradientDrawable();
            ag.setCornerRadius(10 * den); ag.setColor(gold);
            av.setBackground(ag);
            row.addView(av, new LinearLayout.LayoutParams((int) (40 * den), (int) (40 * den)));
            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            TextView nm = new TextView(this);
            nm.setText(PROVIDERS[i][0]); nm.setTextSize(15); nm.setTypeface(null, android.graphics.Typeface.BOLD); nm.setTextColor(fg);
            TextView sb2 = new TextView(this);
            sb2.setText(PROVIDERS[i][1]); sb2.setTextSize(11); sb2.setTextColor(sub);
            txt.addView(nm); txt.addView(sb2);
            row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f) {{ leftMargin = (int) (10 * den); }});
            TextView ck = new TextView(this);
            ck.setText("✓"); ck.setTextSize(18); ck.setTextColor(gold);
            ck.setVisibility(i == cur ? View.VISIBLE : View.GONE);
            row.addView(ck);
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                providerIdx = String.valueOf(idx);
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("aiProvider", providerIdx).apply();
                String u = PROVIDERS[idx][3];
                eUrl.setText(u);
                pvName.setText(providerName());
                tvProviderSub.setText(providerSub());
                pvIcon.setText(providerLetter());
                tvStatus.setText("● " + (getSharedPreferences("settings", MODE_PRIVATE).getString("aiKey", "").isEmpty() ? "未配置" : "已配置"));
                Toast.makeText(PanApiActivity.this, "已选 " + providerName(), Toast.LENGTH_SHORT).show();
                ((android.app.Dialog) row.getTag()).dismiss();
            }});
            list.addView(row, rlp);
        }
        TextView close = new TextView(this);
        close.setText("关 闭"); close.setTextSize(16); close.setTypeface(null, android.graphics.Typeface.BOLD); close.setTextColor(gold);
        close.setGravity(android.view.Gravity.END);
        close.setPadding((int) (10 * den), (int) (12 * den), (int) (14 * den), (int) (10 * den));
        box.addView(close);
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dlgRef.dismiss(); } });
        AlertDialog d = new AlertDialog.Builder(this).create();
        d.setView(box);
        dlgRef = d;
        // 关闭按钮经 dlgRef
        for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).setTag(d);
        d.show();
        try { d.getWindow().setBackgroundDrawableResource(dark ? 0xFF1C2430 : 0xFFFFFFFF); } catch (Throwable ignored) {}
    }

    private android.app.Dialog dlgRef;
    private TextView pvName, pvIcon, pvArrow;


    private void manualModel() {
        final EditText in = new EditText(this);
        in.setHint("model-name");
        new AlertDialog.Builder(this).setTitle("手动输入模型")
            .setView(in).setPositiveButton("确定", (d, w) -> {
                String m = in.getText().toString().trim();
                if (!m.isEmpty()) { eModelParse.setText(m); eModelChat.setText(m); }
            }).setNegativeButton("取消", null).show();
    }

    private void fetchModels() {
        final String base = normBase(eUrl.getText().toString().trim());
        final String key = eKey.getText().toString().trim();
        if (base.isEmpty()) { Toast.makeText(this, "请先填 API 地址", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "获取中...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(base + "/models").openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(15000);
                if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder(); String l;
                while ((l = r.readLine()) != null) sb.append(l);
                r.close();
                JSONObject jo = new JSONObject(sb.toString());
                JSONArray arr = jo.optJSONArray("data");
                if (arr == null) arr = jo.optJSONArray("models");
                final List<String> list = new ArrayList<>();
                if (arr != null) for (int i = 0; i < arr.length() && i < 60; i++) {
                    String id = arr.optJSONObject(i) == null ? arr.optString(i) : arr.getJSONObject(i).optString("id");
                    if (!id.isEmpty()) list.add(id);
                }
                runOnUiThread(new Runnable() { public void run() {
                    if (list.isEmpty()) { Toast.makeText(PanApiActivity.this, "未识别到模型", Toast.LENGTH_SHORT).show(); return; }
                    new AlertDialog.Builder(PanApiActivity.this).setTitle("选择模型")
                        .setItems(list.toArray(new String[0]), (d, w) -> {
                            eModelParse.setText(list.get(w)); eModelChat.setText(list.get(w));
                        }).show();
                }});
            } catch (Throwable e) {
                runOnUiThread(new Runnable() { public void run() { Toast.makeText(PanApiActivity.this, "获取失败: 地址或Key不对", Toast.LENGTH_SHORT).show(); }});
            }
        }}, "fetch-models").start();
    }

    private void testConn() {
        final String base = normBase(eUrl.getText().toString().trim());
        final String key = eKey.getText().toString().trim();
        final String model = eModelChat.getText().toString().trim();
        Toast.makeText(this, "测试中...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { public void run() {
            boolean ok = false;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(base + "/chat/completions").openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(10000); c.setReadTimeout(20000);
                c.setRequestProperty("Content-Type", "application/json");
                if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
                c.setDoOutput(true);
                c.getOutputStream().write(new JSONObject()
                    .put("model", model).put("max_tokens", 5)
                    .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "hi")))
                    .toString().getBytes("UTF-8"));
                ok = c.getResponseCode() < 400;
            } catch (Throwable e) { }
            final boolean fok = ok;
            runOnUiThread(new Runnable() { public void run() {
                Toast.makeText(PanApiActivity.this, fok ? "✓ 连接成功" : "✗ 连接失败(地址/Key/模型)", Toast.LENGTH_LONG).show();
            }});
        }}, "test-conn").start();
    }

    private View advBody(LinearLayout parent, android.content.SharedPreferences sp) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView h = new TextView(this);
        h.setText("温度 · Token · Prompt · 流式"); h.setTextSize(11); h.setTextColor(sub);
        body.addView(h, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (8 * den); }});
        sbTemp = slider(body, "温度 (Temperature)", "精确|平衡|创意", sp, "aiTemp", 70, tvTemp = new TextView(this));
        sbTopP = slider(body, "核采样 (Top P)", "", sp, "aiTopP", 100, tvTopP = new TextView(this));
        eTokens = numRow(body, "最大 Token", sp.getString("aiTokens", "2048"));
        sbFreq = slider(body, "频率惩罚", "", sp, "aiFreq", 0, tvFreq = new TextView(this));
        sbPres = slider(body, "存在惩罚", "", sp, "aiPres", 0, tvPres = new TextView(this));
        eCtx = numRow(body, "上下文消息数", sp.getString("aiCtx", "20"));
        eTimeout = numRow(body, "超时（秒）", sp.getString("aiTimeout", "120"));
        LinearLayout streamRow = new LinearLayout(this);
        streamRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView stLab = new TextView(this);
        stLab.setText("流式输出\n逐字输出响应"); stLab.setTextSize(13); stLab.setTextColor(fg);
        streamRow.addView(stLab, new LinearLayout.LayoutParams(0, -2, 1f));
        swStream = new Switch(this);
        swStream.setChecked(sp.getBoolean("aiStream", false));
        streamRow.addView(swStream);
        LinearLayout srWrap = new LinearLayout(this);
        srWrap.setOrientation(LinearLayout.VERTICAL);
        srWrap.setPadding((int) (14 * den), (int) (10 * den), (int) (14 * den), (int) (10 * den));
        GradientDrawable srg = new GradientDrawable();
        srg.setCornerRadius(12 * den); srg.setColor(fieldBg);
        srWrap.setBackground(srg);
        srWrap.addView(streamRow);
        body.addView(srWrap, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (10 * den); }});
        TextView langLab = new TextView(this);
        langLab.setText("回复语言"); langLab.setTextSize(12); langLab.setTextColor(sub);
        body.addView(langLab, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (10 * den); }});
        EditText lang = new EditText(this);
        lang.setText(sp.getString("aiLang", "中文")); lang.setTextSize(14); lang.setTextColor(fg); lang.setSingleLine(true);
        GradientDrawable lg = new GradientDrawable();
        lg.setCornerRadius(12 * den); lg.setColor(fieldBg);
        lang.setBackground(lg);
        body.addView(lang, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (4 * den); }});
        // 角色预设 (原版: 弹窗单选 默认/专业风水师/民间高人/学术研究者/温暖知心)
        final String[] ROLE_NAMES = {"默认", "专业风水师", "民间高人", "学术研究者", "温暖知心"};
        final String[] ROLES = {"",
            "你是一位资深专业风水师与堪舆大师，措辞专业严谨，分析注重形势与理气。",
            "你是一位民间隐世高人，说话直断吉凶、一针见血，带着江湖气但不失分寸。",
            "你是易学与民俗文化学术研究者，引经据典、理性客观，注重考据与逻辑。",
            "你是一位温暖知心的友人，用亲切鼓励的口吻解读，给人安慰与指引。"};
        TextView roleLab = new TextView(this);
        roleLab.setText("角色预设"); roleLab.setTextSize(12); roleLab.setTextColor(sub);
        body.addView(roleLab, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (10 * den); }});
        LinearLayout roleRow = new LinearLayout(this);
        roleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        roleRow.setPadding((int) (14 * den), (int) (12 * den), (int) (14 * den), (int) (12 * den));
        GradientDrawable rg2 = new GradientDrawable();
        rg2.setCornerRadius(12 * den); rg2.setColor(fieldBg);
        roleRow.setBackground(rg2);
        TextView roleVal = new TextView(this);
        final String curRole0 = sp.getString("aiRole", "");
        String curName = "默认";
        for (int i = 0; i < ROLES.length; i++) if (ROLES[i].equals(curRole0)) curName = ROLE_NAMES[i];
        roleVal.setText(curName); roleVal.setTextSize(14); roleVal.setTextColor(fg);
        roleRow.addView(roleVal, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView roleArr = new TextView(this);
        roleArr.setText("▾"); roleArr.setTextSize(14); roleArr.setTextColor(sub);
        roleRow.addView(roleArr);
        roleRow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            final android.content.SharedPreferences sp2 = getSharedPreferences("settings", MODE_PRIVATE);
            String cur = sp2.getString("aiRole", "");
            int checked = 0;
            for (int i = 0; i < ROLES.length; i++) if (ROLES[i].equals(cur)) checked = i;
            AlertDialog roleDlg = new AlertDialog.Builder(PanApiActivity.this)
                .setTitle("选择角色预设")
                .setSingleChoiceItems(ROLE_NAMES, checked, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        sp2.edit().putString("aiRole", ROLES[which]).apply();
                        roleVal.setText(ROLE_NAMES[which]);
                        d.dismiss();
                    }
                })
                .setNegativeButton("关 闭", null)
                .create();
            roleDlg.show();
            try { roleDlg.getWindow().setBackgroundDrawableResource(dark ? 0xFF1C2430 : 0xFFFFFFFF); } catch (Throwable ignored) {}
        }});
        body.addView(roleRow, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (4 * den); }});
        eSys = new EditText(this);
        eSys.setHint(sp.getString("aiSys", "留空使用默认提示词"));
        eSys.setHintTextColor(sub); eSys.setTextColor(fg); eSys.setTextSize(13);
        eSys.setMinLines(2);
        GradientDrawable sysg = new GradientDrawable();
        sysg.setCornerRadius(12 * den); sysg.setColor(fieldBg);
        eSys.setBackground(sysg);
        eSys.setPadding((int) (14 * den), (int) (10 * den), (int) (14 * den), (int) (10 * den));
        body.addView(eSys, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (10 * den); }});
        parent.addView(body, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (8 * den); }});
        return body;
    }

    private SeekBar slider(LinearLayout body, String label, String scale, android.content.SharedPreferences sp, String key, int def, final TextView val) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView lab = new TextView(this);
        lab.setText(label); lab.setTextSize(12); lab.setTextColor(sub);
        row.addView(lab, new LinearLayout.LayoutParams(0, -2, 1f));
        val.setText(String.valueOf(sp.getInt(key, def) / 100f));
        val.setTextSize(12); val.setTextColor(gold); val.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(val);
        body.addView(row, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (12 * den); }});
        SeekBar sb = new SeekBar(this);
        sb.setMax(200);
        sb.setProgress(sp.getInt(key, def));
        sb.getProgressDrawable().setColorFilter(gold, android.graphics.PorterDuff.Mode.SRC_IN);
        sb.getThumb().setColorFilter(gold, android.graphics.PorterDuff.Mode.SRC_IN);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { val.setText(String.valueOf(p / 100f)); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        body.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        return sb;
    }

    private EditText numRow(LinearLayout body, String label, String val) {
        TextView lab = new TextView(this);
        lab.setText(label); lab.setTextSize(12); lab.setTextColor(sub);
        body.addView(lab, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (12 * den); }});
        EditText e = new EditText(this);
        e.setText(val); e.setTextSize(14); e.setTextColor(fg);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * den); g.setColor(fieldBg);
        e.setBackground(g);
        e.setPadding((int) (14 * den), (int) (12 * den), (int) (14 * den), (int) (12 * den));
        body.addView(e, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = (int) (4 * den); }});
        return e;
    }

    private String normBase(String u) {
        if (u == null) return "";
        u = u.trim();
        if (u.endsWith("/chat/completions")) u = u.substring(0, u.length() - "/chat/completions".length());
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private void saveAll() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("aiKey", eKey.getText().toString().trim())
            .putString("aiBase", normBase(eUrl.getText().toString()))
            .putString("aiProvider", providerIdx)
            .putString("aiModelParse", eModelParse.getText().toString().trim())
            .putString("aiModelChat", eModelChat.getText().toString().trim())
            .putString("aiModel", eModelChat.getText().toString().trim())
            .putString("aiTokens", eTokens.getText().toString().trim())
            .putString("aiCtx", eCtx.getText().toString().trim())
            .putString("aiTimeout", eTimeout.getText().toString().trim())
            .putString("aiSys", eSys.getText().toString().trim())
            .putString("aiLang", "中文")
            .putInt("aiTemp", sbTemp.getProgress())
            .putInt("aiTopP", sbTopP.getProgress())
            .putInt("aiFreq", sbFreq.getProgress())
            .putInt("aiPres", sbPres.getProgress())
            .putBoolean("aiStream", swStream.isChecked())
            .putBoolean("aiEnabled", swEnable.isChecked())
            .apply();
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
