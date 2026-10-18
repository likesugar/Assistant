package com.ziyuan.Assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** MT管理器风格快捷方式编辑页（扁平化深色·单份干净实现） */
public class ShortcutEditActivity extends Activity {

    private EditText etName, etPkg, etCls, etData, etExtra, etCustom;
    private RadioButton rbView, rbMain, rbCustom;
    private android.widget.CheckBox cbNewTask, cbRoot;
    private TextView iconHint;
    private ImageView iconView;
    private android.graphics.Bitmap curIcon = null;
    private String iconPath = "";
    private String editKey = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.c(ShortcutEditActivity.this, 0xFF000000, 0xFFDDE8F2));

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(14), dp(12), dp(14));
        top.setBackgroundColor(0xFF215870);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(20);
        back.setPadding(dp(8), 0, dp(16), 0);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { goBackToList(); } });
        top.addView(back);
        TextView title = new TextView(this);
        title.setText("创建快捷方式");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.getPaint().setFakeBoldText(true);
        top.addView(title);
        root.addView(top);

        // 按钮行：收藏/取消/打开/创建
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(dp(12), 0, dp(12), dp(6));
        String[] labels = {"收藏", "取消", "打开", "创建"};
        View.OnClickListener[] acts = {
            new View.OnClickListener() { public void onClick(View v) { doSave(false); } },
            new View.OnClickListener() { public void onClick(View v) { finish(); } },
            new View.OnClickListener() { public void onClick(View v) { doOpen(); } },
            new View.OnClickListener() { public void onClick(View v) { doSave(true); } }
        };
        for (int i = 0; i < labels.length; i++) {
            TextView bt = new TextView(this);
            bt.setText(labels[i]);
            bt.setTextColor(0xFFCFE3F5);
            bt.setTextSize(15);
            bt.setGravity(Gravity.CENTER);
            bt.setBackgroundColor(0xFF4E86A8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lp.rightMargin = dp(8);
            bt.setLayoutParams(lp);
            bt.setOnClickListener(acts[i]);
            btns.addView(bt);
        }
        root.addView(btns);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), dp(14));

        // 图标区：提示 + 预览 + 两个按钮
        iconHint = new TextView(this);
        iconHint.setText("快捷方式的图标(点击更换)");
        iconHint.setTextColor(0xFF16607A);
        iconHint.setTextSize(13);
        iconHint.setGravity(Gravity.CENTER);
        iconHint.setPadding(0, dp(4), 0, dp(4));
        body.addView(iconHint);
        iconView = new ImageView(this);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ivp = new LinearLayout.LayoutParams(dp(64), dp(64));
        ivp.gravity = Gravity.CENTER_HORIZONTAL;
        iconView.setLayoutParams(ivp);
        iconView.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickSystemIcon(); } });
        body.addView(iconView);
        body.addView(flatButton("从已装应用选择 ›", new View.OnClickListener() { public void onClick(View v) { pickApp(); } }));

        // 字段
        etName = field(body, "名称");
        etPkg = field(body, "包名");
        etPkg.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void onTextChanged(CharSequence c, int a, int s2, int d2) { }
            public void afterTextChanged(android.text.Editable e) {
                if (curIcon != null) return;
                try {
                    curIcon = iconToBitmap(getPackageManager().getApplicationIcon(e.toString()));
                    iconView.setImageBitmap(curIcon);
                } catch (Throwable ignored) {}
            }
        });
        etCls = field(body, "活动");
        etData = field(body, "附加Data(Uri)");
        etExtra = field(body, "附加Extra(每行一个)");
        etExtra.setSingleLine(false);
        etExtra.setMinLines(2);
        etExtra.setGravity(Gravity.TOP);

        TextView at = new TextView(this);
        at.setText("附加Action");
        at.setTextColor(Theme.c(ShortcutEditActivity.this, 0xFF8A919E, 0xFF16607A));
        at.setTextSize(13);
        at.setPadding(0, dp(8), 0, dp(2));
        body.addView(at);
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rbView = radio(rg, "android.intent.action.VIEW", true);
        rbMain = radio(rg, "android.intent.action.MAIN", false);
        rbCustom = radio(rg, "自定义: android.intent.action.VIEW", false);
        body.addView(rg);
        etCustom = field(body, "自定义Action");
        etCustom.setVisibility(View.GONE);
        rbCustom.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etCustom.setVisibility(View.VISIBLE); } });
        rbView.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etCustom.setVisibility(View.GONE); } });
        rbMain.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etCustom.setVisibility(View.GONE); } });

        cbNewTask = check(body, "FLAG_ACTIVITY_NEW_TASK", true);
        cbRoot = check(body, "使用Root打开(仅识别包名和活动并忽略其他参数)", false);

        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        // 编辑模式回填（直传数据优先，其次镜像）
        String key = getIntent().getStringExtra("key");
        String[] it = null;
        if (getIntent().hasExtra("e0")) {
            it = new String[11];
            for (int j = 0; j < 11; j++) it[j] = getIntent().getStringExtra("e" + j) == null ? "" : getIntent().getStringExtra("e" + j);
        } else if (key != null) {
            String raw = getSharedPreferences("home_shortcuts", MODE_PRIVATE).getString("k_" + key, null);
            if (raw != null) it = raw.split("\\\\u0001");
        }
        if (it != null && it.length >= 3) {
            editKey = key;
            etName.setText(z(it, 0));
            etPkg.setText(z(it, 1));
            etCls.setText(z(it, 2));
            if (z(it, 3).length() > 0) etData.setText(z(it, 3));
            if (z(it, 4).length() > 0) etExtra.setText(z(it, 4));
            int am2 = parseInt(z(it, 5), 0);
            if (am2 == 1) rbMain.setChecked(true);
            else if (am2 == 2) { rbCustom.setChecked(true); etCustom.setVisibility(View.VISIBLE); etCustom.setText(z(it, 6)); }
            cbNewTask.setChecked(!z(it, 7).equals("0"));
            cbRoot.setChecked(z(it, 8).equals("1"));
            if (z(it, 10).length() > 0) {
                iconPath = z(it, 10);
            } else {
                iconPath = "";
                // 新建/选择而来: 用目标应用真实图标, 不再沿用上一个快捷方式的图标
                if (curIcon == null && z(it, 1).length() > 0) {
                    try {
                        android.graphics.drawable.Drawable di = getPackageManager().getApplicationIcon(z(it, 1));
                        int w = di.getIntrinsicWidth() > 0 ? di.getIntrinsicWidth() : 96;
                        int h = di.getIntrinsicHeight() > 0 ? di.getIntrinsicHeight() : 96;
                        android.graphics.Bitmap bm3 = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                        android.graphics.Canvas cv = new android.graphics.Canvas(bm3);
                        di.setBounds(0, 0, w, h);
                        di.draw(cv);
                        curIcon = bm3;
                        iconView.setImageBitmap(bm3);
                    } catch (Throwable ignored) {}
                }
            }
            if (iconPath.length() == 0 && curIcon == null) {
                // 再兜底一次：编辑回填的 curIcon 可能来自包名
            }
            if (iconPath.length() > 0) {
                android.graphics.Bitmap bm2 = android.graphics.BitmapFactory.decodeFile(iconPath);
                if (bm2 != null) { curIcon = bm2; iconView.setImageBitmap(bm2); iconHint.setText("已使用自选图标"); }
            }
        }
    }

    private static String z(String[] a, int i) { return i < a.length && a[i] != null ? a[i] : ""; }
    private int parseInt(String s2, int def) { try { return Integer.parseInt(s2); } catch (Throwable e) { return def; } }

    private TextView flatButton(String text, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF3D7BFF);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Theme.c(ShortcutEditActivity.this, 0xFF1B222B, 0xFFE7EDF8));
        t.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(click);
        return t;
    }

    private EditText field(LinearLayout parent, String hint) {
        // 图2式：标签小字(青)在上, 值大字加粗, 下划线
        TextView lb = new TextView(this);
        lb.setText(hint);
        lb.setTextColor(Theme.c(ShortcutEditActivity.this, 0xFF4A8AA5, 0xFF16607A));
        lb.setTextSize(14);
        lb.setPadding(0, dp(10), 0, 0);
        parent.addView(lb);
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextColor(Theme.c(ShortcutEditActivity.this, 0xFFF2F4F8, 0xFF1A1A1A));
        et.setHintTextColor(Theme.c(ShortcutEditActivity.this, 0xFF5C6470, 0xFF9AA5B4));
        et.setTextSize(19);
        et.getPaint().setFakeBoldText(true);
        et.setBackground(null);
        et.setPadding(0, dp(2), 0, dp(8));
        parent.addView(et);
        View line = new View(this);
        line.setBackgroundColor(Theme.c(ShortcutEditActivity.this, 0xFF3A4652, 0xFFB9C4CF));
        parent.addView(line, new LinearLayout.LayoutParams(-1, 1));
        return et;
    }

    private RadioButton radio(RadioGroup rg, String text, boolean checked) {
        RadioButton rb = new RadioButton(this);
        rb.setText(text);
        rb.setTextColor(Theme.c(ShortcutEditActivity.this, 0xFFC8CDD4, 0xFF1A1A1A));
        rb.setPadding(dp(6), dp(4), dp(12), dp(4));
        rb.setChecked(checked);
        rg.addView(rb);
        return rb;
    }

    private android.widget.CheckBox check(LinearLayout parent, String text, boolean checked) {
        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText(text);
        cb.setTextColor(Theme.c(ShortcutEditActivity.this, 0xFFB9C0CA, 0xFF1A1A1A));
        cb.setTextSize(15);
        cb.getPaint().setFakeBoldText(true);
        cb.setChecked(checked);
        parent.addView(cb);
        return cb;
    }

    private void pickSystemIcon() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "选择图标"), 7001);
        } catch (Throwable t) { toast("无法打开系统选择器"); }
    }

    private void pickApp() {
        try { startActivityForResult(new Intent(this, AppPickerActivity.class), 7002); }
        catch (Throwable e) { toast("无法打开应用选择页"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7002 && res == RESULT_OK && data != null) {
            String pkg = data.getStringExtra("pkg");
            String cls = data.getStringExtra("cls");
            String nm = data.getStringExtra("name");
            if (pkg != null) {
                etPkg.setText(pkg);
                etCls.setText(cls == null ? "" : cls);
                if (nm != null && nm.length() > 0 && etName.getText().length() == 0) etName.setText(nm);
            }
            return;
        }
        if (req == 7001 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                android.graphics.BitmapFactory.decodeStream(is, null, o);
                try { is.close(); } catch (Throwable ignored) {}
                int size = Math.max(o.outWidth, o.outHeight);
                int sample = 1;
                while (size / sample > 192) sample *= 2;
                o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = sample;
                is = getContentResolver().openInputStream(data.getData());
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(is, null, o);
                try { is.close(); } catch (Throwable ignored) {}
                if (bm != null) {
                    curIcon = bm;
                    iconPath = "";
                    iconView.setImageBitmap(bm);
                    iconHint.setText("已使用自选图标");
                }
            } catch (Throwable t) { toast("读取图片失败"); }
        }
    }

    private android.graphics.Bitmap iconToBitmap(Drawable d) {
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 96;
        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 96;
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bm);
        d.setBounds(0, 0, w, h);
        d.draw(cv);
        return bm;
    }

    private Intent buildIntent() {
        String pkg = etPkg.getText().toString().trim();
        String cls = etCls.getText().toString().trim();
        String data = etData.getText().toString().trim();
        String custom = etCustom.getText().toString().trim();
        if (pkg.length() == 0) { toast("包名不能为空"); return null; }
        Intent i = new Intent();
        if (rbCustom.isChecked() && custom.length() > 0) i.setAction(custom);
        else if (rbMain.isChecked()) { i.setAction(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER); }
        else i.setAction(Intent.ACTION_VIEW);
        if (data.length() > 0) { try { i.setData(Uri.parse(data)); } catch (Throwable t) { toast("Data格式错误"); return null; } }
        if (cls.length() > 0) i.setComponent(new android.content.ComponentName(pkg, cls));
        for (String ln : etExtra.getText().toString().split("\n")) {
            ln = ln.trim();
            int eq = ln.indexOf('=');
            if (eq <= 0) continue;
            String k = ln.substring(0, eq), v = ln.substring(eq + 1);
            try {
                if (v.matches("-?\\d+")) i.putExtra(k, Long.parseLong(v));
                else if (v.equals("true") || v.equals("false")) i.putExtra(k, Boolean.parseBoolean(v));
                else i.putExtra(k, v);
            } catch (Throwable t) { i.putExtra(k, v); }
        }
        if (cbNewTask.isChecked()) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private void doOpen() {
        Intent i = buildIntent();
        if (i == null) return;
        try { startActivity(i); }
        catch (Throwable t) {
            if (cbRoot.isChecked()) rootOpen();
            else toast("打开失败: " + t.getClass().getSimpleName());
        }
    }

    private void rootOpen() {
        try {
            String cls = etCls.getText().toString().trim();
            String cmd = "am start -n " + etPkg.getText().toString().trim() + (cls.length() > 0 ? "/" + cls : "");
            Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            toast(p2.waitFor() == 0 ? "Root启动成功" : "Root启动失败");
        } catch (Throwable t) { toast("无Root权限"); }
    }

    private void doSave(boolean alsoPin) {
        String name = etName.getText().toString().trim();
        String pkg = etPkg.getText().toString().trim();
        if (name.length() == 0 || pkg.length() == 0) { toast("名称和包名不能为空"); return; }
        if (curIcon == null) {
            // 兜底：恢复最近自选图标
            try {
                java.io.File idir = new java.io.File(getFilesDir(), "sc_icons");
                java.io.File[] fs = idir.listFiles();
                if (fs != null && fs.length > 0) {
                    java.io.File newest = fs[0];
                    for (java.io.File f : fs) if (f.lastModified() > newest.lastModified()) newest = f;
                    android.graphics.Bitmap bm2 = android.graphics.BitmapFactory.decodeFile(newest.getAbsolutePath());
                    if (bm2 != null) { curIcon = bm2; iconPath = newest.getAbsolutePath(); }
                }
            } catch (Throwable ignored) {}
        }
        String rec = joinFields();
        String key = editKey != null ? editKey : ("s" + System.currentTimeMillis());
        if (curIcon != null) {
            try {
                java.io.File idir = new java.io.File(getFilesDir(), "sc_icons");
                idir.mkdirs();
                java.io.File ifile = new java.io.File(idir, key + ".png");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(ifile);
                curIcon.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fo);
                fo.close();
                iconPath = ifile.getAbsolutePath();
                rec = joinFields();
            } catch (Throwable ignored) {}
        }
        ShortcutActivity.saveByKey(this, key, rec);
        if (alsoPin) {
            Intent i = buildIntent();
            if (i != null) {
                try {
                    android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) getSystemService(SHORTCUT_SERVICE);
                    if (sm != null && sm.isRequestPinShortcutSupported()) {
                        android.content.pm.ShortcutInfo.Builder sb2 = new android.content.pm.ShortcutInfo.Builder(this, key)
                            .setShortLabel(name).setLongLabel(name).setIntent(i);
                        if (curIcon != null) sb2.setIcon(android.graphics.drawable.Icon.createWithBitmap(curIcon));
                        sm.requestPinShortcut(sb2.build(), null);
                        toast("请在系统弹窗中确认添加");
                    } else toast("桌面不支持固定快捷方式");
                } catch (Throwable t) { toast("固定失败: " + t.getClass().getSimpleName()); }
            }
        }
        finish();
    }

    private String joinFields() {
        String am2 = rbMain.isChecked() ? "1" : (rbCustom.isChecked() ? "2" : "0");
        return etName.getText().toString().trim() + SEP
            + etPkg.getText().toString().trim() + SEP
            + etCls.getText().toString().trim() + SEP
            + etData.getText().toString().trim() + SEP
            + etExtra.getText().toString().trim() + SEP
            + am2 + SEP
            + etCustom.getText().toString().trim() + SEP
            + (cbNewTask.isChecked() ? "1" : "0") + SEP
            + (cbRoot.isChecked() ? "1" : "0") + SEP
            + iconPath;
    }

    private void goBackToList() {
        // 图2返回 → 图3活动选择(重选活动); 无包名时直接退出
        String p2 = etPkg.getText().toString().trim();
        if (p2.length() > 0) {
            String n2 = etName.getText().toString().trim();
            if (n2.length() == 0) n2 = p2;
            try {
                startActivity(new Intent(ShortcutEditActivity.this, ActivityListActivity.class)
                    .putExtra("pkg", p2).putExtra("name", n2).putExtra("fromEdit", true));
                return;
            } catch (Throwable ignored) {}
        }
        finish();
    }

    @Override
    public void onBackPressed() { goBackToList(); }

    private String newestIconFile() {
        try {
            java.io.File idir = new java.io.File(getFilesDir(), "sc_icons");
            java.io.File[] fs = idir.listFiles();
            if (fs == null || fs.length == 0) return "";
            java.io.File newest = fs[0];
            for (java.io.File f : fs) if (f.lastModified() > newest.lastModified()) newest = f;
            return newest.getAbsolutePath();
        } catch (Throwable e) { return ""; }
    }

    static final String SEP = ShortcutActivity.S1;

    private void toast(String s2) { Toast.makeText(this, s2, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
