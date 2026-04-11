/*
 * Copyright (C) 2025 KamiKaonashi
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

package org.lineageos.settings.resolution;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.os.UserHandle;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import androidx.preference.PreferenceManager;

public final class ResolutionUtils {

    private static final String TAG = "ResolutionUtils";

    // Per-app list buckets (semicolon-separated, one bucket per non-default state)
    private static final String RESOLUTION_CONTROL    = "resolutioncontrol";
    private static final String RESOLUTION_480P       = "resolution.480p";
    private static final String RESOLUTION_540P       = "resolution.540p";
    private static final String RESOLUTION_720P       = "resolution.720p";

    // System-wide baseline state key
    private static final String RESOLUTION_GLOBAL_STATE = "resolution.global_state";

    // States — index matches spinner position and modes[] bucket index
    protected static final int STATE_DEFAULT = 0;
    protected static final int STATE_480P    = 1;
    protected static final int STATE_540P    = 2;
    protected static final int STATE_720P    = 3;

    protected static boolean isAppInList = false;

    private int mCurrentState     = STATE_DEFAULT;
    private int mSavedUserDensity = -1;

    // ---------- Garnet (SM7435 / Redmi Note 13 Pro 5G) baseline ----------
    // Physical panel: 1080 × 2400 @ 395 dpi
    // These values are used as the stock fallback if DisplayManager returns
    // something unexpected (e.g. scaled mode already applied at query time).
    private static final int GARNET_STOCK_WIDTH   = 1080;
    private static final int GARNET_STOCK_HEIGHT  = 2400;
    private static final int GARNET_STOCK_DENSITY = 395;
    // ----------------------------------------------------------------------

    private static class ResolutionConfig {
        final int width;
        final int height;
        final int density;

        ResolutionConfig(int w, int h, int d) {
            width = w; height = h; density = d;
        }
    }

    // Index 0=default, 1=480p, 2=540p, 3=720p
    private static final ResolutionConfig[] RESOLUTION_CONFIGS = new ResolutionConfig[4];

    private final SharedPreferences mSharedPrefs;
    private final Context           mContext;

    private int mStockWidth;
    private int mStockHeight;
    private int mInitialDensity;

    protected ResolutionUtils(Context context) {
        mContext     = context.getApplicationContext();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        initializeStockBaselines();
        calculateResolutionConfigs();
    }

    // -------------------------------------------------------------------------
    // Service entry point
    // -------------------------------------------------------------------------

    public static void startService(Context context) {
        new ResolutionUtils(context).applyBaselineFromGlobal();
        context.startServiceAsUser(
                new Intent(context, ResolutionService.class), UserHandle.CURRENT);
    }

    // -------------------------------------------------------------------------
    // Baseline initialisation
    // -------------------------------------------------------------------------

    private void initializeStockBaselines() {
        // Try to read the real physical mode first.
        try {
            DisplayManager dm = mContext.getSystemService(DisplayManager.class);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            Display.Mode mode = d.getMode();
            int pw = mode.getPhysicalWidth();
            int ph = mode.getPhysicalHeight();

            // Sanity-check: if the system has already applied a forced size the
            // physical values may come back smaller than Garnet's native panel.
            // Fall back to the hard-coded Garnet values in that case.
            if (pw >= GARNET_STOCK_WIDTH && ph >= GARNET_STOCK_HEIGHT) {
                mStockWidth  = pw;
                mStockHeight = ph;
            } else {
                mStockWidth  = GARNET_STOCK_WIDTH;
                mStockHeight = GARNET_STOCK_HEIGHT;
            }
        } catch (Exception e) {
            mStockWidth  = GARNET_STOCK_WIDTH;
            mStockHeight = GARNET_STOCK_HEIGHT;
        }

        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            int d = wm.getInitialDisplayDensity(Display.DEFAULT_DISPLAY);
            // Accept the WM value only if it looks sane for Garnet's 395 dpi panel.
            mInitialDensity = (d >= 300 && d <= 600) ? d : GARNET_STOCK_DENSITY;
        } catch (RemoteException e) {
            mInitialDensity = GARNET_STOCK_DENSITY;
        }
    }

    private void calculateResolutionConfigs() {
        // Default — full native resolution
        RESOLUTION_CONFIGS[STATE_DEFAULT] =
                new ResolutionConfig(mStockWidth, mStockHeight, mInitialDensity);

        // For each target width: scale height to preserve aspect ratio,
        // scale density proportionally (floor at 120 to keep UI usable).
        RESOLUTION_CONFIGS[STATE_480P] = makeConfig(480);
        RESOLUTION_CONFIGS[STATE_540P] = makeConfig(540);
        RESOLUTION_CONFIGS[STATE_720P] = makeConfig(720);
    }

    private ResolutionConfig makeConfig(int targetWidth) {
        float scale   = (float) targetWidth / (float) mStockWidth;
        int   height  = Math.max(1,   Math.round(mStockHeight   * scale));
        int   density = Math.max(120, Math.round(mInitialDensity * scale));
        return new ResolutionConfig(targetWidth, height, density);
    }

    // -------------------------------------------------------------------------
    // System-wide (global) baseline
    // -------------------------------------------------------------------------

    public int getGlobalState() {
        return mSharedPrefs.getInt(RESOLUTION_GLOBAL_STATE, STATE_DEFAULT);
    }

    public void setGlobalState(int state) {
        if (state < STATE_DEFAULT || state > STATE_720P) state = STATE_DEFAULT;
        mSharedPrefs.edit().putInt(RESOLUTION_GLOBAL_STATE, state).apply();
        applyBaselineFromGlobal();
    }

    public void applyBaselineFromGlobal() {
        int s = getGlobalState();
        applyResolution(RESOLUTION_CONFIGS[s], s);
    }

    /** Alias kept for ResolutionService compatibility. */
    public void restoreDefaultResolution() {
        applyBaselineFromGlobal();
    }

    // -------------------------------------------------------------------------
    // Per-app list management
    // -------------------------------------------------------------------------

    /**
     * Storage format: three semicolon-separated buckets, each containing a
     * comma-separated list of package names followed by a trailing comma.
     *
     *   bucket[0] = 480p packages
     *   bucket[1] = 540p packages
     *   bucket[2] = 720p packages
     */
    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(RESOLUTION_CONTROL, profiles).apply();
    }

    private String getValue() {
        String value = mSharedPrefs.getString(RESOLUTION_CONTROL, null);
        if (value == null || value.isEmpty()) {
            value = RESOLUTION_480P + ";" + RESOLUTION_540P + ";" + RESOLUTION_720P;
            writeValue(value);
            return value;
        }
        String[] modes = value.split(";", -1);
        if (modes.length < 3) {
            String fixed = (modes.length > 0 ? modes[0] : RESOLUTION_480P) + ";"
                         + (modes.length > 1 ? modes[1] : RESOLUTION_540P) + ";"
                         + (modes.length > 2 ? modes[2] : RESOLUTION_720P);
            writeValue(fixed);
            return fixed;
        }
        return value;
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        // Remove the package from every bucket first
        value = value.replace(packageName + ",", "");
        String[] modes = value.split(";", -1);
        // Ensure we have 3 buckets after the replace
        while (modes.length < 3) {
            value = value + ";";
            modes = value.split(";", -1);
        }

        switch (mode) {
            case STATE_480P:
                modes[0] = modes[0] + packageName + ",";
                break;
            case STATE_540P:
                modes[1] = modes[1] + packageName + ",";
                break;
            case STATE_720P:
                modes[2] = modes[2] + packageName + ",";
                break;
            default:
                // STATE_DEFAULT → package removed from all buckets, nothing to add
                break;
        }
        writeValue(modes[0] + ";" + modes[1] + ";" + modes[2]);
    }

    protected int getStateForPackage(String packageName) {
        String[] modes = getValue().split(";", -1);
        if (modes.length > 0 && modes[0].contains(packageName + ",")) return STATE_480P;
        if (modes.length > 1 && modes[1].contains(packageName + ",")) return STATE_540P;
        if (modes.length > 2 && modes[2].contains(packageName + ",")) return STATE_720P;
        return STATE_DEFAULT;
    }

    /** Called by ResolutionService on every foreground app change. */
    protected void setResolution(String packageName) {
        String[] modes = getValue().split(";", -1);
        int globalState = getGlobalState();
        int newState    = globalState;  // start from the system baseline

        isAppInList = false;
        if (modes.length > 0 && modes[0].contains(packageName + ",")) {
            newState    = STATE_480P;
            isAppInList = true;
        } else if (modes.length > 1 && modes[1].contains(packageName + ",")) {
            newState    = STATE_540P;
            isAppInList = true;
        } else if (modes.length > 2 && modes[2].contains(packageName + ",")) {
            newState    = STATE_720P;
            isAppInList = true;
        }

        applyResolution(RESOLUTION_CONFIGS[newState], newState);
    }

    // -------------------------------------------------------------------------
    // Low-level WM application
    // -------------------------------------------------------------------------

    private void applyResolution(ResolutionConfig cfg, int newState) {
        if (newState == mCurrentState) return;

        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            if (newState != STATE_DEFAULT) {
                if (mCurrentState == STATE_DEFAULT) {
                    // Save user density before overriding it
                    mSavedUserDensity = wm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
                }
                wm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, cfg.width, cfg.height);
                wm.setForcedDisplayDensityForUser(
                        Display.DEFAULT_DISPLAY, cfg.density, UserHandle.USER_CURRENT);
            } else {
                wm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
                if (mSavedUserDensity != -1) {
                    wm.setForcedDisplayDensityForUser(
                            Display.DEFAULT_DISPLAY, mSavedUserDensity, UserHandle.USER_CURRENT);
                    mSavedUserDensity = -1;
                } else {
                    wm.clearForcedDisplayDensityForUser(
                            Display.DEFAULT_DISPLAY, UserHandle.USER_CURRENT);
                }
            }
            mCurrentState = newState;
        } catch (RemoteException e) {
            // Swallow — WM not available (e.g. early boot)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public String getResolutionString(int state) {
        if (state >= 0 && state < RESOLUTION_CONFIGS.length && RESOLUTION_CONFIGS[state] != null) {
            ResolutionConfig c = RESOLUTION_CONFIGS[state];
            return c.width + "×" + c.height;
        }
        return mStockWidth + "×" + mStockHeight;
    }
}
