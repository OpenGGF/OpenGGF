package com.openggf.game.soniccd.release;

import com.openggf.data.cd.CueDiscImage;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -Dsoniccd.media.root=<directory containing USA, Japan, Europe>. */
class TestSonicCdRetailMedia {
    @ParameterizedTest(name = "{0} captured retail content")
    @ValueSource(strings = {"USA", "Europe", "Japan"})
    void capturedWholeDiscMatchesTheIndependentlyDerivedProfile(String region) throws IOException {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for real-media checks");
        Path directory = Path.of(configured).resolve(region);
        Path cue;
        try (var entries = Files.list(directory)) {
            var cues = entries.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        var expected = switch (region) {
            case "USA" -> CdRetailReleases.USA;
            case "Europe" -> CdRetailReleases.EUROPE;
            default -> CdRetailReleases.JAPAN;
        };
        try (var disc = CueDiscImage.open(cue)) {
            var actual = CdObservedMediaIdentity.capture(disc, cue, Optional.empty());
            assertEquals(35, actual.tracks().size());
            assertEquals(expected, CdRetailReleases.find(actual).orElseThrow());
            assertEquals(expected, CdRetailReleases.requireRequested(actual, expected.key()));
        }
    }
}
