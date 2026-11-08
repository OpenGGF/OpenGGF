package com.openggf.trace;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestTerminalRunAdmission {
    private TraceRunManifest run(List<Integer> census) {
        return new TraceRunManifest("s1", "run", "movie.bk2", "hash", List.of(), List.of(),
                List.of(), TraceRunManifest.ExpectedMovieEndMode.TITLE_SCREEN, census);
    }
    @Test void loadingTheManifestPreservesItsOptionalTerminalCensus(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var path = directory.resolve("run_manifest.json");
        java.nio.file.Files.writeString(path, """
                {"game":"s1","run_id":"run","source_bk2":"movie.bk2","rom_checksum":"hash",
                "trace_schema":5,"recorder":"native-bizhawk-headless","recorder_version":"3.0",
                "segments":[],"transitions":[],"dynamic_art_gap_transitions":[],
                "expected_movie_end_mode":"title_screen","terminal_admission_runs":[1,2,1]}
                """);
        var loaded = TraceRunManifest.load(path);
        assertEquals(List.of(1,2,1), loaded.terminalAdmissionRuns());
        assertTrue(loaded.terminalRowIsLagged(10, 14, 11));
    }

    @Test void terminalCensusSelectsOnlyRecordedLagAdmission() {
        var run = run(List.of(2, 2, 1));
        assertFalse(run.terminalRowIsLagged(10, 15, 11));
        assertTrue(run.terminalRowIsLagged(10, 15, 12));
        assertTrue(run.terminalRowIsLagged(10, 15, 13));
        assertFalse(run.terminalRowIsLagged(10, 15, 14));
        assertThrows(IllegalStateException.class, () -> run.terminalRowIsLagged(10, 16, 12));
        assertThrows(IllegalStateException.class, () -> run.terminalRowIsLagged(10, 15, 15));
    }
    @Test void absentCensusKeepsExistingReplayAndInitialLagRunIsSupported() {
        assertFalse(run(null).terminalRowIsLagged(10, 15, 12));
        assertTrue(run(List.of(0, 2)).terminalRowIsLagged(10, 12, 10));
        assertThrows(IllegalArgumentException.class, () -> run(List.of(1, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> run(List.of(-1)));
    }
}
