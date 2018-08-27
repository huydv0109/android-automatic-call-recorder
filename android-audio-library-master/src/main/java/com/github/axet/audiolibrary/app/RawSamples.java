package com.github.axet.audiolibrary.app;

import android.media.AudioFormat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;

public class RawSamples {
    public static final ByteOrder ORDER = ByteOrder.BIG_ENDIAN;
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;

    File in;

    InputStream is;
    byte[] readBuffer;

    OutputStream os;

    // get samples from bytes
    public static long getSamples(long len) {
        return len / (Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1);
    }

    // get bytes from samples
    public static long getBufferLen(long samples) {
        return samples * (Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1);
    }

    public static double getAmplitude(short[] buffer, int offset, int len) {
        double sum = 0;
        for (int i = offset; i < offset + len; i++) {
            sum += buffer[i] * buffer[i];
        }
        return Math.sqrt(sum / len);
    }

    public static double getDB(short[] buffer, int offset, int len) {
        return getDB(getAmplitude(buffer, offset, len));
    }

    // https://en.wikipedia.org/wiki/Sound_pressure
    public static double getDB(double amplitude) {
        return 20.0 * Math.log10(amplitude / Short.MAX_VALUE);
    }

    public RawSamples(File in) {
        this.in = in;
    }

    // open for writing with specified offset to truncate file
    public void open(long writeOffset) {
        trunk(writeOffset);
        try {
            os = new BufferedOutputStream(new FileOutputStream(in, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // open for reading
    //
    // bufReadSize - samples count
    public void open(int bufReadSize) {
        try {
            readBuffer = new byte[(int) getBufferLen(bufReadSize)];
            is = new FileInputStream(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // open for read with initial offset and buffer read size
    //
    // offset - samples offset
    // bufReadSize - samples size
    public void open(long offset, int bufReadSize) {
        try {
            readBuffer = new byte[(int) getBufferLen(bufReadSize)];
            is = new FileInputStream(in);
            is.skip(offset * (Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int read(short[] buf) {
        try {
            int len = is.read(readBuffer);
            if (len <= 0)
                return len;
            ByteBuffer.wrap(readBuffer, 0, len).order(ORDER).asShortBuffer().get(buf, 0, (int) getSamples(len));
            return (int) getSamples(len);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(short val) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(SHORT_BYTES);
            bb.order(ORDER);
            bb.putShort(val);
            os.write(bb.array(), 0, bb.limit());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(short[] buf, int pos, int len) {
        try {
            ByteBuffer bb = ByteBuffer.allocate(SHORT_BYTES * len);
            bb.order(ORDER);
            ShortBuffer ss = bb.asShortBuffer();
            ss.put(buf, pos, len);
            os.write(bb.array(), 0, bb.limit());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(ByteBuffer bb, int pos, int len) {
        try {
            os.write(bb.array(), pos, len);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long getSamples() {
        return getSamples(in.length());
    }

    public void trunk(long pos) {
        try {
            FileOutputStream fos = new FileOutputStream(in, true);
            FileChannel outChan = fos.getChannel();
            outChan.truncate(getBufferLen(pos));
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            if (is != null) {
                is.close();
                is = null;
            }

            if (os != null) {
                os.close();
                os = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
