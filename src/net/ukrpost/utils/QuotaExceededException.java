package net.ukrpost.utils;

import java.io.IOException;

public class QuotaExceededException extends IOException {

    public QuotaExceededException(String s) {
        super(s);
    }

}
