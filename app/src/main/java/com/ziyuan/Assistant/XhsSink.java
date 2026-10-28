package com.ziyuan.Assistant.xhs;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** 存储落盘（targetSdk 36 无全局写权限 → 默认走 MediaStore；自定义目录仍走 File） */
public final class XhsSink {

    private XhsSink() {}

    public static String mimeOf(String ext) {
        if (ext.endsWith(".png")) return "image/png";
        if (ext.endsWith(".webp")) return "image/webp";
        if (ext.endsWith(".gif")) return "image/gif";
        if (ext.endsWith(".mp4") || ext.endsWith(".mov")) return "video/mp4";
        return "image/jpeg";
    }

    /** MediaStore 是否已有同名文件（checkExisting 用） */
    public static boolean existsStore(Context c, boolean isVideo, String relDir, String name) {
        if (Build.VERSION.SDK_INT < 29) {
            File f = new File(publicDir(isVideo), relDir + "/" + name);
            return f.exists();
        }
        try {
            Uri coll = collection(isVideo);
            android.database.Cursor cur = c.getContentResolver().query(coll,
                    new String[]{MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME
                            + "=? AND " + MediaStore.MediaColumns.IS_PENDING + "=0",
                    new String[]{relDir + "/", name}, null);
            boolean hit = cur != null && cur.moveToFirst();
            if (cur != null) cur.close();
            return hit;
        } catch (Throwable t) { return false; }
    }

    /** MediaStore 流式下载 → 返回 content:// uri 字符串 */
    public static String saveStore(Context c, boolean isVideo, String relDir, String name,
                                   String url, XhsNet.Progress cb, AtomicBoolean cancel) throws Exception {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name));
        if (Build.VERSION.SDK_INT >= 29) {
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relDir);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri coll = collection(isVideo);
        final Uri uri = c.getContentResolver().insert(coll, cv);
        if (uri == null) throw new Exception("MediaStore insert failed");
        try {
            streamTo(c, c.getContentResolver().openOutputStream(uri), url, cb, cancel);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                c.getContentResolver().update(uri, done, null, null);
            }
            try { android.media.MediaScannerConnection.scanFile(c, new String[]{name}, new String[]{mimeOf(name)}, null); } catch (Throwable ignored) { }
        } catch (Throwable t) {
            try { c.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { }
            if (t instanceof Exception) throw (Exception) t;
            throw new Exception(t.getMessage());
        }
        return uri.toString();
    }

    /** 直接 File 写盘（自定义目录模式，多线程分块 + 已有 part 续传退单线程） */
    public static void saveFile(File dest, String url, XhsNet.Progress cb, AtomicBoolean cancel) throws Exception {
        File part = new File(dest.getParentFile(), dest.getName() + ".part");
        if (part.exists() && part.length() > 0) {
            XhsNet.download(url, dest, "https://www.xiaohongshu.com/", cb, cancel); // 续传走单线程
        } else {
            XhsNet.downloadMT(url, dest, "https://www.xiaohongshu.com/", 8, cb, cancel);
        }
    }

    private static Uri collection(boolean isVideo) {
        return isVideo ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                       : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    private static File publicDir(boolean isVideo) {
        return new File(android.os.Environment.getExternalStoragePublicDirectory(
                isVideo ? android.os.Environment.DIRECTORY_MOVIES
                        : android.os.Environment.DIRECTORY_PICTURES), "XHS下载");
    }

    private static void streamTo(final Context c, OutputStream out, String url, XhsNet.Progress cb, AtomicBoolean cancel) throws Exception {
        // 多线程下载到缓存临时文件，再拷贝进 MediaStore（MediaStore 不支持随机写）
        java.io.File tmp = java.io.File.createTempFile("xhs", ".part", c.getCacheDir());
        try {
            XhsNet.downloadMT(url, tmp, "https://www.xiaohongshu.com/", 8, cb, cancel);
            java.io.InputStream in = new java.io.FileInputStream(tmp);
            byte[] buf = new byte[32768];
            int k;
            while ((k = in.read(buf)) > 0) {
                if (cancel != null && cancel.get()) { in.close(); throw new java.io.IOException("cancelled"); }
                out.write(buf, 0, k);
            }
            in.close();
        } finally {
            tmp.delete();
            try { out.close(); } catch (Throwable ignored) { }
        }
    }
}
