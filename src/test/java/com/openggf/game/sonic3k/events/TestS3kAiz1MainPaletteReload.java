package com.openggf.game.sonic3k.events;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AIZ1_Resize routine 0, loc_1C46E/loc_1C4A6 (sonic3k.asm:38913-38935): once Camera_X_pos
 * reaches $1308 it runs LoadPalette_Immediate PalID_AIZ (Pal_AIZ to Normal_palette_line_2,
 * $60 bytes). For Player_mode 3 it then writes word_4FAE4 (104562-104565) to
 * Normal_palette_line_4+$10. Knuckles' AIZ1_ScreenInit also writes word_4FAE4 to
 * Target_palette_line_4+$10 (Adjust_AIZ1Chunks2, 104337-104338, 104498-104502).
 * Expected words come from the ROM's PalPoint entry and word_4FAE4; observation is the
 * level palette only.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAiz1MainPaletteReload {
    private static final int PAL_ID_AIZ = 0x2A;
    private static final int WORD_4FAE4 = 0x4FAE4;
    private static final int BOUNDARY_X = 0x1308;

    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SonicConfigurationService.getInstance().resetToDefaults();
        TestEnvironment.resetAll();
    }

    @Test
    void knucklesReloadsTheMainPaletteWithItsLineFourColoursAtTheCameraBoundary() throws Exception {
        HeadlessTestFixture fixture = knucklesFromHisAiz1Entry();
        int[] expected = expectedLines1To3(true);
        int[] patch = Arrays.copyOfRange(expected, 32 + 8, 32 + 12);
        int[] before = null;
        for (int frame = 0; frame < 200 && (GameServices.camera().getX() & 0xFFFF) < BOUNDARY_X; frame++) {
            before = lines1To3();
            fixture.stepFrameFromRecording();
        }
        assertTrue((GameServices.camera().getX() & 0xFFFF) >= BOUNDARY_X, "real input reaches the $1308 boundary");
        assertNotNull(before);
        assertArrayEquals(patch, Arrays.copyOfRange(before, 32 + 8, 32 + 12),
                "Adjust_AIZ1Chunks2's colours are in place before the boundary");
        assertFalse(Arrays.equals(expected, before), "AnPal has cycled some of these colours before the boundary");
        assertArrayEquals(expected, lines1To3(), "the boundary frame reloads Pal_AIZ, then word_4FAE4");
    }

    @Test
    void sonicReloadsTheMainPaletteWithoutKnucklesColours() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro()
                .startPosition((short) 0x1300, (short) 0x0400).startPositionIsCentre().build();
        int[] expected = expectedLines1To3(false);
        GameServices.camera().setX((short) 0x1200);
        reachBoundaryByCamera(fixture);
        assertArrayEquals(Arrays.copyOfRange(expected, 32 + 8, 32 + 12), Arrays.copyOfRange(lines1To3(), 32 + 8, 32 + 12),
                "Player_mode 0 keeps Pal_AIZ's line-4 colours 8-11");
    }

    @Test
    void reloadRestoresTwiceWithForwardReplayAndRunsAgainAfterAFreshLoad() throws Exception {
        HeadlessTestFixture fixture = knucklesFromHisAiz1Entry();
        for (int frame = 0; frame < 200 && (GameServices.camera().getX() & 0xFFFF) < BOUNDARY_X - 4; frame++) {
            fixture.stepFrameFromRecording();
        }
        assertTrue((GameServices.camera().getX() & 0xFFFF) < BOUNDARY_X);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        List<int[]> forward = forwardAcrossBoundary(fixture);
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            List<int[]> replay = forwardAcrossBoundary(fixture);
            for (int i = 0; i < forward.size(); i++) {
                assertArrayEquals(forward.get(i), replay.get(i), "cycle " + cycle + " frame " + i);
            }
        }
        int[] expected = expectedLines1To3(true);
        assertTrue(forward.stream().anyMatch(row -> Arrays.equals(row, expected)), "the window contains the reload");

        GameServices.level().loadZoneAndAct(0, 0);
        GameServices.level().consumePendingInitialProcessSpritesPass();
        GameServices.camera().setX((short) 0x1200);
        reachBoundaryByCamera(fixture);
        assertArrayEquals(expected, lines1To3(), "a fresh load runs routine 0's reload again");
    }

    /** Ordinary neutral frames with the camera driven across $1308 through the player. */
    private static List<int[]> forwardAcrossBoundary(HeadlessTestFixture fixture) {
        List<int[]> rows = new ArrayList<>();
        var player = fixture.sprite();
        for (int i = 0; i < 12; i++) {
            player.setCentreX((short) (player.getCentreX() + 2));
            fixture.stepIdleFrames(1);
            rows.add(lines1To3());
        }
        return rows;
    }

    private static void reachBoundaryByCamera(HeadlessTestFixture fixture) {
        var player = fixture.sprite();
        for (int i = 0; i < 200 && (GameServices.camera().getX() & 0xFFFF) < BOUNDARY_X; i++) {
            player.setCentreX((short) (player.getCentreX() + 4));
            fixture.stepIdleFrames(1);
        }
        assertTrue((GameServices.camera().getX() & 0xFFFF) >= BOUNDARY_X);
    }

    private static HeadlessTestFixture knucklesFromHisAiz1Entry() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        Path bk2 = Path.of("src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/"
                + "s3k-knuckles-complete-superemeralds.bk2").toAbsolutePath();
        return HeadlessTestFixture.builder().withZoneAndAct(0, 0).withRecording(bk2).withRecordingStartFrame(810).build();
    }

    /** Pal_AIZ words for lines 2-4 (engine lines 1-3), with word_4FAE4 at line 4 colours 8-11 for Knuckles. */
    private static int[] expectedLines1To3(boolean knuckles) throws Exception {
        RomByteReader rom = RomByteReader.fromRom(GameServices.rom().getRom());
        int entry = Sonic3kConstants.PAL_POINTERS_ADDR + PAL_ID_AIZ * Sonic3kConstants.PAL_POINTER_ENTRY_SIZE;
        int source = rom.readU32BE(entry) & 0xFFFFFF;
        assertEquals(0xFC20, rom.readU16BE(entry + 4) & 0xFFFF, "PalID_AIZ targets Normal_palette_line_2");
        byte[] data = rom.slice(source, 0x60);
        int[] words = new int[48];
        for (int i = 0; i < 48; i++) words[i] = ((data[2 * i] & 0xFF) << 8) | (data[2 * i + 1] & 0xFF);
        if (knuckles) {
            byte[] patch = rom.slice(WORD_4FAE4, 8);
            for (int i = 0; i < 4; i++) words[32 + 8 + i] = ((patch[2 * i] & 0xFF) << 8) | (patch[2 * i + 1] & 0xFF);
        }
        return words;
    }

    private static int[] lines1To3() {
        int[] words = new int[48];
        for (int line = 1; line <= 3; line++) {
            for (int c = 0; c < 16; c++) {
                Palette.Color color = GameServices.level().getCurrentLevel().getPalette(line).getColor(c);
                int r = ((color.r & 0xFF) * 7 + 127) / 255, g = ((color.g & 0xFF) * 7 + 127) / 255;
                int b = ((color.b & 0xFF) * 7 + 127) / 255;
                words[(line - 1) * 16 + c] = (b << 9) | (g << 5) | (r << 1);
            }
        }
        return words;
    }
}
