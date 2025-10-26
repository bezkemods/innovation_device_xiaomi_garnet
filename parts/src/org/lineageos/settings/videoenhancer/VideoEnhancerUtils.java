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

    // System properties for video enhancements
    // These properties control Xiaomi's AI Image Engine built into HyperOS
    // MEMC: Motion Estimation and Motion Compensation - adds interpolated frames for smoother motion
    // Super Resolution: AI-powered upscaling - enhances video quality from 720p to near-1080p
    // AI HDR: High Dynamic Range enhancement - optimizes colors and contrast
    // Note: Actual property names may vary by device and HyperOS version
    // Compatible with: Redmi Note 13 Pro 5G (Garnet), Xiaomi 14/15 series, and other HyperOS devices
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
        boolean success = setSystemProp(PROP_MEMC, enabled ? "1" : "0");
        if (success) {
            mPrefs.edit().putBoolean(PREF_MEMC_ENABLED, enabled).apply();
        }
        return success;
    }

    public boolean isSuperResolutionEnabled() {
        return mPrefs.getBoolean(PREF_SUPER_RESOLUTION_ENABLED, false);
    }

    public boolean setSuperResolutionEnabled(boolean enabled) {
        boolean success = setSystemProp(PROP_SUPER_RESOLUTION, enabled ? "1" : "0");
        if (success) {
            mPrefs.edit().putBoolean(PREF_SUPER_RESOLUTION_ENABLED, enabled).apply();
        }
        return success;
    }

    public boolean isAiHdrEnabled() {
        return mPrefs.getBoolean(PREF_AI_HDR_ENABLED, false);
    }

    public boolean setAiHdrEnabled(boolean enabled) {
        boolean success = setSystemProp(PROP_AI_HDR, enabled ? "1" : "0");
        if (success) {
            mPrefs.edit().putBoolean(PREF_AI_HDR_ENABLED, enabled).apply();
        }
        return success;
    }

    public boolean enableVideoEnhancer() {
        try {
            if (!hasRootAccess()) {
                Log.w(TAG, "No root access - Video Enhancer features require root to modify system properties");
                Log.w(TAG, "Features will be marked as enabled but may not function without root access");
                // Still allow enabling in preferences, but warn that it won't work
            }
            
            setEnabled(true);
            
            // Re-apply individual features if they were enabled
            // Note: These features work best with:
            // - Display color scheme set to "Vivid" or "Saturated"
            // - Supported apps: YouTube, Netflix, Gallery, most video players
            // - May increase battery usage during active video playback
            boolean allSuccess = true;
            
            if (isMemcEnabled()) {
                if (!setSystemProp(PROP_MEMC, "1")) {
                    Log.w(TAG, "Failed to enable MEMC");
                    allSuccess = false;
                    // Reset preference if system prop failed
                    mPrefs.edit().putBoolean(PREF_MEMC_ENABLED, false).apply();
                }
            }
            
            if (isSuperResolutionEnabled()) {
                if (!setSystemProp(PROP_SUPER_RESOLUTION, "1")) {
                    Log.w(TAG, "Failed to enable Super Resolution");
                    allSuccess = false;
                    mPrefs.edit().putBoolean(PREF_SUPER_RESOLUTION_ENABLED, false).apply();
                }
            }
            
            if (isAiHdrEnabled()) {
                if (!setSystemProp(PROP_AI_HDR, "1")) {
                    Log.w(TAG, "Failed to enable AI HDR");
                    allSuccess = false;
                    mPrefs.edit().putBoolean(PREF_AI_HDR_ENABLED, false).apply();
                }
            }
            
            Log.d(TAG, "Video Enhancer enabled" + (allSuccess ? "" : " (with some failures)"));
            return allSuccess;
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
            
            if (!setSystemProp(PROP_MEMC, "0")) {
                Log.w(TAG, "Failed to disable MEMC");
                allSuccess = false;
            } else {
                mPrefs.edit().putBoolean(PREF_MEMC_ENABLED, false).apply();
            }
            
            if (!setSystemProp(PROP_SUPER_RESOLUTION, "0")) {
                Log.w(TAG, "Failed to disable Super Resolution");
                allSuccess = false;
            } else {
                mPrefs.edit().putBoolean(PREF_SUPER_RESOLUTION_ENABLED, false).apply();
            }
            
            if (!setSystemProp(PROP_AI_HDR, "0")) {
                Log.w(TAG, "Failed to disable AI HDR");
                allSuccess = false;
            } else {
                mPrefs.edit().putBoolean(PREF_AI_HDR_ENABLED, false).apply();
            }
            
            setEnabled(false);
            Log.d(TAG, "Video Enhancer disabled" + (allSuccess ? "" : " (with some failures)"));
            return allSuccess;
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
