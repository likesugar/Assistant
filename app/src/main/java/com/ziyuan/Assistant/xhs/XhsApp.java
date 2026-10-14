package com.ziyuan.Assistant.xhs;

import android.content.Context;

/** 应用上下文持有（XhsActivity.onCreate 时注入） */
public final class XhsApp {
    private static Context ctx;
    private XhsApp() {}
    public static void init(Context c) { if (ctx == null) ctx = c.getApplicationContext(); }
    public static Context context() { return ctx; }
}
