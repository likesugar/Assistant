package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ziyuan.Assistant.xhs.XhsEngine;
import com.ziyuan.Assistant.xhs.XhsNet;
import com.ziyuan.Assistant.xhs.XhsStore;

/** 小红书下载器 · 应用内设置页（条目与原版 SettingsActivity 一致，不删功能） */
public class XhsSettingsActivity extends Activity {

    private XhsStore store;
    private boolean dark, oled;
    private LinearLayout list;
    private TextView tvAccount, tvStorage;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        oled = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("oled", false);
        XhsEngine.init(this);
        store = XhsEngine.store();
        buildUi();
        refresh();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(dark ? (oled ? Color.BLACK : 0xFF141414) : 0xFFEEF4FF);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        page.setPadding(pad, dp(12), pad, dp(32));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("下载设置");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        page.addView(title, margin(0, 0, 0, dp(10)));

        // 平台账号状态（设置→平台账号→小红书 的入口与状态）
        LinearLayout acc = card();
        acc.addView(rowTitle("📕 平台账号 · 小红书"));
        tvAccount = new TextView(this);
        tvAccount.setTextSize(12);
        acc.addView(tvAccount, margin(0, dp(4), 0, 0));
        TextView goLogin = linkBtn("去登录 / 管理账号");
        goLogin.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new Intent(XhsSettingsActivity.this, SettingsActivity.class));
        }});
        acc.addView(goLogin, margin(0, dp(8), 0, 0));
        page.addView(acc, margin(0, 0, 0, dp(10)));

        // 开关组（对齐原版条目）
        page.addView(switchCard("保存 LivePhoto", "实况照片保存为 图片+视频 对", store.createLivePhotos(),
                new Apply() { public void run(boolean v) { store.setCreateLivePhotos(v); } }));
        page.addView(switchCard("自定义命名格式", "关闭时按 作者-标题 自动命名", store.useCustomNaming(),
                new Apply() { public void run(boolean v) { store.setUseCustomNaming(v); } }));
        page.addView(tapCard("命名模板", new Runnable() { public void run() { editTemplate(); } }));
        page.addView(switchCard("选择性下载", "解析后先勾选要保存的媒体", store.selectiveDownload(),
                new Apply() { public void run(boolean v) { store.setSelectiveDownload(v); } }));
        page.addView(switchCard("显示媒体分辨率", "在媒体上显示宽×高", store.showMediaResolution(),
                new Apply() { public void run(boolean v) { store.setShowMediaResolution(v); } }));
        page.addView(switchCard("下载时保持屏幕常亮", null, store.keepScreenOn(),
                new Apply() { public void run(boolean v) { store.setKeepScreenOn(v); } }));
        page.addView(switchCard("剪贴板气泡", "检测到链接时底部提示", store.showClipboardBubble(),
                new Apply() { public void run(boolean v) { store.setShowClipboardBubble(v); } }));
        page.addView(switchCard("自动读取剪贴板", "回到本页自动检测", store.autoReadClipboard(),
                new Apply() { public void run(boolean v) { store.setAutoReadClipboard(v); } }));
        page.addView(switchCard("手动输入链接", "显示输入链接按钮", store.manualInputLinks(),
                new Apply() { public void run(boolean v) { store.setManualInputLinks(v); } }));
        page.addView(switchCard("保存前检查已存在文件", "同名文件跳过重复下载", store.checkExistingFiles(),
                new Apply() { public void run(boolean v) { store.setCheckExistingFiles(v); } }));
        page.addView(switchCard("调试通知", "输出解析调试日志", store.debugNotification(),
                new Apply() { public void run(boolean v) { store.setDebugNotification(v); } }));

        // 存储位置
        LinearLayout st = card();
        st.addView(rowTitle("存储位置"));
        tvStorage = new TextView(this);
        tvStorage.setTextSize(12);
        tvStorage.setTextColor(dark ? 0xFFB0B8C4 : 0xFF6B7280);
        st.addView(tvStorage, margin(0, dp(4), 0, 0));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView custom = linkBtn("自定义目录");
        custom.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editStorage(); }});
        TextView reset = linkBtn("恢复默认");
        reset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            store.clearCustomStorage(); refresh();
        }});
        row.addView(custom);
        row.addView(reset, margin(dp(16), 0, 0, 0));
        st.addView(row, margin(0, dp(8), 0, 0));
        page.addView(st, margin(0, 0, 0, dp(10)));

        // 清除已完成历史
        LinearLayout clr = card();
        TextView clear = linkBtn("清除已完成任务的记录（文件保留）");
        clear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            new GlassDialog.Builder(XhsSettingsActivity.this)
                    .setMessage("清除已完成任务的记录？已下载文件保留在原目录。")
                    .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { XhsEngine.clearDone(); Toast.makeText(XhsSettingsActivity.this, "已清除", Toast.LENGTH_SHORT).show(); }
                    })
                    .setNegativeButton("取消", null).show();
        }});
        clr.addView(clear);
        page.addView(clr, margin(0, 0, 0, dp(10)));

        setContentView(scroll);
    }

    private void refresh() {
        boolean logged = XhsNet.hasWebSession();
        tvAccount.setText(logged ? "已登录（检测到 web_session Cookie）" : "未登录 · 部分笔记需登录后才能解析");
        tvAccount.setTextColor(logged ? 0xFF0E9F6E : 0xFFE5A50A);
        String custom = store.customStorageDir();
        tvStorage.setText(custom == null || custom.trim().isEmpty()
                ? "默认：/storage/emulated/0/Android/data/com.ziyuan.Assistant/files/xhs" : custom);
    }

    private void editTemplate() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(12), dp(24), 0);
        final EditText input = new EditText(this);
        input.setText(store.namingTemplate());
        input.setBackgroundResource(dark ? R.drawable.bg_input_flat : R.drawable.bg_input);
        input.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        box.addView(input);
        TextView hint = new TextView(this);
        hint.setText("可用占位符：{title} {username} {userId} {postId} {publishTime} {downloadTimestamp}\n默认：{title}({username})_{publishTime}");
        hint.setTextSize(11);
        hint.setTextColor(0xFF8A94A6);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);
        new GlassDialog.Builder(this)
                .setTitle("命名模板")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { store.setNamingTemplate(input.getText().toString()); }
                })
                .setNegativeButton("取消", null).show();
    }

    private void editStorage() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(12), dp(24), 0);
        final EditText input = new EditText(this);
        input.setHint("/sdcard/Download/XHS");
        input.setBackgroundResource(dark ? R.drawable.bg_input_flat : R.drawable.bg_input);
        input.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        box.addView(input);
        new GlassDialog.Builder(this)
                .setTitle("自定义保存目录")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        store.setCustomStorage(input.getText().toString().trim(), "自定义");
                        refresh();
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    // ---------- 通用控件 ----------
    private interface Apply { void run(boolean v); }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackgroundResource(dark ? (oled ? R.drawable.bg_card_oled : R.drawable.bg_card_md3_dark) : R.drawable.bg_card_md3);
        return c;
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(l, t, r, b);
        return p;
    }

    private TextView rowTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(dark ? Color.WHITE : 0xFF1F2329);
        return t;
    }

    private TextView subText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11);
        t.setTextColor(dark ? 0xFF8A94A6 : 0xFF8A94A6);
        return t;
    }

    private TextView linkBtn(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(0xFF1677FF);
        return t;
    }

    private LinearLayout switchCard(String title, String sub, boolean checked, final Apply apply) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(rowTitle(title));
        if (sub != null) texts.addView(subText(sub), margin(0, dp(2), 0, 0));
        c.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView sw = new TextView(this);
        sw.setText(checked ? "开" : "关");
        sw.setTextSize(13);
        sw.setTypeface(Typeface.DEFAULT_BOLD);
        sw.setGravity(Gravity.CENTER);
        sw.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(checked ? 0xFF1677FF : 0xFF9AA3B0);
        g.setCornerRadius(dp(14));
        sw.setBackground(g);
        sw.setPadding(dp(14), dp(4), dp(14), dp(4));
        c.addView(sw);
        sw.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean now = !"开".equals(sw.getText());
                sw.setText(now ? "开" : "关");
                android.graphics.drawable.GradientDrawable g2 = new android.graphics.drawable.GradientDrawable();
                g2.setColor(now ? 0xFF1677FF : 0xFF9AA3B0);
                g2.setCornerRadius(dp(14));
                sw.setBackground(g2);
                apply.run(now);
            }
        });
        LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(-1, -2);
        wrap.bottomMargin = dp(10);
        c.setLayoutParams(wrap);
        return c;
    }

    private LinearLayout tapCard(String title, final Runnable action) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(rowTitle(title), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(0xFF8A94A6);
        c.addView(arrow);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } });
        LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(-1, -2);
        wrap.bottomMargin = dp(10);
        c.setLayoutParams(wrap);
        return c;
    }
}
