package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;

import com.github.axet.audiolibrary.app.Storage;

import java.io.File;
import java.io.FileDescriptor;

@TargetApi(21)
public class Format3GP extends MuxerMP4 {

    public static final String CONTENTTYPE_3GPP = "audio/3gpp";
    public static final String CONTENTTYPE_AMRWB = "audio/amr-wb";

    public Format3GP(Context context, String ct, EncoderInfo info, FileDescriptor out) {
        MediaFormat format = new MediaFormat();

        // for high bitrate AMR_WB
        if (ct.equals(CONTENTTYPE_AMRWB)) {
            final int kBitRates[] = {6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850};
            format.setString(MediaFormat.KEY_MIME, CONTENTTYPE_AMRWB);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 23850); // set maximum
        }

        // for low bitrate, AMR_NB
        if (ct.equals(CONTENTTYPE_3GPP)) {
            final int kBitRates[] = {4750, 5150, 5900, 6700, 7400, 7950, 10200, 12200};
            format.setString(MediaFormat.KEY_MIME, CONTENTTYPE_3GPP);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz); // 8000 only supported
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 12200); // set maximum
        }

        create(context, info, format, out);
    }
}
