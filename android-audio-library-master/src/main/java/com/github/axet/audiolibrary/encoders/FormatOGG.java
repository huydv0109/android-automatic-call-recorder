package com.github.axet.audiolibrary.encoders;

import android.content.Context;

import com.github.axet.androidlibrary.app.Natives;
import com.github.axet.audiolibrary.app.Sound;
import com.github.axet.vorbisjni.Config;
import com.github.axet.vorbisjni.Vorbis;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ShortBuffer;

public class FormatOGG implements Encoder {
    FileOutputStream writer;
    Vorbis vorbis;

    public static void natives(Context context) {
        if (Config.natives) {
            Natives.loadLibraries(context, "ogg", "vorbis", "vorbisjni");
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatOGG.natives(context);
            Vorbis v = new Vorbis();
            v.open(1, Sound.DEFAULT_RATE, 1f);
            v.close();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public FormatOGG(Context context, EncoderInfo info, FileDescriptor out) {
        natives(context);
        vorbis = new Vorbis();
        vorbis.open(info.channels, info.hz, 0.4f);
        writer = new FileOutputStream(out);
    }

    @Override
    public void encode(short[] buf, int pos, int len) {
        byte[] bb = vorbis.encode(buf, pos, len);
        try {
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = vorbis.encode(null, 0, 0);
            writer.write(bb);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        vorbis.close();
    }
}
