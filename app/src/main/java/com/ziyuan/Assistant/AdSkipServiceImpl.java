package com.ziyuan.Assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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
    private ScheduledExecutorService taskExecutorService;
    private volatile boolean skipProcessRunning;
    private String currentPackageName;
    private Set<String> installPackages;
    private Set<String> nodeDescribes;

    public AdSkipServiceImpl(AccessibilityService service) {
        this.service = service;
    }

    public void onServiceConnected() {
        try {
            currentPackageName = "PackageName";
            installPackages = getInstallPackages();
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
                            if (installPackages.contains(actionPackageName)) {
                                startSkipAdProcess();
                            }
                        }
                    }
                    executeSkipTask(service.getRootInActiveWindow());
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
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
        if (!SkipUtil.isKeywords(node, Constant.SCAN_KEYWORD)) {
            return false;
        }
        String nodeDesc = SkipUtil.generateNodeDescribe(node);
        if (!nodeDescribes.contains(nodeDesc)) {
            nodeDescribes.add(nodeDesc);
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d("AdSkip", "try to click " + nodeDesc);
            return true;
        }
        return false;
    }

    public void startSkipAdProcess() {
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
        return installPackages;
    }
}
