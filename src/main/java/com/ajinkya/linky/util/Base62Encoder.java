package com.ajinkya.linky.util;

public class Base62Encoder {

    private static final String CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = CHARSET.length();

    public static String encode(long id) {
        if (id == 0) {
            return String.valueOf(CHARSET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(CHARSET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String shortCode) {
        long id = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            id = id * BASE + CHARSET.indexOf(shortCode.charAt(i));
        }
        return id;
    }
}
