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
            assertThat(generator.next()).matches("FS-[A-Z2-9]{6}").doesNotContainPattern("[01OIL]");
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
        assertThat(BookingCodeGenerator.normalize(" fs-7k3qxm ")).isEqualTo("FS-7K3QXM");
        assertThat(BookingCodeGenerator.normalize("7K3QXM")).isEqualTo("FS-7K3QXM");
        assertThat(BookingCodeGenerator.normalize("fs7k3qxm")).isEqualTo("FS-7K3QXM"); // without the dash
        assertThat(BookingCodeGenerator.normalize("FSAB12")).isEqualTo("FS-FSAB12"); // a code that starts with FS
    }
}
