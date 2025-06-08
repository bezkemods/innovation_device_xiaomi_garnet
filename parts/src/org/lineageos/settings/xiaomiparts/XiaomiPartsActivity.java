package org.lineageos.settings.xiaomiparts;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.widget.ListView;

import org.lineageos.settings.R;
import org.lineageos.settings.thermal.ThermalSettingsActivity;
import org.lineageos.settings.dirac.DiracActivity;
import org.lineageos.settings.speaker.ClearSpeakerActivity;
import org.lineageos.settings.saturation.SaturationActivity;
import org.lineageos.settings.refreshrate.RefreshActivity;
import org.lineageos.settings.autohbm.AutoHbmActivity;
import org.lineageos.settings.gamebar.GameBarSettingsActivity;
import org.lineageos.settings.powertools.PowertoolsActivity;
import org.lineageos.settings.turbocharging.TurboChargingActivity;
import org.lineageos.settings.aboutme.AboutMeActivity;

public class XiaomiPartsActivity extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    private static final String KEY_THERMAL = "thermal_settings";
    private static final String KEY_DIRAC = "dirac_settings";
    private static final String KEY_CLEAR_SPEAKER = "clear_speaker";
    private static final String KEY_SATURATION = "saturation_settings";
    private static final String KEY_REFRESH_RATE = "refresh_rate_settings";
    private static final String KEY_AUTO_HBM = "auto_hbm";
    private static final String KEY_GAMEBAR = "gamebar_settings";
    private static final String KEY_POWERTOOLS = "powertools";
    private static final String KEY_TURBO_CHARGING = "turbo_charging";
    private static final String KEY_ABOUTME = "about_me_settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.xiaomi_parts_settings);

        // Remove dividers...
        ListView listView = getListView();
        listView.setDivider(null); 
        listView.setDividerHeight(0);

        setupPreferences();
    }

    private void setupPreferences() {
        PreferenceScreen prefScreen = getPreferenceScreen();

        Preference thermalPref = findPreference(KEY_THERMAL);
        if (thermalPref != null) {
            thermalPref.setOnPreferenceClickListener(this);
        }

        Preference diracPref = findPreference(KEY_DIRAC);
        if (diracPref != null) {
            diracPref.setOnPreferenceClickListener(this);
        }

        Preference speakerPref = findPreference(KEY_CLEAR_SPEAKER);
        if (speakerPref != null) {
            speakerPref.setOnPreferenceClickListener(this);
        }

        Preference saturationPref = findPreference(KEY_SATURATION);
        if (saturationPref != null) {
            saturationPref.setOnPreferenceClickListener(this);
        }

        Preference refreshRatePref = findPreference(KEY_REFRESH_RATE);
        if (refreshRatePref != null) {
            refreshRatePref.setOnPreferenceClickListener(this);
        }

        Preference autoHbmPref = findPreference(KEY_AUTO_HBM);
        if (autoHbmPref != null) {
            autoHbmPref.setOnPreferenceClickListener(this);
        }

        Preference gamebarPref = findPreference(KEY_GAMEBAR);
        if (gamebarPref != null) {
            gamebarPref.setOnPreferenceClickListener(this);
        }

        Preference powertoolsPref = findPreference(KEY_POWERTOOLS);
        if (powertoolsPref != null) {
            powertoolsPref.setOnPreferenceClickListener(this);
        }

        Preference turboChargingPref = findPreference(KEY_TURBO_CHARGING);
        if (turboChargingPref != null) {
            turboChargingPref.setOnPreferenceClickListener(this);
        }
    }      
        Preference aboutMePref = findPreference(KEY_ABOUTME);
        if (aboutMePref != null) {
            aboutMePref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(this, AboutMeActivity.class);
                startActivity(intent);
                return true;
            });
        }
   }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        Intent intent = null;

        switch (key) {
            case KEY_THERMAL:
                intent = new Intent(this, ThermalSettingsActivity.class);
                break;
            case KEY_DIRAC:
                intent = new Intent(this, DiracActivity.class);
                break;
            case KEY_CLEAR_SPEAKER:
                intent = new Intent(this, ClearSpeakerActivity.class);
                break;
            case KEY_SATURATION:
                intent = new Intent(this, SaturationActivity.class);
                break;
            case KEY_REFRESH_RATE:
                intent = new Intent(this, RefreshActivity.class);
                break;
            case KEY_AUTO_HBM:
                intent = new Intent(this, AutoHbmActivity.class);
                break;
            case KEY_GAMEBAR:
                intent = new Intent(this, GameBarSettingsActivity.class);
                break;
            case KEY_POWERTOOLS:
                intent = new Intent(this, PowertoolsActivity.class);
                break;
            case KEY_TURBO_CHARGING:
                intent = new Intent(this, TurboChargingActivity.class);
                break;
            case KEY_ABOUTME:
                intent = new Intent(this, AboutMeActivity.class);
                break;
        }

        if (intent != null) {
            startActivity(intent);
            return true;
        }

        return false;
    }
}

