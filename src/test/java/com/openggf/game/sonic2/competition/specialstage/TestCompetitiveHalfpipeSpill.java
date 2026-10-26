package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeSpill {
    @Test
    void nativeTablesAndSignedSineWordsSelectEachSuccessfulVelocity() throws Exception {
        var data = data();
        assertEquals("38482a561c640e72008040305020601070008000",
                HexFormat.of().formatHex(data.rom().slice(0x353EA, 20)));
        assertEquals("610010fa066800800012", HexFormat.of().formatHex(data.rom().slice(0x354E4, 10)));
        assertEquals("066800380012", HexFormat.of().formatHex(data.rom().slice(0x16396, 6)));
        assertEquals("0508121c12ff0509131d13ff",
                HexFormat.of().formatHex(data.rom().slice(0x36318, 12)));
        assertEquals(new CompetitiveHalfpipeSpill.Velocity(0, -4096),
                CompetitiveHalfpipeSpill.velocity(data, 1, 0, 0x40));
        int[][] even = {{-196, -4016}, {196, -4016}, {-524, -3504}, {524, -3504},
                {-788, -2592}, {788, -2592}, {-964, -1376}, {964, -1376}, {-1024, 0}, {1024, 0}};
        int[][] odd = {{0, -4096}, {-388, -3776}, {388, -3776}, {-724, -2896}, {724, -2896}};
        for (int ordinal = 0; ordinal < even.length; ordinal++) {
            assertEquals(new CompetitiveHalfpipeSpill.Velocity(even[ordinal][0], even[ordinal][1]),
                    CompetitiveHalfpipeSpill.velocity(data, 10, ordinal, 0x40));
        }
        for (int ordinal = 0; ordinal < odd.length; ordinal++) {
            assertEquals(new CompetitiveHalfpipeSpill.Velocity(odd[ordinal][0], odd[ordinal][1]),
                    CompetitiveHalfpipeSpill.velocity(data, 5, ordinal, 0x40));
        }
        assertThrows(IllegalArgumentException.class, () -> CompetitiveHalfpipeSpill.velocity(data, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CompetitiveHalfpipeSpill.velocity(data, 11, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CompetitiveHalfpipeSpill.velocity(data, 2, 2, 0));
    }

    @Test
    void firstEightStepsKeepFixedPointFractionsAndUseOldVelocityBeforeGravity() throws Exception {
        var spill = new CompetitiveHalfpipeSpill(data(), 100, 150, 0x180, -0x280);
        assertEquals(0, spill.mappingFrame(), "initialization must not impersonate a Main/display pass");
        assertEquals(5, spill.priority());
        assertEquals(0, spill.capture().previousAnimation());
        assertEquals(0, spill.capture().frameDuration());
        int[] x = {0x658000, 0x670000, 0x688000, 0x6A0000, 0x6B8000, 0x6D0000, 0x6E8000, 0x700000};
        int[] y = {0x938000, 0x91B800, 0x90A800, 0x905000, 0x90B000, 0x91C800, 0x939800, 0x962000};
        int[] velocity = {-456, -272, -88, 96, 280, 464, 648, 832};
        int[] countdown = {5, 4, 3, 5, 4, 3, 2, 1};
        for (int step = 0; step < x.length; step++) {
            spill.tick();
            var state = spill.capture();
            assertEquals(x[step], state.xFixed());
            assertEquals(y[step], state.yFixed());
            assertEquals(velocity[step], state.yVelocity());
            assertEquals(countdown[step], state.frameDuration());
            assertEquals(step < 3 ? 8 : 9, spill.mappingFrame());
            assertEquals(step < 3 ? 5 : 0, spill.priority());
            assertEquals(x[step] >> 16, spill.x());
            assertEquals(y[step] >> 16, spill.y());
            assertFalse(spill.removed());
        }
    }

    @Test
    void genericAnimationHoldsSixTicksAndLoopsFourFramesWithoutASeparateLifetime() throws Exception {
        var spill = new CompetitiveHalfpipeSpill(data(), 128, -500, 0, 0);
        int[] frames = {9, 0x13, 0x1D, 0x13};
        for (int step = 0; step < 25; step++) {
            spill.tick();
            assertEquals(frames[(step / 6) % frames.length], spill.mappingFrame());
            assertEquals(5 - step % 6, spill.capture().frameDuration());
            assertEquals(0, spill.priority());
            assertFalse(spill.removed(), "negative screen Y is legal, rather than an artificial lifetime cutoff");
        }
    }

    @Test
    void deletionUsesNativeHorizontalRangeAndStrictLowerScreenEdge() throws Exception {
        var data = data();
        var left = new CompetitiveHalfpipeSpill(data, 0, 0, -0x100, 0);
        left.tick();
        assertTrue(left.removed());
        var right = new CompetitiveHalfpipeSpill(data, 255, 0, 0x100, 0);
        right.tick();
        assertTrue(right.removed());
        var edge = new CompetitiveHalfpipeSpill(data, 255, 224, 0, 0);
        edge.tick();
        assertEquals(224, edge.y());
        assertFalse(edge.removed(), "native BGT allows Y == 224");
        var below = new CompetitiveHalfpipeSpill(data, 128, 225, 0, 0);
        below.tick();
        assertTrue(below.removed());
        assertEquals(0, below.mappingFrame(), "bounds deletion precedes animation/display");
        var above = new CompetitiveHalfpipeSpill(data, 128, -1, 0, -0x100);
        above.tick();
        assertEquals(-2, above.y());
        assertFalse(above.removed());
        var wordVelocity = new CompetitiveHalfpipeSpill(data, 100, 0, 0xFF00, 0x10000);
        wordVelocity.tick();
        assertEquals(99, wordVelocity.x());
        assertEquals(0, wordVelocity.y());
        assertEquals(-0x100, wordVelocity.capture().xVelocity());
        var removed = left.capture();
        left.tick();
        assertEquals(removed, left.capture());
    }

    @Test
    void recreationRestoresSubpixelsVelocitiesAndAnimationThroughForwardDeletion() throws Exception {
        var data = data();
        var original = new CompetitiveHalfpipeSpill(data, 100, -200, 0x155, -0x2A0);
        for (int step = 0; step < 5; step++) original.tick();
        var snapshot = original.capture();
        assertNotEquals(0, snapshot.xFixed() & 0xFFFF);
        assertNotEquals(0, snapshot.yFixed() & 0xFFFF);
        var recreated = new CompetitiveHalfpipeSpill(data, 0, 0, 0, 0);
        recreated.restore(snapshot);
        for (int step = 0; step < 80; step++) {
            original.tick();
            recreated.tick();
            assertEquals(original.capture(), recreated.capture());
        }
        assertTrue(recreated.removed());
        recreated.restore(snapshot);
        assertEquals(snapshot, recreated.capture());
    }

    private static CompetitiveHalfpipeRomData data() throws Exception {
        return new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
    }
}
