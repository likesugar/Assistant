package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 用药提醒(小工具18.0同款): 用药计划 + 多时间点强提醒 */
public class MedPlanActivity extends Activity {

    private static final String CH = "medplan_v2"; // 换id强制重建: 旧渠道被系统缓存低优先级, 不走状态栏/横幅
    private LinearLayout listHost; private FrameLayout formHost;
    private ScrollView listScroll;
    private String formPic = "";
    private Runnable picTag;
    private JSONArray plans;

    // 表单临时状态
    private JSONArray formTimes = new JSONArray();
    private String formRelation = "", formRepeat = "每天";
    private long formStart = 0, formEnd = 0;
    private int editIdx = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        plans = load(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.c(this, 0xFF000000, 0xFFEAF2FF));
        root.setId(17901);

        listScroll = new ScrollView(this);
        listHost = new LinearLayout(this);
        listHost.setOrientation(LinearLayout.VERTICAL);
        listHost.setPadding(dp(16), dp(40), dp(16), dp(140));
        listScroll.addView(listHost);
        root.addView(listScroll, new FrameLayout.LayoutParams(-1, -1));



        TextView add = mkText("＋  新增用药计划", 16, true, Color.WHITE);
        add.setGravity(Gravity.CENTER);
        add.setBackgroundResource(R.drawable.bg_pill_blue);
        add.setPadding(dp(20), dp(16), dp(20), dp(16));
        add.setOnClickListener(v -> showForm(-1));
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        alp.leftMargin = dp(16); alp.rightMargin = dp(16); alp.bottomMargin = dp(24);

        root.addView(add, alp);

        formHost = new FrameLayout(this);
        formHost.setBackgroundColor(Theme.c(this, 0xFF000000, 0xFFEAF2FF));
        formHost.setVisibility(View.GONE);
        root.addView(formHost, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        Immersive.hide(this);
        renderList();
        MedWatchService.ensure(this);
        // 精确闹钟权限(Android 12+): 不批的话提醒会延迟
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 31 && am != null && !am.canScheduleExactAlarms()) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) {}
        }
    }

    private void renderList() {
        listHost.removeAllViews();
        if (plans.length() == 0) {
            TextView e = mkText("还没有用药计划\n点下方「＋ 新增用药计划」创建", 14, false, Theme.c(this, 0xFF8A919E, 0xFF6B7280));
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(120), 0, 0);
            listHost.addView(e);
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < plans.length(); i++) {
            final int idx = i;
            JSONObject o = plans.optJSONObject(i);
            if (o == null) continue;
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rlp.topMargin = dp(14);
                listHost.addView(row, rlp);
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setBackgroundResource(Theme.dark(this) ? R.drawable.bg_card_oled : R.drawable.bg_card_white);
            cell.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 1) clp.leftMargin = dp(14);
            // 相册照片缩略(若有) + 药名
            String pic = o.optString("pic", "");
            if (pic.length() > 0 && new java.io.File(pic).exists()) {
                try {
                    android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                    o2.inSampleSize = 4;
                    android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(pic, o2);
                    if (bm != null) {
                        android.widget.ImageView iv = new android.widget.ImageView(this);
                        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                        iv.setImageBitmap(bm);
                        iv.setClipToOutline(true);
                        iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                            public void getOutline(android.view.View v, android.graphics.Outline ol) {
                                ol.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
                            }
                        });
                        cell.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));
                    }
                } catch (Throwable ignored) {}
            }
            TextView name = mkText(o.optString("name", ""), 15, true, Theme.c(this, 0xFFF2F4F8, 0xFF1F2329));
            name.setPadding(0, dp(8), 0, 0);
            cell.addView(name);
            TextView dose = mkText(o.optString("dose", "1") + o.optString("unit", "片") + " · " + o.optString("relation", ""), 12, false, Theme.c(this, 0xFF8A919E, 0xFF6B7280));
            dose.setPadding(0, dp(4), 0, 0);
            cell.addView(dose);
            // 首个时间胶囊
            JSONArray ts = o.optJSONArray("times");
            // 状态(确认/已服用)在时间左边, 同一行
            String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            boolean hasDue = false, allDueDone = true;
            if (ts != null) for (int t = 0; t < ts.length(); t++) {
                String tt = ts.optString(t);
                if (tt.compareTo(now) <= 0) {
                    hasDue = true;
                    if (!medDone(this, idx, t)) allDueDone = false;
                }
            }
            LinearLayout hrow = new LinearLayout(this);
            hrow.setOrientation(LinearLayout.HORIZONTAL);
            hrow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
            hlp.topMargin = dp(10);
            if (hasDue) {
                if (allDueDone) {
                    TextView done = mkText("已服用", 13, true, 0xFF34A853);
                    done.setBackgroundResource(R.drawable.bg_chip_green);
                    done.setPadding(dp(12), dp(6), dp(12), dp(6));
                    hrow.addView(done);
                } else {
                    final TextView conf = mkText("确认", 13, true, Color.WHITE);
                    conf.setBackgroundResource(R.drawable.bg_btn_red);
                    conf.setPadding(dp(14), dp(6), dp(14), dp(6));
                    conf.setOnClickListener(v -> {
                        try {
                            if (ts != null) for (int t = 0; t < ts.length(); t++) {
                                String tt = ts.optString(t);
                                if (tt.compareTo(now) <= 0) confirmMed(this, idx, t);
                            }
                        } catch (Throwable ignored) {}
                        renderList();
                    });
                    hrow.addView(conf);
                }
                TextView gap = new TextView(this);
                hrow.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
            }
            TextView chip = mkText((ts != null && ts.length() > 0 ? ts.optString(0) : "--:--") + (ts != null && ts.length() > 1 ? " +1" : ""), 13, true, 0xFF315CDE);
            chip.setBackgroundResource(R.drawable.bg_chip_blue);
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setSingleLine(true);
            hrow.addView(chip);
            cell.addView(hrow, hlp);
            cell.setOnClickListener(v -> showForm(idx));
            row.addView(cell, clp);
        }
    }

    // ---------- 表单 ----------
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7001 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                java.io.File dir = new java.io.File(getExternalFilesDir(null), "med_pics");
                dir.mkdirs();
                java.io.File dst = new java.io.File(dir, "pic_" + System.currentTimeMillis() + ".jpg");
                java.io.InputStream in = getContentResolver().openInputStream(data.getData());
                java.io.FileOutputStream fo = new java.io.FileOutputStream(dst);
                byte[] b = new byte[16384]; int n;
                while ((n = in.read(b)) > 0) fo.write(b, 0, n);
                in.close(); fo.close();
                formPic = dst.getAbsolutePath();
                if (picTag != null) picTag.run();
            } catch (Throwable e) { toast("照片保存失败"); }
        }
    }

    private void showForm(int idx) {
        editIdx = idx;
        formTimes = new JSONArray();
        formRelation = ""; formRepeat = "每天"; formStart = System.currentTimeMillis(); formEnd = 0;
        if (idx >= 0) {
            JSONObject o = plans.optJSONObject(idx);
            try { formTimes = new JSONArray(o.optString("times", "[]")); } catch (Throwable ignored) {}
            formRelation = o.optString("relation", "");
            formRepeat = o.optString("repeat", "每天");
            formStart = o.optLong("start", System.currentTimeMillis());
            formEnd = o.optLong("end", 0);
        }
        listScroll.setVisibility(View.GONE);
        formHost.removeAllViews();
        formHost.setVisibility(View.VISIBLE);
        ScrollView fs = new ScrollView(this);
        LinearLayout card = card();
        card.setPadding(dp(16), dp(28), dp(16), dp(120));
        fs.addView(card);
        formHost.addView(fs, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundResource(Theme.dark(this) ? R.drawable.bg_card_oled : R.drawable.bg_card_white);
        bottomBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        formHost.addView(bottomBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        formPic = idx >= 0 ? plans.optJSONObject(idx).optString("pic", "") : "";
        // 相册照片(药物名称上方)
        final android.widget.ImageView picThumb = new android.widget.ImageView(this);
        picThumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        LinearLayout picRow = new LinearLayout(this);
        picRow.setGravity(Gravity.CENTER_VERTICAL);
        picRow.setPadding(0, dp(6), 0, dp(10));
        LinearLayout picWrap = new LinearLayout(this);
        picWrap.setOrientation(LinearLayout.VERTICAL);
        picWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(dp(64), dp(64));
        picWrap.addView(picThumb, ptl);
        TextView picBtn = mkText("📷 选择相册照片", 13, true, 0xFF315CDE);
        picBtn.setPadding(dp(12), 0, 0, 0);
        picRow.addView(picWrap);
        picRow.addView(picBtn);
        final android.graphics.Bitmap[] picHolder = new android.graphics.Bitmap[1];
        Runnable showPic = () -> {
            android.graphics.Bitmap bm = null;
            try {
                if (formPic.length() > 0 && new java.io.File(formPic).exists())
                    bm = android.graphics.BitmapFactory.decodeFile(formPic, new android.graphics.BitmapFactory.Options());
            } catch (Throwable ignored) {}
            picHolder[0] = bm;
            if (bm != null) picThumb.setImageBitmap(bm);
            else picThumb.setImageResource(R.drawable.bg_input);
        };
        showPic.run();
        picBtn.setOnClickListener(v -> {
            try {
                Intent pi = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pi.setType("image/*");
                startActivityForResult(pi, 7001);
            } catch (Throwable ignored) { toast("无法打开相册"); }
        });
        picTag = showPic;
        card.addView(picRow);

        final EditText name = rowInput(card, "", "药品名称", "请输入药品名称", idx >= 0 ? plans.optJSONObject(idx).optString("name", "") : "");

        // 单次用量: 数字 + 片/粒
        LinearLayout doseRow = rowHead(card, "🥛", "单次用量");
        LinearLayout doseLine = new LinearLayout(this);
        doseLine.setOrientation(LinearLayout.HORIZONTAL);
        doseLine.setGravity(Gravity.CENTER_VERTICAL);
        final EditText dose = new EditText(this);
        dose.setText(idx >= 0 ? plans.optJSONObject(idx).optString("dose", "1") : "1");
        dose.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        dose.setGravity(Gravity.CENTER);
        dose.setTextSize(15); dose.setTextColor(0xFF1F2329);
        dose.setBackgroundResource(R.drawable.bg_input);
        dose.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, -2, 1f);
        dlp.leftMargin = dp(10);
        doseLine.addView(dose, dlp);
        final String[] unit = {idx >= 0 ? plans.optJSONObject(idx).optString("unit", "片") : "片"};
        final LinearLayout unitBox = new LinearLayout(this);
        for (final String u : new String[]{"片", "粒"}) {
            TextView c = mkText(u, 14, u.equals(unit[0]), u.equals(unit[0]) ? 0xFF315CDE : Theme.c(this, 0xFF8A919E, 0xFF6B7280));
            c.setBackgroundResource(u.equals(unit[0]) ? R.drawable.bg_chip_blue : R.drawable.bg_chip_gray);
            c.setPadding(dp(18), dp(8), dp(18), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(10);
            c.setOnClickListener(v -> {
                unit[0] = u;
                renderUnitPick(unitBox, unit);
            });
            unitBox.addView(c, lp);
        }
        doseLine.addView(unitBox);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(-1, -2);
        dlp2.leftMargin = dp(10);
        doseRow.addView(doseLine, dlp2);

        // 服用时间
        LinearLayout timeHead = rowHead(card, "⏰", "服用时间");
        final TextView timeAdd = mkText("＋ 添加时间", 14, true, 0xFF315CDE);
        timeAdd.setBackgroundResource(R.drawable.bg_chip_dash);
        timeAdd.setPadding(dp(22), dp(12), dp(22), dp(12));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = dp(12);
        timeHead.addView(timeAdd, tlp);
        final LinearLayout timeChips = new LinearLayout(this);
        timeChips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(-1, -2);
        clp2.topMargin = dp(10);
        card.addView(timeChips);
        Runnable[] renderTimes = new Runnable[1];
        renderTimes[0] = () -> {
            timeChips.removeAllViews();
            for (int t = 0; t < formTimes.length(); t++) {
                final int ti = t;
                TextView chip = mkText(formTimes.optString(ti) + " ✕", 14, true, 0xFF315CDE);
                chip.setBackgroundResource(R.drawable.bg_chip_blue);
                chip.setPadding(dp(14), dp(8), dp(14), dp(8));
                chip.setOnClickListener(v -> {
                    JSONArray na = new JSONArray();
                    for (int m = 0; m < formTimes.length(); m++) if (m != ti) { try { na.put(formTimes.get(m)); } catch (Throwable ignored) {} }
                    formTimes = na;
                    renderTimes[0].run();
                });
                timeChips.addView(chip, chipLp(t > 0));
            }
        };
        timeAdd.setOnClickListener(v -> new TimePickerDialog(MedPlanActivity.this, (tp, hh, mm) -> {
            formTimes.put(String.format(Locale.US, "%02d:%02d", hh, mm));
            renderTimes[0].run();
        }, 8, 0, true).show());
        renderTimes[0].run();

        // 服药关系
        LinearLayout relHead = rowHead(card, "🍽️", "服药关系");
        final String[][] rels = {{"饭前", "饭后", "睡前"}};
        final LinearLayout relBox = new LinearLayout(this);
        relHead.addView(relBox);
        Runnable renderRel = () -> { renderPick(relBox, rels[0], new String[]{formRelation}, v -> formRelation = v); };
        renderRel.run();

        // 重复周期
        LinearLayout repHead = rowHead(card, "🔁", "重复周期");
        final LinearLayout repBox = new LinearLayout(this);
        repHead.addView(repBox);
        Runnable renderRep = () -> { renderPick(repBox, new String[]{"每天", "工作日", "自定义"}, new String[]{formRepeat}, v -> formRepeat = v); };
        renderRep.run();

        // 开始/结束日期(同一行紧凑排布)
        LinearLayout dateRow = rowHead(card, "📅", "开始 / 结束日期");
        LinearLayout dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout startCell = new LinearLayout(this);
        startCell.setOrientation(LinearLayout.VERTICAL);
        startCell.setBackgroundResource(R.drawable.bg_input);
        startCell.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView sLabel = mkText("开始", 11, false, Theme.c(this, 0xFF8A919E, 0xFF6B7280));
        final TextView startV = mkText(fmt(formStart), 14, true, 0xFF1F2329);
        startCell.addView(sLabel); startCell.addView(startV);
        startCell.setOnClickListener(v -> pickDate(formStart, t -> { formStart = t; startV.setText(fmt(t)); }));
        LinearLayout endCell = new LinearLayout(this);
        endCell.setOrientation(LinearLayout.VERTICAL);
        endCell.setBackgroundResource(R.drawable.bg_input);
        endCell.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView eLabel = mkText("结束", 11, false, Theme.c(this, 0xFF8A919E, 0xFF6B7280));
        final TextView endV = mkText(formEnd > 0 ? fmt(formEnd) : "未设置", 14, true, formEnd > 0 ? 0xFF1F2329 : Theme.c(this, 0xFFB7BFCC, 0xFFB7BFCC));
        endCell.addView(eLabel); endCell.addView(endV);
        endCell.setOnClickListener(v -> pickDate(formEnd > 0 ? formEnd : System.currentTimeMillis(), t -> { formEnd = t; endV.setText(fmt(t)); endV.setTextColor(0xFF1F2329); }));
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams ecp = new LinearLayout.LayoutParams(0, -2, 1f);
        ecp.leftMargin = dp(10);
        dateBox.addView(startCell, scp); dateBox.addView(endCell, ecp);
        LinearLayout.LayoutParams dlp3 = new LinearLayout.LayoutParams(-1, -2);
        dlp3.leftMargin = dp(10); dlp3.topMargin = dp(12);
        dateRow.addView(dateBox, dlp3);

        // 保存/取消
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(20);
        TextView cancel = mkText("取消", 16, true, 0xFF315CDE);
        cancel.setBackgroundResource(R.drawable.bg_btn_outline);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(14), 0, dp(14));
        TextView save = mkText("保存计划", 16, true, Color.WHITE);
        save.setBackgroundResource(R.drawable.bg_pill_blue);
        save.setGravity(Gravity.CENTER);
        save.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, -2, 1f);
        c2.leftMargin = dp(14);
        btns.addView(cancel, c1); btns.addView(save, c2);
        bottomBar.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        if (editIdx >= 0) {
            TextView del = mkText("🗑 删除此计划", 14, false, 0xFFFF7B8A);
            del.setGravity(Gravity.CENTER);
            del.setPadding(0, dp(14), 0, 0);
            card.addView(del, new LinearLayout.LayoutParams(-1, -2));
            del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("删除计划")
                .setMessage("删除「" + name.getText().toString() + "」?")
                .setPositiveButton("删除", (d, w) -> { removePlan(editIdx); formHost.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE); })
                .setNegativeButton("取消", null).show());
        }
        cancel.setOnClickListener(v -> { formHost.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE); });
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { toast("请输入药品名称"); return; }
            if (formTimes.length() == 0) { toast("请至少添加一个服用时间"); return; }
            try {
                JSONObject o = new JSONObject();
                o.put("name", n);
                o.put("pic", formPic);
                o.put("dose", dose.getText().toString().trim());
                o.put("unit", unit[0]);
                o.put("times", formTimes);
                o.put("relation", formRelation);
                o.put("repeat", formRepeat);
                o.put("start", formStart);
                o.put("end", formEnd);
                if (editIdx >= 0) plans.put(editIdx, o); else plans.put(o);
                save(this, plans);
                scheduleAll(this);
                formHost.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE);
                renderList();
                toast("已保存，到点提醒 📳");
            } catch (Throwable ignored) {}
        });
    }

    private void renderUnitPick(LinearLayout box, String[] cur) {
        box.removeAllViews();
        renderPick(box, new String[]{"片", "粒"}, cur, v -> {});
    }

    private void renderPick(LinearLayout box, String[] opts, String[] cur, java.util.function.Consumer<String> set) {
        box.removeAllViews();
        for (final String o : opts) {
            boolean on = o.equals(cur[0]);
            TextView c = mkText(o, 14, on, on ? 0xFF315CDE : Theme.c(this, 0xFF8A919E, 0xFF6B7280));
            c.setBackgroundResource(on ? R.drawable.bg_chip_blue : R.drawable.bg_chip_gray);
            c.setPadding(dp(18), dp(8), dp(18), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(10);
            c.setOnClickListener(v -> { cur[0] = o; set.accept(o); renderPick(box, opts, cur, set); });
            box.addView(c, lp);
        }
    }

    private LinearLayout rowHead(LinearLayout parent, String icon, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic = mkText(icon, 17, false, 0xFF1F2329);
        head.addView(ic);
        TextView tv = mkText(label, 16, true, 0xFF1F2329);
        tv.setPadding(dp(10), 0, 0, 0);
        head.addView(tv);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = dp(18);
        row.addView(head);
        parent.addView(row, hlp);
        return row;
    }

    private EditText rowInput(LinearLayout parent, String icon, String label, String hint, String val) {
        LinearLayout row = rowHead(parent, icon, label);
        EditText et = new EditText(this);
        et.setHint(hint); et.setText(val); et.setSingleLine(true);
        et.setTextSize(15); et.setTextColor(0xFF1F2329);
        et.setHintTextColor(Theme.c(this, 0xFFB7BFCC, 0xFFB7BFCC));
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = dp(10);
        row.addView(et, lp);
        return et;
    }

    private LinearLayout.LayoutParams chipLp(boolean margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        if (margin) lp.leftMargin = dp(8);
        return lp;
    }

    private LinearLayout.LayoutParams endLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.gravity = Gravity.END;
        return lp;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.bg_card_white);
        c.setPadding(dp(20), dp(20), dp(20), dp(20));
        return c;
    }

    private TextView mkText(String t, int sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextSize(sp); tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private void pickDate(long cur, java.util.function.Consumer<Long> cb) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(cur);
        new android.app.DatePickerDialog(this, (dp2, y, m, d) -> {
            Calendar r = Calendar.getInstance();
            r.set(y, m, d, 23, 59, 59);
            cb.accept(r.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private int hour(String t) { try { return Integer.parseInt(t.split(":")[0]); } catch (Throwable e) { return 8; } }
    private int min(String t) { try { return Integer.parseInt(t.split(":")[1]); } catch (Throwable e) { return 0; } }
    private String fmt(long t) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t)); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------- 存储 ----------
    /** 确认按钮广播 */
    public static class MedConfirmReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                if (!"MED_CONFIRM".equals(intent.getAction())) return;
                confirmMed(context, intent.getIntExtra("i", -1), intent.getIntExtra("t", -1));
                Toast.makeText(context, "已确认服药 ✓", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
        }
    }

    static JSONArray load(Context c) {
        try {
            JSONArray arr = new JSONArray(c.getSharedPreferences("medplan", 0).getString("plans", "[]"));
            boolean changed = false;
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.optJSONObject(k);
                if (o != null && !o.has("id")) {
                    o.put("id", String.valueOf(System.currentTimeMillis()) + "_" + k);
                    changed = true; // 立即落盘, 保证确认键与读取键一致
                }
            }
            if (changed) save(c, arr);
            return arr;
        } catch (Throwable e) { return new JSONArray(); }
    }
    static void save(Context c, JSONArray a) { c.getSharedPreferences("medplan", 0).edit().putString("plans", a.toString()).apply(); }
    private void removePlan(int idx) {
        JSONArray na = new JSONArray();
        for (int i = 0; i < plans.length(); i++) if (i != idx) { try { na.put(plans.get(i)); } catch (Throwable ignored) {} }
        plans = na; save(this, plans); scheduleAll(this); renderList();
    }

    // ---------- 调度: 每个时间点一颗精确闹钟, 杀后台也能响 ----------
    static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        JSONArray plans = load(c);
        // 先清掉旧闹钟(固定槽位)
        for (int slot = 0; slot < 400; slot++) {
            try { am.cancel(firePI(c, slot, -1, -1)); } catch (Throwable ignored) {}
        }
        Calendar now = Calendar.getInstance();
        for (int i = 0; i < plans.length(); i++) {
            JSONObject o = plans.optJSONObject(i);
            if (o == null) continue;
            JSONArray ts = o.optJSONArray("times");
            if (ts == null) continue;
            for (int t = 0; t < ts.length(); t++) {
                String tt = ts.optString(t);
                int hh, mm;
                try { hh = Integer.parseInt(tt.split(":")[0]); mm = Integer.parseInt(tt.split(":")[1]); }
                catch (Throwable e) { continue; }
                Calendar fire = Calendar.getInstance();
                fire.set(Calendar.HOUR_OF_DAY, hh);
                fire.set(Calendar.MINUTE, mm);
                fire.set(Calendar.SECOND, 0);
                fire.set(Calendar.MILLISECOND, 0);
                if (!fire.after(now)) fire.add(Calendar.DATE, 1); // 已过点->明天的这个点
                int slot = i * 20 + t;
                PendingIntent pi = firePI(c, slot, i, t);
                // setAlarmClock: 系统闹钟语义, 后台/省电/勿扰都保证触发(状态栏显示闹钟图标)
                Intent show = new Intent(c, MedPlanActivity.class);
                PendingIntent showPI = PendingIntent.getActivity(c, 990300 + slot, show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(fire.getTimeInMillis(), showPI), pi);
            }
        }
    }

    /** 每分钟兜底闹钟: 进程被冻结也能被唤醒补挂提醒 */
    static void armTick(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent it = new Intent(c, MedPlanReceiver.class).setAction("MED_TICK");
            PendingIntent pi = PendingIntent.getBroadcast(c, 990501, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000, pi);
        } catch (Throwable e) {
            try {
                AlarmManager am2 = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                Intent it2 = new Intent(c, MedPlanReceiver.class).setAction("MED_TICK");
                PendingIntent pi2 = PendingIntent.getBroadcast(c, 990501, it2,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am2.setWindow(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000, 30000, pi2);
            } catch (Throwable ignored) {}
        }
    }

    private static PendingIntent firePI(Context c, int slot, int planIdx, int timeIdx) {
        Intent it = new Intent(c, MedPlanReceiver.class).setAction("MED_FIRE")
            .putExtra("i", planIdx).putExtra("t", timeIdx);
        return PendingIntent.getBroadcast(c, 990100 + slot, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static class MedPlanReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                context.getSharedPreferences("medplan", 0).edit().putString("alarm_last",
                    new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())).apply();
                if ("MED_TICK".equals(intent.getAction())) {
                    if (inWatchWindow(context)) MedWatchService.ensure(context);
                    checkAndNotifyDue(context);
                    armTick(context);
                    return;
                }
                ensureChannel(context);
                if (!"MED_FIRE".equals(intent.getAction())) return;
                int i = intent.getIntExtra("i", -1), t = intent.getIntExtra("t", -1);
                JSONArray plans = load(context);
                JSONObject o = plans.optJSONObject(i);
                if (o != null && t >= 0 && !medDone(context, i, t)) {
                    String txt = o.optString("name", "") + "  " + o.optString("dose", "1") + o.optString("unit", "片")
                        + (o.optString("relation", "").isEmpty() ? "" : "（" + o.optString("relation") + "）");
                    notifyMedHold(context, i, t, "该吃药啦", txt);
                }
                scheduleAll(context); // 排下一个时间点
            } catch (Throwable ignored) {}
        }
    }

    /** 创建通知渠道(启动即调, 保证系统设置可见可配) */
    public static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "用药提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.enableLights(true);
        ch.setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
            new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM).build());
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        NotificationChannel r2 = new NotificationChannel("remind", "日程提醒", NotificationManager.IMPORTANCE_HIGH);
        r2.enableVibration(true);
        nm.createNotificationChannel(r2);
    }

    static int medNotifId(Context c, int planIdx, int timeIdx) { return medKey(c, planIdx, timeIdx).hashCode(); }

    /** 到点提醒: 常驻通知, 只有确认才消失 */
    static void notifyMedHold(Context c, int planIdx, int timeIdx, String title, String text) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(c, MedPlanActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 990002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent conf = new Intent(c, MedConfirmReceiver.class)
            .setAction("MED_CONFIRM").putExtra("i", planIdx).putExtra("t", timeIdx);
        PendingIntent cpi = PendingIntent.getBroadcast(c, 990400 + medNotifId(c, planIdx, timeIdx), conf,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Intent fsI = new Intent(c, MedPlanActivity.class);
        PendingIntent fpi = PendingIntent.getActivity(c, 990600 + medNotifId(c, planIdx, timeIdx), fsI,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH)
            .setSmallIcon(c.getApplicationInfo().icon)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(fpi, true)
            .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setTimeoutAfter(10 * 60 * 1000L)
            .addAction(0, "✓ 已服用", cpi)
            .build();
        nm.notify(medNotifId(c, planIdx, timeIdx), n);
    }

    /** 确认服药: 标记+撤通知 */
    static void confirmMed(Context c, int planIdx, int timeIdx) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        c.getSharedPreferences("medplan", 0).edit()
            .putBoolean(medKey(c, planIdx, timeIdx), true).apply();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(medNotifId(c, planIdx, timeIdx));
    }

    /** app活着即兜底: 补挂所有到点未确认通知 */
    static void checkAndNotifyDue(Context c) {
        try {
            JSONArray plans = load(c);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            android.content.SharedPreferences pf = c.getSharedPreferences("medplan", 0);
            for (int i = 0; i < plans.length(); i++) {
                JSONObject o = plans.optJSONObject(i);
                if (o == null) continue;
                JSONArray ts = o.optJSONArray("times");
                if (ts == null) continue;
                for (int t = 0; t < ts.length(); t++) {
                    String tt = ts.optString(t);
                    int hh, mm;
                    try { hh = Integer.parseInt(tt.split(":")[0]); mm = Integer.parseInt(tt.split(":")[1]); }
                    catch (Throwable e) { continue; }
                    java.util.Calendar fc = java.util.Calendar.getInstance();
                    fc.set(java.util.Calendar.HOUR_OF_DAY, hh); fc.set(java.util.Calendar.MINUTE, mm);
                    long lateMs = System.currentTimeMillis() - fc.getTimeInMillis();
                    if (lateMs >= 0 && lateMs <= 30 * 60 * 1000L && !medDone(c, i, t)) { // 到点后30分钟内才显示, 错过不挂
                        String txt = o.optString("name", "") + "  " + o.optString("dose", "1") + o.optString("unit", "片")
                            + (o.optString("relation", "").isEmpty() ? "" : "（" + o.optString("relation") + "）");
                        notifyMedHold(c, i, t, "该吃药啦", txt);
                    } else if (lateMs > 30 * 60 * 1000L) {
                        NotificationManager nm2 = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                        nm2.cancel(medNotifId(c, i, t)); // 过期撤下
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 守护窗口: 提醒前1小时起, 直到该时间点确认后才停止(未确认一直需要守护) */
    static boolean inWatchWindow(Context c) {
        try {
            JSONArray plans = load(c);
            java.util.Calendar nowC = java.util.Calendar.getInstance();
            int nowM = nowC.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nowC.get(java.util.Calendar.MINUTE);
            for (int i = 0; i < plans.length(); i++) {
                JSONObject o = plans.optJSONObject(i);
                JSONArray ts = o == null ? null : o.optJSONArray("times");
                if (ts == null) continue;
                for (int t = 0; t < ts.length(); t++) {
                    String tt = ts.optString(t);
                    int hh, mm;
                    try { hh = Integer.parseInt(tt.split(":")[0]); mm = Integer.parseInt(tt.split(":")[1]); }
                    catch (Throwable e) { continue; }
                    int m = hh * 60 + mm;
                    int mStart = m - 60; // 前一天深夜的窗口可能跨零点
                    boolean due = nowM >= mStart;
                    if (nowM < 180 && m >= 1260) due = true; // 零点附近: 前晚22点后的窗口跨天
                    if (due && !medDone(c, i, t)) return true;
                }
            }
            return false;
        } catch (Throwable e) { return false; }
    }

    static String medKey(Context c, int planIdx, int timeIdx) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        JSONObject o = load(c).optJSONObject(planIdx);
        String id = o == null ? String.valueOf(planIdx) : o.optString("id", String.valueOf(planIdx));
        String tt = o == null ? String.valueOf(timeIdx) : o.optJSONArray("times").optString(timeIdx);
        return "done_" + today + "_" + id + "_" + tt;
    }

    private String dueCount() {
        try {
            String now = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
            int n = 0;
            for (int i = 0; i < plans.length(); i++) {
                JSONObject o = plans.optJSONObject(i);
                if (o == null) continue;
                JSONArray ts = o.optJSONArray("times");
                if (ts == null) continue;
                for (int t = 0; t < ts.length(); t++)
                    if (ts.optString(t).compareTo(now) <= 0 && !medDone(this, i, t)) n++;
            }
            return String.valueOf(n);
        } catch (Throwable e) { return "?"; }
    }

    static boolean medDone(Context c, int planIdx, int timeIdx) {
        return c.getSharedPreferences("medplan", 0).getBoolean(medKey(c, planIdx, timeIdx), false);
    }

    static void notifyMed(Context c, String title, String text) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(c, MedPlanActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 990002, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH)
            .setSmallIcon(c.getApplicationInfo().icon)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        nm.notify((int) System.currentTimeMillis(), n);
    }
}
