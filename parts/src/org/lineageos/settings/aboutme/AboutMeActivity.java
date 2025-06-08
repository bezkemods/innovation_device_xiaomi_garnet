package org.lineageos.settings.aboutme;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.lineageos.settings.R;
import org.lineageos.settings.saturation.SaturationActivity;
import org.lineageos.settings.refreshrate.RefreshActivity;
import org.lineageos.settings.autohbm.AutoHbmActivity;
import org.lineageos.settings.dirac.DiracActivity;
import org.lineageos.settings.speaker.ClearSpeakerActivity;
import org.lineageos.settings.thermal.ThermalSettingsActivity;
import org.lineageos.settings.turbocharging.TurboChargingActivity;
import org.lineageos.settings.powertools.PowertoolsActivity;
import org.lineageos.settings.gamebar.GameBarSettingsActivity;
import org.lineageos.settings.zram.ZramActivity;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class AboutMeActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new AboutMeFragment())
                .commit();
    }

    public static class AboutMeFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // Ha főmenüként használod, akkor a xiaomi_parts_settings.xml-t használd
            setPreferencesFromResource(R.xml.about_me_settings, rootKey);
            
            // Display Settings
            setupPreferenceIntent("saturation_settings", SaturationActivity.class);
            setupPreferenceIntent("refresh_rate_settings", RefreshActivity.class);
            setupPreferenceIntent("auto_hbm", AutoHbmActivity.class);
            
            // Sound Settings
            setupPreferenceIntent("dirac_settings", DiracActivity.class);
            setupPreferenceIntent("clear_speaker", ClearSpeakerActivity.class);
            
            // Power & Performance
            setupPreferenceIntent("thermal_settings", ThermalSettingsActivity.class);
            setupPreferenceIntent("turbo_charging", TurboChargingActivity.class);
            setupPreferenceIntent("powertools", PowertoolsActivity.class);
            
            // Gaming
            setupPreferenceIntent("gamebar_settings", GameBarSettingsActivity.class);
            setupPreferenceIntent("zram_settings", ZramActivity.class);
            
            // Contact preference click handler
            Preference contactPreference = findPreference("about_me_contact");
            if (contactPreference != null) {
                contactPreference.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, 
                        Uri.parse("https://t.me/garnet_support"));
                    startActivity(intent);
                    return true;
                });
            }
            
            // Donate preference click handler
            Preference donatePreference = findPreference("about_me_donate");
            if (donatePreference != null) {
                donatePreference.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, 
                        Uri.parse("https://paypal.me/bezke"));
                    startActivity(intent);
                    return true;
                });
            }
        }
        
        private void setupPreferenceIntent(String key, Class<?> activityClass) {
            Preference preference = findPreference(key);
            if (preference != null) {
                preference.setOnPreferenceClickListener(pref -> {
                    Intent intent = new Intent(getActivity(), activityClass);
                    startActivity(intent);
                    return true;
                });
            }
        }
    }
}
