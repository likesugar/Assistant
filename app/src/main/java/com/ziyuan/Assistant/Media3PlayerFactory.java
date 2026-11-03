package com.ziyuan.Assistant;

import android.content.Context;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.PlayerFactory;

/**
 * Media3 ExoPlayer 内核工厂，供 doikki VideoView.setPlayerFactory 使用。
 */
public class Media3PlayerFactory extends PlayerFactory<AbstractPlayer> {
    @Override
    public AbstractPlayer createPlayer(Context context) {
        return new Media3Player(context);
    }
}
