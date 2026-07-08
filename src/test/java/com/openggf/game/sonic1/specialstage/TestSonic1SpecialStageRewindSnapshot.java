package com.openggf.game.sonic1.specialstage;

import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestSonic1SpecialStageRewindSnapshot {
    private static final int[] EXPECTED_SCROLL_BAND_WIDTHS = {6, 0x30, 0x30, 0x30, 0x28, 0x18, 0x18, 0x18};

    @Test
    void snapshotRoundTripDeepCopiesSpecialStageRuntimeState() throws Exception {
        Sonic1SpecialStageManager manager = new Sonic1SpecialStageManager();
        seedSnapshotState(manager);

        Sonic1SpecialStageSnapshot snapshot = manager.captureRewindSnapshot();
        Sonic1SpecialStageSnapshot secondSnapshot = manager.captureRewindSnapshot();
        assertSnapshotPayloadsAreEqualButNotSame(snapshot, secondSnapshot);

        mutateLiveState(manager);

        manager.restoreRewindSnapshot(snapshot);
        assertRestoredState(manager, snapshot);
        assertRestoredPayloadsAreNotSnapshotAliases(manager, snapshot);
        assertExpectedBandHScroll(manager);

        mutateLiveState(manager);
        manager.restoreRewindSnapshot(snapshot);
        assertRestoredState(manager, snapshot);
        assertRestoredPayloadsAreNotSnapshotAliases(manager, snapshot);
        assertExpectedBandHScroll(manager);
    }

    private static void seedSnapshotState(Sonic1SpecialStageManager manager) throws Exception {
        set(manager, "initialized", true);
        set(manager, "finished", true);
        set(manager, "emeraldCollected", true);
        set(manager, "debugMode", true);
        set(manager, "currentStage", 3);
        set(manager, "ringsCollected", 47);
        set(manager, "ssAngle", 0xA123);
        set(manager, "ssRotate", -0x0234);
        set(manager, "debugSavedAngle", 0x3456);
        set(manager, "debugSavedRotate", 0x0789);
        set(manager, "sonicPosX", 0x12345678L);
        set(manager, "sonicPosY", 0x23456789L);
        set(manager, "sonicVelX", -321);
        set(manager, "sonicVelY", 654);
        set(manager, "sonicInertia", -987);
        set(manager, "sonicAirborne", true);
        set(manager, "sonicFacingLeft", true);
        set(manager, "cameraX", 111);
        set(manager, "cameraY", 222);
        set(manager, "ghostState", 2);
        set(manager, "upDownCooldown", 9);
        set(manager, "reverseCooldown", 11);
        set(manager, "ringAnimFrame", 3);
        set(manager, "ringAnimTimer", 4);
        set(manager, "wallVramAnimFrame", 5);
        set(manager, "wallVramAnimTimer", 6);
        set(manager, "sonicAnimId", 7);
        set(manager, "sonicAnimFrameIndex", 8);
        set(manager, "sonicAnimFrameTimer", 9);
        set(manager, "palSsTime", 10);
        set(manager, "palSsNum", 11);
        set(manager, "palSsIndex", 12);
        set(manager, "ani2Frame", 1);
        set(manager, "ani2Timer", 13);
        set(manager, "ani3Frame", 3);
        set(manager, "ani3Timer", 14);
        set(manager, "sonicSpriteFrame", 15);
        set(manager, "exitTriggered", true);
        set(manager, "exitPhase", 16);
        set(manager, "exitTimer", 17);
        set(manager, "exitFadeStarted", true);
        set(manager, "exitFadeTimer", 18);
        set(manager, "heldButtons", 0x5A);
        set(manager, "pressedButtons", 0xA5);
        set(manager, "bgAnimState", 12);
        set(manager, "bgUsingPlane6", false);
        set(manager, "fgAnimPlaneIndex", 3);
        set(manager, "fgYScroll", 0x100);
        set(manager, "bgYScroll", 5);
        set(manager, "bgExtraScrollX", -19);

        set(manager, "layout", new byte[] {1, 2, 3, 4, 5});
        set(manager, "ssAnimBuffer", new int[][] {
                {1, 2, 3, 4},
                {5, 6, 7, 8},
                {9, 10, 11, 12}
        });
        set(manager, "ssAnimGlassFinalBlock", new int[] {21, 22, 23});
        set(manager, "bgSineBuffer", new int[] {31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                41, 42, 43, 44, 45, 46, 47, 48, 49, 50});
        set(manager, "bgBandBuffer", new int[] {101, 1, 202, 2, 303, 3, 404, 4, 505, 5, 606, 6, 707, 7});
        set(manager, "bgHScrollData", filledArray(224, -777));
        set(manager, "ssPalettes", palettes());
    }

    private static void mutateLiveState(Sonic1SpecialStageManager manager) throws Exception {
        set(manager, "initialized", false);
        set(manager, "finished", false);
        set(manager, "emeraldCollected", false);
        set(manager, "debugMode", false);
        set(manager, "currentStage", 0);
        set(manager, "ringsCollected", 0);
        set(manager, "ssAngle", 0x1111);
        set(manager, "ssRotate", 0);
        set(manager, "debugSavedAngle", 0);
        set(manager, "debugSavedRotate", 0);
        set(manager, "sonicPosX", 1L);
        set(manager, "sonicPosY", 2L);
        set(manager, "sonicVelX", 3);
        set(manager, "sonicVelY", 4);
        set(manager, "sonicInertia", 5);
        set(manager, "sonicAirborne", false);
        set(manager, "sonicFacingLeft", false);
        set(manager, "cameraX", 6);
        set(manager, "cameraY", 7);
        set(manager, "ghostState", 0);
        set(manager, "upDownCooldown", 0);
        set(manager, "reverseCooldown", 0);
        set(manager, "ringAnimFrame", 0);
        set(manager, "ringAnimTimer", 0);
        set(manager, "wallVramAnimFrame", 0);
        set(manager, "wallVramAnimTimer", 0);
        set(manager, "sonicAnimId", 0);
        set(manager, "sonicAnimFrameIndex", 0);
        set(manager, "sonicAnimFrameTimer", 0);
        set(manager, "palSsTime", 0);
        set(manager, "palSsNum", 0);
        set(manager, "palSsIndex", 0);
        set(manager, "ani2Frame", 0);
        set(manager, "ani2Timer", 0);
        set(manager, "ani3Frame", 0);
        set(manager, "ani3Timer", 0);
        set(manager, "sonicSpriteFrame", 0);
        set(manager, "exitTriggered", false);
        set(manager, "exitPhase", 0);
        set(manager, "exitTimer", 0);
        set(manager, "exitFadeStarted", false);
        set(manager, "exitFadeTimer", 0);
        set(manager, "heldButtons", 0);
        set(manager, "pressedButtons", 0);
        set(manager, "bgAnimState", 0);
        set(manager, "bgUsingPlane6", true);
        set(manager, "fgAnimPlaneIndex", 0);
        set(manager, "fgYScroll", 0);
        set(manager, "bgYScroll", 99);
        set(manager, "bgExtraScrollX", 99);

        ((byte[]) get(manager, "layout"))[0] = 99;
        ((int[][]) get(manager, "ssAnimBuffer"))[0][0] = 99;
        ((int[]) get(manager, "ssAnimGlassFinalBlock"))[0] = 99;
        ((int[]) get(manager, "bgSineBuffer"))[0] = 99;
        ((int[]) get(manager, "bgBandBuffer"))[0] = 99;
        ((int[]) get(manager, "bgHScrollData"))[0] = 99;
        ((Palette[]) get(manager, "ssPalettes"))[0].getColor(0).r = 99;
    }

    private static void assertRestoredState(Sonic1SpecialStageManager manager,
                                            Sonic1SpecialStageSnapshot snapshot) throws Exception {
        assertEquals(snapshot.initialized, get(manager, "initialized"));
        assertEquals(snapshot.finished, get(manager, "finished"));
        assertEquals(snapshot.emeraldCollected, get(manager, "emeraldCollected"));
        assertEquals(snapshot.debugMode, get(manager, "debugMode"));
        assertEquals(snapshot.currentStage, get(manager, "currentStage"));
        assertEquals(snapshot.ringsCollected, get(manager, "ringsCollected"));
        assertEquals(snapshot.ssAngle, get(manager, "ssAngle"));
        assertEquals(snapshot.ssRotate, get(manager, "ssRotate"));
        assertEquals(snapshot.debugSavedAngle, get(manager, "debugSavedAngle"));
        assertEquals(snapshot.debugSavedRotate, get(manager, "debugSavedRotate"));
        assertEquals(snapshot.sonicPosX, get(manager, "sonicPosX"));
        assertEquals(snapshot.sonicPosY, get(manager, "sonicPosY"));
        assertEquals(snapshot.sonicVelX, get(manager, "sonicVelX"));
        assertEquals(snapshot.sonicVelY, get(manager, "sonicVelY"));
        assertEquals(snapshot.sonicInertia, get(manager, "sonicInertia"));
        assertEquals(snapshot.sonicAirborne, get(manager, "sonicAirborne"));
        assertEquals(snapshot.sonicFacingLeft, get(manager, "sonicFacingLeft"));
        assertEquals(snapshot.cameraX, get(manager, "cameraX"));
        assertEquals(snapshot.cameraY, get(manager, "cameraY"));
        assertEquals(snapshot.ghostState, get(manager, "ghostState"));
        assertEquals(snapshot.upDownCooldown, get(manager, "upDownCooldown"));
        assertEquals(snapshot.reverseCooldown, get(manager, "reverseCooldown"));
        assertEquals(snapshot.ringAnimFrame, get(manager, "ringAnimFrame"));
        assertEquals(snapshot.ringAnimTimer, get(manager, "ringAnimTimer"));
        assertEquals(snapshot.wallVramAnimFrame, get(manager, "wallVramAnimFrame"));
        assertEquals(snapshot.wallVramAnimTimer, get(manager, "wallVramAnimTimer"));
        assertEquals(snapshot.sonicAnimId, get(manager, "sonicAnimId"));
        assertEquals(snapshot.sonicAnimFrameIndex, get(manager, "sonicAnimFrameIndex"));
        assertEquals(snapshot.sonicAnimFrameTimer, get(manager, "sonicAnimFrameTimer"));
        assertEquals(snapshot.palSsTime, get(manager, "palSsTime"));
        assertEquals(snapshot.palSsNum, get(manager, "palSsNum"));
        assertEquals(snapshot.palSsIndex, get(manager, "palSsIndex"));
        assertEquals(snapshot.ani2Frame, get(manager, "ani2Frame"));
        assertEquals(snapshot.ani2Timer, get(manager, "ani2Timer"));
        assertEquals(snapshot.ani3Frame, get(manager, "ani3Frame"));
        assertEquals(snapshot.ani3Timer, get(manager, "ani3Timer"));
        assertEquals(snapshot.sonicSpriteFrame, get(manager, "sonicSpriteFrame"));
        assertEquals(snapshot.exitTriggered, get(manager, "exitTriggered"));
        assertEquals(snapshot.exitPhase, get(manager, "exitPhase"));
        assertEquals(snapshot.exitTimer, get(manager, "exitTimer"));
        assertEquals(snapshot.exitFadeStarted, get(manager, "exitFadeStarted"));
        assertEquals(snapshot.exitFadeTimer, get(manager, "exitFadeTimer"));
        assertEquals(snapshot.heldButtons, get(manager, "heldButtons"));
        assertEquals(snapshot.pressedButtons, get(manager, "pressedButtons"));
        assertEquals(snapshot.bgAnimState, get(manager, "bgAnimState"));
        assertEquals(snapshot.bgUsingPlane6, get(manager, "bgUsingPlane6"));
        assertEquals(snapshot.fgAnimPlaneIndex, get(manager, "fgAnimPlaneIndex"));
        assertEquals(snapshot.fgYScroll, get(manager, "fgYScroll"));
        assertEquals(snapshot.bgYScroll, get(manager, "bgYScroll"));
        assertEquals(snapshot.bgExtraScrollX, get(manager, "bgExtraScrollX"));

        assertEquals(0x08, get(manager, "wallRotFrame"));
        assertArrayEquals(snapshot.layout, (byte[]) get(manager, "layout"));
        assertMatrixEquals(snapshot.ssAnimBuffer, (int[][]) get(manager, "ssAnimBuffer"));
        assertArrayEquals(snapshot.ssAnimGlassFinalBlock, (int[]) get(manager, "ssAnimGlassFinalBlock"));
        assertArrayEquals(snapshot.bgSineBuffer, (int[]) get(manager, "bgSineBuffer"));
        assertArrayEquals(snapshot.bgBandBuffer, (int[]) get(manager, "bgBandBuffer"));
        assertPalettesEqual(snapshot.ssPalettes, (Palette[]) get(manager, "ssPalettes"));
    }

    private static void assertSnapshotPayloadsAreEqualButNotSame(Sonic1SpecialStageSnapshot expected,
                                                                 Sonic1SpecialStageSnapshot actual) {
        assertArrayEquals(expected.layout, actual.layout);
        assertNotSame(expected.layout, actual.layout);
        assertMatrixEquals(expected.ssAnimBuffer, actual.ssAnimBuffer);
        assertNotSame(expected.ssAnimBuffer, actual.ssAnimBuffer);
        for (int i = 0; i < expected.ssAnimBuffer.length; i++) {
            assertNotSame(expected.ssAnimBuffer[i], actual.ssAnimBuffer[i]);
        }
        assertArrayEquals(expected.ssAnimGlassFinalBlock, actual.ssAnimGlassFinalBlock);
        assertNotSame(expected.ssAnimGlassFinalBlock, actual.ssAnimGlassFinalBlock);
        assertArrayEquals(expected.bgSineBuffer, actual.bgSineBuffer);
        assertNotSame(expected.bgSineBuffer, actual.bgSineBuffer);
        assertArrayEquals(expected.bgBandBuffer, actual.bgBandBuffer);
        assertNotSame(expected.bgBandBuffer, actual.bgBandBuffer);
        assertPalettesEqual(expected.ssPalettes, actual.ssPalettes);
        assertNotSame(expected.ssPalettes, actual.ssPalettes);
        for (int i = 0; i < expected.ssPalettes.length; i++) {
            assertNotSame(expected.ssPalettes[i], actual.ssPalettes[i]);
            assertNotSame(expected.ssPalettes[i].getColor(0), actual.ssPalettes[i].getColor(0));
        }
    }

    private static void assertRestoredPayloadsAreNotSnapshotAliases(Sonic1SpecialStageManager manager,
                                                                    Sonic1SpecialStageSnapshot snapshot) throws Exception {
        int[][] liveAnimBuffer = (int[][]) get(manager, "ssAnimBuffer");
        Palette[] livePalettes = (Palette[]) get(manager, "ssPalettes");
        assertNotSame(snapshot.layout, get(manager, "layout"));
        assertNotSame(snapshot.ssAnimBuffer, liveAnimBuffer);
        for (int i = 0; i < snapshot.ssAnimBuffer.length; i++) {
            assertNotSame(snapshot.ssAnimBuffer[i], liveAnimBuffer[i]);
        }
        assertNotSame(snapshot.ssAnimGlassFinalBlock, get(manager, "ssAnimGlassFinalBlock"));
        assertNotSame(snapshot.bgSineBuffer, get(manager, "bgSineBuffer"));
        assertNotSame(snapshot.bgBandBuffer, get(manager, "bgBandBuffer"));
        assertNotSame(snapshot.ssPalettes, livePalettes);
        for (int i = 0; i < snapshot.ssPalettes.length; i++) {
            assertNotSame(snapshot.ssPalettes[i], livePalettes[i]);
            assertNotSame(snapshot.ssPalettes[i].getColor(0), livePalettes[i].getColor(0));
        }
    }

    private static void assertExpectedBandHScroll(Sonic1SpecialStageManager manager) throws Exception {
        int[] bgHScrollData = (int[]) get(manager, "bgHScrollData");
        assertNotNull(bgHScrollData);
        assertArrayEquals(expectedBandHScroll(5, new int[] {101, 1, 202, 2, 303, 3, 404, 4, 505, 5, 606, 6, 707, 7}),
                bgHScrollData);
    }

    private static int[] expectedBandHScroll(int bgYScroll, int[] bgBandBuffer) {
        int[] expected = new int[224];
        int scanline = (-bgYScroll) & 0xFF;
        int bandCount = EXPECTED_SCROLL_BAND_WIDTHS[0] + 1;
        for (int band = 0; band < bandCount; band++) {
            int scroll = bgBandBuffer[band * 2];
            int height = EXPECTED_SCROLL_BAND_WIDTHS[band + 1];
            for (int j = 0; j < height; j++) {
                int idx = scanline & 0xFF;
                if (idx < 224) {
                    expected[idx] = scroll;
                }
                scanline++;
            }
        }
        return expected;
    }

    private static Palette[] palettes() {
        Palette[] palettes = new Palette[4];
        for (int line = 0; line < palettes.length; line++) {
            palettes[line] = new Palette();
            for (int color = 0; color < Palette.PALETTE_SIZE; color++) {
                palettes[line].setColor(color, new Palette.Color(
                        (byte) (line * 30 + color),
                        (byte) (line * 30 + color + 1),
                        (byte) (line * 30 + color + 2)));
            }
        }
        return palettes;
    }

    private static int[] filledArray(int size, int value) {
        int[] values = new int[size];
        for (int i = 0; i < values.length; i++) {
            values[i] = value;
        }
        return values;
    }

    private static void assertMatrixEquals(int[][] expected, int[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i], actual[i]);
        }
    }

    private static void assertPalettesEqual(Palette[] expected, Palette[] actual) {
        assertEquals(expected.length, actual.length);
        for (int line = 0; line < expected.length; line++) {
            for (int color = 0; color < Palette.PALETTE_SIZE; color++) {
                Palette.Color expectedColor = expected[line].getColor(color);
                Palette.Color actualColor = actual[line].getColor(color);
                assertEquals(expectedColor.r, actualColor.r);
                assertEquals(expectedColor.g, actualColor.g);
                assertEquals(expectedColor.b, actualColor.b);
            }
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
