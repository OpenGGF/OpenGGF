package com.openggf.tests.trace.runs;

import com.openggf.tools.audio.completerun.s2.S2ProductionRequestProjector;

import java.nio.file.Path;
import java.util.List;

/** Test-bytecode-only bridge to the package-private run-chain measurement harness. */
public final class S2RequestProjectionBk2TestBridge {
    private S2RequestProjectionBk2TestBridge() {
    }

    public record Capture(
            S2ProductionRequestProjector projector, List<Integer> requestRows) {
        public Capture {
            requestRows = List.copyOf(requestRows);
        }
    }

    public static Capture capture(Path rom, Path bk2)
            throws Exception {
        return new S2RequestProjectionBk2Capture().capture(rom, bk2);
    }
}
