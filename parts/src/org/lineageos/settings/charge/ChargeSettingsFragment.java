/*
 * Copyright (C) 2025 The LineageOS Project
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

package org.lineageos.settings.charge;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.TwoStatePreference;

import org.lineageos.settings.R;

public class ChargeSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "ChargeSettingsFragment";
    private static final String KEY_BYPASS_CHARGE = "bypass_charge";
    private static final String KEY_DEBUG_INFO = "debug_info";
    
    private TwoStatePreference mBypassChargePreference;
    private Preference mDebugInfoPreference;
    private ChargeUtils mChargeUtils;
    private Handler mHandler;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charge_settings, rootKey);

        mHandler = new Handler(Looper.getMainLooper());
        mChargeUtils = new ChargeUtils(getActivity());
        
        initializePreferences();
    }

    private void initializePreferences() {
        mBypassChargePreference = (TwoStatePreference) findPreference(KEY_BYPASS_CHARGE);
        mDebugInfoPreference = findPreference(KEY_DEBUG_INFO);

        boolean bypassChargeSupported = mChargeUtils.isBypassChargeSupported();
        Log.d(TAG, "Bypass charge supported: " + bypassChargeSupported);

        if (mBypassChargePreference != null) {
            mBypassChargePreference.setEnabled(bypassChargeSupported);
            
            if (bypassChargeSupported) {
                updateBypassChargeState();
                mBypassChargePreference.setOnPreferenceChangeListener(this);
                
                // Add summary with active node info
                String activeNode = mChargeUtils.getActiveNode();
                if (activeNode != null) {
                    mBypassChargePreference.setSummary(
                        getString(R.string.charge_bypass_summary) + "\n" +
                        getString(R.string.charge_bypass_node, activeNode)
                    );
                }
            } else {
                mBypassChargePreference.setSummary(R.string.charge_bypass_unavailable);
                mBypassChargePreference.setChecked(false);
            }
        }

        // Debug info preference (only show in development builds or when explicitly enabled)
        if (mDebugInfoPreference != null) {
            if (android.os.Build.TYPE.equals("eng") || android.os.Build.TYPE.equals("userdebug")) {
                mDebugInfoPreference.setVisible(true);
                mDebugInfoPreference.setOnPreferenceClickListener(preference -> {
                    showDebugInfo();
                    return true;
                });
            } else {
                mDebugInfoPreference.setVisible(false);
            }
        }
    }

    private void updateBypassChargeState() {
        if (mBypassChargePreference == null) return;
        
        boolean currentState = mChargeUtils.isBypassChargeEnabled();
        mBypassChargePreference.setChecked(currentState);
        Log.d(TAG, "Updated bypass charge state: " + currentState);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();

        if (KEY_BYPASS_CHARGE.equals(key)) {
            boolean bypassValue = (Boolean) newValue;
            
            if (bypassValue) {
                // Show warning dialog when enabling bypass charge
                showBypassChargeWarning(() -> {
                    enableBypassCharge(true);
                });
                return false; // Don't update UI yet, wait for user confirmation
            } else {
                // Disable bypass charge immediately
                enableBypassCharge(false);
                return true;
            }
        }
        return false;
    }

    private void showBypassChargeWarning(Runnable onConfirm) {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.charge_bypass_title)
                .setMessage(R.string.charge_bypass_warning)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // Reset the preference state
                    updateBypassChargeState();
                })
                .setCancelable(false)
                .show();
    }

    private void enableBypassCharge(boolean enable) {
        Log.d(TAG, "Attempting to " + (enable ? "enable" : "disable") + " bypass charge");
        
        boolean success = mChargeUtils.enableBypassCharge(enable);
        
        if (success) {
            // Update UI after a short delay to allow the system to process the change
            mHandler.postDelayed(() -> {
                updateBypassChargeState();
                Log.d(TAG, "Bypass charge " + (enable ? "enabled" : "disabled") + " successfully");
            }, 200);
        } else {
            // Reset to current state if operation failed
            updateBypassChargeState();
            Log.e(TAG, "Failed to " + (enable ? "enable" : "disable") + " bypass charge");
            
            // Show error message
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.error)
                    .setMessage(R.string.charge_bypass_error)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void showDebugInfo() {
        String debugInfo = mChargeUtils.getDebugInfo();
        
        new AlertDialog.Builder(getActivity())
                .setTitle("Bypass Charge Debug Info")
                .setMessage(debugInfo)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton("Refresh", (dialog, which) -> {
                    mChargeUtils.refreshActiveNode();
                    initializePreferences();
                })
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh state when returning to the fragment
        if (mChargeUtils.isBypassChargeSupported()) {
            updateBypassChargeState();
        }
    }
}
