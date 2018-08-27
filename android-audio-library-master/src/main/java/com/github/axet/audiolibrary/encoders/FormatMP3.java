package com.github.axet.audiolibrary.encoders;

import android.content.Context;

import com.github.axet.androidlibrary.app.Natives;
import com.github.axet.androidlibrary.sound.Sound;
import com.github.axet.lamejni.Lame;
import com.github.axet.vorbisjni.Config;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FormatMP3 implements Encoder {
    FileOutputStream writer;
    FileChannel fc;
    Lame lame;

    public static void natives(Context context) {
        if (Config.natives) {
            Natives.loadLibraries(context, "lame", "lamejni");
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatMP3.natives(context);
            Lame v = new Lame();
            v.open(1, Sound.DEFAULT_RATE, Factory.getBitrate(Sound.DEFAULT_RATE) / 1000, 5);
            v.close();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public FormatMP3(Context context, EncoderInfo info, FileDescriptor out) {
        natives(context);
        lame = new Lame();
        int b = Factory.getBitrate(info.hz) / 1000;
        lame.open(info.channels, info.hz, b, 4);
        writer = new FileOutputStream(out);
        fc = writer.getChannel();
    }

    @Override
    public void encode(short[] buf, int pos, int len) {
        byte[] bb = lame.encode(buf, pos, len);
        try {
            fc.write(ByteBuffer.wrap(bb));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = lame.encode(null, 0, 0);
            fc.write(ByteBuffer.wrap(bb));
            bb = lame.close();
            fc.position(0);
            fc.write(ByteBuffer.wrap(bb));
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
