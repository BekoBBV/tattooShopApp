package com.tattoostudio.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Creates booking codes like K7Q2-M9. Look-alike characters (0/O, 1/I/L) are left out so customers
 * cannot mistype them. 31^6 ≈ 887 million combinations, which together with the phone check and
 * the attempt limit makes guessing someone else's booking impractical.
 */
@Component
public class BookingCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;
    /** The dash goes after this many characters. */
    static final int DASH_AT = 4;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.insert(DASH_AT, '-').toString();
    }

    /** Accepts what a customer types ("k7q2-m9", "K7Q2M9", " k7q2 m9 ") and returns the stored form. */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String bare = input.trim().toUpperCase().replace(" ", "").replace("-", "");
        return bare.length() == LENGTH ? bare.substring(0, DASH_AT) + "-" + bare.substring(DASH_AT) : bare;
    }
}
