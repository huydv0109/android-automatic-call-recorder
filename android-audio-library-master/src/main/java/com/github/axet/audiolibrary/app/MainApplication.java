package com.github.axet.audiolibrary.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.github.axet.audiolibrary.R;

import java.io.File;

public class MainApplication extends com.github.axet.androidlibrary.app.MainApplication {
    public static final String PREFERENCE_STORAGE = "storage_path";
    public static final String PREFERENCE_RATE = "sample_rate";
    public static final String PREFERENCE_CALL = "call";
    public static final String PREFERENCE_SILENT = "silence";
    public static final String PREFERENCE_ENCODING = "encoding";
    public static final String PREFERENCE_LAST = "last_recording";
    public static final String PREFERENCE_THEME = "theme";
    public static final String PREFERENCE_CHANNELS = "channels";
    public static final String PREFERENCE_FORMAT = "format";
    public static final String PREFERENCE_SORT = "sort";
    public static final String PREFERENCE_VOICE = "voice";
    public static final String PREFERENCE_SKIP = "skip";
    public static final String PREFERENCE_VOLUME = "volume";
    public static final String PREFERENCE_FILTER = "filter";
    public static final String PREFERENCE_DETAILS_PREFIX = "details_";
    public static final String PREFERENCE_DETAILS_STAR = "_star";
    public static final String PREFERENCE_DETAILS_FS = "_fs";

    @Override
    public void onCreate() {
        super.onCreate();
        setTheme(getUserTheme()); // services can get proper current theme
    }

    public int getUserTheme() { // override for app theme
        return getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark);
    }

    public static int getTheme(Context context, int light, int dark) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = shared.getString(PREFERENCE_THEME, "");
        if (theme.equals(context.getString(R.string.Theme_Dark))) {
            return dark;
        } else {
            return light;
        }
    }

    public static String formatFree(Context context, long free, long left) {
        String str = "";

        long diff = left;

        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        if (diffDays > 0) {
            str = context.getResources().getQuantityString(R.plurals.days, diffDays, diffDays);
        } else if (diffHours > 0) {
            str = context.getResources().getQuantityString(R.plurals.hours, diffHours, diffHours);
        } else if (diffMinutes > 0) {
            str = context.getResources().getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes);
        } else if (diffSeconds > 0) {
            str = context.getResources().getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds);
        }

        String s = context.getString(R.string.title_free, formatSize(context, free));
        if (diffDays < 2) // 2 and more days does not show 'days'
            s += " ~ " + context.getString(R.string.title_left, str);
        return s;
    }

    public static String getHexString(int l) {
        return String.format("%04X", l);
    }

    public static String getHexString(long l) {
        return String.format("%08X", l);
    }

    public static String getFilePref(Uri f) {
        String s = f.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            return PREFERENCE_DETAILS_PREFIX + getHexString(f.toString().hashCode());
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File ff = Storage.getFile(f);
            return PREFERENCE_DETAILS_PREFIX + getHexString(ff.toString().hashCode());
        } else {
            throw new RuntimeException("unknown uri");
        }
    }

    public static boolean getStar(Context context, Uri f) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_STAR;
        return shared.getBoolean(p, false);
    }

    public static void setStar(Context context, Uri f, boolean b) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_STAR;
        SharedPreferences.Editor editor = shared.edit();
        editor.putBoolean(p, b);
        editor.commit();
    }
}
