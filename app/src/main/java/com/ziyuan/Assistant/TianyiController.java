package com.ziyuan.Assistant;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/** 洛天依陪跑 (移植 w8888-hh/luotianyi-running-app): 4 阶段 bpm 渐变 + 16 句语音 + 字幕广播 */
public class TianyiController {
    private static class Stage {
        final int dur, bpm0, bpm1;
        final String voice, text;
        Stage(int d, int b0, int b1, String v, String t) { dur = d; bpm0 = b0; bpm1 = b1; voice = v; text = t; }
    }

    private static final Stage[] STAGES = {
        new Stage(120, 100, 115, "reshen",   "先慢慢跑哦，跟着天依的节奏，深呼吸～"),
        new Stage(180, 125, 145, "ranzhi",   "进入燃脂区啦，保持节奏，脂肪在燃烧哦～"),
        new Stage(240, 160, 175, "chongci",  "冲刺时间到！全力以赴，天依陪你冲——！"),
        new Stage(150, 130, 105, "fangsong", "最后啦，放慢脚步深呼吸，我们一起慢慢来～"),
    };

    private static final String[][] CHEERS = {
        {"jiayou",   "加油加油！天依看着你呢～"},
        {"jianchi",  "坚持住，你比想象中更厉害哦！"},
        {"huxi",     "稳住呼吸，跟上天依的节拍～"},
        {"henbang",  "你好棒！继续保持这个节奏！"},
        {"songjian", "肩膀放松，步子轻一点～"},
        {"zaiyici",  "累啦？没关系，再来一次就好！"},
        {"maikaibu", "迈开步子，风都在为你加油哦～"},
        {"chongya",  "冲呀！最后一圈天依陪你冲！"},
    };

    private final Context ctx;
    private final MetroEngine engine;
    private final boolean cheerOn, stageOn, followOn;
    public HeartRateManager hrMgr;   // 由服务注入
    public GpsManager gpsMgr;
    public int age = 30;
    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer voice;
    private int stageIdx = 0, elapsed = 0, cheerIn = 0, nextCheer = 40;
    private boolean running = false;

    private static final Object[][] FOLLOW = {
        // {低%, 高%, bpm, 最短停留秒, voiceId, 文案}
        {0, 60, 150, 120, "reshen",  "先慢慢跑哦，跟着天依的节奏，深呼吸～"},
        {60, 70, 162, 180, "ranzhi", "进入燃脂区啦，保持这个节奏，脂肪在燃烧哦～"},
        {70, 80, 172, 180, "maikaibu", "有氧区间，迈开步子，稳住呼吸和步频～"},
        {80, 200, 182, 120, "chongci", "强度上来啦，跟着节拍全力以赴！"},
    };
    private int bandIdx = -1, bandDwell = 0;

    public TianyiController(Context c, MetroEngine e, boolean cheer, boolean stage, boolean follow) {
        ctx = c; engine = e; cheerOn = cheer; stageOn = stage; followOn = follow;
    }

    public void start() {
        stop();
        if (!cheerOn && !stageOn && !followOn) return;
        running = true;
        stageIdx = 0; elapsed = 0;
        if (stageOn) playVoice("zhunbei", "准备好啦～三、二、一，跟天依一起出发吧！");
        if (stageOn) engine.setBpm(STAGES[0].bpm0);
        h.postDelayed(tick, stageOn || followOn ? 2600 : 3000);
    }

    public void stop() {
        running = false;
        h.removeCallbacksAndMessages(null);
        if (voice != null) { try { voice.stop(); voice.release(); } catch (Throwable ignored) {} voice = null; }
        sub("");
    }

    public boolean isRunning() { return running; }

    /** 暂停语音 */
    public void onPaused() { if (running) playVoice("zanting", "运动暂停！休息一下再继续吧！"); }
    public void onResumed() { if (running) playVoice("kaishi", "继续跑！天依一直陪着你哦～"); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            Stage s = STAGES[stageIdx];
            elapsed++;
            if (elapsed >= s.dur) {
                stageIdx++;
                elapsed = 0;
                if (stageIdx >= STAGES.length) {
                    if (stageOn) playVoice("jieshu", "训练完成啦！天依觉得你好棒，记得拉伸放松哦～");
                    stop();
                    return;
                }
                nextCheer = 35 + (int) (Math.random() * 21);
            }
            if (followOn && hrMgr != null && hrMgr.lastHr > 0) {
                // 跟随跑: 心率区间定步频, 区间内最短停留后切换
                int maxHr = Math.max(120, 220 - age);
                int pct = hrMgr.lastHr * 100 / maxHr;
                int want = bandIdx < 0 ? 0 : bandIdx;
                for (int i = 0; i < FOLLOW.length; i++) {
                    if (pct >= (Integer) FOLLOW[i][0] && pct < (Integer) FOLLOW[i][1]) { want = i; break; }
                }
                if (want != bandIdx && bandDwell >= (bandIdx < 0 ? 0 : (Integer) FOLLOW[bandIdx][3])) {
                    bandIdx = want; bandDwell = 0;
                    playVoice((String) FOLLOW[bandIdx][4], (String) FOLLOW[bandIdx][5]);
                    engine.setBpm((Integer) FOLLOW[bandIdx][2]);
                } else if (bandIdx >= 0) {
                    bandDwell++;
                }
            } else if (stageOn) {
                Stage cur = STAGES[stageIdx];
                if (elapsed == 1) playVoice(cur.voice, cur.text);
                float t = Math.min(1f, elapsed / (float) cur.dur);
                engine.setBpm(Math.round(cur.bpm0 + (cur.bpm1 - cur.bpm0) * t));
            }
            if (followOn && gpsMgr != null) {
                // 广播运动数据给页面
                android.content.Intent st = new Intent("tianyi.hr");
                st.setPackage(ctx.getPackageName());
                st.putExtra("hr", hrMgr == null ? 0 : hrMgr.lastHr);
                st.putExtra("dist", gpsMgr.distKm);
                st.putExtra("pace", gpsMgr.lastPace);
                st.putExtra("gpsFix", gpsMgr.gotFix);
                st.putExtra("hrConn", hrMgr.isConnected());
                ctx.sendBroadcast(st);
            }
            if (cheerOn && ++cheerIn >= nextCheer) {
                cheerIn = 0;
                nextCheer = 35 + (int) (Math.random() * 21);
                String[] c = CHEERS[(int) (Math.random() * CHEERS.length)];
                playVoice(c[0], c[1]);
            }
            h.postDelayed(this, 1000);
        }
    };

    private void playVoice(String id, String text) {
        sub(text);
        try {
            if (voice != null) { try { voice.stop(); voice.release(); } catch (Throwable ignored) {} }
            android.content.res.AssetFileDescriptor fd = ctx.getAssets().openFd("tianyi/" + id + ".wav");
            voice = new MediaPlayer();
            voice.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            voice.prepare();
            voice.start();
        } catch (Throwable ignored) {}
    }

    private void sub(String text) {
        Intent i = new Intent("tianyi.sub");
        i.setPackage(ctx.getPackageName());
        i.putExtra("text", text);
        ctx.sendBroadcast(i);
    }
}
