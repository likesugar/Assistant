package com.ziyuan.Assistant;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** 跳过广告 (参考 hujiayucc/Fuck-AD 精简移植):
 *  无障碍监听窗口变化, 找到「跳过」类按钮自动点击。 */
public class SkipAdService extends AccessibilityService {
    private long lastTick = 0;
    private int runDepth = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int t = event.getEventType();
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && t != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        long now = SystemClock.elapsedRealtime();
        int throttle = (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) ? 100 : 400;
        if (now - lastTick < throttle) return;
        lastTick = now;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) { runDepth = 0; dfs(root, 0); }
        } catch (Throwable ignored) { }
    }

    private void dfs(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30 || runDepth > 400) return;
        runDepth++;
        if (isSkipButton(n)) {
            AccessibilityNodeInfo c = n;
            for (int i = 0; i < 4 && c != null && !c.isClickable(); i++) c = c.getParent();
            boolean clicked = false;
            if (c != null && c.isClickable()) clicked = c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) { // performClick 失败(如123网盘): 改用坐标手势点按钮中心
                android.graphics.Rect r = new android.graphics.Rect();
                n.getBoundsInScreen(r);
                if (!r.isEmpty() && r.width() > 0 && r.height() > 0) {
                    try {
                        android.graphics.Path path = new android.graphics.Path();
                        path.moveTo(r.exactCenterX(), r.exactCenterY());
                        android.accessibilityservice.GestureDescription.Builder gb =
                                new android.accessibilityservice.GestureDescription.Builder();
                        gb.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40));
                        dispatchGesture(gb.build(), null, null);
                        clicked = true;
                    } catch (Throwable ignored) { }
                }
            }
            if (clicked) return;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            try { dfs(n.getChild(i), depth + 1); } catch (Throwable ignored) { }
        }
    }

    private static boolean isSkipButton(AccessibilityNodeInfo n) {
        if (n == null) return false;
        return match(n.getText()) || match(n.getContentDescription());
    }

    private static boolean match(CharSequence x) {
        if (x == null) return false;
        String s = x.toString().trim().toLowerCase();
        if (s.isEmpty()) return false;
        if (s.contains("跳过") || s.contains("跳過") || s.contains("关闭广告")
                || s.equals("skip") || s.contains("skip ad") || s.contains("点击跳过")) return true;
        return s.matches("跳过\\s*\\d{1,2}秒?") || s.matches("skip\\s*\\d{1,2}"); // "跳过 3" 带倒计时
    }

    @Override public void onInterrupt() { }
}
