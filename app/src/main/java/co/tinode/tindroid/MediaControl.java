package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaDataSource;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import co.tinode.tindroid.format.FullFormatter;
import co.tinode.tinodesdk.Tinode;

public class MediaControl {
    private static final String TAG = "MediaControl";

    private final Context mContext;
    private AudioManager mAudioManager = null;
    private MediaPlayer mAudioPlayer = null;
    private int mPlayingAudioSeq = -1;
    private FullFormatter.AudioControlCallback mAudioControlCallback = null;
    // Action to take when the player becomes ready.
    private PlayerReadyAction mReadyAction = PlayerReadyAction.NOOP;
    // Playback fraction to seek to when the player is ready.
    private float mSeekTo = -1f;

    public MediaControl(Context context) {
        mContext = context;
    }

    boolean ensurePlayerReady(final int seq, Map<String, Object> data,
                              FullFormatter.AudioControlCallback control) throws IOException {
        if (mAudioPlayer != null && mPlayingAudioSeq == seq) {
            mAudioControlCallback = control;
            return true;
        }

        if (mPlayingAudioSeq > 0 && mAudioControlCallback != null) {
            mAudioControlCallback.reset();
        }

        // Declare current player un-prepared.
        mPlayingAudioSeq = -1;

        if (mAudioPlayer != null) {
            try {
                mAudioPlayer.stop();
            } catch (IllegalStateException ignored) {}
            mAudioPlayer.reset();
        } else {
            mAudioPlayer = new MediaPlayer();
        }

        if (mAudioManager == null) {
            mAudioManager = (AudioManager) mContext.getSystemService(Activity.AUDIO_SERVICE);
            mAudioManager.setMode(AudioManager.MODE_IN_CALL);
            mAudioManager.setSpeakerphoneOn(true);
        }

        mAudioControlCallback = control;
        mAudioPlayer.setOnPreparedListener(mp -> {
            if (mPlayingAudioSeq > 0) {
                // Another media have already been started while we waited for this media to become ready.
                mp.release();
                return;
            }

            mPlayingAudioSeq = seq;
            if (mReadyAction == PlayerReadyAction.PLAY) {
                mReadyAction = PlayerReadyAction.NOOP;
                mp.start();
            } else if (mReadyAction == PlayerReadyAction.SEEK ||
                    mReadyAction == PlayerReadyAction.SEEKNPLAY) {
                seekTo(fractionToPos(mSeekTo));
            }
            mSeekTo = -1f;
        });
        mAudioPlayer.setOnCompletionListener(mp -> {
            if (mPlayingAudioSeq != seq) {
                return;
            }
            int pos = mp.getCurrentPosition();
            if (pos > 0) {
                if (mAudioControlCallback != null) {
                    mAudioControlCallback.reset();
                }
            }
        });
        mAudioPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "Playback error " + what + "/" + extra);
            if (mPlayingAudioSeq != seq) {
                return true;
            }
            Toast.makeText(mContext, R.string.unable_to_play_audio, Toast.LENGTH_SHORT).show();
            return false;
        });
        mAudioPlayer.setOnSeekCompleteListener(mp -> {
            if (mPlayingAudioSeq != seq) {
                return;
            }
            if (mReadyAction == PlayerReadyAction.SEEKNPLAY) {
                mReadyAction = PlayerReadyAction.NOOP;
                mp.start();
            }
        });
        mAudioPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL).build());

        Object val;
        if ((val = data.get("ref")) instanceof String) {
            Tinode tinode = Cache.getTinode();
            URL url = tinode.toAbsoluteURL((String) val);
            if (url != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mAudioPlayer.setDataSource(mContext, Uri.parse(url.toString()),
                                tinode.getRequestHeaders(), null);
                    } else {
                        Uri uri = Uri.parse(url.toString()).buildUpon()
                                .appendQueryParameter("apikey", tinode.getApiKey())
                                .appendQueryParameter("auth", "token")
                                .appendQueryParameter("secret", tinode.getAuthToken())
                                .build();
                        mAudioPlayer.setDataSource(mContext, uri);
                    }
                } catch (SecurityException | IOException ex) {
                    Log.w(TAG, "Failed to add URI data source ", ex);
                    Toast.makeText(mContext, R.string.unable_to_play_audio, Toast.LENGTH_SHORT).show();
                }
            } else {
                mAudioControlCallback.reset();
                Log.w(TAG, "Invalid ref URL " + val);
                Toast.makeText(mContext, R.string.unable_to_play_audio, Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if ((val = data.get("val")) instanceof String) {
            byte[] source = Base64.decode((String) val, Base64.DEFAULT);
            mAudioPlayer.setDataSource(new MemoryAudioSource(source));
        } else {
            mAudioControlCallback.reset();
            Log.w(TAG, "Unable to play audio: missing data");
            Toast.makeText(mContext, R.string.unable_to_play_audio, Toast.LENGTH_SHORT).show();
            return false;
        }
        mAudioPlayer.prepareAsync();
        return true;
    }

    void releasePlayer(int seq) {
        if ((seq != 0 && mPlayingAudioSeq != seq) || mPlayingAudioSeq == -1) {
            return;
        }

        mPlayingAudioSeq = -1;
        mReadyAction = PlayerReadyAction.NOOP;
        mSeekTo = -1f;
        if (mAudioPlayer != null) {
            try {
                mAudioPlayer.stop();
            } catch (IllegalStateException ignored) {}
            mAudioPlayer.reset();
            mAudioPlayer.release();
            mAudioPlayer = null;
        }
        if (mAudioControlCallback != null) {
            mAudioControlCallback.reset();
        }
    }

    // Start playing at the current position.
    void playWhenReady() {
        if (mPlayingAudioSeq > 0) {
            mAudioPlayer.start();
        } else {
            mReadyAction = PlayerReadyAction.PLAY;
        }
    }

    void pause() {
        if (mAudioPlayer != null && mAudioPlayer.isPlaying()) {
            mAudioPlayer.pause();
        }
        mReadyAction = PlayerReadyAction.NOOP;
        mSeekTo = -1f;
    }

    void seekToWhenReady(float fraction) {
        if (mPlayingAudioSeq > 0) {
            // Already prepared.
            int pos = fractionToPos(fraction);
            if (mAudioPlayer.getCurrentPosition() != pos) {
                // Need to seek.
                mReadyAction = PlayerReadyAction.NOOP;
                seekTo(pos);
            } else {
                // Already prepared & at the right position.
                mAudioPlayer.start();
            }
        } else {
            mReadyAction = PlayerReadyAction.SEEK;
            mSeekTo = fraction;
        }
    }

    void seekTo(int pos) {
        if (mAudioPlayer != null && mPlayingAudioSeq > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mAudioPlayer.seekTo(pos, MediaPlayer.SEEK_CLOSEST);
            } else {
                mAudioPlayer.seekTo(pos);
            }
        }
    }

    private int fractionToPos(float fraction) {
        try {
            if (mAudioPlayer != null && mPlayingAudioSeq > 0) {
                long duration = mAudioPlayer.getDuration();
                if (duration > 0) {
                    return ((int) (fraction * duration));
                } else {
                    Log.w(TAG, "Audio has no duration");
                }
            }
        } catch (IllegalStateException ex) {
            Log.w(TAG, "Duration not available yet " + mPlayingAudioSeq, ex);
        }
        return -1;
    }

    // Actions to take in setOnPreparedListener, when the player is ready.
    private enum PlayerReadyAction {
        // Do nothing.
        NOOP,
        // Start playing.
        PLAY,
        // Seek without changing player state.
        SEEK,
        // Seek, then play when seek finishes.
        SEEKNPLAY
    }

    // Wrap in-band audio into MediaDataSource to make it playable by MediaPlayer.
    private static class MemoryAudioSource extends MediaDataSource {
        private final byte[] mData;

        MemoryAudioSource(byte[] source) {
            mData = source;
        }

        @Override
        public int readAt(long position, byte[] destination, int offset, int size) throws IOException {
            size = Math.min(mData.length - (int) position, size);
            System.arraycopy(mData, (int) position, destination, offset, size);
            return size;
        }

        @Override
        public long getSize() throws IOException {
            return mData.length;
        }

        @Override
        public void close() throws IOException {
            // Do nothing.
        }
    }
}
