package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.CdCapturedValueAssertions.assertCapturedValueEquals;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Native source N1ecc5f3a LevelStart19-115,
 * General.ClearPLCs651-658, Misc.VSync45-52, VInterrupt682-724; IPX.RunMMD1372-1387.
 * Genuine cold/Past/8278 cut plus fixed Results object-only Next8 ingress. No actor/global,
 * palette/queue/art/clock/terrain seed, new-act owner, hardware handshake or pixel claim.
 * Marked entry BSET64 retains1 despite its old0 skip-white; the real later old1 selects22 black waits.
 * No synthetic flag/first-load/time-attack World is invented to cover other branches.
 */
@Isolated
class TestSonicCdPastOldProgrammeBlackExitWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void markedEntryRetainsSetBitAndActualOldOnePreludeWaitsOnlyAfterEveryPhysicalSlotKeepingPriorSat() throws Exception {
        var p = ordinary(); var m = p.harness().module();
        assertEquals(1, m.levelProgramState().palFadeFlags(), "Marked entry Main64 sets1 even when its old0 skips the white fade");
        assertTrue((m.pastLoadState().level().plcLoadFlags() & 1) != 0);
        var cpu = image(p, "cpuSat"); var completed = completed(p); var target = image(p, "fadePalette"); var water = image(p, "waterPalette");
        var later = fixed(p, 127).moveByte(ROUTINE, 4).moveByte(MAP_FRAME, 1).moveWord(X, 0x7FFC)
                .moveWord(RESULTS_DEST_X, 0x8004).moveWord(RESULTS_TIMER_WORD, 473).moveByte(FLAGS, 0x40);
        seed(p, fixed(p, 126).moveByte(ROUTINE, 8)); seed(p, later);
        long clock = p.frame().vIntClock().absoluteValue().orElseThrow(); int frames = p.frame().environment().globals().levelFrames();
        step(p.harness(), neutral()); healthyExit(p, SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE);
        assertEquals(later.moveWord(X, 0x7FF4).moveWord(RESULTS_TIMER_WORD, 472), p.placement().pool().state(127));
        assertEquals(2, calls(p).stream().filter(c -> c == SonicCdGameModule.PastLoadCall.RESULTS_BODY).count());
        int walk = calls(p).indexOf(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS);
        assertTrue(walk >= 0); assertEquals(SonicCdGameModule.PastLoadCall.OLD_EXIT_PRELUDE, calls(p).get(walk + 1));
        assertFalse(calls(p).contains(SonicCdGameModule.PastLoadCall.DRAW_OBJECTS));
        assertFalse(calls(p).contains(SonicCdGameModule.PastLoadCall.OLD_EXIT_FADE_SERVICE));
        assertFalse(calls(p).contains(SonicCdGameModule.PastLoadCall.UPDATE_SECTION));
        assertNull(actualField(p.visit(), "drawQueue")); assertSame(cpu, image(p, "cpuSat")); assertSame(completed, completed(p));
        assertSame(target, image(p, "fadePalette")); assertSame(water, image(p, "waterPalette"));
        assertFalse(Arrays.equals(new byte[128], image(p, "cpuPalette").copy()));
        assertArrayEquals(image(p, "cpuPalette").copy(), image(p, "displayedPalette").copy(), "No colour or ClearPal step precedes the first black VSync");
        var program = m.levelProgramState(); assertEquals(0, program.demoMode()); assertEquals(0, program.levelStarted());
        assertEquals(0, program.usePlayer2()); assertEquals(1, program.palFadeFlags()); assertEquals(0, program.whiteFadeSteps());
        assertEquals(2, m.pastLoadState().level().levelRestart()); assertEquals(clock + 1, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertEquals((frames + 1) & 0xFFFF, p.frame().environment().globals().levelFrames()); assertFalse(p.frame().paused());
    }

    @Test void fullNativeBlackLoopThenFinalSelectorCPublishesBlackWithOnlyExistingFinishClockCopyDrawAndFastPlcOwners() throws Exception {
        var p = entered(); blackWaits(p); var actor = p.frame().captureActor(); var placement = p.placement().capture();
        var before = p.harness().module().pastLoadState(); var cpu = image(p, "cpuSat"); var target = image(p, "fadePalette");
        var water = image(p, "waterPalette"); var section = before.section(); var globals = actualField(values(p), "globals");
        var presentation = presentation(p); var scroll = actualField(presentation, "scrollState"); var background = actualField(presentation, "backgroundState");
        long clock = p.frame().vIntClock().absoluteValue().orElseThrow(); int frames = p.frame().environment().globals().levelFrames();
        step(p.harness(), neutral()); healthyExit(p, SonicCdGameModule.PastLoadPhase.OLD_RETURNED);
        var after = p.harness().module().pastLoadState(); assertCapturedValueEquals(actor, p.frame().captureActor()); assertPlacement(placement, p.placement().capture());
        assertEquals(frames, p.frame().environment().globals().levelFrames()); assertEquals(clock + 1, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertSame(scroll, actualField(presentation, "scrollState")); assertSame(background, actualField(presentation, "backgroundState"));
        assertSame(globals, actualField(values(p), "globals")); assertSame(cpu, image(p, "cpuSat")); assertSame(cpu, image(p, "displayedSat"));
        assertSame(target, image(p, "fadePalette")); assertSame(water, image(p, "waterPalette")); assertArrayEquals(new byte[128], after.displayedPalette().copy());
        assertSame(section.codeTable(), after.section().codeTable()); assertSame(section.residency(), after.section().residency());
        assertEquals(section.savedDecoder(), after.section().savedDecoder()); assertEquals(0, after.section().queue().headSource());
        assertEquals(0xFF, p.harness().module().pastOldProgrammeReturnCode());
        assertEquals(SonicCdGameModule.PastLoadCall.OLD_EXIT_RETURN, calls(p).getLast());
        assertEquals(22, calls(p).stream().filter(c -> c == SonicCdGameModule.PastLoadCall.OLD_EXIT_BLACK_COLOURS).count());
    }

    @Test void fullAndCourseCapturesRestoreBeforeForwardAcrossPreludeClearWaitAndReturnOnTheSameFrameEpoch() throws Exception {
        var p = ordinary(); seed(p, fixed(p, 127).moveByte(ROUTINE, 8));
        replayNext(p, SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE);
        for (int wait = 0; wait < 10; wait++) step(p.harness(), neutral());
        replayNext(p, SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE);
        assertEquals(11, p.harness().module().levelProgramState().whiteFadeSteps());
        for (int wait = 11; wait < 21; wait++) step(p.harness(), neutral());
        replayNext(p, SonicCdGameModule.PastLoadPhase.OLD_CLEAR_WAIT);
        replayNext(p, SonicCdGameModule.PastLoadPhase.OLD_RETURNED);
        replayNext(p, SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED);
    }

    @Test void actualIpxBlankRetainsKnownReturnAndFmRequestWithoutOldFrameControllerBodyOrClockWork() throws Exception {
        var p = entered(); blackWaits(p); step(p.harness(), neutral()); healthyExit(p, SonicCdGameModule.PastLoadPhase.OLD_RETURNED);
        var actor = p.frame().captureActor(); var shared = p.frame().captureShared(); var placement = p.placement().capture();
        var section = p.harness().module().pastLoadState().section(); var palette = image(p, "cpuPalette"); var displayed = image(p, "displayedPalette");
        var source = p.frame().stageSource(); var clock = p.frame().vIntClock(); var oldSounds = p.harness().module().pastLoadState().sounds();
        step(p.harness(), neutral()); healthyExit(p, SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED);
        assertCapturedValueEquals(actor, p.frame().captureActor()); assertCapturedValueEquals(shared, p.frame().captureShared()); assertPlacement(placement, p.placement().capture());
        assertSame(section, p.harness().module().pastLoadState().section()); assertSame(palette, image(p, "cpuPalette")); assertSame(displayed, image(p, "displayedPalette"));
        assertSame(source, p.frame().stageSource()); assertSame(clock, p.frame().vIntClock()); assertEquals(0xFF, p.harness().module().pastOldProgrammeReturnCode());
        var sounds = p.harness().module().pastLoadState().sounds(); assertEquals(oldSounds.size() + 1, sounds.size());
        var stop = assertInstanceOf(SonicCdGameModule.PastProgrammeSound.class, sounds.getLast());
        assertEquals(SonicCdGameModule.ProgramSound.STOP_FM, stop.sound()); assertEquals(0xE0, stop.nativeId());
        assertFalse((boolean) actualField(actualField(values(p), "fadeProgress"), "displayEnabled"));
    }

    @Test void returnedProgrammeCapturesStayHealthyUntilActualAct2HandoffRetiresTheirOwner() throws Exception {
        var p = entered(); blackWaits(p); step(p.harness(), neutral()); step(p.harness(), neutral());
        healthyExit(p, SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED); var before = captureNative(p.harness());
        var registry = p.harness().mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        registry.restore(full); assertNativeCapture(before, p.harness()); registry.restoreCourse(course); assertNativeCapture(before, p.harness());
        assertFalse(p.frame().closed()); var world = p.harness().mode().getWorldSession();
        long generation = p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration();
        step(p.harness(), neutral());
        assertTrue(p.harness().module().act2LoadingSelected()); assertTrue(p.frame().closed());
        assertNotSame(p.frame(), p.harness().module().requirePlayerFrame()); assertFalse(p.harness().module().requirePlayerFrame().closed());
        assertSame(world, p.harness().mode().getWorldSession());
        assertEquals(generation + 1, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
    }

    @Test void equalPhaseValuesCannotMintPreludeOrScalarCallbacksAndOrdinaryPresentationMutationRemainsStrict() throws Exception {
        var p = ordinary(); var m = p.harness().module(); var before = captureNative(p.harness());
        assertThrows(IllegalStateException.class, () -> m.beginPastOldProgrammeExit(visit(p), false));
        assertThrows(IllegalStateException.class, () -> m.requirePastLoadingVisit(null)); assertNativeCapture(before, p.harness());
        seed(p, fixed(p, 127).moveByte(ROUTINE, 8)); step(p.harness(), neutral()); healthyExit(p, SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE);
        var exit = captureNative(p.harness()); var presentation = presentation(p);
        assertThrows(IllegalStateException.class, () -> m.completePastOldBlackExitLoad(visit(p)));
        assertThrows(IllegalArgumentException.class, presentation::servicePastPlaneAtLevelLoad);
        assertThrows(IllegalArgumentException.class, () -> presentation.consumePastForegroundFlags(0));
        assertThrows(IllegalStateException.class, presentation::servicePastOldProgrammePlaneAtLevelLoad);
        assertThrows(IllegalStateException.class, () -> presentation.consumePastOldProgrammeForegroundFlags(0));
        assertNativeCapture(exit, p.harness()); assertFalse(p.frame().closed());
    }

    @Test void explicitRetirementKeepsRetainedValuesAndRejectsFurtherCaptures() throws Exception {
        var p = entered(); visit(p).capture(); var values = values(p);
        var palette = image(p, "cpuPalette"); var water = image(p, "waterPalette");
        p.frame().close(); assertTrue(p.frame().closed());
        assertThrows(IllegalStateException.class, () -> visit(p).capture());
        assertThrows(IllegalStateException.class, () -> p.harness().mode().getRewindRegistry().capture());
        assertSame(values, values(p)); assertSame(palette, image(p, "cpuPalette")); assertSame(water, image(p, "waterPalette"));
        // No forward call or restore follows retirement; normal fixture cleanup owns release.
    }

    private static Prepared ordinary() throws Exception {
        var p = ready(); for (int call = p.returned() + 1; call <= 8278; call++) step(p.harness(), route(call));
        assertFalse(p.frame().closed()); assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN)); return p;
    }
    private static Prepared entered() throws Exception {
        var p = ordinary(); seed(p, fixed(p, 127).moveByte(ROUTINE, 8)); step(p.harness(), neutral());
        healthyExit(p, SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE); return p;
    }
    private static void blackWaits(Prepared p) throws Exception {
        var actor = p.frame().captureActor(); var placement = p.placement().capture();
        var cpu = image(p, "cpuSat"); var completed = completed(p); var target = image(p, "fadePalette");
        var section = p.harness().module().pastLoadState().section(); var globals = actualField(values(p), "globals");
        int frames = p.frame().environment().globals().levelFrames(); long clock = p.frame().vIntClock().absoluteValue().orElseThrow();
        var presentation = presentation(p); var scroll = actualField(presentation, "scrollState"); var bg = actualField(presentation, "backgroundState");
        for (int wait = 1; wait <= 22; wait++) {
            var normal = image(p, "cpuPalette"); var water = image(p, "waterPalette");
            var expected = com.openggf.game.soniccd.resources.CdLevelPaletteFade.stepToBlack(
                    new com.openggf.game.soniccd.resources.CdLevelPaletteFade.Palettes(normal, water));
            step(p.harness(), neutral()); healthyExit(p, wait == 22 ? SonicCdGameModule.PastLoadPhase.OLD_CLEAR_WAIT : SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE);
            assertArrayEquals(normal.copy(), image(p, "displayedPalette").copy(), "Publication precedes colour pass " + wait);
            assertArrayEquals(expected.palette().copy(), image(p, "cpuPalette").copy());
            assertArrayEquals(expected.waterPalette().copy(), image(p, "waterPalette").copy());
            assertArrayEquals(new byte[128], image(p, "waterPalette").copy());
            assertNotSame(image(p, "waterPalette"), image(p, "cpuPalette")); assertNotSame(image(p, "waterPalette"), target);
            assertNotSame(image(p, "waterPalette"), image(p, "displayedPalette"));
            assertEquals(wait, p.harness().module().levelProgramState().whiteFadeSteps());
            assertEquals(wait == 22 ? 0 : 1, p.harness().module().levelProgramState().palFadeFlags());
            assertEquals(wait == 22 ? 0 : 2, p.harness().module().pastLoadState().level().levelRestart());
            assertCapturedValueEquals(actor, p.frame().captureActor()); assertPlacement(placement, p.placement().capture());
            assertSame(cpu, image(p, "cpuSat")); assertSame(completed, completed(p)); assertSame(target, image(p, "fadePalette"));
            assertSame(globals, actualField(values(p), "globals")); assertSame(scroll, actualField(presentation, "scrollState")); assertSame(bg, actualField(presentation, "backgroundState"));
            var current = p.harness().module().pastLoadState().section(); assertSame(section.codeTable(), current.codeTable()); assertSame(section.residency(), current.residency());
            assertEquals(0, current.queue().headSource()); assertEquals(0, current.remainingTilesWord());
            assertEquals(frames, p.frame().environment().globals().levelFrames()); assertEquals(clock + wait, p.frame().vIntClock().absoluteValue().orElseThrow());
        }
        assertArrayEquals(new byte[128], image(p, "cpuPalette").copy());
        assertEquals(22, calls(p).stream().filter(c -> c == SonicCdGameModule.PastLoadCall.OLD_EXIT_FADE_SERVICE).count());
        assertEquals(22, calls(p).stream().filter(c -> c == SonicCdGameModule.PastLoadCall.OLD_EXIT_BLACK_COLOURS).count());
        assertEquals(SonicCdGameModule.PastLoadCall.OLD_EXIT_CLEAR_PALETTE, calls(p).getLast());
    }
    private static void replayNext(Prepared p, SonicCdGameModule.PastLoadPhase expected) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry(); var world = h.mode().getWorldSession();
        var source = p.frame().stageSource(); long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); registry.restoreCourse(course); assertNativeCapture(before, h);
        step(h, neutral()); healthyExit(p, expected); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); step(h, neutral()); healthyExit(p, expected); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); step(h, neutral()); healthyExit(p, expected); assertNativeCapture(after, h);
        assertSame(world, h.mode().getWorldSession()); assertSame(source, p.frame().stageSource());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void healthyExit(Prepared p, SonicCdGameModule.PastLoadPhase phase) throws Exception {
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertFalse(p.frame().closed());
        assertEquals(phase, p.harness().module().pastLoadState().phase()); assertNull(actualField(p.visit(), "drawQueue"));
        assertFalse((boolean) actualField(p.visit(), "runningObjects")); assertFalse((boolean) actualField(p.visit(), "servicing"));
        assertFalse((boolean) actualField(p.visit(), "oldProgrammeContinuation")); assertNull(actualField(p.visit(), "activeResults"));
    }
    private static CdObjectState fixed(Prepared p, int whole) {
        var old = p.placement().pool().state(whole); assertEquals(0, old.id());
        return old.moveByte(ID, 0x3A).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0).moveWord(X, 288)
                .moveWord(RESULTS_DEST_X, 288).moveWord(RESULTS_SCREEN_Y_WORD, 204).moveWord(Y, 0x2468)
                .moveWord(Y_FRACTION, 0x1357).moveByte(PRIORITY, 0).moveByte(MAP_FRAME, 0).moveWord(TILE, 0x83C4)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());
    }
    private static void assertPlacement(CdObjectPlacementRuntime.Snapshot expected, CdObjectPlacementRuntime.Snapshot actual) {
        assertEquals(expected.status(), actual.status()); assertArrayEquals(expected.savedFlags().copy(), actual.savedFlags().copy());
        assertEquals(96, actual.pool().slots().size()); assertEquals(expected.pool().slots(), actual.pool().slots());
    }
    private static void seed(Prepared p, CdObjectState row) {
        var before = p.placement().capture(); p.placement().pool().write(row);
        assertEquals(before.status(), p.placement().status());
        assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var other : before.pool().slots()) if (other.identity().wholeIndex() != row.identity().wholeIndex())
            assertEquals(other, p.placement().pool().state(other.identity().wholeIndex()));
    }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
    private static SonicCdPastLoadingVisit visit(Prepared p) { return (SonicCdPastLoadingVisit) p.visit(); }
    private static Object values(Prepared p) throws Exception { return actualField(p.visit(), "values"); }
    private static CdAssetBytes image(Prepared p, String name) throws Exception { return (CdAssetBytes) actualField(values(p), name); }
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.PastLoadCall> calls(Prepared p) throws Exception { return (List<SonicCdGameModule.PastLoadCall>) actualField(values(p), "calls"); }
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat completed(Prepared p) throws Exception { return ((Optional<CdObjectDrawQueue.CpuSat>) actualField(actualField(values(p), "loop"), "completedDraw")).orElseThrow(); }
    private static SonicCdPlayerPresentation presentation(Prepared p) throws Exception { return (SonicCdPlayerPresentation) actualField(p.harness().module(), "playerPresentation"); }
}
