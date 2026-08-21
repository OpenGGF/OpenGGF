package com.openggf.trace.catalog;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.tests.trace.TraceV5RunFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceCatalogDescriptorOwnership {

    @Test
    void catalogValidationPlansDescriptorsWithoutEagerReplayPayloads(
            @TempDir Path root) throws Exception {
        Path runDir = TraceV5RunFixture.writeS3kBonusRun(root.resolve("s3k/runs"));
        TraceV5RunFixture.writeMovie(runDir.resolve("synthetic.bk2"));
        TraceEntry entry = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .findFirst()
                .orElseThrow();
        AtomicInteger descriptorPlans = new AtomicInteger();
        AtomicInteger eagerPlans = new AtomicInteger();

        TraceCatalog.RunLaunchValidation validation = TraceCatalog.validateRunLaunch(
                entry,
                movie -> new Bk2MovieLoader().load(movie),
                new TraceCatalog.RunPlannerPair(
                        (manifest, directory) -> {
                            descriptorPlans.incrementAndGet();
                            return TraceRunReplayWalker.planDescriptors(manifest, directory);
                        },
                        (manifest, directory) -> {
                            throw new AssertionError(
                                    "validation must not plan eager replay payloads");
                        }));

        assertTrue(validation.launchable(), validation.diagnostic());
        assertEquals(1, descriptorPlans.get());
        assertEquals(0, eagerPlans.get());

        TraceCatalog.prepareRunLaunch(
                entry,
                movie -> new Bk2MovieLoader().load(movie),
                new TraceCatalog.RunPlannerPair(
                        (manifest, directory) -> {
                            throw new AssertionError(
                                    "preparation must not plan only descriptors");
                        },
                        (manifest, directory) -> {
                            eagerPlans.incrementAndGet();
                            return TraceRunReplayWalker.plan(manifest, directory);
                        }));

        assertEquals(1, descriptorPlans.get());
        assertEquals(1, eagerPlans.get());
    }
}
