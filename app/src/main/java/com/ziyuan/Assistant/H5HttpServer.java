package com.ziyuan.Assistant;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/** H5 编辑器本地 HTTP 服务器 (对齐 WebCat: WebView 加载 http://127.0.0.1:port/xxx.html, 相对引用原生生效; 支持 Range 视频拖动) */
public class H5HttpServer {
    public interface Responder { byte[] serve(String relPath, String rangeHeader, Map<String, String> out); }
    private final int port;
    private final Responder responder;
    private volatile boolean running;
    private ServerSocket ss;

    public static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html; charset=utf-8"); MIME.put("htm", "text/html; charset=utf-8");
        MIME.put("css", "text/css; charset=utf-8"); MIME.put("js", "application/javascript; charset=utf-8");
        MIME.put("json", "application/json"); MIME.put("txt", "text/plain; charset=utf-8");
        MIME.put("png", "image/png"); MIME.put("jpg", "image/jpeg"); MIME.put("jpeg", "image/jpeg");
        MIME.put("gif", "image/gif"); MIME.put("webp", "image/webp"); MIME.put("svg", "image/svg+xml");
        MIME.put("ico", "image/x-icon"); MIME.put("mp4", "video/mp4"); MIME.put("webm", "video/webm");
        MIME.put("m3u8", "application/vnd.apple.mpegurl"); MIME.put("ts", "video/mp2t");
        MIME.put("mp3", "audio/mpeg"); MIME.put("wav", "audio/wav"); MIME.put("ogg", "audio/ogg");
        MIME.put("woff", "font/woff"); MIME.put("woff2", "font/woff2"); MIME.put("ttf", "font/ttf");
        MIME.put("xml", "application/xml"); MIME.put("pdf", "application/pdf");
    }
    public static String mime(String name) {
        int i = name.lastIndexOf('.');
        String ext = i >= 0 ? name.substring(i + 1).toLowerCase() : "";
        return MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
    }

    public H5HttpServer(Responder r) {
        int p = 18080;
        this.responder = r;
        try { ss = new ServerSocket(0, 64, java.net.InetAddress.getByName("127.0.0.1")); p = ss.getLocalPort(); }
        catch (Throwable t) { try { ss = new ServerSocket(0); p = ss.getLocalPort(); } catch (Throwable ignored) {} }
        this.port = p;
    }
    public int port() { return port; }
    public String base() { return "http://127.0.0.1:" + port + "/"; }
    public void start() {
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    final Socket s = ss.accept();
                    new Thread(() -> handle(s)).start();
                } catch (Throwable e) { if (running) try { Thread.sleep(50); } catch (Throwable ignored) {} }
            }
        });
        t.setDaemon(true);
        t.start();
    }
    public void stop() {
        running = false;
        try { ss.close(); } catch (Throwable ignored) {}
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(8000);
            java.io.InputStream in = s.getInputStream();
            String req = readLine(in);
            if (req == null || !req.startsWith("GET")) { s.close(); return; }
            String path = req.split(" ")[1];
            String range = null;
            String line;
            while ((line = readLine(in)) != null && line.length() > 0) {
                if (line.toLowerCase().startsWith("range:")) range = line.substring(6).trim();
            }
            String rel = URLDecoder.decode(path, "UTF-8");
            if (rel.startsWith("/")) rel = rel.substring(1);
            if (rel.isEmpty() || rel.endsWith("/")) rel = rel + "index.html";
            if (rel.contains("..")) { s.close(); return; }
            Map<String, String> meta = new HashMap<>();
            byte[] body = responder.serve(rel, range, meta);
            OutputStream out = s.getOutputStream();
            if (body == null) {
                out.write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
            } else if (meta.containsKey("status") && "206".equals(meta.get("status"))) {
                // Range 响应: serve() 直接写 socket (流式), 这里只处理整段
                out.write(("HTTP/1.1 206 Partial Content\r\n").getBytes());
                for (Map.Entry<String, String> e : meta.entrySet())
                    if (!e.getKey().equals("status")) out.write((e.getKey() + ": " + e.getValue() + "\r\n").getBytes());
                out.write(("Connection: close\r\n\r\n").getBytes());
                out.write(body);
            } else {
                out.write("HTTP/1.1 200 OK\r\n".getBytes());
                out.write(("Content-Type: " + mime(rel) + "\r\n").getBytes());
                out.write(("Content-Length: " + body.length + "\r\n").getBytes());
                out.write("Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n".getBytes());
                out.write(body);
            }
            out.flush();
            s.close();
        } catch (Throwable ignored) { try { s.close(); } catch (Throwable ignored2) {} }
    }
    private String readLine(java.io.InputStream in) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') break;
                if (c != '\r') bo.write(c);
            }
            return c == -1 && bo.size() == 0 ? null : bo.toString("UTF-8");
        } catch (Throwable t) { return null; }
    }

    /** 目录响应器: 映射到项目目录, 支持 Range 206 */
    public static Responder dirResponder(File root) {
        return (rel, range, meta) -> {
            try {
                File f = new File(root, rel);
                if (!f.exists() || !f.isFile()) return null;
                long len = f.length();
                if (range != null && range.startsWith("bytes=")) {
                    String[] p = range.substring(6).split("-");
                    long start = Long.parseLong(p[0].trim());
                    long end = p.length > 1 && !p[1].isEmpty() ? Long.parseLong(p[1].trim()) : len - 1;
                    if (end >= len) end = len - 1;
                    meta.put("status", "206");
                    meta.put("Content-Range", "bytes " + start + "-" + end + "/" + len);
                    meta.put("Content-Type", mime(rel));
                    meta.put("Accept-Ranges", "bytes");
                    meta.put("Content-Length", String.valueOf(end - start + 1));
                    return fileBytes(f, start, end - start + 1);
                }
                meta.put("Accept-Ranges", "bytes");
                if (len > 8 * 1024 * 1024) return fileBytes(f, 0, len); // 大文件整段读(内存够)
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                FileInputStream fi = new FileInputStream(f);
                byte[] buf = new byte[16384]; int n;
                while ((n = fi.read(buf)) > 0) bo.write(buf, 0, n);
                fi.close();
                return bo.toByteArray();
            } catch (Throwable t) { return null; }
        };
    }
    private static byte[] fileBytes(File f, long skip, long take) throws Exception {
        FileInputStream fi = new FileInputStream(f);
        fi.skip(skip);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384]; long left = take; int n;
        while (left > 0 && (n = fi.read(buf, 0, (int) Math.min(buf.length, left))) > 0) {
            bo.write(buf, 0, n); left -= n;
        }
        fi.close();
        return bo.toByteArray();
    }
}
