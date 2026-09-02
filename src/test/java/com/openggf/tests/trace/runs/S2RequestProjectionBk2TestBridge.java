package com.openggf.tests.trace.runs;

import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.tools.audio.parity.s2.S2OracleRawStream;
import com.openggf.tools.audio.completerun.s2.S2ProductionRequestProjector;

import java.nio.file.Path;
import java.util.List;

/** Test-bytecode-only bridge to the package-private run-chain measurement harness. */
public final class S2RequestProjectionBk2TestBridge {
    private S2RequestProjectionBk2TestBridge() {
    }

    public record Capture(
            S2ProductionRequestProjector projector, List<Integer> requestRows,
            List<ProductionAudioRow> audioRows) {
        public Capture {
            requestRows = List.copyOf(requestRows);
            audioRows = List.copyOf(audioRows);
        }
    }

    /** Immutable observation emitted after one committed production row. */
    public record ProductionAudioRow(
            int row, SmpsDriverSnapshot snapshot,
            List<S2OracleRawStream.ChipWrite> writes,
            boolean completedDriverService) {
        public ProductionAudioRow {
            writes = List.copyOf(writes);
        }
    }

    public static Capture capture(Path rom, Path bk2)
            throws Exception {
        return new S2RequestProjectionBk2Capture().capture(rom, bk2);
    }
}
