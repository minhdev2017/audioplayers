package xyz.luan.audioplayers;

import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelector;

public class MyPlayer extends SimpleExoPlayer {
    public MyPlayer(RenderersFactory renderersFactory, TrackSelector trackSelector,
                    LoadControl loadControl){
        super(renderersFactory, trackSelector,loadControl);
    }
}
