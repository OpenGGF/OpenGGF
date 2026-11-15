package com.openggf.game.soniccd.audio;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Optional user media for the audio checks, resolved by {@link CdTestMedia}: the USA disc
 * ({@code -Dsoniccd.disc.path} or {@code -Dsoniccd.media.root}) and native captures under
 * {@code -Dsoniccd.native.root} (default {@code <media.root>/../native}). Both are user-local and never committed;
 * absent inputs skip.
 */
final class CdAudioTestMedia {
    private CdAudioTestMedia() { }

    static Path usaCue() throws IOException { return CdTestMedia.usaCue(); }

    static CdProgramAssets usaPrograms() throws IOException {
        return CdProgramAssets.open(usaCue(), Optional.of(CdReleaseVariant.USA), Optional.empty());
    }

    static Path nativeDir(String relative) { return CdTestMedia.nativeDir(relative); }
}
