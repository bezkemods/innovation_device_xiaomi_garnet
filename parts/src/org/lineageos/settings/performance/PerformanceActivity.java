/*
 * Copyright (C) 2025 bezke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.lineageos.settings.performance;

import android.os.Bundle;
import android.view.MenuItem;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import org.lineageos.settings.R;

public class PerformanceActivity extends CollapsingToolbarBaseActivity {
    private static final String TAG_PERFORMANCE = "performance";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Használd a Support Fragment Managert
        getSupportFragmentManager().beginTransaction()
            .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    new PerformanceFragment(), TAG_PERFORMANCE)
            .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
