// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   BASE64MailboxEncoder.java

package net.ukrpost.storage.maildir;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;

public final class BASE64MailboxEncoder {

    public static String encode(String s) {
        BASE64MailboxEncoder base64mailboxencoder = null;
        final char[] ac = s.toCharArray();
        final int i = ac.length;
        boolean flag = false;
        final CharArrayWriter chararraywriter = new CharArrayWriter(i);
        for (int j = 0; j < i; j++) {
            final char c = ac[j];
            if (c >= ' ' && c <= '~') {
                if (base64mailboxencoder != null)
                    base64mailboxencoder.flush();
                if (c == '&') {
                    flag = true;
                    chararraywriter.write(38);
                    chararraywriter.write(45);
                } else {
                    chararraywriter.write(c);
                }
            } else {
                if (base64mailboxencoder == null) {
                    base64mailboxencoder = new BASE64MailboxEncoder(chararraywriter);
                    flag = true;
                }
                base64mailboxencoder.write(c);
            }
        }

        if (base64mailboxencoder != null)
            base64mailboxencoder.flush();
        if (flag)
            return chararraywriter.toString();
        else
            return s;
    }

    public BASE64MailboxEncoder(Writer writer) {
        buffer = new byte[4];
        started = false;
        out = writer;
    }

    public void write(int i) {
        try {
            if (!started) {
                started = true;
                out.write(38);
            }
            buffer[bufsize++] = (byte) (i >> 8);
            buffer[bufsize++] = (byte) (i & 0xff);
            if (bufsize >= 3) {
                encode();
                bufsize -= 3;
                return;
            }
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
    }

    public void flush() {
        try {
            if (bufsize > 0) {
                encode();
                bufsize = 0;
            }
            if (started) {
                out.write(45);
                started = false;
                return;
            }
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
    }

    protected void encode()
            throws IOException {
        if (bufsize == 1) {
            final byte byte0 = buffer[0];
            final int i = 0;
            final boolean flag = false;
            out.write(pem_array[byte0 >>> 2 & 0x3f]);
            out.write(pem_array[(byte0 << 4 & 0x30) + (i >>> 4 & 0xf)]);
            return;
        }
        if (bufsize == 2) {
            final byte byte1 = buffer[0];
            final byte byte3 = buffer[1];
            final int j = 0;
            out.write(pem_array[byte1 >>> 2 & 0x3f]);
            out.write(pem_array[(byte1 << 4 & 0x30) + (byte3 >>> 4 & 0xf)]);
            out.write(pem_array[(byte3 << 2 & 0x3c) + (j >>> 6 & 3)]);
            return;
        }
        final byte byte2 = buffer[0];
        final byte byte4 = buffer[1];
        final byte byte5 = buffer[2];
        out.write(pem_array[byte2 >>> 2 & 0x3f]);
        out.write(pem_array[(byte2 << 4 & 0x30) + (byte4 >>> 4 & 0xf)]);
        out.write(pem_array[(byte4 << 2 & 0x3c) + (byte5 >>> 6 & 3)]);
        out.write(pem_array[byte5 & 0x3f]);
        if (bufsize == 4)
            buffer[0] = buffer[3];
    }

    protected final byte[] buffer;
    protected int bufsize;
    protected boolean started;
    protected final Writer out;
    private static final char pem_array[] = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd',
        'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
        'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', '+', ','
    };

}
