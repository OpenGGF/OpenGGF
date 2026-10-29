package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11bSectionArt;
import com.openggf.level.Pattern;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original-USA first selector8, HUD-before-Finish and pre-placement boundary.
 * Native pin1ecc5f3a: Level/Main.asm:821-847,333-403; HUD and Points/Main.asm:186-353.
 * The renderer-only contrast never changes Frame art or supplies a native DPLC request.
 * No new GPU pixels, native timing/audio, ordinary object pass or regional parity is claimed.
 */
@Isolated
class TestSonicCdPastFirstLevelService {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualFirstPastServiceWritesTheCompleteNormalHudBeforeFinishAndStopsBeforePlacement() throws Exception {
        var cut = enterPast(); var h = cut.harness(); var m = h.module();
        var frame = m.requirePlayerFrame(); var visit = actualField(m, "pastLoading");
        var before = m.pastLoadState(); var beforeValues = values(visit);
        var actor = frame.captureActor(); var shared = frame.captureShared(); var environment = frame.environment();
        var scroll = (SonicCdPlayerPresentation.ScrollCapture) m.getScrollHandlerProvider().getHandler(0).captureRewindState();
        var presentation = actualField(m, "playerPresentation");
        var renderer = renderer(visit); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        int oldTime = time(beforeValues); int expectedTime = tick(oldTime);
        var score = assertInstanceOf(CdOpeningObjectRuntime.ScoreGlobals.class, actualField(actualField(beforeValues, "globals"), "score"));
        assertEquals(0, environment.globals().debugCheat(), "This actual fixture must take normal HUD");
        assertEquals(0, environment.controls().controlLocked()); assertFalse(shared.paused());
        assertEquals(0, environment.movement().globals().boredTimer()); assertEquals(0, environment.globals().timeWarpTimer());
        assertEquals(0x80, score.updateHudScore()); assertEquals(0x80, before.ring().updateHudRings());
        assertEquals(1, before.ring().updateHudLives()); assertEquals(1, before.level().updateHudTime());
        assertEquals(44L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, environment.globals().levelFrames()); assertEquals(0, environment.movement().gates().timeZone());
        assertEquals(1, actor.state().header().slotId()); assertEquals(0, actor.state().motion().routine());
        assertEquals(0x78, actor.state().timers().hurt());
        assertEquals(0, actor.state().animation().mapFrame()); assertEquals(0, shared.artState().lastFrame());
        assertFalse(shared.artState().dirty());
        assertArrayEquals(new byte[0x300], shared.artState().residentBank().copy());
        assertBank(renderer, shared.artState());
        var stopped = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class, () -> step(h, route(cut.returned() + 1)));
        assertEquals(SonicCdGameModule.PastLoadBoundary.PLACEMENT, stopped.boundary());
        assertTrue(frame.closed());
        assertEquals(45L, frame.vIntClock().absoluteValue().orElseThrow(), "HUD returned before exactly one Finish");
        assertEquals(actor.state(), frame.state(), "LoadPlayer has not run ObjSonic_Init or any Body");
        assertEquals(expectedEnvironment(environment, new CdPlayerBody.Controls(8, 0, 0, 0, 0), 1), frame.environment());
        assertEquals(shared.artState(), frame.artState(), "Equal map0/last0 and clean art issues no DPLC/DMA");
        assertBank(renderer, frame.artState());
        var afterValues = values(visit);
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, actualField(afterValues, "phase"));
        var afterSection = assertInstanceOf(CdR11bSectionArt.State.class, actualField(afterValues, "section"));
        var afterRing = assertInstanceOf(CdRingGlobals.class, actualField(afterValues, "ring"));
        assertEquals(new CdRingGlobals(Math.min(before.ring().rings(), 999), before.ring().lives(), before.ring().livesFlags(),
                0, 0, before.ring().timeStopTimer(), before.ring().ringAnimationTimer(), before.ring().ringAnimationFrame()), afterRing);
        assertEquals(before.level(), actualField(afterValues, "level"));
        assertLiveUpdate(live(beforeValues), live(afterValues), expectedTime);
        var afterScore = assertInstanceOf(CdOpeningObjectRuntime.ScoreGlobals.class, actualField(actualField(afterValues, "globals"), "score"));
        assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0), afterScore);
        assertSectionMetadata(before.section(), afterSection);
        assertHudAndOtherCurrent(before.assets(), before.section(), afterSection, score.score(), afterRing, expectedTime);
        assertRetainedGlobals(actualField(beforeValues, "globals"), actualField(afterValues, "globals"));
        for (String field : new String[]{"cpuPalette", "fadePalette", "cpuSat", "hudObjects"})
            assertArrayEquals(assertInstanceOf(CdAssetBytes.class, actualField(beforeValues, field)).copy(),
                    assertInstanceOf(CdAssetBytes.class, actualField(afterValues, field)).copy(), field);
        assertArrayEquals(before.cpuPalette().copy(), assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "displayedPalette")).copy());
        assertArrayEquals(assertInstanceOf(CdAssetBytes.class, actualField(beforeValues, "cpuSat")).copy(),
                assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "displayedSat")).copy());
        byte[] selectedScroll = assertInstanceOf(CdAssetBytes.class, actualField(beforeValues, "displayedHScroll")).copy();
        System.arraycopy(scroll.background().hscrollRam().copy(), 0, selectedScroll, 0, 0x380);
        assertArrayEquals(selectedScroll, assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "displayedHScroll")).copy());
        assertEquals(scroll.background().vscroll(), actualField(afterValues, "displayedVScrollLongBits"));
        assertEquals(scroll.state(), actualField(presentation, "scrollState"), "DrawLevel consumes copies, not live foreground arithmetic");
        assertEquals(scroll.background(), actualField(presentation, "backgroundState"), "No LevelScroll/cloud/deformation step");
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty()); assertNull(h.mode().getLevelManager().getLevelGamestate());
        assertSame(world, h.mode().getWorldSession()); assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertEquals(0, h.mode().getLevelManager().getFrameCounter());
        System.out.println("PAST_FIRST_LEVEL boundary=PLACEMENT attempted=" + (cut.returned() + 1)
                + " clock=" + frame.vIntClock().absoluteValue().orElseThrow() + " levelFrames=" + frame.environment().globals().levelFrames()
                + " timeBefore=" + Integer.toUnsignedString(oldTime) + " timeAfter=" + Integer.toUnsignedString(time(afterValues))
                + " rings=" + afterRing.rings() + " lives=" + afterRing.lives() + " score=" + Integer.toUnsignedString(afterScore.score()));
    }

    @Test void actualPastResidentBankReprojectsAllTwentyFourTilesOnFullAndCourseRestoreWithoutSourceWork() throws Exception {
        var cut = enterPast(); var h = cut.harness(); var m = h.module(); var frame = m.requirePlayerFrame();
        var visit = actualField(m, "pastLoading"); var renderer = renderer(visit); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var nativeBefore = captureNative(h);
        var art = frame.artState(); var clock = frame.vIntClock();
        assertBank(renderer, art);
        dirtyBank(renderer); assertEquals(10, PlayerResidentBanks.capture(renderer).tiles()[0].getPixel(0, 0));
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertBank(renderer, art);
        assertEquals(clock, frame.vIntClock()); assertEquals(art, frame.artState());
        dirtyBank(renderer); assertEquals(10, PlayerResidentBanks.capture(renderer).tiles()[23].getPixel(7, 7));
        registry.restoreCourse(course); assertNativeCapture(nativeBefore, h); assertBank(renderer, art);
        assertEquals(clock, frame.vIntClock()); assertEquals(art, frame.artState());
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, m.pastLoadState().phase());
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty());
    }

    @Test void actualStartPausesOnlyAfterTheFirstHudAndFinishThenPausedRewindPreservesTimeUntilUnpause() throws Exception {
        var cut = enterPast(); var h = cut.harness(); var m = h.module(); var frame = m.requirePlayerFrame();
        var visit = actualField(m, "pastLoading"); int oldTime = time(values(visit));
        var oldActor = frame.captureActor(); var registry = h.mode().getRewindRegistry();
        step(h, input(true));
        assertFalse(frame.closed()); assertTrue(frame.paused());
        assertEquals(OptionalInt.of(0x80), actualField(actualField(values(visit), "globals"), "pauseMusicBit"));
        assertEquals(java.util.List.of(new SonicCdGameModule.PastFrameSound(new com.openggf.game.soniccd.player.CdPlayerFrameRuntime.ChargeSound(
                        com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP)),
                new SonicCdGameModule.PastSubCpuSound(SonicCdGameModule.PastMusicCommand.SCMD_PAUSEPCM)), actualField(visit, "sounds"));
        assertEquals(45L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames(), "Paused Main does not reach its count or placement");
        assertEquals(tick(oldTime), time(values(visit)), "HUD sampled the old unpaused word before Start toggled pause");
        assertEquals(new CdPlayerBody.Controls(0x80, 0x80, 0, 0, 0), frame.environment().controls());
        assertEquals(oldActor, frame.captureActor());
        var before = captureNative(h); var full = registry.capture(); var course = registry.captureCourse();
        int pausedTime = time(values(visit));
        step(h, input(false));
        assertTrue(frame.paused()); assertFalse(frame.closed());
        assertEquals(OptionalInt.of(0x80), actualField(actualField(values(visit), "globals"), "pauseMusicBit"));
        assertEquals(java.util.List.of(new SonicCdGameModule.PastFrameSound(new com.openggf.game.soniccd.player.CdPlayerFrameRuntime.ChargeSound(
                com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP))), actualField(visit, "sounds"));
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(pausedTime, time(values(visit)), "Old paused word suppresses HUD timer increment");
        assertEquals(0, frame.environment().globals().levelFrames());
        assertEquals(new CdPlayerBody.Controls(0, 0, 0, 0, 0), frame.environment().controls());
        var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h);
        step(h, input(false)); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h);
        step(h, input(false)); assertNativeCapture(after, h);
        var stopped = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class, () -> step(h, input(true)));
        assertEquals(SonicCdGameModule.PastLoadBoundary.PLACEMENT, stopped.boundary());
        assertTrue(frame.closed()); assertFalse(frame.paused());
        assertEquals(OptionalInt.of(0), actualField(actualField(values(visit), "globals"), "pauseMusicBit"));
        assertEquals(java.util.List.of(new SonicCdGameModule.PastSubCpuSound(
                SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM)), actualField(visit, "sounds"));
        assertEquals(47L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(1, frame.environment().globals().levelFrames());
        assertEquals(pausedTime, time(values(visit)), "Unpause service still used the old paused word for HUD");
        assertEquals(oldActor.state(), frame.state());
        assertEquals(new CdPlayerBody.Controls(0x80, 0x80, 0, 0, 0), frame.environment().controls());
    }

    private record PastCut(Harness harness, int returned) { }
    private static PastCut enterPast() throws Exception {
        var h = opening();
        for (int call = 1; call < 7800; call++) {
            step(h, route(call));
            if (h.module().pastLoadingSelected() && h.module().pastLoadState().phase() == SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL) {
                assertEquals(44L, h.module().requirePlayerFrame().vIntClock().absoluteValue().orElseThrow());
                return new PastCut(h, call);
            }
        }
        fail("The unchanged bounded five-jump route must complete actual Past loading");
        throw new AssertionError();
    }
    private static Object values(Object visit) throws ReflectiveOperationException { return actualField(visit, "values"); }
    private static CdCheckpointLiveAux.State live(Object values) throws ReflectiveOperationException {
        return assertInstanceOf(CdCheckpointLiveAux.State.class, actualField(actualField(values, "globals"), "live"));
    }
    private static int time(Object values) throws ReflectiveOperationException { return CdCheckpointLiveAux.read(live(values), CdCheckpointLiveAux.Field.TIME_LONG); }
    private static int tick(int time) {
        assertNotEquals(0x00093B3B, time, "This actual fixture must not select the unowned max-time Kill branch");
        int minutes = (time >>> 16) & 0xFF, seconds = (time >>> 8) & 0xFF, frames = ((time & 0xFF) + 1) & 0xFF;
        if (frames >= 60) {
            frames = 0; seconds = (seconds + 1) & 0xFF;
            if (seconds >= 60) { seconds = 0; minutes = Math.min((minutes + 1) & 0xFF, 9); }
        }
        return (time & 0xFF000000) | (minutes << 16) | (seconds << 8) | frames;
    }
    private static LogicalInputSnapshot input(boolean start) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, start, start), PlayerInputState.neutral());
    }
    private static PlayerSpriteRenderer renderer(Object visit) throws ReflectiveOperationException {
        return assertInstanceOf(PlayerSpriteRenderer.class, actualField(visit, "playerRenderer"));
    }
    private static void assertBank(PlayerSpriteRenderer renderer, CdPlayerArtRuntime.State art) {
        var tiles = PlayerResidentBanks.capture(renderer).tiles(); assertEquals(24, tiles.length);
        for (int tile = 0; tile < 24; tile++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int packed = art.residentBank().unsignedByte(tile * 32 + y * 4 + x / 2);
            int expected = (packed >>> ((x & 1) == 0 ? 4 : 0)) & 15;
            assertEquals(expected, tiles[tile].getPixel(x, y), "Resident tile" + tile + " pixel" + x + "," + y);
        }
    }
    private static void dirtyBank(PlayerSpriteRenderer renderer) {
        var tiles = new Pattern[24]; byte[] dirty = new byte[32]; Arrays.fill(dirty, (byte) 0xAA);
        for (int i = 0; i < 24; i++) { tiles[i] = new Pattern(); tiles[i].fromSegaFormat(dirty); }
        PlayerResidentBanks.restore(renderer, new PlayerResidentBanks.Snapshot(tiles, -1));
    }
    private static void assertSectionMetadata(CdR11bSectionArt.State before, CdR11bSectionArt.State after) {
        assertEquals(before.sectionWord(), after.sectionWord()); assertEquals(before.queue(), after.queue());
        assertArrayEquals(before.codeTable().copy(), after.codeTable().copy()); assertEquals(before.decoder(), after.decoder());
        assertEquals(before.savedDecoder(), after.savedDecoder()); assertEquals(before.remainingTilesWord(), after.remainingTilesWord());
        assertEquals(before.processBudgetWord(), after.processBudgetWord()); assertEquals(before.hintWord(), after.hintWord());
        assertEquals(before.padWord(), after.padWord()); assertEquals(before.stopped(), after.stopped());
    }
    private static void assertHudAndOtherCurrent(CdR11bAssets assets, CdR11bSectionArt.State before,
                                               CdR11bSectionArt.State after, int score, CdRingGlobals ring, int time) {
        var font = assets.view(CdR11bAssets.Asset.ART_HUD_NUMBERS).bytes();
        byte[] expected = new byte[0x3C0];
        putGlyph(expected, 6 * 64, font, 0); // Actual seventh reset score slot, outside its six drawn digits.
        putCounter(expected, 0, 6, Integer.toUnsignedLong(score), font, true);
        putGlyph(expected, 0x300 + 2 * 64, font, 0);
        putCounter(expected, 0x300, 3, ring.rings(), font, true);
        putCounter(expected, 0x1C0, 1, (time >>> 16) & 255, font, false);
        putCounter(expected, 0x200, 2, (time >>> 8) & 255, font, false);
        int centis = time == 0x00093B3B ? 99 : (time & 255) * 100 / 60;
        putCounter(expected, 0x280, 2, centis, font, false);
        int counted = 0;
        for (var region : CdR11bCameraArtResidency.Region.values()) {
            if (region != CdR11bCameraArtResidency.Region.AT_B060 && region != CdR11bCameraArtResidency.Region.AT_B4A0) {
                assertEquals(before.residency().region(region), after.residency().region(region), "Unwritten current bytes/mask" + region);
                continue;
            }
            byte[] bytes = region == CdR11bCameraArtResidency.Region.AT_B060 ? expected : font.slice(Math.min(ring.lives(), 9) * 64, 64).copy();
            for (int offset = 0; offset < region.byteLength(); offset++)
                assertEquals(OptionalInt.of(Byte.toUnsignedInt(bytes[offset])), after.residency().region(region).knownByte(offset), region + "/" + offset);
            for (int offset = 0; offset < bytes.length; offset += 32) {
                assertArrayEquals(Arrays.copyOfRange(bytes, offset, offset + 32),
                        CdR11bCameraArtResidency.readTile(after.residency(), (region.destinationWord() + offset) >>> 5).copy()); counted++;
            }
        }
        assertEquals(32, counted);
    }
    private static void putCounter(byte[] destination, int offset, int digits, long value, CdAssetBytes font, boolean suppressLeading) {
        long divisor = 1;
        for (int i = 1; i < digits; i++) divisor *= 10;
        boolean found = false;
        for (int digit = 0; digit < digits; digit++, divisor /= 10) {
            int n = (int) (value / divisor); value %= divisor;
            if (n != 0) found = true;
            if (!suppressLeading || found) putGlyph(destination, offset + digit * 64, font, n);
        }
    }
    private static void putGlyph(byte[] destination, int offset, CdAssetBytes font, int digit) {
        assertTrue(digit >= 0 && digit < 16, "This source-selected digit must fit the authenticated font");
        System.arraycopy(font.slice(digit * 64, 64).copy(), 0, destination, offset, 64);
    }
    private static void assertLiveUpdate(CdCheckpointLiveAux.State before, CdCheckpointLiveAux.State after, int expectedTime) {
        assertEquals(before.knownMask(), after.knownMask());
        for (int offset = 0; offset < 4; offset++)
            assertEquals(OptionalInt.of((expectedTime >>> (24 - 8 * offset)) & 255), after.knownByte(0xFF1514 + offset));
        for (int address : new int[]{0xFFF648, 0xFFF649, 0xFFF64D, 0xFFF64E})
            assertEquals(before.knownByte(address), after.knownByte(address), "HUD does not alter live water");
    }
    private static void assertRetainedGlobals(Object before, Object after) throws ReflectiveOperationException {
        for (String field : new String[]{"contact", "drownTimerWord", "tail", "bonus", "debugBlockWord"})
            assertEquals(actualField(before, field), actualField(after, field), field);
        var oldLayout = assertInstanceOf(Optional.class, actualField(before, "layoutRam")).orElseThrow();
        var newLayout = assertInstanceOf(Optional.class, actualField(after, "layoutRam")).orElseThrow();
        assertArrayEquals(assertInstanceOf(CdAssetBytes.class, oldLayout).copy(), assertInstanceOf(CdAssetBytes.class, newLayout).copy());
        assertEquals(2048, assertInstanceOf(CdAssetBytes.class, oldLayout).length());
        assertEquals(OptionalInt.of(0), actualField(before, "debugBlockWord"));
        var bonus = assertInstanceOf(Optional.class, actualField(before, "bonus")).orElseThrow();
        assertEquals(0, actualField(bonus, "timeBonusWord")); assertEquals(0, actualField(bonus, "ringBonusWord"));
        assertEquals(0, actualField(bonus, "updateHudBonusByte"));
    }
    private static CdPlayerBody.Environment expectedEnvironment(CdPlayerBody.Environment before, CdPlayerBody.Controls controls, int levelFrames) {
        var g = before.globals();
        return new CdPlayerBody.Environment(before.movement(), controls,
                new CdPlayerBody.Globals(levelFrames, g.cameraXCentre(), g.timeAttackMode(), g.debugMode(), g.debugCheat(),
                        g.timeWarpTimer(), g.invincible(), g.bossMusic()), before.dynamicSlots());
    }
}
