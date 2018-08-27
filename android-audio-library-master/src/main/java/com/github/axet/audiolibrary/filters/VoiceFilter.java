package com.github.axet.audiolibrary.filters;

import com.github.axet.audiolibrary.encoders.EncoderInfo;

import java.util.ArrayList;

import uk.me.berndporr.iirj.Butterworth;

public class VoiceFilter extends Filter {
    EncoderInfo info;
    ArrayList<Butterworth> bb = new ArrayList<>();

    public VoiceFilter(EncoderInfo info) {
        this.info = info;
        for (int i = 0; i < info.channels; i++) {
            Butterworth b = new Butterworth();
            b.bandPass(2, info.hz, 1650, 2700);
            bb.add(b);
        }
    }

    @Override
    public void filter(Buffer buf) {
        for (int i = 0; i < buf.len; i++) {
            int c = i % info.channels;
            Butterworth b = bb.get(c);
            int pos = buf.pos + i;
            double d = buf.buf[pos] / (double) Short.MAX_VALUE;
            d = b.filter(d);
            buf.buf[pos] = (short) (d * Short.MAX_VALUE);
        }
    }
}
