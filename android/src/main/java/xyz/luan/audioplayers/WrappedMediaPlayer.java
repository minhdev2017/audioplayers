package xyz.luan.audioplayers;

import android.app.WallpaperManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.PlaybackParameters;


public class WrappedMediaPlayer extends xyz.luan.audioplayers.Player {

    private String playerId;

    private String url;
    private double volume = 1.0;
    private float rate = 1.0f;
    private boolean respectSilence;
    private boolean stayAwake;
    private ReleaseMode releaseMode = ReleaseMode.RELEASE;
    private String playingRoute = "speakers";

    private boolean released = true;
    private boolean prepared = false;
    private boolean error = false;

    private int shouldSeekTo = -1;

    private SimpleExoPlayer player;
    private AudioplayersPlugin ref;

    WrappedMediaPlayer(AudioplayersPlugin ref, String playerId) {
        this.ref = ref;
        this.playerId = playerId;
    }

    /**
     * Setter methods
     */

    @Override
    void setUrl(String url, boolean isLocal, Context context) {
        if (this.error || !objectEquals(this.url, url)) {

            ///this.player.prepareAsync();
        }
        this.url = url;
        this.error = false;
        if (this.released) {
            this.player = createPlayer(context);
            this.released = false;
        } else if (this.prepared) {
            //this.player.reset();
            this.prepared = false;
        }

        this.setSource(url, false, context);
        this.player.setVolume((float) volume);
        if(this.releaseMode == ReleaseMode.LOOP)
            this.player.setRepeatMode(Player.REPEAT_MODE_ONE);
    }

    @Override
    void setVolume(double volume) {
        if (this.volume != volume) {
            this.volume = volume;
            if (!this.released) {
                this.player.setVolume((float) volume);
            }
        }
    }

    @Override
    void setPlayingRoute(String playingRoute, Context context) {
        /*if (!objectEquals(this.playingRoute, playingRoute)) {
            boolean wasPlaying = this.playing;
            if (wasPlaying) {
                this.pause();
            }

            this.playingRoute = playingRoute;

            int position = 0;
            if (player != null) {
                position = (int)player.getCurrentPosition();
            }

            this.released = false;
            this.player = createPlayer(context);
            this.setSource(url, wasPlaying, context);

            this.seek(position);
        }*/
    }

    @Override
    int setRate(double rate) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw new UnsupportedOperationException("The method 'setRate' is available only on Android SDK version " + Build.VERSION_CODES.M + " or higher!");
        }
        if (this.player != null) {
            this.rate = (float) rate;
            PlaybackParameters param = new PlaybackParameters(this.rate, 1f);
            player.setPlaybackParameters(param);
            return 1;
        }
        return 0;
    }

    @Override
    void configAttributes(boolean respectSilence, boolean stayAwake, Context context) {
        if (this.respectSilence != respectSilence) {
            this.respectSilence = respectSilence;
            if (!this.released) {
                setAttributes(player, context);
            }
        }
        if (this.stayAwake != stayAwake) {
            this.stayAwake = stayAwake;
            if (!this.released && this.stayAwake) {
                this.player.setWakeMode(C.WAKE_MODE_NETWORK);
            }
        }
    }

    @Override
    void setReleaseMode(ReleaseMode releaseMode) {
        if (this.releaseMode != releaseMode) {
            this.releaseMode = releaseMode;
            if (!this.released) {
                if(this.releaseMode == ReleaseMode.LOOP)
                    this.player.setRepeatMode(Player.REPEAT_MODE_ONE);
                else
                    this.player.setRepeatMode(Player.REPEAT_MODE_OFF);
            }
        }
    }

    /**
     * Getter methods
     */

    @Override
    int getDuration() {
        return (int)this.player.getDuration();
    }

    @Override
    int getCurrentPosition() {
        return (int)this.player.getCurrentPosition();
    }

    @Override
    String getPlayerId() {
        return this.playerId;
    }

    @Override
    boolean isActuallyPlaying() {
        return this.prepared;
    }

    /**
     * Playback handling methods
     */

    @Override
    void play(Context context) {
        if (this.released) {
            this.released = false;
            this.player = createPlayer(context);
            this.setSource(url, true, context);
        } else {
            this.player.setPlayWhenReady(true);
            this.ref.handleIsPlaying(this);
        }
    }

    @Override
    void stop() {
        if (this.released) {
            return;
        }

        if (releaseMode != ReleaseMode.RELEASE) {
            this.player.setPlayWhenReady(false);
            this.player.seekTo(0);
            this.player.stop();
        } else {
            this.release();
        }
    }

    @Override
    void release() {
        if (this.released) {
            return;
        }

        this.player.stop();
        if(listener!= null){
            this.player.removeListener(listener);
            listener = null;
        }
        this.player.release();
        this.player = null;

        this.prepared = false;
        this.released = true;
        this.error = false;
    }

    @Override
    void pause() {
        this.player.setPlayWhenReady(false);
    }

    // seek operations cannot be called until after
    // the player is ready.
    @Override
    void seek(int position) {
        if (this.prepared)
            this.player.seekTo(position);
        else
            this.shouldSeekTo = position;
    }

    /**
     * MediaPlayer callbacks
     */



    /**
     * Internal logic. Private methods
     */

    private SimpleExoPlayer createPlayer(Context context) {
        SimpleExoPlayer player = new SimpleExoPlayer.Builder(context,new AudioOnlyRenderersFactory(context)).build();
        //setAttributes(player, context);
        player.setVolume((float) volume);
        if(this.releaseMode == ReleaseMode.LOOP)
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        initEventListeners(player);
        return player;
    }
    Player.EventListener listener;
    private void initEventListeners(final SimpleExoPlayer player) {
        /*player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onAudioSessionId(EventTime eventTime, int audioSessionId) {
               // ref.handleAudioSessionIdChange(backgroundAudioPlayer, audioSessionId);
            }
        });*/
        listener = new Player.EventListener() {

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                switch (playbackState) {
                    case Player.STATE_BUFFERING: {
                        // buffering
                        break;
                    }
                    case Player.STATE_READY: {
                        Log.e("STATE_READY", playWhenReady+"");
                        prepared = true;
                        if (playWhenReady) {
                            // resumed
                            //player.setPlayWhenReady(true);
                            ref.handleIsPlaying(WrappedMediaPlayer.this);
                        }
                        if (shouldSeekTo >= 0) {
                            player.seekTo(shouldSeekTo);
                            shouldSeekTo = -1;
                        }
                        break;
                    }
                    case Player.STATE_ENDED: {
                        // completed
                        Log.e("STATE_ENDED", playWhenReady+"");
                        if(playWhenReady){
                            if(releaseMode != ReleaseMode.LOOP){
                                player.setPlayWhenReady(false);
                                //player.seekTo(0, 0);
                                ref.handleCompletion(WrappedMediaPlayer.this);
                            }else{
                                //player.seekTo(0, 0);
                            }
                        }


                        break;
                    }
                    case Player.STATE_IDLE: {
                        // stopped
                        //ref.handleStateChange(backgroundAudioPlayer, PlayerState.STOPPED);
                        break;
                    }
                    // handle of released is in release method!
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                WrappedMediaPlayer.this.error = true;
                ref.handleError(WrappedMediaPlayer.this, "Error: " + url);
            }
        };
        player.addListener(listener);
    }

    private void setSource(String url, boolean isPlay, Context context) {
        if(url == "")
            return;
        try{

            String userAgent = Util.getUserAgent(context, "NewAudiotruyen");
            DataSource.Factory httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                    userAgent,
                    20000,
                    20000,
                    true
            );
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context,
                    httpDataSourceFactory);
            Uri uri = Uri.parse(url);


            ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
            player.prepare(mediaSource, true, true);
            //player.seekTo(0, 0);
            Log.e("setSource", isPlay+"");
            player.setPlayWhenReady(isPlay);

        } catch (Exception ex) {
            throw new RuntimeException("Unable to access resource", ex);
        }
    }

    @SuppressWarnings("deprecation")
    private void setAttributes(SimpleExoPlayer player, Context context) {
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (objectEquals(this.playingRoute, "speakers")) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(respectSilence ? AudioAttributes.USAGE_NOTIFICATION_RINGTONE : AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                );
            } else {
                // Works with bluetooth headphones
                // automatically switch to earpiece when disconnect bluetooth headphones
                player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                );
                if ( context != null ) {
                    AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    mAudioManager.setSpeakerphoneOn(false);
                }
            }

        } else {
            // This method is deprecated but must be used on older devices
            if (objectEquals(this.playingRoute, "speakers")) {
                player.setAudioStreamType(respectSilence ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC);
            } else {
                player.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            }
        }*/
    }

}
