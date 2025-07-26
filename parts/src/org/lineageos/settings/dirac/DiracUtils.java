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
import android.view.KeyEvent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;
import java.util.List;

public class DiracUtils {

    private static final String TAG = "DiracUtils";
    private static final String PREF_NAME = "dirac_prefs";
    private static final String PREF_ENABLED = "dirac_enabled";
    private static final String PREF_HEADSET_TYPE = "dirac_headset_type";
    private static final String PREF_HIFI_MODE = "dirac_hifi_mode";
    private static final String PREF_SCENARIO = "dirac_scenario";
    private static final String PREF_PRESET = "dirac_preset";

    // Audio session ID for global audio effects
    private static final int GLOBAL_AUDIO_SESSION = 0;

    private static DiracUtils mInstance;
    private DiracSound mDiracSound;
    private MediaSessionManager mMediaSessionManager;
    private Handler mHandler = new Handler();
    private Context mContext;
    private SharedPreferences mSharedPrefs;

    public DiracUtils(Context context) {
        mContext = context.getApplicationContext(); // Use application context to avoid memory leaks
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mSharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        initializeDiracSound();
        restoreSettings();
    }

    public static synchronized DiracUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DiracUtils(context);
        }
        return mInstance;
    }

    private void initializeDiracSound() {
        try {
            if (mDiracSound != null) {
                try {
                    mDiracSound.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing previous DiracSound instance", e);
                }
            }
            
            // Create DiracSound with global audio session for system-wide effect
            mDiracSound = new DiracSound(0, GLOBAL_AUDIO_SESSION);
            mDiracSound.setEnabled(true); // Keep the effect object enabled
            Log.d(TAG, "DiracSound initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize DiracSound", e);
            mDiracSound = null;
        }
    }

    private void restoreSettings() {
        if (mDiracSound == null) return;

        try {
            // Restore enabled state
            boolean enabled = mSharedPrefs.getBoolean(PREF_ENABLED, false);
            if (enabled) {
                mDiracSound.setMusic(1);
                
                // Restore other settings
                int headsetType = mSharedPrefs.getInt(PREF_HEADSET_TYPE, 0);
                mDiracSound.setHeadsetType(headsetType);
                
                int hifiMode = mSharedPrefs.getInt(PREF_HIFI_MODE, 0);
                mDiracSound.setHifiMode(hifiMode);
                
                int scenario = mSharedPrefs.getInt(PREF_SCENARIO, 0);
                mDiracSound.setScenario(scenario);
                
                String preset = mSharedPrefs.getString(PREF_PRESET, null);
                if (preset != null) {
                    setLevel(preset);
                }
                
                Log.d(TAG, "Dirac settings restored: enabled=" + enabled);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring Dirac settings", e);
        }
    }

    private void saveSettings() {
        if (mSharedPrefs == null) return;
        
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putBoolean(PREF_ENABLED, isDiracEnabled());
        editor.apply();
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
        if (mDiracSound == null) {
            if (enable) {
                initializeDiracSound();
                if (mDiracSound == null) return;
            } else {
                return;
            }
        }

        try {
            mDiracSound.setMusic(enable ? 1 : 0);
            
            // Save the state
            mSharedPrefs.edit().putBoolean(PREF_ENABLED, enable).apply();
            
            if (enable) {
                refreshPlaybackIfNecessary();
            }
            
            Log.d(TAG, "Dirac " + (enable ? "enabled" : "disabled"));
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac enabled state", e);
            // Try to reinitialize if there was an error
            if (enable) {
                initializeDiracSound();
                if (mDiracSound != null) {
                    try {
                        mDiracSound.setMusic(1);
                        mSharedPrefs.edit().putBoolean(PREF_ENABLED, true).apply();
                    } catch (Exception e2) {
                        Log.e(TAG, "Failed to reinitialize Dirac", e2);
                    }
                }
            }
        }
    }

    public boolean isDiracEnabled() {
        if (mDiracSound == null) return false;
        
        try {
            return mDiracSound.getMusic() == 1;
        } catch (Exception e) {
            Log.e(TAG, "Error checking Dirac enabled state", e);
            // Try to get from shared preferences as fallback
            return mSharedPrefs.getBoolean(PREF_ENABLED, false);
        }
    }

    public void setLevel(String preset) {
        if (mDiracSound == null) return;
        
        try {
            String[] level = preset.split("\\s*,\\s*");
            for (int band = 0; band <= level.length - 1; band++) {
                mDiracSound.setLevel(band, Float.valueOf(level[band]));
            }
            mSharedPrefs.edit().putString(PREF_PRESET, preset).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error setting Dirac level", e);
        }
    }

    public void setHeadsetType(int paramInt) {
        if (mDiracSound == null) return;
        
        try {
            mDiracSound.setHeadsetType(paramInt);
            mSharedPrefs.edit().putInt(PREF_HEADSET_TYPE, paramInt).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error setting headset type", e);
        }
    }

    public boolean getHifiMode() {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        if (audioManager != null) {
            try {
                return audioManager.getParameters("hifi_mode").contains("true");
            } catch (Exception e) {
                Log.e(TAG, "Error getting HiFi mode", e);
            }
        }
        return false;
    }

    public void setHifiMode(int paramInt) {
        if (mDiracSound == null) return;
        
        try {
            AudioManager audioManager = mContext.getSystemService(AudioManager.class);
            if (audioManager != null) {
                audioManager.setParameters("hifi_mode=" + (paramInt == 1 ? true : false));
            }
            mDiracSound.setHifiMode(paramInt);
            mSharedPrefs.edit().putInt(PREF_HIFI_MODE, paramInt).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error setting HiFi mode", e);
        }
    }

    public void setScenario(int sceneInt) {
        if (mDiracSound == null) return;
        
        try {
            mDiracSound.setScenario(sceneInt);
            mSharedPrefs.edit().putInt(PREF_SCENARIO, sceneInt).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error setting scenario", e);
        }
    }

    // Method to reinitialize if needed (can be called from BootCompletedReceiver)
    public void reinitialize() {
        Log.d(TAG, "Reinitializing Dirac");
        initializeDiracSound();
        restoreSettings();
    }

    // Cleanup method
    public void release() {
        if (mDiracSound != null) {
            try {
                mDiracSound.release();
                mDiracSound = null;
            } catch (Exception e) {
                Log.w(TAG, "Error releasing DiracSound", e);
            }
        }
    }
}
