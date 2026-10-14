package com.ziyuan.Assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 底部胶囊导航: 动态条目(标签制), 支持任意项隐藏 */
public class CapsuleBottomBar extends FrameLayout {
    public interface OnTag { void onTag(String tag); }

    private static final String[] LABELS = {"首页", "媒体", "排盘", "💖", "设置", "播放器"};
    private static final String[] TAGS = {"home", "media", "pan", "fav", "set", "player"};

    private final boolean[] hide = new boolean[6];
    private final TextView[] items = new TextView[6];
    private int active = 0; // 显示序
    private View pill;
    private int itemW;
    private boolean dark;
    private OnTag cb;

    public CapsuleBottomBar(Context c, boolean darkMode, OnTag callback, boolean hideHome, boolean hideMedia, boolean hidePan, boolean hidePlayer) {
        super(c);
        this.dark = darkMode;
        this.cb = callback;
        hide[0] = hideHome; hide[1] = hideMedia; hide[2] = hidePan; hide[3] = false; hide[4] = false; hide[5] = hidePlayer;
        int visible = 0;
        for (boolean h2 : hide) if (!h2) visible++;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        itemW = Math.min(dp(72), (screenW - dp(24)) / Math.max(1, visible));

        int barBg = dark ? 0xF51A1E28 : 0xFFE6EFFF;
        int pillBg = dark ? 0xFF3D6DFF : 0xFF1677FF;
        int onPill = 0xFFFFFFFF;
        int offText = dark ? 0xFF8A94A6 : 0xFF7C8694;

        FrameLayout capsule = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(19));
        bg.setColor(barBg);
        capsule.setBackground(bg);
        capsule.setElevation(dp(10));
        addView(capsule, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        pill = new View(c);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(13));
        pg.setColor(pillBg);
        pill.setBackground(pg);
        LayoutParams plp = new LayoutParams(itemW - dp(8), dp(30), Gravity.CENTER_VERTICAL | Gravity.START);
        plp.leftMargin = dp(4);
        capsule.addView(pill, plp);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        capsule.addView(row, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < 6; i++) {
            if (hide[i]) continue;
            TextView t = new TextView(c);
            t.setText(LABELS[i]);
            t.setTextSize(11.5f);
            t.setGravity(Gravity.CENTER);
            t.setTypeface("home".equals(TAGS[i]) ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setTextColor("home".equals(TAGS[i]) ? onPill : offText);
            final int idx = i;
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { select(idx, true); }
            });
            row.addView(t, new LinearLayout.LayoutParams(itemW, dp(36)));
            items[i] = t;
        }
    }

    private int shownIndexOf(int tagIdx) {
        int n = 0;
        for (int i = 0; i < tagIdx; i++) if (!hide[i]) n++;
        return n;
    }

    private void select(int tagIdx, boolean fire) {
        if (hide[tagIdx]) return;
        if (shownIndexOf(active) == shownIndexOf(tagIdx) && items[active] == items[tagIdx]) {
            if (fire && cb != null) cb.onTag(TAGS[tagIdx]);
            return;
        }
        int oldShown = -1, newShown = shownIndexOf(tagIdx);
        for (int i = 0; i < 6; i++) if (!hide[i] && items[i] == items[active]) oldShown = shownIndexOf(i);
        if (oldShown < 0) oldShown = 0;
        active = tagIdx;
        int onPill = 0xFFFFFFFF;
        int offText = dark ? 0xFF8A94A6 : 0xFF7C8694;
        for (int i = 0; i < 6; i++) {
            if (hide[i] || items[i] == null) continue;
            boolean on = i == active;
            items[i].setTextColor(on ? onPill : offText);
            items[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        ValueAnimator va = ValueAnimator.ofFloat(oldShown * itemW, newShown * itemW);
        va.setDuration(240);
        va.setInterpolator(new OvershootInterpolator(0.8f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { pill.setTranslationX((Float) a.getAnimatedValue()); }
        });
        va.start();
        if (fire && cb != null) cb.onTag(TAGS[tagIdx]);
    }

    /** 外部同步选中(按标签) */
    public void setActiveTag(String tag) {
        int idx = -1;
        for (int i = 0; i < 6; i++) if (TAGS[i].equals(tag)) idx = i;
        if (idx < 0 || hide[idx] || idx == active) return;
        select(idx, false);
    }

    /** 兼容旧调用: 页号→标签 */
    public void setActive(int idx) {
        String tag = idx == 0 ? "home" : idx == 1 ? "media" : idx == 2 ? "pan" : "set";
        setActiveTag(tag);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
