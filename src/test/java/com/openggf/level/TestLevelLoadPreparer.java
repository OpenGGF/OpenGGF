package com.openggf.level;

import com.openggf.level.resources.PreparedLevelBuild;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestLevelLoadPreparer {
    @Test
    void retirementWaitsUntilRunningBuildReleasesItsResources() throws Exception {
        assertRetirementWaits(false);
    }

    @Test
    void interruptedRetirementStillWaitsAndPreservesInterruption() throws Exception {
        assertRetirementWaits(true);
    }

    private void assertRetirementWaits(boolean interrupted) throws Exception {
        LevelLoadPreparer preparer = new LevelLoadPreparer();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch retiring = new CountDownLatch(1);
        try (var caller = Executors.newSingleThreadExecutor()) {
            preparer.prepare(new Object(), 1, null, () -> {
                started.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return mock(PreparedLevelBuild.class);
                } finally {
                    finished.countDown();
                }
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                var retirement = caller.submit(() -> {
                    if (interrupted) Thread.currentThread().interrupt();
                    retiring.countDown();
                    preparer.discard();
                    assertEquals(0, finished.getCount(),
                            "retirement must finish decoding before its caller closes the ROM");
                    assertEquals(interrupted, Thread.currentThread().isInterrupted());
                });
                assertTrue(retiring.await(5, TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> retirement.get(100, TimeUnit.MILLISECONDS));
                release.countDown();
                retirement.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                assertTrue(finished.await(5, TimeUnit.SECONDS));
                preparer.discard();
            }
        }
    }

    @Test
    void matchingIdentityConsumesTheBuildOnlyOnce() {
        LevelLoadPreparer preparer = new LevelLoadPreparer();
        Object owner = new Object();
        PreparedLevelBuild build = mock(PreparedLevelBuild.class);
        preparer.prepare(owner, 1, "fire", () -> build);
        assertSame(build, preparer.take(owner, 1, "fire"));
        assertNull(preparer.take(owner, 1, "fire"));
    }

    @Test
    void anotherLoaderCannotConsumeAFormerOwnersBuild() {
        LevelLoadPreparer preparer = new LevelLoadPreparer();
        Object owner = new Object();
        preparer.prepare(owner, 1, "fire", () -> mock(PreparedLevelBuild.class));
        assertNull(preparer.take(new Object(), 1, "fire"));
        assertNull(preparer.take(owner, 1, "fire"));
    }

    @Test
    void anotherTargetDiscardsThePendingBuild() {
        LevelLoadPreparer preparer = new LevelLoadPreparer();
        Object owner = new Object();
        preparer.prepare(owner, 1, "fire", () -> mock(PreparedLevelBuild.class));
        assertNull(preparer.take(owner, 2, "fire"));
        assertNull(preparer.take(owner, 1, "fire"));
    }
}
