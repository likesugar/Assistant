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

    private void generateSection() {
        changed = false;
        if (audioData == null || !audioData.isLoaded()) return;
        int total = (int) (MetroConstant.SampleRate * 2 * 60 * notes * 1f / bpm);
        if (total % 2 > 0) total -= total % 2;
        int unit = (int) (total * 1f / notes);
        if (unit % 2 > 0) unit -= unit % 2;
        wave = new byte[total];
        Arrays.fill(wave, (byte) 0);
        // SimpleBeat: 第一拍=downbeat(重音), 其余=upbeat
        byte[] beat1 = audioData.getDownbeat();
        byte[] beat2 = audioData.getUpbeat();
        if (booster) { beat1 = soundBooster(beat1); beat2 = soundBooster(beat2); }
        System.arraycopy(beat1, 0, wave, 0, Math.min(beat1.length, unit));
        for (int i = 1; i < notes; i++) {
            System.arraycopy(beat2, 0, wave, i * unit, Math.min(beat2.length, unit));
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
                if (wave != null && audioTrack != null) audioTrack.write(wave, 0, wave.length);
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }
        }
    }

    /** 当前拍索引(按播放头位置推算), 未播放返回 -1 */
    public int currentBeat(int notes) {
        if (audioTrack == null || wave == null || wave.length == 0) return -1;
        long head = audioTrack.getPlaybackHeadPosition();
        int unitFrames = (wave.length / 2) / notes;
        if (unitFrames <= 0) return -1;
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
