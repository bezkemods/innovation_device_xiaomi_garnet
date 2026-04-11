/*
 * Copyright (C) 2025 kenway214
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

package org.lineageos.settings.chargecontrol;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.MainSwitchPreference;

import org.lineageos.settings.Constants;
import org.lineageos.settings.CustomSeekBarPreference;
import org.lineageos.settings.R;

public class ChargeControlFragment extends PreferenceFragmentCompat
        implements OnCheckedChangeListener, Preference.OnPreferenceChangeListener {

    private MainSwitchPreference mChargeControlSwitch;
    private CustomSeekBarPreference mStopChargingPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charge_control, rootKey);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        mChargeControlSwitch = findPreference(Constants.KEY_CHARGE_CONTROL);
        mChargeControlSwitch.setChecked(sharedPrefs.getBoolean(Constants.KEY_CHARGE_CONTROL, false));
        mChargeControlSwitch.addOnSwitchChangeListener(this);

        mStopChargingPreference = findPreference(Constants.KEY_STOP_CHARGING);

        // Node check via root — runs on a background thread to avoid StrictMode / ANR
        new Thread(() -> {
            boolean accessible = ChargeControlUtils.isNodeAccessible();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (accessible) {
                    mStopChargingPreference.setValue(
                            sharedPrefs.getInt(Constants.KEY_STOP_CHARGING,
                                    Constants.DEFAULT_STOP_CHARGING));
                    mStopChargingPreference.setOnPreferenceChangeListener(this);
                } else {
                    mStopChargingPreference.setSummary(getString(R.string.kernel_node_access_error));
                    mStopChargingPreference.setEnabled(false);
                }
            });
        }).start();

        mStopChargingPreference.setVisible(mChargeControlSwitch.isChecked());
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.edit().putBoolean(Constants.KEY_CHARGE_CONTROL, isChecked).apply();
        mStopChargingPreference.setVisible(isChecked);

        if (!isChecked) {
            // Re-enable charging when control is turned off
            new Thread(() -> ChargeControlUtils.setChargingSuspended(false)).start();
        } else {
            // Immediately apply current threshold
            applyThreshold(prefs.getInt(Constants.KEY_STOP_CHARGING,
                    Constants.DEFAULT_STOP_CHARGING));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mStopChargingPreference) {
            int value = (int) newValue;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putInt(Constants.KEY_STOP_CHARGING, value).apply();
            mStopChargingPreference.refresh(value);
            Toast.makeText(getContext(),
                    getString(R.string.stop_charging_set_to, value),
                    Toast.LENGTH_SHORT).show();
            applyThreshold(value);
            return true;
        }
        return false;
    }

    /**
     * Reads current battery level and suspends/resumes charging based on threshold.
     */
    private void applyThreshold(int threshold) {
        Context ctx = getContext();
        if (ctx == null) return;
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = ctx.registerReceiver(null, ifilter);
        if (batteryStatus == null) return;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = (int) ((level / (float) scale) * 100);
        boolean shouldSuspend = percent >= threshold;

        new Thread(() -> ChargeControlUtils.setChargingSuspended(shouldSuspend)).start();
    }

    public static void restoreStopChargingSetting(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean(Constants.KEY_CHARGE_CONTROL, false);
        if (!enabled) {
            new Thread(() -> ChargeControlUtils.setChargingSuspended(false)).start();
            return;
        }
        int threshold = prefs.getInt(Constants.KEY_STOP_CHARGING, Constants.DEFAULT_STOP_CHARGING);
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        if (batteryStatus == null) return;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = (int) ((level / (float) scale) * 100);
        boolean shouldSuspend = percent >= threshold;
        new Thread(() -> ChargeControlUtils.setChargingSuspended(shouldSuspend)).start();
    }
}
