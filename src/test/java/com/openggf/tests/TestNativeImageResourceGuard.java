package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraalVM native-image embeds only the classpath resources named by
 * {@code resource-config.json}; anything else resolves to a null stream at
 * runtime with no build-time warning. Two resource trees had drifted out of
 * that file after they were added -- {@code load-time-profiles/} (whose absence
 * throws out of {@code Sonic3kGameModule.loadS3kProfile}, crashing every native
 * S3K launch under the default FAST load-time simulation) and {@code icon/}.
 * This guard fails whenever a runtime resource is unreachable from the config,
 * so the drift surfaces on the JVM suite instead of in a shipped native bundle.
 */
class TestNativeImageResourceGuard {

    private static final Path RESOURCE_ROOT =
            Path.of("src/main/resources").toAbsolutePath();
    private static final Path RESOURCE_CONFIG = RESOURCE_ROOT.resolve(
            Path.of("META-INF", "native-image", "com.openggf", "OpenGGF", "resource-config.json"));
    /** Native-image reads this tree itself; it is never a runtime lookup. */
    private static final String NATIVE_IMAGE_METADATA_PREFIX = "META-INF/native-image/";

    @Test
    void everyRuntimeResourceIsReachableFromTheNativeImageConfig() throws IOException {
        List<Pattern> includes = readIncludePatterns();
        assertFalse(includes.isEmpty(),
                "resource-config.json declares no include patterns: " + RESOURCE_CONFIG);

        List<String> unreachable = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RESOURCE_ROOT)) {
            files.filter(Files::isRegularFile)
                    .map(path -> RESOURCE_ROOT.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> !name.startsWith(NATIVE_IMAGE_METADATA_PREFIX))
                    .sorted()
                    .forEach(name -> {
                        if (includes.stream().noneMatch(pattern -> pattern.matcher(name).matches())) {
                            unreachable.add(name);
                        }
                    });
        }

        assertTrue(unreachable.isEmpty(),
                "these classpath resources are absent from the native image and resolve to a"
                        + " null stream at runtime; add matching include patterns to "
                        + RESOURCE_CONFIG + ": " + unreachable);
    }

    private static List<Pattern> readIncludePatterns() throws IOException {
        String json = Files.readString(RESOURCE_CONFIG, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"pattern\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        List<Pattern> patterns = new ArrayList<>();
        while (matcher.find()) {
            patterns.add(Pattern.compile(unescapeJson(matcher.group(1))));
        }
        return patterns;
    }

    private static String unescapeJson(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(++i);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    // A JSON-escaped backslash pair reaching here is a regex
                    // escape such as \Q or \.; hand both characters to Pattern.
                    default -> out.append('\\').append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
