package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;

import com.github.axet.androidlibrary.app.Natives;
import com.github.axet.opusjni.Config;
import com.github.axet.opusjni.Opus;

import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

// https://wiki.xiph.org/MatroskaOpus
@TargetApi(21)
public class FormatOPUS_MKA extends FormatOPUS {
    public static final String TAG = FormatOPUS_MKA.class.getSimpleName();

    public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;

    MatroskaFileWriter writer;
    MatroskaFileTrack track;
    MatroskaFileTrack.MatroskaAudioTrack audio;

    public FormatOPUS_MKA(Context context, EncoderInfo info, FileDescriptor out) {
        super(context, info, out);
    }

    public void create(final EncoderInfo info, FileDescriptor out) {
        super.create(info, out);
        try {
            audio = new MatroskaFileTrack.MatroskaAudioTrack();
            audio.setSamplingFrequency(info.hz);
            audio.setBitDepth(info.bps);
            audio.setChannels((short) info.channels);
            audio.setOutputSamplingFrequency(info.hz);

            track = new MatroskaFileTrack();
            track.setCodecID("A_OPUS");
            track.setAudio(audio);
            track.setTrackType(MatroskaFileTrack.TrackType.AUDIO);
            track.setTrackNo(1);
            track.setTrackUID(1);
            track.setCodecPrivate(opusHead());

            writer = new MatroskaFileWriter(new FormatMKA_AAC.FileDataWriter(out));
            writer.addTrack(track);
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // https://tools.ietf.org/html/rfc7845#page-12
    ByteBuffer opusHead() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream os = new DataOutputStream(bos);

            // head
            os.write(new byte[]{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}); // Magic Signature, This is an 8-octet (64-bit) field
            os.writeByte(1); // Version (8 bits, unsigned)
            os.writeByte(info.channels); // Output Channel Count 'C' (8 bits, unsigned):
            os.writeShort(0); // Pre-skip (16 bits, unsigned, little endian)
            os.write(ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).order(ORDER).putInt(info.hz).array()); // Input Sample Rate (32 bits, unsigned, little endian)
            os.writeShort(0); // Output Gain (16 bits, signed, little endian)
            os.writeByte(0); // Channel Mapping Family (8 bits, unsigned)

            os.close();
            return ByteBuffer.wrap(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void encode(ByteBuffer bb, long dur) {
        MatroskaFileFrame frame = new MatroskaFileFrame();
        frame.setKeyFrame(true);
        frame.setTimecode(getCurrentTimeStamp());
        frame.setTrackNo(track.getTrackNo());
        frame.setData(bb);
        frame.setDuration(dur);
        writer.addFrame(frame);
        writer.flush();
    }

    public void close() {
        super.close();
        writer.setDuration(getCurrentTimeStamp());
        writer.close();
    }
}
