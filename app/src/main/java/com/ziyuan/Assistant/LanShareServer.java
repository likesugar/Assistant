package com.ziyuan.Assistant;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;
import java.util.Collections;

/** 局域网共享服务器：电脑打开 http://手机IP:端口 可见并打开记录中的链接 */
public class LanShareServer {

    private static volatile int port = -1;
    private static volatile boolean running = false;
    private static ServerSocket ss = null;

    public static boolean isRunning() { return running; }
    public static int getPort() { return port; }

    private static int randomPort() {
        return 10000 + new java.util.Random().nextInt(55536);
    }

    public static synchronized void start() {
        if (running) return;
        // 同步绑定：调用返回时端口一定就绪（不再出现 -1）
        ServerSocket tmp = null;
        int p2 = -1;
        for (int i = 0; i < 20; i++) {
            try { p2 = randomPort(); tmp = new ServerSocket(p2); break; }
            catch (Throwable e) { tmp = null; }
        }
        if (tmp == null) return;
        ss = tmp;
        port = p2;
        running = true;
        new Thread(new Runnable() { public void run() {
            try {
                while (running) {
                    final Socket s = ss.accept();
                    new Thread(new Runnable() { public void run() {
                        try { handle(s); } catch (Throwable ignored) {}
                        finally { try { s.close(); } catch (Throwable ignored) {} }
                    }}).start();
                }
            } catch (Throwable ignored) {}
        }}).start();
    }

    public static synchronized void stop() {
        running = false;
        try { if (ss != null) ss.close(); } catch (Throwable ignored) {}
        ss = null;
    }

    private static void handle(Socket s) throws Exception {
        java.io.InputStream in = s.getInputStream();
        String req = readLine(in);
        String l;
        int contentLen = 0;
        while ((l = readLine(in)) != null && !l.isEmpty()) {
            String ll = l.toLowerCase();
            if (ll.startsWith("content-length:")) try { contentLen = Integer.parseInt(l.substring(15).trim()); } catch (Throwable ignored) {}
        }
        String path = "/";
        if (req != null && req.startsWith("GET ")) {
            String[] parts = req.split(" ");
            if (parts.length >= 2) path = parts[1];
        } else if (req != null && req.startsWith("POST ")) {
            String[] parts = req.split(" ");
            if (parts.length >= 2) path = parts[1];
        }
        // 聊天室: 电脑发消息(UDP广播给全屋手机)
        if (path.startsWith("/chat/send") && "POST".equals(req.substring(0, 4))) {
            byte[] body = new byte[Math.max(0, contentLen)];
            int got = 0;
            while (got < body.length) { int n = in.read(body, got, body.length - got); if (n < 0) break; got += n; }
            try {
                org.json.JSONObject o = new org.json.JSONObject(new String(body, "UTF-8"));
                ChatHub.send(o.optString("nick", "PC"), o.optString("msg", ""));
            } catch (Throwable ignored) {}
            OutputStream os = s.getOutputStream();
            os.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".getBytes());
            os.flush();
            return;
        }
        // 聊天室: 电脑拉取新消息
        if (path.startsWith("/chat/poll")) {
            long since = 0;
            int q = path.indexOf("since=");
            if (q > 0) try { since = Long.parseLong(path.substring(q + 6).replaceAll("[^0-9].*$", "")); } catch (Throwable ignored) {}
            byte[] bb = ChatHub.since(since).toString().getBytes("UTF-8");
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + bb.length
                + "\r\nConnection: close\r\n\r\n").getBytes());
            os.write(bb);
            os.flush();
            return;
        }
        // 聊天文件下载: /chatfile?ts=xxx (必须在/chat页面路由之前, 否则startsWith会吃掉)
        if (path.startsWith("/chatfile")) {
            long ts = 0;
            int q = path.indexOf("ts=");
            if (q > 0) try { ts = Long.parseLong(path.substring(q + 3).replaceAll("[^0-9].*$", "")); } catch (Throwable ignored) {}
            java.io.File f = ChatHub.getServeFile(ts);
            if (f == null || !f.exists()) f = ChatHub.ensureFile(ts); // 按需从源拉取(代理)
            if (f == null || !f.exists()) {
                OutputStream os = s.getOutputStream();
                os.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes());
                os.flush();
                return;
            }
            String lu = f.getName().toLowerCase();
            boolean img = lu.endsWith(".jpg") || lu.endsWith(".jpeg") || lu.endsWith(".png") || lu.endsWith(".webp") || lu.endsWith(".gif");
            String mime = img ? (lu.endsWith(".png") ? "image/png" : lu.endsWith(".gif") ? "image/gif" : lu.endsWith(".webp") ? "image/webp" : "image/jpeg")
                              : "application/octet-stream";
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 200 OK\r\nContent-Type: " + mime
                + "\r\nContent-Length: " + f.length() + "\r\nConnection: close\r\n\r\n").getBytes());
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            byte[] bf = new byte[8192]; int nn;
            while ((nn = fis.read(bf)) > 0) os.write(bf, 0, nn);
            fis.close();
            os.flush();
            return;
        }
        // 聊天室: 电脑网页 (精确匹配, 放在/chatfile之后)
        if (path.equals("/chat") || path.equals("/chat/") || path.startsWith("/chat?")) {
            String page = chatPage();
            byte[] bb = page.getBytes("UTF-8");
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bb.length
                + "\r\nConnection: close\r\n\r\n").getBytes());
            os.write(bb);
            os.flush();
            return;
        }
        // 打开链接：302 到手机中转（代拉流，带正确 Referer）
        if (path.startsWith("/open?u=")) {
            String u = java.net.URLDecoder.decode(path.substring(8), "UTF-8");
            // 本地录制文件：直接由手机回放（带Range），不302到中转
            if (u.startsWith("file://")) {
                java.io.File f = new java.io.File(u.substring(7));
                OutputStream os = s.getOutputStream();
                if (!f.exists()) {
                    os.write(("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nConnection: close\r\nContent-Length: 14\r\n\r\nfile not found").getBytes());
                    os.flush(); return;
                }
                String range = null; String l2;
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
                while ((l2 = br.readLine()) != null && !l2.isEmpty()) if (l2.toLowerCase().startsWith("range:")) range = l2;
                long len = f.length(), start = 0, end = len - 1; boolean partial = false;
                if (range != null && range.contains("bytes=")) {
                    try {
                        String spec = range.substring(range.indexOf('=') + 1).trim();
                        int dash = spec.indexOf('-');
                        start = Long.parseLong(spec.substring(0, dash).trim());
                        if (dash < spec.length() - 1) end = Long.parseLong(spec.substring(dash + 1).trim());
                        if (end >= len) end = len - 1;
                        partial = start <= end;
                    } catch (Throwable ignored) { }
                }
                String head = (partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n")
                    + "Content-Type: video/mp2t\r\nAccept-Ranges: bytes\r\n"
                    + (partial ? "Content-Range: bytes " + start + "-" + end + "/" + len + "\r\n" : "")
                    + "Content-Length: " + (end - start + 1) + "\r\nConnection: close\r\n\r\n";
                os.write(head.getBytes());
                java.io.InputStream fin = new java.io.FileInputStream(f);
                if (partial) fin.skip(start);
                byte[] buf = new byte[64 * 1024]; long remain = end - start + 1; int n;
                while (remain > 0 && (n = fin.read(buf, 0, (int)Math.min(buf.length, remain))) > 0) { os.write(buf, 0, n); remain -= n; }
                fin.close(); os.flush();
                return;
            }
            String me = localIp();
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 302 Found\r\nLocation: http://" + me + ":8123/relay?u="
                + java.net.URLEncoder.encode(u, "UTF-8") + "\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").getBytes());
            os.flush();
            return;
        }
        // 列表页
        StringBuilder sb = new StringBuilder();
        sb.append("<html><meta charset='utf-8'><meta name='viewport' content='width=device-width'>")
          .append("<body style='background:#111;color:#eee;font-family:monospace'>")
          .append("<h3>记录的链接</h3>");
        java.util.LinkedHashMap<String, String> links = SniffActivity.lanLinks();
        for (java.util.Map.Entry<String, String> e : links.entrySet()) {
            String enc = java.net.URLEncoder.encode(e.getKey(), "UTF-8");
            sb.append("<p><a style='color:#8ab4f8' href='/open?u=").append(enc).append("'>")
              .append(e.getValue()).append("</a><br><small style='color:#888'>").append(e.getKey()).append("</small></p>");
        }
        if (links.isEmpty()) sb.append("<p>（暂无记录）</p>");
        sb.append("</body></html>");
        byte[] bb = sb.toString().getBytes("UTF-8");
        OutputStream os = s.getOutputStream();
        os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bb.length
            + "\r\nConnection: close\r\n\r\n").getBytes());
        os.write(bb);
        os.flush();
    }

    private static String readLine(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        int c = -1;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
        }
        if (b.size() == 0 && c < 0) return null;
        return b.toString();
    }

    /** 电脑端聊天网页 */
    private static String chatPage() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
            + "<title>聊天室</title><style>"
            + "body{background:#111;color:#eee;font-family:-apple-system,'PingFang SC',sans-serif;margin:0}"
            + "#box{padding:12px;height:calc(100vh - 60px);overflow-y:auto}"
            + ".m{margin:6px 0}.n{font-size:11px;color:#888}.b{display:inline-block;background:#1c2026;padding:8px 12px;border-radius:12px;margin-top:2px}"
            + "#bar{position:fixed;bottom:0;left:0;right:0;display:flex;gap:8px;padding:10px;background:#16181d}"
            + "input{flex:1;background:#0c0e12;border:none;color:#eee;padding:10px 14px;border-radius:20px;outline:none}"
            + "button{background:#315CDE;color:#fff;border:none;border-radius:20px;padding:10px 18px}"
            + "</style></head><body><div id='box'></div><div id='bar'>"
            + "<input id='nick' placeholder='昵称' style='max-width:90px'><input id='msg' placeholder='说点什么…'>"
            + "<button onclick='send()'>发送</button></div><script>"
            + "var since=0;var box=document.getElementById('box');"
            + "function poll(){fetch('/chat/poll?since='+since).then(function(r){return r.json()}).then(function(a){"
            + "for(var i=0;i<a.length;i++){var o=a[i];if(o.ts>since)since=o.ts;"
            + "var d=document.createElement('div');d.className='m';"
            + "var body;"
            + "if(o.fname){var ext=o.fname.toLowerCase();"
            + "if(ext.indexOf('.jpg')>=0||ext.indexOf('.jpeg')>=0||ext.indexOf('.png')>=0||ext.indexOf('.webp')>=0||ext.indexOf('.gif')>=0){"
            + "body=\"<img src='/chatfile?ts=\"+o.fts+\"' style='max-width:220px;border-radius:8px;display:block'>\";}"
            + "else{body=\"<a style='color:#7fb0ff' href='/chatfile?ts=\"+o.fts+\"'>📎 \"+o.fname.replace(/</g,'&lt;')+\" (点击下载)</a>\";}}"
            + "else{body=o.msg.replace(/</g,'&lt;');}"
            + "d.innerHTML=\"<div class='n'>\"+o.nick+' · '+new Date(o.ts).toLocaleTimeString()+\"</div><div class='b'>\"+body+\"</div>\";"
            + "box.appendChild(d);}if(a.length)box.scrollTop=box.scrollHeight;"
            + "setTimeout(poll,1500);}).catch(function(){setTimeout(poll,3000);});}"
            + "function send(){var m=document.getElementById('msg').value.trim();if(!m)return;"
            + "var n=document.getElementById('nick').value.trim()||'PC';"
            + "fetch('/chat/send',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nick:n,msg:m})});"
            + "document.getElementById('msg').value='';}"
            + "document.getElementById('msg').addEventListener('keydown',function(e){if(e.key=='Enter')send()});"
            + "poll();</script></body></html>";
    }

    public static String localIp() {
        try {
            for (java.util.Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                for (java.net.InetAddress a : Collections.list(en.nextElement().getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a.getHostAddress().indexOf(':') < 0) return a.getHostAddress();
                }
            }
        } catch (Throwable ignored) {}
        return "?";
    }
}
