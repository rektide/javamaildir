package net.ukrpost.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class QuotaAwareOutputStream
        extends FilterOutputStream {
    private int limit = 0;
    private int usage = 0;

    public QuotaAwareOutputStream(OutputStream os) {
        super(os);
    }

    public QuotaAwareOutputStream(OutputStream os, int quotalimit) {
        super(os);
        limit = quotalimit;
    }

    public void write(int b)
            throws IOException {
        if (limit != 0 && usage > limit)
            throw new QuotaExceededException("quota limit (" + limit + " bytes) reached");

        usage++;

        super.write(b);
    }

}
