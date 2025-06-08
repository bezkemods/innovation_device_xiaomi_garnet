package org.lineageos.settings.aboutme;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.lineageos.settings.R;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class AboutMeActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    // setTitle(R.string.about_me_title);

    getSupportFragmentManager()
            .beginTransaction()
            .replace(android.R.id.content, new AboutMeFragment())
            .commit();
}
    public static class AboutMeFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.about_me_settings, rootKey);

            // Contact
            Preference contactPreference = findPreference("about_me_contact");
            if (contactPreference != null) {
                contactPreference.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://t.me/garnet_support"));
                    startActivity(intent);
                    return true;
                });
            }

            // Donate
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
    }
}
