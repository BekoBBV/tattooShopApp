package com.tattoostudio.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = { "0888123456", "0888 123 456", "088-812-3456", "+359888123456", "+359 888 123 456",
            "00359888123456", "359888123456", "888123456", "(0888) 123 456" })
    void bulgarianNumbersInAnyFormatBecomeTheSame(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEqualTo("+359888123456");
    }

    @ParameterizedTest
    @ValueSource(strings = { "+44 7700 900123", "0044 7700 900123" })
    void foreignNumbersKeepTheirCountryCode(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEqualTo("+447700900123");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "call me", "123", "+359abc" })
    void rejectsThingsThatAreNotPhoneNumbers(String input) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "+359888123456" })
    void masksTheMiddle(String input) {
        assertThat(PhoneNumbers.mask(input)).isEqualTo("+359******456");
    }
}
