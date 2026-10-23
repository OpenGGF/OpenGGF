package com.openggf.tests;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.ResultsScreen;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageResultsScreen;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.GameLoopRewindTestAccess.owner;
import static com.openggf.tests.TestS3kSanctuaryOriginReturnLifecycle.requireHistory;
import static com.openggf.tests.TestS3kSanctuaryOriginReturnLifecycle.assertRetiredHistory;

/**
 * Actual sanctuary result/return owners, originating in the S3K parity task.
 * END/DEL are declared outcome stimuli, not Blue Sphere winning-input evidence.
 * Callers use a real sanctuary pedestal, with either actual MHZ-ring Saved2
 * entry or an explicitly declared direct developer scene without an origin.
 */
final class SanctuaryResultsLifecycleProbe {
    enum Outcome {
        FAILURE, SUCCESS, FINAL_SEVEN;

        List<Integer> initialProgress() {
            return this == FINAL_SEVEN ? List.of(3, 3, 3, 3, 3, 3, 2)
                    : List.of(2, 2, 2, 2, 2, 2, 2);
        }
    }

    private SanctuaryResultsLifecycleProbe() { }

    static int completeAndReturn(GameLoop loop, Sonic3kSpecialStageProvider provider,
            HeadlessTestFixture fixture, CanonicalEntryMatrixSupport.Row row, Outcome outcome,
            int lives, Object saved) throws Exception {
        assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
        assertSame(provider, loop.getActiveSpecialStageProvider());
        assertEquals(6, provider.getCurrentStage());
        assertTrue(provider.isEntryPresentationReady());
        assertFalse(GameServices.fade().isActive());
        assertFalse(GameServices.fade().hasPendingCompletion());
        assertEquals(saved, GameServices.level().getBigRingReturn());
        var stageController = requireHistory(loop, "the actual incoming stage owns live history");
        int stageHistory = stageController.currentFrame();
        assertTrue(stageHistory > 10, "actual incoming stage built a live timeline");
        var stageObjects = GameServices.level().getObjectManager();

        if (outcome == Outcome.FAILURE) {
            Object shortcuts = field(loop, "debugShortcuts");
            call(shortcuts, "debugFailSpecialStage");
        } else {
            loop.debugCompleteSpecialStageWithEmerald();
        }
        for (int frame = 0; frame < 300 && loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE_RESULTS;
                frame++) advance(loop, lives);
        assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode(), "real results transition");
        assertEquals(6, loop.recordedSpecialStageIdentity(GameMode.SPECIAL_STAGE_RESULTS),
                "results preserve the selected stage identity after provider reset");
        assertResultsRewindGate(loop);
        ResultsScreen preparation = loop.getResultsScreen();
        assertNotNull(preparation);
        // Sparse drawing uses the exact Engine presentation binding; no gameplay state is supplied.
        var bind = Engine.class.getDeclaredMethod("applyViewportWidth", ResultsScreen.class, int.class);
        bind.setAccessible(true);
        int width = row.aspect().pixelWidth();
        bind.invoke(null, preparation, width);
        S3kSpecialStageResultsScreen screen = null;
        for (int frame = 0; frame < 500 && screen == null; frame++) {
            Object candidate = field(preparation, "screen");
            if (candidate instanceof S3kSpecialStageResultsScreen ready) screen = ready;
            else advance(loop, lives);
        }
        assertNotNull(screen, "actual queued Nemesis/KosM jobs install the real results owner");
        assertNotSame(stageObjects, GameServices.level().getObjectManager(), "results rebuild the real sanctuary");
        assertEquals(0x17, GameServices.level().getCurrentZone());
        assertEquals(1, GameServices.level().getCurrentAct());
        assertEquals(saved, GameServices.level().getBigRingReturn(), "backdrop rebuild preserves Saved2");
        assertNull(owner(loop).getRewindController(), "results do not install a gameplay timeline");
        assertRetiredHistory(loop, stageController,
                "results backdrop load retires the actual outgoing stage history");
        assertEquals((width - 320) / 2, (int) field(screen, "viewportXOffset"));
        assertEquals(outcome != Outcome.FAILURE, (boolean) call(screen, "charNameVisibleForTest"));
        assertEquals(outcome == Outcome.FAILURE, (boolean) call(screen, "failMessageVisibleForTest"));
        assertEquals(0x30, (int) call(screen, "emeraldWordFrameForTest"), "native SUPER EMERALD mapping");
        assertEquals(outcome == Outcome.FINAL_SEVEN ? 7 : outcome == Outcome.SUCCESS ? 1 : 0,
                GameServices.gameState().getCollectedSuperEmeraldIndices().size());
        assertResultPaletteSetup(screen, row);
        assertFormWordArchive(screen, row, outcome);

        boolean sawPan = false;
        boolean sawStars = false;
        boolean sawHyperMessage = false;
        boolean sawControllerTargets = false;
        var backdrop = GameServices.level().getObjectManager();
        for (int frame = 0; frame < 2600 && loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE_RESULTS;
                frame++) {
            assertSame(preparation, loop.getResultsScreen());
            assertEquals(saved, GameServices.level().getBigRingReturn());
            assertSame(backdrop, GameServices.level().getObjectManager());
            assertEquals(6, loop.recordedSpecialStageIdentity(GameMode.SPECIAL_STAGE_RESULTS));
            assertResultsRewindGate(loop);
            int y = (int) call(screen, "sanctuaryCameraYForTest");
            if (y > 0x240) sawPan = true;
            if (call(screen, "convergingStarsForTest") != null) {
                if (!sawStars) assertReleasedSuccessPalette(screen, row);
                sawStars = true;
            }
            if ((boolean) call(screen, "hyperMessageArrivedForTest")) {
                sawHyperMessage = true;
                int adj = row.main().equals("knuckles") ? 3 : row.main().equals("tails") ? 2 : 0;
                assertEquals(List.of(0x2C, 0x13 + adj, 0x2D, 0x35, 0x12, 0x13 + adj),
                        call(screen, "revealFramesForTest"), "native six-slot final form message");
            }
            if ((boolean) field(screen, "sanctuaryControllerTargetsApplied")) {
                if (!sawControllerTargets) assertControllerTargets(screen, row);
                sawControllerTargets = true;
            }
            if (outcome == Outcome.FAILURE) {
                assertEquals(0x240, y, "failed outcome never starts the success pan");
                assertNull(call(screen, "convergingStarsForTest"));
                assertFalse((boolean) call(screen, "hyperMessageArrivedForTest"));
                Object palette = call(screen, "sanctuaryPaletteForTest");
                assertEquals(0, (int) call(palette, "fadeTimer"), "failure owns no success palette fade");
                assertEquals(0xCCC, (int) call(palette, "normalColor", 2, 0));
            }
            advance(loop, lives);
        }
        assertNotEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode(), "finite native results exit");
        assertTrue(sawControllerTargets, "actual B5 first setup published its result targets");
        assertEquals(outcome != Outcome.FAILURE, sawPan);
        assertEquals(outcome != Outcome.FAILURE, sawStars);
        assertEquals(outcome == Outcome.FINAL_SEVEN, sawHyperMessage);
        assertNotSame(backdrop, GameServices.level().getObjectManager(), "results exit actually loads its owned destination");
        assertEquals(saved == null ? 0x17 : 7, GameServices.level().getCurrentZone());
        assertEquals(saved == null ? 1 : 0, GameServices.level().getCurrentAct());
        var incoming = owner(loop).getRewindController();
        if (incoming != null) {
            assertEquals(0, incoming.earliestAvailableFrame());
            assertTrue(incoming.currentFrame() <= 1, "actual origin load starts a new live timeline");
        } else {
            // The fresh load may still be assembling its destination. The
            // caller's waitForReleasedWorld requires the first installed
            // production history at frame zero and a controller once ready.
            assertRetiredHistory(loop, stageController, "destination assembly cannot revive stage replay");
        }
        return stageHistory;
    }

    private static void assertResultPaletteSetup(S3kSpecialStageResultsScreen screen,
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        Object palette = call(screen, "sanctuaryPaletteForTest");
        assertNotNull(palette);
        if (row.main().equals("knuckles")) {
            int[] words = {0x84E, 0x40C, 0x206, 0xEE};
            for (int color = 2; color < 6; color++)
                assertEquals(words[color - 2], (int) call(palette, "normalColor", 0, color),
                        "sub_2E2C0 native Knuckles result ramp");
        } else {
            assertEquals(0xEE, (int) call(palette, "normalColor", 1, 5),
                    "sub_2E2C0 native Sonic/Tails result color");
        }
        assertEquals(0xCCC, (int) call(palette, "normalColor", 2, 0), "native initial HPZ wash");
    }

    private static void assertControllerTargets(S3kSpecialStageResultsScreen screen,
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        Object palette = call(screen, "sanctuaryPaletteForTest");
        int address = row.main().equals("knuckles") ? Sonic3kConstants.PAL_SONIC_TAILS_ADDR
                : Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR;
        byte[] expected = GameServices.rom().getRom().readBytes(address, 32);
        for (int color = 0; color < 16; color++) {
            int word = ((expected[color * 2] & 255) << 8) | (expected[color * 2 + 1] & 255);
            assertEquals(word, (int) call(palette, "targetColor", 1, color),
                    "native result character target color" + color);
        }
    }

    private static void assertReleasedSuccessPalette(S3kSpecialStageResultsScreen screen,
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        Object palette = call(screen, "sanctuaryPaletteForTest");
        assertEquals(0, (int) call(palette, "fadeTimer"), "natural success fade completed before stars");
        var rom = GameServices.rom().getRom();
        byte[] results = rom.readBytes(Sonic3kConstants.PAL_RESULTS_ADDR, 32);
        byte[] hpz = rom.readBytes(Sonic3kConstants.HPZ_MAIN_PALETTE_ADDR, 96);
        byte[] character = rom.readBytes(row.main().equals("knuckles")
                ? Sonic3kConstants.PAL_SONIC_TAILS_ADDR : Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR, 32);
        java.util.Set<Integer> whitened = java.util.Set.of(2 * 16 + 1, 2 * 16 + 2, 2 * 16 + 6,
                3 * 16 + 1, 3 * 16 + 3, 3 * 16 + 7, 3 * 16 + 8, 3 * 16 + 14, 3 * 16 + 15);
        // loc_2E410 starts nine cells at EEE, other scenery at CCC. Seven
        // Pal_DecColor2 visits lower each channel to min(start,target); a lower
        // start never increases. Master Emerald's two cyclic cells have their
        // separate object owner and are excluded from this static-byte oracle.
        for (int line = 1; line < 4; line++) for (int color = 0; color < 16; color++) {
            if (line == 3 && (color == 1 || color == 2)) continue;
            int start = line == 1 ? word(results, color * 2)
                    : whitened.contains(line * 16 + color) ? 0xEEE : 0xCCC;
            if (line == 1 && !row.main().equals("knuckles") && color == 5) start = 0xEE;
            int target = line == 1 ? word(character, color * 2) : word(hpz, (line - 1) * 32 + color * 2);
            int expected = Math.min(start & 0xE00, target & 0xE00)
                    | Math.min(start & 0xE0, target & 0xE0) | Math.min(start & 0xE, target & 0xE);
            assertEquals(expected, (int) call(palette, "normalColor", line, color),
                    "native released result color line" + line + "/" + color);
        }
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 255) << 8) | (bytes[offset + 1] & 255);
    }

    private static void assertFormWordArchive(S3kSpecialStageResultsScreen screen,
            CanonicalEntryMatrixSupport.Row row, Outcome outcome) throws Exception {
        // SpecialStage_Results/loc_2E04E (asm63073): Tails alone branches past
        // the seven-Super test, retaining SUPER even in the final-seven outcome.
        boolean hyper = outcome == Outcome.FINAL_SEVEN && !row.main().equals("tails");
        int address = row.main().equals("knuckles")
                ? hyper ? Sonic3kConstants.ART_KOSM_SS_RESULTS_HYPER_K_ADDR
                        : Sonic3kConstants.ART_KOSM_SS_RESULTS_SUPER_K_ADDR
                : hyper ? Sonic3kConstants.ART_KOSM_SS_RESULTS_HYPER_ADDR
                        : Sonic3kConstants.ART_KOSM_SS_RESULTS_SUPER_ADDR;
        Pattern[] expected = PatternDecompressor.kosinskiModuled(GameServices.rom().getRom(), address);
        Pattern[] actual = (Pattern[]) field(screen, "combinedPatterns");
        int offset = Sonic3kConstants.VRAM_SS_RESULTS_SUPER - Sonic3kConstants.VRAM_SS_RESULTS_BASE;
        assertTrue(expected.length > 0);
        for (int tile = 0; tile < expected.length; tile++) {
            byte[] a = new byte[64]; byte[] e = new byte[64];
            actual[offset + tile].copyInto(a, 0); expected[tile].copyInto(e, 0);
            assertArrayEquals(e, a, "literal ROM form archive tile" + tile + " at" + Integer.toHexString(address));
        }
    }

    private static void assertResultsRewindGate(GameLoop loop) throws Exception {
        // GameLoop.stepInternalBody gates rewind by mode before the independent
        // callback/fresh-boundary predicate. Results need not retain a fade.
        assertNotEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        assertFalse((boolean) call(loop, "isSpecialStageRewindable"));
        assertFalse((boolean) call(loop, "isBonusStageRewindable"));
    }

    private static void advance(GameLoop loop, int lives) {
        loop.step();
        GameServices.graphics().runPendingRenderThreadTasks();
        assertFalse(GameServices.sprites().getMainPlayable().getDead());
        assertTrue(GameServices.gameState().getLives() >= lives, "no life-loss reload during results");
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String name, int... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length]; java.util.Arrays.fill(types, int.class);
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        Object[] values = java.util.Arrays.stream(args).boxed().toArray();
        return method.invoke(target, values);
    }
}
