package com.github.axet.audiolibrary.encoders;

import android.media.AudioFormat;
import android.util.Log;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import vavi.sound.pcm.resampling.ssrc.SSRC;

public class Resample {
    public static final String TAG = Resample.class.getSimpleName();

    public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;

    Thread thread = null;
    PipedOutputStream is;
    PipedInputStream os;
    RuntimeException delayed;

    public Resample(final int sampleRate, final int channels, final int hz) {
        try {
            this.is = new PipedOutputStream();
            this.os = new PipedInputStream(100 * 1024);
            final PipedInputStream is = new PipedInputStream(this.is);
            final PipedOutputStream os = new PipedOutputStream(this.os);
            final int c = com.github.axet.audiolibrary.app.Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SSRC ssrc = new SSRC(is, os, sampleRate, hz, c, c, channels, Integer.MAX_VALUE, 0, 0, true);
                    } catch (RuntimeException e) {
                        delayed = e;
                    } catch (IOException e) {
                        delayed = new RuntimeException(e);
                    }
                }
            }, "SSRC");
            thread.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void end() {
        if (delayed != null)
            throw delayed;
        try {
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(short[] buf, int pos, int len) {
        if (delayed != null)
            throw delayed;
        try {
            ByteBuffer bb = ByteBuffer.allocate(len * SHORT_BYTES);
            bb.order(ORDER);
            bb.asShortBuffer().put(buf, pos, len);
            is.write(bb.array());
            is.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ByteBuffer read() {
        if (delayed != null)
            throw delayed;
        try {
            int blen = os.available();
            if (blen <= 0)
                return null;
            byte[] b = new byte[blen];
            int read = os.read(b);
            ByteBuffer bb = ByteBuffer.allocate(read);
            bb.order(ORDER);
            bb.put(b, 0, read);
            return bb;
        } catch (IOException e) {
            throw new RuntimeException(e);
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
    }
}
