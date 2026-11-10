package com.ziyuan.Assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** 原生 TXT 阅读视图。移植自 com.example.txtreader.model.b 的真实分页/绘制逻辑:
 *  按字节偏移读行 -> 段首全角缩进 -> breakText 断行(标点避头尾) -> Canvas.drawText 逐行绘制,
 *  顶部书名/章节、底部进度%与时间, 进度以字节偏移记录。左右/上下翻页带平移动画。 */
public class ReadPageView extends View {

    public interface Host {
        void onMenuTap();
        void onProgress(float p, int chapter);
        void onBookEnd(boolean atEnd);
        void onLoading(boolean show);
    }

    public static class Cfg {
        public int fontSize = 20;        // sp
        public int lineSpace = 10;       // dp
        public int letter = 0;           // 0..4 字距档
        public int padding = 18;         // dp
        public int indent = 2;           // 段首全角空格数
        public int fontColor = 0xFF0D5661;
        public int bgColor = 0xFFE6E6E6;
        public int theme = 0;            // 0白天 1夜间
        public int pageMode = 0;         // 0左右 1上下
        public int anim = 0;             // 0平移 1淡入 2无
        public float brightness = 1.0f;

        private static final String SP = "read_cfg";
        public static Cfg load(Context c) {
            SharedPreferences p = c.getSharedPreferences(SP, 0);
            Cfg x = new Cfg();
            x.fontSize = p.getInt("fs", 20);
            x.lineSpace = p.getInt("ls", 10);
            x.letter = p.getInt("lt", 0);
            x.padding = p.getInt("pd", 18);
            x.indent = p.getInt("id", 2);
            x.fontColor = p.getInt("fc", 0xFF0D5661);
            x.bgColor = p.getInt("bg", 0xFFE6E6E6);
            x.theme = p.getInt("th", 0);
            x.pageMode = p.getInt("pm", 0);
            x.anim = p.getInt("an", 0);
            x.brightness = p.getFloat("br", 1.0f);
            return x;
        }
        public void save(Context c) {
            SharedPreferences.Editor e = c.getSharedPreferences(SP, 0).edit();
            e.putInt("fs", fontSize).putInt("ls", lineSpace).putInt("lt", letter)
             .putInt("pd", padding).putInt("id", indent).putInt("fc", fontColor)
             .putInt("bg", bgColor).putInt("th", theme).putInt("pm", pageMode)
             .putInt("an", anim).putFloat("br", brightness).apply();
        }
        public void applyTheme() {
            if (theme == 1) { bgColor = 0xFF2B2B2B; fontColor = 0xFFA5A5A5; }
            else { bgColor = 0xFFE6E6E6; fontColor = 0xFF0D5661; }
        }
    }

    private static class RLine {
        String text;            // null = 画分隔线
        long off;
        boolean chapter;
        RLine(String t, long o, boolean c) { text = t; off = o; chapter = c; }
    }

    private ReadBook book;
    private Cfg cfg;
    private Host host;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<RLine> lines = new ArrayList<RLine>();
    private int W, H, padX, topY, bottomY, lineH, availW, linesPerPage = 1;
    private int pageStart = 0;          // 当前页首行索引
    private float density = 1f;
    private long savedByte = 0;
    private float dragX, dragY;
    private boolean dragging, animating;
    private int targetPage = -1;        // 动画目标页首行索引
    private ValueAnimator va;
    private float downX, downY, lastX, lastY;
    private long downTime;

    public ReadPageView(Context c) { super(c); density = c.getResources().getDisplayMetrics().density; }

    public void setHost(Host h) { host = h; }
    public Cfg cfg() { return cfg; }

    public void open(ReadBook b, Cfg c, float progress) {
        book = b; cfg = c;
        savedByte = (long) (b.size * Math.max(0f, Math.min(1f, progress)));
        if (W > 0) relayout();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w; H = h;
        if (book != null && cfg != null) relayout();
    }

    private void applyPaint() {
        paint.setTextSize(cfg.fontSize * density);
        paint.setColor(cfg.fontColor);
        paint.setLetterSpacing(cfg.letter * 0.02f);
        padX = (int) (cfg.padding * density);
        topY = (int) (34 * density);
        bottomY = (int) (26 * density);
        lineH = (int) (cfg.fontSize * density + cfg.lineSpace * density);
        availW = W - padX * 2;
        linesPerPage = Math.max(1, (H - topY - bottomY) / lineH);
        deco.setColor(cfg.fontColor);
        deco.setStrokeWidth(1f * density);
        deco.setTextSize(12 * density);
    }

    /** 全书预排版: 依据源码的按行读取 + breakText 断行 + 段首缩进 + 章节规则 */
    private void relayout() {
        if (host != null) host.onLoading(true);
        applyPaint();
        lines.clear();
        book.chapters.clear();
        StringBuilder ind = new StringBuilder();
        for (int i = 0; i < cfg.indent; i++) ind.append('\u3000');
        String indent = ind.toString();
        long off = book.bom;
        int guard = 0;
        while (off < book.size && guard++ < 500000) {
            ReadBook.Line ln = book.lineAt(off);
            if (ln == null || ln.next <= off) break;
            long lineOff = off;
            off = ln.next;
            String t = ln.text.replace('\u00a0', ' ').trim();
            if (t.length() == 0) { lines.add(new RLine("", lineOff, false)); continue; }
            boolean ch = ReadBook.isChapterLine(t);
            if (ch) {
                lines.add(new RLine(null, lineOff, false));
                String title = t.length() > 40 ? t.substring(0, 40) : t;
                book.chapters.add(new ReadBook.Chapter(title, lineOff));
            }
            String body = ch ? t : (t.startsWith("\u3000") ? t : indent + t);
            String rest = body;
            int subGuard = 0;
            while (rest.length() > 0 && subGuard++ < 20000) {
                int n = paint.breakText(rest, true, availW, null);
                if (n <= 0) n = 1;
                if (n > rest.length()) n = rest.length();
                String part = rest.substring(0, n);
                String tail = rest.substring(n);
                // 标点避头尾: 行首标点挪到上一行末
                if (tail.length() > 0 && isPunct(tail.charAt(0))) {
                    part += tail.charAt(0);
                    tail = tail.substring(1);
                }
                lines.add(new RLine(part, lineOff, ch));
                rest = tail;
            }
        }
        if (lines.isEmpty()) lines.add(new RLine("", book.bom, false));
        pageStart = findLine(savedByte);
        dragX = dragY = 0; targetPage = -1; animating = false;
        if (host != null) host.onLoading(false);
        invalidate();
        report();
    }

    private static boolean isPunct(char c) {
        return "，。！？；：、）】》」』…,.!?;:)]}>".indexOf(c) >= 0;
    }

    private int findLine(long byteOff) {
        int lo = 0, hi = lines.size() - 1, ans = 0;
        while (lo <= hi) {
            int m = (lo + hi) >>> 1;
            if (lines.get(m).off <= byteOff) { ans = m; lo = m + 1; }
            else hi = m - 1;
        }
        return Math.max(0, Math.min(lines.size() - 1, ans));
    }

    public void applyCfg(Cfg c) {
        long keep = currentByte();
        cfg = c; savedByte = keep;
        if (W > 0) relayout();
    }

    private long currentByte() {
        if (lines.isEmpty()) return book == null ? 0 : book.bom;
        return lines.get(Math.max(0, Math.min(lines.size() - 1, pageStart))).off;
    }

    public float progress() {
        if (book == null || book.size <= 0) return 0;
        return (float) ((double) currentByte() / (double) book.size);
    }

    public int pageCount() { return Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage); }
    public int pageNo() { return Math.min(pageCount(), pageStart / linesPerPage + 1); }
    public int chapterIndex() { return book == null ? 0 : book.chapterAt(currentByte()); }

    public void gotoProgress(float p) {
        savedByte = (long) (book.size * Math.max(0f, Math.min(1f, p)));
        pageStart = findLine(savedByte);
        invalidate(); report();
    }

    public void gotoChapter(int idx) {
        if (book == null || idx < 0 || idx >= book.chapters.size()) return;
        savedByte = book.chapters.get(idx).offset;
        pageStart = findLine(savedByte);
        invalidate(); report();
    }

    public boolean nextPage() { return flip(true); }
    public boolean prevPage() { return flip(false); }

    private boolean flip(boolean next) {
        if (book == null || animating) return false;
        int target = next ? pageStart + linesPerPage : pageStart - linesPerPage;
        if (next && target >= lines.size()) {
            if (host != null) host.onBookEnd(true);
            return false;
        }
        if (!next && pageStart <= 0) { if (host != null) host.onBookEnd(false); return false; }
        target = Math.max(0, Math.min(lines.size() - 1, target));
        if (cfg.anim == 2) { pageStart = target; savedByte = currentByte(); invalidate(); report(); return true; }
        targetPage = target;
        animating = true;
        final boolean horiz = cfg.pageMode == 0;
        final float from = 0f;
        final float to = next ? (horiz ? -W : -H) : (horiz ? W : H);
        va = ValueAnimator.ofFloat(from, to);
        va.setDuration(cfg.anim == 1 ? 150 : 230);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                if (horiz) dragX = v; else dragY = v;
                invalidate();
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                pageStart = targetPage; targetPage = -1; animating = false;
                dragX = dragY = 0; savedByte = currentByte();
                invalidate(); report();
            }
        });
        va.start();
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        if (book == null || cfg == null) { c.drawColor(0xFFE6E6E6); return; }
        c.drawColor(cfg.bgColor);
        if (lines.isEmpty() || linesPerPage <= 0) return;
        boolean horiz = cfg.pageMode == 0;
        if (animating && targetPage >= 0) {
            float a = cfg.anim == 1 ? 1f - Math.min(1f, Math.abs(horiz ? dragX / W : dragY / H)) : 1f;
            drawPage(c, pageStart, 0, 0, (int) (255 * a));
            drawPage(c, targetPage, horiz ? (dragX > 0 ? -W : W) + dragX : 0,
                    horiz ? 0 : (dragY > 0 ? -H : H) + dragY, 255);
        } else if (dragging && targetPage >= 0) {
            drawPage(c, pageStart, horiz ? dragX : 0, horiz ? 0 : dragY, 255);
            drawPage(c, targetPage, horiz ? (dragX > 0 ? -W : W) + dragX : 0,
                    horiz ? 0 : (dragY > 0 ? -H : H) + dragY, 255);
        } else {
            drawPage(c, pageStart, 0, 0, 255);
        }
    }

    private void drawPage(Canvas c, int start, float dx, float dy, int alpha) {
        c.save();
        c.translate(dx, dy);
        int old = paint.getAlpha();
        paint.setAlpha(alpha); deco.setAlpha(alpha);
        // 顶部: 书名 + 章节名
        paint.setFakeBoldText(false);
        paint.setTextSize(cfg.fontSize * density * 0.55f);
        deco.setTextSize(12 * density);
        String title = book.name == null ? "" : book.name;
        if (title.length() > 18) title = title.substring(0, 18) + "…";
        c.drawText(title, padX, topY - 10 * density, deco);
        int ci = book.chapterAt(lines.get(Math.min(start, lines.size() - 1)).off);
        if (ci >= 0 && ci < book.chapters.size()) {
            String ch = book.chapters.get(ci).title;
            if (ch.length() > 14) ch = ch.substring(0, 14) + "…";
            float w = deco.measureText(ch);
            c.drawText(ch, W - padX - w, topY - 10 * density, deco);
        }
        // 正文
        paint.setTextSize(cfg.fontSize * density);
        int end = Math.min(lines.size(), start + linesPerPage);
        float y = topY + lineH;
        for (int i = start; i < end; i++) {
            RLine l = lines.get(i);
            if (l.text == null) {
                c.drawLine(padX, y - lineH * 0.45f, W - padX, y - lineH * 0.45f, deco);
            } else if (l.text.length() > 0) {
                paint.setFakeBoldText(l.chapter);
                c.drawText(l.text, padX, y, paint);
            }
            y += lineH;
        }
        paint.setFakeBoldText(false);
        // 底部: 进度% + 页码 + 时间
        float pct = book.size > 0 ? (float) ((double) lines.get(Math.min(start, lines.size() - 1)).off * 100.0 / book.size) : 0;
        String left = String.format(Locale.getDefault(), "%.2f %%", pct);
        String mid = pageNo() + "/" + pageCount();
        String right = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        c.drawText(left, padX, H - 8 * density, deco);
        c.drawText(mid, (W - deco.measureText(mid)) / 2f, H - 8 * density, deco);
        c.drawText(right, W - padX - deco.measureText(right), H - 8 * density, deco);
        paint.setAlpha(old); deco.setAlpha(old);
        c.restore();
    }

    private void report() {
        if (host != null && book != null) host.onProgress(progress(), chapterIndex());
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (book == null || cfg == null) return true;
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX(); downY = lastY = e.getY(); downTime = System.currentTimeMillis();
                dragging = false; targetPage = -1;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (!animating && (Math.abs(dx) > 12 || Math.abs(dy) > 12)) {
                    boolean horiz = cfg.pageMode == 0;
                    if ((horiz && Math.abs(dx) > Math.abs(dy)) || (!horiz && Math.abs(dy) > Math.abs(dx))) {
                        dragging = true;
                        float d = horiz ? dx : dy;
                        int t = d < 0 ? pageStart + linesPerPage : pageStart - linesPerPage;
                        if (t >= lines.size() || (d > 0 && pageStart <= 0)) { targetPage = -1; return true; }
                        targetPage = Math.max(0, Math.min(lines.size() - 1, t));
                        if (horiz) dragX = d; else dragY = d;
                        invalidate();
                    }
                }
                lastX = e.getX(); lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging && targetPage >= 0) {
                    boolean horiz = cfg.pageMode == 0;
                    float d = horiz ? dragX : dragY;
                    float need = (horiz ? W : H) * 0.28f;
                    boolean commit = Math.abs(d) > need;
                    final int tp = targetPage;
                    if (commit) {
                        // 用动画收尾到整页
                        animating = true;
                        final float from = d, to = d < 0 ? -(horiz ? W : H) : (horiz ? W : H);
                        final boolean hz = horiz;
                        ValueAnimator a = ValueAnimator.ofFloat(from, to);
                        a.setDuration(160);
                        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            public void onAnimationUpdate(ValueAnimator v) {
                                float q = (Float) v.getAnimatedValue();
                                if (hz) dragX = q; else dragY = q;
                                invalidate();
                            }
                        });
                        a.addListener(new android.animation.AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(android.animation.Animator v) {
                                pageStart = tp; targetPage = -1; animating = false; dragging = false;
                                dragX = dragY = 0; savedByte = currentByte(); invalidate(); report();
                            }
                        });
                        a.start();
                    } else {
                        dragX = dragY = 0; dragging = false; targetPage = -1; invalidate();
                    }
                } else if (System.currentTimeMillis() - downTime < 400
                        && Math.abs(e.getX() - downX) < 12 && Math.abs(e.getY() - downY) < 12) {
                    tap(e.getX(), e.getY());
                } else { dragging = false; targetPage = -1; invalidate(); }
                return true;
        }
        return true;
    }

    private void tap(float x, float y) {
        boolean horiz = cfg.pageMode == 0;
        float pos = horiz ? x / W : y / H;
        if (pos < 0.33f) prevPage();
        else if (pos > 0.67f) nextPage();
        else if (host != null) host.onMenuTap();
    }

    @Override protected void onDetachedFromWindow() {
        if (va != null) va.cancel();
        super.onDetachedFromWindow();
    }
}
