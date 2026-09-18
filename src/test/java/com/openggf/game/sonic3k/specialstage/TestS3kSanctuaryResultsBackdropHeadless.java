package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.HPZMasterEmeraldObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSuperEmeraldObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSuperEmeraldReturnEffectObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production-level coverage for the Super Emerald results reveal: the $1701 sanctuary rebuilt
 * behind {@link S3kSpecialStageResultsScreen} ({@code SpecialStage_Results},
 * sonic3k.asm:63121-63201) and driven by routines $E-$12 of {@code Obj_SpecialStage_Results}.
 *
 * <p>Updates are 1-based loop iterations; with 30 rings the tally ends and routine $E begins
 * on update 862 (see {@code TestS3kSpecialStageResultsReveal}).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSanctuaryResultsBackdropHeadless {
    private static final int E = 862;

    private static HeadlessTestFixture sanctuary(List<Integer> emeraldStates) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(0x17, 1).build();
        GameServices.gameState().restoreS3kEmeraldProgress(emeraldStates, true);
        return fixture;
    }

    private static void runTo(S3kSpecialStageResultsScreen screen, int from, int to) {
        for (int update = from; update <= to; update++) {
            screen.update(update, null);
        }
    }

    private static HPZSuperEmeraldObjectInstance pedestal(ObjectManager objects, int subtype) {
        return objects.activeObjectsOfType(HPZSuperEmeraldObjectInstance.class).stream()
                .filter(pedestal -> pedestal.getSpawn().subtype() == subtype)
                .findFirst().orElseThrow();
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    @Test
    void clearedStageRebuildsTheSanctuaryAndClosesTheStarsOnTheNewEmerald() throws Exception {
        HeadlessTestFixture fixture = sanctuary(List.of(3, 3, 2, 2, 2, 2, 2));
        List<RewindBoundary> boundaries = new ArrayList<>();
        fixture.gameplayMode().setRewindBoundaryReporter(boundaries::add);

        var screen = new S3kSpecialStageResultsScreen(30, true, 1, 7,
                PlayerCharacter.SONIC_ALONE, true, true);

        assertTrue(screen.drawsLevelBackdrop(), "SK_special_stage_flag rebuilds $1701");
        assertEquals(List.of(RewindBoundary.LEVEL_LOAD), boundaries,
                "the rebuild is a level load: rewind history before the results is discarded");
        assertEquals(0x1540, GameServices.camera().getX() & 0xFFFF, "word_2E398[1]");
        assertEquals(0x240, GameServices.camera().getY() & 0xFFFF);
        ObjectManager objects = GameServices.level().getObjectManager();
        HPZSSEntryControlObjectInstance controller =
                objects.activeObjectsOfType(HPZSSEntryControlObjectInstance.class).get(0);
        assertTrue(controller.isHostingSpecialStageResults());

        screen.update(1, null);
        assertEquals(7, objects.activeObjectsOfType(HPZSuperEmeraldObjectInstance.class).size(),
                "loc_909EA allocates every pedestal when HPZ_special_stage_completed is set");
        assertEquals(2, objects.activeObjectsOfType(HPZMasterEmeraldObjectInstance.class).size(),
                "HPZMini_Sprites places a $B0 and loc_909CC allocates another");
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY, pedestal(objects, 1).display(),
                "_unkFAC0 = stage|$80 greys the cleared pedestal");
        assertEquals(HPZSuperEmeraldObjectInstance.Display.COLORED, pedestal(objects, 0).display());

        var rom = GameServices.rom().getRom();
        byte[] hpzMain = rom.readBytes(Sonic3kConstants.HPZ_MAIN_PALETTE_ADDR, 0x60);
        byte[] cutsceneKnux = rom.readBytes(Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR, 0x20);
        S3kSanctuaryResultsPalette palette = screen.sanctuaryPaletteForTest();
        assertNotNull(palette);
        for (int color = 0; color < 16; color++) {
            assertEquals(0x0CCC, palette.normalColor(2, color), "loc_2E150 wash, colour " + color);
            assertEquals(word(hpzMain, 0x20 + color * 2), palette.targetColor(2, color),
                    "loc_909B0 retargets line 3 to Pal_HPZ+$20");
            assertEquals(word(cutsceneKnux, color * 2), palette.targetColor(1, color),
                    "loc_90998 retargets line 2 to Pal_CutsceneKnux");
        }
        assertEquals(0x06A0, palette.targetColor(3, 1));
        assertEquals(0x0660, palette.targetColor(3, 2));

        runTo(screen, 2, 360);
        assertEquals(0x0CCC, palette.normalColor(2, 1));
        assertEquals(0, palette.fadeTimer());
        int[][] start = new int[4][16];
        runTo(screen, 361, 361);
        assertEquals(0x15, palette.fadeTimer(), "loc_2E410 arms $16 calls; the first ran");
        assertEquals(0x0EEE, palette.normalColor(2, 1), "Normal_palette+$42");
        assertEquals(0x0EEE, palette.normalColor(3, 15), "Normal_palette+$7E");
        for (int line = 0; line < 4; line++) {
            for (int color = 0; color < 16; color++) {
                start[line][color] = palette.normalColor(line, color);
            }
        }
        runTo(screen, 362, 382);
        assertEquals(0, palette.fadeTimer());
        for (int line = 0; line < 4; line++) {
            for (int color = 0; color < 16; color++) {
                int expected = start[line][color];
                // 22 calls with Pal_fade_delay2 entering at 2 step on calls 3,6,...,21.
                for (int step = 0; step < 7; step++) {
                    expected = S3kSanctuaryResultsPalette.decColor2(
                            expected, palette.targetColor(line, color));
                }
                assertEquals(expected, palette.normalColor(line, color),
                        "Pal_FromWhite line " + line + " colour " + color);
            }
        }

        runTo(screen, 383, E + 284);
        assertEquals(0x320, GameServices.camera().getY() & 0xFFFF);
        assertEquals(1, objects.activeObjectsOfType(
                HPZSuperEmeraldReturnEffectObjectInstance.class).size(),
                "loc_2E6EA allocates the converging stars");
        runTo(screen, E + 285, E + 508);
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY, pedestal(objects, 1).display());
        runTo(screen, E + 509, E + 509);
        assertEquals(HPZSuperEmeraldObjectInstance.Display.COLORED, pedestal(objects, 1).display(),
                "loc_2EDAE clears _unkFAC0 on the borrow frame");
        runTo(screen, E + 510, E + 569);
        assertFalse(screen.isComplete());
        runTo(screen, E + 570, E + 570);
        assertTrue(screen.isComplete());
        assertEquals(0x320, GameServices.camera().getY() & 0xFFFF);

        // Level-select return without a saved origin: the hub is rebuilt and does not replay.
        GameServices.level().markSanctuaryReentry(1, false);
        GameServices.level().loadCurrentLevel();
        fixture.stepIdleFrames(3);
        ObjectManager hubObjects = GameServices.level().getObjectManager();
        assertTrue(hubObjects.activeObjectsOfType(
                HPZSuperEmeraldReturnEffectObjectInstance.class).isEmpty());
        assertEquals(HPZSuperEmeraldObjectInstance.Display.COLORED,
                pedestal(hubObjects, 1).display());
        assertFalse(hubObjects.activeObjectsOfType(HPZSSEntryControlObjectInstance.class).get(0)
                .isHostingSpecialStageResults());
    }

    @Test
    void sevenSuperEmeraldsReleaseTheMasterEmeraldWhenTheHyperMessageArrives() {
        sanctuary(List.of(3, 3, 3, 3, 3, 3, 3));

        var screen = new S3kSpecialStageResultsScreen(30, true, 0, 7,
                PlayerCharacter.KNUCKLES, true, true);
        ObjectManager objects = GameServices.level().getObjectManager();
        HPZSSEntryControlObjectInstance controller =
                objects.activeObjectsOfType(HPZSSEntryControlObjectInstance.class).get(0);

        // Stage 0 starts at $15A0, so loc_2E77E spawns the expanding stars at once.
        int s = E + 539;
        runTo(screen, 1, s);
        assertTrue(objects.activeObjectsOfType(HPZSuperEmeraldReturnEffectObjectInstance.class)
                .stream().anyMatch(HPZSuperEmeraldReturnEffectObjectInstance::isExpanding));
        runTo(screen, s + 1, s + 175);
        assertTrue(controller.resultsReturnTransformationActive(),
                "_unkFAC1 stays set while the message slides in");
        runTo(screen, s + 176, s + 176);
        assertFalse(controller.resultsReturnTransformationActive(),
                "loc_2E9D8 clears _unkFAC1");
        S3kSanctuaryResultsPalette palette = screen.sanctuaryPaletteForTest();
        assertEquals(0x06A0, palette.normalColor(3, 1));
        // Native complete-run ss_14 (movie frames 295000-295020): the Master Emerald's
        // off_914CE rotation writes $8C0/$680 on the pass after the message lands, then
        // $AC0/$680 ten passes later and $CE0/$880 ten after that.
        runTo(screen, s + 177, s + 177);
        assertEquals(0x08C0, palette.normalColor(3, 1));
        assertEquals(0x0680, palette.normalColor(3, 2));
        runTo(screen, s + 178, s + 187);
        assertEquals(0x0AC0, palette.normalColor(3, 1));
        runTo(screen, s + 188, s + 197);
        assertEquals(0x0CE0, palette.normalColor(3, 1));
        assertEquals(0x0880, palette.normalColor(3, 2));
        runTo(screen, s + 198, s + 482);
        assertTrue(screen.isComplete());
    }

    @Test
    void failedStageKeepsTheWashedBackdropAndNeverPans() {
        sanctuary(List.of(3, 2, 2, 2, 2, 2, 2));

        var screen = new S3kSpecialStageResultsScreen(30, false, 1, 7,
                PlayerCharacter.SONIC_AND_TAILS, true, true);
        ObjectManager objects = GameServices.level().getObjectManager();

        assertTrue(screen.drawsLevelBackdrop(), "the rebuild does not depend on success");
        for (int update = 1; update <= 2000 && !screen.isComplete(); update++) {
            screen.update(update, null);
        }
        assertTrue(screen.isComplete());
        assertEquals(0x240, GameServices.camera().getY() & 0xFFFF);
        assertEquals(0x0CCC, screen.sanctuaryPaletteForTest().normalColor(2, 4),
                "loc_2E410 arms the fade only when Special_stage_spheres_left is zero");
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY, pedestal(objects, 1).display(),
                "a failed stage leaves state 2 grey without _unkFAC0");
        assertTrue(objects.activeObjectsOfType(HPZSuperEmeraldReturnEffectObjectInstance.class)
                .isEmpty());
    }
}
