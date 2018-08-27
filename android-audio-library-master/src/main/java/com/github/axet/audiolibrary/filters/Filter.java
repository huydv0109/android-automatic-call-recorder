package com.github.axet.audiolibrary.filters;

public class Filter {

    public static class Buffer {
        public short[] buf;
        public int pos;
        public int len;

        public Buffer(short[] buf, int pos, int len) {
            this.buf = buf;
            this.pos = pos;
            this.len = len;
        }
    }

    public void filter(Buffer buffer) {
    }

}
