package com.ziyuan.Assistant;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * HLS 自管抓片器 —— 移植 HoloArchivists/fc2-live-dl 的 hls.py 思路，替代 ffmpeg 直吃 m3u8：
 *  - 每 1s 轮询媒体清单，按「分片 ID」(解 relay 外壳后去 query 的末段文件名)去重，
 *    因此 FC2 分片 ?time=&amp;hash= 签名轮换不会导致同一片被重复下载；
 *  - 增量定位：以上一片在本轮清单的位置 +1 取新增分片(滑动窗口)，对齐 _fill_queue；
 *  - 固定 4 线程池并行下载，Future 按序号保序写 seg%05d.ts，对齐 _download_worker/_read；
 *  - 403=流结束 / 404=清单暂空 / 单片失败最多重试 5 次 / 30s 无新片=超时；
 *  - 输出目录(seg%05d.ts)与 DlManager.finishHls 的 concat 合并完全兼容。
 *
 * 仅用于本地 relay 清单(LiveProxy)：/relay?u= 每轮实时回源，分片行已重写为 relay 外壳，
 * 由代理负责 UA/Referer/上游签名，抓片器只管去重、并发与保序落盘。
 */
final class HlsPuller {

    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final int THREADS = 8;
    private static final long NO_FRAG_TIMEOUT_MS = 30000L;
    private static final int MAX_RETRY = 5;

    /** 一个抓片任务：暂停只停线程，seen/segNo 保留，续录不重复下载。 */
    static final class Task {
        final DlManager.DlJob job;
        final String listUrl;
        final File dir;
        final LinkedHashSet<String> seen = new LinkedHashSet<String>();
        volatile int segNo;
        volatile boolean cancelled;
        volatile boolean running;
        volatile String endReason;
        Thread thread;

        Task(DlManager.DlJob j, String u, File d, int startSeg) {
            job = j; listUrl = u; dir = d; segNo = startSeg;
        }

        synchronized void start() {
            if (running) {                       // 暂停后旧线程尚未退出: 等它结束再起, 避免续录空转
                Thread old = thread;
                if (old != null) { try { old.join(1500); } catch (Throwable ignored) {} }
            }
            cancelled = false; endReason = null;
            running = true;
            thread = new Thread(new Runnable() {
                public void run() {
                    try { loop(Task.this); }
                    finally { running = false; }
                }
            }, "hls-pull");
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            cancelled = true;
            Thread t = thread;
            if (t != null) t.interrupt();
        }
    }

    /** 分片 ID = 解 relay/ts 外壳 → 去 query(签名) → 末段文件名（对齐 hls.py _get_fragment_id）。
     *  FC2 分片 ?time=&hash= 每次刷新都变，去 query 后才是稳定 ID。 */
    static String fragId(String url) {
        try {
            String s = url;
            String relayPath = "/relay?u=";
            String tsPath = "/ts?u=";
            int k = s.indexOf(relayPath);
            if (k < 0) k = s.indexOf(tsPath);
            if (k >= 0) {
                int eq = s.indexOf("?u=", k);
                if (eq >= 0) {
                    String inner = java.net.URLDecoder.decode(s.substring(eq + 3), "UTF-8");
                    if (inner.startsWith("http")) s = inner;
                }
            }
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            int p = s.lastIndexOf('/');
            String id = p >= 0 ? s.substring(p + 1) : s;
            return id.isEmpty() ? url : id;
        } catch (Throwable t) { return url; }
    }

    /** 拉一轮清单：返回 [httpCode, 分片绝对 URL 列表]；非 200 时列表为空。 */
    private static Object[] fetchList(String listUrl) {
        HttpURLConnection c = null;
        List<String> segs = new ArrayList<String>();
        try {
            c = (HttpURLConnection) new URL(listUrl).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", UA);
            int rc = c.getResponseCode();
            if (rc != 200) return new Object[]{rc, segs};
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String ln;
            while ((ln = br.readLine()) != null) {
                ln = ln.trim();
                if (ln.isEmpty() || ln.startsWith("#")) continue;
                if (!ln.startsWith("http")) {
                    try { ln = new URL(new URL(listUrl), ln).toString(); }
                    catch (Throwable ignored) { continue; }
                }
                segs.add(ln);
            }
            br.close();
            return new Object[]{200, segs};
        } catch (Throwable e) {
            return new Object[]{-1, segs};
        } finally {
            if (c != null) { try { c.disconnect(); } catch (Throwable ignored) {} }
        }
    }

    /** 下载单个分片（失败最多重试 MAX_RETRY 次）；返回字节或 null。 */
    private static byte[] fetchSeg(String url) {
        for (int tries = 0; tries < MAX_RETRY; tries++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", UA);
                if (c.getResponseCode() != 200) {
                    c.disconnect();
                    continue;
                }
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[32768];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                in.close();
                return bo.toByteArray();
            } catch (Throwable ignored) {
            } finally {
                if (c != null) { try { c.disconnect(); } catch (Throwable ignored) {} }
            }
        }
        return null;
    }

    private static void loop(final Task t) {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        long lastFragAt = System.currentTimeMillis();
        String lastId = null;
        try {
            while (!t.cancelled) {
                Object[] r = fetchList(t.listUrl);
                int code = ((Integer) r[0]).intValue();
                @SuppressWarnings("unchecked")
                List<String> segs = (List<String>) r[1];

                if (code == 403 || code == 410) { t.endReason = "流已结束(HTTP" + code + ")"; break; }
                if (code != 200) {
                    t.job.state = "清单 HTTP" + code + "，重试中…";
                    if (System.currentTimeMillis() - lastFragAt > NO_FRAG_TIMEOUT_MS) {
                        t.endReason = "清单持续不可用"; break;
                    }
                    Thread.sleep(800);
                    continue;
                }

                // 增量：定位上一片在本轮清单的位置 +1；找不到则从 0 起，由 seen 兜底去重
                int newIdx = 0;
                if (lastId != null) {
                    for (int i = segs.size() - 1; i >= 0; i--) {
                        if (lastId.equals(fragId(segs.get(i)))) { newIdx = i + 1; break; }
                    }
                }
                final List<String> fresh = new ArrayList<String>();
                for (int i = newIdx; i < segs.size(); i++) {
                    String id = fragId(segs.get(i));
                    if (t.seen.add(id)) { fresh.add(segs.get(i)); lastId = id; }
                }
                // 有界裁剪：只保留最近 300 个 ID，防长时间录制内存增长
                if (t.seen.size() > 400) {
                    Iterator<String> it = t.seen.iterator();
                    int drop = t.seen.size() - 300;
                    while (drop-- > 0 && it.hasNext()) { it.next(); it.remove(); }
                }

                if (!fresh.isEmpty()) {
                    lastFragAt = System.currentTimeMillis();
                    // 并行下载，Future 列表按清单序号保序
                    List<Future<byte[]>> fs = new ArrayList<Future<byte[]>>(fresh.size());
                    for (final String u : fresh) {
                        fs.add(pool.submit(new Callable<byte[]>() {
                            public byte[] call() { return fetchSeg(u); }
                        }));
                    }
                    for (Future<byte[]> f : fs) {
                        if (t.cancelled) break;
                        byte[] data;
                        try { data = f.get(20, TimeUnit.SECONDS); }
                        catch (Throwable e) { data = null; }
                        if (data == null || data.length == 0) continue;
                        File out = new File(t.dir, String.format(Locale.US, "seg%05d.ts", t.segNo++));
                        FileOutputStream fo = null;
                        try {
                            fo = new FileOutputStream(out);
                            fo.write(data);
                            fo.flush();
                            t.job.doneBytes += data.length;
                            t.job.state = "下载中";
                        } catch (Throwable ignored) {
                        } finally {
                            if (fo != null) { try { fo.close(); } catch (Throwable ignored) {} }
                        }
                    }
                } else if (System.currentTimeMillis() - lastFragAt > NO_FRAG_TIMEOUT_MS) {
                    t.endReason = "30s 无新分片(流结束)";
                    break;
                }
                Thread.sleep(800);   // hls.py 同款轮询间隔
            }
        } catch (InterruptedException ie) {
            // 暂停 / 取消：正常退出
        } catch (Throwable e) {
            t.endReason = "中断: " + e.getClass().getSimpleName();
        } finally {
            pool.shutdownNow();
            if (!t.cancelled && t.endReason != null) {
                t.job.state = t.endReason + "（点结束合并）";
            }
        }
    }
}
