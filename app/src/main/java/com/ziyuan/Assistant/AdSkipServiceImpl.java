package com.ziyuan.Assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import android.graphics.Path;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** 广告跳过具体实现类 (1:1 移植 Giftedcat/AdSkipHelper) */
public class AdSkipServiceImpl {

    private final AccessibilityService service;
    public Handler processActionHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService taskExecutorService;
    private volatile boolean skipProcessRunning;
    private String currentPackageName;
    private Set<String> installPackages;
    private Set<String> nodeDescribes;
    // ===== 李跳跳式自定义规则 (skipAdCfg) =====
    private String cfgRaw = null;
    private final java.util.ArrayList<org.json.JSONObject> lttPopup = new java.util.ArrayList<>();
    private final java.util.ArrayList<String> lttKeywords = new java.util.ArrayList<>();
    private final java.util.ArrayList<String[]> simplePopups = new java.util.ArrayList<>();

    private void ensureCfg() {
        try {
            String raw = getSharedPreferences("settings", MODE_PRIVATE)
                    .getString("skipAdCfg", "");
            if (raw != null && raw.equals(cfgRaw)) return;
            cfgRaw = raw;
            parseCfg(raw);
        } catch (Throwable ignored) { }
    }

    private void parseCfg(String text) {
        lttPopup.clear(); lttKeywords.clear(); simplePopups.clear();
        if (text == null || text.trim().isEmpty()) return;
        try {
            String t = text.trim();
            // 李跳跳整包导入: 外层 [{"hash":"{rules}"},...]
            if (t.startsWith("[{")) {
                JSONArray outer = new JSONArray(t);
                StringBuilder merged = new StringBuilder();
                for (int i = 0; i < outer.length(); i++) {
                    JSONObject o = outer.optJSONObject(i);
                    if (o == null) continue;
                    java.util.Iterator<String> ks = o.keys();
                    while (ks.hasNext()) {
                        String k = ks.next();
                        if (k.equals("popup_rules") || k.equals("keywords") || k.equals("keywords_append")) continue;
                        String v = o.optString(k, "");
                        if (v.startsWith("{") && v.contains("popup_rules")) {
                            if (merged.length() > 0) merged.append(',');
                            merged.append(v);
                        }
                    }
                }
                if (merged.length() > 0) t = "[" + merged + "]";
            }
            if (t.startsWith("[")) {
                JSONArray arr = new JSONArray(t);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    JSONArray pr = o.optJSONArray("popup_rules");
                    if (pr != null) for (int j = 0; j < pr.length(); j++) {
                        JSONObject r = pr.optJSONObject(j);
                        if (r != null) lttPopup.add(r);
                    }
                    for (String kn : new String[]{"keywords", "keywords_append"}) {
                        JSONArray kw = o.optJSONArray(kn);
                        if (kw != null) for (int j = 0; j < kw.length(); j++) {
                            String k = kw.optString(j, "");
                            if (!k.isEmpty()) lttKeywords.add(k);
                        }
                    }
                }
            } else {
                JSONObject o = new JSONObject(t);
                JSONArray pr = o.optJSONArray("popup_rules");
                if (pr != null) for (int i = 0; i < pr.length(); i++) {
                    JSONObject r = pr.getJSONObject(i);
                    String id = r.optString("id", ""), act = r.optString("action", "");
                    if (!id.isEmpty() && !act.isEmpty()) simplePopups.add(new String[]{id, act});
                }
            }
        } catch (Throwable ignored) { }
    }

    public AdSkipServiceImpl(AccessibilityService service) {
        this.service = service;
    }

    public void onServiceConnected() {
        try {
            currentPackageName = "PackageName";
            installPackages = getInstallPackages();
            logClick("CONNECTED pkgs=" + (installPackages == null ? -1 : installPackages.size()));
            nodeDescribes = new HashSet<>();
            initActionHandler();
            taskExecutorService = Executors.newSingleThreadScheduledExecutor();
        } catch (Throwable e) {
            Log.e("AdSkip", e.getMessage());
        }
    }

    private void initActionHandler() {
        processActionHandler = new Handler(Looper.getMainLooper(), msg -> {
            switch (msg.what) {
                case ProcessAction.ACTION_REFRESH_PACKAGE:
                    installPackages = getInstallPackages();
                    break;
                case ProcessAction.ACTION_STOP_SERVICE:
                    service.disableSelf();
                    break;
                case ProcessAction.ACTION_START_SKIP_PROCESS:
                    startSkipAdProcess();
                    break;
                case ProcessAction.ACTION_STOP_SKIP_PROCESS:
                    stopSkipAdProcess();
                    break;
            }
            return true;
        });
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null || event.getClassName() == null) {
            return;
        }
        String actionPackageName = event.getPackageName().toString();
        String actionClassname = event.getClassName().toString();
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    boolean isActivity = !actionClassname.startsWith("android.")
                            && !actionClassname.startsWith("androidx.");
                    if (!currentPackageName.equals(actionPackageName)) {
                        if (isActivity) {
                            currentPackageName = actionPackageName;
                            stopSkipAdProcess();
                            logClick("WIN " + actionPackageName + " " + actionClassname
                                    + " inList=" + (installPackages != null && installPackages.contains(actionPackageName)));
                            // 名单包含该应用, 或名单为空(包可见性受限兜底)=扫所有非桌面应用
                            boolean inList = installPackages != null && installPackages.contains(actionPackageName);
                            boolean emptyList = installPackages == null || installPackages.isEmpty();
                            if (inList || (emptyList && !actionPackageName.equals("com.android.launcher"))) {
                                startSkipAdProcess();
                            }
                        }
                    }
                    ensureCfg();
                    if (!lttPopup.isEmpty() || !simplePopups.isEmpty()) {
                        handlePopups(service.getRootInActiveWindow());
                    }
                    executeSkipTask(service.getRootInActiveWindow());
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    ensureCfg();
                    if (!installPackages.contains(actionPackageName)) {
                        break;
                    }
                    executeSkipTask(event.getSource());
                    break;
            }
        } catch (Throwable e) {
            Log.e("AdSkip", String.valueOf(e.getMessage()));
        }
    }

    private void executeSkipTask(final AccessibilityNodeInfo nodeInfo) {
        if (!skipProcessRunning) {
            return;
        }
        taskExecutorService.execute(() -> iterateNodesToSkipAd(nodeInfo));
    }

    private void iterateNodesToSkipAd(AccessibilityNodeInfo rootNodeInfo) {
        List<AccessibilityNodeInfo> parentNodes = new ArrayList<>();
        List<AccessibilityNodeInfo> childNodes = new ArrayList<>();
        parentNodes.add(rootNodeInfo);

        int total = parentNodes.size();
        int index = 0;
        while (index < total && skipProcessRunning) {
            AccessibilityNodeInfo nodeInfo = parentNodes.get(index++);
            if (nodeInfo != null) {
                boolean clicked = clickSkipNode(nodeInfo);
                if (clicked) {
                    nodeInfo.recycle();
                    break;
                }
                for (int n = 0; n < nodeInfo.getChildCount(); n++) {
                    childNodes.add(nodeInfo.getChild(n));
                }
                nodeInfo.recycle();
            }
            if (index == total) {
                parentNodes.clear();
                parentNodes.addAll(childNodes);
                childNodes.clear();
                index = 0;
                total = parentNodes.size();
            }
        }
        while (index < total) {
            AccessibilityNodeInfo node = parentNodes.get(index++);
            if (node != null) node.recycle();
        }
        index = 0;
        total = childNodes.size();
        while (index < total) {
            AccessibilityNodeInfo node = childNodes.get(index++);
            if (node != null) node.recycle();
        }
    }

    private boolean clickSkipNode(AccessibilityNodeInfo node) {
        boolean hit = SkipUtil.isKeywords(node, Constant.SCAN_KEYWORD);
        if (!hit && !lttKeywords.isEmpty()) {
            CharSequence[] ts = { node.getText(), node.getContentDescription() };
            for (CharSequence t : ts) {
                if (t == null) continue;
                String ls = t.toString().trim().toLowerCase();
                for (String k : lttKeywords) {
                    if (!ls.isEmpty() && ls.contains(k.toLowerCase())) { hit = true; break; }
                }
                if (hit) break;
            }
        }
        if (!hit) {
            return false;
        }
        // 按钮形状过滤: 跳过按钮一定是小控件且在屏幕上半区, 防止命中横跨全屏的大容器(点错拽下通知栏)
        android.graphics.Rect br = new android.graphics.Rect();
        node.getBoundsInScreen(br);
        int sw = service.getResources().getDisplayMetrics().widthPixels;
        int sh = service.getResources().getDisplayMetrics().heightPixels;
        if (br.isEmpty()) { logClick("SKIP-CAND empty " + node); return false; }
        logClick("CAND " + node.getText() + " rect=" + br);
        if (br.width() > sw * 0.5f || br.height() > sh * 0.2f) { logClick("CAND-REJ size"); return false; }
        if (br.top > sh * 0.5f) { logClick("CAND-REJ pos"); return false; }
        String nodeDesc = SkipUtil.generateNodeDescribe(node);
        if (!nodeDescribes.contains(nodeDesc)) {
            // 先 performAction; 失败(123网盘等不可点击节点)改用坐标手势点击, 失败不记账可重试
            boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) clicked = gestureTap(node);
            logClick(nodeDesc + " act=" + clicked + " rect=" + br);
            if (clicked) {
                nodeDescribes.add(nodeDesc);
                return true;
            }
        }
        return false;
    }

    /** 坐标手势点击按钮中心 (必须主线程) */
    private boolean gestureTap(AccessibilityNodeInfo node) {
        try {
            final android.graphics.Rect r = new android.graphics.Rect();
            node.getBoundsInScreen(r);
            if (r.isEmpty() || r.width() <= 0 || r.height() <= 0) return false;
            final boolean[] ok = {false};
            mainHandler.post(() -> {
                try {
                    android.graphics.Path path = new android.graphics.Path();
                    path.moveTo(r.exactCenterX(), r.exactCenterY());
                    GestureDescription.Builder gb = new GestureDescription.Builder();
                    gb.addStroke(new GestureDescription.StrokeDescription(path, 0, 40));
                    ok[0] = service.dispatchGesture(gb.build(), null, null);
                } catch (Throwable ignored) { }
            });
            // 等 dispatchGesture 排队结果(最多200ms)
            for (int i = 0; i < 20 && !ok[0]; i++) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) { }
            }
            return ok[0];
        } catch (Throwable t) { return false; }
    }

    // ===== 李跳跳规则执行 =====
    /** 屏上出现 id 条件 → 执行 action。id/action 支持: 文本(包含), +包含, -排除, &与, =精确,
     *  l,t,r,b 坐标, viewId, action=GLOBAL_ACTION_BACK */
    private void handlePopups(AccessibilityNodeInfo root) {
        if (root == null) return;
        for (String[] r : simplePopups) {
            if (findCond(root, r[0], false, 0) != null) {
                AccessibilityNodeInfo btn = findCond(root, r[1], false, 0);
                if (btn != null) clickNode(btn);
            }
        }
        for (int ri = 0; ri < lttPopup.size(); ri++) {
            JSONObject r = lttPopup.get(ri);
            String id = r.optString("id", ""), act = r.optString("action", "");
            if (id.isEmpty() || act.isEmpty()) continue;
            if (!lttIdMatched(root, id)) continue;
            int delay = r.optInt("delay", 0);
            if (delay > 0) {
                try { Thread.sleep(Math.min(delay, 800)); } catch (InterruptedException ignored) { }
            }
            doLttAction(root, act);
        }
    }

    private boolean lttIdMatched(AccessibilityNodeInfo root, String id) {
        if (id.matches("\\d+,\\d+,\\d+,\\d+")) {
            return boundsExists(root, parseBounds(id), 0);
        }
        boolean requireAll = id.contains("&");
        String[] parts = id.split("&");
        for (String part : parts) {
            boolean positive = true, exact = false;
            String cond = part;
            if (cond.startsWith("+")) cond = cond.substring(1);
            else if (cond.startsWith("-")) { positive = false; cond = cond.substring(1); }
            if (cond.startsWith("=")) { exact = true; cond = cond.substring(1); }
            boolean found = cond.matches("\\d+,\\d+,\\d+,\\d+")
                    ? boundsExists(root, parseBounds(cond), 0)
                    : findCond(root, cond, exact, 0) != null;
            if (requireAll && !found) return false;
            if (!requireAll && !positive && found) return false;
        }
        return true;
    }

    private Rect parseBounds(String s) {
        String[] p = s.split(",");
        return new Rect(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
    }

    private boolean boundsExists(AccessibilityNodeInfo n, Rect want, int depth) {
        if (n == null || depth > 20) return false;
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        if (r.equals(want) || (r.left == want.left && r.top == want.top
                && Math.abs(r.width() - want.width()) <= 40)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            try { if (boundsExists(n.getChild(i), want, depth + 1)) return true; } catch (Throwable ignored) { }
        }
        return false;
    }

    private AccessibilityNodeInfo findCond(AccessibilityNodeInfo n, String kw, boolean exact, int depth) {
        if (n == null || depth > 20) return null;
        CharSequence[] ts = { n.getText(), n.getContentDescription() };
        for (CharSequence t : ts) {
            if (t == null) continue;
            String sv = t.toString();
            if (exact ? sv.trim().equals(kw) : sv.contains(kw)) return n;
        }
        CharSequence vid = n.getViewIdResourceName();
        if (vid != null && vid.toString().contains(kw)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            try {
                AccessibilityNodeInfo r = findCond(n.getChild(i), kw, exact, depth + 1);
                if (r != null) return r;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private void doLttAction(AccessibilityNodeInfo root, String act) {
        try {
            if (act.equals("GLOBAL_ACTION_BACK")) {
                service.performGlobalAction(GLOBAL_ACTION_BACK);
                return;
            }
            if (act.matches("\\d+,\\d+,\\d+,\\d+")) {
                Rect r = parseBounds(act);
                Path path = new Path();
                path.moveTo(r.exactCenterX(), r.exactCenterY());
                GestureDescription.Builder gb = new GestureDescription.Builder();
                gb.addStroke(new GestureDescription.StrokeDescription(path, 0, 40));
                service.dispatchGesture(gb.build(), null, null);
                return;
            }
            AccessibilityNodeInfo btn = findCond(root, act.replace("=", ""), false, 0);
            if (btn != null) clickNode(btn);
        } catch (Throwable ignored) { }
    }

    private void clickNode(AccessibilityNodeInfo btn) {
        AccessibilityNodeInfo c = btn;
        for (int i = 0; i < 4 && c != null && !c.isClickable(); i++) c = c.getParent();
        boolean ok = c != null && c.isClickable() && c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (!ok) gestureTap(btn);
    }

    private void logClick(String msg) {
        try {
            java.io.File dir = service.getExternalFilesDir(null);
            if (dir == null) return;
            java.io.File f = new java.io.File(dir, "skipad_log.txt");
            if (f.length() > 200000) f.delete(); // 防膨胀
            java.io.FileOutputStream out = new java.io.FileOutputStream(f, true);
            out.write((System.currentTimeMillis() + " " + msg + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) { }
    }

    public void startSkipAdProcess() {
        logClick("SCAN-START " + currentPackageName);
        skipProcessRunning = true;
        nodeDescribes.clear();
        processActionHandler.removeMessages(ProcessAction.ACTION_STOP_SKIP_PROCESS);
        processActionHandler.sendEmptyMessageDelayed(ProcessAction.ACTION_STOP_SKIP_PROCESS, Constant.SCAN_TIME);
    }

    public void stopSkipAdProcess() {
        skipProcessRunning = false;
        processActionHandler.removeMessages(ProcessAction.ACTION_STOP_SKIP_PROCESS);
    }

    private Set<String> getInstallPackages() {
        PackageManager packageManager = service.getPackageManager();
        Set<String> installPackages = new HashSet<>();
        Set<String> homeAppPackages = new HashSet<>();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : resolveInfoList) {
            installPackages.add(e.activityInfo.packageName);
        }
        intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : resolveInfoList) {
            homeAppPackages.add(e.activityInfo.packageName);
        }
        homeAppPackages.add(service.getPackageName());
        homeAppPackages.add("com.android.settings");
        installPackages.removeAll(homeAppPackages);
        // 用户自定义名单: 勾选了才扫, 未勾选=扫全部
        try {
            JSONArray sel = new JSONArray(service.getSharedPreferences("settings",
                    android.content.Context.MODE_PRIVATE).getString("skipAdPkgs", "[]"));
            if (sel.length() > 0) {
                Set<String> allow = new HashSet<>();
                for (int i = 0; i < sel.length(); i++) allow.add(sel.getString(i));
                installPackages.retainAll(allow);
            }
        } catch (Throwable ignored) { }
        return installPackages;
    }
}
