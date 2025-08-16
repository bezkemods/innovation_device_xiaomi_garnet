/*
 * Copyright (C) 2018,2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.dirac;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.UserHandle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.util.List;

public class DiracUtils {

    private static final String TAG = "DiracUtils";
    private static final String PREF_DIRAC_ENABLED = "dirac_enabled";
    
    private static DiracUtils mInstance;
    private DiracSound mDiracSound;
    private MediaSessionManager mMediaSessionManager;
    private Handler mHandler = new Handler();
    private Context mContext;
    private boolean mDiracSupported = false;
    private SharedPreferences mSharedPrefs;

    public DiracUtils(Context context) {
        mContext = context;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        
        try {
            mDiracSound = new DiracSound(0, 0);
            // Test if the DiracSound is properly initialized
            mDiracSound.getMusic();
            mDiracSupported = true;
            Log.d(TAG, "DiracSound initialized successfully");
        } catch (Exception e) {
            Log.w(TAG, "DiracSound not supported on this device: " + e.getMessage());
            mDiracSound = null;
            mDiracSupported = false;
        }
    }

    public static synchronized DiracUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DiracUtils(context);
        }

        return mInstance;
    }

    public boolean isDiracSupported() {
        return mDiracSupported;
    }

    private void triggerPlayPause(MediaController controller) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDownPause = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0);
        final KeyEvent evUpPause = KeyEvent.changeAction(evDownPause, KeyEvent.ACTION_UP);
        final KeyEvent evDownPlay = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0);
        final KeyEvent evUpPlay = KeyEvent.changeAction(evDownPlay, KeyEvent.ACTION_UP);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evDownPause);
            }
        });
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evUpPause);
            }
        }, 20);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evDownPlay);
            }
        }, 1000);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                controller.dispatchMediaButtonEvent(evUpPlay);
            }
        }, 1020);
    }

    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller != null) {
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getState();
            }
        }
        return PlaybackState.STATE_NONE;
    }

    private void refreshPlaybackIfNecessary(){
        if (mMediaSessionManager == null) return;

        final List<MediaController> sessions
                = mMediaSessionManager.getActiveSessionsForUser(
                null, UserHandle.ALL);
        for (MediaController aController : sessions) {
            if (PlaybackState.STATE_PLAYING ==
                    getMediaControllerPlaybackState(aController)) {
                triggerPlayPause(aController);
                break;
            }
        }
    }

    public void setEnabled(boolean enable) {
        if (!mDiracSupported) {
            Log.w(TAG, "Dirac not supported, cannot set enabled state");
            return;
        }
        
        try {
            mDiracSound.setEnabled(enable);
            mDiracSound.setMusic(enable ? 1 : 0);
            
            // Save preference
            mSharedPrefs.edit().putBoolean(PREF_DIRAC_ENABLED, enable).apply();
            
            if (enable) {
                refreshPlaybackIfNecessary();
            }
            Log.d(TAG, "Dirac enabled: " + enable);
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac enabled state: " + enable, e);
        }
    }

    public boolean isDiracEnabled() {
        if (!mDiracSupported) {
            return false;
        }
        
        try {
            return mDiracSound != null && mDiracSound.getMusic() == 1;
        } catch (Exception e) {
            Log.w(TAG, "Error getting Dirac enabled state, falling back to preference", e);
            // Fallback to shared preference if AudioEffect fails
            return mSharedPrefs.getBoolean(PREF_DIRAC_ENABLED, false);
        }
    }

    public void setLevel(String preset) {
        if (!mDiracSupported) {
            Log.w(TAG, "Dirac not supported, cannot set level");
            return;
        }
        
        try {
            String[] level = preset.split("\\s*,\\s*");

            for (int band = 0; band <= level.length - 1; band++) {
                mDiracSound.setLevel(band, Float.valueOf(level[band]));
            }
            Log.d(TAG, "Dirac level set: " + preset);
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac level: " + preset, e);
        }
    }

    public void setHeadsetType(int paramInt) {
        if (!mDiracSupported) {
            Log.w(TAG, "Dirac not supported, cannot set headset type");
            return;
        }
        
        try {
            mDiracSound.setHeadsetType(paramInt);
            Log.d(TAG, "Dirac headset type set: " + paramInt);
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac headset type: " + paramInt, e);
        }
    }

    public boolean getHifiMode() {
        try {
            AudioManager audioManager = mContext.getSystemService(AudioManager.class);
            return audioManager.getParameters("hifi_mode").contains("true");
        } catch (Exception e) {
            Log.w(TAG, "Error getting HiFi mode", e);
            return false;
        }
    }

    public void setHifiMode(int paramInt) {
        if (!mDiracSupported) {
            Log.w(TAG, "Dirac not supported, cannot set HiFi mode");
            return;
        }
        
        try {
            AudioManager audioManager = mContext.getSystemService(AudioManager.class);
            audioManager.setParameters("hifi_mode=" + (paramInt == 1 ? true : false));
            mDiracSound.setHifiMode(paramInt);
            Log.d(TAG, "Dirac HiFi mode set: " + paramInt);
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac HiFi mode: " + paramInt, e);
        }
    }

    public void setScenario(int sceneInt) {
        if (!mDiracSupported) {
            Log.w(TAG, "Dirac not supported, cannot set scenario");
            return;
        }
        
        try {
            mDiracSound.setScenario(sceneInt);
            Log.d(TAG, "Dirac scenario set: " + sceneInt);
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac scenario: " + sceneInt, e);
        }
    }
}
