package com.ziyuan.Assistant;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 局域网共享服务：stopWithTask —— 应用任务被划掉时自动销毁并撤通知、停服务器 */
public class LanShareService extends Service {

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LanShareServer.start();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LanShareServer.stop();
        super.onDestroy();
    }
}
