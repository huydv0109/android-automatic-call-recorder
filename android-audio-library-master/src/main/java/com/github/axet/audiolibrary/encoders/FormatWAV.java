package com.github.axet.audiolibrary.encoders;

// based on http://soundfile.sapp.org/doc/WaveFormat/

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class FormatWAV implements Encoder {
    int NumSamples;
    EncoderInfo info;
    int BytesPerSample;
    FileOutputStream outFile;
    FileChannel fc;

    public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
    public static final int INT_BYTES = Integer.SIZE / Byte.SIZE;
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;

    public FormatWAV(EncoderInfo info, FileDescriptor out) {
        this.info = info;
        NumSamples = 0;

        BytesPerSample = info.bps / 8;

        outFile = new FileOutputStream(out);
        fc = outFile.getChannel();

        save();
    }

    public void save() {
        int SubChunk1Size = 16;
        int SubChunk2Size = NumSamples * info.channels * BytesPerSample;
        int ChunkSize = 4 + (8 + SubChunk1Size) + (8 + SubChunk2Size);

        write("RIFF", ByteOrder.BIG_ENDIAN);
        write(ChunkSize, ORDER);
        write("WAVE", ByteOrder.BIG_ENDIAN);

        int ByteRate = info.hz * info.channels * BytesPerSample;
        short AudioFormat = 1; // PCM = 1 (i.e. Linear quantization)
        int BlockAlign = BytesPerSample * info.channels;

        write("fmt ", ByteOrder.BIG_ENDIAN);
        write(SubChunk1Size, ORDER);
        write((short) AudioFormat, ORDER); //short
        write((short) info.channels, ORDER); // short
        write(info.hz, ORDER);
        write(ByteRate, ORDER);
        write((short) BlockAlign, ORDER); // short
        write((short) info.bps, ORDER); // short

        write("data", ByteOrder.BIG_ENDIAN);
        write(SubChunk2Size, ORDER);
    }

    void write(String str, ByteOrder order) {
        byte[] cc = str.getBytes(Charset.defaultCharset());
        ByteBuffer bb = ByteBuffer.allocate(cc.length);
        bb.order(order);
        bb.put(cc);
        bb.flip();
        try {
            fc.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void write(int i, ByteOrder order) {
        ByteBuffer bb = ByteBuffer.allocate(INT_BYTES);
        bb.order(order);
        bb.putInt(i);
        bb.flip();
        try {
            fc.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void write(short i, ByteOrder order) {
        ByteBuffer bb = ByteBuffer.allocate(SHORT_BYTES);
        bb.order(order);
        bb.putShort(i);
        bb.flip();
        try {
            fc.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int pos, int buflen) {
        NumSamples += buflen / info.channels;
        ByteBuffer bb = ByteBuffer.allocate(buflen * SHORT_BYTES);
        bb.order(ORDER);
        bb.asShortBuffer().put(buf, pos, buflen);
        try {
            fc.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void end() {
        try {
            fc.position(0);
            save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            end();
            outFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EncoderInfo getInfo() {
        return info;
    }
}
