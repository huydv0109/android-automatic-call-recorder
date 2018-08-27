package com.github.axet.audiolibrary.encoders;

import net.sourceforge.javaflacencoder.EncodingConfiguration;
import net.sourceforge.javaflacencoder.FLACEncoder;
import net.sourceforge.javaflacencoder.FLACFileOutputStream;
import net.sourceforge.javaflacencoder.StreamConfiguration;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

// compile 'com.github.axet:java-flac-encoder:0.3.8'
public class FormatFLAC implements Encoder {
    EncoderInfo info;
    FLACEncoder flacEncoder;
    FLACFileOutputStream flacOutputStream;

    public FormatFLAC(EncoderInfo info, FileDescriptor out) {
        this.info = info;

        StreamConfiguration sc = new StreamConfiguration();
        sc.setSampleRate(info.hz);
        sc.setBitsPerSample(info.bps);
        sc.setChannelCount(info.channels);

        EncodingConfiguration ec = new EncodingConfiguration();
        ec.setSubframeType(EncodingConfiguration.SubframeType.LPC);

        try {
            flacEncoder = new FLACEncoder();
            flacOutputStream = new FLACFileOutputStream(new FileOutputStream(out));
            flacEncoder.setStreamConfiguration(sc);
            flacEncoder.setEncodingConfiguration(ec);
            flacEncoder.setOutputStream(flacOutputStream);
            flacEncoder.openFLACStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int pos, int buflen) {
        try {
            int[] ii = new int[buflen];
            int end = pos + buflen;
            for (int i = pos; i < end; i++)
                ii[i] = buf[i];
            int count = buflen / info.channels;
            flacEncoder.addSamples(ii, count);
            flacEncoder.encodeSamples(count, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            flacEncoder.encodeSamples(flacEncoder.samplesAvailableToEncode(), true);
            flacOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
