package co.tinode.tindroid;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.telecom.CallAudioState;
import android.util.Log;

public class AudioControl {
    private static final String TAG = "AudioControl";

    private final AudioManager mAudioManager;
    private boolean mIsAudioFocused = false;
    private AudioFocusRequest mAudioFocusRequest = null;
    private int mCallAudioRoute = 0;

    public AudioControl(final Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public AudioManager getAudioManager() {
        return mAudioManager;
    }

    public void setMode(final int mode) {
        mAudioManager.setMode(mode);
    }

    public void setMicrophoneMute(final boolean mute) {
        mAudioManager.setMicrophoneMute(mute);
    }

    // Set the speakerphone on or off.
    // Returns true if the speakerphone was successfully set, false otherwise.
    public boolean setSpeakerphoneOn(final boolean enable) {
        // If a call is in progress then we must use the Telecom API to set the audio route.
        if (Cache.isCallUseful()) {
            int route = Cache.getCallAudioRoute();
            if (enable) {
                if (route == CallAudioState.ROUTE_SPEAKER) {
                    return true;
                }
                // Save the original audio route.
                mCallAudioRoute = route;
                return Cache.setCallAudioRoute(CallAudioState.ROUTE_SPEAKER);
            }
            // Speakerphone off. Restore the original audio route.
            if (route != CallAudioState.ROUTE_SPEAKER) {
                return true;
            }
            route = mCallAudioRoute > 0 ? mCallAudioRoute : CallAudioState.ROUTE_EARPIECE;
            mCallAudioRoute = 0;
            return Cache.setCallAudioRoute(route);
        }

        // Not in a call, use the AudioManager API to toggle the speakerphone.
        boolean done = false;
        // Request audio focus to ensure the app can control audio output
        requestAudioFocus();

        // Set the appropriate audio mode based on whether the speakerphone is being enabled or disabled
        mAudioManager.setMode(enable ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);

        // Check if the device is running Android 12 (API level 31) or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 14 and above
            if (enable) {
                // Retrieve a list of all available output audio devices
                AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

                // Iterate through the list to find the built-in speaker
                for (AudioDeviceInfo device : devices) {
                    if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        // Set the communication device to the built-in speaker
                        done = mAudioManager.setCommunicationDevice(device);
                        break; // Exit the loop once the speaker is set
                    }
                }
            } else {
                // Clear the communication device to revert to the default audio routing.
                mAudioManager.clearCommunicationDevice();
                done = true; // Assume the operation was successful, no way to verify.
            }
        } else {
            // For Android versions below API level 31, use the traditional method to toggle the speakerphone.
            mAudioManager.setSpeakerphoneOn(enable);
            done = true; // Assume the operation was successful, no way to verify.
        }

        if (!done) {
            Log.w(TAG, "Failed to set speakerphone");
        }

        return done;
    }

    public boolean isSpeakerphoneOn() {
        // If call is in progress then we must use the Telecom API to read the audio route;
        if (Cache.isCallUseful()) {
            return Cache.getCallAudioRoute() == CallAudioState.ROUTE_SPEAKER;
        }

        // Not in a call, use the AudioManager API to check the speakerphone status.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo device = mAudioManager.getCommunicationDevice();
            return device != null && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        }

        // Legacy platforms.
        return mAudioManager.isSpeakerphoneOn();
    }

    public void requestAudioFocus()  {
        if (mIsAudioFocused) {
            return;
        }

        if (mAudioFocusRequest == null) {
            mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build();
        }

        String status = null;
        switch (mAudioManager.requestAudioFocus(mAudioFocusRequest)) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                status = "AUDIOFOCUS_REQUEST_FAILED";
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                mIsAudioFocused = true;
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                status = "AUDIOFOCUS_REQUEST_DELAYED";
                break;
            default:
                status = "AUDIOFOCUS_REQUEST_UNKNOWN";
                break;
        }

        if (status != null) {
            Log.w(TAG, "requestAudioFocus failed: " + status);
        }
    }

    public void abandonAudioFocus() {
        if (!mIsAudioFocused || mAudioFocusRequest == null) {
            return;
        }

        String status = null;
        switch (mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest)) {
            case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                status = "AUDIOFOCUS_REQUEST_FAILED";
                break;
            case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                mIsAudioFocused = false;
                break;
            default:
                status = "AUDIOFOCUS_REQUEST_UNKNOWN";
                break;
        }

        if (status != null) {
            Log.w(TAG, "abandonAudioFocus failed: " + status);
        }
    }
}
