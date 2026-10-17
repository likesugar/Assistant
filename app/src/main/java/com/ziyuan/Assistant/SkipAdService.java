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
        if (now - lastTick < 300) return; // 节流
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
            if (c != null && c.isClickable()) {
                c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
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
        return s.contains("跳过") || s.contains("跳過") || s.contains("关闭广告")
                || s.equals("skip") || s.contains("skip ad");
    }

    @Override public void onInterrupt() { }
}
