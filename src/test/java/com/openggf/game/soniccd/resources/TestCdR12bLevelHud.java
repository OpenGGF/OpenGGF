package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bDirectArtSources;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.resources.CdLevelHud.*;
import static org.junit.jupiter.api.Assertions.*;

/** ALL JAVA UNRUN. Actual-USA raw HUD data plus explicit component values, not a World/service/clock.
 * N1ecc5f3a HUD and Points/Main.asm:186-717. Expected full physical images use literal native cells
 * and genuine source glyph halves. Constructed bad reset/divisor bytes are format-prefix witnesses,
 * not retail mutation ingress, art preload, Frame publication, natural entry, route or pixel evidence. */
class TestCdR12bLevelHud {
    private static CdR12bTerrainAssets assets;
    private static CdR12bSectionArt art;
    private static Assets views;
    private static final CdR11aSectionArt.SavedDecoder SAVED = new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.XOR, 0x1234, 10, 0x89ABCDEF, 0x5678, 0x9ABC);

    @BeforeAll static void originalUsaHudSourceAfterCueClose() throws Exception {
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure original CD media for selected HUD checks"); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) { var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst(); }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) { assets = CdR12bTerrainAssets.fromPrograms(source); }
        art = CdR12bSectionArt.fromAssets(assets); views = assets.directArtSources().hudAssets();
    }

    @Test void actualRetainedNumbersResetAndDivisorsAreTheOnlyBorrowedHudAssets() throws Exception {
        assertSame(views, assets.directArtSources().hudAssets());
        assertSame(assets.directArtSources().view(CdR12bDirectArtSources.View.HUD_NUMBERS), views.numbers());
        assertSame(assets.directArtSources().view(CdR12bDirectArtSources.View.HUD_RESET_TILES), views.resetTiles());
        assertSame(assets.directArtSources().view(CdR12bDirectArtSources.View.HUD_DIVISORS), views.divisors());
        assertEquals("843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42", sha(views.numbers()));
        assertEquals("3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c", sha(views.resetTiles()));
        assertEquals("7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea", sha(views.divisors()));
        assertEquals("ffffffffffff0000ffff0000", HexFormat.of().formatHex(views.resetTiles().copy()));
        assertEquals(1024, views.numbers().length()); assertEquals(12, views.resetTiles().length()); assertEquals(40, views.divisors().length());
    }

    @Test void realFontFirstResetScoreRingsAndTimeKeepTheWholeActualActiveSectionContinuation() {
        var initial = state(1234, 0x80, 1001, 0x80, 12, 1, 1, 0xA8023B3B, 0xFF);
        var before = withSection(initial, art.prepare(art.initList(initial.section(), 3))); var result = hud().run(before, input()); var after = result.state();
        var expected = new HashMap<Integer, byte[]>(); for (int i = 0; i < 6; i++) blank(expected, 0xB060 + i * 64);
        glyph(expected, 0xB1E0, 0); for (int i = 0; i < 4; i++) glyph(expected, 0xB0E0 + i * 64, i + 1);
        for (int i = 0; i < 3; i++) glyph(expected, 0xB360 + i * 64, 9);
        glyph(expected, 0xB220, 3); glyph(expected, 0xB260, 0); glyph(expected, 0xB2A0, 0); glyph(expected, 0xB2E0, 0); glyph(expected, 0xB320, 0); glyph(expected, 0xB4A0, 9);
        image(expected, after.section().residency()); queue(before.section(), after.section());
        assertEquals(999, after.ring().rings()); assertEquals(12, after.ring().lives()); assertEquals(0, after.ring().updateHudLives()); assertEquals(0, after.ring().updateHudRings());
        assertEquals(0, after.score().updateHudScore()); assertEquals(before.score().score(), after.score().score()); assertEquals(before.score().nextLifeScore(), after.score().nextLifeScore());
        assertEquals(0xA8030000, CdCheckpointLiveAux.read(after.liveTime(), CdCheckpointLiveAux.Field.TIME_LONG)); water(before.liveTime(), after.liveTime());
        assertSame(before.level(), after.level()); assertEquals(before.ring().livesFlags(), after.ring().livesFlags()); assertEquals(before.ring().timeStopTimer(), after.ring().timeStopTimer());
        assertEquals(List.of(NativeCall.DRAW_LIVES, NativeCall.RESET_SCORE, NativeCall.DRAW_SCORE, NativeCall.RESET_RINGS, NativeCall.DRAW_RINGS,
                NativeCall.DRAW_MINS, NativeCall.DRAW_SECS, NativeCall.DRAW_SECS, NativeCall.DRAW_LIVES), result.calls());
    }

    @Test void positiveZeroCountersClearTheirFlagsButPreserveEveryOldUnknownOrKnownLeadingCell() {
        var before = state(0, 1, 0, 1, 3, 0, 0, 0, 0xF0); var result = hud().run(before, input());
        assertSame(before.section(), result.state().section()); image(Map.of(), result.state().section().residency());
        assertEquals(0, result.state().score().updateHudScore()); assertEquals(0, result.state().ring().updateHudRings()); assertSame(before.liveTime(), result.state().liveTime());
        assertEquals(List.of(NativeCall.DRAW_SCORE, NativeCall.DRAW_RINGS), result.calls());
    }

    @Test void nativeTimeCarryDrawsLiteralMinuteSecondsCentisecondsAndPreservesLeadingAndWaterBytes() {
        var before = state(0, 0, 0, 0, 1, 0, 1, 0xA8023B3B, 0xFF); var result = hud().run(before, input());
        assertEquals(0xA8030000, CdCheckpointLiveAux.read(result.state().liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB220, 3); glyph(expected, 0xB260, 0); glyph(expected, 0xB2A0, 0); glyph(expected, 0xB2E0, 0); glyph(expected, 0xB320, 0);
        image(expected, result.state().section().residency()); water(before.liveTime(), result.state().liveTime()); queue(before.section(), result.state().section());
        assertSame(before.ring(), result.state().ring()); assertSame(before.level(), result.state().level()); assertSame(before.score(), result.state().score());
        assertEquals(List.of(NativeCall.DRAW_MINS, NativeCall.DRAW_SECS, NativeCall.DRAW_SECS), result.calls());
    }

    @Test void unknownReachedTimeCarriesAct2ScoreAndRingPrefixAndTheOriginalUnknownByteCause() {
        var before = state(1, 1, 7, 1, 2, 1, 1, 0xAA010203, 0xA7);
        var stopped = assertThrows(UnsupportedAct2PastHud.class, () -> hud().run(before, input()));
        assertEquals(Boundary.LIVE_TIME, stopped.boundary()); assertEquals(0xFF1517, stopped.sourceOffset()); assertEquals(-1, stopped.destinationWord());
        var cause = assertInstanceOf(CdCheckpointLiveAux.UnknownLiveByte.class, stopped.getCause()); assertEquals(0xFF1517, cause.nativeAddress());
        assertSame(before.liveTime(), stopped.state().liveTime()); assertEquals(0, stopped.state().score().updateHudScore()); assertEquals(0, stopped.state().ring().updateHudRings()); assertEquals(1, stopped.state().ring().updateHudLives());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB1A0, 1); glyph(expected, 0xB3E0, 7); image(expected, stopped.state().section().residency());
        queue(before.section(), stopped.state().section()); water(before.liveTime(), stopped.state().liveTime());
        assertEquals(List.of(NativeCall.DRAW_SCORE, NativeCall.DRAW_RINGS), stopped.calls()); assertThrows(UnsupportedOperationException.class, () -> stopped.calls().clear());
    }

    @Test void secondFontHalfFaultCarriesTheEarlierLivesAndFirstRawTileBeforeAnyLaterFlagClear() {
        byte[] reset = views.resetTiles().copy(); reset[0] = 31; var owner = new Act2Past(new Assets(views.numbers(), CdAssetBytes.copyOf(reset), views.divisors()), art);
        var before = state(0, 0x80, 7, 0x80, 2, 1, 1, 0, 0xFF);
        var stopped = assertThrows(UnsupportedAct2PastHud.class, () -> owner.run(before, input()));
        assertEquals(Boundary.FONT_ADDRESS, stopped.boundary()); assertEquals(1024, stopped.sourceOffset()); assertEquals(0xB080, stopped.destinationWord());
        assertEquals(0x80, stopped.state().score().updateHudScore()); assertSame(before.ring(), stopped.state().ring()); assertSame(before.liveTime(), stopped.state().liveTime());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB4A0, 2); expected.put(0xB060, views.numbers().slice(992, 32).copy()); image(expected, stopped.state().section().residency());
        queue(before.section(), stopped.state().section()); assertEquals(List.of(NativeCall.DRAW_LIVES), stopped.calls());
    }

    @Test void zeroDivisorStopsAfterTheExactScoreFlagClearWithoutMutatingLaterOwners() {
        byte[] divisors = views.divisors().copy(); ByteBuffer.wrap(divisors).putInt(0, 0); var owner = new Act2Past(new Assets(views.numbers(), views.resetTiles(), CdAssetBytes.copyOf(divisors)), art);
        var before = state(1, 1, 7, 1, 2, 1, 1, 0x01020304, 0xFF);
        var stopped = assertThrows(UnsupportedAct2PastHud.class, () -> owner.run(before, input()));
        assertEquals(Boundary.NON_RETURNING_DIGIT, stopped.boundary()); assertEquals(0, stopped.sourceOffset()); assertEquals(0xB060, stopped.destinationWord());
        assertEquals(0, stopped.state().score().updateHudScore()); assertSame(before.ring(), stopped.state().ring()); assertSame(before.liveTime(), stopped.state().liveTime());
        assertSame(before.section(), stopped.state().section()); assertTrue(stopped.calls().isEmpty());
    }

    @Test void maximumTimeStopCarriesOnlyItsNativeFlagAndWholeTimeClearWhileMarkedMaximumReadsNothingLater() {
        var before = withBonus(state(0, 0, 0, 0, 3, 1, 1, 0x00093B3B, 0xFF), Optional.empty());
        var stopped = assertThrows(UnsupportedAct2PastHud.class, () -> hud().run(before, input()));
        assertEquals(Boundary.MAX_TIME_KILL, stopped.boundary()); assertEquals(0, stopped.state().level().updateHudTime()); assertEquals(0, CdCheckpointLiveAux.read(stopped.state().liveTime(), CdCheckpointLiveAux.Field.TIME_LONG));
        assertSame(before.section(), stopped.state().section()); assertSame(before.ring(), stopped.state().ring()); assertTrue(stopped.calls().isEmpty()); water(before.liveTime(), stopped.state().liveTime());
        var active = hud().run(before, new Inputs(new Globals(0, 0, 0, 0x81, 0, 0, 1), debug())); assertSame(before, active.state()); assertTrue(active.calls().isEmpty());
    }

    @Test void actualAct2BonusCellsUseTheSharedBlankLeadingRuleAndClearOnlyTheExistingBonusFlag() {
        var before = withBonus(state(0, 0, 0, 0, 1, 0, 0, 0, 0xF0), Optional.of(new BonusValues(1234, 0, 0xFF))); var result = hud().run(before, input());
        var expected = new HashMap<Integer, byte[]>(); blank(expected, 0x8780); for (int i = 1; i < 5; i++) glyph(expected, 0x8780 + i * 64, i);
        for (int i = 0; i < 4; i++) blank(expected, 0x88C0 + i * 64); glyph(expected, 0x89C0, 0);
        image(expected, result.state().section().residency()); queue(before.section(), result.state().section());
        assertEquals(Optional.of(new BonusValues(1234, 0, 0)), result.state().bonus()); assertSame(before.ring(), result.state().ring()); assertSame(before.score(), result.state().score()); assertSame(before.liveTime(), result.state().liveTime());
        assertEquals(List.of(NativeCall.DRAW_BONUS, NativeCall.DRAW_BONUS), result.calls());
    }

    @Test void unknownBonusIsReadOnlyAfterTheReachedLivesFlagClearAndDraw() {
        var before = withBonus(state(0, 0, 0, 0, 3, 1, 0, 0, 0xF0), Optional.empty()); var stopped = assertThrows(UnsupportedAct2PastHud.class, () -> hud().run(before, input()));
        assertEquals(Boundary.BONUS_VALUES, stopped.boundary()); assertEquals(0xFFF7D6, stopped.sourceOffset()); assertEquals(0, stopped.state().ring().updateHudLives());
        var expected = new HashMap<Integer, byte[]>(); glyph(expected, 0xB4A0, 3); image(expected, stopped.state().section().residency()); assertSame(before.liveTime(), stopped.state().liveTime());
        assertEquals(List.of(NativeCall.DRAW_LIVES), stopped.calls());
    }

    @Test void debugKeepsPositionGlyphsBeforeItsFirstMissingOverwrittenReadWithoutNormalCounterChanges() {
        var before = state(123, 0x80, 99, 0x80, 1, 1, 1, 0, 0xF0);
        var stopped = assertThrows(UnsupportedAct2PastHud.class, () -> hud().run(before, new Inputs(new Globals(0x100, 0, 0, 1, 0x1234, 0x2345, 1), debug())));
        assertEquals(Boundary.DEBUG_SAVED_FLAGS, stopped.boundary()); assertEquals(0xFF1200, stopped.sourceOffset());
        var expected = new HashMap<Integer, byte[]>(); int[] x = {1, 2, 3, 4}, y = {2, 3, 4, 5};
        for (int i = 0; i < 4; i++) { glyph(expected, 0xB0E0 + i * 64, x[i]); glyph(expected, 0xB260 + i * 64, y[i]); }
        image(expected, stopped.state().section().residency()); assertSame(before.ring(), stopped.state().ring()); assertSame(before.score(), stopped.state().score()); assertSame(before.liveTime(), stopped.state().liveTime());
        assertEquals(List.of(NativeCall.DRAW_POS), stopped.calls());
    }

    @Test void wholePausedWordsSkipUnknownTimeAndTrueNoopResultsKeepTheActualInputReference() {
        var before = state(0, 0, 0, 0, 1, 0, 1, 0, 0xF0);
        for (int paused : new int[]{1, 0x100, 0x8000, 0xFFFF}) {
            var result = hud().run(before, new Inputs(new Globals(0, paused, 0, 1, 0, 0, 1), debug()));
            assertSame(before, result.state()); assertTrue(result.calls().isEmpty()); image(Map.of(), result.state().section().residency());
        }
    }

    @Test void sourceValuesRemainImmutableAndNullStoppedGuardsPrecedeAnyHudWrite() {
        var before = state(1, 1, 0, 0, 1, 0, 0, 0, 0xF0); var first = hud().run(before, input()); var second = hud().run(before, input());
        assertEquals(first, second); assertEquals(1, before.score().updateHudScore()); image(Map.of(), before.section().residency());
        var copied = views.numbers().copy(); Arrays.fill(copied, (byte) 0); assertEquals(first, hud().run(before, input()));
        assertThrows(NullPointerException.class, () -> new Act2Past(null, art)); assertThrows(NullPointerException.class, () -> new Act2Past(views, null));
        assertThrows(NullPointerException.class, () -> hud().run(null, input())); assertThrows(NullPointerException.class, () -> hud().run(before, null));
        var failed = assertThrows(CdR12bSectionArt.SectionArtException.class, () -> art.initList(before.section(), 0xFFFF)).state();
        assertThrows(IllegalStateException.class, () -> hud().run(withSection(before, failed), input()));
        assertThrows(UnsupportedOperationException.class, () -> first.calls().clear());
    }

    private static Act2Past hud() { return new Act2Past(views, art); }
    private static Act2PastState state(int score, int sf, int rings, int rf, int lives, int lf, int enabled, int time, int mask) {
        byte[] raw = ByteBuffer.allocate(8).putInt(time).putInt(0x12345678).array(), table = new byte[512]; Arrays.fill(table, (byte) 0x5A);
        return new Act2PastState(art.fromValues(CdAssetBytes.copyOf(table), CdR12bCameraArtResidency.unknown(), 0xA55A, CdR11aPlcBuffer.cleared(), Optional.empty(), SAVED, 0, 0x2345, 0x3456, 0x4567),
                new CdRingGlobals(rings, lives, 0xA5, rf, lf, 0xBEEF, 0xD2, 3), new CdLevelGlobals(0x1234, enabled, 0x56, 0x78, 0x9A, 0xBCDEFA, 0xDC, 0xEE, 0x65),
                new CdOpeningObjectRuntime.ScoreGlobals(score, 0xA1B2C3D4, sf), CdCheckpointLiveAux.declaredDevIngress(CdAssetBytes.copyOf(raw), mask), Optional.of(new BonusValues(0, 0, 0)));
    }
    private static Act2PastState withSection(Act2PastState s, CdR12bSectionArt.State section) { return new Act2PastState(section, s.ring(), s.level(), s.score(), s.liveTime(), s.bonus()); }
    private static Act2PastState withBonus(Act2PastState s, Optional<BonusValues> bonus) { return new Act2PastState(s.section(), s.ring(), s.level(), s.score(), s.liveTime(), bonus); }
    private static Inputs input() { return new Inputs(new Globals(0, 0, 0, 0, 0x1234, 0x2345, 1), debug()); }
    private static DebugData debug() { return new DebugData(OptionalInt.empty(), Optional.empty(), OptionalInt.empty()); }
    private static void glyph(Map<Integer, byte[]> image, int destination, int digit) { image.put(destination, views.numbers().slice(digit * 64, 32).copy()); image.put(destination + 32, views.numbers().slice(digit * 64 + 32, 32).copy()); }
    private static void blank(Map<Integer, byte[]> image, int destination) { image.put(destination, new byte[32]); image.put(destination + 32, new byte[32]); }
    private static void image(Map<Integer, byte[]> expected, CdR12bCameraArtResidency.State actual) { for (var r : CdR12bCameraArtResidency.Region.values()) for (int at = 0; at < r.byteLength(); at++) { int address = r.destinationWord() + at; var bytes = expected.get(address & ~31); var value = actual.region(r).knownByte(at); if (bytes == null) assertTrue(value.isEmpty(), "Unexpected knowledge at " + Integer.toHexString(address)); else assertEquals(Byte.toUnsignedInt(bytes[address & 31]), value.orElseThrow()); } }
    private static void queue(CdR12bSectionArt.State a, CdR12bSectionArt.State b) { assertEquals(a.sectionWord(), b.sectionWord()); assertSame(a.queue(), b.queue()); assertSame(a.codeTable(), b.codeTable()); assertSame(a.decoder().orElse(null), b.decoder().orElse(null)); assertSame(a.savedDecoder(), b.savedDecoder()); assertEquals(a.remainingTilesWord(), b.remainingTilesWord()); assertEquals(a.processBudgetWord(), b.processBudgetWord()); assertEquals(a.hintWord(), b.hintWord()); assertEquals(a.padWord(), b.padWord()); assertEquals(a.stopped(), b.stopped()); }
    private static void water(CdCheckpointLiveAux.State a, CdCheckpointLiveAux.State b) { for (int address : new int[]{0xFFF648, 0xFFF649, 0xFFF64D, 0xFFF64E}) assertEquals(a.knownByte(address), b.knownByte(address)); assertEquals(a.knownMask() & 0xF0, b.knownMask() & 0xF0); }
    private static String sha(CdAssetBytes bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copy())); }
}
