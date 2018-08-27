package com.github.axet.audiolibrary.filters;

import com.github.axet.audiolibrary.app.Sound;

public class AmplifierFilter extends Filter {

    public static final int MAX = 4;

    double db;

    public AmplifierFilter(float amp) {
        this.db = Sound.log1(amp, MAX + 1) * MAX;
    }

    public void filter(Buffer buf) {
        int end = buf.pos + buf.len;
        for (int i = buf.pos; i < end; i++) {
            double d = (buf.buf[i] * db);
            short s;
            if (d > Short.MAX_VALUE)
                s = Short.MAX_VALUE;
            else
                s = (short) d;
            buf.buf[i] = s;
        }
    }
}
