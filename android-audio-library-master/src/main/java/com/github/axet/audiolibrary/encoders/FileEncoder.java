package com.github.axet.audiolibrary.encoders;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.github.axet.audiolibrary.app.RawSamples;
import com.github.axet.audiolibrary.filters.Filter;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileEncoder {
    public static final String TAG = FileEncoder.class.getSimpleName();

    Context context;
    Handler handler;

    public File in;
    public Encoder encoder;
    RawSamples rs;
    Thread thread;
    long samples;
    long cur;
    Throwable t;
    final AtomicBoolean pause = new AtomicBoolean(false);
    public ArrayList<Filter> filters = new ArrayList<>();

    public FileEncoder(Context context, File in, Encoder encoder) {
        this.context = context;
        this.in = in;
        this.encoder = encoder;
        handler = new Handler();
    }

    public void run(final Runnable progress, final Runnable done, final Runnable error) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                cur = 0;

                rs = new RawSamples(in);

                try {
                    samples = rs.getSamples();

                    short[] buf = new short[1000];

                    rs.open(buf.length); // FileNotFoundException if sdcard removed?

                    while (true) {
                        try {
                            synchronized (pause) {
                                if (pause.get()) {
                                    pause.wait();
                                }
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                        int len = rs.read(buf);
                        if (len <= 0) {
                            break;
                        } else {
                            Filter.Buffer b = new Filter.Buffer(buf, 0, len);
                            for (Filter f : filters) {
                                f.filter(b);
                            }
                            if (b.len > 0)
                                encoder.encode(b.buf, b.pos, b.len);
                            handler.post(progress);
                            synchronized (thread) {
                                cur += b.len;
                            }
                        }
                    }
                    closeFiles();
                    handler.post(done);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Exception", e);
                    t = e;
                    handler.post(error);
                }
            }
        }, "FileEncoder");
        thread.start();
    }

    public int getProgress() {
        synchronized (thread) {
            return (int) (cur * 100 / samples);
        }
    }

    public Throwable getException() {
        return t;
    }

    public void pause() {
        synchronized (pause) {
            pause.set(true);
        }
    }

    public void resume() {
        synchronized (pause) {
            pause.set(false);
            pause.notifyAll();
        }
    }

    public void closeFiles() {
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }
        if (rs != null) {
            rs.close();
            rs = null;
        }
    }

    public void close() {
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        closeFiles();
        handler.removeCallbacksAndMessages(null); // prevent call progress/done after encoder closed()
    }
}
