package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.openggf.game.soniccd.resources.CdLevelHud.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure component ingress, not native invocation, elapsed V-int or GPU evidence.
 * Synthetic glyph halves/old images make writes and nonwrites independently visible;
 * the actual configured USA source supplies the existing SectionArt source owner only.
 * N1ecc5f3a HUD and Points/Main.asm:186-717 owns the literals and native order.
 */
class TestCdLevelHud {
    private static CdR11bAssets nativeAssets;
    private static CdR11bSectionArt art;

    @BeforeAll static void actualSectionOwner() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Set soniccd.media.root; explicitly selected retail-backed HUD checks must not silently skip");
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            nativeAssets = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        art = CdR11bSectionArt.fromAssets(nativeAssets);
    }

    @Test void normalFirstUpdateUsesLiteralCellsAndPreservesTheWholeActiveSection() {
        var before = state(1234, 0x80, 1001, 0x80, 12, 1, 1, 0xA8023B3B, 0xFF);
        var queued = art.prepare(art.initList(before.section(), 3));
        before = withSection(before, queued);
        var after = hud().run(before, input()).state();
        var expected = new HashMap<Integer, byte[]>();
        for (int i = 0; i < 6; i++) blank(expected, 0xB060 + i * 64);
        glyph(expected, 0xB1E0, 0);
        for (int i = 0; i < 4; i++) glyph(expected, 0xB0E0 + i * 64, i + 1);
        for (int i = 0; i < 3; i++) glyph(expected, 0xB360 + i * 64, 9);
        glyph(expected, 0xB220, 3); glyph(expected, 0xB260, 0); glyph(expected, 0xB2A0, 0);
        glyph(expected, 0xB2E0, 0); glyph(expected, 0xB320, 0); glyph(expected, 0xB4A0, 9);
        image(expected, after.section().residency()); queue(before.section(), after.section());
        assertEquals(999, after.ring().rings()); assertEquals(12, after.ring().lives());
        assertEquals(0, after.ring().updateHudRings()); assertEquals(0, after.ring().updateHudLives());
        assertEquals(0, after.score().updateHudScore()); assertEquals(before.score().score(), after.score().score());
        assertEquals(before.score().nextLifeScore(), after.score().nextLifeScore());
        assertEquals(0xA8030000, CdCheckpointLiveAux.read(after.liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
        assertEquals(before.level(), after.level()); water(before.liveTime(), after.liveTime());
        assertEquals(before.ring().livesFlags(), after.ring().livesFlags());
        assertEquals(before.ring().timeStopTimer(), after.ring().timeStopTimer());
        assertEquals(before.ring().ringAnimationTimer(), after.ring().ringAnimationTimer());
        assertEquals(before.ring().ringAnimationFrame(), after.ring().ringAnimationFrame());
    }

    @Test void positiveZeroCountersSkipEveryPatternWriteAndRetainOldLeadingDigits() {
        var before = state(0, 1, 0, 1, 3, 0, 0, 0, 0xFF);
        var expected = new HashMap<Integer, byte[]>();
        for (int i = 0; i < 7; i++) glyph(expected, 0xB060 + i * 64, 7);
        for (int i = 0; i < 3; i++) glyph(expected, 0xB360 + i * 64, 5);
        before = withSection(before, paint(before.section(), expected));
        var result = hud().run(before, input());
        assertSame(before.section(), result.state().section()); image(expected, result.state().section().residency());
        assertEquals(List.of(NativeCall.DRAW_SCORE, NativeCall.DRAW_RINGS), result.calls());
        assertEquals(0, result.state().score().updateHudScore()); assertEquals(0, result.state().ring().updateHudRings());
    }

    @Test void positiveOneScoreWritesOnlyTheSixthDigitWithoutInventingTheSeventhZero() {
        var result = hud().run(state(1, 0x7F, 0, 0, 3, 0, 0, 0, 0), input());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB1A0, 1);
        image(expected, result.state().section().residency());
        assertEquals(List.of(NativeCall.DRAW_SCORE), result.calls());
    }

    @Test void resetUsesByteShiftFiveRatherThanTheOrdinaryDigitShiftSix() {
        byte[] reset = views().resetTiles().copy(); reset[0] = 1;
        var owner = new CdLevelHud(new Assets(views().numbers(), CdAssetBytes.copyOf(reset), views().divisors()), art);
        var result = owner.run(state(0, 0x80, 0, 0, 2, 0, 0, 0, 0), input());
        var expected = new HashMap<Integer, byte[]>();
        expected.put(0xB060, filled(2)); expected.put(0xB080, filled(3));
        for (int i = 1; i < 6; i++) blank(expected, 0xB060 + i * 64);
        glyph(expected, 0xB1E0, 0); glyph(expected, 0xB4A0, 2);
        image(expected, result.state().section().residency());
        assertEquals(List.of(NativeCall.DRAW_LIVES, NativeCall.RESET_SCORE, NativeCall.DRAW_SCORE), result.calls());
    }

    @Test void secondGlyphHalfFaultKeepsFirstTileAndTheEarlierLivesButNotLaterFlagClear() {
        byte[] reset = views().resetTiles().copy(); reset[0] = 31;
        var owner = new CdLevelHud(new Assets(views().numbers(), CdAssetBytes.copyOf(reset), views().divisors()), art);
        var before = state(0, 0x80, 7, 0x80, 2, 1, 1, 0, 0xFF);
        var stopped = assertThrows(UnsupportedHud.class, () -> owner.run(before, input()));
        assertEquals(Boundary.FONT_ADDRESS, stopped.boundary()); assertEquals(1024, stopped.sourceOffset()); assertEquals(0xB080, stopped.destinationWord());
        assertEquals(0x80, stopped.state().score().updateHudScore()); assertEquals(before.ring(), stopped.state().ring());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB4A0, 2); expected.put(0xB060, filled(32));
        image(expected, stopped.state().section().residency()); queue(before.section(), stopped.state().section());
        assertEquals(List.of(NativeCall.DRAW_LIVES), stopped.calls());
    }

    @Test void everyNegativeResetSelectorBlanksWithoutAnInvalidFontRead() {
        byte[] reset = views().resetTiles().copy(); Arrays.fill(reset, (byte) 0x80);
        var owner = new CdLevelHud(new Assets(views().numbers(), CdAssetBytes.copyOf(reset), views().divisors()), art);
        var result = owner.run(state(0, 0xFF, 0, 0xFF, 1, 0, 0, 0, 0), input());
        var expected = new HashMap<Integer, byte[]>();
        for (int i = 0; i < 7; i++) blank(expected, 0xB060 + i * 64);
        for (int i = 0; i < 3; i++) blank(expected, 0xB360 + i * 64);
        glyph(expected, 0xB4A0, 1); image(expected, result.state().section().residency());
    }

    @Test void unsignedScoreAndSignedWordGlyphIndexStopAfterTheActualFlagClear() {
        var before = state(0xFFFFFFFF, 1, 7, 1, 1, 1, 1, 0, 0xFF);
        var stopped = assertThrows(UnsupportedHud.class, () -> hud().run(before, input()));
        assertEquals(Boundary.FONT_ADDRESS, stopped.boundary()); assertEquals(-3776, stopped.sourceOffset());
        assertEquals(0xB060, stopped.destinationWord()); assertEquals(0, stopped.state().score().updateHudScore());
        assertEquals(0xFFFFFFFF, stopped.state().score().score()); assertEquals(before.ring(), stopped.state().ring());
        image(Map.of(), stopped.state().section().residency()); assertTrue(stopped.calls().isEmpty());
    }

    @Test void digitCountWrapsAsAWordBeforeLeadingZeroAndGlyphIndexDecisions() {
        byte[] divisors = views().divisors().copy(); ByteBuffer.wrap(divisors).putInt(0, 1);
        var owner = new CdLevelHud(new Assets(views().numbers(), views().resetTiles(), CdAssetBytes.copyOf(divisors)), art);
        var result = owner.run(state(65536, 1, 0, 0, 1, 0, 0, 0, 0), input());
        image(Map.of(), result.state().section().residency());
        assertEquals(65536, result.state().score().score()); assertEquals(List.of(NativeCall.DRAW_SCORE), result.calls());
    }

    @Test void zeroDivisorReportsFiniteNonreturnAfterTheCompletedResetPrefix() {
        byte[] divisors = views().divisors().copy(); ByteBuffer.wrap(divisors).putInt(0, 0);
        var owner = new CdLevelHud(new Assets(views().numbers(), views().resetTiles(), CdAssetBytes.copyOf(divisors)), art);
        var before = state(0, 0x80, 0, 0, 2, 0, 0, 0, 0);
        var stopped = assertThrows(UnsupportedHud.class, () -> owner.run(before, input()));
        assertEquals(Boundary.NON_RETURNING_DIGIT, stopped.boundary()); assertEquals(0, stopped.sourceOffset());
        assertEquals(0, stopped.state().score().updateHudScore());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB4A0, 2);
        for (int i = 0; i < 6; i++) blank(expected, 0xB060 + i * 64);
        glyph(expected, 0xB1E0, 0); image(expected, stopped.state().section().residency());
        assertEquals(List.of(NativeCall.DRAW_LIVES, NativeCall.RESET_SCORE), stopped.calls());
    }

    @ParameterizedTest @CsvSource({"0000003B,00000100", "00003B3B,00010000", "00083B3B,00090000", "AAFF3B3B,AA000000", "000000FF,00000000", "000000FE,00000100", "0000FF3B,00000000", "00FF3B3B,00000000"})
    void timerUsesWrappedBytesBeforeUnsignedCarryComparisons(String raw, String expected) {
        var before = state(0, 0, 0, 0, 1, 0, 1, hex(raw), 0xFF);
        var result = hud().run(before, input());
        assertEquals(hex(expected), CdCheckpointLiveAux.read(result.state().liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
        water(before.liveTime(), result.state().liveTime()); assertEquals(before.level(), result.state().level());
        assertEquals(List.of(NativeCall.DRAW_MINS, NativeCall.DRAW_SECS, NativeCall.DRAW_SECS), result.calls());
    }

    @ParameterizedTest @ValueSource(ints = {1, 128, 255})
    void lockedControlsDrawTheExistingTimeWithoutTicking(int locked) {
        var before = state(0, 0, 0, 0, 1, 0, 1, 0xF1020304, 0xFF);
        var result = hud().run(before, input(new Globals(0, 0, locked, 0, 0, 0, 0), emptyDebug()));
        assertEquals(before.liveTime(), result.state().liveTime());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB220, 2); glyph(expected, 0xB260, 0); glyph(expected, 0xB2A0, 3); glyph(expected, 0xB2E0, 0); glyph(expected, 0xB320, 6);
        image(expected, result.state().section().residency());
    }

    @ParameterizedTest @ValueSource(ints = {1, 256, 32768, 65535})
    void theWholeOldPausedWordSkipsUnknownTimeWithoutSuppressingTheHudCall(int paused) {
        var before = state(0, 0, 0, 0, 1, 0, 1, 0, 0xF0);
        var result = hud().run(before, input(new Globals(0, paused, 0, 0, 0, 0, 0), emptyDebug()));
        assertEquals(before, result.state()); assertTrue(result.calls().isEmpty()); image(Map.of(), result.state().section().residency());
    }

    @Test void allSixteenTimeKnowledgeMasksKeepEarlierCounterPrefixesAndWaterKnowledge() {
        for (int mask = 0; mask < 16; mask++) {
            var before = state(0, 1, 0, 1, 1, 0, 1, 0xAA010203, mask | 0xA0);
            if (mask != 15) {
                var stopped = assertThrows(UnsupportedHud.class, () -> hud().run(before, input()));
                int first = 0; while ((mask & (1 << first)) != 0) first++;
                assertEquals(Boundary.LIVE_TIME, stopped.boundary()); assertEquals(0xFF1514 + first, stopped.sourceOffset());
                assertEquals(before.liveTime(), stopped.state().liveTime()); assertEquals(0, stopped.state().score().updateHudScore()); assertEquals(0, stopped.state().ring().updateHudRings());
                assertEquals(List.of(NativeCall.DRAW_SCORE, NativeCall.DRAW_RINGS), stopped.calls());
                image(Map.of(), stopped.state().section().residency()); water(before.liveTime(), stopped.state().liveTime());
            } else {
                var after = hud().run(before, input()).state();
                assertEquals(0xAA010204, CdCheckpointLiveAux.read(after.liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
                water(before.liveTime(), after.liveTime()); assertEquals(0xAF, after.liveTime().knownMask());
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 127})
    void exactMaximumClearsTimeAndStopsBeforeKillEvenWhenControlsAreLocked(int period) {
        var before = state(0, 0x80, 7, 0x80, 2, 1, 1, 0x00093B3B, 0xFF);
        before = withBonus(before, Optional.of(new BonusValues(123, 456, 1)));
        var actual = before;
        var stopped = assertThrows(UnsupportedHud.class, () -> hud().run(actual, input(new Globals(0, 0, 255, period, 0, 0, 0), emptyDebug())));
        assertEquals(Boundary.MAX_TIME_KILL, stopped.boundary()); assertEquals(0, stopped.state().level().updateHudTime());
        assertEquals(0, CdCheckpointLiveAux.read(stopped.state().liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
        assertEquals(before.level().timeOver(), stopped.state().level().timeOver()); assertEquals(1, stopped.state().ring().updateHudLives()); assertEquals(before.bonus(), stopped.state().bonus());
        assertEquals(List.of(NativeCall.DRAW_LIVES, NativeCall.RESET_SCORE, NativeCall.DRAW_SCORE, NativeCall.RESET_RINGS, NativeCall.DRAW_RINGS), stopped.calls());
        water(before.liveTime(), stopped.state().liveTime());
    }

    @ParameterizedTest @ValueSource(ints = {128, 255})
    void activeWarpMaximumReturnsWithoutKillTimeFlagOrLaterLivesBonusReads(int period) {
        var before = withBonus(state(0, 0, 0, 0, 3, 1, 1, 0x00093B3B, 0xFF), Optional.empty());
        var result = hud().run(before, input(new Globals(0, 0, 0, period, 0, 0, 0), emptyDebug()));
        assertEquals(before, result.state()); assertTrue(result.calls().isEmpty()); image(Map.of(), result.state().section().residency());
    }

    @Test void aNonzeroLeadingTimeByteDoesNotSelectTheMaximumTimeBranch() {
        var before = state(0, 0, 0, 0, 1, 0, 1, 0xAA093B3B, 0xFF);
        var after = hud().run(before, input()).state();
        assertEquals(0xAA090000, CdCheckpointLiveAux.read(after.liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
        assertEquals(before.level(), after.level());
    }

    @Test void reachingMaximumDuringTheTickDrawsNinetyNineCentiseconds() {
        var result = hud().run(state(0, 0, 0, 0, 1, 0, 1, 0x00093B3A, 0xFF), input());
        assertEquals(0x00093B3B, CdCheckpointLiveAux.read(result.state().liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
        tile(result.state().section(), 0xB2E0, 19); tile(result.state().section(), 0xB300, 20); tile(result.state().section(), 0xB320, 19); tile(result.state().section(), 0xB340, 20);
    }

    @Test void dirtyCentisecondsStopAfterMinutesAndSecondsWithoutFittingTheirDigits() {
        var before = state(0, 0, 0, 0, 1, 1, 1, 0x000102FF, 0xFF);
        var stopped = assertThrows(UnsupportedHud.class, () -> hud().run(before, input(new Globals(0, 0, 1, 0, 0, 0, 0), emptyDebug())));
        assertEquals(Boundary.FONT_ADDRESS, stopped.boundary()); assertEquals(2688, stopped.sourceOffset()); assertEquals(0xB2E0, stopped.destinationWord());
        assertEquals(before.liveTime(), stopped.state().liveTime()); assertEquals(1, stopped.state().ring().updateHudLives());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB220, 1); glyph(expected, 0xB260, 0); glyph(expected, 0xB2A0, 2);
        image(expected, stopped.state().section().residency()); assertEquals(List.of(NativeCall.DRAW_MINS, NativeCall.DRAW_SECS), stopped.calls());
    }

    @Test void knownZeroBonusFlagSkipsBothBonusWordsAndZoneSelection() {
        var before = withBonus(state(0, 0, 0, 0, 1, 0, 0, 0, 0), Optional.of(new BonusValues(0xFFFF, 0xFFFF, 0)));
        var result = hud().run(before, input(new Globals(0, 0, 0, 0, 0, 0, 0x502), emptyDebug()));
        assertEquals(before, result.state()); assertTrue(result.calls().isEmpty());
    }

    @Test void missingBonusFlagStopsOnlyAfterTheReachedLivesClearAndDraw() {
        var before = withBonus(state(0, 0, 0, 0, 3, 1, 0, 0, 0), Optional.empty());
        var stopped = assertThrows(UnsupportedHud.class, () -> hud().run(before, input()));
        assertEquals(Boundary.BONUS_VALUES, stopped.boundary()); assertEquals(0xFFF7D6, stopped.sourceOffset());
        assertEquals(0, stopped.state().ring().updateHudLives());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB4A0, 3); image(expected, stopped.state().section().residency()); assertEquals(List.of(NativeCall.DRAW_LIVES), stopped.calls());
    }

    @ParameterizedTest @CsvSource({"0,8780,88C0", "1282,6D40,6E80"})
    void bonusUsesItsFullZoneWordAndBlanksLeadingSlotsButAlwaysDrawsFinalZero(int zone, String first, String second) {
        var before = withBonus(state(0, 0, 0, 0, 1, 0, 0, 0, 0), Optional.of(new BonusValues(1234, 0, 0xFF)));
        var result = hud().run(before, input(new Globals(0, 0, 0, 0, 0, 0, zone), emptyDebug()));
        int a = hex(first), b = hex(second); var expected = new HashMap<Integer, byte[]>(); blank(expected, a);
        for (int i = 1; i < 5; i++) glyph(expected, a + i * 64, i);
        for (int i = 0; i < 4; i++) blank(expected, b + i * 64); glyph(expected, b + 4 * 64, 0);
        image(expected, result.state().section().residency());
        assertEquals(Optional.of(new BonusValues(1234, 0, 0)), result.state().bonus()); assertEquals(List.of(NativeCall.DRAW_BONUS, NativeCall.DRAW_BONUS), result.calls());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void debugRetainsPositionGlyphsAndStopsAtTheFirstMissingOverwrittenRead(int phase) {
        var before = state(123, 0x80, 99, 0x80, 1, 1, 1, 0, 0);
        var debug = new DebugData(phase == 0 ? OptionalInt.empty() : OptionalInt.of(0xA5), phase < 2 ? Optional.empty() : Optional.of(CdAssetBytes.copyOf(new byte[2048])), OptionalInt.empty());
        var stopped = assertThrows(UnsupportedHud.class, () -> hud().run(before, input(new Globals(0x100, 0, 0, 0, 0x7F22, 0xEFFE, 0), debug)));
        assertEquals(phase == 0 ? Boundary.DEBUG_SAVED_FLAGS : phase == 1 ? Boundary.DEBUG_LAYOUT : Boundary.DEBUG_BLOCK, stopped.boundary());
        assertEquals(phase == 0 ? 0xFF1200 : phase == 1 ? 0x3FF : 0xFF194E, stopped.sourceOffset());
        var expected = new HashMap<Integer, byte[]>(); int[] x = {7,15,2,2}, y = {14,15,15,14};
        for (int i = 0; i < 4; i++) { glyph(expected, 0xB0E0 + i * 64, x[i]); glyph(expected, 0xB260 + i * 64, y[i]); }
        image(expected, stopped.state().section().residency()); assertEquals(before.ring(), stopped.state().ring()); assertEquals(before.score(), stopped.state().score()); assertEquals(before.liveTime(), stopped.state().liveTime()); assertEquals(List.of(NativeCall.DRAW_POS), stopped.calls());
    }

    @Test void completedDebugUsesHighXByteLayoutIndexThenJoinsLivesWithoutNormalCountersOrTime() {
        var before = state(123, 0x80, 99, 0x80, 8, 1, 1, 0, 0);
        var debug = new DebugData(OptionalInt.of(1), Optional.of(CdAssetBytes.copyOf(new byte[2048])), OptionalInt.of(0x123));
        var result = hud().run(before, input(new Globals(0x100, 0, 0, 0, 0x1234, 0xABCD, 0), debug));
        var expected = new HashMap<Integer, byte[]>(); int[] x = {1,2,3,4}, y = {10,11,12,13}, block = {2,9,1};
        for (int i = 0; i < 4; i++) { glyph(expected, 0xB0E0 + i * 64, x[i]); glyph(expected, 0xB260 + i * 64, y[i]); }
        for (int i = 0; i < 3; i++) glyph(expected, 0xB360 + i * 64, block[i]); glyph(expected, 0xB4A0, 8);
        image(expected, result.state().section().residency()); assertEquals(before.score(), result.state().score()); assertEquals(before.liveTime(), result.state().liveTime()); assertEquals(before.level(), result.state().level()); assertEquals(0x80, result.state().ring().updateHudRings()); assertEquals(List.of(NativeCall.DRAW_POS, NativeCall.DRAW_LIVES), result.calls());
        var shortDebug = new DebugData(OptionalInt.of(1), Optional.of(CdAssetBytes.copyOf(new byte[0x3FF])), OptionalInt.of(0));
        var stopped = assertThrows(UnsupportedHud.class, () -> hud().run(before, input(new Globals(1, 0, 0, 0, 0x7F22, 0xEFFE, 0), shortDebug)));
        assertEquals(Boundary.DEBUG_LAYOUT, stopped.boundary()); assertEquals(0x3FF, stopped.sourceOffset());
    }

    @Test void constructedAssetExtentsAreFormatAdmissionRatherThanClaimedNativeReadFaults() {
        assertThrows(IllegalArgumentException.class, () -> new Assets(CdAssetBytes.copyOf(new byte[1023]), views().resetTiles(), views().divisors()));
        assertThrows(IllegalArgumentException.class, () -> new Assets(views().numbers(), CdAssetBytes.copyOf(new byte[11]), views().divisors()));
        assertThrows(IllegalArgumentException.class, () -> new Assets(views().numbers(), views().resetTiles(), CdAssetBytes.copyOf(new byte[39])));
        assertThrows(NullPointerException.class, () -> new Assets(null, views().resetTiles(), views().divisors()));
        assertThrows(NullPointerException.class, () -> new CdLevelHud(views(), null));
        assertThrows(NullPointerException.class, () -> hud().run(null, input()));
    }

    @Test void publicValuesKeepTheirFiniteUnsignedWidthsAndUnknownDebugData() {
        assertThrows(IllegalArgumentException.class, () -> new BonusValues(65536, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BonusValues(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BonusValues(0, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> new Globals(65536, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Globals(0, 0, 256, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DebugData(OptionalInt.of(256), Optional.empty(), OptionalInt.empty()));
        assertThrows(IllegalArgumentException.class, () -> new DebugData(OptionalInt.empty(), Optional.empty(), OptionalInt.of(-1)));
        assertThrows(NullPointerException.class, () -> new Inputs(null, emptyDebug()));
        assertTrue(emptyDebug().savedFlagsHeadByte().isEmpty()); assertTrue(emptyDebug().layoutRam().isEmpty());
    }

    @Test void aForeignSectionOwnerRejectsAtItsFirstPatternCommitWithoutChangingTheInput() {
        var other = CdR11bSectionArt.fromAssets(nativeAssets);
        var source = state(0, 0, 0, 0, 2, 1, 0, 0, 0);
        var foreign = withSection(source, other.fromValues(CdAssetBytes.copyOf(new byte[512]), CdR11bCameraArtResidency.unknown(), 0));
        assertThrows(IllegalArgumentException.class, () -> hud().run(foreign, input()));
        image(Map.of(), foreign.section().residency()); assertEquals(1, foreign.ring().updateHudLives());
    }

    @Test void callsAreImmutableCompletedMetadataAndAReusedValueIsNotAnInvocationToken() {
        var before = state(0, 0, 0, 0, 2, 1, 0, 0, 0);
        var result = hud().run(before, input()); assertThrows(UnsupportedOperationException.class, () -> result.calls().add(NativeCall.DRAW_SCORE));
        var again = hud().run(result.state(), input()); assertTrue(again.calls().isEmpty()); assertSame(result.state(), again.state());
        assertEquals(1, before.ring().updateHudLives()); image(Map.of(), before.section().residency());
        var list = new ArrayList<NativeCall>(); list.add(NativeCall.DRAW_LIVES); var copied = new Result(result.state(), list); list.clear(); assertEquals(List.of(NativeCall.DRAW_LIVES), copied.calls());
    }

    @Test void authenticatedFactoryUsesOnlyTheThreeExistingRawViews() {
        var actual = Assets.fromAssets(nativeAssets);
        assertArrayEquals(nativeAssets.view(CdR11bAssets.Asset.ART_HUD_NUMBERS).bytes().copy(), actual.numbers().copy());
        assertArrayEquals(nativeAssets.view(CdR11bAssets.Asset.HUD_RESET_TILES).bytes().copy(), actual.resetTiles().copy());
        assertArrayEquals(nativeAssets.view(CdR11bAssets.Asset.HUD_DIVISORS).bytes().copy(), actual.divisors().copy());
        assertEquals(1024, actual.numbers().length()); assertEquals(12, actual.resetTiles().length()); assertEquals(40, actual.divisors().length());
    }

    private static CdLevelHud hud() { return new CdLevelHud(views(), art); }
    private static Assets views() {
        byte[] font = new byte[1024]; for (int i = 0; i < font.length; i++) font[i] = (byte) (i / 32 + 1);
        byte[] reset = {(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,0,0,(byte)255,(byte)255,0,0};
        var divisors = ByteBuffer.allocate(40); for (int n : new int[]{100000,10000,1000,100,10,1,4096,256,16,1}) divisors.putInt(n);
        return new Assets(CdAssetBytes.copyOf(font), CdAssetBytes.copyOf(reset), CdAssetBytes.copyOf(divisors.array()));
    }
    private static State state(int score, int sf, int rings, int rf, int lives, int lf, int enabled, int time, int mask) {
        byte[] raw = ByteBuffer.allocate(8).putInt(time).putInt(0x12345678).array();
        byte[] table = new byte[512]; Arrays.fill(table, (byte)0x5A);
        return new State(art.fromValues(CdAssetBytes.copyOf(table), CdR11bCameraArtResidency.unknown(), 0xA55A),
                new CdRingGlobals(rings,lives,0xA5,rf,lf,0xBEEF,0xD2,3),
                new CdLevelGlobals(0x1234,enabled,0x56,0x78,0x9A,0xBCDEFA,0xDC,0xEE,0x65),
                new CdOpeningObjectRuntime.ScoreGlobals(score,0xA1B2C3D4,sf),
                CdCheckpointLiveAux.declaredDevIngress(CdAssetBytes.copyOf(raw),mask), Optional.of(new BonusValues(0,0,0)));
    }
    private static State withSection(State s, CdR11bSectionArt.State next) { return new State(next,s.ring(),s.level(),s.score(),s.liveTime(),s.bonus()); }
    private static State withBonus(State s, Optional<BonusValues> next) { return new State(s.section(),s.ring(),s.level(),s.score(),s.liveTime(),next); }
    private static Inputs input() { return input(new Globals(0,0,0,0,0x1234,0x2345,0),emptyDebug()); }
    private static Inputs input(Globals globals, DebugData debug) { return new Inputs(globals,debug); }
    private static DebugData emptyDebug() { return new DebugData(OptionalInt.empty(),Optional.empty(),OptionalInt.empty()); }
    private static int hex(String s) { return Integer.parseUnsignedInt(s,16); }
    private static byte[] filled(int value) { byte[] b=new byte[32];Arrays.fill(b,(byte)value);return b; }
    private static void glyph(Map<Integer,byte[]> image,int destination,int digit) { image.put(destination,filled(2*digit+1));image.put(destination+32,filled(2*digit+2)); }
    private static void blank(Map<Integer,byte[]> image,int destination) { image.put(destination,new byte[32]);image.put(destination+32,new byte[32]); }
    private static CdR11bSectionArt.State paint(CdR11bSectionArt.State section,Map<Integer,byte[]> image) {
        var current=section;for(var e:image.entrySet())current=art.writeCurrentTile(current,e.getKey(),CdAssetBytes.copyOf(e.getValue()));return current;
    }
    private static void tile(CdR11bSectionArt.State section,int destination,int halfValue) { assertArrayEquals(filled(halfValue),CdR11bCameraArtResidency.readTile(section.residency(),destination>>>5).copy()); }
    private static void image(Map<Integer,byte[]> expected,CdR11bCameraArtResidency.State actual) {
        for(var region:CdR11bCameraArtResidency.Region.values())for(int at=0;at<region.byteLength();at++) {
            int address=region.destinationWord()+at;int tile=address&~31;byte[] bytes=expected.get(tile);var value=actual.region(region).knownByte(at);
            if(bytes==null)assertTrue(value.isEmpty(),"Unexpected known byte at "+Integer.toHexString(address));
            else assertEquals(Byte.toUnsignedInt(bytes[address&31]),value.orElseThrow(),"Byte at "+Integer.toHexString(address));
        }
    }
    private static void queue(CdR11bSectionArt.State before,CdR11bSectionArt.State after) {
        assertEquals(before.sectionWord(),after.sectionWord());assertSame(before.queue(),after.queue());assertSame(before.codeTable(),after.codeTable());assertSame(before.decoder().orElse(null),after.decoder().orElse(null));assertEquals(before.savedDecoder(),after.savedDecoder());assertEquals(before.remainingTilesWord(),after.remainingTilesWord());assertEquals(before.processBudgetWord(),after.processBudgetWord());assertEquals(before.hintWord(),after.hintWord());assertEquals(before.padWord(),after.padWord());assertEquals(before.stopped(),after.stopped());
    }
    private static void water(CdCheckpointLiveAux.State before,CdCheckpointLiveAux.State after) {
        for(int address:new int[]{0xFFF648,0xFFF649,0xFFF64D,0xFFF64E})assertEquals(before.knownByte(address),after.knownByte(address));
        assertEquals(before.knownMask()&0xF0,after.knownMask()&0xF0);
    }
}
