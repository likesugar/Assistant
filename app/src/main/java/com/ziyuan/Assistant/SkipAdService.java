package com.ziyuan.Assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** 跳过广告 (参考 hujiayucc/Fuck-AD + GuoXiCheng/SKIP):
 *  无障碍监听窗口变化自动点「跳过」; 支持自定义规则/严格模式/布局检查。 */
public class SkipAdService extends AccessibilityService {
    private long lastTick = 0;
    private static SkipAdService inst;
    private long checkUntil = 0;
    private final java.util.ArrayList<View> overlays = new java.util.ArrayList<>();
    private WindowManager wm;
    private String lastPkg = "";

    public static boolean isRunning() { return inst != null; }
    public static void startCheck(long ms) {
        if (inst != null) { inst.checkUntil = SystemClock.elapsedRealtime() + ms; inst.clearOverlays(); }
    }

    @Override
    protected void onServiceConnected() { super.onServiceConnected(); inst = this; wm = (WindowManager) getSystemService(WINDOW_SERVICE); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int t = event.getEventType();
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && t != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        CharSequence pkgCs = event.getPackageName();
        lastPkg = pkgCs != null ? pkgCs.toString() : "";
        boolean check = SystemClock.elapsedRealtime() < checkUntil;
        long now = SystemClock.elapsedRealtime();
        int throttle = check ? 200 : (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ? 100 : 400);
        if (now - lastTick < throttle) return;
        lastTick = now;
        try {
            if (check) { clearOverlays(); inspect(getRootInActiveWindow(), 0); return; }
            clearOverlays();
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) dfs(root, 0);
        } catch (Throwable ignored) { }
    }

    private void dfs(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 25) return;
        if (isSkipButton(n)) {
            AccessibilityNodeInfo c = n;
            for (int i = 0; i < 4 && c != null && !c.isClickable(); i++) c = c.getParent();
            boolean clicked = false;
            if (c != null && c.isClickable()) clicked = c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) clicked = gestureTap(n);
            if (clicked) return;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            try { dfs(n.getChild(i), depth + 1); } catch (Throwable ignored) { }
        }
    }

    /** 布局检查: 给候选节点画红框, 点框=跳过, 长按框=存为自定义规则 */
    private void inspect(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 25) return;
        if (isSkipButton(n) && overlays.size() < 12) addOverlay(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            try { inspect(n.getChild(i), depth + 1); } catch (Throwable ignored) { }
        }
    }

    private void addOverlay(final AccessibilityNodeInfo n) {
        try {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.isEmpty()) return;
            FrameLayout v = new FrameLayout(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.argb(28, 255, 59, 48));
            bg.setStroke(4, Color.rgb(255, 59, 48));
            v.setBackground(bg);
            v.setClickable(true);
            v.setOnTouchListener((v1, ev) -> false);
            v.setOnClickListener(vv -> { gestureTap(n); Toast.makeText(this, "已点击", Toast.LENGTH_SHORT).show(); });
            v.setOnLongClickListener(vv -> {
                CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
                addRule(lastPkg, t != null ? t.toString() : "");
                Toast.makeText(this, "已存为自定义规则", Toast.LENGTH_SHORT).show();
                return true;
            });
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            lp.format = android.graphics.PixelFormat.TRANSLUCENT;
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = r.left; lp.y = r.top;
            lp.width = r.width(); lp.height = r.height();
            wm.addView(v, lp);
            overlays.add(v);
        } catch (Throwable ignored) { }
    }

    private void clearOverlays() {
        for (View v : overlays) { try { wm.removeView(v); } catch (Throwable ignored) { } }
        overlays.clear();
    }

    private boolean gestureTap(AccessibilityNodeInfo n) {
        try {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.isEmpty() || r.width() <= 0 || r.height() <= 0) return false;
            Path path = new Path();
            path.moveTo(r.exactCenterX(), r.exactCenterY());
            GestureDescription.Builder gb = new GestureDescription.Builder();
            gb.addStroke(new GestureDescription.StrokeDescription(path, 0, 40));
            return dispatchGesture(gb.build(), null, null);
        } catch (Throwable t) { return false; }
    }

    // ===== 匹配 =====
    private boolean isSkipButton(AccessibilityNodeInfo n) {
        if (n == null) return false;
        boolean strict = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("skipAdStrict", false);
        if (strict && !getPackageName().equals(lastPkg)) {
            // 严格模式: 非本应用包才检查, 且要求文本短而精确
            return matchStrict(n.getText()) || matchStrict(n.getContentDescription()) || customMatch(n, true);
        }
        return matchLoose(n.getText()) || matchLoose(n.getContentDescription()) || customMatch(n, false);
    }

    private static boolean matchLoose(CharSequence x) {
        if (x == null) return false;
        String s = x.toString().trim().toLowerCase();
        if (s.isEmpty()) return false;
        if (s.contains("跳过") || s.contains("跳過") || s.contains("关闭广告")
                || s.equals("skip") || s.contains("skip ad") || s.contains("点击跳过")) return true;
        return s.matches("跳过\\s*\\d{1,2}秒?") || s.matches("skip\\s*\\d{1,2}");
    }

    private static boolean matchStrict(CharSequence x) {
        if (x == null) return false;
        String s = x.toString().trim().toLowerCase();
        if (s.isEmpty() || s.length() > 12) return false;
        return s.equals("跳过") || s.equals("跳过广告") || s.equals("跳過") || s.equals("skip")
                || s.matches("跳过\\s*\\d{1,2}秒?") || s.matches("skip\\s*\\d{1,2}");
    }

    private boolean customMatch(AccessibilityNodeInfo n, boolean strict) {
        try {
            JSONArray rules = new JSONArray(getSharedPreferences("settings", MODE_PRIVATE)
                    .getString("skipAdRules", "[]"));
            if (rules.length() == 0) return false;
            String pkg = lastPkg;
            for (int i = 0; i < rules.length(); i++) {
                JSONObject r = rules.getJSONObject(i);
                if (!pkg.equals(r.optString("p"))) continue;
                String kw = r.optString("k", "");
                if (kw.isEmpty()) continue;
                CharSequence[] ts = { n.getText(), n.getContentDescription() };
                for (CharSequence t : ts) {
                    if (t == null) continue;
                    String s = t.toString().trim().toLowerCase();
                    boolean hit = strict ? s.equals(kw.toLowerCase()) : s.contains(kw.toLowerCase());
                    if (hit) return true;
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static void addRule(String pkg, String kw) {
        try {
            if (pkg == null || pkg.isEmpty() || kw == null || kw.trim().isEmpty()) return;
            android.content.SharedPreferences p =
                    inst.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString("skipAdRules", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (pkg.equals(o.optString("p")) && kw.equals(o.optString("k"))) return;
            }
            JSONObject o = new JSONObject();
            o.put("p", pkg); o.put("k", kw.trim());
            arr.put(o);
            p.edit().putString("skipAdRules", arr.toString()).apply();
        } catch (Throwable ignored) { }
    }

    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        clearOverlays();
        if (inst == this) inst = null;
        super.onDestroy();
    }
}
