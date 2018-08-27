package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;

import org.ebml.io.DataWriter;
import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

@TargetApi(16) // os mp4/aac codec
public class FormatMKA_AAC implements Encoder {
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;

    EncoderInfo info;
    MediaCodec encoder;
    long NumSamples;
    ByteBuffer input;
    int inputIndex;
    MatroskaFileWriter writer;
    MatroskaFileTrack track;
    MatroskaFileTrack.MatroskaAudioTrack audio;

    MatroskaFileFrame old;

    public static class FileDataWriter implements DataWriter {
        FileOutputStream file = null;
        FileChannel fc = null;

        public FileDataWriter(FileDescriptor fd) throws IOException {
            file = new FileOutputStream(fd);
            fc = file.getChannel();
        }

        @Override
        public int write(final byte b) {
            try {
                ByteBuffer bb = ByteBuffer.allocate(1);
                bb.put(b);
                return fc.write(bb);
            } catch (final IOException ex) {
                return 0;
            }
        }

        @Override
        public int write(final ByteBuffer buff) {
            try {
                return fc.write(buff);
            } catch (final IOException ex) {
                return 0;
            }
        }

        @Override
        public long length() {
            try {
                return fc.size();
            } catch (final IOException ex) {
                return -1;
            }
        }

        @Override
        public long getFilePointer() {
            try {
                return fc.position();
            } catch (final IOException ex) {
                return -1;
            }
        }

        @Override
        public boolean isSeekable() {
            return true;
        }

        @Override
        public long seek(final long pos) {
            try {
                fc.position(pos);
                return fc.position();
            } catch (final IOException ex) {
                return -1;
            }
        }

        public void close() throws IOException {
            file.close();
        }
    }

    public FormatMKA_AAC(EncoderInfo info, FileDescriptor out) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, Factory.CONTENTTYPE_MP4A);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, Factory.getBitrate(info.hz));
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        format.setInteger(MediaFormat.KEY_AAC_SBR_MODE, 0);
        create(info, format, out);
    }

    public void create(EncoderInfo info, MediaFormat format, FileDescriptor out) {
        this.info = info;
        try {
            encoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            writer = new MatroskaFileWriter(new FileDataWriter(out));
            audio = new MatroskaFileTrack.MatroskaAudioTrack();
            audio.setSamplingFrequency(info.hz);
            audio.setOutputSamplingFrequency(info.hz);
            audio.setBitDepth(info.bps);
            audio.setChannels((short) info.channels);
            track = new MatroskaFileTrack();
            track.setCodecID("A_AAC");
            track.setAudio(audio);
            track.setTrackType(MatroskaFileTrack.TrackType.AUDIO);
            writer.addTrack(track);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int pos, int len) {
        int end = pos + len;
        for (int offset = pos; offset < end; offset++) {
            if (input == null) {
                inputIndex = encoder.dequeueInputBuffer(-1);
                if (inputIndex < 0)
                    throw new RuntimeException("unable to open encoder input buffer");
                if (Build.VERSION.SDK_INT >= 21)
                    input = encoder.getInputBuffer(inputIndex);
                else
                    input = encoder.getInputBuffers()[inputIndex];
                input.clear();
            }
            input.putShort(buf[offset]);
            if (!input.hasRemaining()) {
                queue();
            }
        }
    }

    void queue() {
        if (input == null)
            return;
        encoder.queueInputBuffer(inputIndex, 0, input.position(), getCurrentTimeStamp(), 0);
        NumSamples += input.position() / info.channels / SHORT_BYTES;
        input = null;
        while (encode())
            ;// do encode()
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    boolean encode() {
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        int outputIndex = encoder.dequeueOutputBuffer(outputInfo, 0);
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
            return false;

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // never get called on API 16
            return true;
        }

        if (outputIndex >= 0) {
            ByteBuffer output;
            if (Build.VERSION.SDK_INT >= 21)
                output = encoder.getOutputBuffer(outputIndex);
            else
                output = encoder.getOutputBuffers()[outputIndex];
            output.position(outputInfo.offset);
            output.limit(outputInfo.offset + outputInfo.size);
            old(outputInfo.presentationTimeUs / 1000);
            if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                track.setCodecPrivate(clone(output));
                writer.flush();
                encoder.releaseOutputBuffer(outputIndex, false);
            } else {
                MatroskaFileFrame frame = new MatroskaFileFrame();
                frame.setKeyFrame((outputInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) == MediaCodec.BUFFER_FLAG_KEY_FRAME);
                frame.setTimecode(outputInfo.presentationTimeUs / 1000);
                frame.setTrackNo(track.getTrackNo());
                frame.setData(clone(output));
                encoder.releaseOutputBuffer(outputIndex, false);
                old = frame;
            }
        }

        return true;
    }

    void old(long cur) {
        if (old != null) {
            old.setDuration(cur - old.getTimecode());
            writer.addFrame(old);
            writer.flush();
            old = null;
        }
    }

    public void close() {
        end();
        encoder.release();
        writer.close();
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 * 1000 / info.hz;
    }

    public void end() {
        if (input != null) {
            queue();
        }
        int inputIndex = encoder.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer input;
            if (Build.VERSION.SDK_INT >= 21)
                input = encoder.getInputBuffer(inputIndex);
            else
                input = encoder.getInputBuffers()[inputIndex];
            input.clear();
            encoder.queueInputBuffer(inputIndex, 0, 0, getCurrentTimeStamp(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        while (encode())
            ;// do encode()
        old(getCurrentTimeStamp() / 1000);
        writer.setDuration(getCurrentTimeStamp() / 1000);
        encoder.stop();
    }

    public EncoderInfo getInfo() {
        return info;
    }
}
