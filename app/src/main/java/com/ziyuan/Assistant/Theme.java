package com.ziyuan.Assistant;

import android.content.Context;

/** 全局主题: 首页明暗开关控制所有页面; 深色默认深蓝(护眼), 已移除纯黑(OLED)模式 */
public class Theme {
    public static boolean dark(Context c) { return c.getSharedPreferences("settings", 0).getBoolean("dark", false); }
    /** 纯黑(OLED)模式已移除, 始终返回 false 以兼容旧引用 */
    public static boolean oled(Context c) { return false; }

    public static int bg(Context c)    { return dark(c) ? 0xFF11151D : 0xFFEEF4FF; }
    public static int card(Context c)  { return dark(c) ? 0xFF191F2E : 0xFFFFFFFF; }
    public static int border(Context c){ return dark(c) ? 0xFF232A3A : 0xFFE3E9F5; }
    public static int text(Context c)  { return dark(c) ? 0xFFF2F4F8 : 0xFF1F2329; }
    public static int sub(Context c)   { return dark(c) ? 0xFF8A919E : 0xFF6B7280; }
    public static int c(Context c, int darkC, int lightC) { return dark(c) ? darkC : lightC; }
    public static int accent()         { return 0xFF3D7BFF; }

    /** 行卡片背景资源 */
    public static int rowRes(Context c) {
        return dark(c) ? R.drawable.bg_row_md3_dark : R.drawable.bg_row_md3;
    }
    public static int segBarRes(Context c) { return dark(c) ? R.drawable.bg_seg_bar_dark : R.drawable.bg_seg_bar; }
}
