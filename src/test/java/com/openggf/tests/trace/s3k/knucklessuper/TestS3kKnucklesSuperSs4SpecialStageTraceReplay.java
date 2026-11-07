package com.openggf.tests.trace.s3k.knucklessuper;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.s3k.AbstractS3kSpecialStageTraceReplayTest;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;

/**
 * Strict independent replay of manifest segment 16, {@code ss_4},
 * from {@code s3k-knuckles-complete-superemeralds}: movie offset 75520, 5116 recorded rows.
 *
 * <p>Uses the shared entry bootstrap and its one-time carried run-state import.
 * Every row accepted by the sibling replay driver remains compared. A red names
 * the observed frontier; a green is conditional on segment-entry state and does
 * not certify the continuous chain or its round trips.
 */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("trace-scope-r7")
public class TestS3kKnucklesSuperSs4SpecialStageTraceReplay extends AbstractS3kSpecialStageTraceReplayTest {
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/ss_4");
    }
}
