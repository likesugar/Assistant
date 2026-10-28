package com.ziyuan.Assistant;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * 点击防误触守卫：手指按下后若发生「横向」滑动(右滑/左滑)，抬手时不再触发 click，
 * 用于卡片/列表项在横滑手势下被误判成点击而跳转下一页的问题。
 *
 * 判定条件：横位移 > 阈值(默认 系统slop*2 ≈ 16dp，最小 14dp) 且 横位移 > 纵位移(明确横滑意图)。
 * 纵向滚动仍交回系统处理，不影响上下滑动。
 */
public final class TapGuard {
    private TapGuard() {}

    public static void guard(View v) {
        guard(v, 14);
    }

    public static void guard(final View v, final int minDp) {
        if (v == null) return;
        float density = v.getResources().getDisplayMetrics().density;
        final float slop = Math.max(ViewConfiguration.get(v.getContext()).getScaledTouchSlop() * 2f, minDp * density);
        v.setOnTouchListener(new View.OnTouchListener() {
            private float x0, y0;
            private boolean moved;

            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getX();
                        y0 = e.getY();
                        moved = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(e.getX() - x0);
                        float dy = Math.abs(e.getY() - y0);
                        if (dx > slop && dx > dy) moved = true;
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (moved) {
                            moved = false;
                            view.setPressed(false);
                            view.cancelPendingInputEvents();
                            return true;   // 消费掉抬手事件 → 不产生 click
                        }
                        break;
                }
                return false;
            }
        });
    }
}
