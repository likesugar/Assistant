package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 设置页：平台账号(动态渲染) / 剪贴板开关 / 下载通知 / OLED 主题 */
public class SettingsPageView extends android.widget.FrameLayout {

    private final Activity host;

    /** 内置平台: 名称 / 登录页 / Cookie特征 / 站点域 / 圆标emoji / 圆标色(暗) */
    private static final String[][] PLATFORMS = {
            {"哔哩哔哩", "https://passport.bilibili.com/h5-app/passport/login", "SESSDATA=", "https://www.bilibili.com", "📺", "0xFFFB7299"},
            {"抖音", "https://www.douyin.com/jingxuan", "sessionid=", "https://www.douyin.com", "🎵", "0xFF161823"},
            {"YouTube", "https://www.youtube.com/signin?next=%2F&hl=zh-CN", "SAPISID=", "https://www.youtube.com", "▶️", "0xFFFF0033"},
            {"小红书", "https://www.xiaohongshu.com", "web_session=", "https://www.xiaohongshu.com", "📕", "0xFFFF2442"},
    };

    private boolean dark;
    private android.widget.ScrollView platScroll;
    private LinearLayout platList;
    private LinearLayout ghList;
    private final List<View> themed = new ArrayList<>();

    public SettingsPageView(Activity hostActivity) {
        super(hostActivity);
        host = hostActivity;
        android.view.LayoutInflater.from(host).inflate(R.layout.activity_settings, this, true);
        findViewById(R.id.btnBack).setVisibility(GONE); // 内嵌页无返回键
        // 内层滚动区加底部留白给首页胶囊条
        android.view.ViewGroup root = (android.view.ViewGroup) getChildAt(0);
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (c instanceof android.widget.ScrollView) {
                c.setPadding(c.getPaddingLeft(), c.getPaddingTop(), c.getPaddingRight(), (int) (20 * getResources().getDisplayMetrics().density));
                break;
            }
        }

        dark = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("dark", false);
        platScroll = (android.widget.ScrollView) findViewById(R.id.platScroll);
        // 内层滚动优先, 别让外页抢走触摸
        platScroll.setOnTouchListener(new android.view.View.OnTouchListener() {
            public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            }
        });
        platList = (LinearLayout) findViewById(R.id.platList);
        ghList = (LinearLayout) findViewById(R.id.ghList);
        android.widget.Switch swC = (android.widget.Switch) findViewById(R.id.swClipboard);
        swC.setChecked(host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("clipboard", true));
        android.widget.Switch swAuto = (android.widget.Switch) findViewById(R.id.swAutoplay);
        if (swAuto != null) {
            swAuto.setChecked(host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("autoplay", true));
            swAuto.setOnCheckedChangeListener((bv, checked) ->
                host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("autoplay", checked).apply());
        }

        // 胶囊: 开关先缓存, 点"确认生效"才写入并重建
        final int[] capIds={R.id.swHidePan,R.id.swHideHome,R.id.swHideMedia,R.id.swHidePlayer};
        final String[] capKeys={"hidePan","hideHome","hideMedia","hidePlayer"};
        final boolean[] capPend=new boolean[4];
        for(int i=0;i<4;i++){
            final int fi=i;
            android.widget.Switch swX=(android.widget.Switch) findViewById(capIds[i]);
            if(swX==null) continue;
            capPend[i]=host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean(capKeys[i],false);
            swX.setChecked(capPend[i]);
            swX.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener(){
                public void onCheckedChanged(android.widget.CompoundButton b, boolean on){ capPend[fi]=on; }
            });
        }
        View capBtn=findViewById(R.id.btnCapApply);
        if(capBtn!=null) capBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                android.content.SharedPreferences.Editor ed=host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit();
                boolean changed=false;
                for(int i=0;i<4;i++){
                    if(capPend[i]!=host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean(capKeys[i],false)) changed=true;
                    ed.putBoolean(capKeys[i],capPend[i]);
                }
                ed.apply();
                if(changed) host.recreate();
                else Toast.makeText(host,"设置未变化",Toast.LENGTH_SHORT).show();
            }
        });
        swC.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("clipboard", on).apply();
                Toast.makeText(host, on ? "已开启自动检测剪贴板" : "已关闭自动检测剪贴板", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.cardOled).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean oled = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("oled", false);
                host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("oled", !oled).apply();
                dark = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("dark", false);
                applyTheme();
            }
        });
        findViewById(R.id.cardNotify).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    android.content.Intent it = new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    it.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, host.getPackageName());
                    host.startActivity(it);
                } catch (Throwable t) {
                    Toast.makeText(host, "无法打开通知设置", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.cardDark).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.SharedPreferences esp = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
                boolean newDark = !esp.getBoolean("dark", false);
                esp.edit().putBoolean("dark", newDark).putBoolean("oled", newDark).apply();
                dark = esp.getBoolean("dark", false);
                applyTheme();
                if (host instanceof HomeActivity) ((HomeActivity) host).refreshGlobalTheme();
            }
        });
        findViewById(R.id.cardLan).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (host instanceof HomeActivity) ((HomeActivity) host).showLanDialog();
            }
        });
        findViewById(R.id.cardAbout2).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (host instanceof HomeActivity) ((HomeActivity) host).openAboutPage();
                return;
            }
        });
        findViewById(R.id.btnClearAll).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { clearAll(); }
        });
        applyTheme();
    }

    // ---------- 平台列表 ----------

    private List<String[]> allPlatforms() {
        List<String[]> out = new ArrayList<>();
        for (String[] p : PLATFORMS) out.add(p);
        String list = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("custom_sites", "");
        for (String e : list.split("")) {
            if (e.length() == 0) continue;
            String[] p = e.split("", -1);
            if (p.length < 2) continue;
            out.add(new String[]{p[0], p[1], "", "", "🌐", "0xFF5B7CFA"});
        }
        return out;
    }

    private boolean isCustom(int i) { return i >= PLATFORMS.length; }

    private String platformDomain(int i) {
        if (i < PLATFORMS.length) return PLATFORMS[i][3];
        try { java.net.URL u = new java.net.URL(allPlatforms().get(i)[1]); return u.getProtocol() + "://" + u.getHost(); }
        catch (Throwable t) { return ""; }
    }

    private boolean loggedIn(int i) {
        List<String[]> all = allPlatforms();
        if (i >= all.size()) return false;
        String[] p = all.get(i);
        if (p[2].length() == 0) return false; // 自定义平台无特征串
        try {
            String c = android.webkit.CookieManager.getInstance().getCookie(platformDomain(i));
            return c != null && c.contains(p[2]);
        } catch (Throwable t) { return false; }
    }

    private void buildPlatList() {
        platList.removeAllViews();
        themed.clear();
        List<String[]> all = allPlatforms();
        int div = color(0xFF2A3142, 0xFFEDEFF3);
        for (int i = 0; i < all.size(); i++) {
            final int idx = i;
            String[] p = all.get(i);
            boolean in = loggedIn(i);
            String sub;
            if (isCustom(i)) sub = in ? "已登录(检测到Cookie)" : "已配置 · 点登录生效";
            else sub = in ? "已登录" : "未登录";
            View row = platRow(p[4], (int) ((long) Long.decode(p[5])), p[0], sub,
                    in ? "管理" : "登录", color(0xFFB4C5FF, 0xFF315CDE),
                    new View.OnClickListener() {
                        public void onClick(View v) { openWeb(idx); }
                    });
            if (isCustom(i)) {
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { confirmDelete(idx); return true; }
                });
            }
            platList.addView(row);
            if (i < all.size() - 1) platList.addView(divLine(div));
        }
        // 添加平台
        View add = platRow("＋", color(0xFF2A3142, 0xFFEDEFF3), "添加平台",
                "支持任意网站的网页登录", "添加", color(0xFFB4C5FF, 0xFF315CDE),
                new View.OnClickListener() {
                    public void onClick(View v) { showAddDialog(); }
                });
        platList.addView(divLine(div));
        platList.addView(add);
        // GitHub 配置备份 (默认 token 已内置)
        // 账号超过4行: 卡内滚动, 限高=前4行+分隔线 (布局变化时重算)
        platList.addOnLayoutChangeListener(new android.view.View.OnLayoutChangeListener() {
            public void onLayoutChange(android.view.View v, int l, int t, int r, int b,
                                       int ol, int ot, int or2, int ob) {
                adjustPlatScroll();
            }
        });
        platScroll.post(new Runnable() { public void run() { adjustPlatScroll(); }});
        buildGhList();
    }

    private void adjustPlatScroll() {
        if (platScroll == null || platList == null) return;
        android.view.ViewGroup.LayoutParams lp = platScroll.getLayoutParams();
        int n = platList.getChildCount();
        if (n == 0) return;
        int rowH = 0, seen = 0, extra = 0;
        for (int k = 0; k < n; k++) {
            android.view.View c = platList.getChildAt(k);
            int h = c.getHeight();
            if (h > dp(20)) {
                if (seen < 4) { rowH += h; seen++; }
            } else extra += h;
        }
        if (seen == 0 || rowH == 0) return;
        int full = platList.getHeight();
        if (seen >= 4 && full > rowH + extra) {
            if (lp.height != rowH + extra) { lp.height = rowH + extra; platScroll.setLayoutParams(lp); }
        } else if (lp.height != android.view.ViewGroup.LayoutParams.WRAP_CONTENT) {
            lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            platScroll.setLayoutParams(lp);
        }
    }

    private void buildGhList() {
        ghList.removeAllViews();
        themed.clear();
        int div = color(0xFF2A3142, 0xFFEDEFF3);
        android.content.SharedPreferences gsp = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        { String gt = gsp.getString("ghToken", "");
          if (gt.equals("GITHUB_TOKEN_PLACEHOLDER") || gt.startsWith("[REDACTED")) gsp.edit().remove("ghToken").apply(); }
        View ghUp = platRow("⬆️", color(0xFF2F7D4F, 0xFF4CAF50), "上传配置到 GitHub",
                "AI配置+收藏+下载历史 备份到私密gist", "上传", color(0xFF7CC47F, 0xFF4CAF50),
                new View.OnClickListener() {
                    public void onClick(View v) { ghStart(true); }
                });
        ghList.addView(ghUp);
        ghList.addView(divLine(div));
        View ghDn = platRow("⬇️", color(0xFF3B6EA5, 0xFF2196F3), "从 GitHub 下载配置",
                "恢复 AI配置+收藏+下载历史", "下载", color(0xFF7CC47F, 0xFF2196F3),
                new View.OnClickListener() {
                    public void onClick(View v) { ghStart(false); }
                });
        ghList.addView(ghDn);
    }

    private static final String[] GH_KEYS = {"aiKey","aiBase","aiModel","aiModelParse","aiModelChat","aiRole","aiSys","aiTemp","aiTopP","aiTokens","aiFreq","aiPres"};

    private void ghStart(final boolean upload) {
        final android.content.SharedPreferences sp = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        if (upload) {
            // 长按行可自定义 token; 短按直接用已内置/已存 token
            final android.widget.EditText et = new android.widget.EditText(host);
            et.setHint("留空 = 用当前token");
            { String gt = sp.getString("ghToken", ""); et.setText(gt.startsWith("GITHUB_TOKEN") || gt.startsWith("[REDACTED") ? "" : gt); }
            new android.app.AlertDialog.Builder(host)
                .setTitle("GitHub Token")
                .setView(et)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String t = et.getText().toString().trim();
                        if (!t.isEmpty()) sp.edit().putString("ghToken", t).apply();
                        ghRun(true, sp.getString("ghToken", ""));
                    }
                }).setNegativeButton("取消", null).show();
        } else {
            final android.widget.EditText et2 = new android.widget.EditText(host);
            et2.setHint("留空 = 用当前token");
            { String gt2 = sp.getString("ghToken", ""); et2.setText(gt2.startsWith("GITHUB_TOKEN") || gt2.startsWith("[REDACTED") ? "" : gt2); }
            new android.app.AlertDialog.Builder(host)
                .setTitle("GitHub Token")
                .setView(et2)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String t = et2.getText().toString().trim();
                        if (!t.isEmpty()) sp.edit().putString("ghToken", t).apply();
                        ghRun(false, sp.getString("ghToken", ""));
                    }
                }).setNegativeButton("取消", null).show();
        }
    }

    private void ghRun(final boolean upload, final String token) {
        final android.content.SharedPreferences sp = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
        if (token.isEmpty()) { toast("token 为空"); return; }
        toast(upload ? "上传中..." : "下载中...");
        new Thread(() -> {
            try {
                if (upload) {
                    StringBuilder json = new StringBuilder("{");
                    for (int i = 0; i < GH_KEYS.length; i++) {
                        if (i > 0) json.append(',');
                        json.append('"').append(GH_KEYS[i]).append("\":").append(org.json.JSONObject.quote(sp.getString(GH_KEYS[i], "")));
                    }
                    json.append(",\"favCards\":").append(org.json.JSONObject.quote(sp.getString("favCards", "")));
                    json.append(",\"dark\":").append(sp.getBoolean("dark", false));
                    json.append(",\"oled\":").append(sp.getBoolean("oled", true));
                    json.append(",\"hideHome\":").append(sp.getBoolean("hideHome", false));
                    json.append(",\"hideMedia\":").append(sp.getBoolean("hideMedia", false));
                    json.append(",\"hidePan\":").append(sp.getBoolean("hidePan", false));
                    json.append(",\"hidePlayer\":").append(sp.getBoolean("hidePlayer", false));
                    android.content.SharedPreferences dl = host.getSharedPreferences("dl_history", android.content.Context.MODE_PRIVATE);
                    StringBuilder dlj = new StringBuilder("{");
                    java.util.Map<String, ?> all = dl.getAll();
                    int di = 0;
                    for (java.util.Map.Entry<String, ?> en : all.entrySet()) {
                        if (di++ > 0) dlj.append(',');
                        Object v = en.getValue();
                        String vs = (v instanceof Boolean) ? String.valueOf(v) : org.json.JSONObject.quote(String.valueOf(v));
                        dlj.append('"').append(en.getKey()).append("\":").append(vs);
                    }
                    dlj.append('}');
                    json.append(",\"dlHistory\":").append(dlj.toString());
                    json.append('}');
                    String gistId = sp.getString("ghGistId", "");
                    if (gistId.isEmpty()) {
                        String payload = "{\"description\":\"xgjhome ai config\",\"public\":false,\"files\":{\"config.json\":{\"content\":" + org.json.JSONObject.quote(json.toString()) + "}}}";
                        try {
                            String resp = ghHttp("https://api.github.com/gists", "POST", token, payload);
                            sp.edit().putString("ghGistId", new org.json.JSONObject(resp).getString("id")).apply();
                            toast("已上传 (新建gist)");
                        } catch (Exception ce) {
                            String m = String.valueOf(ce.getMessage());
                            if (m.contains("404") || m.contains("403") || m.contains("401"))
                                toast("新建gist失败(" + m + "): token需为经典token并勾选gist权限");
                            else throw ce;
                        }
                    } else {
                        String payload = "{\"files\":{\"config.json\":{\"content\":" + org.json.JSONObject.quote(json.toString()) + "}}}";
                        try {
                            ghHttp("https://api.github.com/gists/" + gistId, "PATCH", token, payload);
                            toast("已更新");
                        } catch (Exception pe) {
                            if (String.valueOf(pe.getMessage()).contains("404")) {
                                // gist 已不存在(被删/token换了账号): 清掉旧id改新建
                                sp.edit().putString("ghGistId", "").apply();
                                String resp = ghHttp("https://api.github.com/gists", "POST", token, payload);
                                sp.edit().putString("ghGistId", new org.json.JSONObject(resp).getString("id")).apply();
                                toast("旧gist失效,已新建gist上传");
                            } else throw pe;
                        }
                    }
                } else {
                    String gistId = sp.getString("ghGistId", "");
                    if (gistId.isEmpty()) {
                        // 新设备: 自动在 token 名下找含 config.json 的gist
                        String list = ghHttp("https://api.github.com/gists?per_page=100", "GET", token, null);
                        org.json.JSONArray arr = new org.json.JSONArray(list);
                        String found = "";
                        for (int i = 0; i < arr.length() && found.isEmpty(); i++) {
                            org.json.JSONObject g = arr.getJSONObject(i);
                            if (g.has("files") && g.getJSONObject("files").has("config.json")) found = g.getString("id");
                        }
                        if (found.isEmpty()) { toast("该token名下没有配置gist"); return; }
                        gistId = found;
                        sp.edit().putString("ghGistId", found).apply();
                    }
                    String resp;
                    try {
                        resp = ghHttp("https://api.github.com/gists/" + gistId, "GET", token, null);
                    } catch (Exception ge) {
                        if (String.valueOf(ge.getMessage()).contains("404")) { // gist失效: 清id自动重找
                            sp.edit().putString("ghGistId", "").apply();
                            String list = ghHttp("https://api.github.com/gists?per_page=100", "GET", token, null);
                            org.json.JSONArray arr2 = new org.json.JSONArray(list);
                            String found2 = "";
                            for (int i = 0; i < arr2.length() && found2.isEmpty(); i++) {
                                org.json.JSONObject g2 = arr2.getJSONObject(i);
                                if (g2.has("files") && g2.getJSONObject("files").has("config.json")) found2 = g2.getString("id");
                            }
                            if (found2.isEmpty()) { toast("token名下没有配置gist (404)"); return; }
                            gistId = found2;
                            sp.edit().putString("ghGistId", found2).apply();
                            resp = ghHttp("https://api.github.com/gists/" + gistId, "GET", token, null);
                        } else if (String.valueOf(ge.getMessage()).contains("401")) { toast("token无效 (401), 请重新粘贴"); return; }
                        else if (String.valueOf(ge.getMessage()).contains("403")) { toast("token无gist权限 (403)"); return; }
                        else throw ge;
                    }
                    org.json.JSONObject files = new org.json.JSONObject(resp).getJSONObject("files");
                    if (!files.has("config.json")) { toast("gist 里没有 config.json"); return; }
                    org.json.JSONObject cfg = new org.json.JSONObject(files.getJSONObject("config.json").getString("content"));
                    android.content.SharedPreferences.Editor ed = sp.edit();
                    java.util.Set<String> intKeys = new java.util.HashSet<>(java.util.Arrays.asList("aiTemp","aiTopP","aiTokens","aiFreq","aiPres"));
                    for (String k : GH_KEYS) {
                        if (!cfg.has(k)) continue;
                        if (intKeys.contains(k)) {
                            try { ed.putInt(k, Integer.parseInt(cfg.getString(k))); }
                            catch (Exception ie) { try { ed.putInt(k, cfg.getInt(k)); } catch (Exception e2) {} }
                        } else ed.putString(k, cfg.getString(k));
                    }
                    if (cfg.has("favCards")) ed.putString("favCards", cfg.getString("favCards"));
                    if (cfg.has("dark")) ed.putBoolean("dark", cfg.getBoolean("dark"));
                    if (cfg.has("oled")) ed.putBoolean("oled", cfg.getBoolean("oled"));
                    for (String hk : new String[]{"hideHome","hideMedia","hidePan","hidePlayer"}) {
                        if (cfg.has(hk)) ed.putBoolean(hk, cfg.getBoolean(hk));
                    }
                    ed.apply();
                    if (cfg.has("dlHistory")) {
                        org.json.JSONObject dlj2 = cfg.getJSONObject("dlHistory");
                        android.content.SharedPreferences dl2 = host.getSharedPreferences("dl_history", android.content.Context.MODE_PRIVATE);
                        dl2.edit().clear().apply();
                        android.content.SharedPreferences.Editor ed2 = dl2.edit();
                        java.util.Iterator<String> it = dlj2.keys();
                        while (it.hasNext()) {
                            String k2 = it.next();
                            Object v2 = dlj2.get(k2);
                            if (v2 instanceof Boolean) ed2.putBoolean(k2, (Boolean) v2);
                            else if (v2 instanceof Integer) ed2.putInt(k2, (Integer) v2);
                            else ed2.putString(k2, String.valueOf(v2));
                        }
                        ed2.apply();
                    }
                    host.runOnUiThread(() -> host.recreate());
                    toast("配置已恢复");
                }
            } catch (Exception e) {
                toast("失败: " + e.getMessage());
            }
        }).start();
    }

    private String ghHttp(String url, String method, String token, String body) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("Content-Type", "application/json");
        if (body != null) { c.setDoOutput(true); c.getOutputStream().write(body.getBytes("UTF-8")); }
        int code = c.getResponseCode();
        java.io.InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        if (code >= 400) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    private void toast(String m) {
        host.runOnUiThread(() -> android.widget.Toast.makeText(host, m, Toast.LENGTH_SHORT).show());
    }

    private View platRow(String emoji, int avColor, String name, String sub, String action, int actionColor,
                         View.OnClickListener click) {
        int dp8 = dp(8);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp8, 0, dp8);

        TextView av = new TextView(host);
        av.setText(emoji);
        av.setTextSize(16);
        av.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(avColor);
        av.setBackground(bg);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(40), dp(40));
        alp.rightMargin = dp(12);
        av.setLayoutParams(alp);
        row.addView(av);

        LinearLayout mid = new LinearLayout(host);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mid.setLayoutParams(mlp);
        TextView tvName = new TextView(host);
        tvName.setText(name); tvName.setTextSize(16); tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
        tvName.setTextColor(color(0xFFE8ECF4, 0xFF1F2329));
        mid.addView(tvName);
        TextView tvSub = new TextView(host);
        tvSub.setText(sub); tvSub.setTextSize(12);
        tvSub.setTextColor(color(0xFF8A94A6, 0xFF8A94A6));
        tvSub.setPadding(0, dp8 / 2, 0, 0);
        mid.addView(tvSub);
        row.addView(mid);

        TextView go = new TextView(host);
        go.setText(action); go.setTextSize(15);
        go.setTextColor(actionColor);
        go.setPadding(dp8, dp8, 0, dp8);
        go.setOnClickListener(click);
        row.addView(go);

        themed.add(row);
        return row;
    }

    private View divLine(int c) {
        View v = new View(host);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(c);
        return v;
    }

    private void openWeb(int idx) {
        try {
            String url = allPlatforms().get(idx)[1];
            CookieHosts.add(host, url);
            host.startActivity(new Intent(host, LoginWebActivity.class).putExtra("url", url));
        } catch (Throwable t) { Toast.makeText(host, "打开失败", Toast.LENGTH_SHORT).show(); }
    }

    private void confirmDelete(final int idx) {
        final String name = allPlatforms().get(idx)[0];
        new AlertDialog.Builder(host)
                .setTitle("删除平台")
                .setMessage("删除「" + name + "」？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String list = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("custom_sites", "");
                        StringBuilder sb = new StringBuilder();
                        for (String e : list.split("")) {
                            if (e.length() > 0 && !e.startsWith(name + "")) {
                                if (sb.length() > 0) sb.append('');
                                sb.append(e);
                            }
                        }
                        host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putString("custom_sites", sb.toString()).apply();
                        buildPlatList();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText etName = new EditText(host);
        etName.setHint("平台名字"); etName.setSingleLine(true);
        box.addView(etName);
        final EditText etUrl = new EditText(host);
        etUrl.setHint("登录页 https://…"); etUrl.setSingleLine(true);
        box.addView(etUrl);
        RadioGroup rg = new RadioGroup(host);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbM = new RadioButton(host); rbM.setText("手机版"); rbM.setId(101); rbM.setChecked(true);
        RadioButton rbP = new RadioButton(host); rbP.setText("PC版"); rbP.setId(102);
        rg.addView(rbM); rg.addView(rbP);
        box.addView(rg);
        new AlertDialog.Builder(host)
                .setTitle("添加平台")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String n = etName.getText().toString().trim();
                        String u = etUrl.getText().toString().trim();
                        String mode = rg.getCheckedRadioButtonId() == 102 ? "pc" : "mobile";
                        if (n.length() == 0 || !u.startsWith("http")) {
                            Toast.makeText(host, "名字与登录页URL必填", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String list = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("custom_sites", "");
                        String add = n + "" + u + "" + mode;
                        host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit()
                                .putString("custom_sites", list.length() > 0 ? list + "" + add : add).apply();
                        buildPlatList();
                        Toast.makeText(host, "已添加，点「登录」完成网页登录", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void clearAll() {
        try {
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.removeAllCookies(null);
            cm.flush();
            buildPlatList();
            Toast.makeText(host, "已清除全部网页登录", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(host, "清除失败: " + t, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- 主题 ----------


    private int color(int darkC, int lightC) { return dark ? darkC : lightC; }

    /** 切到本页时调用：刷新主题与登录态 */
    public void refresh() { applyTheme(); }

    private void applyTheme() {
        dark = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("dark", false); // 每次重读, 防外部切换后用旧值
        boolean oled = host.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("oled", true);
        int bg = color(oled ? 0xFF000000 : 0xFF11151D, 0xFFEEF4FF);
        int card = color(oled ? 0xFF000000 : 0xFF191F2E, 0xFFFFFFFF);
        int title = color(0xFFE8ECF4, 0xFF1F2329);
        int sub = color(0xFF8A94A6, 0xFF8A94A6);
        int accent = color(0xFFB4C5FF, 0xFF315CDE);
        int div = color(0xFF2A3142, 0xFFEDEFF3);
        findViewById(R.id.settingsRoot).setBackgroundColor(bg);
        int[] titles = {R.id.tvTitle, R.id.t4, R.id.t5, R.id.t6, R.id.tOled, R.id.tDark, R.id.tLan, R.id.tvAboutTitle2};
        for (int id : titles) ((TextView) findViewById(id)).setTextColor(title);
        int[] subs = {R.id.label1, R.id.labelGh, R.id.label2, R.id.descOled, R.id.descDark, R.id.descLan, R.id.desc1, R.id.desc2,
                R.id.desc3, R.id.desc5, R.id.desc6, R.id.tvAboutText2};
        for (int id : subs) ((TextView) findViewById(id)).setTextColor(sub);
        int[] accents = {R.id.btnClearAll, R.id.arrow};
        for (int id : accents) ((TextView) findViewById(id)).setTextColor(accent);
        int[] cards = {R.id.card1, R.id.cardGh, R.id.card2, R.id.card3, R.id.cardOled, R.id.cardDark, R.id.cardLan, R.id.cardNotify, R.id.cardAbout2};
        for (int id : cards) findViewById(id).setBackgroundResource(dark ? (oled ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3);
        View dv = null;
        if (dv != null) dv.setBackgroundColor(div);
        TextView tvO = (TextView) findViewById(R.id.tvOled);
        if (tvO != null) tvO.setText(oled ? "开" : "关");
        TextView tvD = (TextView) findViewById(R.id.tvDark);
        if (tvD != null) tvD.setText(dark ? "深" : "浅");
        buildPlatList();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
