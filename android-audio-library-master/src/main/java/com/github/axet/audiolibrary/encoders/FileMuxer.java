package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaMuxer;

import com.github.axet.audiolibrary.app.Storage;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;

public class FileMuxer {
    public static String TAG = FileMuxer.class.getSimpleName();

    public FileDescriptor fd;
    public File out;

    @TargetApi(18)
    public MediaMuxer create(Context context, FileDescriptor fd, int format) throws IOException {
        try { // API26+
            Constructor<?> k = MediaMuxer.class.getConstructor(FileDescriptor.class, int.class);
            return (MediaMuxer) k.newInstance(fd, format);
        } catch (Exception e) {
            createOut(context, fd);
            return new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }
    }

    public void createOut(Context context, FileDescriptor fd) {
        this.fd = fd;

        Storage storage = new Storage(context);
        out = storage.getTempRecording();

        File parent = out.getParentFile();

        if (!parent.exists() && !parent.mkdirs()) { // in case if it were manually deleted
            throw new RuntimeException("Unable to create: " + parent);
        }
    }

    public void close() {
        if (out != null && out.exists() && out.length() > 0) {
            try {
                FileInputStream fis = new FileInputStream(out);
                FileOutputStream fos = new FileOutputStream(fd);
                IOUtils.copy(fis, fos);
                fos.close();
                fis.close();
                Storage.delete(out);
            } catch (IOException e) {
                Storage.delete(out); // delete tmp encoding file
                throw new RuntimeException(e);
            }
            out = null;
        }
    }

}
