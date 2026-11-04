package com.ziyuan.Assistant;

import android.content.Context;

/** 全局主题: 首次启动默认跟随系统明暗, 用户手动开关后固定; 深色=纯黑, 浅色=浅蓝 */
public class Theme {
    /** 系统当前是否深色 */
    private static boolean sysDark(Context c) {
        int m = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 当前主题:
     * - 用户手动开关过(darkUser=true) → 用用户选择的值;
     * - 从未设置过 → 实时跟随系统明暗。
     */
    public static boolean dark(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("settings", 0);
        if (p.getBoolean("darkUser", false)) return p.getBoolean("dark", false);
        return sysDark(c);
    }

    /**
     * 未手动设置过主题时, 把当前系统明暗同步写进 settings.dark ——
     * 大量页面直接读该键, 同步后即获得"跟随系统"; 用户手动开关后不再覆盖。
     * 返回同步后是否与之前不同(用于检测系统明暗变化刷新页面)。
     */
    public static boolean sync(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("settings", 0);
        if (p.getBoolean("darkUser", false)) return false;
        boolean sys = sysDark(c);
        boolean changed = p.getBoolean("dark", sys) != sys;
        p.edit().putBoolean("dark", sys).apply();
        return changed;
    }

    /** 兼容旧引用：纯黑模式已并入深色主题，oled 等价 dark */
    public static boolean oled(Context c) { return dark(c); }

    public static int bg(Context c)    { return dark(c) ? 0xFF000000 : 0xFFEEF4FF; }
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
    /** 首页卡片同款: 透明底+描边+涟漪(资源自带) */
    public static int cardRes(Context c) {
        return dark(c) ? R.drawable.bg_card_md3_dark : R.drawable.bg_card_md3;
    }
    public static int segBarRes(Context c) { return dark(c) ? R.drawable.bg_seg_bar_dark : R.drawable.bg_seg_bar; }
}
