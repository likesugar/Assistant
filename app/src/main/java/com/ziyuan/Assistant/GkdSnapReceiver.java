package com.ziyuan.Assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 通知栏抓取界面快照: 弹窗界面时按下拉通知按钮, 不切走界面 */
public class GkdSnapReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        new Thread(() -> {
            try {
                String tree = GkdAccessibilityService.dumpTree();
                GkdStore.sp(context).edit().putString("last_snap", tree)
                        .putLong("last_snap_t", System.currentTimeMillis()).apply();
            } catch (Throwable ignored) {}
        }).start();
    }
}
