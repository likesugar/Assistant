package com.ziyuan.Assistant;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.Arrays;

/** SimpleBeat 移植: 预生成整段 16bit 波形, AudioTrack 流式写入, 节拍零累积误差 */
public class MetroEngine extends Thread {
    private AudioTrack audioTrack;
    private boolean playing = false, quit = false, changed = false, booster = false;
    private int bpm = 120, notes = 4;
    private byte[] wave;
    private MetroAudioData audioData;

    public MetroEngine() {
        setDaemon(true);
        prepareAudioTrack(); // 原版: 构造时建好播放器, play() 不会与线程起跑竞争
    }

    public void setAudioData(MetroAudioData ad) { this.audioData = ad; this.changed = true; }
    public void setBpm(int v) { bpm = v; changed = true; }
    public void setNotes(int v) { notes = v; changed = true; }
    public void setBooster(boolean b) { booster = b; changed = true; }

    private byte[] soundBooster(byte[] origin) {
        byte[] target = new byte[origin.length];
        double multiple = Math.pow(10, 20.0 / 20); // +20dB
        for (int i = 0; i < origin.length - 1; i += 2) {
            short volume = (short) ((origin[i] & 0xFF) | (origin[i + 1] << 8));
            int temp = (int) (volume * multiple);
            if (temp > Short.MAX_VALUE) volume = Short.MAX_VALUE;
            else if (temp < Short.MIN_VALUE) volume = Short.MIN_VALUE;
            else volume = (short) temp;
            target[i] = (byte) (volume & 0xFF);
            target[i + 1] = (byte) ((volume >> 8) & 0xFF);
        }
        return target;
    }

    private int unitFrames = 0; // 每拍帧数, currentBeat 与波形共用同一套对齐

    private void generateSection() {
        changed = false;
        if (audioData == null || !audioData.isLoaded()) return;
        // 先按帧算每拍长度, 再反推整段 => 每一拍严格等距, 不受浮点误差影响
        int framesPerBar = MetroConstant.SampleRate * 60 / bpm; // 一小节帧数
        unitFrames = framesPerBar / notes;
        if (unitFrames < 1) unitFrames = 1;
        int total = unitFrames * notes * 2; // 字节
        wave = new byte[total];
        Arrays.fill(wave, (byte) 0);
        // SimpleBeat: 第一拍=downbeat(重音), 其余=upbeat
        byte[] beat1 = audioData.getDownbeat();
        byte[] beat2 = audioData.getUpbeat();
        if (booster) { beat1 = soundBooster(beat1); beat2 = soundBooster(beat2); }
        System.arraycopy(beat1, 0, wave, 0, Math.min(beat1.length, total));
        for (int i = 1; i < notes; i++) {
            System.arraycopy(beat2, 0, wave, i * unitFrames * 2, Math.min(beat2.length, total - i * unitFrames * 2));
        }
    }

    private void prepareAudioTrack() {
        audioTrack = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(MetroConstant.SampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(MetroConstant.SampleRate * 2)
            .build();
    }

    @SuppressWarnings("BusyWait")
    public void run() {
        while (!quit) {
            if (playing) {
                if (changed) generateSection();
                if (wave != null && audioTrack != null) {
                    int chunk = MetroConstant.SampleRate / 2 * 2; // 0.25s, 块间检查 changed
                    for (int off = 0; off < wave.length && playing && !quit; off += chunk) {
                        if (changed) { // 拍数/BPM/音色变了: 丢弃旧队列从头同步
                            audioTrack.pause(); audioTrack.flush();
                            generateSection();
                            audioTrack.play();
                        }
                        int n = Math.min(chunk, wave.length - off);
                        audioTrack.write(wave, off, n);
                    }
                }
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }
        }
    }

    /** 当前拍索引(按播放头位置推算), 未播放返回 -1 */
    public int currentBeat(int notes) {
        if (audioTrack == null || wave == null || unitFrames <= 0) return -1;
        long head = audioTrack.getPlaybackHeadPosition();
        return (int) ((head / unitFrames) % notes);
    }

    public void play() { playing = true; if (audioTrack != null) audioTrack.play(); }
    public void pause() { playing = false; if (audioTrack != null) { audioTrack.pause(); audioTrack.flush(); } }
    public boolean isPlaying() { return playing; }
    public void close() {
        if (quit) return;
        playing = false;
        try { if (audioTrack != null) { audioTrack.stop(); audioTrack.release(); } } catch (Throwable ignored) {}
        quit = true;
    }
}
