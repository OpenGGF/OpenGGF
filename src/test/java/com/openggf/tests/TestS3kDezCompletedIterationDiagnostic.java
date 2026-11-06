package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test-only feasibility witnesses for the 2026-10-30 DEZ completed-iteration
 * diagnostic at evidence commit 6febc74c2. These do not extend the raw cold
 * prefix, qualify a native mid-object sample, or install completion authority.
 */
class TestS3kDezCompletedIterationDiagnostic {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void completedIterationCameraNormalizationRemainsSeparateFromRawRows() throws Exception {
        TraceData trace = TraceData.load(TestS3kDezColdRoutes.RUN.resolve("ssz"));
        TraceFrame previous = trace.getFrame(16447);
        TraceFrame raw = trace.getFrame(16448);
        TraceFrame held = trace.getFrame(16449);
        TraceFrame nextFull = trace.getFrame(16450);
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, previous, raw));
        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceReplayBootstrap.phaseForReplay(trace, raw, held));
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, held, nextFull));
        assertEquals(0x4041, raw.gameplayFrameCounter());
        assertEquals(0x4041, held.gameplayFrameCounter());
        assertEquals(0x4042, nextFull.gameplayFrameCounter());
        assertEquals(0x6180, raw.vblankCounter());
        assertEquals(0x6181, held.vblankCounter());
        assertEquals(0x6182, nextFull.vblankCounter());
        assertEquals(0, raw.lagCounter());
        assertEquals(1, held.lagCounter());

        TraceFrame normalized = TraceReplayBootstrap.s3kFrameForGameplayComparison(
                trace, 16448, previous, raw, TraceExecutionPhase.FULL_LEVEL_FRAME);
        assertEquals(raw.withCameraDiagnosticsFrom(held), normalized,
                "only camera diagnostics may be borrowed; every other record field stays raw");
        assertEquals(0x354E, raw.cameraX(), "raw frontier must remain intact");
        assertEquals(0x3554, normalized.cameraX(), "completed-iteration comparison only");
        assertSame(raw, trace.getFrame(16448), "normalization cannot mutate source rows");
        assertEquals(131, normalized.rings());

        // Comparator controls use recorded vectors, not engine-state writes or
        // timing input. The next dispatch's movement cannot pass as a held row.
        int[] expectedHeld = movement(held);
        assertThrows(AssertionFailedError.class,
                () -> assertArrayEquals(expectedHeld, movement(nextFull)),
                "an extra player dispatch must fail the held-row movement comparison");
        int[] alteredRing = movement(normalized);
        alteredRing[7]++;
        assertThrows(AssertionFailedError.class,
                () -> assertArrayEquals(movement(normalized), alteredRing),
                "camera normalization must never hide an altered ring");
        assertNotEquals(held.sidekick().x(), nextFull.sidekick().x(),
                "the recorded extra companion dispatch also differs");
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void coldDriverRewindGapStopsContinuationBeforeTheLagBoundary() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .withFreshLevelStartLifecycle()
                .withRecording(TestS3kDezColdRoutes.BK2.toAbsolutePath())
                .withRecordingStartFrame(TestS3kDezColdRoutes.SEGMENT_MOVIE_OFFSET)
                .build();
        RecordingFrameDriver driver = (RecordingFrameDriver) read(fixture.runner(), "driver");
        int cursorBefore = (int) read(driver, "currentBk2Index");
        int driverBefore = driver.getFrameCounter();
        int levelBefore = GameServices.level().getFrameCounter();
        long vintBefore = EngineTiming.vIntRunCounter().value();
        Bk2FrameInput pollBefore = (Bk2FrameInput) read(driver, "lastPolledBk2Input");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();

        assertEquals(TestS3kDezColdRoutes.SEGMENT_MOVIE_OFFSET, cursorBefore);
        assertEquals(cursorBefore - 1, pollBefore.frameIndex());
        fixture.stepFrameFromRecording(); // Original movie row 468982, no fabricated held row.
        assertEquals(cursorBefore + 1, read(driver, "currentBk2Index"));
        assertEquals(driverBefore + 1, driver.getFrameCounter());
        assertEquals(cursorBefore,
                ((Bk2FrameInput) read(driver, "lastPolledBk2Input")).frameIndex());
        int levelAfterStep = GameServices.level().getFrameCounter();
        long vintAfterStep = EngineTiming.vIntRunCounter().value();
        registry.restore(before);

        assertEquals(levelBefore, GameServices.level().getFrameCounter(), "level clock restored");
        assertEquals(vintBefore, EngineTiming.vIntRunCounter().value(), "V-int restored independently");
        assertEquals(cursorBefore + 1, read(driver, "currentBk2Index"), "movie cursor was not restored");
        assertEquals(driverBefore + 1, driver.getFrameCounter(), "driver clock was not restored");
        assertEquals(cursorBefore,
                ((Bk2FrameInput) read(driver, "lastPolledBk2Input")).frameIndex(),
                "original poll baseline was not restored");
        assertThrows(AssertionFailedError.class,
                () -> assertEquals(cursorBefore, read(driver, "currentBk2Index")),
                "a continuation requiring rewind must reject this driver");
        System.out.printf("DEZ diagnostic STOP: movie %d->%s, driver %d->%d, "
                        + "level %d->%d->%d, V-int %d->%d->%d, poll %d->%d; "
                        + "no resumed input or lag continuation executed%n",
                cursorBefore, read(driver, "currentBk2Index"), driverBefore, driver.getFrameCounter(),
                levelBefore, levelAfterStep, GameServices.level().getFrameCounter(),
                vintBefore, vintAfterStep, EngineTiming.vIntRunCounter().value(),
                pollBefore.frameIndex(),
                ((Bk2FrameInput) read(driver, "lastPolledBk2Input")).frameIndex());
        // Deliberately stop. No cursor reset, input priming, adapter registration,
        // trace hydration, or replay after the mismatched restore is permitted.
    }

    private static int[] movement(TraceFrame frame) {
        return new int[] {frame.x() & 0xFFFF, frame.y() & 0xFFFF,
                frame.xSpeed() & 0xFFFF, frame.ySpeed() & 0xFFFF, frame.gSpeed() & 0xFFFF,
                frame.xSub(), frame.ySub(), frame.rings()};
    }

    /** Observation only: deliberately no reflective writes or restoration workaround. */
    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
