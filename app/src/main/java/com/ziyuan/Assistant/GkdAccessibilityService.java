package com.ziyuan.Assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** GKD 复刻: 无障碍执行引擎 — @gkd-kit/selector 完整移植引擎 + 订阅规则自动点击 */
public class GkdAccessibilityService extends AccessibilityService {
    public static volatile boolean running = false;
    public static int hitCount = 0;
    private final Map<String, Long> pkgCooldown = new HashMap<>();
    private final Map<String, GkdSelector.Selector> selCache = new ConcurrentHashMap<>();

    /** AccessibilityNodeInfo 适配器: 属性名对齐 GKD 官方 A11yContext */
    private final GkdSelector.NodeAdapter adapter = new GkdSelector.NodeAdapter() {
        public String getName(Object n) { return s(((AccessibilityNodeInfo) n).getClassName()); }
        public Object getAttr(Object n, String k) {
            AccessibilityNodeInfo x = (AccessibilityNodeInfo) n;
            if (k.equals("id")) return s(x.getViewIdResourceName());
            if (k.equals("vid")) { String f = s(x.getViewIdResourceName()); int i = f.lastIndexOf('/'); return i >= 0 ? f.substring(i + 1) : f; }
            if (k.equals("name")) return s(x.getClassName());
            if (k.equals("text")) return x.getText();
            if (k.equals("desc")) return x.getContentDescription();
            if (k.equals("clickable")) return x.isClickable();
            if (k.equals("longClickable")) return x.isLongClickable();
            if (k.equals("checkable")) return x.isCheckable();
            if (k.equals("checked")) return x.isChecked();
            if (k.equals("selected")) return x.isSelected();
            if (k.equals("focusable")) return x.isFocusable();
            if (k.equals("enabled")) return x.isEnabled();
            if (k.equals("scrollable")) return x.isScrollable();
            if (k.equals("password")) return x.isPassword();
            if (k.equals("visibleToUser")) return x.isVisibleToUser();
            if (k.equals("childCount")) return x.getChildCount();
            return null;
        }
        public int getChildCount(Object n) { return ((AccessibilityNodeInfo) n).getChildCount(); }
        public Object getChild(Object n, int i) { return ((AccessibilityNodeInfo) n).getChild(i); }
        public Object getParent(Object n) { return ((AccessibilityNodeInfo) n).getParent(); }
        public Object getRoot(Object n) { return getRootInActiveWindow(); }
    };

    @Override
    public void onServiceConnected() {
        running = true;
        AccessibilityServiceInfo i = new AccessibilityServiceInfo();
        i.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        i.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        i.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        i.notificationTimeout = 100;
        setServiceInfo(i);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) { running = false; return super.onUnbind(intent); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence p = event.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName())) return;
        long now = System.currentTimeMillis();
        Long cd = pkgCooldown.get(pkg);
        if (cd != null && now - cd < 1500) return;
        try {
            JSONArray groups = GkdStore.enabledRules(this, pkg);
            if (groups == null || groups.length() == 0) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            boolean hit = false;
            for (int g = 0; g < groups.length() && !hit; g++) {
                JSONObject grp = groups.optJSONObject(g); if (grp == null) continue;
                JSONArray rules = grp.optJSONArray("rules"); if (rules == null) continue;
                for (int r = 0; r < rules.length() && !hit; r++) {
                    JSONObject rule = rules.optJSONObject(r); if (rule == null) continue;
                    JSONArray matches = rule.optJSONArray("matches");
                    JSONArray anys = rule.optJSONArray("anyMatches");
                    JSONArray src = matches != null ? matches : anys;
                    if (src == null) continue;
                    for (int m = 0; m < src.length() && !hit; m++) {
                        String sel = src.optString(m, "");
                        if (sel.length() == 0) continue;
                        hit = tryMatchAndClick(root, sel);
                    }
                }
            }
            if (hit) {
                hitCount++;
                pkgCooldown.put(pkg, now);
                GkdStore.log(this, pkg);
            } else {
                pkgCooldown.put(pkg, now - 1200);
            }
        } catch (Throwable ignored) {}
    }

    private boolean tryMatchAndClick(AccessibilityNodeInfo root, String selector) {
        try {
            GkdSelector.Selector sel = selCache.get(selector);
            if (sel == null) {
                sel = GkdSelector.parse(selector);
                if (selCache.size() > 500) selCache.clear();
                selCache.put(selector, sel);
            }
            Object target = sel.match(root, adapter);
            if (!(target instanceof AccessibilityNodeInfo)) return false;
            AccessibilityNodeInfo t = (AccessibilityNodeInfo) target;
            // 目标不可点时向下/向上找可点节点
            if (t.isClickable()) return t.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            for (int i = 0; i < t.getChildCount(); i++) {
                AccessibilityNodeInfo c = t.getChild(i);
                if (c != null && c.isClickable()) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            AccessibilityNodeInfo pp = t.getParent();
            while (pp != null) {
                if (pp.isClickable()) return pp.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                pp = pp.getParent();
            }
            return t.performAction(AccessibilityNodeInfo.ACTION_SELECT);
        } catch (Throwable t) { return false; }
    }

    private String s(CharSequence c) { return c == null ? "" : c.toString(); }

    @Override
    public void onInterrupt() {}
}
