package com.ziyuan.Assistant;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;

/** Kr0oked Metronome 式调度: 每拍一个 period(滴声+静音填充), 小块写入, 参数变化下一拍立即生效, 快拍不吞 */
public class MetroEngine extends Thread {
    private static final int SILENCE_CHUNK = 4800; // 每次最多补 4800 帧(~109ms)静音, 块间响应参数变化

    private AudioTrack audioTrack;
    private boolean playing = false, quit = false, changed = false, booster = false, accentFirst = false;
    private int bpm = 120, notes = 4, sub = 1;
    private byte[] beat1, beat2; // downbeat / upbeat 样本(16bit)
    private MetroAudioData audioData;

    public MetroEngine() {
        setDaemon(true);
        prepareAudioTrack(); // 构造时建好播放器, play() 不会与线程起跑竞争
    }

    public void setAudioData(MetroAudioData ad) { this.audioData = ad; this.changed = true; }
    public void setBpm(int v) { if (v != bpm) { bpm = v; changed = true; } }
    public void setNotes(int v) { if (v != notes) { notes = v; changed = true; } }
    public void setSubdiv(int v) { v = Math.max(1, v); if (v != sub) { sub = v; changed = true; } }
    public void setBooster(boolean v) { if (v != booster) { booster = v; changed = true; } }
    public void setAccentFirst(boolean v) { if (v != accentFirst) { accentFirst = v; changed = true; } }

    private void prepareAudioTrack() {
        int buf = MetroConstant.SampleRate * 2 * 2; // 2s 字节缓冲
        audioTrack = new AudioTrack(
            android.media.AudioManager.STREAM_MUSIC,
            MetroConstant.SampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buf,
            AudioTrack.MODE_STREAM);
    }

    private static byte[] soundBooster(byte[] origin) {
        byte[] r = new byte[origin.length];
        for (int i = 0; i < origin.length - 1; i += 2) {
            short s = (short) (((origin[i] & 0xFF) | (origin[i + 1] << 8)));
            int v = (int) (s * 1.8f);
            if (v > 32767) v = 32767;
            if (v < -32768) v = -32768;
            r[i] = (byte) v;
            r[i + 1] = (byte) (v >> 8);
        }
        return r;
    }

    /** 增强第一个拍子: 对 downbeat 再加一层增益 */
    private static byte[] accentBoost(byte[] origin) {
        byte[] r = new byte[origin.length];
        for (int i = 0; i < origin.length - 1; i += 2) {
            short s = (short) (((origin[i] & 0xFF) | (origin[i + 1] << 8)));
            int v = (int) (s * 2.5f);
            if (v > 32767) v = 32767;
            if (v < -32768) v = -32768;
            r[i] = (byte) v;
            r[i + 1] = (byte) (v >> 8);
        }
        return r;
    }

    private void loadSamples() {
        if (audioData == null || !audioData.isLoaded()) { beat1 = null; beat2 = null; return; }
        beat1 = audioData.getDownbeat();
        beat2 = audioData.getUpbeat();
        if (booster) { beat1 = soundBooster(beat1); beat2 = soundBooster(beat2); }
        if (accentFirst) beat1 = accentBoost(beat1);
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        while (!quit) {
            if (playing && audioTrack != null) {
                if (changed) { loadSamples(); changed = false; }
                if (beat1 == null) { try { sleep(50); } catch (InterruptedException ignored) {} continue; }
                // 一个细分 tick 一个 period: 60s/bpm/sub, 换算成字节
                int subN = Math.max(1, sub);
                int periodFrames = MetroConstant.SampleRate * 60 / Math.max(1, bpm) / subN;
                int periodBytes = periodFrames * 2;
                if (resync) { // 暂停恢复: 清残音, 先预填第一个整拍再起播(杜绝空缓冲欠载卡顿)
                    resync = false;
                    try { audioTrack.pause(); audioTrack.flush(); } catch (Throwable ignored) {}
                    tickIndex = 0;
                    writeAll(beat1, 0, Math.min(beat1.length, periodBytes));
                    int filled0 = Math.min(beat1.length, periodBytes);
                    while (filled0 < periodBytes && !quit) {
                        int n0 = Math.min(SILENCE_CHUNK, periodBytes - filled0);
                        audioTrack.write(SILENCE, 0, n0);
                        filled0 += n0;
                    }
                    try { audioTrack.play(); } catch (Throwable ignored) {}
                    tickIndex = 1 % Math.max(1, notes * subN);
                    continue;
                }
                // 1) 本 tick 声音: 小节头=downbeat, 整拍=upbeat, 细分弱位=upbeat*0.6
                byte[] tick;
                if (tickIndex % notes == 0) tick = beat1;
                else if (tickIndex % subN == 0) tick = beat2;
                else tick = scale(beat2, 0.6f);
                writeAll(tick, 0, Math.min(tick.length, periodBytes));
                // 2) 静音填满 period, 小块写, 期间参数变化立即跳出
                int filled = Math.min(tick.length, periodBytes);
                byte[] sil = SILENCE;
                while (filled < periodBytes && playing && !quit) {
                    int n = Math.min(SILENCE_CHUNK, periodBytes - filled);
                    if (changed) break;
                    audioTrack.write(sil, 0, n);
                    filled += n;
                }
                tickIndex = (tickIndex + 1) % Math.max(1, notes * subN);
            } else {
                try { sleep(10); } catch (InterruptedException ignored) {}
            }
        }
    }

    private static final byte[] SILENCE = new byte[SILENCE_CHUNK];
    private int tickIndex = 0;

    private static byte[] scale(byte[] b, float g) {
        byte[] r = new byte[b.length];
        for (int i = 0; i + 1 < b.length; i += 2) {
            short sv = (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
            int v = (int) (sv * g);
            if (v > 32767) v = 32767;
            if (v < -32768) v = -32768;
            r[i] = (byte) v;
            r[i + 1] = (byte) (v >> 8);
        }
        return r;
    }

    private void writeAll(byte[] b, int off, int len) {
        int w = 0;
        while (w < len && playing && !quit) {
            w += audioTrack.write(b, off + w, len - w);
        }
    }

    /** 当前 tick 索引(按播放头位置推算), 未播放返回 -1 */
    public int currentTick(int ticks) {
        if (audioTrack == null || bpm <= 0) return -1;
        long head = audioTrack.getPlaybackHeadPosition();
        int periodFrames = MetroConstant.SampleRate * 60 / Math.max(1, bpm) / Math.max(1, sub);
        if (periodFrames <= 0) return -1;
        return (int) ((head / periodFrames) % ticks);
    }

    private boolean resync = false;
    public void play() { resync = true; playing = true; if (audioTrack != null) { try { audioTrack.play(); } catch (Throwable ignored) {} } }
    public void pause() { playing = false; if (audioTrack != null) { audioTrack.pause(); audioTrack.flush(); } }
    public boolean isPlaying() { return playing; }
    public int currentBpm() { return bpm; }
    public void close() {
        if (quit) return;
        playing = false;
        try { if (audioTrack != null) { audioTrack.stop(); audioTrack.release(); } } catch (Throwable ignored) {}
        quit = true;
    }
}
