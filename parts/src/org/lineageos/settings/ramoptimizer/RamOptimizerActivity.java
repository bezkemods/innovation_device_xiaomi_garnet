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

package org.lineageos.settings.ramoptimizer;

import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.FragmentManager;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * Main activity for RAM Optimizer settings.
 * Displays the RamOptimizerFragment within a collapsing toolbar layout.
 */
public class RamOptimizerActivity extends CollapsingToolbarBaseActivity {
    
    private static final String TAG = "RamOptimizerActivity";
    private static final String FRAGMENT_TAG = "ramoptimizer_fragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            initializeFragment(savedInstanceState);
            Log.d(TAG, "RamOptimizerActivity created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating RamOptimizerActivity", e);
        }
    }

    /**
     * Initialize and attach the RAM Optimizer fragment
     */
    private void initializeFragment(Bundle savedInstanceState) {
        // Only create fragment if this is the first creation
        if (savedInstanceState == null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            
            RamOptimizerFragment fragment = new RamOptimizerFragment();
            
            fragmentManager.beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            fragment,
                            FRAGMENT_TAG)
                    .commit();
            
            Log.d(TAG, "RamOptimizerFragment attached");
        } else {
            Log.d(TAG, "Fragment already exists, skipping recreation");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Activity resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Activity paused");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed");
    }
}
