package com.ajinkya.linky.util;

public final class Base62Encoder {

    private Base62Encoder() {}

    private static final String CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = CHARSET.length();

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** Unguessable code: {@code length} chars drawn uniformly from the Base62 alphabet. */
    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(RANDOM.nextInt(BASE)));
        }
        return sb.toString();
    }
}
