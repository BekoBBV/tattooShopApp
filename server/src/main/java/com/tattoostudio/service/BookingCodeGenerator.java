package com.tattoostudio.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Creates booking codes like FS-7K3QXM. Look-alike characters (0/O, 1/I/L) are left out
 * so customers cannot mistype them. 31^6 ≈ 887 million combinations, which together with
 * the phone check and the attempt limit makes guessing someone else's booking impractical.
 */
@Component
public class BookingCodeGenerator {

    static final String PREFIX = "FS-";
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Accepts what a customer types ("fs-7k3qxm", "FS7K3QXM", "7K3QXM ") and returns the stored form. */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String code = input.trim().toUpperCase().replace(" ", "");
        if (code.startsWith(PREFIX)) {
            return code;
        }
        String bare = PREFIX.replace("-", "");
        if (code.startsWith(bare) && code.length() == bare.length() + LENGTH) {
            return PREFIX + code.substring(bare.length()); // typed without the dash
        }
        return PREFIX + code;
    }
}
