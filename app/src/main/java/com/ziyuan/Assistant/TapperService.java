package com.ziyuan.Assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Random;

/** 自动连点器: 无障碍 dispatchGesture, 无需 root; 配置存 SharedPreferences 与页面共享 */
public class TapperService extends AccessibilityService {

    private static volatile TapperService sInst;
    public static boolean ready() { return sInst != null; }
    public static boolean isRunning() { return sInst != null && sInst.running; }

    public static void openController() {
        if (sInst != null) sInst.showPanel();
    }

    public static void closeController() {
        if (sInst != null) { sInst.stopRun(); sInst.removeAll(); }
    }

    public static void startRun() {
        if (sInst != null) sInst.beginRun();
    }

    public static void stopRunStatic() {
        if (sInst != null) sInst.stopRun();
    }

    public static void showScriptLib() {
        if (sInst != null) sInst.showLib();
    }

    private WindowManager wm;
    private Handler handler;
    private View ball, panel, capture, recView;
    private android.widget.ImageView ivStart;
    private TextView stText;
    private ArrayList<int[]> points = new ArrayList<int[]>();
    private boolean running = false;
    private int runIdx = 0, tapCount = 0, targetCount = 0;

    // ---- 录制脚本(点击+滑动) ----
    private boolean recOn = false;
    private JSONArray recSteps = new JSONArray();
    private long recDownT = 0, recLastEnd = 0;
    private float recDnX, recDnY, recLX, recLY;
    private boolean recMoved = false;
    private JSONArray playSteps = new JSONArray();
    private int playIdx = 0, playRounds = 0;

    private final Runnable playTick = new Runnable() {
        public void run() {
            if (!running || playSteps.length() == 0) return;
            // 次数上限按脚本总执行步数算，到点即停
            if (targetCount > 0 && tapCount >= targetCount) { stopRun(); return; }
            if (playIdx >= playSteps.length()) playIdx = 0;
            JSONObject s = playSteps.optJSONObject(playIdx);
            if (s == null) { playIdx++; handler.postDelayed(playTick, 50); return; }
            final long delay = Math.max(0, s.optLong("d", 300));
            final String act = s.optString("a", "tap");
            handler.postDelayed(new Runnable() {
                public void run() {
                    if (!running) return;
                    if ("swipe".equals(act)) {
                        swipe(s.optInt("x"), s.optInt("y"), s.optInt("x2"), s.optInt("y2"), s.optLong("ms", 300));
                    } else {
                        tap(s.optInt("x"), s.optInt("y"));
                    }
                    tapCount++; playIdx++;
                    handler.postDelayed(playTick, 60);
                    refresh();
                }
            }, delay);
        }
    };

    private final Runnable runTick = new Runnable() {
        public void run() {
            if (!running || points.isEmpty()) return;
            if (targetCount > 0 && tapCount >= targetCount) { stopRun(); return; }
            if (runIdx >= points.size()) runIdx = 0;
            int[] pt = points.get(runIdx);
            tap(pt[0], pt[1]);
            tapCount++;
            runIdx++;
            handler.postDelayed(this, Math.max(50, baseInterval() + rand.nextInt(Math.max(1, jitter() + 1))));
        }
    };
    private final Random rand = new Random();

    private android.content.SharedPreferences cfg() {
        return getSharedPreferences("tapper", MODE_PRIVATE);
    }

    private int baseInterval() {
        int v = cfg().getInt("interval", 400);
        return v < 50 ? 50 : v;
    }

    private int jitter() {
        return Math.max(0, cfg().getInt("jitter", 0));
    }

    private int countCfg() {
        return Math.max(0, cfg().getInt("count", 0));
    }

    private ArrayList<int[]> loadPoints() {
        ArrayList<int[]> out = new ArrayList<int[]>();
        try {
            JSONArray a = new JSONArray(cfg().getString("points", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.getJSONArray(i);
                out.add(new int[]{p.getInt(0), p.getInt(1)});
            }
        } catch (Exception ignore) {}
        return out;
    }

    private void savePoints(ArrayList<int[]> pts) {
        try {
            JSONArray a = new JSONArray();
            for (int[] p : pts) a.put(new JSONArray().put(p[0]).put(p[1]));
            cfg().edit().putString("points", a.toString()).apply();
        } catch (Exception ignore) {}
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        sInst = this;
        // 修复崩溃: 不在连接时自动弹窗, 由页面「3.打开悬浮控制器」触发
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override
    public void onInterrupt() {}
    @Override
    public boolean onUnbind(android.content.Intent i) { stopRun(); removeAll(); sInst = null; return super.onUnbind(i); }
    @Override
    public void onDestroy() { stopRun(); removeAll(); sInst = null; super.onDestroy(); }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (capture != null && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            endCapture();
            return true;
        }
        return super.onKeyEvent(event);
    }

    private File libDir() {
        File d = new File(getFilesDir(), "tapper");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void removeAll() {
        try { if (ball != null) { wm.removeView(ball); ball = null; } } catch (Exception ignore) {}
        try { if (panel != null) { wm.removeView(panel); panel = null; } } catch (Exception ignore) {}
        try { if (capture != null) { wm.removeView(capture); capture = null; } } catch (Exception ignore) {}
        try { if (recView != null) { wm.removeView(recView); recView = null; } } catch (Exception ignore) {}
        for (View v : markers) {
            try { wm.removeView(v); } catch (Throwable ignore) {}
        }
        markers.clear();
        try { if (floatView != null) { wm.removeView(floatView); floatView = null; } } catch (Exception ignore) {}
        try { if (lineImg != null) { wm.removeView(lineImg); lineImg = null; } } catch (Exception ignore) {}
        if (lineBmp != null) { lineBmp.recycle(); lineBmp = null; }
    }

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Button btn(String text, int color, int txtColor, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(txtColor);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setPadding(0, dip(10), 0, dip(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(16));
        bg.setColor(color);
        b.setBackground(bg);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    // ---- 悬浮球 (仿原版: 侧边小球, 点开控制面板) ----
    private void showBall() {
        removeAll();
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // 圆形悬浮球：背景就是应用图标，可拖动，点一下展开面板
            final android.widget.ImageView b = new android.widget.ImageView(this);
            b.setImageResource(getApplicationInfo().icon);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xFFFFFFFF);
            b.setBackground(bg);
            b.setClipToOutline(true);
            b.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            b.setPadding(dip(2), dip(2), dip(2), dip(2));
            final int size = dip(52);
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    size, size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = dip(4);
            lp.y = dip(160);
            b.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY; int startX, startY; boolean dragged;
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = e.getRawX(); downY = e.getRawY();
                            startX = lp.x; startY = lp.y; dragged = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                            if (Math.abs(dx) > dip(6) || Math.abs(dy) > dip(6)) dragged = true;
                            if (dragged) {
                                lp.x = startX + (int) dx;
                                lp.y = startY + (int) dy;
                                try { wm.updateViewLayout(v, lp); } catch (Exception ignore) {}
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!dragged) {
                                try { wm.removeView(ball); } catch (Exception ignore) {}
                                ball = null;
                                showPanel();
                            }
                            return true;
                    }
                    return false;
                }
            });
            wm.addView(b, lp);
            ball = b;
            Toast.makeText(this, "悬浮控制器已打开", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPanel() {
        if (panel != null) return;
        loadOps();
        rebuildMarkers();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(5), dip(3), dip(5), dip(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(18));
        bg.setColor(0x00000000); // 全透明底
        box.setBackground(bg);

        // 顶部拖动把手(仿原版 drag handle)
        TextView drag = new TextView(this);
        drag.setText("☰");
        drag.setTextColor(0xFFAAAAAA);
        drag.setTextSize(14);
        drag.setGravity(Gravity.CENTER);
        drag.setPadding(0, dip(2), 0, dip(2));
        box.addView(drag, new LinearLayout.LayoutParams(-1, dip(20)));

        // 竖排图标列(1:1 原版7键): ▶/⏸ ＋ － ⚙ 👁 ✥ ✕
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        View.OnClickListener[] acts = {
            new View.OnClickListener() { public void onClick(View v) { if (running) stopRun(); else beginRun(); } },
            new View.OnClickListener() { public void onClick(View v) { showAddMenu(); } },
            new View.OnClickListener() { public void onClick(View v) { removeLastOp(); } },
            new View.OnClickListener() { public void onClick(View v) { showGlobalSettings(); } },
            new View.OnClickListener() { public void onClick(View v) {
                try { wm.removeView(panel); } catch (Exception ignore) {}
                panel = null;
            } },
            new View.OnClickListener() { public void onClick(View v) { Toast.makeText(TapperService.this, "按住面板空白处拖动", Toast.LENGTH_SHORT).show(); } },
            new View.OnClickListener() { public void onClick(View v) { stopRun(); removeAll(); } }
        };
        int[] drawables = {
            R.drawable.c_outline_play_circle_filled_white_black_48dp,
            R.drawable.c_outline_add_circle_outline_black_48dp,
            R.drawable.c_outline_remove_circle_outline_black_48dp,
            R.drawable.c_outline_settings_black_48dp,
            R.drawable.c_outline_visibility_off_black_48dp,
            R.drawable.c_outline_drag_handle_black_48dp,
            R.drawable.c_outline_close_black_48dp
        };
        for (int i = 0; i < drawables.length; i++) {
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dip(30), dip(30));
            if (i > 0) clp.topMargin = dip(5);
            android.widget.ImageView im = new android.widget.ImageView(this);
            im.setImageResource(drawables[i]);
            im.setAlpha(0.8f);
            im.setOnClickListener(acts[i]);
            if (i == 0) ivStart = im;
            col.addView(im, clp);
        }
        box.addView(col);
        // 去掉底部状态文字(仅保留在需要时以Toast提示)
        stText = null;

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dip(4);
        lp.y = dip(160);
        // 面板可拖动(按住把手或空白区)
        box.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; boolean drag2;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX() - lp.x; dy = e.getRawY() - lp.y; drag2 = true; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (drag2) {
                            lp.x = (int) (e.getRawX() - dx);
                            lp.y = (int) (e.getRawY() - dy);
                            try { wm.updateViewLayout(v, lp); } catch (Exception ignore) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: drag2 = false; return true;
                }
                return false;
            }
        });
        try {
            wm.addView(box, lp);
            panel = box;
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView roundIco(String txt, int color, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        t.setBackground(g);
        t.setOnClickListener(l);
        return t;
    }

    private void refresh() {
        if (panel == null || stText == null) return;
        int clicks = 0, swipes = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject o = ops.optJSONObject(i);
            if (o == null) continue;
            if ("s".equals(o.optString("t"))) swipes++; else clicks++;
        }
        stText.setText((running ? "▶ 执行中 " + tapCount + " 步" : "■ 已停止")
                + "\n点击" + clicks + " · 滑动" + swipes
                + (countCfg() > 0 ? " · 上限" + countCfg() + "步" : " · 无限")
                + (recView != null ? " · 录制中" : ""));
    }

    // ================= ops 模型(1:1 仿原版多操作脚本) =================
    private JSONArray ops = new JSONArray();
    private final ArrayList<View> markers = new ArrayList<View>();
    private final ArrayList<View> lineViews = new ArrayList<View>();

    private void invalidateLines() {
        // 保险起见双重刷新: invalidate + 整窗重建(部分ROM对透明overlay的invalidate不可靠)
        for (View v : lineViews) { v.invalidate(); v.postInvalidate(); }
        redrawLines();
    }

    /** 移除并按当前 ops 重建所有滑动连线窗口 */
    private void redrawLines() {
        for (View v : lineViews) {
            try { wm.removeView(v); } catch (Throwable ignore) {}
        }
        lineViews.clear();
    }

    private void loadOps() {
        try { ops = new JSONArray(cfg().getString("ops", "[]")); } catch (Throwable e) { ops = new JSONArray(); }
    }

    private void saveOps() {
        cfg().edit().putString("ops", ops.toString()).apply();
    }

    /** ＋: 选择要添加的功能(1:1 仿原版弹窗) */
    private void showAddMenu() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(24), dip(18), dip(24), dip(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(14));
        bg.setColor(0xFFFFFFFF);
        box.setBackground(bg);
        TextView t = new TextView(this);
        t.setText("选择要添加的功能");
        t.setTextSize(16);
        t.setTextColor(0xFF222222);
        t.setPadding(0, 0, 0, dip(8));
        box.addView(t);
        final View root = box;
        String[] items = {"点击", "滑动", "双击", "长按", "长按10秒", "主页键", "返回键", "打开通知栏"};
        for (final String it : items) {
            TextView r = new TextView(this);
            r.setText(it);
            r.setTextSize(17);
            r.setTextColor(0xFF222222);
            r.setPadding(dip(4), dip(12), dip(4), dip(12));
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                try { wm.removeView(root); } catch (Throwable ignore) {}
                int cx = dip(120), cy = dip(300);
                if ("点击".equals(it)) addOp("c", cx, cy);
                else if ("滑动".equals(it)) addOp("s", cx, cy);
                else if ("双击".equals(it)) addOp("c", cx, cy, 2, 120, 40);
                else if ("长按".equals(it)) addOp("c", cx, cy, 1, 400, 800);
                else if ("长按10秒".equals(it)) addOp("c", cx, cy, 1, 400, 10000);
                else if ("主页键".equals(it)) addOp("home", cx, cy, 1, 0, 0);
                else if ("返回键".equals(it)) addOp("back", cx, cy, 1, 0, 0);
                else if ("打开通知栏".equals(it)) addOp("notif", cx, cy, 1, 0, 0);
            }});
            box.addView(r);
        }
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(0xFF00897B);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.END);
        cancel.setPadding(0, dip(8), dip(4), dip(4));
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { wm.removeView(root); } catch (Throwable ignore) {}
        }});
        box.addView(cancel);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dip(260), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(box, lp); } catch (Throwable ignore) {}
    }

    private void addOp(String t, int x, int y) {
        addOp(t, x, y, 1, cfg().getInt("interval", 400), "s".equals(t) ? 400 : cfg().getInt("touchMs", 40));
    }

    private void addOp(String t, int x, int y, int reps, int period, int touch) {
        loadOps();
        try {
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("x", x); o.put("y", y);
            if ("s".equals(t)) { o.put("x2", x); o.put("y2", y + dip(160)); o.put("d", 400); }
            o.put("p", period);
            o.put("touch", touch);
            o.put("r", reps);
            o.put("w", 0);
            o.put("o", cfg().getInt("offset", 0));
            ops.put(o);
            saveOps();
        } catch (Throwable ignore) {}
        rebuildMarkers();
        refresh();
    }

    private void removeLastOp() {
        loadOps();
        if (ops.length() == 0) return;
        JSONArray ns = new JSONArray();
        for (int i = 0; i < ops.length() - 1; i++) ns.put(ops.opt(i));
        ops = ns;
        saveOps();
        rebuildMarkers();
        refresh();
        Toast.makeText(this, "已删除最后一个操作", Toast.LENGTH_SHORT).show();
    }

    private View floatView;
    private android.widget.ImageView lineImg;
    private android.graphics.Bitmap lineBmp;
    private long lastLineDraw;

    /** 连线: 全屏不可触摸 ImageView, 用 Bitmap 刷新(必重绘) */
    private void redrawLineBitmap() {
        if (lineImg == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastLineDraw < 16) return; // 60fps 节流
        lastLineDraw = now;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        if (lineBmp == null || lineBmp.getWidth() != w || lineBmp.getHeight() != h) {
            lineBmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        }
        lineBmp.eraseColor(0);
        Canvas c = new Canvas(lineBmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dip(3));
        p.setColor(0xCC9B59B6);
        for (int i = 0; i < ops.length(); i++) {
            JSONObject o = ops.optJSONObject(i);
            if (o == null || !"s".equals(o.optString("t"))) continue;
            c.drawLine(o.optInt("x"), o.optInt("y"), o.optInt("x2"), o.optInt("y2"), p);
        }
        lineImg.setImageBitmap(lineBmp);
        lineImg.invalidate(); // 同一 Bitmap 引用, setImageBitmap 可能跳过重绘, 必须显式刷
    }

    private void rebuildMarkers() {
        for (View v : markers) {
            try { wm.removeView(v); } catch (Throwable ignore) {}
        }
        markers.clear();
        if (floatView != null) {
            try { wm.removeView(floatView); } catch (Throwable ignore) {}
            floatView = null;
        }
        loadOps();
        // 连线层: 全屏透明, 只画线, 不挡触摸
        if (lineImg == null && wm != null) {
            lineImg = new android.widget.ImageView(this);
            WindowManager.LayoutParams llp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            try { wm.addView(lineImg, llp); } catch (Throwable ignore) { lineImg = null; }
        }
        redrawLineBitmap();
        for (int i = 0; i < ops.length(); i++) {
            final int idx = i;
            JSONObject o = ops.optJSONObject(i);
            if (o == null) continue;
            final boolean swipe = "s".equals(o.optString("t"));
            android.widget.FrameLayout mwrap = new android.widget.FrameLayout(this);
            android.widget.ImageView tim = new android.widget.ImageView(this);
            tim.setImageResource(R.drawable.target);
            if (swipe) tim.setColorFilter(0xFF9B59B6);
            tim.setAlpha(0.85f);
            mwrap.addView(tim, new android.widget.FrameLayout.LayoutParams(-1, -1));
            // 中心显示第几步 (仿原版)
            TextView tnum = new TextView(this);
            tnum.setText(String.valueOf(i + 1));
            tnum.setTextColor(0xFF333333);
            tnum.setTextSize(12);
            tnum.setTypeface(null, android.graphics.Typeface.BOLD);
            tnum.setGravity(Gravity.CENTER);
            android.widget.FrameLayout.LayoutParams nlp = new android.widget.FrameLayout.LayoutParams(-2, -2);
            nlp.gravity = Gravity.CENTER;
            mwrap.addView(tnum, nlp);
            WindowManager.LayoutParams mlp = new WindowManager.LayoutParams(dip(34), dip(34),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            mlp.gravity = Gravity.TOP | Gravity.START;
            mlp.x = o.optInt("x") - dip(17);
            mlp.y = o.optInt("y") - dip(17);
            mwrap.setOnTouchListener(new View.OnTouchListener() {
                // 快照式: 按下时记手指起点与数据起点, 移动全程按位移直接算
                float rx0, ry0; int bx, by, bx2, by2; boolean moved;
                public boolean onTouch(View v, MotionEvent e) {
                    WindowManager.LayoutParams lp;
                    try { lp = (WindowManager.LayoutParams) v.getLayoutParams(); }
                    catch (ClassCastException cce) { return false; }
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            rx0 = e.getRawX(); ry0 = e.getRawY(); moved = false;
                            JSONObject d0 = ops.optJSONObject(idx);
                            if (d0 != null) { bx = d0.optInt("x"); by = d0.optInt("y"); bx2 = d0.optInt("x2"); by2 = d0.optInt("y2"); }
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float mx = e.getRawX() - rx0, my = e.getRawY() - ry0;
                            if (Math.abs(mx) > dip(4) || Math.abs(my) > dip(4)) moved = true;
                            JSONObject oo = ops.optJSONObject(idx);
                            if (oo != null) {
                                int nx = bx + (int) mx, ny = by + (int) my;
                                lp.x = nx - dip(17); lp.y = ny - dip(17);
                                try { wm.updateViewLayout(v, lp); } catch (Throwable ignore) {}
                                try {
                                    if (swipe) { oo.put("x2", bx2 + (int) mx); oo.put("y2", by2 + (int) my); }
                                    oo.put("x", nx); oo.put("y", ny);
                                } catch (Throwable ignore) {}
                                lastLineDraw = 0;
                                redrawLineBitmap();
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (moved) {
                                saveOps();
                                lastLineDraw = 0; redrawLineBitmap();
                                rebuildMarkers(); // 松手重建, 窗口与数据强制对齐
                            } else showOpEdit(idx);
                            return true;
                    }
                    return false;
                }
            });
            try { wm.addView(mwrap, mlp); markers.add(mwrap); } catch (Throwable ignore) {}
            if (swipe) {
                android.widget.ImageView e2 = new android.widget.ImageView(this);
                e2.setImageResource(R.drawable.swipe_64);
                e2.setColorFilter(0xFF9B59B6);
                e2.setAlpha(0.85f);
                // 箭头方向随滑动走向旋转
                float ang = (float) Math.toDegrees(Math.atan2(o.optInt("y2") - o.optInt("y"), o.optInt("x2") - o.optInt("x")));
                e2.setRotation(ang);
                WindowManager.LayoutParams elp = new WindowManager.LayoutParams(dip(24), dip(24),
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
                elp.gravity = Gravity.TOP | Gravity.START;
                elp.x = o.optInt("x2") - dip(12);
                elp.y = o.optInt("y2") - dip(12);
                final int eidx = i;
                e2.setOnTouchListener(new View.OnTouchListener() {
                    float dx, dy;
                    public boolean onTouch(View v, MotionEvent e) {
                        WindowManager.LayoutParams lp;
                    try { lp = (WindowManager.LayoutParams) v.getLayoutParams(); }
                    catch (ClassCastException cce) { return false; }
                        switch (e.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                dx = e.getRawX() - lp.x; dy = e.getRawY() - lp.y; return true;
                            case MotionEvent.ACTION_MOVE:
                                lp.x = (int) (e.getRawX() - dx); lp.y = (int) (e.getRawY() - dy);
                                try { wm.updateViewLayout(v, lp); } catch (Throwable ignore) {}
                                JSONObject oo = ops.optJSONObject(eidx);
                                if (oo != null) {
                                    try { oo.put("x2", lp.x + dip(12)); oo.put("y2", lp.y + dip(12)); } catch (Throwable ignore) {}
                                    JSONObject st = ops.optJSONObject(eidx);
                                    if (st != null) v.setRotation((float) Math.toDegrees(Math.atan2(lp.y + dip(12) - st.optInt("y"), lp.x + dip(12) - st.optInt("x"))));
                                    redrawLineBitmap();
                                }
                                return true;
                            case MotionEvent.ACTION_UP:
                                saveOps();
                                lastLineDraw = 0; redrawLineBitmap();
                                return true;
                        }
                        return false;
                    }
                });
                try { wm.addView(e2, elp); markers.add(e2); } catch (Throwable ignore) {}
            }
        }
    }
    private android.widget.EditText numField(LinearLayout box, String label, int val) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(0xFF444444);
        box.addView(l);
        android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setText(String.valueOf(val));
        e.setTextSize(13);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dip(6));
        ibg.setColor(0xFFF2F2F2);
        e.setBackground(ibg);
        e.setPadding(dip(8), dip(6), dip(8), dip(6));
        box.addView(e);
        return e;
    }

    /** 编辑单个操作参数(仿原版 编辑点击参数): 位置/周期/时长/重复/延迟/偏移 */
    private void showOpEdit(final int idx) {
        loadOps();
        final JSONObject o = ops.optJSONObject(idx);
        if (o == null) return;
        final boolean swipe = "s".equals(o.optString("t"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(20), dip(16), dip(20), dip(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(16));
        bg.setColor(0xFFFFFFFF);
        box.setBackground(bg);
        TextView t = new TextView(this);
        t.setText(swipe ? "编辑滑动参数 #" + (idx + 1) : "编辑点击参数 #" + (idx + 1));
        t.setTextSize(14);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(0xFF222222);
        box.addView(t);
        final android.widget.EditText eX = numField(box, "X", o.optInt("x"));
        final android.widget.EditText eY = numField(box, "Y", o.optInt("y"));
        final android.widget.EditText eX2 = swipe ? numField(box, "滑动终点X", o.optInt("x2")) : null;
        final android.widget.EditText eY2 = swipe ? numField(box, "滑动终点Y", o.optInt("y2")) : null;
        final android.widget.EditText eP = numField(box, "点击周期ms(间隔)", o.optInt("p", 400));
        final android.widget.EditText eD = numField(box, swipe ? "滑动持续时间ms" : "触屏时长ms", swipe ? o.optInt("d", 400) : o.optInt("touch", 40));
        final android.widget.EditText eR = numField(box, "重复次数", o.optInt("r", 1));
        final android.widget.EditText eW = numField(box, "开始延迟ms", o.optInt("w", 0));
        final android.widget.EditText eO = numField(box, "位置随机偏移px", o.optInt("o", 0));
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(box);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dip(12), 0, 0);
        Button del = new Button(this); del.setText("删除"); del.setTextSize(12);
        Button cancel = new Button(this); cancel.setText("取消"); cancel.setTextSize(12);
        Button save = new Button(this); save.setText("保存"); save.setTextSize(12);
        row.addView(del, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(row);
        final View dlgRoot = sc;
        final View.OnClickListener close = new View.OnClickListener() { public void onClick(View v) {
            try { wm.removeView(dlgRoot); } catch (Throwable ignore) {}
        }};
        del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            JSONArray ns = new JSONArray();
            for (int i = 0; i < ops.length(); i++) if (i != idx) ns.put(ops.opt(i));
            ops = ns; saveOps(); rebuildMarkers(); refresh(); close.onClick(v);
        }});
        cancel.setOnClickListener(close);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try {
                o.put("x", parseInt(eX, o.optInt("x"))); o.put("y", parseInt(eY, o.optInt("y")));
                if (swipe) { o.put("x2", parseInt(eX2, o.optInt("x2"))); o.put("y2", parseInt(eY2, o.optInt("y2"))); o.put("d", parseInt(eD, o.optInt("d", 400))); }
                else o.put("touch", parseInt(eD, o.optInt("touch", 40)));
                o.put("p", parseInt(eP, o.optInt("p", 400)));
                o.put("r", Math.max(1, parseInt(eR, 1)));
                o.put("w", parseInt(eW, 0));
                o.put("o", parseInt(eO, 0));
                ops.put(idx, o); saveOps(); rebuildMarkers(); refresh();
            } catch (Throwable ignore) {}
            close.onClick(v);
        }});
        try {
            WindowManager.LayoutParams plp = new WindowManager.LayoutParams(
                    dip(240), WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    0, PixelFormat.TRANSLUCENT);
            plp.gravity = Gravity.CENTER;
            wm.addView(sc, plp);
        } catch (Throwable ignore) {}
    }

    private int parseInt(android.widget.EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Throwable x) { return def; }
    }

    /** 全局参数(⚙): 循环轮数 + 新点默认值 */
    private void showGlobalSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(20), dip(16), dip(20), dip(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(16));
        bg.setColor(0xFFFFFFFF);
        box.setBackground(bg);
        TextView t = new TextView(this);
        t.setText("全局参数");
        t.setTextSize(14); t.setTypeface(null, android.graphics.Typeface.BOLD); t.setTextColor(0xFF222222);
        box.addView(t);
        final android.widget.EditText eC = numField(box, "循环轮数(0=无限)", countCfg());
        final android.widget.EditText eP = numField(box, "新点默认周期ms", cfg().getInt("interval", 400));
        final android.widget.EditText eT = numField(box, "新点默认触屏时长ms", cfg().getInt("touchMs", 40));
        final android.widget.EditText eO = numField(box, "新点默认偏移px", cfg().getInt("offset", 0));
        final android.widget.EditText eI = numField(box, "新点默认随机延迟ms", cfg().getInt("jitter", 0));
        final android.widget.EditText eG = numField(box, "步骤间隔ms(每步之间停顿, 0=不额外停)", cfg().getInt("stepGap", 0));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dip(12), 0, 0);
        Button cancel = new Button(this); cancel.setText("取消"); cancel.setTextSize(12);
        Button save = new Button(this); save.setText("保存"); save.setTextSize(12);
        row.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(row);
        Button editBtn = new Button(this);
        editBtn.setText("编辑脚本 (按顺序列出所有步骤)");
        editBtn.setTextSize(12);
        LinearLayout.LayoutParams elp2 = new LinearLayout.LayoutParams(-2, -2);
        elp2.topMargin = dip(10);
        box.addView(editBtn, elp2);
        editBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { wm.removeView(box); } catch (Throwable ignore) {}
            showScriptEditor();
        }});
        final WindowManager.LayoutParams plp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
        plp.gravity = Gravity.CENTER;
        final View root = box;
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { wm.removeView(root); } catch (Throwable ignore) {}
        }});
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            cfg().edit()
                    .putInt("count", Math.max(0, parseInt(eC, 0)))
                    .putInt("interval", Math.max(20, parseInt(eP, 400)))
                    .putInt("touchMs", Math.max(10, parseInt(eT, 40)))
                    .putInt("offset", Math.max(0, parseInt(eO, 0)))
                    .putInt("jitter", Math.max(0, parseInt(eI, 0)))
                    .putInt("stepGap", Math.max(0, parseInt(eG, 0)))
                    .apply();
            try { wm.removeView(root); } catch (Throwable ignore) {}
            refresh();
            Toast.makeText(TapperService.this, "已保存", Toast.LENGTH_SHORT).show();
        }});
        try { wm.addView(box, plp); } catch (Throwable ignore) {}
    }

    private int jrand(int base, int jit) {
        return Math.max(20, base + rand.nextInt(Math.max(1, jit + 1)));
    }

    /** 编辑脚本: 按执行顺序列出所有步骤, 单步可改参数/上移/下移/删除 (仿原版 OptEditActivity) */
    private void showScriptEditor() {
        loadOps();
        if (ops.length() == 0) { Toast.makeText(this, "脚本为空: 先加操作或录制", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(20), dip(16), dip(20), dip(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(20));
        bg.setColor(0xF0FFFFFF);
        box.setBackground(bg);
        TextView title = new TextView(this);
        title.setText("编辑脚本 (点步骤=改参数 · ↑↓=调顺序 · 删=移除)");
        title.setTextColor(0xFF222222);
        title.setTextSize(13);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);
        final View root = box;
        final ScrollView sc = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        box.addView(sc, new LinearLayout.LayoutParams(-1, dip(300)));
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = new Runnable() { public void run() {
            list.removeAllViews();
            for (int i = 0; i < ops.length(); i++) {
                final int idx = i;
                JSONObject o = ops.optJSONObject(i);
                if (o == null) continue;
                boolean sw = "s".equals(o.optString("t"));
                LinearLayout row = new LinearLayout(TapperService.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dip(8), dip(6), dip(8), dip(6));
                row.setGravity(Gravity.CENTER_VERTICAL);
                GradientDrawable rbg = new GradientDrawable();
                rbg.setCornerRadius(dip(8));
                rbg.setColor(idx % 2 == 0 ? 0xFFF6F6F6 : 0xFFEEEEEE);
                row.setBackground(rbg);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.topMargin = dip(4);
                TextView lab = new TextView(TapperService.this);
                String desc = sw
                        ? "滑动 (" + o.optInt("x") + "," + o.optInt("y") + ")→(" + o.optInt("x2") + "," + o.optInt("y2") + ") " + o.optInt("d", 400) + "ms"
                        : "点击 (" + o.optInt("x") + "," + o.optInt("y") + ") ×" + o.optInt("r", 1) + " 每" + o.optInt("p", 400) + "ms";
                lab.setText((i + 1) + ". " + desc + "  延迟" + o.optLong("w", 0) + "ms");
                lab.setTextColor(0xFF222222);
                lab.setTextSize(12);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, -2, 1f);
                row.addView(lab, llp);
                lab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showOpEdit(idx); } });
                String[] bs = {"↑", "↓", "删"};
                View.OnClickListener[] ls = {
                    new View.OnClickListener() { public void onClick(View v) {
                        if (idx == 0) return;
                        try {
                            JSONObject tmp = ops.getJSONObject(idx);
                            ops.put(idx, ops.get(idx - 1)); ops.put(idx - 1, tmp);
                            saveOps(); rebuildMarkers();
                        } catch (Throwable ignore) {}
                        rebuild[0].run();
                    }},
                    new View.OnClickListener() { public void onClick(View v) {
                        if (idx >= ops.length() - 1) return;
                        try {
                            JSONObject tmp = ops.getJSONObject(idx);
                            ops.put(idx, ops.get(idx + 1)); ops.put(idx + 1, tmp);
                            saveOps(); rebuildMarkers();
                        } catch (Throwable ignore) {}
                        rebuild[0].run();
                    }},
                    new View.OnClickListener() { public void onClick(View v) {
                        try { ops.remove(idx); saveOps(); rebuildMarkers(); } catch (Throwable ignore) {}
                        if (ops.length() == 0) { try { wm.removeView(root); } catch (Throwable ignore) {} }
                        rebuild[0].run();
                    }},
                };
                for (int k = 0; k < bs.length; k++) {
                    TextView b = new TextView(TapperService.this);
                    b.setText(bs[k]);
                    b.setTextSize(13);
                    b.setTextColor(0xFF1E6FBA);
                    b.setGravity(Gravity.CENTER);
                    b.setPadding(dip(10), 0, dip(10), 0);
                    row.addView(b, new LinearLayout.LayoutParams(-2, -2));
                    b.setOnClickListener(ls[k]);
                }
                list.addView(row, rlp);
            }
        }};
        rebuild[0].run();
        Button done = new Button(this);
        done.setText("完成 (已实时保存)");
        done.setTextSize(12);
        box.addView(done, new LinearLayout.LayoutParams(-1, -2));
        done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { wm.removeView(root); } catch (Throwable ignore) {}
            rebuildMarkers();
            refresh();
        }});
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.82),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(box, lp); } catch (Exception ignore) {}
    }

    private final Runnable opsTick = new Runnable() {
        public void run() {
            if (!running || ops.length() == 0) return;
            if (playIdx >= ops.length()) {
                playIdx = 0; playRounds++;
                if (targetCount > 0 && playRounds >= targetCount) { stopRun(); return; }
            }
            JSONObject o = ops.optJSONObject(playIdx);
            if (o == null) { playIdx++; handler.postDelayed(opsTick, 30); return; }
            final int idx = playIdx;
            playIdx++;
            long delay = Math.max(o.optLong("w", 0), cfg().getInt("stepGap", 0));
            handler.postDelayed(new Runnable() { public void run() {
                if (!running) return;
                execOp(ops.optJSONObject(idx), idx, 0);
            }}, delay);
        }
    };

    /** 执行单个操作(含重复次数与周期) */
    private void execOp(final JSONObject o, final int idx, final int rep) {
        if (!running || o == null) { if (running) handler.postDelayed(opsTick, 30); return; }
        boolean swipe = "s".equals(o.optString("t"));
        String tt = o.optString("t");
        if ("home".equals(tt)) { performGlobalAction(GLOBAL_ACTION_HOME); tapCount++; refresh(); handler.postDelayed(opsTick, 30); return; }
        if ("back".equals(tt)) { performGlobalAction(GLOBAL_ACTION_BACK); tapCount++; refresh(); handler.postDelayed(opsTick, 30); return; }
        if ("notif".equals(tt)) { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); tapCount++; refresh(); handler.postDelayed(opsTick, 30); return; }
        int off = o.optInt("o", 0);
        int jx = off > 0 ? rand.nextInt(2 * off + 1) - off : 0;
        int jy = off > 0 ? rand.nextInt(2 * off + 1) - off : 0;
        if (swipe) {
            swipe(o.optInt("x") + jx, o.optInt("y") + jy, o.optInt("x2") + jx, o.optInt("y2") + jy, Math.max(80, o.optInt("d", 400)));
        } else {
            tapDur(o.optInt("x") + jx, o.optInt("y") + jy, Math.max(10, o.optInt("touch", 40)));
        }
        tapCount++;
        refresh();
        if (targetCount > 0 && tapCount >= targetCount * Math.max(1, totalReps())) { stopRun(); return; }
        int reps = Math.max(1, o.optInt("r", 1));
        if (rep + 1 < reps) {
            handler.postDelayed(new Runnable() { public void run() { execOp(o, idx, rep + 1); } },
                    jrand(o.optInt("p", 400), cfg().getInt("jitter", 0)));
        } else {
            handler.postDelayed(opsTick, 30);
        }
    }

    private int totalReps() {
        int t = 0;
        for (int i = 0; i < ops.length(); i++) {
            JSONObject o = ops.optJSONObject(i);
            if (o != null) t += Math.max(1, o.optInt("r", 1));
        }
        return Math.max(1, t);
    }

    private void beginRun() {
        loadOps();
        if (ops.length() == 0) {
            Toast.makeText(this, "先添加点击/滑动操作或录制脚本", Toast.LENGTH_SHORT).show();
            return;
        }
        targetCount = countCfg();
        tapCount = 0;
        playIdx = 0; playRounds = 0;
        running = true;
        handler.post(opsTick);
        setStartIcon(true);
        refresh();
        Toast.makeText(this, "开始执行: " + ops.length() + " 个操作", Toast.LENGTH_SHORT).show();
    }

    private void setStartIcon(boolean playing) {
        if (ivStart != null) ivStart.setImageResource(playing
                ? R.drawable.c_outline_pause_circle_outline_black_48dp
                : R.drawable.c_outline_play_circle_filled_white_black_48dp);
    }

    private void stopRun() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        setStartIcon(false);
        refresh();
        if (sInst != null) Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show();
    }

    // ---- 取点: 全屏半透明拦截, 音量下键结束 ----
    private void showCapture() {
        if (capture != null) return;
        final ArrayList<int[]> tmp = loadPoints();
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE);
        txt.setTextSize(30);
        txt.setTextAlign(Paint.Align.CENTER);
        View cv = new View(this) {
            protected void onDraw(Canvas c) {
                for (int i = 0; i < tmp.size(); i++) {
                    int[] p = tmp.get(i);
                    fill.setColor(0xFFE74C3C);
                    c.drawCircle(p[0], p[1], 20, fill);
                    c.drawText(String.valueOf(i + 1), p[0], p[1] + 10, txt);
                }
            }
        };
        cv.setBackgroundColor(0x26000000);
        cv.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    tmp.add(new int[]{(int) e.getX(), (int) e.getY()});
                    savePoints(tmp);
                    v.invalidate();
                    Toast.makeText(TapperService.this, "已加 " + tmp.size() + " 个点位", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return true;
            }
        });
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.addView(cv, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Button done = btn("\u5b8c\u6210\u53d6\u70b9", 0xFF1FA855, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View v) { endCapture(); }
        });
        android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dip(40);
        wrap.addView(done, dlp);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, PixelFormat.TRANSLUCENT);
        try {
            wm.addView(wrap, lp);
            capture = wrap;
            points = tmp;
            Toast.makeText(this, "点屏幕加点位, 点【完成取点】或按音量下键结束", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "取点失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void endCapture() {
        if (capture == null) return;
        try { wm.removeView(capture); } catch (Exception ignore) {}
        capture = null;
        Toast.makeText(this, "取点完成: " + points.size() + " 个", Toast.LENGTH_SHORT).show();
    }

    // ---- 录制脚本: 全屏拦截, 记录点击与滑动(含间隔), 音量下键或按钮结束 ----
    private void showRecord() {
        if (recView != null) return;
        recSteps = new JSONArray();
        recOn = true;
        recLastEnd = 0;
        final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(8);
        line.setColor(0xFF9B59B6);
        final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE);
        txt.setTextSize(34);
        txt.setTextAlign(Paint.Align.CENTER);
        View cv = new View(this) {
            protected void onDraw(Canvas c) {
                for (int i = 0; i < recSteps.length(); i++) {
                    JSONObject s = recSteps.optJSONObject(i);
                    if (s == null) continue;
                    float x = s.optInt("x"), y = s.optInt("y");
                    if ("swipe".equals(s.optString("a"))) {
                        line.setColor(0xFF9B59B6);
                        c.drawLine(x, y, s.optInt("x2"), s.optInt("y2"), line);
                        dot.setColor(0xFF9B59B6);
                        c.drawCircle(s.optInt("x2"), s.optInt("y2"), 16, dot);
                    } else {
                        dot.setColor(0xFFE74C3C);
                    }
                    dot.setColor("swipe".equals(s.optString("a")) ? 0xFF9B59B6 : 0xFFE74C3C);
                    c.drawCircle(x, y, 20, dot);
                    c.drawText(String.valueOf(i + 1), x, y + 10, txt);
                }
            }
        };
        cv.setBackgroundColor(0x26000000);
        cv.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                long now = System.currentTimeMillis();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        recDnX = recLX = e.getX(); recDnY = recLY = e.getY();
                        recDownT = now; recMoved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        recLX = e.getX(); recLY = e.getY();
                        if (Math.abs(recLX - recDnX) > dip(12) || Math.abs(recLY - recDnY) > dip(12)) recMoved = true;
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (recSteps.length() == 0) recLastEnd = recDownT;
                        long delay = Math.max(0, recDownT - recLastEnd);
                        long dur = Math.max(80, Math.min(3000, now - recDownT));
                        try {
                            JSONObject s = new JSONObject();
                            if (recMoved && (Math.abs(recLX - recDnX) > dip(12) || Math.abs(recLY - recDnY) > dip(12))) {
                                s.put("a", "swipe");
                                s.put("x", (int) recDnX); s.put("y", (int) recDnY);
                                s.put("x2", (int) recLX); s.put("y2", (int) recLY);
                                s.put("ms", dur);
                            } else {
                                s.put("a", "tap");
                                s.put("x", (int) recDnX); s.put("y", (int) recDnY);
                            }
                            s.put("d", delay);
                            recSteps.put(s);
                        } catch (Throwable ignore) {}
                        recLastEnd = now;
                        v.invalidate();
                        Toast.makeText(TapperService.this, "已录 " + recSteps.length() + " 步", Toast.LENGTH_SHORT).show();
                        return true;
                }
                return true;
            }
        });
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.addView(cv, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Button done = btn("\u5b8c\u6210\u5f55\u5236", 0xFF8E44AD, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View v) { endRecord(); }
        });
        android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dip(40);
        wrap.addView(done, dlp);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, PixelFormat.TRANSLUCENT);
        try {
            wm.addView(wrap, lp);
            recView = wrap;
            Toast.makeText(this, "直接操作屏幕：点击和滑动都会录进脚本，点【完成录制】保存", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            recOn = false;
            Toast.makeText(this, "录制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void endRecord() {
        if (recView == null) return;
        try { wm.removeView(recView); } catch (Exception ignore) {}
        recView = null;
        recOn = false;
        cfg().edit().putString("steps", recSteps.toString()).apply();
        // 录制步骤并入 ops(1:1 多操作模型): tap→点击 op, swipe→滑动 op, 录制间隔→延迟
        try {
            loadOps();
            for (int i = 0; i < recSteps.length(); i++) {
                JSONObject s = recSteps.optJSONObject(i);
                if (s == null) continue;
                JSONObject o = new JSONObject();
                boolean sw = "swipe".equals(s.optString("a"));
                o.put("t", sw ? "s" : "c");
                o.put("x", s.optInt("x")); o.put("y", s.optInt("y"));
                if (sw) {
                    o.put("x2", s.optInt("x2")); o.put("y2", s.optInt("y2"));
                    o.put("d", s.optInt("ms", 400));
                } else {
                    o.put("touch", cfg().getInt("touchMs", 40));
                }
                o.put("p", sw ? 0 : cfg().getInt("interval", 400));
                o.put("r", 1);
                o.put("w", s.optLong("d", 0));
                o.put("o", 0);
                ops.put(o);
            }
            saveOps();
            rebuildMarkers();
        } catch (Throwable ignore) {}
        Toast.makeText(this, "录制完成: " + recSteps.length() + " 步已加入操作列表", Toast.LENGTH_LONG).show();
        refresh();
    }

    private void clearSteps() {
        cfg().edit().putString("steps", "[]").apply();
        playSteps = new JSONArray();
        refresh();
        Toast.makeText(this, "已清空录制脚本", Toast.LENGTH_SHORT).show();
    }

    private void swipe(float x1, float y1, float x2, float y2, long ms) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(80, Math.min(5000, ms))));
        try { dispatchGesture(b.build(), null, null); } catch (Exception ignore) {}
    }

    private void tap(int x, int y) {
        tapDur(x, y, Math.max(10, cfg().getInt("touchMs", 40)));
    }

    private void tapDur(int x, int y, long dur) {
        // 反检测: 位置随机偏移
        int off = cfg().getInt("offset", 0);
        if (off > 0) {
            x += rand.nextInt(2 * off + 1) - off;
            y += rand.nextInt(2 * off + 1) - off;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path, 0, dur));
        try { dispatchGesture(b.build(), null, null); } catch (Exception ignore) {}
    }

    // ---- 保存脚本: 悬浮输入面板 ----
    private void saveDialog(final String caller) {
        boolean hasSteps = false;
        try { hasSteps = new JSONArray(cfg().getString("ops", "[]")).length() > 0; } catch (Exception ignore) {}
        if (loadPoints().isEmpty() && !hasSteps) { Toast.makeText(this, "先取点或录制", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(24), dip(18), dip(24), dip(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(20));
        bg.setColor(0xF0FFFFFF);
        box.setBackground(bg);
        TextView t = new TextView(this);
        t.setText("保存脚本 (点位 " + loadPoints().size() + " · " + baseInterval() + "ms)");
        t.setTextColor(0xFF222222);
        t.setTextSize(13);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(t);
        final EditText name = new EditText(this);
        name.setHint("脚本名");
        name.setTextSize(12);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(dip(8));
        ibg.setColor(0xFFF0F0F0);
        name.setBackground(ibg);
        name.setPadding(dip(10), dip(8), dip(10), dip(8));
        box.addView(name);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn("保存", 0xFF1FA855, 0xFFFFFFFF, null));
        row.addView(btn("取消", 0xFF333333, 0xFFFFFFFF, null));
        box.addView(row);
        Button save = (Button) row.getChildAt(0);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) n = "script_" + System.currentTimeMillis();
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", n);
                    o.put("interval", baseInterval());
                    o.put("jitter", jitter());
                    o.put("count", countCfg());
                    o.put("points", new JSONArray(cfg().getString("points", "[]")));
                    o.put("steps", new JSONArray(cfg().getString("steps", "[]")));
                    o.put("ops", new JSONArray(cfg().getString("ops", "[]")));
                    FileOutputStream f = new FileOutputStream(new File(libDir(), n + ".json"));
                    f.write(o.toString().getBytes(StandardCharsets.UTF_8));
                    f.close();
                    Toast.makeText(TapperService.this, "已保存: " + n, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(TapperService.this, "保存失败", Toast.LENGTH_SHORT).show();
                }
                try { wm.removeView(box); } catch (Exception ignore) {}
                if ("lib".equals(caller)) showLib();
            }
        });
        Button cancel = (Button) row.getChildAt(1);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { wm.removeView(box); } catch (Exception ignore) {}
                if ("lib".equals(caller)) showLib();
            }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.66),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(box, lp); } catch (Exception ignore) {}
    }

    // ---- 脚本库 ----
    private void showLib() {
        if (panel != null) { try { wm.removeView(panel); panel = null; } catch (Exception ignore) {} }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dip(20), dip(16), dip(20), dip(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dip(20));
        bg.setColor(0xF0FFFFFF);
        box.setBackground(bg);
        TextView title = new TextView(this);
        title.setText("脚本库 (点=载入并显示配置 · 长按=删除)");
        title.setTextColor(0xFF222222);
        title.setTextSize(13);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(title);
        ScrollView sc = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        box.addView(sc);
        File[] fs = libDir().listFiles();
        boolean any = false;
        if (fs != null) {
            java.util.Arrays.sort(fs);
            for (final File f : fs) {
                if (!f.getName().endsWith(".json")) continue;
                any = true;
                TextView tv = new TextView(this);
                String nm = f.getName().replace(".json", "");
                try {
                    FileInputStream in = new FileInputStream(f);
                    byte[] buf = new byte[(int) f.length()];
                    in.read(buf);
                    in.close();
                    JSONObject o = new JSONObject(new String(buf, StandardCharsets.UTF_8));
                    nm = o.optString("name", nm) + " · " + o.optInt("interval", 400) + "ms · "
                            + (o.optJSONArray("points") == null ? 0 : o.optJSONArray("points").length()) + "点";
                } catch (Exception ignore) {}
                tv.setText(nm);
                tv.setTextColor(0xFF222222);
                tv.setTextSize(12);
                tv.setPadding(dip(6), dip(12), dip(6), dip(12));
                tv.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { loadScript(f); try { wm.removeView(box); } catch (Exception ignore) {} }
                });
                tv.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        f.delete();
                        Toast.makeText(TapperService.this, "已删除", Toast.LENGTH_SHORT).show();
                        try { wm.removeView(box); } catch (Exception ignore) {}
                        showLib();
                        return true;
                    }
                });
                list.addView(tv);
            }
        }
        if (!any) {
            TextView e = new TextView(this);
            e.setText("还没有保存的脚本");
            e.setTextColor(0xFF888888);
            e.setTextSize(12);
            e.setPadding(dip(6), dip(16), dip(6), dip(16));
            list.addView(e);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn("保存当前为脚本", 0xFFD8A520, 0xFF241636, new View.OnClickListener() {
            public void onClick(View v) {
                try { wm.removeView(box); } catch (Exception ignore) {}
                saveDialog("lib");
            }
        }));
        box.addView(row);
        box.addView(btn("关闭", 0xFF333333, 0xFFFFFFFF, new View.OnClickListener() {
            public void onClick(View v) {
                try { wm.removeView(box); } catch (Exception ignore) {}
            }
        }));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.72),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.5),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(box, lp); } catch (Exception ignore) {}
    }

    private void loadScript(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            in.read(buf);
            in.close();
            JSONObject o = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            cfg().edit()
                    .putInt("interval", o.optInt("interval", 400))
                    .putInt("jitter", o.optInt("jitter", 0))
                    .putInt("count", o.optInt("count", 0))
                    .putString("points", o.optJSONArray("points") == null ? "[]" : o.optJSONArray("points").toString())
                    .putString("steps", o.optJSONArray("steps") == null ? "[]" : o.optJSONArray("steps").toString())
                    .putString("ops", o.optJSONArray("ops") == null ? "[]" : o.optJSONArray("ops").toString())
                    .apply();
            loadOps(); rebuildMarkers();
            Toast.makeText(this, "已载入: " + o.optString("name"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "载入失败", Toast.LENGTH_SHORT).show();
        }
    }

}
