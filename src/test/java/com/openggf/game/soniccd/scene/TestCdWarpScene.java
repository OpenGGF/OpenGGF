package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/** Literal native scene vectors; real asset capability comes only from the configured original USA CUE. */
class TestCdWarpScene {
    private static CdWarpAssets cachedAssets;

    @Test void nullAssetsCannotCreateAnExecutingComponent() {
        assertThrows(NullPointerException.class, () -> new CdWarpScene(null));
    }

    @Test void initialCounterMustBeAnExplicitUnsignedWord() throws Exception {
        var scene = scene();
        assertThrows(IllegalArgumentException.class, () -> scene.initial(-1));
        assertThrows(IllegalArgumentException.class, () -> scene.initial(65536));
        assertEquals(65535, scene.initial(65535).vIntCounterWord());
    }

    @Test void initialCutKeepsUnknownExternalCramAndControllerInsteadOfInventingZeros() throws Exception {
        var s = scene().initial(0);
        assertEquals(CdWarpScene.Phase.DELAY, s.phase()); assertEquals(90, s.timerWord());
        assertEquals(0, s.vIntCounterWord()); assertEquals(0, s.fadeStep());
        assertEquals(0, s.vscrollBgWord()); assertEquals(0, s.displayedVscrollBgWord());
        assertEquals(0, s.trailIndex()); assertEquals(0, s.rngSeed());
        assertEquals(65535, s.cycleTimerWord()); assertEquals(0, s.cycleIndexWord());
        assertFalse(s.displayEnabled()); assertEquals(0, s.fadedOutByte());
        assertTrue(s.heldByte().isEmpty()); assertTrue(s.tapByte().isEmpty()); assertTrue(s.displayedPalette().isEmpty());
        assertArrayEquals(new byte[2048], s.objects().copy()); assertArrayEquals(new byte[640], s.cpuSprites().copy());
        assertArrayEquals(new byte[640], s.displayedSprites().copy()); assertArrayEquals(new byte[128], s.fadePalette().copy());
        assertArrayEquals(assets().palette().copy(), s.palette().copy()); assertEquals(0, s.spriteCount());
    }

    @Test void allNinetyDelayPulsesOnlyTickOwnTimerAndCounter() throws Exception {
        var scene = scene(); var first = scene.initial(0); var s = first;
        for (int n = 1; n <= 90; n++) {
            var result = scene.step(s, n % 2 == 0 ? 0x80 : 0xFF); s = result.state(); assertTrue(result.sounds().isEmpty());
            assertEquals(90 - n, s.timerWord()); assertEquals(n, s.vIntCounterWord());
            assertEquals(n == 90 ? CdWarpScene.Phase.PREPARE : CdWarpScene.Phase.DELAY, s.phase());
            assertArrayEquals(first.objects().copy(), s.objects().copy()); assertArrayEquals(first.palette().copy(), s.palette().copy());
            assertTrue(s.heldByte().isEmpty()); assertTrue(s.tapByte().isEmpty()); assertTrue(s.displayedPalette().isEmpty());
            assertFalse(s.displayEnabled()); assertEquals(0, s.vscrollBgWord()); assertEquals(0, s.displayedVscrollBgWord());
        }
    }

    @Test void ownCounterWrapIsWordArithmeticAndNeverAnOldLevelClock() throws Exception {
        var scene = scene(); var s = advance(scene, scene.initial(65535), 90, 0);
        assertEquals(89, s.vIntCounterWord()); assertEquals(0, s.timerWord());
        assertEquals(CdWarpScene.Phase.PREPARE, s.phase());
    }

    @Test void preparationPublishesWhiteBeforePollingAndRequestsOnlyDirectQueueTwoD8() throws Exception {
        var scene = scene(); var before = advance(scene, scene.initial(0), 90, 0x80);
        var result = scene.step(before, 0x88); var s = result.state();
        assertEquals(List.of(CdWarpScene.SoundRequest.FM_D8), result.sounds());
        assertEquals(0xD8, result.sounds().getFirst().nativeId()); assertEquals(2, result.sounds().getFirst().driverQueue());
        assertEquals(CdWarpScene.Phase.FADE_IN, s.phase()); assertEquals(0, s.fadeStep());
        assertEquals(239, s.timerWord()); assertEquals(91, s.vIntCounterWord()); assertTrue(s.displayEnabled());
        assertArrayEquals(white(), s.palette().copy()); assertArrayEquals(white(), s.displayedPalette().orElseThrow().copy());
        assertArrayEquals(assets().palette().copy(), s.fadePalette().copy());
        assertEquals(0, s.displayedVscrollBgWord()); assertEquals(65532, s.vscrollBgWord());
        assertEquals(OptionalInt.of(0x88), s.heldByte()); assertTrue(s.tapByte().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.sounds().clear());
    }

    @Test void firstUnknownTapBecomesKnownOnSecondSelectedPoll() throws Exception {
        var scene = scene(); var prep = advance(scene, scene.initial(0), 91, 8);
        assertTrue(prep.tapByte().isEmpty()); var changed = scene.step(prep, 0x88).state();
        assertEquals(OptionalInt.of(0x88), changed.heldByte()); assertEquals(OptionalInt.of(0x80), changed.tapByte());
        var held = scene.step(changed, 0x88).state(); assertEquals(OptionalInt.of(0), held.tapByte());
    }

    @Test void fadeInUsesEightRedThenEightGreenThenEightBluePasses() throws Exception {
        var scene = scene(); var s = advance(scene, scene.initial(0), 91, 8); var target = assets().palette();
        for (int n = 1; n <= 24; n++) {
            var result = scene.step(s, 8); s = result.state(); assertTrue(result.sounds().isEmpty());
            assertEquals(239 - n, s.timerWord()); assertEquals(91 + n, s.vIntCounterWord());
            int component = (n - 1) / 8, intensity = 14 - 2 * ((n - 1) % 8);
            for (int colour = 0; colour < 64; colour++) {
                int expected = 0;
                for (int c = 0; c < 3; c++) {
                    int mask = 0xE << (c * 4), raw = target.unsignedWord(colour * 2) & mask;
                    int level = c < component ? raw : c == component ? Math.max(raw, intensity << (c * 4)) : mask;
                    expected |= level;
                }
                assertEquals(expected, s.palette().unsignedWord(colour * 2));
            }
            assertArrayEquals(s.palette().copy(), s.displayedPalette().orElseThrow().copy());
            assertArrayEquals(new byte[2048], s.objects().copy()); assertEquals(65535, s.cycleTimerWord());
        }
        assertEquals(CdWarpScene.Phase.MAIN, s.phase()); assertEquals(0, s.fadeStep()); assertEquals(215, s.timerWord());
        assertEquals(0, s.fadedOutByte()); assertArrayEquals(target.copy(), s.palette().copy());
    }

    @Test void completeNoStartScheduleIs354PulsesWithOneSoundAndNoOldOwnerInputs() throws Exception {
        var scene = scene(); var s = scene.initial(0); int sounds = 0;
        for (int row = 1; row <= 354; row++) {
            var result = scene.step(s, 8); s = result.state(); sounds += result.sounds().size(); scene.validate(s);
            assertEquals(row, s.vIntCounterWord());
            if (row < 354) assertNotEquals(CdWarpScene.Phase.RETURNED, s.phase());
            for (int slot = 29; slot < 32; slot++) assertArrayEquals(new byte[64], image(s, slot), "Never-run final three back slots remain unallocated naturally");
        }
        assertEquals(1, sounds); assertEquals(CdWarpScene.Phase.RETURNED, s.phase()); assertEquals(24, s.fadeStep());
        assertEquals(0, s.timerWord()); assertEquals(1, s.fadedOutByte());
        assertArrayEquals(white(), s.palette().copy()); assertArrayEquals(white(), s.displayedPalette().orElseThrow().copy());
        assertEquals((-4 * 264) & 65535, s.vscrollBgWord()); assertEquals((-4 * 263) & 65535, s.displayedVscrollBgWord());
        assertThrows(IllegalStateException.class, () -> scene.step(advance(scene, scene.initial(0), 354, 8), 8));
    }

    @Test void holdingStartThroughDelayAndFadeDoesNotManufactureAMainLoopTap() throws Exception {
        var scene = scene(); var s = advance(scene, scene.initial(0), 115, 0x80);
        assertEquals(CdWarpScene.Phase.MAIN, s.phase()); assertEquals(OptionalInt.of(0), s.tapByte());
        s = scene.step(s, 0x80).state(); assertEquals(CdWarpScene.Phase.MAIN, s.phase()); assertEquals(214, s.timerWord());
    }

    @Test void newlyPressedStartIsTestedAfterOneActualMainObjectAndSelectedPulse() throws Exception {
        var scene = scene(); var s = main(scene); var counter = s.vIntCounterWord();
        var stopped = scene.step(s, 0x80).state(); assertEquals(CdWarpScene.Phase.FADE_OUT, stopped.phase());
        assertEquals(counter + 1, stopped.vIntCounterWord()); assertEquals(214, stopped.timerWord());
        assertEquals(OptionalInt.of(0x80), stopped.tapByte()); assertEquals(1, stopped.fadedOutByte());
        assertEquals(0, stopped.cycleTimerWord()); assertEquals(1, stopped.cycleIndexWord());
        var returned = advance(scene, stopped, 24, 0x80); assertEquals(CdWarpScene.Phase.RETURNED, returned.phase());
        assertEquals(190, returned.timerWord());
    }

    @Test void fullStartExitFadeStopsObjectsAndPaletteCycleImmediately() throws Exception {
        var scene = scene(); var start = scene.step(main(scene), 0x80).state(); var s = start;
        for (int n = 1; n <= 24; n++) {
            s = scene.step(s, 0).state(); assertArrayEquals(start.objects().copy(), s.objects().copy());
            assertArrayEquals(start.cpuSprites().copy(), s.cpuSprites().copy()); assertEquals(start.spriteCount(), s.spriteCount());
            assertEquals(start.rngSeed(), s.rngSeed()); assertEquals(start.cycleTimerWord(), s.cycleTimerWord());
            assertEquals(start.cycleIndexWord(), s.cycleIndexWord());
        }
    }

    @Test void firstSpawnUsesLiteralRngAndDoubleFrontVisitBeforeAndAfterSonic() throws Exception {
        var scene = scene(); var s = main(scene); s = scene.step(s, 8).state(); assertEquals(116, s.vIntCounterWord());
        s = scene.step(s, 8).state(); assertEquals(117, s.vIntCounterWord());
        assertEquals(0x7FE7B46AL, s.rngSeed()); assertEquals(1, s.trailIndex());
        byte[] expected = new byte[64]; word(expected, 0, 2); expected[3] = 1;
        longWord(expected, 6, CdWarpAssets.SPARKLE_MAPPING_ADDRESS); expected[11] = 1; expected[12] = 1; expected[13] = 1;
        word(expected, 14, 43); word(expected, 16, -9); longWord(expected, 20, (-24 << 16) + 2 * 0x17FE7);
        longWord(expected, 28, 0x17FE7); assertArrayEquals(expected, image(s, 4));
        var sonic = image(s, 0); assertEquals(1, uword(sonic, 0)); assertEquals(1, sonic[3] & 255);
        assertEquals(288, uword(sonic, 16)); assertEquals(408, uword(sonic, 20)); assertEquals(1, sonic[12] & 255);
        assertEquals(0, sonic[11] & 255); assertEquals(12, s.spriteCount(), "Front sparkle contributes two four-piece admissions around Sonic's four pieces");
    }

    @Test void mainObjectPassOrderAndSourceSatLinksRemainLiteral() throws Exception {
        var scene = scene(); var s = advance(scene, main(scene), 2, 8); var sat = s.cpuSprites();
        assertEquals(12, s.spriteCount());
        assertEquals(((-24 + (0x17FE7 >>> 16)) - 32) & 65535, sat.unsignedWord(0));
        assertEquals(1, sat.unsignedByte(3)); assertEquals(0x46, sat.unsignedWord(4));
        assertEquals(384, sat.unsignedWord(4 * 8)); assertEquals(5, sat.unsignedByte(4 * 8 + 3)); assertEquals(0x8013, sat.unsignedWord(4 * 8 + 4));
        assertEquals(12, sat.unsignedByte(11 * 8 + 3)); assertEquals(0, sat.unsignedLong(12 * 8));
    }

    @Test void unusedBackThreeAreNotDispatchedButLastActuallyVisitedBackSlotIs() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy();
        for (int slot = 28; slot < 32; slot++) System.arraycopy(sparkle(45), 0, objects, slot * 64, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state();
        assertEquals(44, uword(image(after, 28), 14));
        for (int slot = 29; slot < 32; slot++) assertArrayEquals(image(edit(s, objects, 1, null, null, null), slot), image(after, slot));
    }

    @Test void frontSparkleDeletionClearsAll64BytesAndSecondVisitDoesNothing() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sparkle(1);
        Arrays.fill(value, 32, 64, (byte) 0xA5); System.arraycopy(value, 0, objects, 4 * 64, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 0).state();
        assertArrayEquals(new byte[64], image(after, 4)); assertEquals(0, after.spriteCount());
    }

    @Test void ordinaryBackSparkleRunsOnceWhileFrontRunsTwice() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy();
        System.arraycopy(sparkle(45), 0, objects, 4 * 64, 64); System.arraycopy(sparkle(45), 0, objects, 7 * 64, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state();
        assertEquals(43, uword(image(after, 4), 14)); assertEquals(44, uword(image(after, 7), 14));
        assertEquals(1, image(after, 4)[11] & 255); assertEquals(0, image(after, 7)[11] & 255);
    }

    @Test void sonicInitAndIntegerMotionPreserveDirtyFractionsAndUnusedBytes() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), sonic = new byte[64];
        Arrays.fill(sonic, (byte) 0xA5); word(sonic, 0, 1); sonic[2] = 0; sonic[3] = 0;
        System.arraycopy(sonic, 0, objects, 0, 64); var after = image(scene.step(edit(s, objects, 1, null, null, null), 8).state(), 0);
        assertEquals(288, uword(after, 16)); assertEquals(408, uword(after, 20));
        assertEquals(0xA5A5, uword(after, 18)); assertEquals(0xA5A5, uword(after, 22));
        assertArrayEquals(Arrays.copyOfRange(sonic, 24, 64), Arrays.copyOfRange(after, 24, 64));
        assertEquals(0x8000, uword(after, 4)); assertEquals(1, after[3] & 255);
    }

    @Test void sonicWaitWritesAndDecrementsOnlyTheHighTimerByte() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), sonic = sonic();
        sonic[3] = 1; word(sonic, 20, 256); word(sonic, 14, 0xABCD); System.arraycopy(sonic, 0, objects, 0, 64);
        var arrived = scene.step(edit(s, objects, 1, null, null, null), 8).state();
        assertEquals(2, image(arrived, 0)[3] & 255); assertEquals(0x78CD, uword(image(arrived, 0), 14));
        var waiting = scene.step(arrived, 8).state(); assertEquals(0x77CD, uword(image(waiting, 0), 14));
    }

    @Test void sonicRoutineDoneDoesNotResetWhenItsIdIsRewrittenOnCadence() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), sonic = sonic();
        word(sonic, 0, 0x1234); word(sonic, 20, 96); System.arraycopy(sonic, 0, objects, 0, 64);
        var after = scene.step(edit(s, objects, 0, 0, null, null), 8).state();
        assertEquals(1, uword(image(after, 0), 0)); assertEquals(4, image(after, 0)[3] & 255); assertEquals(96, uword(image(after, 0), 20));
    }

    @Test void trailsCopyAllFourMainAnimationBytesThenApplyTheirOwnDrawDecrement() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy();
        System.arraycopy(sonic(), 0, objects, 0, 64); System.arraycopy(sonic(), 0, objects, 64, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state();
        assertEquals(1, image(after, 0)[12] & 255); assertEquals(0, image(after, 1)[12] & 255);
        assertEquals(image(after, 0)[10], image(after, 1)[10]); assertEquals(image(after, 0)[11], image(after, 1)[11]);
        assertEquals(image(after, 0)[13], image(after, 1)[13]);
    }

    @Test void emptySatBuildClearsOnlyItsFirstLongAndRetainsAllOtherTailBytes() throws Exception {
        var scene = scene(); var s = main(scene); byte[] sat = new byte[640]; Arrays.fill(sat, (byte) 0xA5);
        var after = scene.step(edit(s, null, 1, null, null, CdAssetBytes.copyOf(sat)), 8).state();
        assertEquals(0, after.spriteCount()); assertArrayEquals(new byte[4], after.cpuSprites().slice(0, 4).copy());
        assertArrayEquals(Arrays.copyOfRange(sat, 4, 640), after.cpuSprites().slice(4, 636).copy());
        assertArrayEquals(after.cpuSprites().copy(), after.displayedSprites().copy());
    }

    @Test void fullSatKeepsLastNativeLink80AndDoesNotSuppressLaterObjectMotion() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy();
        for (int slot = 4; slot < 29; slot++) System.arraycopy(sparkle(45), 0, objects, slot * 64, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state();
        assertEquals(80, after.spriteCount()); assertEquals(80, after.cpuSprites().unsignedByte(79 * 8 + 3));
        assertEquals(44, uword(image(after, 28), 14)); assertEquals(43, uword(image(after, 4), 14));
    }

    @Test void fullSparkleSearchStopsBeforeTheFirstUnownedBankWrite() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy();
        for (int slot = 4; slot < 32; slot++) System.arraycopy(sparkle(45), 0, objects, slot * 64, 64);
        var input = edit(s, objects, 0, null, null, null);
        var failure = assertThrows(CdWarpScene.UnsupportedScene.class, () -> scene.step(input, 8));
        assertEquals(CdWarpScene.Boundary.SPARKLE_BANK_FULL, failure.boundary()); assertEquals(32, failure.objectIndex());
        assertArrayEquals(objects, input.objects().copy(), "Pure failure publishes no partial successful Result");
    }

    @Test void flagsBitOneFreezesAnimationWhileBitTwoSkipsOnlyDrawing() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sonic();
        value[2] = 2; value[12] = (byte) 0x80; System.arraycopy(value, 0, objects, 0, 64);
        var held = scene.step(edit(s, objects, 1, null, null, null), 8).state(); assertEquals(128, image(held, 0)[12] & 255); assertEquals(4, held.spriteCount());
        value[2] = 4; value[12] = 0; System.arraycopy(value, 0, objects, 0, 64);
        var hidden = scene.step(edit(s, objects, 1, null, null, null), 8).state(); assertEquals(1, image(hidden, 0)[11] & 255); assertEquals(0, hidden.spriteCount());
    }

    @Test void rawAnimationFrameWrapHonoursFrameCountFourDespiteFifthSparklePointer() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sparkle(45);
        value[11] = 3; value[12] = 0; System.arraycopy(value, 0, objects, 7 * 64, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state(); assertEquals(0, image(after, 7)[11] & 255);
        assertEquals(1, image(after, 7)[12] & 255); assertEquals(4, after.spriteCount());
    }

    @Test void zeroXIsForcedToOneOnlyAfterSignedOffsetAndWordMask() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sonic();
        value[2] = 2; word(value, 16, 8); System.arraycopy(value, 0, objects, 0, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state(); assertEquals(1, after.cpuSprites().unsignedWord(6));
        word(value, 16, 0xFF08); System.arraycopy(value, 0, objects, 0, 64);
        after = scene.step(edit(s, objects, 1, null, null, null), 8).state(); assertEquals(0x100, after.cpuSprites().unsignedWord(6));
    }

    @Test void selectedMalformedRoutineAndMappingStopsKeepTheInputImmutable() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sonic();
        value[3] = 5; System.arraycopy(value, 0, objects, 0, 64); var invalidRoutine = edit(s, objects, 1, null, null, null);
        assertEquals(CdWarpScene.Boundary.SONIC_ROUTINE, assertThrows(CdWarpScene.UnsupportedScene.class, () -> scene.step(invalidRoutine, 8)).boundary());
        value[3] = 4; longWord(value, 6, 0xDEADBEEF); System.arraycopy(value, 0, objects, 0, 64); var invalidMap = edit(s, objects, 1, null, null, null);
        assertEquals(CdWarpScene.Boundary.ANIMATION_DIRECTORY, assertThrows(CdWarpScene.UnsupportedScene.class, () -> scene.step(invalidMap, 8)).boundary());
        assertArrayEquals(objects, invalidMap.objects().copy());
    }

    @Test void hiddenOrAnimationHeldObjectsStillReachTheirNativeDirectoryRead() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sonic();
        value[2] = 6; longWord(value, 6, 0xDEADBEEF); System.arraycopy(value, 0, objects, 0, 64);
        var input = edit(s, objects, 1, null, null, null);
        assertEquals(CdWarpScene.Boundary.ANIMATION_DIRECTORY, assertThrows(CdWarpScene.UnsupportedScene.class, () -> scene.step(input, 8)).boundary());
    }

    @Test void malformedIgnoredFinalBackRoutineIsNeverEagerlyRead() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sparkle(45);
        value[3] = (byte) 0xFF; longWord(value, 6, 0xDEADBEEF); System.arraycopy(value, 0, objects, 31 * 64, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state(); assertArrayEquals(value, image(after, 31));
    }

    @Test void drawingOffDoesNotReadTheSelectedFramePointer() throws Exception {
        var scene = scene(); var s = main(scene); byte[] objects = s.objects().copy(), value = sonic();
        value[2] = 6; value[11] = (byte) 255; System.arraycopy(value, 0, objects, 0, 64);
        var after = scene.step(edit(s, objects, 1, null, null, null), 8).state(); assertEquals(0, after.spriteCount());
        value[2] = 2; System.arraycopy(value, 0, objects, 0, 64); var input = edit(s, objects, 1, null, null, null);
        assertEquals(CdWarpScene.Boundary.ANIMATION_FRAME, assertThrows(CdWarpScene.UnsupportedScene.class, () -> scene.step(input, 8)).boundary());
    }

    @Test void requestLedgerCopiesItsInputAndNoReturnedPhaseCanTickAgain() throws Exception {
        var scene = scene(); var s = scene.initial(0); var sounds = new ArrayList<CdWarpScene.SoundRequest>(); sounds.add(CdWarpScene.SoundRequest.FM_D8);
        var result = new CdWarpScene.Result(s, sounds); sounds.clear(); assertEquals(1, result.sounds().size());
        assertThrows(UnsupportedOperationException.class, () -> result.sounds().clear());
        var returned = advance(scene, s, 354, 0); assertThrows(IllegalStateException.class, () -> scene.step(returned, 0));
    }

    @Test void stateBytesRemainDefensiveAndEqualValueCopiesReplayIdentically() throws Exception {
        var scene = scene(); var s = advance(scene, scene.initial(0), 136, 8); var clone = edit(s, s.objects().copy(), null, null, null, CdAssetBytes.copyOf(s.cpuSprites().copy()));
        assertEquals(s, clone); assertEquals(s.hashCode(), clone.hashCode());
        byte[] escaped = clone.objects().copy(); Arrays.fill(escaped, (byte) 0xFF); assertEquals(s, clone);
        assertThrows(ReadOnlyBufferException.class, () -> clone.objects().buffer().put((byte) 9));
        assertEquals(scene.step(s, 8), scene.step(clone, 8));
    }

    @Test void rawHeldWidthsRejectBeforeAnyPhaseIncludingUnpolledDelay() throws Exception {
        var scene = scene(); var s = scene.initial(0);
        assertThrows(IllegalArgumentException.class, () -> scene.step(s, -1)); assertThrows(IllegalArgumentException.class, () -> scene.step(s, 256));
        assertThrows(NullPointerException.class, () -> scene.step(null, 0)); assertThrows(NullPointerException.class, () -> scene.validate(null));
    }

    private static synchronized CdWarpAssets assets() throws Exception {
        if (cachedAssets != null) return cachedAssets;
        String media = System.getProperty("soniccd.media.root"); Assumptions.assumeTrue(media != null && !media.isBlank(), "Actual original USA media configured");
        Path cue;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            cachedAssets = CdWarpAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
        return cachedAssets;
    }
    private static CdWarpScene scene() throws Exception { return new CdWarpScene(assets()); }
    private static CdWarpScene.State main(CdWarpScene scene) { return advance(scene, scene.initial(0), 115, 8); }
    private static CdWarpScene.State advance(CdWarpScene scene, CdWarpScene.State state, int count, int held) {
        for (int n = 0; n < count; n++) state = scene.step(state, held).state(); return state;
    }
    private static byte[] white() { byte[] result = new byte[128]; for (int n = 0; n < 128; n += 2) { result[n] = 14; result[n + 1] = (byte) 238; } return result; }
    private static byte[] image(CdWarpScene.State state, int slot) { return state.objects().slice(slot * 64, 64).copy(); }
    private static byte[] sonic() {
        byte[] v = new byte[64]; word(v, 0, 1); v[3] = 4; word(v, 4, 0x8000); longWord(v, 6, CdWarpAssets.SONIC_MAPPING_ADDRESS);
        v[12] = 2; v[13] = 2; word(v, 16, 288); word(v, 20, 248); return v;
    }
    private static byte[] sparkle(int timer) {
        byte[] v = new byte[64]; word(v, 0, 2); v[3] = 1; longWord(v, 6, CdWarpAssets.SPARKLE_MAPPING_ADDRESS);
        v[12] = 1; v[13] = 1; word(v, 14, timer); word(v, 16, 200); word(v, 20, 200); return v;
    }
    private static CdWarpScene.State edit(CdWarpScene.State s, byte[] objects, Integer counter, Integer trail, Long rng, CdAssetBytes cpu) {
        return new CdWarpScene.State(s.phase(), s.timerWord(), counter == null ? s.vIntCounterWord() : counter, s.fadeStep(),
                s.vscrollBgWord(), s.displayedVscrollBgWord(), trail == null ? s.trailIndex() : trail, rng == null ? s.rngSeed() : rng,
                s.cycleTimerWord(), s.cycleIndexWord(), s.displayEnabled(), s.fadedOutByte(), s.heldByte(), s.tapByte(),
                objects == null ? s.objects() : CdAssetBytes.copyOf(objects), s.palette(), s.fadePalette(), s.displayedPalette(),
                cpu == null ? s.cpuSprites() : cpu, s.spriteCount(), s.displayedSprites(), s.displayedSpriteCount());
    }
    private static void word(byte[] out, int at, int value) { out[at] = (byte) (value >>> 8); out[at + 1] = (byte) value; }
    private static void longWord(byte[] out, int at, int value) { word(out, at, value >>> 16); word(out, at + 2, value); }
    private static int uword(byte[] out, int at) { return ((out[at] & 255) << 8) | (out[at + 1] & 255); }
}
