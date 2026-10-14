package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/** AutoTapper 风格页面: 权限引导+配置+摘要+开始/停止 */
public class TapperActivity extends Activity {

    private TextView ptsView, summary;
    private EditText inInterval, inJitter, inCount, inTouch, inOffset;
    private View btnPerm, btnSvc;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        setContentView(swrap(buildUi()));
        refresh();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F5F5);
        root.setPadding(40, 50, 40, 40);

        TextView title = new TextView(this);
        title.setText("AutoTapper 自动连点器");
        title.setTextColor(0xFF222222);
        title.setTextSize(21);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("先授予权限，再打开悬浮控制器…支持单点连续点击、多点位循环点击，无需 root。");
        desc.setTextColor(0xFF777777);
        desc.setTextSize(12);
        desc.setPadding(0, 8, 0, 20);
        root.addView(desc);

        ptsView = new TextView(this);
        ptsView.setTextSize(13);
        ptsView.setTextColor(0xFF1E7E34);
        ptsView.setPadding(24, 20, 24, 20);
        ptsView.setBackground(card(0xFFE0F5EC));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = 14;
        ptsView.setLayoutParams(plp);
        root.addView(ptsView);

        btnPerm = bigBtn("1. 授予悬浮窗权限", new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e1) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                    } catch (Exception e2) {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                            Toast.makeText(TapperActivity.this, "在应用信息里找「显示在其他应用上层」", Toast.LENGTH_LONG).show();
                        } catch (Exception ignore) {}
                    }
                }
            }
        });
        root.addView(btnPerm);
        btnSvc = bigBtn("2. 开启无障碍服务", new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignore) {}
            }
        });
        root.addView(btnSvc);
        root.addView(bigBtn("3. 打开悬浮控制器", new View.OnClickListener() {
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(TapperActivity.this)) {
                    Toast.makeText(TapperActivity.this, "先完成 1", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!svcEnabled()) {
                    Toast.makeText(TapperActivity.this, "先完成 2 (开启后回来)", Toast.LENGTH_SHORT).show();
                    return;
                }
                TapperService.openController();
                Toast.makeText(TapperActivity.this, "悬浮球已打开", Toast.LENGTH_SHORT).show();
            }
        }));
        root.addView(bigBtn("关闭悬浮控制器", new View.OnClickListener() {
            public void onClick(View v) { TapperService.closeController(); }
        }));

        root.addView(secTitle("点击配置"));
        TextView alg = new TextView(this);
        alg.setText("每次点击后等待：基础间隔 + 0~随机附加延迟 毫秒；点击次数填 0 表示无限循环，直到手动停止。");
        alg.setTextColor(0xFF888888);
        alg.setTextSize(11);
        alg.setPadding(0, 4, 0, 12);
        root.addView(alg);

        summary = new TextView(this);
        summary.setTextSize(12);
        summary.setTextColor(0xFF555555);
        summary.setPadding(24, 20, 24, 20);
        summary.setBackground(card(0xFFE8F0F8));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = 18;
        summary.setLayoutParams(slp);

        inInterval = numInput("基础间隔", "tapper", "interval", 400, root);
        inJitter = numInput("随机附加延迟", "tapper", "jitter", 0, root);
        inCount = numInput("点击次数", "tapper", "count", 0, root);
        inTouch = numInput("点击触屏时长(ms)", "tapper", "touchMs", 40, root);
        inOffset = numInput("位置随机偏移px(反检测)", "tapper", "offset", 0, root);

        root.addView(summary);

        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable s) { persistCfg(); refreshSummary(); }
        };
        inInterval.addTextChangedListener(w);
        inJitter.addTextChangedListener(w);
        inCount.addTextChangedListener(w);
        inTouch.addTextChangedListener(w);
        inOffset.addTextChangedListener(w);

        root.addView(bigBtn("开始点击", new View.OnClickListener() {
            public void onClick(View v) {
                persistCfg();
                if (!svcEnabled()) { Toast.makeText(TapperActivity.this, "先开启无障碍服务", Toast.LENGTH_SHORT).show(); return; }
                if (!Settings.canDrawOverlays(TapperActivity.this)) { Toast.makeText(TapperActivity.this, "先授予悬浮窗权限", Toast.LENGTH_SHORT).show(); return; }
                TapperService.openController();
                TapperService.startRun();
                Toast.makeText(TapperActivity.this, "已开始 (悬浮球可见)", Toast.LENGTH_SHORT).show();
            }
        }));
        root.addView(bigBtn("停止点击", new View.OnClickListener() {
            public void onClick(View v) { TapperService.stopRunStatic(); }
        }));
        root.addView(bigBtn("查看脚本库", new View.OnClickListener() {
            public void onClick(View v) {
                if (!svcEnabled()) { Toast.makeText(TapperActivity.this, "先开启无障碍服务", Toast.LENGTH_SHORT).show(); return; }
                if (!Settings.canDrawOverlays(TapperActivity.this)) { Toast.makeText(TapperActivity.this, "先授予悬浮窗权限", Toast.LENGTH_SHORT).show(); return; }
                TapperService.openController();
                TapperService.showScriptLib();
            }
        }));

        TextView help = new TextView(this);
        help.setText("使用说明书\n· 打开悬浮控制器后屏幕出现小球(可拖动)，点小球展开面板\n· ＋取点：屏幕上点出要点的位置（可多个），按音量下键结束取点\n· ●录制：正常操作屏幕，点击和滑动都会录进脚本（含真实间隔），完成后自动保存\n· ▶播放：优先回放录制脚本，无脚本则按点位循环点击；次数上限按脚本步数计\n· 点击触屏时长/位置随机偏移：模拟真人触摸，防检测\n· 脚本库：保存当前配置为脚本，随时载入反复运行");
        help.setTextColor(0xFF777777);
        help.setTextSize(12);
        help.setLineSpacing(4, 1);
        help.setPadding(24, 20, 24, 20);
        help.setBackground(card(0xFFECECEC));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = 26;
        help.setLayoutParams(hlp);
        root.addView(help);
        return root;
    }

    private TextView secTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xFF222222);
        v.setTextSize(15);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, 24, 0, 10);
        return v;
    }

    private GradientDrawable card(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(20);
        g.setColor(color);
        return g;
    }

    private EditText numInput(String label, String sp, final String key, int def, LinearLayout root) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xFF333333);
        l.setTextSize(13);
        l.setTypeface(null, android.graphics.Typeface.BOLD);
        l.setPadding(0, 14, 0, 6);
        root.addView(l);
        final EditText e = new EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(getSharedPreferences(sp, MODE_PRIVATE).getInt(key, def)));
        e.setTextSize(14);
        e.setTextColor(0xFF222222);
        e.setPadding(24, 18, 24, 18);
        e.setBackground(card(0xFFFFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(e, lp);
        return e;
    }

    private void persistCfg() {
        try { getSharedPreferences("tapper", MODE_PRIVATE).edit()
                .putInt("interval", clamp(inInterval, 50, 60000, 400))
                .putInt("jitter", clamp(inJitter, 0, 60000, 0))
                .putInt("count", clamp(inCount, 0, 1000000, 0)).apply(); } catch (Exception ignore) {}
    }

    private int clamp(EditText e, int min, int max, int def) {
        try {
            int v = Integer.parseInt(e.getText().toString());
            return Math.max(min, Math.min(max, v));
        } catch (Exception ex) { return def; }
    }

    private void refreshSummary() {
        if (summary == null) return;
        int iv = clamp(inInterval, 50, 60000, 400);
        int jt = clamp(inJitter, 0, 60000, 0);
        int ct = clamp(inCount, 0, 1000000, 0);
        int n = 0;
        try {
            n = new JSONArray(getSharedPreferences("tapper", MODE_PRIVATE)
                    .getString("points", "[]")).length();
        } catch (Exception ignore) {}
        summary.setText("当前为" + (n <= 1 ? "单点" : n + "点循环") + "点击模式。目标点位："
                + (n == 0 ? "未选择" : "已选 " + n + " 个")
                + "\n每次点击后等待：" + iv + " 到 " + (iv + jt) + " 毫秒"
                + "\n总点击次数：" + (ct == 0 ? "无限次（直到手动停止）" : ct + " 次"));
    }

    private boolean svcEnabled() {
        if (TapperService.ready()) return true; // 服务活着直接算已开启
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            java.util.List<android.accessibilityservice.AccessibilityServiceInfo> l =
                    am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (android.accessibilityservice.AccessibilityServiceInfo i : l) {
                if (i.getId() != null && i.getId().contains(getPackageName())) return true;
            }
        } catch (Throwable ignore) {}
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName());
    }

    private void refresh() {
        // 权限已开启就隐藏入口按钮，未开启才显示
        if (btnPerm != null) btnPerm.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        if (btnSvc != null) btnSvc.setVisibility(svcEnabled() ? View.GONE : View.VISIBLE);
        int n = 0, sw = 0;
        try {
            JSONArray ops = new JSONArray(getSharedPreferences("tapper", MODE_PRIVATE)
                    .getString("ops", "[]"));
            for (int i = 0; i < ops.length(); i++) {
                if ("s".equals(ops.optJSONObject(i) == null ? "" : ops.optJSONObject(i).optString("t"))) sw++; else n++;
            }
        } catch (Exception ignore) {}
        ptsView.setText("操作列表（点击" + n + " · 滑动" + sw + "）："
                + (n + sw == 0 ? "尚未添加" : "已配置 " + (n + sw) + " 个操作"));
        refreshSummary();
    }

    private View bigBtn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setPadding(0, 24, 0, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(22);
        bg.setColor(0xFF555555);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private View swrap(View content) {
        ScrollView sc = new ScrollView(this);
        sc.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }

}
