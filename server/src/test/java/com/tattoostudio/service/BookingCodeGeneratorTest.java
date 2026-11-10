package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BookingCodeGeneratorTest {

    private final BookingCodeGenerator generator = new BookingCodeGenerator();

    @Test
    void codesHaveTheRightShapeAndNoLookAlikeCharacters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.next()).matches("[A-Z2-9]{4}-[A-Z2-9]{2}").doesNotContainPattern("[01OIL]");
        }
    }

    @Test
    void codesAreRandom() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSizeGreaterThan(995);
    }

    @Test
    void normalizesWhatCustomersType() {
        assertThat(BookingCodeGenerator.normalize(" k7q2-m9 ")).isEqualTo("K7Q2-M9");
        assertThat(BookingCodeGenerator.normalize("K7Q2M9")).isEqualTo("K7Q2-M9"); // without the dash
        assertThat(BookingCodeGenerator.normalize("k7 q2 m9")).isEqualTo("K7Q2-M9");
        assertThat(BookingCodeGenerator.normalize("K7Q2-M")).isEqualTo("K7Q2M"); // too short: never matches a code
    }
}
