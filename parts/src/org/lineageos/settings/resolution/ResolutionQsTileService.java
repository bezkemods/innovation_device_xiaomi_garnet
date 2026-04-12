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

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.lineageos.settings.R;

/**
 * Quick Settings tile that cycles through resolution states:
 *   Default → 480p → 540p → 720p → Custom → Default → …
 *
 * The tile skips STATE_CUSTOM if no custom resolution has been configured.
 *
 * Register in AndroidManifest.xml inside <application>:
 *
 *   <service
 *       android:name=".resolution.ResolutionQsTileService"
 *       android:exported="true"
 *       android:icon="@drawable/ic_resolution_qs"
 *       android:label="@string/resolution_qs_label"
 *       android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
 *       <intent-filter>
 *           <action android:name="android.service.quicksettings.action.QS_TILE" />
 *       </intent-filter>
 *   </service>
 */
public class ResolutionQsTileService extends TileService {

    private static final String TAG = "ResolutionQsTile";

    private ResolutionUtils mResolutionUtils;

    @Override
    public void onStartListening() {
        mResolutionUtils = new ResolutionUtils(this);
        updateTile();
    }

    @Override
    public void onClick() {
        if (mResolutionUtils == null) {
            mResolutionUtils = new ResolutionUtils(this);
        }

        int current = mResolutionUtils.getGlobalState();
        int next    = nextState(current);
        Log.d(TAG, "onClick: " + current + " → " + next);

        mResolutionUtils.setGlobalState(next);
        updateTile();
    }

    // -------------------------------------------------------------------------
    // Tile update
    // -------------------------------------------------------------------------
    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        int state = mResolutionUtils.getGlobalState();

        tile.setLabel(getLabel(state));
        tile.setSubtitle(mResolutionUtils.getResolutionString(state));
        tile.setIcon(Icon.createWithResource(this, getIcon(state)));
        tile.setState(state == ResolutionUtils.STATE_DEFAULT ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.updateTile();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Cycles: DEFAULT → 480p → 540p → 720p → CUSTOM (if configured) → DEFAULT
     */
    private int nextState(int current) {
        switch (current) {
            case ResolutionUtils.STATE_DEFAULT: return ResolutionUtils.STATE_480P;
            case ResolutionUtils.STATE_480P:    return ResolutionUtils.STATE_540P;
            case ResolutionUtils.STATE_540P:    return ResolutionUtils.STATE_720P;
            case ResolutionUtils.STATE_720P:
                // Only go to CUSTOM if it's been configured (differs from native)
                if (hasCustomConfig()) return ResolutionUtils.STATE_CUSTOM;
                return ResolutionUtils.STATE_DEFAULT;
            case ResolutionUtils.STATE_CUSTOM:
            default:
                return ResolutionUtils.STATE_DEFAULT;
        }
    }

    private boolean hasCustomConfig() {
        int[] cfg = mResolutionUtils.getCustomConfig();
        return cfg[0] != mResolutionUtils.getNativeWidth()
            || cfg[1] != mResolutionUtils.getNativeHeight();
    }

    private String getLabel(int state) {
        switch (state) {
            case ResolutionUtils.STATE_480P:   return getString(R.string.resolution_480p);
            case ResolutionUtils.STATE_540P:   return getString(R.string.resolution_540p);
            case ResolutionUtils.STATE_720P:   return getString(R.string.resolution_720p);
            case ResolutionUtils.STATE_CUSTOM: return getString(R.string.resolution_custom);
            default:                           return getString(R.string.resolution_default);
        }
    }

    private int getIcon(int state) {
        switch (state) {
            case ResolutionUtils.STATE_480P:   return R.drawable.ic_resolution_480;
            case ResolutionUtils.STATE_540P:   return R.drawable.ic_resolution_540;
            case ResolutionUtils.STATE_720P:   return R.drawable.ic_resolution_720;
            case ResolutionUtils.STATE_CUSTOM: return R.drawable.ic_resolution_custom;
            default:                           return R.drawable.ic_resolution_default;
        }
    }
}
