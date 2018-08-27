package com.github.axet.audiolibrary.widgets;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.github.axet.androidlibrary.widgets.SeekBarPreference;
import com.github.axet.audiolibrary.R;
import com.github.axet.audiolibrary.filters.AmplifierFilter;

public class RecordingVolumePreference extends SeekBarPreference {

    public static void show(Fragment f, String key) {
        DialogFragment d = DialogFragment.newInstance(key);
        d.setTargetFragment(f, 0);
        d.show(f.getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
    }

    public static class DialogFragment extends SeekBarPreferenceDialogFragment {

        public static DialogFragment newInstance(String key) {
            DialogFragment fragment = new DialogFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            seekBar.setMax(AmplifierFilter.MAX * 100);
            seekBar.setProgress((int) (value * 100));
            builder.setNeutralButton(R.string.default_button, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final AlertDialog d = (AlertDialog) super.onCreateDialog(savedInstanceState);
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = d.getButton(DialogInterface.BUTTON_NEUTRAL);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mPreferenceChanged = true;
                            value = 1;
                            seekBar.setProgress((int) (value * 100));
                            updateText();
                        }
                    });
                }
            });
            return d;
        }
    }

    public RecordingVolumePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public String format(float value) {
        return super.format(value);
    }
}
