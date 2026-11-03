package com.tattoostudio.util;

import java.util.regex.Pattern;

/**
 * Brings phone numbers to one format (E.164, e.g. +359888123456) so that
 * "0888 123 456", "+359 888 123 456" and "00359888123456" all match each other.
 * Numbers without a country code are treated as Bulgarian.
 */
public final class PhoneNumbers {

    private static final String DEFAULT_COUNTRY_CODE = "359";
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

    private PhoneNumbers() {
    }

    /**
     * @return the normalised number
     * @throws IllegalArgumentException if it does not look like a phone number
     */
    public static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String s = input.trim().replaceAll("[\\s\\-().\\/]", "");
        if (s.startsWith("00")) {
            s = "+" + s.substring(2);
        } else if (s.startsWith("0")) {
            s = "+" + DEFAULT_COUNTRY_CODE + s.substring(1);
        } else if (s.startsWith(DEFAULT_COUNTRY_CODE)) {
            s = "+" + s;
        } else if (!s.startsWith("+")) {
            s = "+" + DEFAULT_COUNTRY_CODE + s;
        }
        if (!E164.matcher(s).matches()) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        return s;
    }

    /** Hides the middle of a number for display, e.g. +359******456. */
    public static String mask(String normalized) {
        if (normalized == null || normalized.length() < 8) {
            return null;
        }
        int keepStart = normalized.startsWith("+" + DEFAULT_COUNTRY_CODE) ? 4 : 3;
        int keepEnd = 3;
        return normalized.substring(0, keepStart)
                + "*".repeat(normalized.length() - keepStart - keepEnd)
                + normalized.substring(normalized.length() - keepEnd);
    }
}
