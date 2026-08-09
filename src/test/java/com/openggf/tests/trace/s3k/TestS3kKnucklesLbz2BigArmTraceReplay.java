package com.openggf.tests.trace.s3k;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Comparison-only replay of the Knuckles complete-run LBZ2 segment that
 * contains Big Arm and the post-capsule route into MHZ.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kKnucklesLbz2BigArmTraceReplay extends AbstractTraceReplayTest {
    @Override
    protected SonicGame game() {
        return SonicGame.SONIC_3K;
    }

    @Override
    protected int zone() {
        return 6;
    }

    @Override
    protected int act() {
        return 1;
    }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/runs/"
                + "s3k-knuckles-complete-superemeralds/lbz_2");
    }

    @Override
    protected Path findBk2File(Path traceDirectory) throws IOException {
        // This run segment is nested below runs/<run-id>, while the v5 movie
        // remains deduplicated at the standard game-level _movies location.
        Path sharedMovie = Path.of("src/test/resources/traces/s3k/_movies/"
                + "s3k-knuckles-complete-superemeralds.bk2");
        return Files.exists(sharedMovie) ? sharedMovie : super.findBk2File(traceDirectory);
    }
}
