package com.openggf.game.sonic1.scroll;

import com.openggf.game.CheckpointBackgroundScrollAccess;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.internal.CheckpointBackgroundScroll;
import com.openggf.graphics.RgbaImage;
import com.openggf.level.ParallaxCheckpointAccess;
import com.openggf.level.ParallaxManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.game.sonic1.objects.Sonic1LamppostObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1Sbz3ScrollAlias {
    @Test
    void productionFullLoadRendersTheRomLabyrinthScrollAtTheScrapBrainIdentity() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic1RomAvailable().toPath(), 5, 2, settings);
            assertSbz3Identity();
            var rom = GameServices.rom().getRom();
            // Native v_zone dispatch selects LZ ($0108 + $62AE), not SBZ2.
            assertArrayEquals(HexFormat.of().parseHex("000e0108029a03920462050e000e"),
                    rom.readBytes(0x62AE, 14));
            assertArrayEquals(HexFormat.of().parseHex("3838f73a48c4ef843a38f73c48c5ef85610006fc"),
                    rom.readBytes(0x63B6, 20));
            assertArrayEquals(HexFormat.of().parseHex("e28031c0f70c4e75"), rom.readBytes(0x61FE, 8));

            var camera = GameServices.camera();
            var parallax = GameServices.parallax();
            int entryX = camera.getX();
            int entryY = camera.getY();
            camera.setX((short) (entryX + 32));
            camera.setY((short) (entryY + 16));
            GameServices.level().recomputeParallaxAfterRewindRestore();
            camera.captureRenderCopy();
            int[] actual = parallax.getHScroll().clone();
            short actualY = parallax.getVscrollFactorBG();

            SwScrlLz nativeRoutine = new SwScrlLz();
            nativeRoutine.init(entryX, entryY);
            int[] expected = new int[224];
            nativeRoutine.update(expected, camera.getX(), camera.getY(), 1, 3);
            SwScrlSbz wrongRoutine = new SwScrlSbz();
            wrongRoutine.init(2, entryX, entryY);
            int[] wrong = new int[224];
            wrongRoutine.update(wrong, camera.getX(), camera.getY(), 1, 2);

            RgbaImage actualPixels = session.render(false);
            RgbaImage expectedPixels = renderScrollOutput(session, expected, nativeRoutine.getVscrollFactorBG());
            RgbaImage wrongPixels = renderScrollOutput(session, wrong, wrongRoutine.getVscrollFactorBG());
            int nativePixelDifferences = differences(actualPixels, expectedPixels);
            int wrongPixelDifferences = differences(expectedPixels, wrongPixels);
            System.out.printf("SBZ3 full-load probe: entry=(%04X,%04X) camera=(%04X,%04X) BG actual=%04X/%04X native=%04X/%04X; pixels actual/native=%d native/SBZ2=%d%n",
                    entryX, entryY, camera.getX(), camera.getY(), (short) -actual[0] & 0xFFFF,
                    actualY & 0xFFFF, (short) -expected[0] & 0xFFFF,
                    nativeRoutine.getVscrollFactorBG() & 0xFFFF, nativePixelDifferences, wrongPixelDifferences);
            assertTrue(wrongPixelDifferences > 0, "rendered terrain must expose the background scroll difference");
            assertArrayEquals(expected, actual, "SBZ3 uses Deform_LZ's half-speed X on all 224 lines");
            assertEquals(nativeRoutine.getVscrollFactorBG(), actualY, "BgScroll_LZ and half-speed Y");
            assertEquals(0, nativePixelDifferences, "production renderer receives native LZ output");
            assertSbz3Identity();
        }
    }

    @Test
    void productionProgressionCheckpointAndFractionalReplayKeepTheAlias() throws Exception {
        SharedLevel shared = SharedLevel.load(SonicGame.SONIC_1, 5, 1);
        try {
            var level = GameServices.level();
            level.advanceToNextLevel();
            level.skipPendingInitialTitleCardPresentation();
            assertSbz3Identity();
            var camera = GameServices.camera();
            var parallax = GameServices.parallax();
            int entryX = camera.getX();
            int entryY = camera.getY();
            camera.setX((short) (entryX + 32));
            camera.setY((short) (entryY + 16));
            level.recomputeParallaxAfterRewindRestore();
            CheckpointBackgroundScroll nativeBank = ParallaxCheckpointAccess.capture(parallax, 5);
            assertEquals(entryX + 16, nativeBank.bgX(), "actual SBZ2 -> SBZ3 full-load selects LZ");
            assertEquals((entryY >> 1) + 8, nativeBank.bgY());
            assertEquals(entryX, nativeBank.bg2X(), "LZ leaves the secondary fresh-entry word alone");
            assertEquals(entryX, nativeBank.bg3X(), "LZ leaves the tertiary fresh-entry word alone");

            var player = GameServices.sprites().getMainPlayable();
            player.setCentreX((short) 0xC00);
            player.setCentreY((short) 0x200);
            var lamp = new Sonic1LamppostObjectInstance(new ObjectSpawn(0xC00, 0x200, 0x79, 1, 0, false, 0));
            lamp.setServices(level.getObjectManager().getObjectServices());
            lamp.update(0, player);
            var checkpoint = (CheckpointState) level.getCheckpointState();
            assertTrue(checkpoint.isActive());
            assertEquals(nativeBank, CheckpointBackgroundScrollAccess.saved(checkpoint));
            level.respawnPlayer();
            level.recomputeParallaxAfterRewindRestore();
            assertSbz3Identity();
            CheckpointBackgroundScroll restored = ParallaxCheckpointAccess.capture(parallax, 5);
            assertEquals(nativeBank.bgX(), restored.bgX(), "Lamp_LoadInfo preserves BG1 X");
            assertEquals(camera.getY() >> 1, restored.bgY(), "BgScroll_LZ recomputes Y");
            assertEquals(nativeBank.bg2X(), restored.bg2X());
            assertEquals(nativeBank.bg3X(), restored.bg3X());

            camera.setX((short) (camera.getX() + 3));
            camera.setY((short) (camera.getY() + 5));
            level.recomputeParallaxAfterRewindRestore();
            int savedX = camera.getX();
            int savedY = camera.getY();
            var snapshot = parallax.capture();
            camera.setX((short) (savedX + 1));
            camera.setY((short) (savedY + 1));
            level.recomputeParallaxAfterRewindRestore();
            int[] expected = parallax.getHScroll().clone();
            short expectedY = parallax.getVscrollFactorBG();
            assertEquals(nativeBank.bgX() + 2, ParallaxCheckpointAccess.capture(parallax, 5).bgX(),
                    "two odd movements retain and carry the native half pixel");
            camera.setX((short) (savedX + 99));
            camera.setY((short) (savedY + 33));
            level.recomputeParallaxAfterRewindRestore();
            parallax.restore(snapshot);
            camera.setX((short) (savedX + 1));
            camera.setY((short) (savedY + 1));
            level.recomputeParallaxAfterRewindRestore();
            assertArrayEquals(expected, parallax.getHScroll());
            assertEquals(expectedY, parallax.getVscrollFactorBG());

            level.loadZoneAndAct(5, 1);
            int sbz2X = camera.getX();
            int sbz2Y = camera.getY();
            camera.setX((short) (sbz2X + 32));
            camera.setY((short) (sbz2Y + 16));
            level.recomputeParallaxAfterRewindRestore();
            CheckpointBackgroundScroll ordinary = ParallaxCheckpointAccess.capture(parallax, 5);
            assertEquals(sbz2X + 8, ordinary.bgX(), "SBZ2 still scrolls X at quarter speed");
            assertEquals(((sbz2Y & 0x7F8) >> 3) + 3, ordinary.bgY(), "SBZ2 still scrolls Y at eighth speed");
        } finally {
            shared.dispose();
        }
    }

    private static void assertSbz3Identity() {
        assertEquals(5, GameServices.level().getCurrentZone());
        assertEquals(2, GameServices.level().getCurrentAct());
        assertEquals(1, GameServices.level().getCurrentLevel().getZoneIndex(), "terrain remains ROM LZ act 4");
    }

    /** Change only derived GPU scroll inputs for the independent render oracle. */
    private static RgbaImage renderScrollOutput(GameplayCaptureSession session, int[] words, short y) throws Exception {
        var parallax = GameServices.parallax();
        int[] previous = parallax.getHScroll().clone();
        short previousY = parallax.getVscrollFactorBG();
        Field bgY = ParallaxManager.class.getDeclaredField("vscrollFactorBG");
        bgY.setAccessible(true);
        try {
            System.arraycopy(words, 0, parallax.getHScroll(), 0, words.length);
            bgY.setShort(parallax, y);
            return session.render(false);
        } finally {
            System.arraycopy(previous, 0, parallax.getHScroll(), 0, previous.length);
            bgY.setShort(parallax, previousY);
        }
    }

    private static int differences(RgbaImage first, RgbaImage second) {
        int count = 0;
        for (int i = 0; i < first.pixels().length; i++) if (first.pixels()[i] != second.pixels()[i]) count++;
        return count;
    }
}
