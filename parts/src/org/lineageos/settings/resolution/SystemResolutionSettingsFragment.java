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

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.lineageos.settings.R;

/**
 * System-wide resolution selector.
 *
 * Spinner entries: Default | 480p | 540p | 720p | Custom
 *
 * Selecting "Custom" opens a dialog that lets the user input:
 *   - Width  (px)
 *   - Height (px)
 *   - Density (dpi)
 *
 * After saving, the custom config is persisted and the system resolution is applied.
 */
public class SystemResolutionSettingsFragment extends Fragment
        implements AdapterView.OnItemSelectedListener {

    private ResolutionUtils mResolutionUtils;
    private Spinner         mModeSpinner;
    private TextView        mSummaryView;

    /** Position of STATE_CUSTOM in the spinner. */
    private static final int SPINNER_CUSTOM_POS = ResolutionUtils.STATE_CUSTOM; // = 4

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.system_resolution_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mResolutionUtils = new ResolutionUtils(getActivity());

        mSummaryView = view.findViewById(R.id.system_resolution_summary);
        mModeSpinner = view.findViewById(R.id.system_resolution_mode);

        // Build adapter from string-array (must include "Custom" entry at index 4)
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                getActivity(),
                R.array.system_resolution_entries,   // 5 entries: Default, 480p, 540p, 720p, Custom
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mModeSpinner.setAdapter(adapter);
        mModeSpinner.setOnItemSelectedListener(this);

        int current = mResolutionUtils.getGlobalState();
        mModeSpinner.setSelection(current, false);
        updateSummary(current);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(getString(R.string.system_resolution_title));
    }

    // -------------------------------------------------------------------------
    // Spinner callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int current = mResolutionUtils.getGlobalState();

        if (position == SPINNER_CUSTOM_POS) {
            showCustomResolutionDialog();
            return;
        }

        if (current != position) {
            mResolutionUtils.setGlobalState(position);
            updateSummary(position);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }

    // -------------------------------------------------------------------------
    // Custom resolution dialog
    // -------------------------------------------------------------------------

    private void showCustomResolutionDialog() {
        int[] cfg = mResolutionUtils.getCustomConfig();

        final View dialogView = LayoutInflater.from(getActivity())
                .inflate(R.layout.custom_resolution_dialog, null);

        final EditText etWidth   = dialogView.findViewById(R.id.et_custom_width);
        final EditText etHeight  = dialogView.findViewById(R.id.et_custom_height);
        final EditText etDensity = dialogView.findViewById(R.id.et_custom_density);

        etWidth.setText(String.valueOf(cfg[0]));
        etHeight.setText(String.valueOf(cfg[1]));
        etDensity.setText(String.valueOf(cfg[2]));

        // Hint: show native panel values
        etWidth.setHint(getString(R.string.custom_resolution_width_hint,
                mResolutionUtils.getNativeWidth()));
        etHeight.setHint(getString(R.string.custom_resolution_height_hint,
                mResolutionUtils.getNativeHeight()));
        etDensity.setHint(getString(R.string.custom_resolution_density_hint,
                mResolutionUtils.getNativeDensity()));

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.custom_resolution_dialog_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    applyCustomFromDialog(etWidth, etHeight, etDensity);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // Revert spinner to previous selection
                    int prev = mResolutionUtils.getGlobalState();
                    mModeSpinner.setSelection(prev, false);
                })
                .setNeutralButton(R.string.custom_resolution_reset, (dialog, which) -> {
                    resetToNative();
                })
                .setOnCancelListener(d -> {
                    int prev = mResolutionUtils.getGlobalState();
                    mModeSpinner.setSelection(prev, false);
                })
                .show();
    }

    private void applyCustomFromDialog(EditText etWidth, EditText etHeight, EditText etDensity) {
        try {
            int w = Integer.parseInt(etWidth.getText().toString().trim());
            int h = Integer.parseInt(etHeight.getText().toString().trim());
            int d = Integer.parseInt(etDensity.getText().toString().trim());

            if (w < 240 || h < 320 || d < 80) {
                Toast.makeText(getActivity(),
                        R.string.custom_resolution_invalid, Toast.LENGTH_SHORT).show();
                mModeSpinner.setSelection(mResolutionUtils.getGlobalState(), false);
                return;
            }

            mResolutionUtils.setCustomConfig(w, h, d);
            mResolutionUtils.setGlobalState(ResolutionUtils.STATE_CUSTOM);
            updateSummary(ResolutionUtils.STATE_CUSTOM);
        } catch (NumberFormatException e) {
            Toast.makeText(getActivity(),
                    R.string.custom_resolution_invalid, Toast.LENGTH_SHORT).show();
            mModeSpinner.setSelection(mResolutionUtils.getGlobalState(), false);
        }
    }

    private void resetToNative() {
        mResolutionUtils.setCustomConfig(
                mResolutionUtils.getNativeWidth(),
                mResolutionUtils.getNativeHeight(),
                mResolutionUtils.getNativeDensity());
        mResolutionUtils.setGlobalState(ResolutionUtils.STATE_DEFAULT);
        mModeSpinner.setSelection(ResolutionUtils.STATE_DEFAULT, false);
        updateSummary(ResolutionUtils.STATE_DEFAULT);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateSummary(int state) {
        String detail = mResolutionUtils.getResolutionDetail(state);
        mSummaryView.setText(getString(R.string.system_resolution_summary, detail));
    }
}
