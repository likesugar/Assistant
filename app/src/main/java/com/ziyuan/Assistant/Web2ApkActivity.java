package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class Web2ApkActivity extends Activity {
    private static final int BG = 0xFF17191D, ROW = 0xFF212429, ACCENT = 0xFF25D0A5, TXT = 0xFFF2F3F5, SUB = 0xFF9AA0A6;
    private static final String[] TOGGLES = {"隐藏", "隐藏导航栏", "沉浸式状态栏", "用户滑动", "暗黑模式", "浏览器标识"};
    private EditText nameI, pkgI, urlI;
    private byte[] iconBytes;
    private TextView pathL;
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        getWindow().setStatusBarColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(16), dp(20), dp(20));

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextColor(SUB);
        back.setTextSize(15);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        root.addView(back);

        TextView title = new TextView(this);
        title.setText("网页转应用");
        title.setTextColor(TXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(12), 0, dp(16));
        root.addView(title);

        nameI = new EditText(this);
        nameI.setHint("应用名称");
        nameI.setTextColor(TXT);
        nameI.setHintTextColor(SUB);
        root.addView(nameI);

        pkgI = new EditText(this);
        pkgI.setHint("包名（留空=自动 com.拼音.web）");
        pkgI.setTextColor(TXT);
        pkgI.setHintTextColor(SUB);
        pkgI.setTextSize(13);
        root.addView(pkgI);

        urlI = new EditText(this);
        urlI.setHint("网址 https://...");
        urlI.setTextColor(TXT);
        urlI.setHintTextColor(SUB);
        root.addView(urlI);

        TextView iconRow = new TextView(this);
        iconRow.setText("🖼 应用图标：默认（点此自定义）");
        iconRow.setTextColor(SUB);
        iconRow.setTextSize(15);
        iconRow.setPadding(dp(8), dp(16), dp(8), dp(16));
        iconRow.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("image/*");
            try { startActivityForResult(Intent.createChooser(it, "选择图标"), 7001); }
            catch (Throwable t) { Toast.makeText(Web2ApkActivity.this, "无法打开图片选择", Toast.LENGTH_SHORT).show(); }
        }});
        root.addView(iconRow);

        for (final String tg : TOGGLES) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(ROW);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            TextView t = new TextView(this);
            t.setText(tg);
            t.setTextColor(TXT);
            t.setTextSize(15);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
            t.setLayoutParams(tp);
            row.addView(t);
            Switch sw = new Switch(this);
            sw.setChecked("1".equals(getSharedPreferences("web2apk_cfg", MODE_PRIVATE).getString(tg, "0")));
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton btn, boolean on) {
                    getSharedPreferences("web2apk_cfg", MODE_PRIVATE).edit().putString(tg, on ? "1" : "0").apply();
                }
            });
            row.addView(sw);
            root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView buildBtn = new TextView(this);
        buildBtn.setText("打包成APK");
        buildBtn.setTextColor(0xFFFFFFFF);
        buildBtn.setTextSize(17);
        buildBtn.setTypeface(Typeface.DEFAULT_BOLD);
        buildBtn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xFF1A73E8);
        buildBtn.setBackground(bg);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(52));
        bp.setMargins(0, dp(24), 0, 0);
        buildBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doBuild(); } });
        root.addView(buildBtn, bp);

        pathL = new TextView(this);
        pathL.setTextColor(SUB);
        pathL.setTextSize(13);
        pathL.setPadding(dp(4), dp(10), dp(4), 0);
        root.addView(pathL, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sc = new ScrollView(this);
        sc.addView(root);
        setContentView(sc);
    }

    private void doBuild() {
        final String name = nameI.getText().toString().trim();
        final String url = urlI.getText().toString().trim();
        if (name.length() == 0 || !url.startsWith("http")) {
            Toast.makeText(this, "请填名称和 http(s) 网址", Toast.LENGTH_SHORT).show();
            return;
        }
        final byte[] icon = iconBytes;
        new Thread(new Runnable() { public void run() {
            try {
                File dir = new File(getExternalFilesDir(null), "web2apk");
                if (!dir.exists()) dir.mkdirs();
                final File out = new File(dir, name + ".apk");
                String pkg = pkgI.getText().toString().trim().toLowerCase();
                if (pkg.length() > 0 && !pkg.matches("[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+")) {
                    runOnUiThread(new Runnable() { public void run() {
                        Toast.makeText(Web2ApkActivity.this, "包名格式不对，如 com.abc.web", Toast.LENGTH_SHORT).show();
                    }});
                    return;
                }
                RepackUtil.buildFromAssets(getApplicationContext(), url, name, icon, pkg.length() > 0 ? pkg : null, out);
                runOnUiThread(new Runnable() { public void run() {
                    pathL.setText("安装包: " + out.getAbsolutePath());
                    try {
                        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(Web2ApkActivity.this,
                                getPackageName() + ".files", out);
                        Intent it = new Intent(Intent.ACTION_VIEW);
                        it.setDataAndType(uri, "application/vnd.android.package-archive");
                        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(it);
                    } catch (Throwable t) { }
                }});
            } catch (final Throwable t) {
                runOnUiThread(new Runnable() { public void run() {
                    pathL.setText("打包失败: " + t);
                }});
            }
        }}).start();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7001 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(
                        getContentResolver().openInputStream(data.getData()));
                if (bmp == null) { Toast.makeText(this, "图片解析失败", Toast.LENGTH_SHORT).show(); return; }
                android.graphics.Bitmap out = android.graphics.Bitmap.createScaledBitmap(bmp, 192, 192, true);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
                iconBytes = bo.toByteArray();
                Toast.makeText(this, "图标已设置", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "图标设置失败: " + t, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }

}
