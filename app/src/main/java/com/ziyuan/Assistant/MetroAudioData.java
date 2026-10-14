package com.ziyuan.Assistant;

public class MetroAudioData {
    private byte[] upbeat;
    private byte[] downbeat;
    private final String upbeatPath, downbeatPath, name;
    private boolean isLoaded = false;

    public MetroAudioData(String name, String upbeatPath, String downbeatPath) {
        this.name = name; this.upbeatPath = upbeatPath; this.downbeatPath = downbeatPath;
    }
    public byte[] getUpbeat() { return upbeat; }
    public byte[] getDownbeat() { return downbeat; }
    public void setBeat(byte[] u, byte[] d) { isLoaded = true; upbeat = u; downbeat = d; }
    public String getUpbeatPath() { return upbeatPath; }
    public String getDownbeatPath() { return downbeatPath; }
    public String getName() { return name; }
    public boolean isLoaded() { return isLoaded; }
}
