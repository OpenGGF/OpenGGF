package com.openggf.trace.catalog;

import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRunLaunchValidation {

    @Test
    void committedSchemaTwoRunValidatesNonEmptyDestinationOpeningLedger()
            throws Exception {
        Path runDir = Path.of("src", "test", "resources", "traces", "s2",
                "runs", "s2-ehz-halfpipe-roundtrip");
        TraceRunManifest run = TraceRunManifest.load(
                runDir.resolve("run_manifest.json"));

        var plans = TraceRunReplayWalker.plan(run, runDir);

        assertEquals(5, plans.size());
        assertEquals(List.of(8078L), plans.get(3).segment()
                .dynamicArtInitialLedgerDescriptors().stream()
                .map(descriptor -> descriptor.transferId())
                .toList());
    }

    @Test
    void compatibleLevelFirstRunPassesLaunchValidation(@TempDir Path root)
            throws Exception {
        prepareSyntheticRunWithValidMovie(root);
        TraceEntry entry = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .findFirst()
                .orElseThrow();

        TraceCatalog.RunLaunchValidation validation =
                TraceCatalog.validateRunLaunch(entry);

        assertTrue(validation.launchable(), validation.diagnostic());
    }

    @Test
    void nonLevelSegmentZeroRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        mutateManifest(runDir, json -> json
                .replaceFirst("\"kind\": \"level\"",
                        "\"kind\": \"special_stage\"")
                .replaceFirst("\"act\": 1}",
                        "\"act\": 1, \"special_stage_index\": 0}"));

        assertVisibleButInvalid(root, "segment 0 must be level");
    }

    @Test
    void outOfBoundsMovieRangeRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        mutateManifest(runDir,
                json -> json.replace("\"bk2_frame_offset\": 2900",
                        "\"bk2_frame_offset\": 999999"));

        assertVisibleButInvalid(root, "BK2 range");
    }

    @Test
    void profileMismatchRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        mutateManifest(runDir,
                json -> json.replace("\"trace_profile\": \"s3k_bonus_stage\"",
                        "\"trace_profile\": \"unsupported_bonus\""));

        assertVisibleButInvalid(root, "profile");
    }

    @Test
    void parserFailureRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        Files.writeString(runDir.resolve("seg01_gumball/physics.csv"),
                "frame,bad\nnot,a,valid,trace,row\n");

        assertVisibleButInvalid(root, "parser");
    }

    @Test
    void rowCountMismatchRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        mutateManifest(runDir,
                json -> json.replaceFirst("\"trace_frame_count\": 2",
                        "\"trace_frame_count\": 3"));

        assertVisibleButInvalid(root, "row count");
    }

    private static void assertVisibleButInvalid(Path root, String diagnosticFragment) {
        TraceEntry entry = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "deeper launch incompatibility must not hide the run"));
        TraceCatalog.RunLaunchValidation validation =
                TraceCatalog.validateRunLaunch(entry);
        assertFalse(validation.launchable());
        assertTrue(validation.diagnostic().contains(diagnosticFragment),
                validation.diagnostic());
    }

    private static Path prepareSyntheticRunWithValidMovie(Path root) throws IOException {
        Path source = Path.of("src", "test", "resources", "traces", "synthetic",
                "run_aiz_gumball_3seg");
        Path runDir = root.resolve("s3k/runs/run_aiz_gumball_3seg");
        copyRecursively(source, runDir);
        Files.copy(
                Path.of("src", "test", "resources", "traces", "s2", "runs",
                        "s2-ehz-halfpipe-roundtrip", "s2-ehz-halfpipe-roundtrip.bk2"),
                runDir.resolve("synthetic.bk2"));
        return runDir;
    }

    private static void mutateManifest(Path runDir,
            java.util.function.UnaryOperator<String> mutation) throws IOException {
        Path manifest = runDir.resolve("run_manifest.json");
        Files.writeString(manifest, mutation.apply(Files.readString(manifest)));
    }

    private static void copyRecursively(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }
}
