package com.ziyuan.Assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Pattern;

/** 本地 TXT 书。正文保留原始字节存内部文件(按字节偏移分页, 不能转码后存),
 *  元数据存 SharedPreferences。章节规则与分页依据反编译 com.example.txtreader.model.b 移植。 */
public class ReadBook {

    public String id, name, charset = "UTF-8";
    public int bom = 0;
    public long size, lastRead;
    public float progress;                 // 0..1, 当前字节偏移 / 总字节
    public byte[] data;                    // 运行时全文, 不持久化
    public ArrayList<Chapter> chapters = new ArrayList<Chapter>();

    public static class Chapter {
        public String title;
        public long offset;
        public Chapter(String t, long o) { title = t; offset = o; }
    }

    private static final String SP = "read_shelf";
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(SP, 0); }

    public static ArrayList<ReadBook> loadAll(Context c) {
        ArrayList<ReadBook> list = new ArrayList<ReadBook>();
        String ids = sp(c).getString("ids", "");
        if (ids.length() == 0) return list;
        for (String id : ids.split(",")) {
            if (id.length() == 0) continue;
            String j = sp(c).getString("b_" + id, null);
            if (j == null) continue;
            try {
                JSONObject o = new JSONObject(j);
                ReadBook b = new ReadBook();
                b.id = id;
                b.name = o.optString("name", "未命名");
                b.size = o.optLong("size", 0);
                b.progress = (float) o.optDouble("progress", 0);
                b.lastRead = o.optLong("last", 0);
                list.add(b);
            } catch (Throwable ignored) {}
        }
        Collections.sort(list, new Comparator<ReadBook>() {
            public int compare(ReadBook a, ReadBook b) { return Long.compare(b.lastRead, a.lastRead); }
        });
        return list;
    }

    public void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name); o.put("size", size); o.put("progress", progress);
            o.put("last", lastRead);
            SharedPreferences p = sp(c);
            String ids = p.getString("ids", "");
            boolean has = false;
            for (String s : ids.split(",")) if (id.equals(s)) { has = true; break; }
            if (!has) ids = ids.length() == 0 ? id : ids + "," + id;
            p.edit().putString("ids", ids).putString("b_" + id, o.toString()).apply();
        } catch (Throwable ignored) {}
    }

    public static void remove(Context c, ArrayList<String> ids) {
        SharedPreferences p = sp(c);
        StringBuilder keep = new StringBuilder();
        for (String s : p.getString("ids", "").split(",")) {
            if (s.length() == 0 || ids.contains(s)) continue;
            if (keep.length() > 0) keep.append(',');
            keep.append(s);
        }
        SharedPreferences.Editor e = p.edit().putString("ids", keep.toString());
        for (String id : ids) {
            e.remove("b_" + id);
            try { file(c, id).delete(); } catch (Throwable ignored) {}
        }
        e.apply();
    }

    private static File file(Context c, String id) {
        File d = new File(c.getFilesDir(), "readbooks");
        if (!d.exists()) d.mkdirs();
        return new File(d, id + ".txt");
    }

    public File file(Context c) { return file(c, id); }

    /** 导入: 原始字节原样落盘 */
    public void writeBytes(Context c, byte[] raw) {
        try {
            FileOutputStream f = new FileOutputStream(file(c));
            f.write(raw); f.close();
            data = raw; size = raw.length;
            detectCharset();
        } catch (Throwable ignored) {}
    }

    public boolean prepare(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return false;
            data = readAll(new FileInputStream(f));
            size = data.length;
            detectCharset();
            parseChapters();
            return true;
        } catch (Throwable e) { return false; }
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toByteArray();
    }

    /** BOM + UTF-8 严格校验 + GB18030 回退(覆盖 GBK/GB2312), 另识别无 BOM 的 UTF-16 */
    private void detectCharset() {
        bom = 0;
        if (data.length >= 3 && (data[0] & 0xff) == 0xEF && (data[1] & 0xff) == 0xBB && (data[2] & 0xff) == 0xBF) {
            charset = "UTF-8"; bom = 3; return;
        }
        if (data.length >= 2 && (data[0] & 0xff) == 0xFF && (data[1] & 0xff) == 0xFE) {
            charset = "UTF-16LE"; bom = 2; return;
        }
        if (data.length >= 2 && (data[0] & 0xff) == 0xFE && (data[1] & 0xff) == 0xFF) {
            charset = "UTF-16BE"; bom = 2; return;
        }
        int zeros = 0, even = 0, odd = 0, probe = Math.min(data.length, 4096);
        for (int i = 0; i < probe; i++) {
            if (data[i] == 0) { zeros++; if (i % 2 == 0) even++; else odd++; }
        }
        if (zeros > probe / 4) { charset = even > odd ? "UTF-16BE" : "UTF-16LE"; return; }
        try {
            CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            d.decode(ByteBuffer.wrap(data, bom, data.length - bom));
            charset = "UTF-8";
        } catch (Throwable e) {
            try { Charset.forName("GB18030"); charset = "GB18030"; }
            catch (Throwable e2) { charset = "GBK"; }
        }
    }

    public boolean isUtf16() { return charset.startsWith("UTF-16"); }

    /** 按字节偏移读一行(换行符算在 next 里); 最多 4000 字节, 复刻原版 b(J)[B */
    public Line lineAt(long start) {
        if (data == null || start < 0 || start >= data.length) return null;
        long p = start, lim = Math.min(data.length, start + 4000);
        boolean le = "UTF-16LE".equals(charset), be = "UTF-16BE".equals(charset);
        if (le || be) {
            while (p + 1 < lim) {
                if (le && (data[(int) p] == 0x0A) && (data[(int) p + 1] == 0)) { p += 2; break; }
                if (be && (data[(int) p] == 0) && (data[(int) p + 1] == 0x0A)) { p += 2; break; }
                p += 2;
            }
            if (p > data.length) p = data.length;
        } else {
            while (p < lim && data[(int) p] != 0x0A) p++;
            if (p < data.length && data[(int) p] == 0x0A) p++;
        }
        int len = (int) (p - start);
        String s;
        try { s = new String(data, (int) start, len, charset); }
        catch (Throwable e) { s = ""; }
        s = s.replace("\uFEFF", "").replace("\r", "").replace("\n", "");
        return new Line(s, p);
    }

    /** 反向找上一行的起点。复刻原版 j(I)[B: 起点处的换行(当前行尾)必须跳过,
     *  否则会原地返回导致往回翻页卡死。UTF-16 按 2 字节单元对齐。 */
    public long prevLineStart(long pos) {
        if (data == null || pos <= bom) return bom;
        boolean le = "UTF-16LE".equals(charset), be = "UTF-16BE".equals(charset);
        long step = (le || be) ? 2 : 1;
        long p = pos - step;
        boolean first = true;
        long lim = Math.max(bom, p - 4000);
        while (p >= bom && p > lim) {
            boolean nl;
            if (le) nl = p + 1 < data.length && data[(int) p] == 0x0A && data[(int) p + 1] == 0;
            else if (be) nl = p + 1 < data.length && data[(int) p] == 0 && data[(int) p + 1] == 0x0A;
            else nl = data[(int) p] == 0x0A;
            if (nl && !first) return p + step;
            first = false;
            p -= step;
        }
        return bom;
    }

    public static class Line {
        public String text;
        public long next;
        public Line(String t, long n) { text = t; next = n; }
    }

    /** 原版 model.b 的 8 条章节识别规则(1:1 移植) */
    private static final String[] CH_RULES = {
        "[^'’”“‘\",，。,.?]*第[\\u0391-\\uFFE50-9一二三四五六七八九十\\s]{1,5}[章|卷]{0,1}.*",
        ".*(Chapter|No)[\\u0391-\\uFFE50-9\\.]{4}.*",
        // 原版为 ".*楔子.*", 会把正文里含「楔子」的句子误判; 收紧为行首
        "^[\\s\\u3000]*(☆|楔子|参考书目|\\(\\d+\\)).*",
        "[^'’”“‘\",，。,.?？Mm月：:星期]{0,8}[0-9]{1,4}[^:：。!！…\\u4E00-\\u9FA50-9A-Za-z]*",
        "[['’”“‘\",，。,.?:：？\\u0391-\\uFFE5]|(章|卷)]{1,8}[一二三四五六七八九十]{1,4}[、.:：\\s]+.*",
        ".*番外[^\\u4E00-\\u9FA5]*",
        ".*Part[\\u0391-\\uFFE50-9].*",
        "[\\\\s]*(上册|尾声|下册|序言|前言|后记|正文).*",
        // 原版 8 条的补宽容版: 常见「Chapter 12 标题」「番外 特别篇」带空格/后接中文
        "(?i)^[\\s\\u3000]*(Chapter|No)\\.?[\\s]*[0-9]+.*",
        "^[\\s\\u3000]*番外[^\\n]{0,24}$"
    };
    private static final Pattern[] CH_PATTERNS = buildPatterns();

    private static Pattern[] buildPatterns() {
        Pattern[] a = new Pattern[CH_RULES.length];
        for (int i = 0; i < CH_RULES.length; i++) {
            try { a[i] = Pattern.compile(CH_RULES[i]); }
            catch (Throwable e) { a[i] = Pattern.compile("$.^"); }
        }
        return a;
    }

    public static boolean isChapterLine(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() == 0 || t.length() >= 30) return false;
        for (Pattern p : CH_PATTERNS) if (p.matcher(t).matches()) return true;
        return false;
    }

    /** 章节目录: 顺序扫描全文按字节偏移记录 */
    public void parseChapters() {
        chapters.clear();
        if (data == null) return;
        long off = bom;
        int guard = 0;
        while (off < data.length && guard++ < 400000) {
            Line ln = lineAt(off);
            if (ln == null || ln.next <= off) break;
            String t = ln.text.trim();
            if (isChapterLine(t)) {
                if (t.length() > 40) t = t.substring(0, 40);
                chapters.add(new Chapter(t, off));
            }
            off = ln.next;
        }
    }

    public int chapterAt(long byteOffset) {
        int idx = 0;
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).offset <= byteOffset) idx = i; else break;
        return idx;
    }

    public static String displayName(Context c, Uri u) {
        String n = null;
        try {
            Cursor cur = c.getContentResolver().query(u, null, null, null, null);
            if (cur != null) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && cur.moveToFirst()) n = cur.getString(i);
                cur.close();
            }
        } catch (Throwable ignored) {}
        if (n == null || n.length() == 0) {
            n = u.getLastPathSegment();
            if (n == null) n = "未命名";
        }
        if (n.toLowerCase().endsWith(".txt")) n = n.substring(0, n.length() - 4);
        return n;
    }
}
