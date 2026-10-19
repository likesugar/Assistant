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
            if (k.equals("parent")) return x.getParent();
            if (k.equals("width") || k.equals("height") || k.equals("centerX") || k.equals("centerY")) {
                android.graphics.Rect r = new android.graphics.Rect();
                x.getBoundsInScreen(r);
                if (k.equals("width")) return r.width();
                if (k.equals("height")) return r.height();
                if (k.equals("centerX")) return r.centerX();
                return r.centerY();
            }
            if (k.equals("index") || k.equals("depth")) return null;
            return null;
        }
        public int getChildCount(Object n) { return ((AccessibilityNodeInfo) n).getChildCount(); }
        public Object getChild(Object n, int i) { return ((AccessibilityNodeInfo) n).getChild(i); }
        public Object getParent(Object n) { return ((AccessibilityNodeInfo) n).getParent(); }
        public Object getRoot(Object n) { return getRootInActiveWindow(); }
    };

    public static volatile GkdAccessibilityService instance;
    @Override
    public void onServiceConnected() {
        running = true;
        instance = this;
        AccessibilityServiceInfo i = new AccessibilityServiceInfo();
        i.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        i.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        i.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        i.notificationTimeout = 100;
        setServiceInfo(i);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) { running = false; return super.onUnbind(intent); }

    private final java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor();

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
        // 匹配全程后台单线程, 不占主线程 (防卡顿/ANR)
        exec.execute(() -> doMatch(pkg, event.getClassName() == null ? null : event.getClassName().toString()));
    }

    private void doMatch(String pkg, String activity) {
        try {
            long now = System.currentTimeMillis();
            JSONArray groups = GkdStore.enabledRules(this, pkg);
            if (groups == null || groups.length() == 0) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            boolean hit = false; String lastGroup = null; int lastKey = 0; boolean lastGl = false;
            for (int g = 0; g < groups.length() && !hit; g++) {
                Object gobj = groups.opt(g);
                JSONObject grp = gobj instanceof JSONObject ? (JSONObject) gobj : null;
                if (grp == null) continue;
                if (!grp.optBoolean("enable", true)) continue;
                JSONArray rules = asArray(grp, "rules"); if (rules == null) continue;
                for (int r = 0; r < rules.length() && !hit; r++) {
                    Object rv = rules.opt(r);
                    JSONObject rule = rv instanceof JSONObject ? (JSONObject) rv : null;
                    if (rule == null) {
                        // 规则项本身是选择器字符串 (AIsouler 格式)
                        if (rv instanceof String && ((String) rv).length() > 0) {
                            rule = new JSONObject();
                            try { rule.put("matches", rv); } catch (Throwable ignored) {}
                        } else continue;
                    }
                    if (!rule.optBoolean("enable", true)) continue;
                    JSONArray src = asArray(rule, "matches");
                    if (src == null) src = asArray(rule, "anyMatches");
                    if (src == null) continue;
                    for (int m = 0; m < src.length() && !hit; m++) {
                        String sel = src.optString(m, "");
                        if (sel.length() == 0) continue;
                        hit = tryMatchAndClick(root, sel);
                        if (hit) {
                            lastGroup = grp.optString("name", "");
                            lastKey = rule.optInt("key", grp.optInt("key", 0));
                            lastGl = grp.optBoolean("_gl", false);
                        }
                    }
                }
            }
            if (hit) {
                hitCount++;
                pkgCooldown.put(pkg, now);
                String[] asi = GkdStore.activeSubInfo(this);
                GkdStore.logHit(this, pkg, activity, lastGroup, lastKey, lastGl, asi[0], asi[1] == null ? 0 : Integer.parseInt(asi[1].replaceAll("[^0-9]", "")));
                GkdStatusService.refresh(this);
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
            // 官方行为: 手势坐标点击优先 (广告 SDK 屏蔽 performAction 点击)
            android.graphics.Rect rr = new android.graphics.Rect();
            t.getBoundsInScreen(rr);
            if (rr.isEmpty()) return false;
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(rr.centerX(), rr.centerY());
            android.accessibilityservice.GestureDescription.Builder gb =
                    new android.accessibilityservice.GestureDescription.Builder();
            gb.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40));
            if (dispatchGesture(gb.build(), null, null)) return true;
            // 手势失败回退 performAction: 目标不可点时向下/向上找可点节点
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

    /** 界面快照: dump 所有非本应用窗口 (通知栏触发, 弹窗界面时按下拉通知) */
    public static String dumpTree() {
        GkdAccessibilityService s = instance;
        if (s == null) return "无障碍服务未运行";
        StringBuilder sb = new StringBuilder();
        try {
            // 优先 123云盘, 跳过自己/systemui (下拉通知面板会盖住焦点)
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> wins = new ArrayList<>();
            for (android.view.accessibility.AccessibilityWindowInfo w : s.getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null) continue;
                String pk = r.getPackageName() == null ? "" : r.getPackageName().toString();
                if (pk.equals("com.ziyuan.Assistant") || pk.equals("com.android.systemui")) continue;
                wins.add(w);
            }
            // 目标应用排前
            wins.sort((x, y) -> {
                boolean ax = x.getRoot().getPackageName().toString().contains("mfcloud");
                boolean by = y.getRoot().getPackageName().toString().contains("mfcloud");
                return ax == by ? 0 : (ax ? -1 : 1);
            });
            if (wins.isEmpty()) {
                AccessibilityNodeInfo root = s.getRootInActiveWindow();
                if (root == null) return "当前无可访问窗口";
                sb.append("== pkg=").append(root.getPackageName()).append(" ==\n");
                dumpNode(root, 0, sb);
            } else {
                for (android.view.accessibility.AccessibilityWindowInfo w : wins) {
                    AccessibilityNodeInfo r = w.getRoot();
                    if (sb.length() > 100000) { sb.append("\n(截断)"); break; }
                    sb.append("== pkg=").append(r.getPackageName()).append(" type=").append(w.getType()).append(" ==\n");
                    dumpNode(r, 0, sb);
                }
            }
        } catch (Throwable t) { sb.append("dump err: ").append(t); }
        return sb.toString();
    }
    private static void dumpNode(AccessibilityNodeInfo n, int depth, StringBuilder sb) {
        if (n == null || depth > 30 || sb.length() > 100000) return;
        StringBuilder ind = new StringBuilder();
        for (int i = 0; i < depth; i++) ind.append("  ");
        String cls = n.getClassName() == null ? "?" : n.getClassName().toString();
        int dot = cls.lastIndexOf('.'); if (dot >= 0) cls = cls.substring(dot + 1);
        String vid = n.getViewIdResourceName();
        android.graphics.Rect r = new android.graphics.Rect();
        n.getBoundsInScreen(r);
        sb.append(ind).append(cls);
        if (vid != null) sb.append(" vid=").append(vid.substring(vid.lastIndexOf('/') + 1));
        if (n.getText() != null && n.getText().length() > 0) sb.append(" text=\"").append(n.getText()).append('"');
        if (n.getContentDescription() != null && n.getContentDescription().length() > 0) sb.append(" desc=\"").append(n.getContentDescription()).append('"');
        if (n.isClickable()) sb.append(" clickable");
        sb.append(" w=").append(r.width()).append(" h=").append(r.height());
        sb.append(" ch=").append(n.getChildCount());
        sb.append('\n');
        for (int i = 0; i < n.getChildCount(); i++) dumpNode(n.getChild(i), depth + 1, sb);
    }

    /** rules/matches/anyMatches 官方订阅里可能是字符串也可能是数组 */
    private JSONArray asArray(JSONObject o, String k) {
        Object v = o.opt(k);
        if (v instanceof JSONArray) return (JSONArray) v;
        if (v instanceof String) {
            JSONArray out = new JSONArray();
            out.put(v);
            return out;
        }
        return null;
    }

    private String s(CharSequence c) { return c == null ? "" : c.toString(); }

    @Override
    public void onInterrupt() {}
}
