package com.github.axet.audiolibrary.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiolibrary.R;
import com.github.axet.audiolibrary.encoders.Factory;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static String TAG = Storage.class.getSimpleName();

    public static final String RECORDINGS = "recordings";
    public static final String RAW = "raw";
    public static final String TMP_REC = "recording.data";
    public static final String TMP_ENC = "encoding.data";

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US);
    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);

    public static class RecordingStats {
        public int duration;
        public long size;
        public long last;

        public RecordingStats() {
        }

        public RecordingStats(RecordingStats fs) {
            this.duration = fs.duration;
            this.size = fs.size;
            this.last = fs.last;
        }

        public RecordingStats(String json) {
            try {
                JSONObject j = new JSONObject(json);
                duration = j.getInt("duration");
                size = j.getLong("size");
                last = j.getLong("last");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public JSONObject save() {
            try {
                JSONObject o = new JSONObject();
                o.put("duration", duration);
                o.put("size", size);
                o.put("last", last);
                return o;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class RecordingUri extends RecordingStats {
        public Uri uri;
        public String name;

        public RecordingUri(Uri f, RecordingStats fs) {
            super(fs);
            uri = f;
            name = Storage.getDocumentName(uri);
        }
    }

    public static void migrateLocalStorageDialog(final Context context, final Handler handler, final Storage storage) {
        int dp10 = ThemeUtils.dp2px(context, 10);
        ProgressBar progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        progress.setPadding(dp10, dp10, dp10, dp10);
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(R.string.migrating_data);
        b.setView(progress);
        b.setCancelable(false);
        final AlertDialog dialog = b.create();
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    storage.migrateLocalStorage();
                } catch (final RuntimeException e) {
                    Log.d(TAG, "migrate error", e);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.cancel();
                    }
                });
            }
        });
        dialog.show();
        thread.start();
    }

    public Storage(Context context) {
        super(context);
    }

    public boolean isLocalStorageEmpty() {
        File[] ff = getLocalStorage().listFiles();
        if (ff == null)
            return true;
        return ff.length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(context, PERMISSIONS_RW);
    }

    public boolean recordingPending() {
        File tmp = getTempRecording();
        return tmp.exists() && tmp.length() > 0;
    }

    public File getLocalInternal() {
        return new File(context.getFilesDir(), RECORDINGS);
    }

    public File getLocalExternal() {
        return context.getExternalFilesDir(RECORDINGS);
    }

    public Uri getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        return getStoragePath(path);
    }

    public void migrateLocalStorage() {
        migrateLocalStorage(new File(context.getApplicationInfo().dataDir, RECORDINGS)); // old recordings folder
        migrateLocalStorage(new File(context.getApplicationInfo().dataDir)); // old recordings folder
        migrateLocalStorage(context.getFilesDir()); // old recordings folder
        migrateLocalStorage(context.getExternalFilesDir("")); // old recordings folder
        migrateLocalStorage(getLocalInternal());
        migrateLocalStorage(getLocalExternal());
    }

    public boolean isLocalStorage(File f) {
        if (super.isLocalStorage(f))
            return true;
        File a = context.getFilesDir();
        if (f.getPath().startsWith(a.getPath()))
            return true;
        a = context.getExternalFilesDir("");
        if (a != null && f.getPath().startsWith(a.getPath()))
            return true;
        if (Build.VERSION.SDK_INT >= 19) {
            File[] aa = context.getExternalFilesDirs("");
            if (aa != null) {
                for (File b : aa) {
                    if (f.getPath().startsWith(b.getPath()))
                        return true;
                }
            }
        }
        return false;
    }

    public void migrateLocalStorage(File l) {
        if (l == null)
            return;

        if (!canWrite(l))
            return;

        Uri path = getStoragePath();

        String s = path.getScheme();
        if (s.equals(ContentResolver.SCHEME_FILE)) {
            File p = getFile(path);
            if (!canWrite(p))
                return;
            if (l.equals(p)) // same storage path
                return;
        }

        Uri u = Uri.fromFile(l);
        if (u.equals(path)) // same storage path
            return;

        File[] ff = l.listFiles();

        if (ff == null)
            return;

        for (File f : ff) {
            if (f.isFile()) { // skip directories (we didn't create one)
                migrate(f, path);
            }
        }
    }

    @Override
    public Uri migrate(File f, Uri t) {
        Uri u = super.migrate(f, t);
        if (u == null)
            return null;
        boolean star = MainApplication.getStar(context, Uri.fromFile(f));
        MainApplication.setStar(context, u, star); // copy star to migrated file
        return u;
    }

    @Override
    public Uri rename(Uri f, String t) {
        Uri u = super.rename(f, t);
        if (u == null)
            return null;
        boolean star = MainApplication.getStar(context, f);
        MainApplication.setStar(context, u, star); // copy star to renamed name
        return u;
    }

    public Uri getNewFile() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        Uri parent = getStoragePath();

        String s = parent.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File p = getFile(parent);
            if (!p.exists() && !p.mkdirs())
                throw new RuntimeException("Unable to create: " + parent);
        }

        return getNextFile(parent, SIMPLE.format(new Date()), ext);
    }

    public List<Uri> scan(Uri uri, String[] ee) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            ArrayList<Uri> list = new ArrayList<>();

            ContentResolver contentResolver = context.getContentResolver();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            Cursor childCursor = contentResolver.query(childrenUri, null, null, null, null);
            if (childCursor != null) {
                try {
                    while (childCursor.moveToNext()) {
                        String id = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        String t = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        long size = childCursor.getLong(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                        if (size > 0) {
                            String n = t.toLowerCase();
                            for (String e : ee) {
                                if (n.endsWith("." + e)) {
                                    Uri d = DocumentsContract.buildDocumentUriUsingTree(uri, id);
                                    list.add(d);
                                }
                            }
                        }
                    }
                } finally {
                    childCursor.close();
                }
            }

            return list;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File dir = getFile(uri);
            ArrayList<Uri> list = new ArrayList<>();

            File[] ff = dir.listFiles();
            if (ff == null)
                return list;

            for (File f : ff) {
                if (f.length() > 0) {
                    String n = f.getName().toLowerCase();
                    for (String e : ee) {
                        if (n.endsWith("." + e))
                            list.add(Uri.fromFile(f));
                    }
                }
            }

            return list;
        } else {
            throw new RuntimeException("unknow uri");
        }
    }

    // get average recording miliseconds based on compression format
    public static long average(Context context, long free) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int rate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        int m = Sound.getChannels(context);
        long perSec = Factory.getEncoderRate(ext, rate) * m;
        return free / perSec * 1000;
    }

    public static File getLocalDataDir(Context context) {
        return new File(context.getApplicationInfo().dataDir);
    }

    public static File getFilesDir(Context context, String type) {
        File raw = new File(context.getFilesDir(), type);
        if (!raw.exists() && !raw.mkdirs() && !raw.exists())
            throw new RuntimeException("no files permissions");
        return raw;
    }

    public File getTempRecording() {
        File internalOld = new File(getLocalDataDir(context), "recorind.data");
        if (internalOld.exists())
            return internalOld;
        internalOld = new File(getLocalDataDir(context), TMP_REC);
        if (internalOld.exists())
            return internalOld;
        internalOld = new File(context.getCacheDir(), TMP_REC); // cache/ dir auto cleared by OS if space is low
        if (internalOld.exists())
            return internalOld;
        internalOld = context.getExternalCacheDir();
        if (internalOld != null) {
            internalOld = new File(internalOld.getParentFile(), TMP_REC);
            if (internalOld.exists())
                return internalOld;
        }

        File internal = new File(getFilesDir(context, RAW), TMP_REC);
        if (internal.exists())
            return internal;

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS_RW))
                return internal;
        }

        File c = context.getExternalFilesDir(RAW);
        if (c == null) // some old phones <15API with disabled sdcard return null
            return internal;

        File external = new File(c, TMP_REC);

        if (external.exists()) // external already been used as tmp storage, keep using it
            return external;

        try {
            long freeI = getFree(internal);
            long freeE = getFree(external);
            if (freeI > freeE)
                return internal;
            else
                return external;
        } catch (RuntimeException e) { // samsung devices unable to determine external folders
            return internal;
        }
    }

    public File getTempEncoding() {
        File internalOld = new File(context.getCacheDir(), TMP_ENC);
        if (internalOld.exists())
            return internalOld;
        internalOld = context.getExternalCacheDir();
        if (internalOld != null) {
            internalOld = new File(internalOld, TMP_ENC);
            if (internalOld.exists())
                return internalOld;
        }

        File internal = new File(getFilesDir(context, RAW), TMP_ENC);
        if (internal.exists())
            return internal;

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS_RW))
                return internal;
        }

        File c = context.getExternalFilesDir(RAW);
        if (c == null) // some old phones <15API with disabled sdcard return null
            return internal;

        File external = new File(c, TMP_ENC);

        if (external.exists())
            return external;

        try {
            long freeI = getFree(internal);
            long freeE = getFree(external);
            if (freeI > freeE)
                return internal;
            else
                return external;
        } catch (RuntimeException e) { // samsung devices unable to determine external folders
            return internal;
        }
    }
}
