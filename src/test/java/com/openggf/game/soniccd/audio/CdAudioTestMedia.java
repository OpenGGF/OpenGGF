package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional user media for the audio checks: {@code -Dsoniccd.media.root=<dir with USA/*.cue>}
 * and native captures under {@code -Dsoniccd.native.root} (default {@code <media.root>/../native}).
 * Both are user-local and never committed; absent inputs skip.
 */
final class CdAudioTestMedia {
    private CdAudioTestMedia() { }

    static Path usaCue() throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for Sonic CD audio checks");
        try (var files = Files.list(Path.of(root, "USA"))) {
            return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue"))
                    .findFirst().orElseThrow(() -> new IOException("No USA CUE under " + root));
        }
    }

    static CdProgramAssets usaPrograms() throws IOException {
        return CdProgramAssets.open(usaCue(), Optional.of(CdReleaseVariant.USA), Optional.empty());
    }

    static Path nativeDir(String relative) {
        String configured = System.getProperty("soniccd.native.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("soniccd.media.root", "."), "..", "native");
        Path dir = root.resolve(relative).normalize();
        Assumptions.assumeTrue(Files.isDirectory(dir), "Native capture absent: " + dir);
        return dir;
    }
}
