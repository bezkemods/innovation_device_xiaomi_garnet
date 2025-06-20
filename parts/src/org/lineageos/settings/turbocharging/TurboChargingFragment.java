package org.lineageos.settings.turbocharging;

import android.os.Bundle;
import android.widget.Toast;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;
import org.lineageos.settings.R;

public class TurboChargingFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String PREF_TURBO_ENABLED = "turbo_enable";
    private static final String PREF_SPORTS_MODE = "sports_mode";
    private static final String PREF_TURBO_CURRENT = "turbo_current";

    private MainSwitchPreference mTurboEnabled;
    private SwitchPreferenceCompat mSportsMode;
    private ListPreference mTurboCurrent;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.turbocharging, rootKey);

        mTurboEnabled = (MainSwitchPreference) findPreference(PREF_TURBO_ENABLED);
        mTurboEnabled.setOnPreferenceChangeListener(this);

        mSportsMode = (SwitchPreferenceCompat) findPreference(PREF_SPORTS_MODE);
        mSportsMode.setOnPreferenceChangeListener(this);
        mSportsMode.setEnabled(mTurboEnabled.isChecked());

        mTurboCurrent = (ListPreference) findPreference(PREF_TURBO_CURRENT);
        mTurboCurrent.setOnPreferenceChangeListener(this);
        mTurboCurrent.setEnabled(mTurboEnabled.isChecked());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mTurboEnabled) {
            boolean turboEnabled = (boolean) newValue;
            mTurboCurrent.setEnabled(turboEnabled);
            mSportsMode.setEnabled(turboEnabled);
            if (!turboEnabled) {
                mSportsMode.setChecked(false);
            }
            TurboChargingUtil.applyTurboAndSportsSettings(getActivity());
            Toast.makeText(getActivity(),
                    turboEnabled ? getString(R.string.toast_turbo_on) : getString(R.string.toast_turbo_off),
                    Toast.LENGTH_SHORT).show();
            return true;
        } else if (preference == mSportsMode) {
            TurboChargingUtil.applyTurboAndSportsSettings(getActivity());
            Toast.makeText(getActivity(),
                    (boolean) newValue ? getString(R.string.toast_sports_on) : getString(R.string.toast_sports_off),
                    Toast.LENGTH_SHORT).show();
            return true;
        } else if (preference == mTurboCurrent) {
            TurboChargingUtil.applyTurboAndSportsSettings(getActivity());
            CharSequence entry = mTurboCurrent.getEntries()[mTurboCurrent.findIndexOfValue((String) newValue)];
            Toast.makeText(getActivity(),
                    String.format(getString(R.string.toast_wattage_set), entry),
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }
}
