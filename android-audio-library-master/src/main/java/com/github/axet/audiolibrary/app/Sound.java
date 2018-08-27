package com.github.axet.audiolibrary.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.sound.AudioTrack;

public class Sound extends com.github.axet.androidlibrary.sound.Sound {
    public static String TAG = Sound.class.getSimpleName();

    // quite room gives me 20db
    public static int NOISE_DB = 20;
    // max 90 dB detection for android mic
    public static int MAXIMUM_DB = 90;
    public static int SOUND_STREAM = AudioManager.STREAM_MUSIC;
    public static int SOUND_CHANNEL = AudioAttributes.USAGE_MEDIA;
    public static int SOUND_TYPE = AudioAttributes.CONTENT_TYPE_MUSIC;

    public Sound(Context context) {
        super(context);
    }

    public void silent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.silent();
        }
    }

    public void unsilent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.unsilent();
        }
    }

    public static int getChannels(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int i = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_CHANNELS, "1"));
        return i;
    }

    public static int getInMode(Context context) {
        switch (getChannels(context)) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;
            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;
            default:
                throw new RuntimeException("unknown mode");
        }
    }

    public static int getOutMode(Context context) {
        switch (getChannels(context)) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            default:
                throw new RuntimeException("unknown mode");
        }
    }

    public static int indexOf(int[] ss, int s) {
        for (int i = 0; i < ss.length; i++) {
            if (ss[i] == s)
                return i;
        }
        return -1;
    }

    public static int getSampleRate(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        sampleRate = Sound.getValidRecordRate(getInMode(context), sampleRate);
        if (sampleRate == -1)
            sampleRate = Sound.DEFAULT_RATE;
        return sampleRate;
    }

    public static AudioRecord createAudioRecorder(Context context, int sampleRate, int[] ss, int i) {
        AudioRecord r = null;

        int c = getInMode(context);
        final int min = AudioRecord.getMinBufferSize(sampleRate, c, Sound.DEFAULT_AUDIOFORMAT);
        if (min <= 0)
            throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");

        for (; i < ss.length; i++) {
            int s = ss[i];
            try {
                r = new AudioRecord(s, sampleRate, c, Sound.DEFAULT_AUDIOFORMAT, min);
                if (r.getState() == AudioRecord.STATE_INITIALIZED)
                    return r;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Recorder Create Failed: " + s, e);
            }
        }
        if (r == null || r.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new RuntimeException("Unable to initialize AudioRecord");
        }

        return r;
    }

    public static void throwError(int readSize) {
        switch (readSize) {
            case AudioRecord.ERROR:
                throw new RuntimeException("AudioRecord.ERROR");
            case AudioRecord.ERROR_BAD_VALUE:
                throw new RuntimeException("AudioRecord.ERROR_BAD_VALUE");
            case AudioRecord.ERROR_INVALID_OPERATION:
                throw new RuntimeException("AudioRecord.ERROR_INVALID_OPERATION");
            case AudioRecord.ERROR_DEAD_OBJECT:
                throw new RuntimeException("AudioRecord.ERROR_DEAD_OBJECT");
        }
    }
}
