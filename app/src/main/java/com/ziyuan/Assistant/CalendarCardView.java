package com.ziyuan.Assistant;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ziyuan.Assistant.cal.ChinaDate;
import com.ziyuan.Assistant.cal.SolarTermsUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** 首页日历卡：月视图 + holiday-cn 节假日(休/班) + 每日签到 + 自定义提醒(到点通知)。扁平风格 */
public class CalendarCardView extends LinearLayout {

    private final Context ctx;
    private boolean dark;
    private int year, month; // month: 0-11
    private LinearLayout gridHost, remindList;
    private TextView monthLabel;
    private final Map<String, JSONObject> holidays = new HashMap<String, JSONObject>(); // date->obj
    private String holidayYearLoaded = "";

    private static final int ACCENT = 0xFF315CDE;
    private static final int RED = 0xFFFF6B6B;
    private static final int BLUE_W = 0xFF5B8DEF;

    public CalendarCardView(Context c) {
        super(c);
        ctx = c;
        Calendar cal = Calendar.getInstance();
        year = cal.get(Calendar.YEAR);
        month = cal.get(Calendar.MONTH);
        setOrientation(VERTICAL);
        build();
    }

    // ---------- 外观 ----------
    private int bgCard() { return dark ? 0xFF111418 : 0xFFFFFFFF; }
    private int fgMain() { return dark ? Color.WHITE : 0xFF1F2329; }
    private int fgSub() { return dark ? 0xFF9AA3AE : 0xFF8A94A6; }
    private int cellBg() { return dark ? 0xFF171B21 : 0xFFF2F6FF; }

    private GradientDrawable flatBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radiusDp));
        g.setColor(color);
        return g;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    public void setDark(boolean d) {
        dark = d;
        build();
    }

    private void build() {
        removeAllViews();

        // 标题行: 📅 日历 + 月份切换
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("📅 日历");
        title.setTextSize(17); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(fgMain());
        head.addView(title, new LayoutParams(0, -2, 1f));

        TextView prev = navBtn("‹");
        prev.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            month--; if (month < 0) { month = 11; year--; }
            loadHolidaysAndBuildGrid();
        }});
        head.addView(prev);
        monthLabel = new TextView(ctx);
        monthLabel.setTextSize(15); monthLabel.setTypeface(Typeface.DEFAULT_BOLD);
        monthLabel.setTextColor(fgMain());
        monthLabel.setPadding(dp(10), 0, dp(10), 0);
        head.addView(monthLabel);
        TextView next = navBtn("›");
        next.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            month++; if (month > 11) { month = 0; year++; }
            loadHolidaysAndBuildGrid();
        }});
        head.addView(next);
        LinearLayout calBox = new LinearLayout(ctx);
        calBox.setOrientation(VERTICAL);
        calBox.addView(head);

        // 星期表头
        LinearLayout week = new LinearLayout(ctx);
        week.setPadding(0, dp(10), 0, dp(4));
        String[] wd = {"日", "一", "二", "三", "四", "五", "六"};
        for (int i = 0; i < 7; i++) {
            TextView t = new TextView(ctx);
            t.setText(wd[i]); t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(i == 0 || i == 6 ? RED : fgSub());
            week.addView(t, new LayoutParams(0, -2, 1f));
        }
        calBox.addView(week);

        gridHost = new LinearLayout(ctx);
        gridHost.setOrientation(VERTICAL);
        android.widget.ScrollView gsv = new android.widget.ScrollView(ctx);
        gsv.setVerticalScrollBarEnabled(true);
        gsv.addView(gridHost, new LayoutParams(-1, -2));
        calBox.addView(frameWrap(gsv));
        addView(calBox);

        // 自定义签到区(可加多个, 不显示连签天数)
        addView(buildSignSection());
        // 药物提醒已移至首页「用药提醒」

        // 药物提醒区(不包白底)

        // 日程提醒区已按要求移除

        loadHolidaysAndBuildGrid();
        refreshReminders();
        try { CalendarCardView.scheduleSchedules(ctx); } catch (Throwable ignored) {}
        try { CalendarCardView.scheduleMedsAll(ctx); } catch (Throwable ignored) {}
    }

    private TextView navBtn(String s) {
        TextView t = new TextView(ctx);
        t.setText(s); t.setTextSize(16);
        t.setTextColor(fgMain());
        t.setGravity(Gravity.CENTER);
        t.setBackground(flatBg(cellBg(), 10));
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        return t;
    }

    // 当天提醒弹窗
    private void showDayReminders(final String date) {
        JSONArray arr = reminders();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = dp(18);
        box.setPadding(p, dp(10), p, 0);
        int cnt = 0;
        for (int i = 0; i < arr.length(); i++) {
            try {
                final JSONObject o = arr.getJSONObject(i);
                String oday = df.format(new Date(o.optLong("ts")));
                if (!date.equals(oday)) continue;
                cnt++;
                LinearLayout row = new LinearLayout(ctx);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(flatBg(cellBg(), 10));
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                LayoutParams rlp = new LayoutParams(-1, -2);
                rlp.setMargins(0, dp(6), 0, 0);
                TextView t = new TextView(ctx);
                String when = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(o.optLong("ts")));
                t.setText(when + "  " + o.optString("t"));
                t.setTextSize(13); t.setTextColor(fgMain());
                row.addView(t, new LayoutParams(0, -2, 1f));
                TextView ok = new TextView(ctx);
                ok.setText("确认"); ok.setTextSize(13);
                ok.setTextColor(Color.WHITE);
                ok.setBackground(flatBg(ACCENT, 8));
                ok.setPadding(dp(12), dp(6), dp(12), dp(6));
                final int idx = i;
                ok.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    JSONArray arr2 = reminders();
                    JSONArray out = new JSONArray();
                    try { for (int j = 0; j < arr2.length(); j++) if (j != idx) out.put(arr2.getJSONObject(j)); } catch (Throwable ignored) {}
                    saveReminders(out);
                    Toast.makeText(ctx, "已完成", Toast.LENGTH_SHORT).show();
                    buildGrid();
                }});
                row.addView(ok);
                box.addView(row, rlp);
            } catch (Throwable ignored) {}
        }
        if (cnt == 0) {
            TextView e = new TextView(ctx);
            e.setText("这一天没有提醒");
            e.setTextSize(13); e.setTextColor(fgSub());
            box.addView(e);
        }
        new AlertDialog.Builder(ctx).setTitle(date).setView(box)
            .setPositiveButton("关闭", null).show();
    }

    // ---------- 药物提醒 ----------







    /** 重启后/进页时: 从存储重排全部药物闹钟 */




    private LinearLayout frameWrap(android.view.View content) {
        LinearLayout f = new LinearLayout(ctx);
        f.setOrientation(VERTICAL);
        f.setBackground(flatBg(cellBg(), 14));
        int p2 = dp(12);
        f.setPadding(p2, p2, p2, p2);
        LayoutParams lp = new LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, 0);
        f.addView(content, new android.view.ViewGroup.LayoutParams(-1, -2));
        // 占位让margin生效
        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(VERTICAL);
        outer.addView(f, lp);
        return outer;
    }


    private String join(java.util.List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String n : list) sb.append(n).append("、");
        return sb.substring(0, sb.length() - 1);
    }



    /** 药物提醒功能已移除, 保留空实现兼容旧调用 */
    private static final String[] MED_SLOTS = {"早", "中", "晚"};
    private static final int[] MED_DEFAULT_MIN = {8 * 60, 12 * 60, 18 * 60 + 30};

    private int medMin(int slot) {
        try {
            JSONObject o = new JSONObject(ctx.getSharedPreferences("cal", 0).getString("meds_times", "{}"));
            return o.optInt(MED_SLOTS[slot], MED_DEFAULT_MIN[slot]);
        } catch (Throwable e) { return MED_DEFAULT_MIN[slot]; }
    }

    private void setMedMin(int slot, int minutes) {
        try {
            JSONObject o = new JSONObject(ctx.getSharedPreferences("cal", 0).getString("meds_times", "{}"));
            o.put(MED_SLOTS[slot], minutes);
            ctx.getSharedPreferences("cal", 0).edit().putString("meds_times", o.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private java.util.List<String>[] medArr() {
        java.util.List<String>[] out = new java.util.List[3];
        for (int i = 0; i < 3; i++) out[i] = new java.util.ArrayList<String>();
        try {
            JSONObject o = new JSONObject(ctx.getSharedPreferences("cal", 0).getString("meds", "{}"));
            for (int i = 0; i < 3; i++) {
                JSONArray a = o.optJSONArray(MED_SLOTS[i]);
                if (a != null) for (int j = 0; j < a.length(); j++) {
                    String n = a.optString(j); if (n.length() > 0) out[i].add(n);
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private void saveMeds(java.util.List<String>[] arr) {
        try {
            JSONObject o = new JSONObject();
            for (int i = 0; i < 3; i++) {
                JSONArray a = new JSONArray();
                for (String n : arr[i]) a.put(n);
                o.put(MED_SLOTS[i], a);
            }
            ctx.getSharedPreferences("cal", 0).edit().putString("meds", o.toString()).apply();
            scheduleMeds(ctx, arr);
        } catch (Throwable ignored) {}
    }

    public static void scheduleMeds(Context c, java.util.List<String>[] arr) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            for (int i = 0; i < 3; i++) {
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 46010 + i,
                    new Intent(c, ScheduleReceiver.class).putExtra("t", medText(arr, i)),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
                if (arr[i].isEmpty()) continue;
                Calendar t = Calendar.getInstance();
                int mm = medMinStatic(c, i);
                t.set(Calendar.HOUR_OF_DAY, mm / 60);
                t.set(Calendar.MINUTE, mm % 60);
                t.set(Calendar.SECOND, 0);
                if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DATE, 1);
                if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
                else
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            }
        } catch (Throwable ignored) {}
    }

    private static int medMinStatic(Context c, int slot) {
        try {
            JSONObject o = new JSONObject(c.getSharedPreferences("cal", 0).getString("meds_times", "{}"));
            return o.optInt(MED_SLOTS[slot], MED_DEFAULT_MIN[slot]);
        } catch (Throwable e) { return MED_DEFAULT_MIN[slot]; }
    }

    /** 重启后/进页时: 从存储重排全部药物闹钟 */
    public static void scheduleMedsAll(Context c) {
        try {
            JSONObject o = new JSONObject(c.getSharedPreferences("cal", 0).getString("meds", "{}"));
            java.util.List<String>[] arr = new java.util.List[3];
            for (int i = 0; i < 3; i++) {
                arr[i] = new java.util.ArrayList<String>();
                JSONArray a = o.optJSONArray(MED_SLOTS[i]);
                if (a != null) for (int j = 0; j < a.length(); j++) arr[i].add(a.optString(j));
            }
            scheduleMeds(c, arr);
        } catch (Throwable ignored) {}
    }

    private static String medText(java.util.List<String>[] arr, int i) {
        StringBuilder sb = new StringBuilder(MED_SLOTS[i] + "吃药: ");
        for (String n : arr[i]) sb.append(n).append("、");
        return sb.substring(0, sb.length() - 1);
    }

    private java.util.Set<String> medTaken(String day, int slot) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        try {
            JSONObject o = new JSONObject(ctx.getSharedPreferences("cal", 0).getString("meds_taken", "{}"));
            JSONObject d = o.optJSONObject(day);
            if (d != null) {
                JSONArray a = d.optJSONArray(MED_SLOTS[slot]);
                if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private void toggleTaken(int slot, String name) {
        try {
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            android.content.SharedPreferences sp = ctx.getSharedPreferences("cal", 0);
            JSONObject o = new JSONObject(sp.getString("meds_taken", "{}"));
            JSONObject d = o.optJSONObject(day); if (d == null) d = new JSONObject();
            JSONArray a = d.optJSONArray(MED_SLOTS[slot]); if (a == null) a = new JSONArray();
            java.util.List<String> l = new java.util.ArrayList<String>();
            for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
            if (l.contains(name)) l.remove(name); else l.add(name);
            JSONArray na = new JSONArray(); for (String x : l) na.put(x);
            d.put(MED_SLOTS[slot], na); o.put(day, d);
            sp.edit().putString("meds_taken", o.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private LinearLayout buildMedSection() {
        LinearLayout sec = new LinearLayout(ctx);
        sec.setOrientation(VERTICAL);
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(16), 0, dp(4));
String[] labels = {"早上", "中午", "晚上"};
        TextView ti = new TextView(ctx);
        ti.setText("💊 药物提醒(点时间可改, 点药名确认已吃)");
        // 时间行: 早上/中午/晚上 三个可点时间(时间在本区顶部统一改)
        LinearLayout times = new LinearLayout(ctx);
        times.setGravity(Gravity.CENTER_VERTICAL);
        times.setPadding(0, dp(8), 0, 0);
        for (int i = 0; i < 3; i++) {
            final int si = i;
            int m0 = medMin(i);
            TextView tt = new TextView(ctx);
            tt.setText(labels[i] + " " + String.format(Locale.US, "%02d:%02d", m0 / 60, m0 % 60));
            tt.setTextSize(13); tt.setTypeface(Typeface.DEFAULT_BOLD);
            tt.setTextColor(ACCENT);
            tt.setGravity(Gravity.CENTER);
            tt.setBackground(flatBg(dark ? 0xFF232A38 : 0xFFE8EEFF, 8));
            tt.setPadding(dp(10), dp(6), dp(10), dp(6));
            LayoutParams tlp = new LayoutParams(0, -2, 1f);
            if (i > 0) tlp.setMargins(dp(8), 0, 0, 0);
            tt.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                int cur = medMin(si);
                new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(TimePicker tp, int hh, int mm) {
                        setMedMin(si, hh * 60 + mm);
                        saveMeds(medArr());
                        rebuildMedsOnly();
                    }
                }, cur / 60, cur % 60, true).show();
            }});
            times.addView(tt, tlp);
        }
        TextView medMgr = new TextView(ctx);
        medMgr.setText("＋"); medMgr.setTextSize(15);
        medMgr.setTextColor(Color.WHITE);
        medMgr.setGravity(Gravity.CENTER);
        medMgr.setBackground(flatBg(ACCENT, 8));
        medMgr.setPadding(dp(10), dp(2), dp(10), dp(2));
        LayoutParams mlp = new LayoutParams(-2, -2);
        mlp.setMargins(dp(8), 0, 0, 0);
        medMgr.setOnClickListener(new OnClickListener() { public void onClick(View v) { showMedManager(); }});
        times.addView(medMgr, mlp);
        sec.addView(times);
        ti.setTextSize(14); ti.setTypeface(Typeface.DEFAULT_BOLD);
        ti.setTextColor(fgMain());
        head.addView(ti, new LayoutParams(0, -2, 1f));
        sec.addView(head);
        java.util.List<String>[] meds = medArr();
        for (int i = 0; i < 3; i++) {
            final int si = i;
            LinearLayout row = new LinearLayout(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(flatBg(dark ? 0xFF232A38 : 0xFFE8EEFF, 10));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LayoutParams rlp = new LayoutParams(-1, -2);
            rlp.setMargins(0, dp(6), 0, 0);
            TextView slot = new TextView(ctx);
            slot.setText(MED_SLOTS[i]); slot.setTextSize(14); slot.setTypeface(Typeface.DEFAULT_BOLD);
            slot.setTextColor(fgMain());
            row.addView(slot);
            // 药名: 每种一行, 黑色加大加粗, 点击确认吃没吃, 后跟✕删除
            LinearLayout drugBox = new LinearLayout(ctx);
            drugBox.setOrientation(VERTICAL);
            drugBox.setPadding(dp(12), 0, 0, 0);
            String tday = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            java.util.Set<String> taken = medTaken(tday, si);
            for (int j = 0; j < meds[i].size(); j++) {
                final int dj = j;
                String dn = meds[i].get(j);
                boolean ate = taken.contains(dn);
                LinearLayout drow = new LinearLayout(ctx);
                drow.setGravity(Gravity.CENTER_VERTICAL);
                TextView drug = new TextView(ctx);
                drug.setText((ate ? "✓ " : "") + dn);
                drug.setTextSize(16);
                drug.setTypeface(Typeface.DEFAULT_BOLD);
                drug.setTextColor(ate ? 0xFF9CCC65 : fgMain());
                drug.setPadding(dp(6), dp(4), dp(6), dp(4));
                drug.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    toggleTaken(si, dn);
                    rebuildMedsOnly();
                }});
                drow.addView(drug, new LayoutParams(0, -2, 1f));
                drugBox.addView(drow, new LayoutParams(-1, -2));
            }
            if (meds[i].isEmpty()) {
                TextView none = new TextView(ctx);
                none.setText("未添加"); none.setTextSize(13); none.setTextColor(fgSub());
                drugBox.addView(none);
            }
            row.addView(drugBox, new LayoutParams(0, -2, 1f));
            sec.addView(row);
        }
        return sec;
    }

    private void rebuildMedsOnly() {
        // 仅重建药物区: build()整体重建最简单
        build();
    }



    // ---------- 药物管理(时间行＋进入: 增删早/中/晚药物) ----------
    private void showMedManager() {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = dp(6);
        box.setPadding(p, p, p, p);
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { public void run() {
            box.removeAllViews();
            java.util.List<String>[] meds = medArr();
            for (int i = 0; i < 3; i++) {
                final int si = i;
                LinearLayout head = new LinearLayout(ctx);
                head.setGravity(Gravity.CENTER_VERTICAL);
                head.setPadding(0, dp(8), 0, dp(4));
                TextView slotT = new TextView(ctx);
                slotT.setText(MED_SLOTS[i]); slotT.setTextSize(14); slotT.setTypeface(Typeface.DEFAULT_BOLD);
                slotT.setTextColor(fgMain());
                head.addView(slotT, new LayoutParams(0, -2, 1f));
                TextView add = new TextView(ctx);
                add.setText("＋"); add.setTextSize(14);
                add.setTextColor(fgMain());
                add.setBackground(flatBg(cellBg(), 8));
                add.setPadding(dp(10), dp(2), dp(10), dp(2));
                add.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    android.widget.EditText et = new android.widget.EditText(ctx);
                    et.setHint("药物名称"); et.setSingleLine(true);
                    new AlertDialog.Builder(ctx).setTitle("添加" + MED_SLOTS[si] + "药物").setView(et)
                        .setPositiveButton("添加", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d2, int w) {
                                String n = et.getText().toString().trim();
                                if (n.isEmpty()) return;
                                java.util.List<String>[] a = medArr();
                                a[si].add(n);
                                saveMeds(a);
                                render[0].run();
                            }
                        }).setNegativeButton("取消", null).show();
                }});
                head.addView(add);
                box.addView(head);
                if (meds[i].isEmpty()) {
                    TextView none = new TextView(ctx);
                    none.setText("未添加"); none.setTextSize(13); none.setTextColor(fgSub());
                    box.addView(none);
                } else {
                    LinearLayout chips = new LinearLayout(ctx);
                    for (int j = 0; j < meds[i].size(); j++) {
                        final int dj = j;
                        TextView chip = new TextView(ctx);
                        chip.setText(meds[i].get(j) + " ✕");
                        chip.setTextSize(13);
                        chip.setTextColor(fgMain());
                        chip.setBackground(flatBg(cellBg(), 8));
                        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
                        LayoutParams clp = new LayoutParams(-2, -2);
                        clp.setMargins(0, 0, dp(6), dp(6));
                        chip.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                            java.util.List<String>[] a = medArr();
                            a[si].remove(dj);
                            saveMeds(a);
                            render[0].run();
                        }});
                        chips.addView(chip, clp);
                    }
                    box.addView(chips);
                }
            }
        }};
        render[0].run();
        new AlertDialog.Builder(ctx).setTitle("药物管理(点药删除)").setView(box)
            .setPositiveButton("完成", null)
            .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                public void onDismiss(android.content.DialogInterface d) { build(); }
            }).show();
    }

    // ---------- 签到管理(增删签到项) ----------
    private void showSignManager() {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = dp(6);
        box.setPadding(p, p, p, p);
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { public void run() {
            box.removeAllViews();
            JSONArray arr = signArr();
            LinearLayout chips = new LinearLayout(ctx);
            for (int k = 0; k < arr.length(); k++) {
                final int idx = k;
                JSONObject o = arr.optJSONObject(k);
                if (o == null) continue;
                TextView chip = new TextView(ctx);
                chip.setText(o.optString("n", "签到") + " ✕");
                chip.setTextSize(13);
                chip.setTextColor(fgMain());
                chip.setBackground(flatBg(cellBg(), 8));
                chip.setPadding(dp(10), dp(6), dp(10), dp(6));
                LayoutParams clp = new LayoutParams(-2, -2);
                clp.setMargins(0, 0, dp(6), dp(6));
                chip.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                    try {
                        JSONArray a = signArr();
                        JSONArray na = new JSONArray();
                        for (int m = 0; m < a.length(); m++) if (m != idx) na.put(a.get(m));
                        ctx.getSharedPreferences("cal", 0).edit().putString("signs", na.toString()).apply();
                    } catch (Throwable ignored) {}
                    render[0].run();
                }});
                chips.addView(chip, clp);
            }
            box.addView(chips);
        }};
        render[0].run();
        android.widget.EditText et = new android.widget.EditText(ctx);
        et.setHint("签到名称"); et.setSingleLine(true);
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(VERTICAL);
        wrap.addView(box);
        wrap.addView(et);
        new AlertDialog.Builder(ctx).setTitle("签到管理(点签到删除)")
            .setView(wrap)
            .setPositiveButton("添加", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d2, int w) {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    try {
                        JSONArray a = signArr();
                        JSONObject o = new JSONObject();
                        o.put("n", n); o.put("last", "");
                        a.put(o);
                        ctx.getSharedPreferences("cal", 0).edit().putString("signs", a.toString()).apply();
                    } catch (Throwable ignored) {}
                    render[0].run();
                }
            })
            .setNegativeButton("完成", null)
            .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                public void onDismiss(android.content.DialogInterface d) { build(); }
            }).show();
    }

    // ---------- holiday-cn ----------
    private void loadHolidaysAndBuildGrid() {
        monthLabel.setText(year + "年" + (month + 1) + "月");
        final String yKey = String.valueOf(year);
        if (holidayYearLoaded.equals(yKey)) { buildGrid(); return; }
        holidays.clear();
        buildGrid(); // 先画无节假日版
        new Thread(new Runnable() { public void run() {
            try {
                java.io.File f = new java.io.File(ctx.getFilesDir(), "holiday_" + yKey + ".json");
                String raw;
                if (f.exists() && f.length() > 50) {
                    raw = readFile(f);
                } else {
                    raw = fetch("https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/" + yKey + ".json");
                    if (raw == null || raw.length() < 50)
                        raw = fetch("https://gh-proxy.com/https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/" + yKey + ".json");
                    if (raw != null && raw.length() > 50) writeFile(f, raw);
                }
                if (raw != null && raw.length() > 50) {
                    JSONObject o = new JSONObject(raw);
                    JSONArray days = o.optJSONArray("days");
                    if (days != null) {
                        synchronized (holidays) {
                            for (int i = 0; i < days.length(); i++) {
                                JSONObject d = days.getJSONObject(i);
                                holidays.put(d.optString("date"), d);
                            }
                        }
                        holidayYearLoaded = yKey;
                        post(new Runnable() { public void run() { buildGrid(); }});
                    }
                }
            } catch (Throwable ignored) {}
        }}).start();
    }

    private String fetch(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096]; int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable e) { return null; }
    }

    private String readFile(java.io.File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096]; int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable e) { return null; }
    }

    private void writeFile(java.io.File f, String s) {
        try {
            java.io.FileOutputStream o = new java.io.FileOutputStream(f);
            o.write(s.getBytes("UTF-8")); o.close();
        } catch (Throwable ignored) {}
    }

    // ---------- 月网格 ----------
    private void buildGrid() {
        gridHost.removeAllViews();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.SUNDAY);   // 强制周日起算, 与表头对齐
        cal.set(year, month, 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0=周日
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar today = Calendar.getInstance();
        String todayStr = df.format(today.getTime());

        LinearLayout row = new LinearLayout(ctx);
        gridHost.addView(row, new LayoutParams(-1, -2));
        for (int b = 0; b < firstDow; b++) {   // 月首前置空白
            TextView pad = new TextView(ctx);
            pad.setTextSize(14);
            row.addView(pad, new LayoutParams(0, -2, 1f));
        }
        for (int day = 1; day <= maxDay; day++) {
            int pos = firstDow + day - 1;
            if (pos % 7 == 0) { row = new LinearLayout(ctx); gridHost.addView(row, new LayoutParams(-1, -2)); }
            cal.set(year, month, day);
            String ds = df.format(cal.getTime());
            JSONObject hol = holidays.get(ds);
            boolean isToday = ds.equals(todayStr);
            boolean weekend = pos % 7 == 0 || pos % 7 == 6;

            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(VERTICAL);
            cell.setGravity(Gravity.CENTER);
            LayoutParams clp = new LayoutParams(0, -2, 1f);
            clp.setMargins(dp(2), dp(2), dp(2), dp(2));
            cell.setBackground(flatBg(isToday ? ACCENT : cellBg(), 10));
            cell.setPadding(dp(2), dp(4), dp(2), dp(4));

            TextView num = new TextView(ctx);
            num.setText(String.valueOf(day));
            num.setTextSize(13); num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            if (isToday) num.setTextColor(Color.WHITE);
            else if (hol != null && hol.optBoolean("isOffDay")) num.setTextColor(RED);
            else if (hol != null) num.setTextColor(BLUE_W);
            else if (weekend) num.setTextColor(dark ? 0xFFFF9E9E : RED);
            else num.setTextColor(fgMain());
            cell.addView(num);
            final String fds = ds;
            cell.setOnClickListener(new OnClickListener() { public void onClick(View v) { showDayReminders(fds); }});

            String mark = null; int markColor = fgSub();
            if (hol != null) {
                mark = hol.optBoolean("isOffDay") ? "休" : "班";
                markColor = hol.optBoolean("isOffDay") ? RED : BLUE_W;
                if (isToday) markColor = Color.WHITE;
            } else if (isToday) {
                mark = "今"; markColor = Color.WHITE;
            } else {
                // 农历/节气/节日 副行 (NCalendar同款优先级: 节气/节日高亮, 农历日灰)
                try {
                    String lunar = ChinaDate.getChinaDay(year, month + 1, day);
                    if (lunar != null && lunar.trim().length() > 0) {
                        lunar = lunar.trim();
                        String term = SolarTermsUtil.getSolarTermName(year, month + 1, day);
                        long[] l = ChinaDate.calElement(year, month + 1, day);
                        String pure = ChinaDate.getChinaDate((int) l[2]);
                        if (pure.equals("初一")) pure = (l[1] == 12) ? "腊月" : ((0 != l[6]) ? ("闰" + (int) l[1] + "月") : ChinaDate.getChinaDate((int) l[2]));
                        mark = (lunar.length() > 3) ? lunar.substring(0, 3) : lunar;
                        if (term != null && term.trim().length() > 0) markColor = BLUE_W;
                        else if (!lunar.equals(pure)) markColor = RED;
                        else markColor = fgSub();
                    }
                } catch (Throwable ignored) {}
            }
            if (mark != null) {
                TextView mk = new TextView(ctx);
                mk.setText(mark); mk.setTextSize(9);
                mk.setTextColor(markColor);
                mk.setGravity(Gravity.CENTER);
                cell.addView(mk);
            } else {
                TextView mk = new TextView(ctx);
                mk.setText(" "); mk.setTextSize(9);
                cell.addView(mk);
            }
            row.addView(cell, clp);
        }
        // 补齐末行
        if (row != null) {
            int rest = (firstDow + maxDay) % 7;
            for (int i = rest; i < 7 && i > 0; i++) {
                TextView pad = new TextView(ctx);
                row.addView(pad, new LayoutParams(0, -2, 1f));
            }
        }
    }

    // ---------- 签到 ----------
    // ---------- 药物管理(时间行＋进入: 增删早/中/晚药物) ----------

    // ---------- 签到管理(增删签到项) ----------
    // ---------- 自定义签到 ----------
    private JSONArray signArr() {
        try { return new JSONArray(ctx.getSharedPreferences("cal", 0).getString("signs", "[{\"n\":\"每日签到\"}]")); }
        catch (Throwable e) { return new JSONArray(); }
    }

    private LinearLayout buildSignSection() {
        LinearLayout sec = new LinearLayout(ctx);
        sec.setOrientation(VERTICAL);
        sec.setPadding(0, dp(14), 0, 0);
        LinearLayout head = new LinearLayout(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView ti = new TextView(ctx);
        ti.setText("✅ 签到"); ti.setTextSize(15); ti.setTypeface(Typeface.DEFAULT_BOLD);
        ti.setTextColor(fgMain());
        head.addView(ti, new LayoutParams(0, -2, 1f));
        TextView addBtn = new TextView(ctx);
        addBtn.setText("＋ 新建");
        addBtn.setTextSize(13); addBtn.setTextColor(Color.WHITE);
        addBtn.setBackground(flatBg(ACCENT, 12));
        addBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        addBtn.setOnClickListener(new OnClickListener() { public void onClick(View v) { showSignManager(); }});
        head.addView(addBtn);
        sec.addView(head);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        JSONArray arr = signArr();
        for (int k = 0; k < arr.length(); k++) {
            final int idx = k;
            JSONObject o = arr.optJSONObject(k);
            if (o == null) continue;
            final String name = o.optString("n", "签到");
            boolean signed = today.equals(o.optString("last", ""));
            LinearLayout row = new LinearLayout(ctx);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams rlp = new LayoutParams(-1, -2);
            rlp.setMargins(0, dp(8), 0, 0);
            TextView nm = new TextView(ctx);
            nm.setText(name); nm.setTextSize(14);
            nm.setTextColor(signed ? fgSub() : fgMain());
            row.addView(nm, new LayoutParams(0, -2, 1f));
            TextView btn = new TextView(ctx);
            btn.setText(signed ? "已签" : "签到");
            btn.setTextSize(13); btn.setTypeface(Typeface.DEFAULT_BOLD);
            btn.setPadding(dp(16), dp(7), dp(16), dp(7));
            if (signed) { btn.setTextColor(fgSub()); btn.setBackground(flatBg(cellBg(), 12)); }
            else { btn.setTextColor(Color.WHITE); btn.setBackground(flatBg(ACCENT, 12)); }
            btn.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                try {
                    JSONArray a = signArr();
                    JSONObject oo = a.optJSONObject(idx);
                    if (oo == null) return;
                    String t2 = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                    oo.put("last", t2.equals(oo.optString("last", "")) ? "" : t2);
                    ctx.getSharedPreferences("cal", 0).edit().putString("signs", a.toString()).apply();
                } catch (Throwable ignored) {}
                build();
            }});
            row.addView(btn);
            sec.addView(row, rlp);
        }
        return sec;
    }

    // ---------- 提醒 ----------
    private JSONArray reminders() { return schedulesArr(); }

    private JSONArray schedulesArr() {
        try { return new JSONArray(ctx.getSharedPreferences("cal", 0).getString("schedules", "[]")); }
        catch (Throwable e) { return new JSONArray(); }
    }

    private void saveReminders(JSONArray arr) {
        ctx.getSharedPreferences("cal", 0).edit().putString("schedules", arr.toString()).apply();
        try { scheduleSchedules(ctx); } catch (Throwable ignored) {}
    }

    /** 安排最近日程的后台通知 */
    public static void scheduleSchedules(Context c) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(c.getSharedPreferences("cal", 0).getString("schedules", "[]"));
            long best = Long.MAX_VALUE; String title = null;
            for (int i = 0; i < arr.length(); i++) {
                long ts = arr.getJSONObject(i).optLong("ts");
                if (ts > System.currentTimeMillis() && ts < best) { best = ts; title = arr.getJSONObject(i).optString("t"); }
            }
            android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 46002,
                new Intent(c, ScheduleReceiver.class).putExtra("t", title == null ? "" : title),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
            if (best != Long.MAX_VALUE) {
                if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, best, pi);
                else
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, best, pi);
            }
        } catch (Throwable ignored) {}
    }

    private void refreshReminders() {
        if (remindList == null) return; // 日程区已移除
 /* 日程区已移除 */ }

    private void showAddReminder() {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(VERTICAL);
        int p = dp(20);
        box.setPadding(p, p, p, 0);
        final EditText et = new EditText(ctx);
        et.setHint("提醒内容");
        et.setSingleLine(true);
        box.addView(et);
        final long[] chosen = { System.currentTimeMillis() + 3600_000L };
        final TextView when = new TextView(ctx);
        refreshWhen(when, chosen[0]);
        when.setTextSize(14);
        when.setTextColor(dark ? 0xFFB4C5FF : 0xFF315CDE);
        when.setPadding(0, dp(14), 0, dp(4));
        box.addView(when);
        final boolean[] daily = { false };
        final TextView dailyT = new TextView(ctx);
        dailyT.setText("☐ 每天重复");
        dailyT.setTextSize(14); dailyT.setTextColor(fgMain());
        dailyT.setPadding(0, dp(8), 0, dp(8));
        dailyT.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            daily[0] = !daily[0];
            dailyT.setText(daily[0] ? "☑ 每天重复" : "☐ 每天重复");
        }});
        box.addView(dailyT);
        when.setOnClickListener(new OnClickListener() { public void onClick(View v) {
            Calendar c0 = Calendar.getInstance(); c0.setTimeInMillis(chosen[0]);
            new DatePickerDialog(ctx, new DatePickerDialog.OnDateSetListener() {
                public void onDateSet(DatePicker dp2, int y2, int m2, int d2) {
                    Calendar c1 = Calendar.getInstance(); c1.setTimeInMillis(chosen[0]);
                    c1.set(y2, m2, d2);
                    chosen[0] = c1.getTimeInMillis();
                    new TimePickerDialog(ctx, new TimePickerDialog.OnTimeSetListener() {
                        public void onTimeSet(TimePicker tp, int h2, int mi2) {
                            Calendar c2 = Calendar.getInstance(); c2.setTimeInMillis(chosen[0]);
                            c2.set(Calendar.HOUR_OF_DAY, h2); c2.set(Calendar.MINUTE, mi2);
                            chosen[0] = c2.getTimeInMillis();
                            refreshWhen(when, chosen[0]);
                        }
                    }, c1.get(Calendar.HOUR_OF_DAY), c1.get(Calendar.MINUTE), true).show();
                }
            }, c0.get(Calendar.YEAR), c0.get(Calendar.MONTH), c0.get(Calendar.DAY_OF_MONTH)).show();
        }});

        new AlertDialog.Builder(ctx)
            .setTitle("新建日程")
            .setView(box)
            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface dlg, int w) {
                    String t = et.getText().toString().trim();
                    if (t.length() == 0) { Toast.makeText(ctx, "内容为空", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        JSONArray arr = reminders();
                        JSONObject o = new JSONObject();
                        o.put("t", t); o.put("ts", chosen[0]); o.put("daily", daily[0]);
                        arr.put(o);
                        saveReminders(arr);
                        refreshReminders();
                    } catch (Throwable ignored) {}
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void refreshWhen(TextView tv, long ts) {
        tv.setText("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ts)) + " (点击修改)");
    }

    // ---------- 到点触发(每30s由首页调用) ----------

}
