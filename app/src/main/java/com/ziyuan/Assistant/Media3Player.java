package com.ziyuan.Assistant;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * Media3 (ExoPlayer) 内核封装：抖音直播是 HLS(.m3u8)，Media3 原生支持且稳定。
 * 视频渲染走 MediaCodec（默认开启，无需额外配置）；请求头(Referer/UA)由 HttpDataSource
 * 的默认请求属性注入，避免 MediaItem 内没有 setHttpRequestHeaders 的版本限制。
 */
public class Media3Player extends AbstractPlayer {

    private ExoPlayer mPlayer;
    private final Context mAppContext;
    private float mSpeed = 1.0f;
    private boolean mPrepared = false;
    private String mPendingPath;
    private Map<String, String> mPendingHeaders;

    public Media3Player(Context context) {
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        // 快速起播：默认 LoadControl 首播要缓冲 2500ms/重缓冲 5000ms，直播体感偏慢。
        // 收紧为首播 800ms/重缓冲 1500ms（min/max 缓冲保持 10s/30s，避免频繁卡顿）
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(10000, 30000, 800, 1500)
                .build();
        mPlayer = new ExoPlayer.Builder(mAppContext)
                .setLoadControl(loadControl)
                .build();
        mPlayer.setPlaybackSpeed(mSpeed);
        mPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        mPrepared = false;
                        mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_BUFFERING_START, 701);
                        break;
                    case Player.STATE_READY:
                        if (!mPrepared) {
                            mPrepared = true;
                            mPlayerEventListener.onPrepared();
                        }
                        mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_BUFFERING_END, 702);
                        mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_RENDERING_START, 3);
                        break;
                    case Player.STATE_ENDED:
                        mPlayerEventListener.onCompletion();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                mPlayerEventListener.onError();
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    mPlayerEventListener.onVideoSizeChanged(videoSize.width, videoSize.height);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    mPlayerEventListener.onInfo(AbstractPlayer.MEDIA_INFO_RENDERING_START, 3);
                }
            }
        });
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        mPendingPath = path;
        mPendingHeaders = headers;
    }

    @Override
    public void setDataSource(android.content.res.AssetFileDescriptor fd) {
        // 不支持 asset 播放
    }

    @Override
    public void start() {
        mPlayer.play();
    }

    @Override
    public void pause() {
        mPlayer.pause();
    }

    @Override
    public void stop() {
        try {
            mPlayer.stop();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void prepareAsync() {
        mPrepared = false;
        HttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
        if (mPendingHeaders != null && !mPendingHeaders.isEmpty()) {
            try {
                httpFactory.setDefaultRequestProperties(mPendingHeaders);
            } catch (Throwable ignored) {
            }
        }
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(mAppContext, httpFactory);
        MediaItem item = MediaItem.fromUri(mPendingPath);
        MediaSource source;
        if (mPendingPath != null && mPendingPath.contains(".m3u8")) {
            // chunkless preparation：准备阶段不再额外下载变体播放列表/初始化分片，直播起播更快
            source = new HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(item);
        } else {
            source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item);
        }
        mPlayer.setMediaSource(source);
        // 显式自动开播：Ijk 内核依赖 IjkMediaPlayer 默认 start-on-prepared=1 自动播放，
        // Media3 必须显式置 playWhenReady=true，否则个别 ROM/状态下进页面不自动播放
        mPlayer.setPlayWhenReady(true);
        mPlayer.prepare();
    }

    @Override
    public void reset() {
        try {
            mPlayer.stop();
            mPlayer.clearMediaItems();
        } catch (Exception ignored) {
        }
        mPrepared = false;
    }

    @Override
    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        mPlayer.seekTo((int) time);
    }

    @Override
    public void release() {
        mPlayer.release();
    }

    @Override
    public long getCurrentPosition() {
        return mPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        long d = mPlayer.getDuration();
        return d < 0 ? 0 : d;
    }

    @Override
    public int getBufferedPercentage() {
        return mPlayer.getBufferedPercentage();
    }

    @Override
    public void setSurface(Surface surface) {
        mPlayer.setVideoSurface(surface);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        mPlayer.setVideoSurfaceHolder(holder);
    }

    @Override
    public void setVolume(float v1, float v2) {
        mPlayer.setVolume((v1 + v2) / 2f);
    }

    @Override
    public void setLooping(boolean isLooping) {
        mPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    @Override
    public void setOptions() {
    }

    @Override
    public void setSpeed(float speed) {
        mSpeed = speed;
        if (mPlayer != null) {
            mPlayer.setPlaybackSpeed(speed);
        }
    }

    @Override
    public float getSpeed() {
        return mPlayer != null ? mPlayer.getPlaybackParameters().speed : mSpeed;
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }
}
