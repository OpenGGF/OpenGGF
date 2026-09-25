package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Rewind coverage for the Sonic 3 &amp; Knuckles Death Egg presentation slice: the
 * {@code AnPal_DEZ} cycle counters, the {@code AniPLC_DEZ} script counters and the
 * {@code Events_routine_fg} / {@code Events_fg_4} words in {@link S3kDezZoneRuntimeState}.
 *
 * <p>Each case captures, runs forward, restores, asserts the restored state equals the captured
 * state, and then replays the same forward run to check it lands on the same values again — a
 * restore that only looked right because nothing had moved would fail the replay.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezPresentationRewind {

    private static RewindRegistry rewind() {
        return TestEnvironment.activeGameplayMode().getRewindRegistry();
    }

    private static S3kDezZoneRuntimeState dezState() {
        return S3kRuntimeStates.currentDez(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    /** Palette line 3 colours 8-14 and line 4 colours 12-15: every colour the DEZ cycles touch. */
    private static List<String> cycledColours(Level level) {
        List<String> values = new ArrayList<>();
        for (int colour = 8; colour <= 14; colour++) {
            values.add(describe(level.getPalette(2).getColor(colour)));
        }
        for (int colour = 12; colour <= 15; colour++) {
            values.add(describe(level.getPalette(3).getColor(colour)));
        }
        return values;
    }

    private static String describe(Palette.Color colour) {
        return (colour.r & 0xFF) + "," + (colour.g & 0xFF) + "," + (colour.b & 0xFF);
    }

    @Test
    void restoringReturnsThePaletteCycleCountersAndReplaysToTheSameColours() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
        Level level = GameServices.level().getCurrentLevel();
        fixture.stepIdleFrames(0x40);

        CompositeSnapshot checkpoint = rewind().capture();
        List<String> atCheckpoint = cycledColours(level);

        // 23 passes is more than four channel B periods (5) and more than one channel C period
        // (20), so those two channels have certainly advanced before the restore. Channel A
        // (period 16) advances too, but AnPal_PalDEZ1 frames 2 and 4 are byte-identical, so over
        // this window its four colours can legitimately read back unchanged; its own cadence is
        // asserted in TestS3kDezPaletteCycling.
        fixture.stepIdleFrames(23);
        List<String> afterRun = cycledColours(level);
        assertNotEquals(atCheckpoint, afterRun, "the cycles must actually advance over 23 passes");

        rewind().restore(checkpoint);
        assertEquals(atCheckpoint, cycledColours(GameServices.level().getCurrentLevel()),
                "restore must return every cycled colour");

        fixture.stepIdleFrames(23);
        assertEquals(afterRun, cycledColours(GameServices.level().getCurrentLevel()),
                "forward replay from the restored counters must reach the same colours");
    }

    @Test
    void restoringReturnsTheEventRoutineAndFlagWords() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        fixture.stepIdleFrames(4);
        assertEquals(4, dezState().foregroundRoutine());

        CompositeSnapshot beforeStage = rewind().capture();

        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);
        assertEquals(8, dezState().foregroundRoutine(), "the stage must advance before the restore");

        rewind().restore(beforeStage);
        assertEquals(4, dezState().foregroundRoutine(),
                "restore must return Events_routine_fg to the captured stage");
        assertEquals(0, dezState().eventsFg4(), "and Events_fg_4 with it");

        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);
        assertEquals(8, dezState().foregroundRoutine(),
                "forward replay from the restored words advances the same way");
    }
}
