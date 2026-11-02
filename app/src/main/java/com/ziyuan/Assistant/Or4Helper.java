package com.ziyuan.Assistant;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抖音直播「新鲜地址」登记处：
 * 嗅探阶段(shouldInterceptRequest/dygrab.js)每拿到一条流地址就 handle() 登记；
 * 签名/expire 只有几分钟时效，录制断流重试、后台播放重启时用 freshFor()
 * 把过期旧地址热替换为同一路直播的最新地址，否则永远 403
 * （症状：录制大小 0 字节、后台播放卡"转码中"）。
 *
 * 只负责地址登记与替换，不产生记录行——列表抓取由 SniffActivity 管线负责。
 */
public final class Or4Helper {

    private static final Pattern STREAM_ID = Pattern.compile("stream-\\d+");

    // 最新一条新鲜流地址(含签名/expire)
    private static volatile String sFreshUrl;
    private static volatile long sFreshMs;

    private Or4Helper() {
    }

    /**
     * 返回同一路直播的最新新鲜地址；拿不到(过期>20s/不同路/相同)返回 null。
     * 供录制重试与播放启动在旧地址 403 时热替换。
     */
    public static String freshFor(String old) {
        try {
            String f = sFreshUrl;
            if (old == null || f == null || f.length() < 20) return null;
            long age = System.currentTimeMillis() - sFreshMs;
            if (age < 0 || age > 20000L) return null;
            String a = streamId(old);
            if (a == null || !a.equals(streamId(f))) return null;
            if (f.equals(old)) return null;
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 嗅探到抖音直播流地址即登记（主线程/拦截线程均可，字段 volatile）。 */
    public static void handle(String url) {
        if (url == null || url.length() < 20) return;
        String l = url.toLowerCase();
        if (!l.startsWith("http")) return;
        sFreshUrl = url;
        sFreshMs = System.currentTimeMillis();
        log("FRESH " + url);
    }

    private static String streamId(String u) {
        if (u == null) return null;
        try {
            Matcher m = STREAM_ID.matcher(u);
            return m.find() ? m.group() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void log(String s) {
        try {
            Method m = SniffActivity.class.getDeclaredMethod("dumpFc2Debug", String.class);
            m.setAccessible(true);
            m.invoke(null, s);
        } catch (Throwable ignored) {
        }
    }
}
