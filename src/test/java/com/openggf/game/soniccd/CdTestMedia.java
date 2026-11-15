package com.openggf.game.soniccd;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Optional user media: {@code -Dsoniccd.media.root=<dir with USA/*.cue>}; native fixtures beside it. */
final class CdTestMedia {
    private CdTestMedia() { }

    static Path usaCue() throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for Sonic CD disc checks");
        try (Stream<Path> cues = Files.list(Path.of(root, "USA"))) {
            return cues.filter(p -> p.toString().endsWith(".cue")).findFirst()
                    .orElseThrow(() -> new IOException("No USA CUE under " + root));
        }
    }

    /** The native fixture file if it exists, else null (no skip). */
    static Path optionalNativeFile(String relative) {
        String configured = System.getProperty("soniccd.native.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("soniccd.media.root", "."), "..", "native");
        Path file = root.resolve(relative).normalize();
        return Files.isRegularFile(file) ? file : null;
    }

    /** {@code -Dsoniccd.native.root}, defaulting to {@code <media.root>/../native}. */
    static Path nativeFile(String relative) {
        String configured = System.getProperty("soniccd.native.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("soniccd.media.root", "."), "..", "native");
        Path file = root.resolve(relative).normalize();
        Assumptions.assumeTrue(Files.isRegularFile(file), "Native fixture absent: " + file);
        return file;
    }
}
