package com.ziyuan.Assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** GKD 复刻: 无障碍执行引擎 — 订阅规则匹配 + 自动点击(跳过/关闭类节点) */
public class GkdAccessibilityService extends AccessibilityService {
    public static volatile boolean running = false;
    public static int hitCount = 0;
    private long lastRun = 0;
    private final Map<String, Long> pkgCooldown = new HashMap<>();

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
        if (cd != null && now - cd < 1500) return; // 同包冷却防刷
        lastRun = now;
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

    /** 简化选择器: [attr op value] 单段/多段 ' > ' 链, 支持 text/id/desc, = != *= ^=(含GKD vid*=写法) */
    private boolean tryMatchAndClick(AccessibilityNodeInfo root, String selector) {
        try {
            selector = selector.replace("''", "\"");
            // 拆段: GKD 选择器常带 [attr] 或 @ViewById + [attr], 这里只处理 [...] 链
            java.util.List<String[]> parts = new java.util.ArrayList<>();
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]").matcher(selector);
            while (mm.find()) {
                String seg = mm.group(1).trim();
                String[] c = parseSeg(seg);
                if (c != null) parts.add(c);
            }
            if (parts.isEmpty()) return false;
            java.util.List<AccessibilityNodeInfo> cur = new java.util.ArrayList<>();
            cur.add(root);
            for (String[] c : parts) {
                java.util.List<AccessibilityNodeInfo> next = new java.util.ArrayList<>();
                for (AccessibilityNodeInfo n : cur) {
                    for (int i = 0; i < n.getChildCount(); i++) {
                        AccessibilityNodeInfo ch = n.getChild(i);
                        if (ch != null && segMatch(ch, c)) next.add(ch);
                    }
                }
                cur = next;
                if (cur.isEmpty()) return false;
            }
            // 倒数第一段命中即点; 若最后要求点击目标(text含跳过/关闭/取消/以后/知道) 优先
            AccessibilityNodeInfo target = cur.get(cur.size() - 1);
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    || target.performAction(AccessibilityNodeInfo.ACTION_SELECT);
        } catch (Throwable t) { return false; }
    }

    private String[] parseSeg(String seg) {
        // attr op value
        String[] ops = {"*=", "^=", "$=", "!=", "="};
        for (String op : ops) {
            int ix = seg.indexOf(op);
            if (ix > 0) {
                String k = seg.substring(0, ix).trim().replace("\"", "");
                String v = seg.substring(ix + op.length()).trim();
                if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) v = v.substring(1, v.length() - 1);
                else if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
                return new String[]{k, op, v};
            }
        }
        return new String[]{seg, "?", ""};
    }

    private boolean segMatch(AccessibilityNodeInfo n, String[] c) {
        String k = c[0], op = c[1], v = c[2];
        String val;
        if (k.equals("text") || k.endsWith("text")) val = s(n.getText());
        else if (k.equals("desc") || k.endsWith("description")) val = s(n.getContentDescription());
        else if (k.equals("id") || k.equals("vid") || k.endsWith("id")) {
            String full = s(n.getViewIdResourceName());
            int ix = full.lastIndexOf('/');
            val = ix >= 0 ? full.substring(ix + 1) : full;
        } else if (k.equals("name")) val = s(n.getClassName());
        else val = s(n.getText()) + s(n.getContentDescription());
        if (op.equals("=")) return val.equals(v);
        if (op.equals("!=")) return !val.equals(v);
        if (op.equals("*=")) return val.contains(v);
        if (op.equals("^=")) return val.startsWith(v);
        if (op.equals("$=")) return val.endsWith(v);
        return false;
    }

    private String s(CharSequence c) { return c == null ? "" : c.toString(); }

    @Override
    public void onInterrupt() {}
}
