package com.openggf.game.soniccd.release;

import com.openggf.data.cd.CueDiscImage;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -Dsoniccd.media.root=<directory containing USA, Japan, Europe>. */
class TestSonicCdRetailMedia {
    @ParameterizedTest(name = "{0} captured retail content")
    @ValueSource(strings = {"USA", "Europe", "Japan"})
    void capturedWholeDiscMatchesTheIndependentlyDerivedProfile(String region) throws IOException {
        // Reads the CUE sheet itself (CueDiscImage), so every region comes from the media root's folders.
        Path cue = com.openggf.game.soniccd.CdTestMedia.regionCue(region);
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
