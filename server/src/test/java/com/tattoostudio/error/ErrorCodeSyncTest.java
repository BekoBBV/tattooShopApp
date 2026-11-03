package com.tattoostudio.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * The frontend lists the same error codes in client/src/lib/types.ts. This fails when a code is
 * added or removed on one side only, so the two lists can't drift apart unnoticed.
 */
class ErrorCodeSyncTest {

    private static final Path TYPES = Path.of("..", "client", "src", "lib", "types.ts");

    @Test
    void frontendKnowsEveryErrorCode() throws IOException {
        String types = Files.readString(TYPES);
        Matcher union = Pattern.compile("export type ErrorCode =([^=]*?)(?:\\nexport|\\z)", Pattern.DOTALL).matcher(types);
        assertThat(union.find()).as("ErrorCode union in " + TYPES).isTrue();

        Set<String> frontend = Pattern.compile("\"([A-Z_]+)\"").matcher(union.group(1)).results()
                .map(m -> m.group(1))
                .collect(Collectors.toSet());
        Set<String> backend = Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(frontend).isEqualTo(backend);
    }
}
