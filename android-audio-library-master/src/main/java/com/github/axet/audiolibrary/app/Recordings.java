package com.github.axet.audiolibrary.app;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.PopupMenu;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.services.StorageProvider;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.androidlibrary.widgets.PopupShareActionProvider;
import com.github.axet.androidlibrary.widgets.ProximityPlayer;
import com.github.axet.androidlibrary.widgets.ProximityShader;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiolibrary.R;
import com.github.axet.audiolibrary.animations.RecordingAnimation;
import com.github.axet.audiolibrary.data.AppDatabase;
import com.github.axet.audiolibrary.data.Record;
import com.github.axet.audiolibrary.data.RecordDao;
import com.github.axet.audiolibrary.encoders.Factory;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class Recordings extends ArrayAdapter<Storage.RecordingUri> implements AbsListView.OnScrollListener, SharedPreferences.OnSharedPreferenceChangeListener {
    public static String TAG = Recordings.class.getSimpleName();

    protected static final int TYPE_COLLAPSED = 0;
    protected static final int TYPE_EXPANDED = 1;
    protected static final int TYPE_DELETED = 2;

    protected Handler handler;
    protected Storage storage;
    protected MediaPlayer player;
    protected ProximityShader proximity;
    protected Runnable updatePlayer;
    protected int selected = -1;
    protected ListView list;
    protected int scrollState;
    protected Thread thread;
    protected LayoutInflater inflater;
    protected String filter;

    private RecordDao recordDao;

    public static int getDuration(Context context, Uri u) {
        MediaPlayer mp = createPlayer(context, u);
        int duration = mp.getDuration();
        mp.release();
        return duration;
    }

    public static MediaPlayer createPlayer(Context context, Uri u) {
        try {
            MediaPlayer mp = new MediaPlayer(); // MediaPlayer.create(context, u) failed with ':' in uri
            if (Build.VERSION.SDK_INT >= 21) {
                final AudioAttributes aa = new AudioAttributes.Builder().build();
                mp.setAudioAttributes(aa);
            }
            String s = u.getScheme();
            ParcelFileDescriptor pfd;
            if (s.equals(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver resolver = context.getContentResolver();
                pfd = resolver.openFileDescriptor(u, "r");
            } else if (s.equals(ContentResolver.SCHEME_FILE)) {
                pfd = ParcelFileDescriptor.open(Storage.getFile(u), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                throw new RuntimeException("unknown uri");
            }
            FileDescriptor fd = pfd.getFileDescriptor();
            mp.setDataSource(fd);
            pfd.close();
            mp.prepare();
            return mp;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            String a = intent.getAction();
            if (a.equals(Intent.ACTION_MEDIA_EJECT)) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        load(false, null);
                    }
                });
            }
            if (a.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        load(false, null);
                    }
                });
            }
            if (a.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        load(false, null);
                    }
                });
            }
        }
    };

    protected ViewGroup toolbar;
    protected View toolbar_a;
    protected View toolbar_s;
    protected View toolbar_n;
    protected View toolbar_d;
    protected boolean toolbarFilterAll = true; // all or stars
    protected boolean toolbarSortName = true; // name or date

    protected Map<Uri, Storage.RecordingStats> cache = new ConcurrentHashMap<>();

    public static Storage.RecordingStats getFileStats(Map<String, ?> prefs, Uri f) {
        String json = (String) prefs.get(MainApplication.getFilePref(f) + MainApplication.PREFERENCE_DETAILS_FS);
        if (json != null && !json.isEmpty()) {
            return new Storage.RecordingStats(json);
        }
        return null;
    }

    public static void setFileStats(Context context, Uri f, Storage.RecordingStats fs) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String p = MainApplication.getFilePref(f) + MainApplication.PREFERENCE_DETAILS_FS;
        SharedPreferences.Editor editor = shared.edit();
        editor.putString(p, fs.save().toString());
        editor.commit();
    }

    public class SortName implements Comparator<Storage.RecordingUri> {
        @Override
        public int compare(Storage.RecordingUri file, Storage.RecordingUri file2) {
            return file.name.compareTo(file2.name);
        }
    }

    public class SortDate implements Comparator<Storage.RecordingUri> {
        @Override
        public int compare(Storage.RecordingUri file, Storage.RecordingUri file2) {
            long l1 = file.last;
            long l2 = file2.last;
            return Long.valueOf(l1).compareTo(l2);
        }
    }

    public Recordings(Context context, ListView list) {
        super(context, 0);
        this.list = list;
        this.handler = new Handler();
        this.storage = new Storage(context);
        this.list.setOnScrollListener(this);
        load();
        IntentFilter ff = new IntentFilter();
        ff.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        ff.addAction(Intent.ACTION_MEDIA_MOUNTED);
        ff.addAction(Intent.ACTION_MEDIA_EJECT);
        context.registerReceiver(receiver, ff);
        inflater = LayoutInflater.from(getContext());
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    // true - include
    protected boolean filter(Storage.RecordingUri f) {
        if (filter != null) {
            if (!f.name.toLowerCase().contains(filter))
                return false;
        }
        if (toolbarFilterAll)
            return true;
        if (MainApplication.getStar(getContext(), f.uri))
            return true;
        else
            return false;
    }

    public void scan(final List<Uri> ff, final boolean clean, final Runnable done) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        final Map<String, ?> prefs = shared.getAll();

        final Thread old = thread;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (old != null) {
                    old.interrupt();
                    try {
                        old.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                final Thread t = Thread.currentThread();
                final ArrayList<Storage.RecordingUri> all = new ArrayList<>();
                for (Uri f : ff) {
                    if (t.isInterrupted())
                        return;
                    Storage.RecordingStats fs = cache.get(f);
                    if (fs == null) {
                        fs = getFileStats(prefs, f);
                        if (fs != null)
                            cache.put(f, fs);
                    }
                    if (fs != null) {
                        long last = storage.getLastModified(f);
                        long size = storage.getLength(f);
                        if (last != fs.last || size != fs.size)
                            fs = null;
                    }
                    if (fs == null) {
                        fs = new Storage.RecordingStats();
                        fs.size = storage.getLength(f);
                        fs.last = storage.getLastModified(f);
                        try {
                            fs.duration = getDuration(getContext(), f);
                            cache.put(f, fs);
                            setFileStats(getContext(), f, fs);
                            all.add(new Storage.RecordingUri(f, fs));
                        } catch (Exception e) {
                            Log.d(TAG, f.toString(), e);
                        }
                    } else {
                        all.add(new Storage.RecordingUri(f, fs));
                    }
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (thread != t)
                            return; // replaced with new thread, exit
                        setNotifyOnChange(false);
                        clear(); // clear recordings
                        TreeSet<String> delete = new TreeSet<>();
                        for (String k : prefs.keySet()) {
                            if (k.startsWith(MainApplication.PREFERENCE_DETAILS_PREFIX))
                                delete.add(k);
                        }
                        TreeSet<Uri> delete2 = new TreeSet<>(cache.keySet());
                        for (Storage.RecordingUri f : all) {
                            if (filter(f))
                                add(f); // add recording
                            cleanDelete(delete, f.uri);
                            delete2.remove(f.uri);
                        }
                        if (clean) {
                            SharedPreferences.Editor editor = shared.edit();
                            for (String s : delete) {
                                editor.remove(s);
                            }
                            for (Uri f : delete2) {
                                cache.remove(f);
                            }
                            editor.commit();
                        }
                        sort();
                        notifyDataSetChanged();
                        if (done != null)
                            done.run();
                    }
                });
            }
        }, "Recordings Scan");
        thread.start();
    }

    public void cleanDelete(TreeSet<String> delete, Uri f) { // file exists, prevent it from cleaning
        String p = MainApplication.getFilePref(f);
        delete.remove(p + MainApplication.PREFERENCE_DETAILS_FS);
        delete.remove(p + MainApplication.PREFERENCE_DETAILS_STAR);
    }

    public void sort() {
        Comparator<Storage.RecordingUri> sort;
        if (toolbarSortName)
            sort = new SortName();
        else
            sort = new SortDate();
        sort(Collections.reverseOrder(sort));
    }

    public void close() {
        playerStop();
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (receiver != null) {
            getContext().unregisterReceiver(receiver);
            receiver = null;
        }
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    public String[] getEncodingValues() {
        return Factory.getEncodingValues(getContext());
    }

    public void load(boolean clean, Runnable done) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");

        Uri user;
        if (path.startsWith(ContentResolver.SCHEME_CONTENT))
            user = Uri.parse(path);
        else if (path.startsWith(ContentResolver.SCHEME_FILE))
            user = Uri.parse(path);
        else
            user = Uri.fromFile(new File(path));

        Uri mount = storage.getStoragePath(path);

        if (!user.equals(mount))
            clean = false; // do not clean if we failed to mount user selected folder

        scan(storage.scan(mount, getEncodingValues()), clean, done);
    }

    public View inflate(int id, ViewGroup parent) {
        return inflater.inflate(id, parent, false);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflate(R.layout.recording, parent);
            convertView.setTag(-1);
        }

        final View view = convertView;
        final View base = convertView.findViewById(R.id.recording_base);

        if ((int) convertView.getTag() == TYPE_DELETED) {
            RemoveItemAnimation.restore(base);
            convertView.setTag(-1);
        }

        final Storage.RecordingUri f = getItem(position);
        recordDao = AppDatabase.getAppDatabase(getContext()).recordDao();
        Record record = recordDao.getRecordByFilePath(f.uri.getPath());

        final boolean starb = MainApplication.getStar(getContext(), f.uri);
        final ImageView star = (ImageView) convertView.findViewById(R.id.recording_star);
        starUpdate(star, starb);
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = !MainApplication.getStar(getContext(), f.uri);
                MainApplication.setStar(getContext(), f.uri, b);
                starUpdate(star, b);
            }
        });

        TextView title = (TextView) convertView.findViewById(R.id.recording_title);
        if (record != null) {
            if (!TextUtils.isEmpty(record.getContactName())) {
                title.setText(record.getContactName());
            } else {
                title.setText(record.getPhone());
            }
        }


        TextView time = (TextView) convertView.findViewById(R.id.recording_time);
        time.setText(MainApplication.SIMPLE.format(new Date(f.last)));

        TextView dur = (TextView) convertView.findViewById(R.id.recording_duration);
        dur.setText(MainApplication.formatDuration(getContext(), f.duration));

        TextView size = (TextView) convertView.findViewById(R.id.recording_size);
        size.setText(MainApplication.formatSize(getContext(), f.size));

        final View playerBase = convertView.findViewById(R.id.recording_player);
        playerBase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        final Runnable delete = new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(R.string.delete_recording);
                builder.setMessage("...\\" + f.name + "\n\n" + getContext().getString(R.string.are_you_sure));
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playerStop();
                        dialog.cancel();
                        RemoveItemAnimation.apply(list, base, new Runnable() {
                            @Override
                            public void run() {
                                playerStop(); // in case if playback got started twice during delete animation
                                storage.delete(f.uri);
                                view.setTag(TYPE_DELETED);
                                select(-1);
                                remove(f); // instant remove
                                load(true, null); // thread load
                            }
                        });
                    }
                });
                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                showDialog(builder);
            }
        };

        final Runnable rename = new Runnable() {
            @Override
            public void run() {
                final OpenFileDialog.EditTextDialog e = new OpenFileDialog.EditTextDialog(getContext());
                e.setTitle(getContext().getString(R.string.rename_recording));
                e.setText(Storage.getNameNoExt(f.name));
                e.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playerStop();
                        String ext = Storage.getExt(f.name);
                        String s = String.format("%s.%s", e.getText(), ext);
                        storage.rename(f.uri, s);
                        load(true, null);
                    }
                });
                showDialog(e);

            }
        };

        if (selected == position) {
            RecordingAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_COLLAPSED);
            convertView.setTag(TYPE_EXPANDED);

            updatePlayerText(convertView, f);

            final View play = convertView.findViewById(R.id.recording_player_play);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (player == null) {
                        playerPlay(playerBase, f);
                    } else if (player.isPlaying()) {
                        playerPause(playerBase, f);
                    } else {
                        playerPlay(playerBase, f);
                    }
                }
            });

            final View edit = convertView.findViewById(R.id.recording_player_edit);
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rename.run();
                }
            });

            final View share = convertView.findViewById(R.id.recording_player_share);
            share.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String name = "Recordings";
                    try {
                        PackageManager pm = getContext().getPackageManager();
                        ApplicationInfo info = pm.getApplicationInfo(getContext().getPackageName(), 0);
                        name = info.loadLabel(pm).toString();
                    } catch (PackageManager.NameNotFoundException e) {
                        ;
                    }

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(Storage.getTypeByName(f.name));
                    intent.putExtra(Intent.EXTRA_EMAIL, "");
                    intent.putExtra(Intent.EXTRA_STREAM, StorageProvider.share(getContext(), f.uri));
                    intent.putExtra(Intent.EXTRA_SUBJECT, f.name);
                    intent.putExtra(Intent.EXTRA_TEXT, getContext().getString(R.string.shared_via, name));
                    PopupShareActionProvider.show(getContext(), share, intent);
                }
            });

            KeyguardManager myKM = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
            final boolean locked = myKM.inKeyguardRestrictedInputMode();

            ImageView trash = (ImageView) convertView.findViewById(R.id.recording_player_trash);
            if (locked) {
                trash.setOnClickListener(null);
                trash.setClickable(true);
                trash.setColorFilter(Color.GRAY);
            } else {
                trash.setColorFilter(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent));
                trash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete.run();
                    }
                });
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(-1);
                }
            });
        } else {
            RecordingAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_EXPANDED);
            convertView.setTag(TYPE_COLLAPSED);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(position);
                }
            });
        }

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PopupMenu popup = new PopupMenu(getContext(), v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.menu_context, popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.action_delete) {
                            delete.run();
                            return true;
                        }
                        if (item.getItemId() == R.id.action_rename) {
                            rename.run();
                            return true;
                        }
                        return false;
                    }
                });
                popup.show();
                return true;
            }
        });

        return convertView;
    }

    protected void starUpdate(ImageView star, boolean starb) {
        if (starb) {
            star.setImageResource(R.drawable.ic_star_black_24dp);
            star.setContentDescription(getContext().getString(R.string.starred));
        } else {
            star.setImageResource(R.drawable.ic_star_border_black_24dp);
            star.setContentDescription(getContext().getString(R.string.not_starred));
        }
    }

    protected void playerPlay(View v, final Storage.RecordingUri f) {
        if (player == null) {
            player = createPlayer(getContext(), f.uri);
        }
        if (player == null) {
            Toast.makeText(getContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        player.start();

        if (proximity == null) {
            proximity = new ProximityPlayer(getContext()) {
                @Override
                public void prepare() {
                    try {
                        int pos = player.getCurrentPosition();
                        player.release();
                        player = new MediaPlayer();
                        if (Build.VERSION.SDK_INT >= 21) {
                            AudioAttributes.Builder b = new AudioAttributes.Builder();
                            switch (streamType) {
                                case AudioManager.STREAM_MUSIC:
                                    b.setUsage(AudioAttributes.USAGE_MEDIA);
                                    b.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
                                    break;
                                case AudioManager.STREAM_VOICE_CALL:
                                    b.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION);
                                    b.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
                                    break;
                            }
                            b.setLegacyStreamType(streamType);
                            final AudioAttributes aa = b.build();
                            player.setAudioAttributes(aa);
                        } else {
                            player.setAudioStreamType(streamType);
                        }
                        player.setDataSource(getContext(), f.uri);
                        player.prepare();
                        player.seekTo(pos);
                        player.start();
                    } catch (IOException e) {
                        Log.d(TAG, "unable to reset payer", e);
                        playerStop();
                    }
                }
            };
            proximity.create();
        }

        updatePlayerRun(v, f);
    }

    protected void playerPause(View v, Storage.RecordingUri f) {
        if (player != null) {
            player.pause();
        }
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }
        if (proximity != null) {
            proximity.close();
            proximity = null;
        }
        updatePlayerText(v, f);
    }

    protected void playerStop() {
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }
        if (proximity != null) {
            proximity.close();
            proximity = null;
        }
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    protected void updatePlayerRun(final View v, final Storage.RecordingUri f) {
        boolean playing = updatePlayerText(v, f);

        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer);
            updatePlayer = null;
        }

        if (!playing) {
            playerStop(); // clear player instance
            updatePlayerText(v, f); // update length
            return;
        }

        updatePlayer = new Runnable() {
            @Override
            public void run() {
                updatePlayerRun(v, f);
            }
        };
        handler.postDelayed(updatePlayer, 200);
    }

    protected boolean updatePlayerText(final View v, final Storage.RecordingUri f) {
        ImageView i = (ImageView) v.findViewById(R.id.recording_player_play);

        final boolean playing = player != null && player.isPlaying();

        i.setImageResource(playing ? R.drawable.ic_pause_black_24dp : R.drawable.ic_play_arrow_black_24dp);
        i.setContentDescription(getContext().getString(playing ? R.string.pause_button : R.string.play_button));

        TextView start = (TextView) v.findViewById(R.id.recording_player_start);
        SeekBar bar = (SeekBar) v.findViewById(R.id.recording_player_seek);
        TextView end = (TextView) v.findViewById(R.id.recording_player_end);

        int c = 0;
        Integer d = f.duration;

        if (player != null) {
            c = player.getCurrentPosition();
            d = player.getDuration();
        }

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser)
                    return;

                if (player == null)
                    playerPlay(v, f);

                if (player != null) {
                    player.seekTo(progress);
                    if (!player.isPlaying())
                        playerPlay(v, f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        start.setText(MainApplication.formatDuration(getContext(), c));
        bar.setMax(d);
        bar.setKeyProgressIncrement(1);
        bar.setProgress(c);
        end.setText("-" + MainApplication.formatDuration(getContext(), d - c));

        return playing;
    }

    public void select(int pos) {
        selected = pos;
        notifyDataSetChanged();
        playerStop();
    }

    public int getSelected() {
        return selected;
    }

    protected AppCompatImageButton getCheckBox(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = getCheckBox(g.getChildAt(i));
                if (c != null) {
                    return (AppCompatImageButton) c;
                }
            }
        }
        if (v instanceof AppCompatImageButton) {
            return (AppCompatImageButton) v;
        }
        return null;
    }

    protected void selectToolbar(View v, boolean pressed) {
        AppCompatImageButton cc = getCheckBox(v);
        if (pressed) {
            int[] states = new int[]{
                    android.R.attr.state_checked,
            };
            cc.setImageState(states, false);
        } else {
            int[] states = new int[]{
                    -android.R.attr.state_checked,
            };
            cc.setImageState(states, false);
        }
    }

    protected void selectToolbar() {
        selectToolbar(toolbar_a, toolbarFilterAll);
        selectToolbar(toolbar_s, !toolbarFilterAll);
        selectToolbar(toolbar_n, toolbarSortName);
        selectToolbar(toolbar_d, !toolbarSortName);
    }

    public void setToolbar(ViewGroup v) {
        this.toolbar = v;
        toolbar_a = v.findViewById(R.id.toolbar_all);
        toolbar_a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarFilterAll = true;
                selectToolbar();
                load(false, null);
                save();
            }
        });
        toolbar_s = v.findViewById(R.id.toolbar_stars);
        toolbar_s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarFilterAll = false;
                selectToolbar();
                load(false, null);
                save();
            }
        });
        toolbar_n = v.findViewById(R.id.toolbar_name);
        toolbar_n.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarSortName = true;
                sort();
                selectToolbar();
                save();
            }
        });
        toolbar_d = v.findViewById(R.id.toolbar_date);
        toolbar_d.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarSortName = false;
                sort();
                selectToolbar();
                save();
            }
        });
        selectToolbar();
    }

    protected void save() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor edit = shared.edit();
        edit.putBoolean(MainApplication.PREFERENCE_SORT, toolbarSortName);
        edit.putBoolean(MainApplication.PREFERENCE_FILTER, toolbarFilterAll);
        edit.commit();
    }

    protected void load() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        toolbarSortName = shared.getBoolean(MainApplication.PREFERENCE_SORT, true);
        toolbarFilterAll = shared.getBoolean(MainApplication.PREFERENCE_FILTER, true);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_STORAGE)) {
            load(true, null);
        }
    }

    public void search(String q) {
        filter = q.toLowerCase(Locale.US);
        load(false, null);
    }

    public void searchClose() {
        filter = null;
        load(false, null);
    }

    public void showDialog(AlertDialog.Builder e) {
        e.show();
    }
}
