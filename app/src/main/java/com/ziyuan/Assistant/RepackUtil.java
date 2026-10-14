package com.ziyuan.Assistant;

import android.content.Context;

import java.io.*;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.zip.*;

/** 模板APK改包 + v1/v2/v3签名 (纯Java) */
public class RepackUtil {

    static final String KEY_B64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQChyCZivclXmQTbUa0z272NontZVCkJvA4LHgXIB0Xa0IgMOhk9Sh7XjACyisd7XvDmlrJ4HgOtMqb5HvQ5ej/Hr5yrZA/OMRLEnP26ZX/bHHGNlz3vo5VIMH/pI1vFxMMAB4fZEJLD5x894YtEhUHu6UFma19GCVD5+xMkZM5B5Nkjn8gjkLMI+9NlS4NM0b8fCqZDfEez3bayI28GdRad5mRun3SS16MIC2fqwc6M7CrtC8lbpDO5oNB4hAEHHo6prj4nK8y3E0w4vHDWGskmii/jWVGKA+Y3O3d6UoPlD/WsNsNbmTKXWdrJbrF7yvKeMHQFXAbs3cL+1OCf3pWzAgMBAAECggEABqC+C4+yfcQFBrQMtNtBbLVBMAAxUMkjUd0ip+2ZfhvK06X7Hk0vt5Hwa/Jp/5whNAJapUnbybN/U0niSR1lZD5FyjwLJEuch0yNOWgPQ0Ah2fSnMtx+J/Z3jw+F2cX9XSwEGGZoopRsNuImsbPgwo8DHGdr27ycr+JBLLkF9MalWihu8hVn0viGVNAgGJ8KTW48sYQAJ3j00jOUOXVmAr2VNhJCMwqDTw8pOK1vF8t1uFxtkzwvKrRVWL4kOe+OW35ketlVNUeOzFP08eY+sa2N4iH1gTpss9fupC3U4UINVwEH5+h18x5M/SFFzKpS17nj3wHgnPEpACAM1PkFXQKBgQDMIeGMkyILaLdz1n7gHMgU/jy6XZ4PhzZiulh+u+18lim0X9eSZalMFEyP3miqjYQrhS86+lPc5WT9IXTN9I1RXQ35wHoSpj4/a+laJLeDLH7/vPxGkJqogoPu55Q9bVqwLyfpjOTrWuYW9E7K4REgLNW0qbJFRW0xZnihd9NBDQKBgQDK44LjwvW+PC5DyxHTw43J7d1nTrC//Dhsze5ocfweYsqXsguInfaP9LqkcKUx6QBf3AjblsBlGdELrRV+z8NwX5OG+mCfNUG2AtkxLBhO27FfRHza8T7HACMglHEQ1wjMu6Gxn94TAzFfbo+NVpazE1/kzh+8qKO6V6mcfgABvwKBgQCryRd2pZtQ3p8D7M5467+1av7QF1ic2Lz7+LXgcWY6ImSWVtGjcco3LB4CNLkATneb6EkG6QMKMkH6g904NUV340ePllsktqJL3RRTt/J3gUqfDPuAptCAXtWIh3pI924KNqTv9pHvPl/DkXV9ZkpzOUfe8bAEe5iYqhN6TsyJQQKBgHUrXvPaK8FpT+6m3+zECqaLUEnREBJPvuZXJ0/6Q/foZcelczO6xiHs270LsNtaDHVPxW3LaeD5P9jWZIuPwPasu64/+nz3bLOIgZX8OS2RgqXaD6EBoZebZK30DDgFd3eFBKRoBwBD38eVOiIN14ojrkWdJIb3fVaoObO+sJJRAoGAUB+XJU4a/TAwmGh8JuON83U3ihT/hrgpdQzJxTxJSbmyXd1IudJIlV+3TPQ7RetlMxSS6Re8flJaHsMBfNufriKVIRKOPmSz3dLR8zi1LkQHIRu9+40c+ax2zFIpuT0E61gd2BmURsS9fIyNzIhyx1m2UHcoYRukpyQApBVdEEQ=";
    static final String CERT_B64 = "MIIDBzCCAe+gAwIBAgIUaEMCLhzDxwfniD7/HUry+Ugnhp0wDQYJKoZIhvcNAQELBQAwEjEQMA4GA1UEAwwHd2ViMmFwazAgFw0yNjEwMDkwMTM4MjNaGA8yMDUxMTAwMzAxMzgyM1owEjEQMA4GA1UEAwwHd2ViMmFwazCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKHIJmK9yVeZBNtRrTPbvY2ie1lUKQm8DgseBcgHRdrQiAw6GT1KHteMALKKx3te8OaWsngeA60ypvke9Dl6P8evnKtkD84xEsSc/bplf9sccY2XPe+jlUgwf+kjW8XEwwAHh9kQksPnHz3hi0SFQe7pQWZrX0YJUPn7EyRkzkHk2SOfyCOQswj702VLg0zRvx8KpkN8R7PdtrIjbwZ1Fp3mZG6fdJLXowgLZ+rBzozsKu0LyVukM7mg0HiEAQcejqmuPicrzLcTTDi8cNYaySaKL+NZUYoD5jc7d3pSg+UP9aw2w1uZMpdZ2slusXvK8p4wdAVcBuzdwv7U4J/elbMCAwEAAaNTMFEwHQYDVR0OBBYEFCu/aWrSRoPHvszy1lWVxAS1lxXwMB8GA1UdIwQYMBaAFCu/aWrSRoPHvszy1lWVxAS1lxXwMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBACMtAHl00FNpyldZ3HKDgW/e8UEWZDX9w/3WwBuS4kUX9CzpY9aWnfGFojA7MO6zAgoL48QtU+kKnvHouKfYn/4oMmirkzs6XCovcVZa89VfOumgNqkTcjn3pggIorfWwiFalREo+cwfFOicV1mdVIwMrHudtBrdcDVJ1pxRaclqVQ2JkGE3obp2/K444N2V2a9xD3XLMsgc1XTrhFMFiDepV4XjhKQEaqSa7d9vEVlijZuwZmnZbdQ7l7z6sgsdB0IeiXVPxeieSfn0ZWMVwj8uYjLNAP6GoSE1L2erxcpKSq8ZGV1gscF+quY/zjQJ5t7azODXAoYun1z0IxK1lrs=";

    static PrivateKey privKey;
    static X509Certificate cert;

    static void initKeys() throws Exception {
        byte[] kb = Base64.getMimeDecoder().decode(KEY_B64);
        byte[] cb = Base64.getMimeDecoder().decode(CERT_B64);
        privKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(kb));
        cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cb));
    }

    // ================= DER helpers =================
    static byte[] cat(byte[]... a) { int t=0; for (byte[] b:a) t+=b.length; byte[] o=new byte[t]; int p=0; for (byte[] b:a){System.arraycopy(b,0,o,p,b.length);p+=b.length;} return o; }
    static byte[] len(int n) { n&=0xFFFFFF; if (n<0x80) return new byte[]{(byte)n}; if (n<0x100) return new byte[]{(byte)0x81,(byte)n}; return new byte[]{(byte)0x82,(byte)((n>>8)&0xFF),(byte)(n&0xFF)}; }
    static byte[] seq(byte[]... it) { byte[] b=cat(it); return cat(new byte[]{0x30},len(b.length),b); }
    static byte[] set(byte[]... it) { byte[] b=cat(it); return cat(new byte[]{0x31},len(b.length),b); }
    static byte[] ctx0(byte[] b) { byte[] l=len(b.length); return cat(new byte[]{(byte)0xA0},l,b); }
    static byte[] oct(byte[] b) { byte[] l=len(b.length); return cat(new byte[]{0x04},l,b); }
    static byte[] i32b(int v) { return new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v}; }
    static byte[] i32l(int v) { return new byte[]{(byte)v,(byte)(v>>>8),(byte)(v>>>16),(byte)(v>>>24)}; }
    static byte[] derInt(int v) {
        if (v >= 0 && v < 0x80) return new byte[]{0x02, 0x01, (byte) v};
        java.util.List<Byte> b = new ArrayList<>();
        boolean started = false;
        for (int i = 31; i >= 0; i -= 8) {
            int byteV = (v >>> i) & 0xFF;
            if (!started && byteV == 0 && i > 0) continue;
            started = true;
            b.add((byte) byteV);
        }
        if (!started) b.add((byte) 0);
        byte[] a = new byte[b.size()];
        for (int i = 0; i < b.size(); i++) a[i] = b.get(i);
        return cat(new byte[]{0x02}, len(a.length), a);
    }
    static byte[] oid(String s) {
        String[] p=s.split("\\."); List<Byte> b=new ArrayList<>();
        b.add((byte)(Integer.parseInt(p[0])*40+Integer.parseInt(p[1])));
        for (int i=2;i<p.length;i++){
            int v=Integer.parseInt(p[i]);
            if (v==0) { b.add((byte)0); continue; }
            java.util.Deque<Byte> st=new ArrayDeque<>();
            while (v>0){ st.push((byte)(v&0x7F)); v>>=7; }
            while (st.size()>1) b.add((byte)(0x80|st.pop()));
            b.add(st.pop());
        }
        byte[] a=new byte[b.size()]; for (int i=0;i<b.size();i++) a[i]=b.get(i);
        return cat(new byte[]{0x06},len(a.length),a);
    }
    static byte[] OID_DATA = oid("1.2.840.113549.1.7.1");
    static byte[] OID_SHA256RSA = oid("1.2.840.113549.1.1.11");
    static byte[] OID_SHA256 = oid("2.16.840.1.101.3.4.2.1");
    static byte[] OID_CT = oid("1.2.840.113549.1.9.3");
    static byte[] OID_TIME = oid("1.2.840.113549.1.9.5");
    static byte[] OID_MSGDIG = oid("1.2.840.113549.1.9.4");
    static byte[] sha256(byte[] d) throws Exception { return MessageDigest.getInstance("SHA-256").digest(d); }
    static byte[] utctime() throws Exception {
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        byte[] b=f.format(new java.util.Date()).getBytes(StandardCharsets.US_ASCII);
        return cat(new byte[]{0x17},len(b.length),b);
    }

    // ================= PKCS7 CERT.RSA =================
    static byte[] pkcs7(byte[] sf) throws Exception {
        byte[] contentInfo = seq(OID_DATA, ctx0(new byte[0]));
        byte[] aaBody = cat(
                seq(OID_CT, set(OID_DATA)),
                seq(OID_TIME, set(utctime())),
                seq(OID_MSGDIG, set(oct(sha256(sf)))));
        byte[] aa = ctx0(aaBody);
        Signature sg = Signature.getInstance("SHA256withRSA");
        sg.initSign(privKey); sg.update(aa);
        byte[] sigv = sg.sign();
        byte[] iss = cert.getIssuerX500Principal().getEncoded();
        byte[] ser = cert.getSerialNumber().toByteArray();
        if (ser[0] < 0) ser = cat(new byte[]{0}, ser);
        byte[] iasn = seq(cat(new byte[]{0x30},len(iss.length),iss, new byte[]{0x02},len(ser.length),ser));
        byte[] signerInfo = seq(derInt(1), iasn, seq(OID_SHA256RSA, new byte[]{0x05,0x00}), aa, seq(OID_SHA256RSA, new byte[]{0x05,0x00}), oct(sigv));
        byte[] c = cert.getEncoded();
        byte[] certs = ctx0(cat(new byte[]{0x30},len(c.length),c));
        byte[] sd = seq(derInt(1), set(seq(OID_SHA256, new byte[]{0x05,0x00})), contentInfo, certs, set(signerInfo));
        byte[] OID_SIGNEDDATA = oid("1.2.840.113549.1.7.2");
        return seq(OID_SIGNEDDATA, ctx0(sd));
    }

    // ================= AXML 字符串池补丁 =================
    static byte[] patchAxml(byte[] xml, Map<String,String> rep) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN);
        int poolSize = bb.getInt(12);
        int strCount = bb.getInt(16);
        int flags = bb.getInt(24);
        int stringsStart = bb.getInt(28);
        boolean utf8 = (flags & 0x100) != 0;
        int[] offs = new int[strCount];
        for (int i=0;i<strCount;i++) offs[i]=bb.getInt(28+4+4+i*4);
        List<String> strs = new ArrayList<>();
        System.err.println("pool utf8=" + utf8 + " count=" + strCount + " stringsStart=" + stringsStart + " poolSize=" + poolSize);
        for (int i=0;i<strCount;i++) {
            int off = 8 + stringsStart + offs[i];
            System.err.println("  off[" + i + "]=" + offs[i]);
            if (utf8) {
                int p = off;
                int u16 = xml[p]&0xFF; p++;
                if ((u16&0x80)!=0) { u16 = ((u16&0x7F)<<8)|(xml[p]&0xFF); p++; }
                int u8 = xml[p]&0xFF; p++;
                if ((u8&0x80)!=0) { u8 = ((u8&0x7F)<<8)|(xml[p]&0xFF); p++; }
                strs.add(new String(xml,p,u8,StandardCharsets.UTF_8));
            } else {
                int p = off;
                int u16 = (xml[p]&0xFF)|((xml[p+1]&0xFF)<<8); p+=2;
                if ((u16&0x8000)!=0) { u16=((u16&0x7FFF)<<16)|((xml[p]&0xFF)|((xml[p+1]&0xFF)<<8)); p+=2; }
                strs.add(new String(xml,p,u16*2,StandardCharsets.UTF_16LE));
            }
        }
        for (int i=0;i<strs.size();i++){ String r=rep.get(strs.get(i)); if (r!=null) strs.set(i,r); }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] no = new int[strCount];
        for (int i=0;i<strCount;i++) {
            no[i]=data.size();
            String t=strs.get(i);
            if (utf8) {
                byte[] u=t.getBytes(StandardCharsets.UTF_8);
                byte[] w=t.getBytes(StandardCharsets.UTF_16LE);
                int u16=w.length/2;
                if (u16>0x7F){data.write(0x80|(u16>>8));data.write(u16&0xFF);}else data.write(u16);
                if (u.length>0x7F){data.write(0x80|(u.length>>8));data.write(u.length&0xFF);}else data.write(u.length);
                data.write(u); data.write(0);
            } else {
                byte[] w=t.getBytes(StandardCharsets.UTF_16LE);
                int u16=w.length/2;
                data.write(u16&0xFF); data.write((u16>>8)&0xFF);
                data.write(w); data.write(0); data.write(0);
            }
        }
        while (data.size()%4!=0) data.write(0);
        byte[] da=data.toByteArray();
        int newStringsStart = 28 + strCount*4;
        int newPoolSize = newStringsStart + da.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(xml,0,4);
        out.write(i32l((int)(bb.getInt(4)&0xFFFFFFFFL) + (newPoolSize-poolSize)));
        out.write(new byte[]{0x01,0x00,0x1C,0x00});
        out.write(i32l(newPoolSize));
        out.write(i32l(strCount));
        out.write(i32l(0));
        out.write(i32l(flags));
        out.write(i32l(newStringsStart));
        out.write(i32l(0));
        for (int i=0;i<strCount;i++) out.write(i32l(no[i]));
        out.write(da);
        out.write(xml, 8+poolSize, xml.length-8-poolSize);
        return out.toByteArray();
    }

    static byte[] patchArscPkg(byte[] a, String pkg) throws Exception {
        byte[] oldU = "com.wink.webshell".getBytes(StandardCharsets.UTF_16LE);
        byte[] newU = pkg.getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[oldU.length];
        System.arraycopy(newU, 0, out, 0, newU.length);
        for (int k = newU.length; k < oldU.length; k++) out[k] = 0;
        for (int i = 0; i + oldU.length <= a.length; i++) {
            boolean m = true;
            for (int k = 0; k < oldU.length; k++) if (a[i+k] != oldU[k]) { m = false; break; }
            if (m) { System.arraycopy(out, 0, a, i, out.length); i += oldU.length - 1; }
        }
        return a;
    }

    // ================= ZIP =================
    static class Ent { String name; byte[] data; boolean stored; }
    static List<Ent> readZip(File f) throws Exception {
        List<Ent> out=new ArrayList<>();
        ZipInputStream zin=new ZipInputStream(new FileInputStream(f));
        ZipEntry e;
        while ((e=zin.getNextEntry())!=null) {
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            byte[] buf=new byte[8192]; int n;
            while ((n=zin.read(buf))>0) b.write(buf,0,n);
            Ent en=new Ent(); en.name=e.getName(); en.data=b.toByteArray(); en.stored=(e.getMethod()==ZipEntry.STORED);
            out.add(en);
        }
        zin.close();
        return out;
    }

    static byte[] defl(byte[] d) throws Exception {
        ByteArrayOutputStream db=new ByteArrayOutputStream();
        Deflater def=new Deflater(9,true);
        def.setInput(d); def.finish();
        byte[] buf=new byte[8192];
        while (!def.finished()){int n=def.deflate(buf);db.write(buf,0,n);}
        def.end();
        return db.toByteArray();
    }

    // ================= 主入口 =================

    public static void signWithApksig(File inApk, File outApk) throws Exception {
        byte[] kb = Base64.getMimeDecoder().decode(KEY_B64);
        byte[] cb = Base64.getMimeDecoder().decode(CERT_B64);
        java.security.PrivateKey pk = java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(kb));
        java.security.cert.X509Certificate ct = (java.security.cert.X509Certificate)
                java.security.cert.CertificateFactory.getInstance("X.509")
                        .generateCertificate(new java.io.ByteArrayInputStream(cb));
        com.android.apksig.ApkSigner.SignerConfig sc = new com.android.apksig.ApkSigner.SignerConfig.Builder(
                "web2apk", pk, java.util.Collections.singletonList(ct)).build();
        com.android.apksig.ApkSigner signer = new com.android.apksig.ApkSigner.Builder(
                java.util.Collections.singletonList(sc))
                .setInputApk(inApk)
                .setOutputApk(outApk)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build();
        signer.sign();
    }

    public static void buildFromAssets(Context ctx, String url, String label, byte[] icon, String pkg, File outFile) throws Exception {
        android.content.SharedPreferences cf = ctx.getSharedPreferences("web2apk_cfg", Context.MODE_PRIVATE);
        String json = "{\"app_name\":\"" + label.replace("\"", "") + "\",\"url\":\"" + url.replace("\"", "") +
                "\",\"night\":" + ("1".equals(cf.getString("暗黑模式", "0")) ? 1 : 0) +
                ",\"js\":1" +
                ",\"hide_nav\":" + ("1".equals(cf.getString("隐藏导航栏", "0")) ? 1 : 0) +
                ",\"hide_status\":" + ("1".equals(cf.getString("沉浸式状态栏", "0")) ? 1 : 0) +
                ",\"user_slide\":" + ("1".equals(cf.getString("用户滑动", "1")) ? 1 : 0) +
                ",\"hide\":" + ("1".equals(cf.getString("隐藏", "0")) ? 1 : 0) +
                ",\"ua_desktop\":" + ("1".equals(cf.getString("浏览器标识", "0")) ? 1 : 0) + "}";
        java.io.InputStream is = ctx.getAssets().open("web2apk_template.apk");
        File tpl = new File(ctx.getCacheDir(), "web2apk_template.apk");
        FileOutputStream to = new FileOutputStream(tpl);
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) > 0) to.write(buf, 0, n);
        is.close(); to.close();
        File unsigned = new File(ctx.getCacheDir(), "web2apk_unsigned.apk");
        if (pkg == null || pkg.length() == 0) pkg = toPkg(ctx, label);
        build(tpl, unsigned, label, json, icon, pkg);
        signWithApksig(unsigned, outFile);
    }

    // 拼音表来自 assets/pinyindb/unicode_to_hanyu_pinyin.txt（pinyin4j 的表,R8 只带类不带资源,真机查表必挂,故自读）
    static final java.util.HashMap<String, String> PYDB = new java.util.HashMap<>();
    static void loadPyDB(Context ctx) throws Exception {
        if (!PYDB.isEmpty()) return;
        java.io.InputStream is = ctx.getAssets().open("pinyindb/unicode_to_hanyu_pinyin.txt");
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
        String ln;
        while ((ln = br.readLine()) != null) {
            int sp = ln.indexOf(' ');
            if (sp < 1) continue;
            String key = ln.substring(0, sp).trim();
            String val = ln.substring(sp).replace("(", "").replace(")", "").trim();
            String[] parts = val.split(",");
            if (parts.length > 0 && !parts[0].startsWith("none")) {
                String p = parts[0];
                if (p.length() > 1 && Character.isDigit(p.charAt(p.length() - 1))) p = p.substring(0, p.length() - 1);
                PYDB.put(key, p.replace("ü", "v"));
            }
        }
        br.close();
    }
    static String toPkg(Context ctx, String label) {
        try { loadPyDB(ctx); } catch (Throwable ignore) { }
        StringBuilder sb = new StringBuilder();
        for (char ch : label.toCharArray()) {
            String py = PYDB.get(String.format("%04X", (int) ch));
            if (py != null && py.length() > 0) { sb.append(py); continue; }
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'))
                sb.append(Character.toLowerCase(ch));
        }
        String s = sb.toString();
        if (s.length() == 0) s = "app";
        if (s.charAt(0) >= '0' && s.charAt(0) <= '9') s = "a" + s;
        if (s.length() > 24) s = s.substring(0, 24);
        return "com." + s + ".web";
    }

    static String toPkg(String label) { // 无Context兜底: 仅ASCII
        StringBuilder sb = new StringBuilder();
        for (char ch : label.toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'))
                sb.append(Character.toLowerCase(ch));
        }
        String s = sb.toString();
        if (s.length() == 0) s = "app";
        if (s.charAt(0) >= '0' && s.charAt(0) <= '9') s = "a" + s;
        if (s.length() > 24) s = s.substring(0, 24);
        return "com." + s + ".web";
    }

    static void build(File template, File outFile, String label, String json, byte[] icon, String pkg) throws Exception {
        initKeys();
        List<Ent> ents = readZip(template);
        Map<String,String> rep = new HashMap<>();
        rep.put("xapk", label);
        if (pkg == null || pkg.length() == 0) pkg = toPkg(label);
        rep.put("com.wink.webshell", pkg);
        LinkedHashMap<String,byte[]> files = new LinkedHashMap<>();
        for (Ent e : ents) {
            if (e.name.startsWith("META-INF/")) continue;
            byte[] d = e.data;
            if (e.name.equals("AndroidManifest.xml")) d = patchAxml(d, rep);
            else if (e.name.endsWith(".arsc")) d = patchArscPkg(d, pkg);
            else if (e.name.equals("assets/site.json")) d = json.getBytes(StandardCharsets.UTF_8);
            else if (icon != null && e.name.contains("ic_launcher")) d = icon;
            files.put(e.name, d);
        }

        // ---- 写zip ----
        ByteArrayOutputStream zo = new ByteArrayOutputStream();
        List<int[]> meta = new ArrayList<>(); // method, dataOff, compLen
        List<String> order = new ArrayList<>(files.keySet());
        for (String name : order) {
            byte[] d = files.get(name);
            boolean stored = name.endsWith(".arsc");
            int method = stored ? ZipEntry.STORED : ZipEntry.DEFLATED;
            byte[] payload = stored ? d : defl(d);
            int pos = zo.size();
            byte[] nb = name.getBytes(StandardCharsets.UTF_8);
            int extra = 0;
            if (stored) {
                int dataStart = pos + 30 + nb.length;
                extra = (4 - (dataStart % 4)) % 4;
            }
            CRC32 c = new CRC32(); c.update(d);
            zo.write(i32l(0x04034b50));
            zo.write(new byte[]{0x14, 0});            // version needed
            zo.write(new byte[]{0, 0});               // flags
            zo.write(new byte[]{(byte) method, (byte) (method >> 8)}); // method
            zo.write(new byte[]{0, 0});               // time
            zo.write(new byte[]{0, 0});               // date
            zo.write(i32l((int) c.getValue()));
            zo.write(i32l(payload.length));
            zo.write(i32l(d.length));
            zo.write(new byte[]{(byte)nb.length, 0});
            zo.write(new byte[]{(byte)(extra&0xFF),(byte)((extra>>8)&0xFF)});
            zo.write(nb);
            byte[] ex = new byte[extra];
            zo.write(ex);
            zo.write(payload);
            meta.add(new int[]{method, pos, payload.length});
        }
        int cdStart = zo.size();
        ByteArrayOutputStream cd = new ByteArrayOutputStream();
        for (int i=0;i<order.size();i++) {
            String name = order.get(i);
            byte[] d = files.get(name);
            int[] mi = meta.get(i);
            byte[] nb = name.getBytes(StandardCharsets.UTF_8);
            CRC32 c = new CRC32(); c.update(d);
            cd.write(i32l(0x02014b50));
            cd.write(new byte[]{0x14, 0});            // version made by
            cd.write(new byte[]{0x14, 0});            // version needed
            cd.write(new byte[]{0, 0});               // flags
            cd.write(new byte[]{(byte) mi[0], (byte) (mi[0] >> 8)}); // method
            cd.write(new byte[]{0, 0});               // time
            cd.write(new byte[]{0, 0});               // date
            cd.write(i32l((int) c.getValue()));       // crc
            cd.write(i32l(mi[2]));                    // compressed size
            cd.write(i32l(d.length));                 // uncompressed size
            cd.write(new byte[]{(byte) nb.length, 0});// name len
            cd.write(new byte[]{0, 0});               // extra len
            cd.write(new byte[]{0, 0});               // comment len
            cd.write(new byte[]{0, 0});               // disk start
            cd.write(new byte[]{0, 0});               // internal attrs
            cd.write(i32l(0));                        // external attrs
            cd.write(i32l(mi[1]));                    // local offset
            cd.write(nb);
        }
        byte[] cdB = cd.toByteArray();
        int cdSize = cdB.length;
        ByteArrayOutputStream eo = new ByteArrayOutputStream();
        eo.write(i32l(0x06054b50));
        eo.write(new byte[]{0,0,0,0});
        eo.write(new byte[]{(byte)order.size(),0,(byte)order.size(),0});
        eo.write(i32l(cdSize));
        eo.write(i32l(cdStart));
        eo.write(new byte[]{0,0});
        byte[] eocdRaw = eo.toByteArray();
        zo.write(cdB);
        zo.write(eocdRaw);
        byte[] zip0 = zo.toByteArray();

        byte[] signed = signV2V3(zip0, cdStart, cdSize, cdB, eocdRaw, order.size());
        FileOutputStream fo = new FileOutputStream(outFile);
        fo.write(signed); fo.close();
    }

    // ---- V2/V3 signing block ----
    static byte[] sectionDigest(byte[] data, int off, int size) throws Exception {
        int chunk = 1048576;
        int count = (size + chunk - 1) / chunk;
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            int st = off + i * chunk;
            int ln = Math.min(chunk, size - i * chunk);
            byte[] h = cat(new byte[]{(byte)0xa5}, i32l(ln));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(h);
            md.update(data, st, ln);
            all.write(md.digest());
        }
        return cat(new byte[]{(byte)0x5a}, i32l(count), all.toByteArray());
    }

    static byte[] v2SignedData(byte[] d1, byte[] d2, byte[] d3) throws Exception {
        byte[] digests = seq(seq(derInt(0x0103), oct(cat(d1,d2,d3))));
        byte[] certs = ctx0(cert.getEncoded());
        byte[] attrs = seq();
        return seq(digests, certs, attrs);
    }

    static byte[] v3SignedData(byte[] d1, byte[] d2, byte[] d3) throws Exception {
        byte[] digests = seq(seq(derInt(0x0103), oct(cat(d1,d2,d3))));
        byte[] certs = ctx0(cert.getEncoded());
        byte[] attrs = seq();
        return seq(digests, certs, attrs, i32l(24), i32l(6000));
    }

    static byte[] lenPref(byte[] b) { return cat(i32l(b.length), b); }

    static byte[] signV2V3(byte[] zip, int cdStart, int cdSize, byte[] cdB, byte[] eocdRaw, int count) throws Exception {
        int blockLen;
        byte[] block;
        {
            // d3: eocd with cdOffset = cdStart (signing block offset)
            byte[] e3 = eocdRaw.clone();
            byte[] cdOffB = i32l(cdStart);
            System.arraycopy(cdOffB, 0, e3, 16, 4);
            byte[] d1 = sectionDigest(zip, 0, cdStart);
            byte[] d2 = sectionDigest(cdB, 0, cdSize);
            byte[] d3 = sectionDigest(e3, 0, e3.length);

            byte[] sd2 = v2SignedData(d1, d2, d3);
            Signature s2 = Signature.getInstance("SHA256withRSA");
            s2.initSign(privKey); s2.update(sd2);
            byte[] sig2 = s2.sign();
            byte[] signer2 = cat(lenPref(sd2),
                    lenPref(seq(seq(derInt(0x0103), oct(sig2)))),
                    lenPref(cert.getPublicKey().getEncoded()));
            byte[] pair2v = seq(signer2);
            byte[] pair2 = cat(i32l(0x7109871a), pair2v);

            byte[] sd3 = v3SignedData(d1, d2, d3);
            Signature s3 = Signature.getInstance("SHA256withRSA");
            s3.initSign(privKey); s3.update(sd3);
            byte[] sig3 = s3.sign();
            byte[] signer3 = cat(lenPref(sd3), i32l(24), i32l(6000),
                    lenPref(seq(seq(derInt(0x0103), oct(sig3)))));
            byte[] pair3v = seq(signer3);
            byte[] pair3 = cat(i32l(0xf05368c0), pair3v);

            byte[] v1pair = cat(i32l(0x42726577), oct(("V1 signing via META-INF").getBytes(StandardCharsets.UTF_8)));
            byte[] pairs = cat(lenPref(pair2), lenPref(pair3), lenPref(v1pair));
            blockLen = pairs.length + 32;
            ByteBuffer bb = ByteBuffer.allocate(blockLen).order(ByteOrder.LITTLE_ENDIAN);
            bb.putLong(blockLen);
            bb.put(pairs);
            bb.putLong(blockLen);
            bb.put("APK Sig Block 42".getBytes(StandardCharsets.US_ASCII));
            block = bb.array();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(zip, 0, cdStart);
        out.write(block);
        out.write(cdB);
        byte[] e = eocdRaw.clone();
        byte[] off = i32l(cdStart + block.length);
        System.arraycopy(off, 0, e, 16, 4);
        out.write(e);
        return out.toByteArray();
    }

}
