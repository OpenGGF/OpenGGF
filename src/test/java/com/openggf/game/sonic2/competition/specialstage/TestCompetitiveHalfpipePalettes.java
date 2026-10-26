package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.level.Palette;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipePalettes {
    @Test
    void actualFadePassesVisitBlueThenGreenThenRedForEveryStageColor() throws Exception {
        var data = data();
        var owner = new CompetitiveHalfpipePalettes(data);
        assertEquals("044102006508b242650430c14e753203044100206508b242650430c1",
                HexFormat.of().formatHex(data.rom().slice(0x256C, 28)));
        int[] black = {0xCEE, 0xAEE, 0x8EE, 0x6EE, 0x4EE, 0x2EE, 0x0EE,
                0x0CE, 0x0AE, 0x08E, 0x06E, 0x04E, 0x02E, 0x00E,
                0x00C, 0x00A, 0x008, 0x006, 0x004, 0x002, 0x000, 0x000};
        for (int stage = 0; stage < 7; stage++) {
            owner.load(stage);
            assertTrue(owner.capture().words().stream().allMatch(word -> word == 0xEEE));
            int paletteIndex = stage < 2 ? stage : stage + 3;
            int paletteId = data.rom().readU16BE(0x778E + paletteIndex * 2);
            int stageAddress = data.rom().readU32BE(0x2782 + paletteId * 8);
            for (int pass = 1; pass <= 22; pass++) {
                owner.fadeStep();
                assertEquals(pass, owner.capture().fadePass());
                assertEquals(black[pass - 1], owner.capture().words().getFirst());
                for (int color = 0; color < 64; color++) {
                    int address = color < 48 ? 0x3162 + color * 2 : stageAddress + (color - 48) * 2;
                    int target = data.rom().readU16BE(address);
                    assertEquals(expectedColor(target, pass), owner.capture().words().get(color),
                            "stage " + stage + ", color " + color + ", pass " + pass);
                }
            }
            Palette[] expected = data.palettes(stage);
            Palette[] actual = owner.palettes();
            for (int line = 0; line < 4; line++) assertTrue(expected[line].dataEquals(actual[line]));
            var complete = owner.capture();
            owner.fadeStep();
            assertEquals(complete, owner.capture());
        }
    }

    @Test
    void nativeStarsUseZeroAndFourSeedsAtActualVintDivisibility() throws Exception {
        var data = data();
        var owner = faded(data, 4);
        assertEquals("0eee0ccc0aaa088808880aaa0ccc0eee",
                HexFormat.of().formatHex(data.rom().slice(0x542A, 16)));
        assertEquals(0, owner.capture().starOne());
        assertEquals(4, owner.capture().starTwo());
        var before = owner.capture();
        for (int vint = 1; vint < 4; vint++) owner.tick(vint, false);
        assertEquals(before, owner.capture());
        owner.tick(4, false);
        assertEquals(0xEEE, owner.capture().words().get(14));
        assertEquals(0x888, owner.capture().words().get(15));
        assertEquals(1, owner.capture().starOne());
        assertEquals(5, owner.capture().starTwo());
        owner.tick(8, false);
        assertEquals(0xCCC, owner.capture().words().get(14));
        assertEquals(0xAAA, owner.capture().words().get(15));
        for (int step = 2; step < 8; step++) owner.tick((step + 1) * 4, false);
        assertEquals(0xEEE, owner.capture().words().get(14));
        assertEquals(0x888, owner.capture().words().get(15));
    }

    @Test
    void rainbowEdgesOverrideSamePassCyclesAndPhasePersistsAcrossCheckpoints() throws Exception {
        var data = data();
        var owner = faded(data, 0);
        assertEquals("00ee0088004400ee00cc0088", HexFormat.of().formatHex(data.rom().slice(0x35548, 12)));
        assertEquals("00ee00cc008800e000c000800ee00cc008800e0e0c0c0808",
                HexFormat.of().formatHex(data.rom().slice(0x54C4, 24)));
        owner.tick(8, true);
        assertRainbow(owner, 0xEE, 0xCC, 0x88);
        assertEquals(0, owner.capture().rainbowPhase(), "begin follows PalCycle_SS and does not invent a cycle");
        int[][] cycles = {{0xEE, 0xCC, 0x88}, {0xE0, 0xC0, 0x80},
                {0xEE0, 0xCC0, 0x880}, {0xE0E, 0xC0C, 0x808}, {0xEE, 0xCC, 0x88}};
        for (int step = 0; step < cycles.length; step++) {
            owner.tick(16 + step * 8, true);
            assertRainbow(owner, cycles[step][0], cycles[step][1], cycles[step][2]);
        }
        owner.tick(56, false);
        assertRainbow(owner, 0xEE, 0x88, 0x44);
        assertEquals(6, owner.capture().rainbowPhase(), "end-cycle work precedes Obj5A's reset colors");
        owner.tick(64, true);
        assertRainbow(owner, 0xEE, 0xCC, 0x88);
        owner.tick(72, true);
        assertRainbow(owner, 0xEE0, 0xCC0, 0x880);
        assertEquals(7, owner.capture().rainbowPhase());
    }

    @Test
    void competitiveStageSixNeverRunsTheOrdinarySummedRingRotation() throws Exception {
        var data = data();
        var owner = faded(data, 6);
        var initial = owner.capture();
        for (int vint = 0; vint < 96; vint++) owner.tick(vint, false);
        for (int color = 0; color < 64; color++) {
            if (color != 14 && color != 15) assertEquals(initial.words().get(color), owner.capture().words().get(color));
        }
        assertEquals(0, owner.capture().rainbowPhase());
    }

    @Test
    void snapshotsAndReturnedPalettesStayIsolatedAndReplayExactVintPhases() throws Exception {
        var data = data();
        var owner = faded(data, 5);
        for (int vint = 0; vint < 61; vint++) owner.tick(vint, vint >= 9);
        var snapshot = owner.capture();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.words().clear());
        Palette[] returned = owner.palettes();
        returned[0].getColor(1).r ^= 0x7F;
        assertEquals(snapshot, owner.capture());
        List<CompetitiveHalfpipePalettes.State> forward = new ArrayList<>();
        for (int vint = 61; vint < 137; vint++) {
            owner.tick(vint, vint < 83 || vint >= 102);
            forward.add(owner.capture());
        }
        owner.load(0);
        assertEquals(0, owner.capture().starOne());
        assertEquals(4, owner.capture().starTwo());
        assertEquals(0, owner.capture().rainbowPhase());
        owner.restore(snapshot);
        for (int vint = 61; vint < 137; vint++) {
            owner.tick(vint, vint < 83 || vint >= 102);
            assertEquals(forward.get(vint - 61), owner.capture());
        }
    }

    private static int expectedColor(int target, int pass) {
        int blueSteps = 7 - ((target >>> 9) & 7);
        int greenSteps = 7 - ((target >>> 5) & 7);
        int redSteps = 7 - ((target >>> 1) & 7);
        int blue = 7 - Math.min(pass, blueSteps);
        int green = 7 - Math.min(Math.max(pass - blueSteps, 0), greenSteps);
        int red = 7 - Math.min(Math.max(pass - blueSteps - greenSteps, 0), redSteps);
        return (blue << 9) | (green << 5) | (red << 1);
    }

    private static void assertRainbow(CompetitiveHalfpipePalettes owner, int one, int two, int three) {
        assertEquals(List.of(one, two, three), owner.capture().words().subList(59, 62));
    }

    private static CompetitiveHalfpipePalettes faded(CompetitiveHalfpipeRomData data, int stage) {
        var owner = new CompetitiveHalfpipePalettes(data);
        owner.load(stage);
        for (int pass = 0; pass < 22; pass++) owner.fadeStep();
        return owner;
    }

    private static CompetitiveHalfpipeRomData data() throws Exception {
        return new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
    }
}
