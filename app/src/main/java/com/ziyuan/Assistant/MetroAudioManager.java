package com.ziyuan.Assistant;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetroAudioManager {
    private final Context mContext;
    private final Map<String, MetroAudioData> audios = new HashMap<>();
    private final List<String> audioList = new ArrayList<>();

    private void putAudio(String name, String up, String down) {
        audios.put(name, new MetroAudioData(name, up, down));
        audioList.add(name);
    }

    public MetroAudioManager(Context context) {
        mContext = context;
        putAudio(MetroConstant.AudioDefault, "audios/upbeat.wav", "audios/downbeat.wav");
        putAudio(MetroConstant.AudioClap, "audios/Clap1.wav", "audios/Clap2.wav");
        putAudio(MetroConstant.AudioPluck, "audios/Pluck1.wav", "audios/Pluck2.wav");
        putAudio(MetroConstant.AudioRisset, "audios/Risset1.wav", "audios/Risset2.wav");
        putAudio(MetroConstant.AudioSine, "audios/Sine1.wav", "audios/Sine2.wav");
        putAudio(MetroConstant.AudioSquare, "audios/Square1.wav", "audios/Square2.wav");
        putAudio(MetroConstant.AudioBongo, "audios/Bongo2.wav", "audios/Bongo1.wav");
    }

    private byte[] loadData(String path) throws IOException {
        InputStream in = mContext.getAssets().open(path);
        in.skip(MetroConstant.WavHeaderSize);
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        while (in.read(buffer) != -1) bo.write(buffer);
        in.close();
        return bo.toByteArray();
    }

    public MetroAudioData getAudio(String key) throws IOException {
        MetroAudioData ad = audios.get(key);
        if (ad == null) throw new IOException("AudioData " + key + " Not Found");
        if (!ad.isLoaded()) ad.setBeat(loadData(ad.getUpbeatPath()), loadData(ad.getDownbeatPath()));
        return ad;
    }

    public List<String> getAudioList() { return audioList; }
    public int getPosition(String key) { return audioList.indexOf(key); }
}
