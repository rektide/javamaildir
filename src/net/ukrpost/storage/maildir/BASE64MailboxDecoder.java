// Decompiled by Jad v1.5.8e. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.geocities.com/kpdus/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   BASE64MailboxDecoder.java

package net.ukrpost.storage.maildir;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public final class BASE64MailboxDecoder {

    public static String decode(String s) {
        if (s == null || s.length() == 0)
            return s;
        boolean flag = false;
        int i = 0;
        final char[] ac = new char[s.length()];
        final StringCharacterIterator stringcharacteriterator = new StringCharacterIterator(s);
        for (char c = stringcharacteriterator.first(); c != '\uFFFF'; c = stringcharacteriterator.next())
            if (c == '&') {
                flag = true;
                i = base64decode(ac, i, stringcharacteriterator);
            } else {
                ac[i++] = c;
            }

        if (flag)
            return new String(ac, 0, i);
        else
            return s;
    }

    protected static int base64decode(char ac[], int i, CharacterIterator characteriterator) {
        boolean flag = true;
        int j = -1;
        final boolean flag1 = false;
        do {
            final byte byte0 = (byte) characteriterator.next();
            if (byte0 == -1)
                break;
            if (byte0 == 45) {
                if (flag)
                    ac[i++] = '&';
                break;
            }
            flag = false;
            final byte byte1 = (byte) characteriterator.next();
            if (byte1 == -1 || byte1 == 45)
                break;
            byte byte2 = pem_convert_array[byte0 & 0xff];
            byte byte3 = pem_convert_array[byte1 & 0xff];
            byte byte4 = (byte) (byte2 << 2 & 0xfc | byte3 >>> 4 & 3);
            if (j != -1) {
                ac[i++] = (char) (j << 8 | byte4 & 0xff);
                j = -1;
            } else {
                j = byte4 & 0xff;
            }
            final byte byte5 = (byte) characteriterator.next();
            if (byte5 == 61)
                continue;
            if (byte5 == -1 || byte5 == 45)
                break;
            byte2 = byte3;
            byte3 = pem_convert_array[byte5 & 0xff];
            byte4 = (byte) (byte2 << 4 & 0xf0 | byte3 >>> 2 & 0xf);
            if (j != -1) {
                ac[i++] = (char) (j << 8 | byte4 & 0xff);
                j = -1;
            } else {
                j = byte4 & 0xff;
            }
            final byte byte6 = (byte) characteriterator.next();
            if (byte6 == 61)
                continue;
            if (byte6 == -1 || byte6 == 45)
                break;
            byte2 = byte3;
            byte3 = pem_convert_array[byte6 & 0xff];
            byte4 = (byte) (byte2 << 6 & 0xc0 | byte3 & 0x3f);
            if (j != -1) {
                final char c = (char) (j << 8 | byte4 & 0xff);
                ac[i++] = (char) (j << 8 | byte4 & 0xff);
                j = -1;
            } else {
                j = byte4 & 0xff;
            }
        } while (true);
        return i;
    }

    public BASE64MailboxDecoder() {
    }

    protected static final char pem_array[] = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd',
        'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
        'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', '+', ','
    };
    protected static final byte pem_convert_array[];

    static {
        pem_convert_array = new byte[256];
        for (int i = 0; i < 255; i++)
            pem_convert_array[i] = -1;

        for (int j = 0; j < pem_array.length; j++)
            pem_convert_array[pem_array[j]] = (byte) j;

    }
}
