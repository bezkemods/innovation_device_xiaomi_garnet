package org.lineageos.settings.turbocharging;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;
import org.lineageos.settings.R;

public class TurboChargingFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private MainSwitchPreference mTurboEnabled;
    private SwitchPreferenceCompat mSportsMode;
    private ListPreference mTurboCurrent;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            setPreferencesFromResource(R.xml.turbocharging, rootKey);
            initializePreferences();
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error creating preferences", e);
        }
    }

    private void initializePreferences() {
        mTurboEnabled = findPreference(TurboChargingConstants.PREF_TURBO_ENABLED);
        if (mTurboEnabled != null) {
            mTurboEnabled.setOnPreferenceChangeListener(this);
        }

        mSportsMode = findPreference(TurboChargingConstants.PREF_SPORTS_MODE);
        if (mSportsMode != null) {
            mSportsMode.setOnPreferenceChangeListener(this);
            mSportsMode.setEnabled(mTurboEnabled != null && mTurboEnabled.isChecked());
        }

        mTurboCurrent = findPreference(TurboChargingConstants.PREF_TURBO_CURRENT);
        if (mTurboCurrent != null) {
            mTurboCurrent.setOnPreferenceChangeListener(this);
            mTurboCurrent.setEnabled(mTurboEnabled != null && mTurboEnabled.isChecked());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context context = getContext();
        if (context == null) {
            Log.w(TurboChargingConstants.TAG, "Context is null in onPreferenceChange");
            return false;
        }

        try {
            if (preference == mTurboEnabled) {
                return handleTurboEnabledChange(context, (Boolean) newValue);
            } else if (preference == mSportsMode) {
                return handleSportsModeChange(context, (Boolean) newValue);
            } else if (preference == mTurboCurrent) {
                return handleTurboCurrentChange(context, (String) newValue);
            }
        } catch (Exception e) {
            Log.e(TurboChargingConstants.TAG, "Error handling preference change", e);
            showErrorToast(context);
        }
        
        return false;
    }

    private boolean handleTurboEnabledChange(Context context, boolean turboEnabled) {
        if (mTurboCurrent != null) {
            mTurboCurrent.setEnabled(turboEnabled);
        }
        if (mSportsMode != null) {
            mSportsMode.setEnabled(turboEnabled);
            if (!turboEnabled) {
                mSportsMode.setChecked(false);
            }
        }
        
        TurboChargingUtil.applyTurboAndSportsSettings(context);
        
        String message = turboEnabled ? getString(R.string.toast_turbo_on) : getString(R.string.toast_turbo_off);
        showToast(context, message);
        
        return true;
    }

    private boolean handleSportsModeChange(Context context, boolean sportsEnabled) {
        TurboChargingUtil.applyTurboAndSportsSettings(context);
        
        String message = sportsEnabled ? getString(R.string.toast_sports_on) : getString(R.string.toast_sports_off);
        showToast(context, message);
        
        return true;
    }

    private boolean handleTurboCurrentChange(Context context, String newValue) {
        TurboChargingUtil.applyTurboAndSportsSettings(context);
        
        if (mTurboCurrent != null) {
            CharSequence[] entries = mTurboCurrent.getEntries();
            int index = mTurboCurrent.findIndexOfValue(newValue);
            
            if (entries != null && index >= 0 && index < entries.length) {
                CharSequence entry = entries[index];
                String message = String.format(getString(R.string.toast_wattage_set), entry);
                showToast(context, message);
            }
        }
        
        return true;
    }

    private void showToast(Context context, String message) {
        if (context != null && message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showErrorToast(Context context) {
        if (context != null) {
            Toast.makeText(context, "Error applying settings", Toast.LENGTH_SHORT).show();
        }
    }
}
