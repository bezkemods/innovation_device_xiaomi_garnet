package org.lineageos.settings.videoenhancer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class VideoEnhancerUtils {
    private static final String TAG = "VideoEnhancerUtils";

    private static final String PREF_VIDEO_ENHANCER_ENABLED = "video_enhancer_enabled";
    private static final String PREF_MEMC_ENABLED = "memc_enabled";
    private static final String PREF_SUPER_RESOLUTION_ENABLED = "super_resolution_enabled";
    private static final String PREF_AI_HDR_ENABLED = "ai_hdr_enabled";

    // System properties for video enhancements (based on typical MIUI/Xiaomi props; adjust as needed)
    private static final String PROP_MEMC = "persist.vendor.video.memc";
    private static final String PROP_SUPER_RESOLUTION = "persist.vendor.video.superres";
    private static final String PROP_AI_HDR = "persist.vendor.video.aihdr";

    private Context mContext;
    private SharedPreferences mPrefs;
    private Boolean mHasRoot = null; // Cache root status

    public VideoEnhancerUtils(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Log.d(TAG, "VideoEnhancerUtils initialized");
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(PREF_VIDEO_ENHANCER_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_VIDEO_ENHANCER_ENABLED, enabled).apply();
    }

    public boolean isMemcEnabled() {
        return mPrefs.getBoolean(PREF_MEMC_ENABLED, false);
    }

    public boolean setMemcEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_MEMC_ENABLED, enabled).apply();
        return setSystemProp(PROP_MEMC, enabled ? "1" : "0");
    }

    public boolean isSuperResolutionEnabled() {
        return mPrefs.getBoolean(PREF_SUPER_RESOLUTION_ENABLED, false);
    }

    public boolean setSuperResolutionEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_SUPER_RESOLUTION_ENABLED, enabled).apply();
        return setSystemProp(PROP_SUPER_RESOLUTION, enabled ? "1" : "0");
    }

    public boolean isAiHdrEnabled() {
        return mPrefs.getBoolean(PREF_AI_HDR_ENABLED, false);
    }

    public boolean setAiHdrEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_AI_HDR_ENABLED, enabled).apply();
        return setSystemProp(PROP_AI_HDR, enabled ? "1" : "0");
    }

    public boolean enableVideoEnhancer() {
        try {
            if (!hasRootAccess()) {
                Log.w(TAG, "No root access, Video Enhancer may not work properly");
                // Still allow enabling, but features won't work without root
            }
            
            setEnabled(true);
            
            // Re-apply individual features if they were enabled
            boolean allSuccess = true;
            if (isMemcEnabled()) {
                if (!setMemcEnabled(true)) {
                    Log.w(TAG, "Failed to enable MEMC");
                    allSuccess = false;
                }
            }
            if (isSuperResolutionEnabled()) {
                if (!setSuperResolutionEnabled(true)) {
                    Log.w(TAG, "Failed to enable Super Resolution");
                    allSuccess = false;
                }
            }
            if (isAiHdrEnabled()) {
                if (!setAiHdrEnabled(true)) {
                    Log.w(TAG, "Failed to enable AI HDR");
                    allSuccess = false;
                }
            }
            
            Log.d(TAG, "Video Enhancer enabled" + (allSuccess ? "" : " (with some failures)"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable Video Enhancer", e);
            setEnabled(false);
            return false;
        }
    }

    public boolean disableVideoEnhancer() {
        try {
            // Disable individual features
            boolean allSuccess = true;
            if (!setMemcEnabled(false)) {
                Log.w(TAG, "Failed to disable MEMC");
                allSuccess = false;
            }
            if (!setSuperResolutionEnabled(false)) {
                Log.w(TAG, "Failed to disable Super Resolution");
                allSuccess = false;
            }
            if (!setAiHdrEnabled(false)) {
                Log.w(TAG, "Failed to disable AI HDR");
                allSuccess = false;
            }
            setEnabled(false);
            Log.d(TAG, "Video Enhancer disabled" + (allSuccess ? "" : " (with some failures)"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable Video Enhancer", e);
            return false;
        }
    }

    private boolean setSystemProp(String prop, String value) {
        if (!hasRootAccess()) {
            Log.w(TAG, "No root access, cannot set system property: " + prop);
            return false;
        }
        
        Process process = null;
        BufferedReader reader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "setprop " + prop + " " + value});
            
            // Read error stream to check for issues
            reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            StringBuilder errorOutput = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                Log.d(TAG, "Successfully set prop " + prop + " to " + value);
                return true;
            } else {
                Log.e(TAG, "Failed to set prop " + prop + ", exit code: " + exitCode);
                if (errorOutput.length() > 0) {
                    Log.e(TAG, "Error output: " + errorOutput.toString());
                }
                return false;
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to set prop " + prop, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close reader", e);
                }
            }
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to destroy process", e);
                }
            }
        }
    }

    public boolean hasRootAccess() {
        // Return cached result if already checked
        if (mHasRoot != null) {
            return mHasRoot;
        }
        
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su -c 'id'");
            int exitCode = process.waitFor();
            mHasRoot = (exitCode == 0);
            Log.d(TAG, "Root access: " + (mHasRoot ? "available" : "not available"));
            return mHasRoot;
        } catch (Exception e) {
            Log.d(TAG, "Root access not available: " + e.getMessage());
            mHasRoot = false;
            return false;
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to destroy process", e);
                }
            }
        }
    }
}
