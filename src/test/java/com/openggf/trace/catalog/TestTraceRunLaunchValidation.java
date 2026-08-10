package com.openggf.trace.catalog;

import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.tests.trace.TraceV5RunFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRunLaunchValidation {

    @Test
    void generatedV5RunPassesLaunchValidation(@TempDir Path root) throws Exception {
        prepareSyntheticRunWithValidMovie(root);
        TraceEntry entry = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .filter(candidate -> "run_aiz_gumball_3seg".equals(
                        candidate.runManifest().runId()))
                .findFirst()
                .orElseThrow();

        TraceCatalog.RunLaunchValidation validation =
                TraceCatalog.validateRunLaunch(entry);

        assertTrue(validation.launchable(), validation.diagnostic());
    }

    @Test
    void preparationLoadsGeneratedV5MovieAndSegmentPayloadsExactlyOnce(
            @TempDir Path root) throws Exception {
        prepareSyntheticRunWithValidMovie(root);
        TraceEntry entry = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .filter(candidate -> "run_aiz_gumball_3seg".equals(
                        candidate.runManifest().runId()))
                .findFirst()
                .orElseThrow();
        AtomicInteger movieLoads = new AtomicInteger();
        AtomicInteger planLoads = new AtomicInteger();

        TraceCatalog.PreparedRunLaunch prepared = TraceCatalog.prepareRunLaunch(
                entry,
                movie -> {
                    movieLoads.incrementAndGet();
                    return new com.openggf.debug.playback.Bk2MovieLoader().load(movie);
                },
                (manifest, runDir) -> {
                    planLoads.incrementAndGet();
                    return TraceRunReplayWalker.plan(manifest, runDir);
                });

        assertEquals(1, movieLoads.get());
        assertEquals(1, planLoads.get());
        assertFalse(prepared.segments().isEmpty());
    }

    @Test
    void generatedV5RunPlansEverySegment(@TempDir Path root) throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        TraceRunManifest run = TraceRunManifest.load(
                runDir.resolve("run_manifest.json"));

        var plans = TraceRunReplayWalker.plan(run, runDir);

        assertEquals(3, plans.size());
        assertTrue(plans.stream().allMatch(plan -> plan.trace().metadata()
                .hasPerFrameDynamicArtTransferState()));
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
    void headerlessLegacyLevelSegmentRetainsEveryRowDuringPreparation(
            @TempDir Path root) throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        Path physics = runDir.resolve("seg00_aiz/physics.csv");
        List<String> lines = Files.readAllLines(physics);
        Files.write(physics, lines.subList(1, lines.size()));
        TraceEntry entry = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .findFirst()
                .orElseThrow();

        TraceCatalog.PreparedRunLaunch prepared =
                TraceCatalog.prepareRunLaunch(entry);

        assertEquals(2, prepared.segments().getFirst().trace().frameCount());
    }

    @Test
    void nonLevelSegmentZeroRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        mutateManifest(runDir, json -> json
                .replaceFirst("\"kind\":\"level\"",
                        "\"kind\":\"special_stage\"")
                .replaceFirst("\"act\":1}",
                        "\"act\":1,\"special_stage_index\":0}"));

        assertVisibleButInvalid(root, "segment 0 must be level");
    }

    @Test
    void outOfBoundsMovieRangeRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        mutateManifest(runDir,
                json -> json.replace("\"bk2_frame_offset\":2900",
                        "\"bk2_frame_offset\":999999"));

        assertVisibleButInvalid(root, "BK2 range");
    }

    @Test
    void profileMismatchRemainsVisibleWithLaunchDiagnostic(@TempDir Path root)
            throws Exception {
        Path runDir = prepareSyntheticRunWithValidMovie(root);
        mutateManifest(runDir,
                json -> json.replace("\"trace_profile\":\"s3k_bonus_stage\"",
                        "\"trace_profile\":\"unsupported_bonus\""));

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
                json -> json.replaceFirst("\"trace_frame_count\":2",
                        "\"trace_frame_count\":3"));

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
        Path runDir = TraceV5RunFixture.writeS3kBonusRun(root.resolve("s3k/runs"));
        TraceV5RunFixture.writeMovie(runDir.resolve("synthetic.bk2"));
        return runDir;
    }

    private static void mutateManifest(Path runDir,
            java.util.function.UnaryOperator<String> mutation) throws IOException {
        Path manifest = runDir.resolve("run_manifest.json");
        Files.writeString(manifest, mutation.apply(Files.readString(manifest)));
    }

}
