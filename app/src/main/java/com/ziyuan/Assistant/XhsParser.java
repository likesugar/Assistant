package com.ziyuan.Assistant.xhs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析核心：对齐原版 XhsUrlParser + XhsNoteParser（org.json 等价移植） */
public final class XhsParser {

    // ---------- URL 解析（XhsUrlParser） ----------

    private static final Pattern LINKS = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:xiaohongshu\\.com|rednote\\.com|xhslink\\.(?:com|cn))/[^\\s\\\"<>\\\\^`{|}，。；！？、【】《》]+",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> HOSTS = new LinkedHashSet<String>();
    static {
        for (String h : new String[]{"xiaohongshu.com", "www.xiaohongshu.com", "rednote.com", "www.rednote.com", "xhslink.com", "xhslink.cn"}) HOSTS.add(h);
    }
    private static final Pattern POST_ID = Pattern.compile("[A-Za-z0-9_-]+");

    public interface ShortUrlResolver { String resolve(String url); }

    public static List<String> extractLinks(String input, ShortUrlResolver resolver) {
        List<String> out = new ArrayList<String>();
        if (input == null) return out;
        Matcher m = LINKS.matcher(input);
        while (m.find()) {
            int start = m.start();
            if (start > 0) {
                char prev = input.charAt(start - 1);
                if ((prev >= 'a' && prev <= 'z') || (prev >= 'A' && prev <= 'Z') || (prev >= '0' && prev <= '9')
                        || "._-".indexOf(prev) >= 0) continue;
            }
            String raw = m.group().trim();
            while (raw.length() > 0 && ")]}.,;!，。）】".indexOf(raw.charAt(raw.length() - 1)) >= 0)
                raw = raw.substring(0, raw.length() - 1);
            String url = raw.toLowerCase().startsWith("http") ? raw : "https://" + raw;
            if (!isSupportedUrl(url)) continue;
            if (isShortUrl(url) && resolver != null) {
                String r = resolver.resolve(url);
                if (r != null && !r.isEmpty()) url = r;
            }
            if (!out.contains(url)) out.add(url);
        }
        return out;
    }

    public static boolean isSupportedUrl(String url) {
        if (url == null) return false;
        try {
            java.net.URI u = new java.net.URI(url);
            String scheme = u.getScheme() == null ? null : u.getScheme().toLowerCase();
            String host = u.getHost() == null ? null : u.getHost().toLowerCase();
            return ("http".equals(scheme) || "https".equals(scheme)) && host != null && HOSTS.contains(host) && u.getUserInfo() == null;
        } catch (Throwable t) { return false; }
    }

    public static boolean isShortUrl(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            return host != null && (host.equalsIgnoreCase("xhslink.com") || host.equalsIgnoreCase("xhslink.cn"));
        } catch (Throwable t) { return false; }
    }

    /** 提取链接前的分享文案（仅当输入里只有一个链接时） */
    public static String extractShareTitle(String input) {
        List<String> ls = extractLinks(input, null);
        if (ls.size() != 1) return null;
        String url = ls.get(0);
        int start = input.indexOf(url);
        if (start < 0) {
            String bare = url.startsWith("https://") ? url.substring(8) : url;
            start = input.indexOf(bare);
            if (start < 0) return null;
        }
        String prefix = input.substring(0, start).trim();
        return prefix.isEmpty() ? null : prefix;
    }

    public static String extractPostId(String url) {
        try {
            if (url == null) return null;
            String normalized = url.toLowerCase().startsWith("http") ? url : "https://" + url;
            if (!isSupportedUrl(normalized) || isShortUrl(normalized)) return null;
            String path = new java.net.URI(normalized).getPath();
            if (path.startsWith("/")) path = path.substring(1);
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String[] seg = path.split("/");
            String id = null;
            if (seg.length == 2 && "explore".equals(seg[0])) id = seg[1];
            else if (seg.length == 3 && "discovery".equals(seg[0]) && "item".equals(seg[1])) id = seg[2];
            else if (seg.length == 4 && "user".equals(seg[0]) && "profile".equals(seg[1])) id = seg[3];
            if (id == null) return null;
            return POST_ID.matcher(id).matches() ? id : null;
        } catch (Throwable t) { return null; }
    }

    // ---------- 笔记解析（XhsNoteParser） ----------

    public static class Media {
        public String url;          // 下载用地址
        public String originalUrl;  // 原始地址
        public String id;
        public String kind;         // image / video / live / comment
        public int width, height;
        public boolean cover;       // 视频封面
        public String previewUrl;   // 预览图（live 用图，视频用封面）
        public String liveVideoUrl; // live 才有
        public boolean watermarked;
    }

    public static class Note {
        public String noteId, type, title, body, description, authorName, authorId, publishTime, canonicalUrl;
        public long publishedAt = 0;
        public List<String> tags = new ArrayList<String>();
        public Map<String, String> interactions = new LinkedHashMap<String, String>();
        public List<Media> items = new ArrayList<Media>();
        public List<Media> images() { return filter("image"); }
        public List<Media> videos() { return filter("video"); }
        public List<Media> lives() { return filter("live"); }
        public boolean hasVideo() { return !videos().isEmpty() || !lives().isEmpty(); }
        private List<Media> filter(String k) {
            List<Media> out = new ArrayList<Media>();
            for (Media m : items) if (k.equals(m.kind)) out.add(m);
            return out;
        }
    }

    public static class ResolveException extends RuntimeException {
        public final String reason; // invalid / network / parse / webview
        public ResolveException(String reason, Throwable cause) { super(cause); this.reason = reason; }
    }

    /** 从 HTML 解析（主入口） */
    public static Note parse(String html, String expectedNoteId, String canonicalUrl) throws ResolveException {
        if (html == null || html.trim().isEmpty()) throw new ResolveException("webview", null);
        JSONObject root = parseInitialStateRoot(html);
        List<JSONObject> notes = root == null ? new ArrayList<JSONObject>() : findNoteObjects(root);
        JSONObject selected = null;
        if (!notes.isEmpty()) {
            if (expectedNoteId != null) {
                for (JSONObject n : notes) if (expectedNoteId.equals(n.optString("noteId"))) { selected = n; break; }
                if (selected == null && notes.size() == 1 && notes.get(0).optString("noteId").isEmpty()) selected = notes.get(0);
            } else if (notes.size() == 1) selected = notes.get(0);
            if (selected == null) throw new ResolveException("parse", null);
        }
        if (selected == null) {
            // 退化：裸 URL 扫（原版 legacy 提取，但仓库层要求真笔记，这里同样报 webview）
            throw new ResolveException("webview", null);
        }
        JSONObject mobile = null;
        if (root != null) {
            JSONObject nd = root.optJSONObject("noteData");
            if (nd != null) {
                JSONObject data = nd.optJSONObject("data");
                if (data != null) {
                    JSONObject inner = data.optJSONObject("noteData");
                    if (inner != null && selected.optString("noteId").equals(inner.optString("noteId"))) mobile = data;
                }
            }
        }
        JSONObject detail = null;
        if (root != null) {
            JSONObject noteRoot = root.optJSONObject("note");
            if (noteRoot != null) {
                JSONObject map = noteRoot.optJSONObject("noteDetailMap");
                if (map != null) detail = map.optJSONObject(selected.optString("noteId"));
            }
        }
        if (detail == null) detail = mobile;
        Note note = parseDetail(detail != null ? detail : selected, canonicalUrl, expectedNoteId);
        if (note.noteId == null || note.noteId.trim().isEmpty()) throw new ResolveException("webview", null);
        return note;
    }

    public static Note parseDetail(JSONObject detail, String canonicalUrl, String expectedNoteId) {
        JSONObject note = detail.optJSONObject("note");
        if (note == null) note = detail.optJSONObject("noteData");
        if (note == null) note = detail;
        JSONObject comments = detail.optJSONObject("comments");
        if (comments == null) comments = detail.optJSONObject("commentData");
        if (comments == null) comments = note.optJSONObject("comments");
        return parseNote(note, canonicalUrl, expectedNoteId, comments);
    }

    public static Note parseNote(JSONObject note, String canonicalUrl, String expectedNoteId, JSONObject comments) {
        Note out = new Note();
        out.canonicalUrl = canonicalUrl;
        out.noteId = optNonBlank(note, "noteId");
        if (out.noteId.isEmpty() && expectedNoteId != null) out.noteId = expectedNoteId;
        JSONArray imageList = note.optJSONArray("imageList");
        if (imageList == null) imageList = note.optJSONArray("images");
        if (imageList == null) imageList = new JSONArray();
        JSONObject mainVideo = note.optJSONObject("video");
        String type = optNonBlank(note, "type");
        if (type.isEmpty()) {
            boolean noStream = imageList.length() <= 1;
            if (!noStream) {
                JSONObject first = imageList.optJSONObject(0);
                noStream = first == null || first.optJSONObject("stream") == null;
            }
            type = (mainVideo != null && noStream) ? "video" : "normal";
        }
        out.type = type;
        boolean isVideoNote = "video".equals(type);
        Set<String> seenUrls = new LinkedHashSet<String>();
        for (int index = 0; index < imageList.length(); index++) {
            JSONObject item = imageList.optJSONObject(index);
            if (item == null) continue;
            String original = optNonBlank(item, "urlDefault");
            if (original.isEmpty()) original = optNonBlank(item, "url");
            if (original.isEmpty()) {
                JSONArray info = item.optJSONArray("infoList");
                if (info != null) for (int i = 0; i < info.length(); i++) {
                    JSONObject o = info.optJSONObject(i);
                    if (o != null && !o.optString("url").trim().isEmpty()) { original = o.optString("url").trim(); break; }
                }
            }
            if (original.isEmpty()) {
                String trace = item.optString("traceId");
                if (!trace.isEmpty()) original = "https://sns-img-qc.xhscdn.com/" + trace;
            }
            if (!isHttp(original)) continue;
            List<Cand> streams = new ArrayList<Cand>();
            mergeCandidates(streams, videoCandidates(item.optJSONObject("livePhoto")));
            mergeCandidates(streams, videoCandidates(item));
            boolean isCover = isVideoNote && streams.isEmpty();
            Media img = new Media();
            img.url = original; img.originalUrl = original;
            img.id = out.noteId + ":" + (isCover ? "cover" : "image") + ":" + (index + 1);
            img.previewUrl = original; img.cover = isCover;
            img.width = item.optInt("width"); img.height = item.optInt("height");
            img.kind = "image";
            if (!streams.isEmpty()) {
                img.kind = "live";
                img.liveVideoUrl = streams.get(0).url;
                img.id = out.noteId + ":live:" + (index + 1);
                img.watermarked = streams.get(0).watermarked;
            }
            if (seenUrls.add(img.kind + "|" + img.url + (img.kind.equals("live") ? "|" + img.liveVideoUrl : ""))) out.items.add(img);
        }
        if (mainVideo != null && (isVideoNote || !note.has("type"))) {
            List<Cand> distinct = videoCandidates(mainVideo);
            if (!distinct.isEmpty()) {
                Media v = new Media();
                v.kind = "video"; v.url = distinct.get(0).url; v.originalUrl = distinct.get(0).url;
                v.id = out.noteId + ":video";
                v.watermarked = distinct.get(0).watermarked;
                v.previewUrl = out.items.isEmpty() ? "" : out.items.get(0).previewUrl;
                if (seenUrls.add("video|" + v.url)) out.items.add(v);
            }
        }
        // 评论图片（对齐 parseCommentImages：BFS 去重）
        if (comments != null) {
            Set<String> seenComments = new LinkedHashSet<String>();
            ArrayDeque<JSONObject> queue = new ArrayDeque<JSONObject>();
            enqueueAll(queue, comments.optJSONArray("list"));
            if (queue.isEmpty()) enqueueAll(queue, comments.optJSONArray("comments"));
            while (!queue.isEmpty()) {
                JSONObject comment = queue.poll();
                String nid = comment.optString("noteId");
                if (!nid.isEmpty() && !nid.equals(out.noteId)) continue;
                if (comment.optBoolean("invalid", false)) continue;
                String cid = comment.optString("id");
                if (cid.isEmpty() || !seenComments.add(cid)) continue;
                enqueueAll(queue, comment.optJSONArray("subComments"));
                JSONArray pictures = comment.optJSONArray("pictures");
                if (pictures == null) continue;
                for (int index = 0; index < pictures.length(); index++) {
                    JSONObject picture = pictures.optJSONObject(index);
                    if (picture == null) continue;
                    JSONArray info = picture.optJSONArray("infoList");
                    String original = optNonBlank(picture, "urlDefault");
                    if (!isHttp(original)) original = "";
                    if (original.isEmpty() && info != null) {
                        for (int i = 0; i < info.length(); i++) {
                            JSONObject o = info.optJSONObject(i);
                            if (o != null && "WB_DFT".equals(o.optString("imageScene")) && isHttp(o.optString("url"))) { original = o.optString("url"); break; }
                        }
                    }
                    if (original.isEmpty() && isHttp(picture.optString("url"))) original = picture.optString("url");
                    if (original.isEmpty() && info != null) {
                        for (int i = 0; i < info.length(); i++) {
                            JSONObject o = info.optJSONObject(i);
                            if (o != null && isHttp(o.optString("url"))) { original = o.optString("url"); break; }
                        }
                    }
                    if (original.isEmpty() && isHttp(picture.optString("urlPre"))) original = picture.optString("urlPre");
                    if (!isHttp(original)) continue;
                    Media m = new Media();
                    m.kind = "comment"; m.url = original; m.originalUrl = original;
                    m.id = out.noteId + ":comment:" + cid + ":" + (index + 1);
                    m.previewUrl = isHttp(picture.optString("urlPre")) ? picture.optString("urlPre") : original;
                    m.width = picture.optInt("width"); m.height = picture.optInt("height");
                    if (seenUrls.add("comment|" + m.url)) out.items.add(m);
                }
            }
        }
        JSONObject user = note.optJSONObject("user");
        if (user == null) user = note.optJSONObject("user_info");
        if (user == null) user = new JSONObject();
        out.title = optNonBlank(note, "title");
        out.body = optNonBlank(note, "desc");
        if (out.body.isEmpty()) out.body = optNonBlank(note, "description");
        StringBuilder desc = new StringBuilder();
        if (!out.title.isEmpty()) desc.append(out.title);
        if (!out.body.isEmpty()) { if (desc.length() > 0) desc.append('\n'); desc.append(out.body); }
        out.description = desc.length() == 0 ? null : desc.toString();
        String[] nameKeys = {"nickname", "nickName", "name", "userName"};
        for (String k : nameKeys) { String v = optNonBlank(user, k); if (!v.isEmpty()) { out.authorName = v; break; } }
        String[] idKeys = {"userId", "user_id", "id", "redId"};
        for (String k : idKeys) { String v = optNonBlank(user, k); if (!v.isEmpty()) { out.authorId = v; break; } }
        long time = note.optLong("time", 0);
        if (time > 0) { out.publishedAt = time; out.publishTime = String.valueOf(time); }
        else { String t = optNonBlank(note, "timeText"); if (!t.isEmpty()) out.publishTime = t; }
        JSONArray tags = note.optJSONArray("tagList");
        if (tags != null) for (int i = 0; i < tags.length(); i++) {
            JSONObject o = tags.optJSONObject(i);
            if (o != null && !o.optString("name").trim().isEmpty()) out.tags.add(o.optString("name").trim());
        }
        JSONObject interact = note.optJSONObject("interactInfo");
        if (interact != null) for (String k : new String[]{"likedCount", "collectedCount", "commentCount", "shareCount"}) {
            String v = interact.optString(k, "");
            if (!v.isEmpty()) out.interactions.put(k, v);
        }
        return out;
    }

    private static class Cand { String url; boolean watermarked; Cand(String u, boolean w) { url = u; watermarked = w; } }

    private static void mergeCandidates(List<Cand> out, List<Cand> add) {
        for (Cand c : add) {
            boolean dup = false;
            for (Cand o : out) if (o.url.equals(c.url)) { dup = true; break; }
            if (!dup) out.add(c);
        }
        // 排序对齐原版：original 优先、无水印优先
        java.util.Collections.sort(out, new java.util.Comparator<Cand>() {
            public int compare(Cand a, Cand b) { return Boolean.compare(a.watermarked, b.watermarked); }
        });
    }

    private static List<Cand> videoCandidates(JSONObject video) {
        List<Cand> out = new ArrayList<Cand>();
        if (video == null) return out;
        String key = null;
        JSONObject consumer = video.optJSONObject("consumer");
        if (consumer != null && consumer.optString("originVideoKey").trim().length() > 0) key = consumer.optString("originVideoKey").trim();
        if (key == null && video.optString("originVideoKey").trim().length() > 0) key = video.optString("originVideoKey").trim();
        if (key != null) {
            String original = isHttp(key) ? key : "https://sns-video-bd.xhscdn.com/" + trim(key, '/');
            out.add(new Cand(original, false));
        }
        addStreamCandidates(out, optObj(optObj(video, "media"), "stream"));
        addStreamCandidates(out, video.optJSONObject("stream"));
        return out;
    }

    private static void addStreamCandidates(List<Cand> out, JSONObject stream) {
        if (stream == null) return;
        java.util.Iterator<String> keys = stream.keys();
        while (keys.hasNext()) {
            String codec = keys.next();
            JSONArray array = stream.optJSONArray(codec);
            if (array == null) continue;
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) {
                    String s = array.optString(index);
                    if (isHttp(s)) out.add(new Cand(s, false));
                    continue;
                }
                JSONArray backups = item.optJSONArray("backupUrls");
                List<String> urls = new ArrayList<String>();
                if (backups != null) for (int i = 0; i < backups.length(); i++) urls.add(backups.optString(i));
                urls.add(item.optString("masterUrl"));
                urls.add(item.optString("url"));
                boolean wm = isWatermarkStream(item);
                for (String u : urls) if (isHttp(u)) {
                    boolean dup = false;
                    for (Cand o : out) if (o.url.equals(u)) { dup = true; break; }
                    if (!dup) out.add(new Cand(u, wm));
                }
            }
        }
    }

    /** 对齐原版：streamType 259/309 或 streamDesc WEB_LIVEPHOTO_19 视为带水印 */
    private static boolean isWatermarkStream(JSONObject item) {
        int st = item.optInt("streamType", 0);
        if (st == 259 || st == 309) return true;
        return "WEB_LIVEPHOTO_19".equalsIgnoreCase(item.optString("streamDesc"));
    }

    // ---------- __INITIAL_STATE__ 提取 ----------

    private static JSONObject parseInitialStateRoot(String html) {
        int start = html.indexOf("window.__INITIAL_STATE__");
        if (start < 0) return null;
        int end = html.indexOf("</script>", start);
        if (end < 0) return null;
        String script = html.substring(start, end);
        int eq = script.indexOf('=');
        if (eq < 0) return null;
        String snippet = script.substring(eq + 1).trim();
        String literal = extractFirstJsObjectLiteral(snippet);
        if (literal == null) literal = snippet;
        literal = trim(literal, ';').trim();
        literal = normalizeStateLiteral(literal);
        try { return new JSONObject(literal); }
        catch (Throwable t) { return null; }
    }

    private static String extractFirstJsObjectLiteral(String snippet) {
        boolean inString = false; char quote = 0; boolean escaped = false;
        int depth = 0, start = -1;
        for (int i = 0; i < snippet.length(); i++) {
            char c = snippet.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) inString = false;
                continue;
            }
            if (c == '\'' || c == '"') { inString = true; quote = c; continue; }
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { if (depth > 0 && --depth == 0 && start >= 0) return snippet.substring(start, i + 1); }
        }
        return null;
    }

    private static String normalizeStateLiteral(String input) {
        StringBuilder out = new StringBuilder(input.length());
        Character quote = null; boolean escaped = false; int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (quote != null) {
                if (c < 32) out.append(String.format("\\u%04x", (int) c));
                else out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = null;
                i++;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            if (input.startsWith("undefined", i) && !isJsIdent(charAt(input, i - 1)) && !isJsIdent(charAt(input, i + 9))) {
                out.append("null"); i += "undefined".length(); continue;
            }
            if (input.startsWith("new Map([])", i)) { out.append("[]"); i += "new Map([])".length(); continue; }
            if (c >= 32 || c == '\n' || c == '\r' || c == '\t') out.append(c);
            i++;
        }
        return out.toString();
    }

    private static char charAt(String s, int i) { return (i >= 0 && i < s.length()) ? s.charAt(i) : 0; }
    private static boolean isJsIdent(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static List<JSONObject> findNoteObjects(JSONObject root) {
        List<JSONObject> notes = new ArrayList<JSONObject>();
        Set<String> seenIds = new LinkedHashSet<String>();
        try {
            JSONObject noteRoot = root.optJSONObject("note");
            if (noteRoot != null) {
                JSONObject map = noteRoot.optJSONObject("noteDetailMap");
                if (map != null) {
                    java.util.Iterator<String> keys = map.keys();
                    while (keys.hasNext()) {
                        JSONObject entry = map.optJSONObject(keys.next());
                        if (entry != null) addCandidate(notes, seenIds, entry.optJSONObject("note"));
                    }
                } else {
                    JSONObject inner = noteRoot.optJSONObject("note");
                    if (inner != null) addCandidate(notes, seenIds, inner);
                    else {
                        JSONObject feed = noteRoot.optJSONObject("feed");
                        JSONArray items = feed == null ? null : feed.optJSONArray("items");
                        if (items != null) for (int i = 0; i < items.length(); i++) addCandidate(notes, seenIds, items.optJSONObject(i));
                        else addCandidate(notes, seenIds, noteRoot);
                    }
                }
            }
            JSONObject feed = root.optJSONObject("feed");
            JSONArray feedItems = feed == null ? null : feed.optJSONArray("items");
            if (feedItems != null) for (int i = 0; i < feedItems.length(); i++) addCandidate(notes, seenIds, feedItems.optJSONObject(i));
            JSONObject noteData = root.optJSONObject("noteData");
            if (noteData != null) {
                JSONObject data = noteData.optJSONObject("data");
                if (data != null) addCandidate(notes, seenIds, data.optJSONObject("noteData") != null ? data.optJSONObject("noteData") : data.optJSONObject("note"));
            }
            boolean hasLikely = false;
            for (JSONObject n : notes) if (isLikelyNoteObject(n)) { hasLikely = true; break; }
            if (notes.isEmpty() || !hasLikely) {
                ArrayDeque<Object> stack = new ArrayDeque<Object>();
                stack.add(root);
                int visited = 0;
                while (!stack.isEmpty() && visited < 50000 && notes.size() < 50) {
                    Object cur = stack.removeLast();
                    visited++;
                    if (cur instanceof JSONObject) {
                        JSONObject jo = (JSONObject) cur;
                        JSONObject innerNote = jo.optJSONObject("note");
                        if (innerNote != null) stack.add(innerNote);
                        if (isLikelyNoteObject(jo)) addCandidate(notes, seenIds, jo);
                        java.util.Iterator<String> keys = jo.keys();
                        while (keys.hasNext()) {
                            Object v = jo.opt(keys.next());
                            if (v instanceof JSONObject || v instanceof JSONArray) stack.add(v);
                        }
                    } else if (cur instanceof JSONArray) {
                        JSONArray ja = (JSONArray) cur;
                        for (int i = 0; i < ja.length(); i++) {
                            Object v = ja.opt(i);
                            if (v instanceof JSONObject || v instanceof JSONArray) stack.add(v);
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }
        return notes;
    }

    private static void addCandidate(List<JSONObject> notes, Set<String> seenIds, JSONObject note) {
        if (note == null || note.length() == 0) return;
        String id = note.optString("noteId");
        if (!id.isEmpty() && !seenIds.add(id)) return;
        notes.add(note);
    }

    private static boolean isLikelyNoteObject(JSONObject obj) {
        try {
            if (obj.has("noteId") && (obj.has("title") || obj.has("desc"))) return true;
            JSONArray imageArray = obj.optJSONArray("imageList");
            if (imageArray == null) imageArray = obj.optJSONArray("images");
            JSONObject image = imageArray == null ? null : imageArray.optJSONObject(0);
            if (image != null && (image.has("urlDefault") || image.has("url") || image.has("traceId") || image.has("infoList"))) return true;
            JSONObject video = obj.optJSONObject("video");
            return video != null && (video.has("consumer") || video.has("media"));
        } catch (Throwable t) { return false; }
    }

    // ---------- 小工具 ----------

    private static JSONObject optObj(JSONObject o, String k) { return o == null ? null : o.optJSONObject(k); }
    private static void enqueueAll(ArrayDeque<JSONObject> q, JSONArray arr) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) { JSONObject o = arr.optJSONObject(i); if (o != null) q.add(o); }
    }
    private static String optNonBlank(JSONObject o, String k) { String v = o == null ? "" : o.optString(k, ""); return v == null ? "" : v.trim(); }
    private static boolean isHttp(String v) { return v != null && (v.startsWith("https://") || v.startsWith("http://")); }
    private static String trim(String s, char c) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == c) a++;
        while (b > a && s.charAt(b - 1) == c) b--;
        return s.substring(a, b);
    }
}
