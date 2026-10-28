package com.ziyuan.Assistant.xhs;

/** 自定义命名模板（对齐原版 NamingFormat）：{title}({username})_{publishTime} 等 */
public final class XhsNaming {
    public static final String DEFAULT_TEMPLATE = "{title}({username})_{publishTime}";

    /** 模板占位符替换；非法字符清洗与截断对齐原版 NoteOutput */
    public static String apply(String template, Note n, int index, long downloadTimestamp) {
        if (template == null || template.trim().isEmpty()) template = DEFAULT_TEMPLATE;
        String publishTime = n.publishTime == null ? "" : n.publishTime;
        String out = template
                .replace("{title}", safe(n.title))
                .replace("{username}", safe(n.authorName))
                .replace("{userId}", safe(n.authorId))
                .replace("{postId}", safe(n.noteId))
                .replace("{publishTime}", publishTime)
                .replace("{index}", String.valueOf(index))
                .replace("{index_padded}", String.format("%02d", index))
                .replace("{downloadTimestamp}", String.valueOf(downloadTimestamp));
        return sanitize(out);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static String sanitize(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ("\\/:*?\"<>|\n\r\t".indexOf(c) >= 0) continue;
            if (Character.isISOControl(c)) continue;
            sb.append(c);
        }
        String out = sb.toString().trim();
        if (out.length() > 100) out = out.substring(0, 100);
        return out; // 空回退交给上层（原版无 untitled）
    }

    /** 笔记元数据（命名所需字段） */
    public static class Note {
        public String title, authorName, authorId, noteId, publishTime;
        public Note(String t, String u, String uid, String id, String pt) {
            title = t; authorName = u; authorId = uid; noteId = id; publishTime = pt;
        }
    }
}
