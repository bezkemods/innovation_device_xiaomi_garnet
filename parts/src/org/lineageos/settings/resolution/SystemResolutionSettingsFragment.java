package org.lineageos.settings.resolution;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.lineageos.settings.R;

public class SystemResolutionSettingsFragment extends Fragment
        implements AdapterView.OnItemSelectedListener {

    private ResolutionUtils mResolutionUtils;
    private Spinner         mModeSpinner;
    private TextView        mSummaryView;

    private static final int SPINNER_CUSTOM_POS = ResolutionUtils.STATE_CUSTOM;

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

        // 🔥 CUSTOM ADAPTER
        mModeSpinner.setAdapter(new ResolutionAdapter());
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

    // =========================================================
    // Spinner
    // =========================================================

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
    public void onNothingSelected(AdapterView<?> parent) {}

    // =========================================================
    // Adapter (🔥 grafikus preview)
    // =========================================================

    private class ResolutionAdapter extends ArrayAdapter<String> {

        private final LayoutInflater inflater;

        ResolutionAdapter() {
            super(getActivity(), 0,
                    getResources().getStringArray(R.array.system_resolution_entries));
            inflater = LayoutInflater.from(getActivity());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createItem(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return createItem(position, convertView, parent);
        }

        private View createItem(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.resolution_spinner_item, parent, false);
            }

            TextView title = v.findViewById(R.id.title);
            TextView subtitle = v.findViewById(R.id.subtitle);
            View preview = v.findViewById(R.id.preview_inner);

            title.setText(getItem(position));

            String detail = mResolutionUtils.getResolutionDetail(position);
            subtitle.setText(detail);

            int baseW = mResolutionUtils.getNativeWidth();
            int baseH = mResolutionUtils.getNativeHeight();

            int[] cfg;
            if (position == ResolutionUtils.STATE_CUSTOM) {
                cfg = mResolutionUtils.getCustomConfig();
            } else {
                String[] split = detail.split("×|@");
                cfg = new int[]{
                        Integer.parseInt(split[0].trim()),
                        Integer.parseInt(split[1].trim())
                };
            }

            float scaleW = (float) cfg[0] / baseW;
            float scaleH = (float) cfg[1] / baseH;

            int w = Math.max(8, (int)(30 * scaleW));
            int h = Math.max(12, (int)(60 * scaleH));

            preview.getLayoutParams().width  = w;
            preview.getLayoutParams().height = h;

            // 🎨 performance hint
            if (position == ResolutionUtils.STATE_480P) {
                preview.setBackgroundColor(0xFF4CAF50); // zöld
            } else if (position == ResolutionUtils.STATE_720P) {
                preview.setBackgroundColor(0xFFFFC107); // sárga
            } else {
                preview.setBackgroundColor(0xFFFFFFFF);
            }

            preview.requestLayout();

            return v;
        }
    }

    // =========================================================
    // Custom dialog (unchanged)
    // =========================================================

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

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.custom_resolution_dialog_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        int w = Integer.parseInt(etWidth.getText().toString().trim());
                        int h = Integer.parseInt(etHeight.getText().toString().trim());
                        int d = Integer.parseInt(etDensity.getText().toString().trim());

                        if (w < 240 || h < 320 || d < 80) throw new Exception();

                        mResolutionUtils.setCustomConfig(w, h, d);
                        mResolutionUtils.setGlobalState(ResolutionUtils.STATE_CUSTOM);
                        updateSummary(ResolutionUtils.STATE_CUSTOM);

                    } catch (Exception e) {
                        Toast.makeText(getActivity(),
                                R.string.custom_resolution_invalid,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateSummary(int state) {
        String detail = mResolutionUtils.getResolutionDetail(state);
        mSummaryView.setText(getString(R.string.system_resolution_summary, detail));
    }
}
