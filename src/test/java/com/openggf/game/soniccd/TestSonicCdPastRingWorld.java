package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdRing;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11bSectionArt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original-USA first Ring prefix after Main402/primary/reserved HUD.
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Ring/Main.asm:20-232;
 * Object Spawner.asm:42-101,176-227,267-325,352-373;
 * Object Functions.asm:11-33,137-196. J68529a8f source fixture is reused.
 * Expected scanner, Ring Init/Main, DrawObject and full byte images are detached
 * literal native arithmetic. No production Ring/draw/animation helper is an oracle.
 * The unchanged five-jump route supplies all actual input. This partial pass has
 * no camera continuation, DrawObjects, CPU SAT, returned7775 or parity claim.
 */
@Isolated
class TestSonicCdPastRingWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualRingPrefixMatchesAllNativePoolBankAndPhysicalQueueBytesAtItsDerivedBoundary() throws Exception {
        var p = ready();
        var before = before(p);
        assertTerminal(p, before, 45L, 8, 0);
    }

    @Test void fullHealthy44RestoreUndoesBankAndPoolContrastBeforeOneActualRingPrefix() throws Exception {
        var p = ready(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var nativeBefore = captureNative(h); var before = before(p);
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertNull(actualField(p.visit(), "activeRing"));
        assertNull(actualField(p.visit(), "activeSpring"));
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 45L, 8, 0);
    }

    @Test void realStart45Hold46CourseRestoreThenUnpause47RunsTheNativeRingPrefixOnce() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame();
        var actor = frame.captureActor(); var initialReserved = reserved(p.visit());
        step(h, start(true));
        assertTrue(frame.paused()); assertFalse(frame.closed()); assertEquals(actor, frame.captureActor());
        assertEquals(45L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames()); assertUnscanned(p);
        assertArrayEquals(initialReserved.copy(), reserved(p.visit()).copy()); assertNoReservedPass(p);
        step(h, start(false));
        assertTrue(frame.paused()); assertFalse(frame.closed()); assertEquals(actor, frame.captureActor());
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames()); assertUnscanned(p);
        assertArrayEquals(initialReserved.copy(), reserved(p.visit()).copy()); assertNoReservedPass(p);
        var registry = h.mode().getRewindRegistry(); var course = registry.captureCourse();
        var nativeBefore = captureNative(h); var before = before(p);
        contrast(p.placement());
        registry.restoreCourse(course); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertNull(actualField(p.visit(), "activeRing"));
        assertNull(actualField(p.visit(), "activeSpring"));
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 47L, 0x80, 0x80);
    }

    record Prepared(Harness harness, Object visit, CdPlayerFrameRuntime frame,
                            CdObjectPlacementRuntime placement, CdR11bAssets assets,
                            CdAssetBytes carried, int returned) { }
    record Before(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                          SonicCdGameModule.PastLoadState observed, Object values,
                          SonicCdPlayerPresentation.ScrollCapture scroll, Object presentation,
                          Object world, long generation, boolean paused, RingPrefix prefix,
                          CdObjectPool pool, Object poolOwner, Object stageSource, Object artOwner,
                          Object ringBody, Object springBody, PlanePrelude planes) { }

    static Prepared ready() throws Exception {
        var h = opening(); var m = h.module();
        var old = assertInstanceOf(CdOpeningObjectRuntime.class, m.getGameService(NativeDynamicObjectPass.class));
        for (int call = 1; call <= 7728; call++) step(h, route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING, m.levelProgramState().phase());
        var bank = old.placement().capture().savedFlags();
        step(h, route(7729));
        var frame = m.requirePlayerFrame(); var visit = actualField(m, "pastLoading");
        var placement = assertInstanceOf(CdObjectPlacementRuntime.class, actualField(visit, "placement"));
        int returned = 7729;
        while (m.pastLoadState().phase() != SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL) {
            assertTrue(returned < 7800, "The unchanged bounded recipe must complete real Past loading");
            step(h, route(++returned));
        }
        assertEquals(7774, returned); assertEquals(44L, frame.vIntClock().absoluteValue().orElseThrow());
        var p = new Prepared(h, visit, frame, placement, m.pastLoadState().assets(), bank, returned);
        assertUnscanned(p); assertInitialReserved(reserved(visit).copy());
        assertNull(actualField(visit, "drawQueue")); assertNull(actualField(visit, "activeReserved"));
        assertNull(actualField(visit, "activeRing"));
        assertNull(actualField(visit, "activeSpring"));
        return p;
    }
    static Before before(Prepared p) throws Exception {
        var h = p.harness(); var frame = p.frame();
        assertFalse(frame.closed()); assertEquals(0, frame.environment().globals().levelFrames());
        assertEquals(1, frame.state().header().slotId()); assertEquals(0, frame.state().motion().routine());
        assertEquals(0, frame.environment().globals().debugCheat()); assertEquals(0, frame.environment().globals().debugMode());
        assertEquals(0, frame.environment().globals().timeAttackMode());
        assertEquals(0, frame.environment().movement().gates().miniSonic());
        assertEquals(0, frame.environment().movement().gates().timeZone());
        assertEquals(0, frame.environment().movement().gates().timeWarpDirection());
        assertEquals(0, frame.environment().globals().timeWarpTimer());
        assertEquals(0, frame.environment().controls().controlLocked());
        var scroll = (SonicCdPlayerPresentation.ScrollCapture) h.module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        assertEquals(0x0400, scroll.state().scrollFlags() & 0x0400,
                "Native Main247 forces the left-column flag; copied DrawLevel never clears this live bit");
        var planes = ordinaryPlanePrelude(p.assets(), scroll,
                assertInstanceOf(CdR11aBackgroundPlane.State.class, actualField(actualField(p.visit(), "values"), "foregroundPlane")));
        return new Before(frame.captureActor(), frame.captureShared(), h.module().pastLoadState(),
                actualField(p.visit(), "values"), scroll, actualField(h.module(), "playerPresentation"),
                h.mode().getWorldSession(), h.mode().getLevelManager().getCompletedProductionLoadGeneration(),
                frame.paused(), ringPrefix(p.assets().placements().records(), scroll.state().cameraX(), scroll.state().cameraYWord(),
                        p.carried().copy(), frame.environment().movement().gates().timeZone(),
                        frame.environment().movement().gates().timeWarpDirection(),
                        h.module().pastLoadState().ring().timeStopTimer(), h.module().pastLoadState().ring().ringAnimationFrame()),
                p.placement().pool(), actualField(p.placement().pool(), "owner"), frame.stageSource(),
                actualField(frame, "art"), actualField(p.visit(), "ring"), actualField(p.visit(), "spring"), planes);
    }
    static void assertNoReservedPass(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertNull(actualField(p.visit(), "activeRing"));
        assertNull(actualField(p.visit(), "activeSpring"));
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.PRIMARY_INIT));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.RING_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.SPRING_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS));
    }
    static CdAssetBytes reserved(Object visit) throws Exception {
        return assertInstanceOf(CdAssetBytes.class, actualField(actualField(visit, "values"), "hudObjects"));
    }
    static void assertUnscanned(Prepared p) {
        var s = p.placement().capture(); byte[] bank = p.carried().copy();
        assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0,
                Byte.toUnsignedInt(bank[0]), Byte.toUnsignedInt(bank[1]), -1, -1), s.status());
        assertArrayEquals(bank, s.savedFlags().copy());
        for (int i = 0; i < 96; i++) {
            assertEquals(32 + i, s.pool().slots().get(i).identity().wholeIndex());
            assertArrayEquals(new byte[64], s.pool().slots().get(i).copyBytes());
        }
    }
    static void contrast(CdObjectPlacementRuntime placement) {
        var before = placement.capture();
        placement.pool().write(placement.pool().state(127).moveByte(CdObjectState.ByteField.ID, 0x3F));
        if ((placement.savedFlag(1, 0) & 0x40) == 0) placement.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_7);
        else placement.bclrSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_7);
        assertFalse(Arrays.equals(before.savedFlags().copy(), placement.capture().savedFlags().copy()));
        assertEquals(0x3F, placement.pool().state(127).id());
        // Constructed restore contrast only: always removed before a real forward row.
    }
    static LogicalInputSnapshot start(boolean pressed) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, pressed, pressed), PlayerInputState.neutral());
    }

    static void assertTerminal(Prepared p, Before before, long clock, int held, int tap) throws Exception {
        assertTerminal(p, before, clock, held, tap, "PAST_RING_PREFIX");
    }
    static void assertTerminal(Prepared p, Before before, long clock, int held, int tap, String diagnostic) throws Exception {
        var h = p.harness(); var frame = p.frame();
        var cpuBefore = assertInstanceOf(CdAssetBytes.class, actualField(before.values(), "cpuSat"));
        assertArrayEquals(new byte[640], cpuBefore.copy(), "The source-established first pass has not built a CPU SAT");
        var failure = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class,
                () -> step(h, held == 8 ? route(p.returned() + 1) : start(true)));
        assertEquals(before.prefix().boundary(), failure.boundary(), "Boundary comes from the detached live native walk");
        // Only retained refs/read-only values below: no closed-Frame capture, guarded
        // Module accessor, registry restore, producer replay or another forward row.
        assertTrue(frame.closed()); assertFalse(frame.paused());
        assertEquals(clock, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(expectedPastInitState(before.actor().state()), frame.state());
        var e = before.shared().environment(); var g = e.globals(); var c = e.controls();
        var serviced = new CdPlayerBody.Environment(e.movement(),
                new CdPlayerBody.Controls(held, tap, c.logicalHeld(), c.logicalTap(), c.controlLocked()),
                new CdPlayerBody.Globals(1, g.cameraXCentre(), g.timeAttackMode(), g.debugMode(), g.debugCheat(),
                        g.timeWarpTimer(), g.invincible(), g.bossMusic()), e.dynamicSlots());
        assertEquals(expectedPastInitEnvironment(serviced), frame.environment());
        assertTrue(frame.display().isEmpty());
        assertSame(before.actor().owner(), actualField(frame, "captureOwner"));
        assertSame(before.shared().owner(), actualField(frame, "captureOwner"));
        assertSame(before.stageSource(), actualField(frame, "pastSource"));
        assertSame(before.artOwner(), actualField(frame, "art"));
        assertEquals(before.shared().artState(), actualField(before.artOwner(), "state"));
        assertFalse((boolean) actualField(frame, "inOperation")); assertNull(actualField(frame, "pending"));
        assertFalse((boolean) actualField(frame, "bodyEntitled")); assertNull(actualField(frame, "artPublication"));
        assertSame(frame, actualField(h.module(), "playerFrame")); assertNull(actualField(h.module(), "playerActor"));
        assertSame(p.assets(), actualField(p.visit(), "assets")); assertSame(p.placement(), actualField(p.visit(), "placement"));
        var expected = before.prefix();
        assertSame(before.pool(), actualField(p.placement(), "pool"));
        assertEquals(expected.status(), p.placement().status());
        assertArrayEquals(expected.bank(), assertInstanceOf(byte[].class, actualField(p.placement(), "savedFlags")));
        assertSame(before.poolOwner(), actualField(before.pool(), "owner"));
        int occupied = 0;
        for (int i = 0; i < 96; i++) {
            var image = before.pool().state(32 + i);
            assertEquals(32 + i, image.identity().wholeIndex());
            assertSame(before.poolOwner(), actualField(image, "owner"), "Physical pool owner " + (32 + i));
            assertArrayEquals(expected.images()[i], image.copyBytes(), "Native full post-prefix image " + (32 + i));
            if (image.id() != 0) occupied++;
        }
        if (expected.firstUnownedWhole() >= 0)
            assertEquals(expected.firstUnownedId(), before.pool().state(expected.firstUnownedWhole()).id());
        assertSame(before.ringBody(), actualField(p.visit(), "ring"), "One source-bound cached Ring owner");
        assertInstanceOf(CdRing.class, before.ringBody());
        assertSame(before.springBody(), actualField(p.visit(), "spring"), "One source-bound cached Spring owner");
        assertInstanceOf(com.openggf.game.soniccd.objects.CdSpring.class, before.springBody());
        assertNull(actualField(p.visit(), "activeSpring"), "Actual Spring scope retires before the reached next-owner fault");
        assertNull(actualField(p.visit(), "activeRing"), "Actual Ring scope retires before the reached next-owner fault");
        assertArrayEquals(expectedReserved(), reserved(p.visit()).copy(), "All31 complete images after only2/3/5/31 execute");
        assertNull(actualField(p.visit(), "activeReserved"), "The real invocation scope retires before the next-body fault");
        var queue = assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue"));
        assertQueue(queue, before.prefix(), cpuBefore.copy());
        assertOrdinaryNonwrites(p, before, clock == 47L);
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        for (var call : List.of(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS,
                SonicCdGameModule.PastLoadCall.PRIMARY_INIT, SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS))
            assertEquals(1, Collections.frequency(calls, call), "One genuine completed" + call);
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.UPDATE_HUD) < calls.indexOf(SonicCdGameModule.PastLoadCall.ORDINARY_SERVICE));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.ORDINARY_SERVICE) < calls.indexOf(SonicCdGameModule.PastLoadCall.LEVEL_FRAME_INCREMENT));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.LEVEL_FRAME_INCREMENT) < calls.indexOf(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS) < calls.indexOf(SonicCdGameModule.PastLoadCall.PRIMARY_INIT));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.PRIMARY_INIT) < calls.indexOf(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
        var completed = new ArrayList<SonicCdGameModule.PastLoadCall>();
        completed.addAll(expected.completedBodyCalls());
        if (expected.firstUnownedWhole() == -1) completed.add(SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS);
        assertEquals(completed, calls.subList(calls.indexOf(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS) + 1, calls.size()),
                "Only actually completed physical Ring/Spring calls, and a completed full dynamic walk when reached");
        assertEquals(expected.completedRingWholes().size(), Collections.frequency(calls, SonicCdGameModule.PastLoadCall.RING_BODY));
        assertEquals(expected.completedSpringWholes().size(), Collections.frequency(calls, SonicCdGameModule.PastLoadCall.SPRING_BODY));
        assertEquals(expected.firstUnownedWhole() == -1 ? 1 : 0,
                Collections.frequency(calls, SonicCdGameModule.PastLoadCall.DYNAMIC_OBJECTS));
        assertSame(before.world(), h.mode().getWorldSession());
        assertEquals(before.generation(), h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty()); assertNull(h.mode().getLevelManager().getLevelGamestate());
        assertEquals(0, h.mode().getLevelManager().getFrameCounter());
        System.out.println(diagnostic + " boundary=" + expected.boundary() + " recipeIndex=" + (p.returned() + 1)
                + " clock=" + clock + " levelFrames=" + frame.environment().globals().levelFrames()
                + " completedRingBodies=" + expected.completedRingWholes().size() + " occupiedAfterPrefix=" + occupied
                + " firstUnownedWhole=" + expected.firstUnownedWhole() + " firstUnownedId=" + expected.firstUnownedId()
                + " completedSpringBodies=" + expected.completedSpringWholes().size()
                + " Maincount=" + frame.environment().globals().levelFrames()
                + " RingBodies=" + expected.completedRingWholes().size() + " SpringBodies=" + expected.completedSpringWholes().size()
                + " pointerBytes=" + Arrays.toString(expected.pointerByteCounts()) + " CPUcount=0");

    }
    private static void assertQueue(CdObjectDrawQueue queue, RingPrefix expectedPrefix, byte[] cpuBefore) throws Exception {
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, queue.phase(), "Fault closes admission without draining the partial pass");
        var counts = assertInstanceOf(int[].class, actualField(queue, "pointerByteCounts"));
        assertArrayEquals(expectedPrefix.pointerByteCounts(), counts, "Native pointer WORD bytes remain separate from SAT pieces");
        var pointers = assertInstanceOf(CdObjectDrawQueue.CdSlotRef[][].class, actualField(queue, "pointers"));
        assertEquals(8, pointers.length);
        for (int bucket = 0; bucket < 8; bucket++) {
            assertEquals(63, pointers[bucket].length);
            for (int ordinal = 0; ordinal < 63; ordinal++) {
                int whole = expectedPrefix.pointerWholes()[bucket][ordinal];
                CdObjectDrawQueue.CdSlotRef expected = whole < 0 ? null : new CdObjectDrawQueue.CdSlotRef(whole);
                assertEquals(expected, pointers[bucket][ordinal], "Retained bucket/ordinal" + bucket + "/" + ordinal);
            }
        }
        var allowed = assertInstanceOf(List.class, actualField(queue, "reservedSlots"));
        assertEquals(Set.of(new CdObjectDrawQueue.CdSlotRef(2), new CdObjectDrawQueue.CdSlotRef(3),
                new CdObjectDrawQueue.CdSlotRef(5), new CdObjectDrawQueue.CdSlotRef(31)), Set.copyOf(allowed));
        var cpu = assertInstanceOf(CdObjectDrawQueue.CpuSat.class, actualField(queue, "cpuSat"));
        assertArrayEquals(cpuBefore, cpu.bytes()); assertEquals(0, cpu.spriteCount()); assertTrue(cpu.provenance().isEmpty());
        // No captureClosedCut/cpuSat() call on the terminal queue, no finishAfterCamera.
        // Unchanged CPU values are not a separate observable count of pure resolver reads.
    }
    private static void assertOrdinaryNonwrites(Prepared p, Before before, boolean unpause) throws Exception {
        var after = actualField(p.visit(), "values"); var old = before.observed();
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, actualField(after, "phase"));
        for (String field : new String[]{"cpuPalette", "fadePalette", "cpuSat"})
            assertArrayEquals(assertInstanceOf(CdAssetBytes.class, actualField(before.values(), field)).copy(),
                    assertInstanceOf(CdAssetBytes.class, actualField(after, field)).copy(), field);
        assertArrayEquals(old.cpuPalette().copy(), assertInstanceOf(CdAssetBytes.class, actualField(after, "displayedPalette")).copy());
        assertArrayEquals(assertInstanceOf(CdAssetBytes.class, actualField(before.values(), "cpuSat")).copy(),
                assertInstanceOf(CdAssetBytes.class, actualField(after, "displayedSat")).copy());
        byte[] hscroll = assertInstanceOf(CdAssetBytes.class, actualField(before.values(), "displayedHScroll")).copy();
        System.arraycopy(before.scroll().background().hscrollRam().copy(), 0, hscroll, 0, 0x380);
        assertArrayEquals(hscroll, assertInstanceOf(CdAssetBytes.class, actualField(after, "displayedHScroll")).copy());
        assertEquals(before.scroll().background().vscroll(), actualField(after, "displayedVScrollLongBits"));
        assertEquals(before.scroll().state(), actualField(before.presentation(), "scrollState"));
        assertEquals(before.scroll().background(), actualField(before.presentation(), "backgroundState"));
        var ring = assertInstanceOf(CdRingGlobals.class, actualField(after, "ring"));
        var r = old.ring(); assertEquals(new CdRingGlobals(Math.min(r.rings(), 999), r.lives(), r.livesFlags(),
                0, 0, r.timeStopTimer(), r.ringAnimationTimer(), r.ringAnimationFrame()), ring);
        assertEquals(old.level(), actualField(after, "level"));
        var oldGlobals = actualField(before.values(), "globals"); var newGlobals = actualField(after, "globals");
        var score = assertInstanceOf(CdOpeningObjectRuntime.ScoreGlobals.class, actualField(oldGlobals, "score"));
        assertEquals(new CdOpeningObjectRuntime.ScoreGlobals(score.score(), score.nextLifeScore(), 0), actualField(newGlobals, "score"));
        var liveBefore = assertInstanceOf(CdCheckpointLiveAux.State.class, actualField(oldGlobals, "live"));
        var liveAfter = assertInstanceOf(CdCheckpointLiveAux.State.class, actualField(newGlobals, "live"));
        int t = CdCheckpointLiveAux.read(liveBefore, CdCheckpointLiveAux.Field.TIME_LONG);
        int expectedTime = before.paused() ? t : tick(t);
        assertEquals(liveBefore.knownMask(), liveAfter.knownMask());
        assertEquals(expectedTime, CdCheckpointLiveAux.read(liveAfter, CdCheckpointLiveAux.Field.TIME_LONG));
        for (int address : new int[]{0xFFF648, 0xFFF649, 0xFFF64D, 0xFFF64E})
            assertEquals(liveBefore.knownByte(address), liveAfter.knownByte(address));
        for (String field : new String[]{"contact", "drownTimerWord", "tail", "bonus", "debugBlockWord", "layoutRam"})
            assertEquals(actualField(oldGlobals, field), actualField(newGlobals, field), field);
        assertEquals(OptionalInt.of(0), actualField(newGlobals, "pauseMusicBit"), "Main music pause-bit clear is retained");
        var section = assertInstanceOf(CdR11bSectionArt.State.class, actualField(after, "section")); var b = old.section();
        assertEquals(b.sectionWord(), section.sectionWord()); assertEquals(b.queue(), section.queue());
        assertArrayEquals(b.codeTable().copy(), section.codeTable().copy()); assertEquals(b.decoder(), section.decoder());
        assertEquals(b.savedDecoder(), section.savedDecoder()); assertEquals(b.remainingTilesWord(), section.remainingTilesWord());
        assertEquals(b.processBudgetWord(), section.processBudgetWord()); assertEquals(b.hintWord(), section.hintWord());
        assertEquals(b.padWord(), section.padWord()); assertEquals(b.stopped(), section.stopped());
        assertHudAndOtherCurrent(p.assets(), b, section, score.score(), ring, expectedTime);
        assertArrayEquals(before.planes().foreground(),
                assertInstanceOf(CdR11aBackgroundPlane.State.class, actualField(after, "foregroundPlane")).nameTable().copy(),
                "Complete native FG image after the ordinary copied-flags prelude");
        assertEquals(actualField(before.values(), "fadeProgress"), actualField(after, "fadeProgress"));
        for (String field : new String[]{"planeState", "publishedPlane"})
            assertArrayEquals(before.planes().background(),
                    assertInstanceOf(CdR11aBackgroundPlane.State.class, actualField(before.presentation(), field)).nameTable().copy(),
                    "Complete native BG image after the ordinary copied-flags prelude: " + field);
        assertEquals(before.planes().copies(), actualField(before.presentation(), "drawCopies"),
                "Copied flags are consumed; every copied camera bit and live scroll image is retained");
        assertEquals(unpause ? List.of(new SonicCdGameModule.PastSubCpuSound(SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM))
                : List.of(), actualField(p.visit(), "sounds"), "Reached Ring Init/Main, Spring Init/display-only and reserved bodies emit no new sound request");
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

    private static int tick(int time) {
        assertNotEquals(0x00093B3B, time, "This real entry must not select unowned maximum-time Kill");
        int minutes = (time >>> 16) & 255, seconds = (time >>> 8) & 255, frames = ((time & 255) + 1) & 255;
        if (frames >= 60) { frames = 0; seconds = (seconds + 1) & 255; if (seconds >= 60) { seconds = 0; minutes = Math.min((minutes + 1) & 255, 9); } }
        return (time & 0xFF000000) | (minutes << 16) | (seconds << 8) | frames;
    }
    private static void assertInitialReserved(byte[] actual) {
        byte[] expected = new byte[31 * 64];
        for (int whole : new int[]{2, 3, 5}) expected[(whole - 1) * 64] = 0x1C;
        expected[2 * 64 + 0x28] = 1; expected[4 * 64 + 0x29] = 1;
        expected[30 * 64] = 0x19; expected[30 * 64 + 0x28] = 0x0A;
        assertArrayEquals(expected, actual);
    }
    private static byte[] expectedReserved() {
        byte[] expected = new byte[31 * 64];
        for (int whole : new int[]{2, 3, 5}) {
            int at = (whole - 1) * 64; expected[at] = 0x1C; expected[at + 0x24] = 2;
            putLong(expected, at + 4, 0x20ABBE); putWord(expected, at + 2, 0x8568);
            putWord(expected, at + 8, 0x90); putWord(expected, at + 0x0A, whole == 3 ? 0x148 : 0x88);
            expected[at + 0x1A] = (byte) (whole == 3 ? 1 : whole == 5 ? 3 : 0);
        }
        expected[2 * 64 + 0x28] = 1; expected[4 * 64 + 0x29] = 1;
        int at = 30 * 64; expected[at] = 0x19; expected[at + 0x24] = 2; expected[at + 0x28] = 0x0A;
        putLong(expected, at + 4, 0x20A8A6); putWord(expected, at + 2, 0x85A8);
        putWord(expected, at + 8, 0xC4); putWord(expected, at + 0x0A, 0x152); expected[at + 0x1A] = 0x13;
        return expected;
    }
    private static void putWord(byte[] bytes, int at, int word) { bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word; }
    private static void putLong(byte[] bytes, int at, int bits) { putWord(bytes, at, bits >>> 16); putWord(bytes, at + 2, bits); }


    /**
     * Detached ordinary V-int draw prelude, before the unchanged Ring oracle below.
     * N1ecc5f3a Main:840-844 copies live camera/flag words; Past Scroll DrawLevel:607-628
     * draws BG1, then the two no-op BG owners, then FG. DrawLevelFG:642-686 consumes
     * copied HIGH bits0..3 independently; DrawLevelBG1:700-803 owns only BG1's HIGH
     * byte. Live flags/cameras are nonwrites at this cut. Main:247 forces live FG
     * bit2 and InitLevelDrawFG:868-886 does not consume it, so $0400 is legitimate.
     * These expected images use actual BEFORE bytes and raw prepared source data.
     * No production Plane/scroll/formatter invocation computes an expected value.
     */
    private record PlanePrelude(byte[] foreground, byte[] background,
                                SonicCdPlayerPresentation.DrawCopies copies) { }
    private record PreludeMetadata(byte[] source, int offset) { }

    private static PlanePrelude ordinaryPlanePrelude(CdR11bAssets assets,
            SonicCdPlayerPresentation.ScrollCapture scroll, CdR11aBackgroundPlane.State foreground) {
        byte[] blocks = assets.terrain().blocks().copy();
        byte[] chunks = assets.terrain().chunks().copy();
        byte[] layout = assets.terrain().layout().ram().copy(); // Complete combined $800-byte layout.
        byte[] ids = assets.view(CdR11bAssets.Asset.BG_CAMERA_SECT_IDS).bytes().copy();
        byte[] fg = foreground.nameTable().copy(), bg = scroll.plane().nameTable().copy();
        var f = scroll.state(); var b = scroll.background();
        var cameras = new CdR11aBackgroundPlane.CopiedCameras(
                new CdR11aBackgroundPlane.Camera(b.cameraBgX(), b.cameraBgY()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg2X(), b.cameraBg2Y()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg3X(), b.cameraBg3Y()));
        int high = b.scrollFlagsBg() >>> 8;
        int rowY = -16;
        boolean row = (high & 1) != 0;
        high &= ~1; // BCLR0 always; BCLR1 is reached only when top was absent.
        if (!row) { row = (high & 2) != 0; high &= ~2; rowY = 224; }
        if (row) {
            int index = 1 + ((short) ((b.cameraBgYWord() + rowY) & 0xFFF0) >> 4);
            int id = preludeByte(ids, index);
            var camera = preludeCamera((byte) id, cameras); // EXT.W before the local table.
            // DrawBlockRow restores A0 to IDs+1 after either static or dynamic rows.
            preludeRow(bg, blocks, chunks, layout, 0x40, camera.xWord(), camera.yWord(),
                    id == 0 ? 0 : -16, rowY, id == 0 ? 32 : 22, id == 0,
                    new PreludeMetadata(ids, 1));
        }
        int foregroundMetadataIndex = 1; // BG1 LEA/ADDA, retained by its row save/restore.
        if (high != 0) {
            int right = high & 0xA8, x = -16;
            if (right != 0) { high = right >>> 1; x = 320; }
            int start = (short) (b.cameraBgYWord() & 0xFFF0) >> 4;
            for (int i = 0; i < 16; i++) {
                int index = start + i;
                int id = preludeByte(ids, index); // Every row advances A0, including skipped rows.
                if ((high & (1 << (id & 7))) != 0) {
                    var camera = preludeCamera(id, cameras); // Column ID is unsigned.
                    preludeBlock(bg, blocks, chunks, layout, 0x40,
                            (camera.xWord() + x) & 0xFFFF, (camera.yWord() - 16 + 16 * i) & 0xFFFF,
                            new PreludeMetadata(ids, index + 1));
                }
            }
            foregroundMetadataIndex = start + 16;
            high = 0; // CLR.B after all16 reads; BG2/3 copied words are untouched.
        }
        var metadata = new PreludeMetadata(ids, foregroundMetadataIndex);
        int flags = f.scrollFlags();
        if ((flags & 0xFF00) != 0) {
            // Foreground source order is top, bottom, left, right, with A0 carried throughout.
            for (int bit = 0; bit < 4; bit++) {
                int mask = 1 << (bit + 8);
                boolean draw = (flags & mask) != 0;
                flags &= ~mask;
                if (!draw) continue;
                if (bit < 2) {
                    metadata = preludeRow(fg, blocks, chunks, layout, 0, f.cameraX(), f.cameraYWord(),
                            -16, bit == 0 ? -16 : 224, 22, false, metadata);
                } else {
                    int x = bit == 2 ? -16 : 320;
                    for (int i = 0; i < 16; i++)
                        metadata = preludeBlock(fg, blocks, chunks, layout, 0,
                                (f.cameraX() + x) & 0xFFFF, (f.cameraYWord() - 16 + 16 * i) & 0xFFFF, metadata);
                }
            }
        }
        var copies = new SonicCdPlayerPresentation.DrawCopies(f.cameraX() << 16, f.cameraY16_16(), cameras,
                flags, new CdR11aBackgroundPlane.FlagWords((high << 8) | (b.scrollFlagsBg() & 255),
                b.scrollFlagsBg2(), b.scrollFlagsBg3()));
        return new PlanePrelude(fg, bg, copies);
    }

    /** Past Scroll .CameraSects:770-774 has precisely these finite copied-camera associations. */
    private static CdR11aBackgroundPlane.Camera preludeCamera(int id, CdR11aBackgroundPlane.CopiedCameras cameras) {
        return switch (id) {
            case 0, 2 -> cameras.bg1();
            case 4 -> cameras.bg2();
            case 6 -> cameras.bg3();
            default -> throw new AssertionError("Unmodeled reached native copied-camera selector " + id);
        };
    }

    private static PreludeMetadata preludeRow(byte[] plane, byte[] blocks, byte[] chunks, byte[] layout,
            int base, int cameraX, int cameraY, int firstX, int y, int count, boolean absoluteX,
            PreludeMetadata metadata) {
        for (int i = 0; i < count; i++)
            metadata = preludeBlock(plane, blocks, chunks, layout, base,
                    ((absoluteX ? 0 : cameraX) + firstX + i * 16) & 0xFFFF,
                    (cameraY + y) & 0xFFFF, metadata);
        return metadata;
    }

    /**
     * Block Draw GetBlockDataAbsXY:202-235 selects a native chunk WORD, retaining
     * the incoming metadata association for a raw blank byte (raw $80 is nonblank).
     * DrawBlock:117-180 selects the four source tiles and XORs their flip bits.
     * The output is painted directly in the 64x32 tile mosaic; command formatters,
     * plane writers and production render/block-reference helpers are not called.
     */
    private static PreludeMetadata preludeBlock(byte[] plane, byte[] blocks, byte[] chunks,
            byte[] layout, int base, int worldX, int worldY, PreludeMetadata metadata) {
        int cell = base + ((worldY >>> 8) & 7) * 128 + ((worldX >>> 8) & 127);
        int raw = preludeByte(layout, cell), block = 0;
        if (raw != 0) {
            int at = ((raw - 1) & 127) * 512 + ((worldY >>> 4) & 15) * 32 + ((worldX >>> 4) & 15) * 2;
            metadata = new PreludeMetadata(chunks, at);
            block = ((preludeByte(chunks, at) << 8) | preludeByte(chunks, at + 1)) & 1023;
        }
        int high = preludeByte(metadata.source(), metadata.offset());
        int flip = ((high & 8) != 0 ? 0x800 : 0) | ((high & 16) != 0 ? 0x1000 : 0);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
            int sourceX = x ^ ((high >>> 3) & 1), sourceY = y ^ ((high >>> 4) & 1);
            int at = block * 8 + (sourceY * 2 + sourceX) * 2;
            int tile = ((preludeByte(blocks, at) << 8) | preludeByte(blocks, at + 1)) ^ flip;
            int destination = ((((worldY >>> 4) & 15) * 2 + y) * 64
                    + ((worldX >>> 4) & 31) * 2 + x) * 2;
            putWord(plane, destination, tile);
        }
        return metadata;
    }
    private static int preludeByte(byte[] bytes, int at) {
        assertTrue(at >= 0 && at < bytes.length, "Source-backed prelude must own reached byte " + at);
        return Byte.toUnsignedInt(bytes[at]);
    }

    /**
     * Detached value model for the actual first Past cut only. Array accessors
     * copy their values; this record carries no invocation or runtime authority.
     */
    record RingPrefix(CdObjectPlacementRuntime.Status status, byte[] bank, byte[][] images,
                      int[] pointerByteCounts, int[][] pointerWholes, List<Integer> completedRingWholes,
                      List<Integer> completedSpringWholes,
                      int firstUnownedWhole, int firstUnownedId, SonicCdGameModule.PastLoadBoundary boundary) {
        RingPrefix {
            bank = bank.clone(); images = copyImages(images); pointerByteCounts = pointerByteCounts.clone();
            pointerWholes = copyPointers(pointerWholes); completedRingWholes = List.copyOf(completedRingWholes);
            completedSpringWholes = List.copyOf(completedSpringWholes);
        }
        @Override public byte[] bank() { return bank.clone(); }
        @Override public byte[][] images() { return copyImages(images); }
        @Override public int[] pointerByteCounts() { return pointerByteCounts.clone(); }
        @Override public int[][] pointerWholes() { return copyPointers(pointerWholes); }
        List<SonicCdGameModule.PastLoadCall> completedBodyCalls() {
            var calls = new ArrayList<SonicCdGameModule.PastLoadCall>();
            for (int whole = 32; whole < 128; whole++) {
                if (completedRingWholes.contains(whole)) calls.add(SonicCdGameModule.PastLoadCall.RING_BODY);
                if (completedSpringWholes.contains(whole)) calls.add(SonicCdGameModule.PastLoadCall.SPRING_BODY);
            }
            return List.copyOf(calls);
        }
    }

    /**
     * Shared package-only expected prefix for retained actual-World fixtures.
     * Scope is the real first USA Past rawperiod0/direction0 signed-forward scan;
     * the unused warp branches are not claimed by this helper or these tests.
     */
    static RingPrefix ringPrefix(List<CdObjectPlacements.Placement> rows, int cameraXWord, int cameraYWord,
                                 byte[] carried, int timeZoneByte, int timeWarpDirectionByte,
                                 int timeStopTimerWord, int ringAnimationFrameByte) {
        assertEquals(0, timeZoneByte, "This actual first Past fixture selects raw period0");
        assertEquals(0, timeWarpDirectionByte, "The actual first Past fixture has no active warp direction");
        assertTrue((cameraXWord & ~0xFFFF) == 0 && (cameraYWord & ~0xFFFF) == 0);
        assertTrue((timeStopTimerWord & ~0xFFFF) == 0 && (ringAnimationFrameByte & ~0xFF) == 0);
        var scan = firstScan(rows, cameraXWord, carried);
        int initialOccupied = 0;
        for (byte[] image : scan.images()) if (unsigned(image[0]) != 0) initialOccupied++;
        assertEquals(18, initialOccupied, "Prior actual18 is independently checked BEFORE any Ring model body");
        assertEquals(0x10, unsigned(scan.images()[0][0]), "Prior actual first native32 ID10 is still unexecuted");
        byte[] bank = scan.bank(); byte[][] images = scan.images();
        int[] counts = new int[8]; int[][] pointers = new int[8][63];
        for (int[] bucket : pointers) Arrays.fill(bucket, -1);
        // Genuine reserved HUD DrawObject calls already completed in physical2/3/5 order.
        pointers[0][0] = 2; pointers[0][1] = 3; pointers[0][2] = 5; counts[0] = 6;
        var completed = new ArrayList<Integer>();
        var completedSprings = new ArrayList<Integer>();
        int firstUnownedWhole = -1, firstUnownedId = 0;
        for (int index = 0; index < 96; index++) {
            int id = unsigned(images[index][0]); // Native RunObjects rereads each CURRENT occupant.
            if (id == 0) continue;
            if (id == 0x0A) {
                springInitOrDisplayOnly(images, index, cameraXWord, cameraYWord, counts, pointers);
                completedSprings.add(32 + index);
                continue;
            }
            if (id != 0x10) {
                firstUnownedWhole = 32 + index; firstUnownedId = id; break;
            }
            int routine = unsigned(images[index][0x24]);
            assertTrue(routine == 0 || routine == 2, "Only reached first-pass Ring Init/Main is modeled");
            boolean main = routine == 2 || ringInit(images, bank, index);
            if (main) ringMain(images, index, cameraXWord, cameraYWord, timeStopTimerWord,
                    ringAnimationFrameByte, counts, pointers);
            completed.add(32 + index);
        }
        var boundary = firstUnownedWhole < 0 ? SonicCdGameModule.PastLoadBoundary.LEVEL_FLOW
                : SonicCdGameModule.PastLoadBoundary.DYNAMIC_OBJECTS;
        return new RingPrefix(scan.status(), bank, images, counts, pointers, completed, completedSprings,
                firstUnownedWhole, firstUnownedId, boundary);
    }

    // N ObjRing_Deltas:20-35; literal signed bytes, not a runtime asset or A/B source view.
    private static final byte[] RING_DELTAS = {
            0x10, 0, 0x18, 0, 0x20, 0, 0, 0x10, 0, 0x18, 0, 0x20,
            0x10, 0x10, 0x18, 0x18, 0x20, 0x20, (byte) 0xF0, 0x10,
            (byte) 0xE8, 0x18, (byte) 0xE0, 0x20, 0x10, 8, 0x18, 0x10,
            (byte) 0xF0, 8, (byte) 0xE8, 0x10
    };

    /** ObjRing_Init:38-210, raw period0 only. Finding a child never clears its dirty image. */
    private static boolean ringInit(byte[][] images, byte[] bank, int parentIndex) {
        byte[] parent = images[parentIndex];
        int saved = unsigned(parent[0x23]), at = 2 + 3 * saved;
        assertTrue(at >= 2 && at < bank.length, "Real savedID0 remains a valid member-bank address");
        int subtype = unsigned(parent[0x28]), countMinusOne = subtype & 7;
        if (countMinusOne == 7) countMinusOne = 6;
        int deltaAt = (subtype >>> 4) * 2;
        int x = word(parent, 8), y = word(parent, 12);
        for (int member = 1; member <= countMinusOne + 1; member++) {
            if ((unsigned(bank[at]) & (1 << (member - 1))) == 0) {
                bank[at] &= 0x7F; // Native loaded clear precedes even failed FindNext.
                int target = parentIndex;
                if (member != 1) {
                    target = parentIndex + 1; // Restart strictly after original A0 EVERY search.
                    while (target < 96 && unsigned(images[target][0]) != 0) target++;
                    if (target == 96) break; // Keep the completed prefix; proceed to .DidInit.
                }
                byte[] image = images[target];
                image[0] = 0x10; image[0x24] = 2;
                putWord(image, 8, x);
                putWord(image, 0x32, word(images[parentIndex], 8)); // Read AFTER target X write.
                putWord(image, 12, y); putLong(image, 4, 0x209F52); putWord(image, 2, 0xA7AE);
                image[0x18] = 2; // Actual PPZ zone0 does not select MMZ's branch.
                image[1] = 4; image[0x20] = 0x47; image[0x19] = 8; image[0x16] = 8;
                image[0x23] = images[parentIndex][0x23]; image[0x34] = (byte) member;
                // All other64-byte fields, fractions, subtype and animation bytes are native nonwrites.
            }
            x = (x + RING_DELTAS[deltaAt]) & 0xFFFF;
            y = (y + RING_DELTAS[deltaAt + 1]) & 0xFFFF;
        }
        if ((unsigned(bank[at]) & 1) != 0) {
            Arrays.fill(images[parentIndex], (byte) 0); // Plain DeleteObject, children survive.
            return false;
        }
        return true; // Init falls through Main in this same invocation.
    }

    /** ObjRing_Main:214-232; later children reach this only at their physical visit. */
    private static void ringMain(byte[][] images, int index, int cameraX, int cameraY,
                                 int timeStop, int ringFrame, int[] counts, int[][] pointers) {
        byte[] image = images[index];
        if ((unsigned(image[1]) & 0x80) == 0) {
            int distance = ((word(image, 0x32) & 0xFF80) - ((cameraX - 0x80) & 0xFF80)) & 0xFFFF;
            if (distance > 0x280) { Arrays.fill(image, (byte) 0); return; }
        }
        if (timeStop == 0) image[0x1A] = (byte) ringFrame;
        nativeDrawObject(image, 32 + index, cameraX, cameraY, counts, pointers);
    }

    /** DrawObject:137-196; no mapping reads, bit7-set, queue drain, SAT, camera step or producer call. */
    private static void nativeDrawObject(byte[] image, int whole, int cameraX, int cameraY,
                                         int[] counts, int[][] pointers) {
        image[1] &= 0x7F;
        if ((unsigned(image[1]) & 12) != 0) {
            int width = unsigned(image[0x19]), dx = (word(image, 8) - cameraX) & 0xFFFF;
            if ((short) ((dx + width) & 0xFFFF) < 0 || (short) ((dx - width) & 0xFFFF) >= 320) return;
            int radius = unsigned(image[0x16]), y = word(image, 12);
            if (cameraY < 0x100) { if (y >= 0x800) y = (y - 0x800) & 0xFFFF; }
            else if (cameraY >= 0x700 && y < 0x100) y = (y + 0x800) & 0xFFFF;
            int dy = (y - cameraY) & 0xFFFF;
            if ((short) ((dy + radius) & 0xFFFF) < 0 || (short) ((dy - radius) & 0xFFFF) >= 224) return;
        }
        int bucket = unsigned(image[0x18]) & 7;
        if (counts[bucket] >= 0x7E) return;
        pointers[bucket][counts[bucket] >>> 1] = whole; counts[bucket] += 2;
    }

    // Original USA B MMD $CF94..$CFDB, independently hash-authenticated before authoring.
    // Object Tile IDs (Act 1 Past).asm:7-44: complete72, never A74 or adjacent Animal bytes.
    private static final byte[] SPRING_TILE_IDS = java.util.HexFormat.of().parseHex(
            "002400260028002a002c002e00380032003600340030003a003c003e0040004200440046"
                    + "2396243f23ed2000240343340320038140004000037203468328000000000490039e0000");

    /**
     * N ObjSpring:130-167 / Init:186-244. Native ordinary Init returns before Main.
     * This first-prefix model owns only Init or a nonzero bit7-clear display-only wrapper.
     * A real future control/mirror reach is a source-model gap, never a fabricated Solid result.
     * DrawObject consumes the LIVE image before parent integer stores and anchor deletion.
     */
    private static void springInitOrDisplayOnly(byte[][] images, int index, int cameraX, int cameraY,
                                                 int[] counts, int[][] pointers) {
        byte[] image = images[index];
        assertNotEquals(5, unsigned(image[0x25]), "Reached Spring mirror needs its own literal source model");
        int routine = unsigned(image[0x24]);
        if (routine == 0) {
            image[0x24] += 2; putLong(image, 4, 0x2099F8); putWord(image, 2, 0x0520);
            image[1] |= 4; image[0x19] = 0x10; image[0x16] = 8;
            putWord(image, 0x36, word(image, 8)); image[0x18] = 4;
            int subtype = unsigned(image[0x28]);
            if ((subtype & 4) != 0) {
                image[0x24] = 8; image[0x19] = 8; image[0x16] = 0x10;
                putLong(image, 4, 0x2099F8 + 6);
            } else if ((subtype & 8) != 0) {
                image[0x24] = 0x14; image[0x19] = 0x18; image[0x16] = 0x0C;
                putLong(image, 4, 0x2099F8 + 80);
                // SetObjectTileID:7-17: signed directory WORD, then subtype2 WORD index.
                int subtype2 = unsigned(image[0x29]);
                assertEquals(0, subtype2, "Reached B selectorF subtype2 needs its own bounded source contract");
                int target = (short) word(SPRING_TILE_IDS, 0xF * 2);
                assertTrue(target >= 36 && target + subtype2 * 2 + 1 < SPRING_TILE_IDS.length);
                putWord(image, 2, word(SPRING_TILE_IDS, target + subtype2 * 2));
            } else if ((unsigned(image[1]) & 2) != 0) {
                image[0x24] = 0x0E; image[0x22] |= 2;
            }
            if ((subtype & 2) != 0) putWord(image, 2, word(image, 2) | 0x2000);
            putWord(image, 0x30, (subtype & 2) == 0 ? 0xF000 : 0xF600);
        } else {
            assertEquals(0, unsigned(image[1]) & 0x80,
                    "Reached Spring Main/animation/reset needs a literal source model; do not simulate Solid");
        }
        nativeDrawObject(image, 32 + index, cameraX, cameraY, counts, pointers);
        int parentLow = word(image, 0x34);
        if (parentLow != 0) {
            int offset = parentLow - 0xD000;
            assertTrue(offset >= 32 * 64 && offset < 128 * 64 && (offset & 63) == 0,
                    "Reached Spring parent read is outside the complete detached dynamic images");
            byte[] parent = images[offset / 64 - 32]; // No parent ID/liveness gate exists natively.
            putWord(image, 8, word(parent, 8)); putWord(image, 12, word(parent, 12));
            putWord(image, 8, (word(image, 8) + image[0x38]) & 0xFFFF);
            putWord(image, 12, (word(image, 12) + image[0x39]) & 0xFFFF);
        }
        int distance = ((word(image, 0x36) & 0xFF80) - ((cameraX - 0x80) & 0xFF80)) & 0xFFFF;
        if (distance > 0x280) Arrays.fill(image, (byte) 0); // Plain DeleteObject: bank remains untouched.
    }

    private static int unsigned(byte value) { return Byte.toUnsignedInt(value); }
    private static int word(byte[] bytes, int at) { return (unsigned(bytes[at]) << 8) | unsigned(bytes[at + 1]); }
    private static byte[][] copyImages(byte[][] source) {
        byte[][] copy = new byte[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
        return copy;
    }
    private static int[][] copyPointers(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
        return copy;
    }
    private record Scan(CdObjectPlacementRuntime.Status status, byte[] bank, byte[][] images) { }

    /** Native first Init/Main scan for this actual signed-forward camera half only. */
    private static Scan firstScan(List<CdObjectPlacements.Placement> rows, int camera, byte[] carried) {
        assertEquals(766, carried.length); assertTrue(camera >= 0 && camera < 0x8000); assertEquals(497, rows.size());
        byte[] bank = carried.clone(); bank[0] = bank[1] = 1;
        int right = 0, left = 0; int bound = camera < 0x80 ? 0 : (camera - 0x80) & 0xFFFF; bound &= 0xFF80;
        while (bound > rowX(rows, right)) { if (rows.get(right).hasSavedFlag()) bank[0]++; right++; }
        boolean borrow = bound < 0x80; bound = (bound - 0x80) & 0xFFFF;
        if (!borrow) while (bound > rowX(rows, left)) { if (rows.get(left).hasSavedFlag()) bank[1]++; left++; }
        byte[][] images = new byte[96][64]; int chunk = camera & 0xFF80; bound = (chunk + 0x280) & 0xFFFF; int savedId = 0;
        while (bound > rowX(rows, right)) {
            var row = rows.get(right);
            if (row.hasSavedFlag()) { savedId = Byte.toUnsignedInt(bank[0]); bank[0]++; }
            boolean allocated = true;
            if ((row.periodFlags() & 0x20) != 0) {
                int at = 2 + 3 * savedId; boolean loaded = false;
                if (row.hasSavedFlag()) { loaded = (bank[at] & 0x80) != 0; bank[at] |= (byte) 0x80; }
                if (!loaded) {
                    int free = 0; while (free < 96 && images[free][0] != 0) free++;
                    if (free == 96) allocated = false;
                    else {
                        byte[] image = images[free]; image[8] = (byte) (row.centreX() >>> 8); image[9] = (byte) row.centreX();
                        int y = row.packedY() & 0xFFF; image[12] = (byte) (y >>> 8); image[13] = (byte) y;
                        int flips = (row.packedY() >>> 14) & 3; image[1] = image[0x22] = (byte) flips;
                        if (row.hasSavedFlag()) image[0x23] = (byte) savedId;
                        image[0] = (byte) (row.encodedId() & 0x7F); image[0x28] = (byte) row.subtype(); image[0x29] = (byte) row.subtype2();
                    }
                }
            }
            if (!allocated) { if (row.hasSavedFlag()) { bank[0]--; bank[2 + 3 * savedId] &= 0x7F; } break; }
            right++;
        }
        borrow = bound < 0x300; bound = (bound - 0x300) & 0xFFFF;
        if (!borrow) while (bound > rowX(rows, left)) { if (rows.get(left).hasSavedFlag()) bank[1]++; left++; }
        return new Scan(new CdObjectPlacementRuntime.Status(2, chunk, right, left,
                Byte.toUnsignedInt(bank[0]), Byte.toUnsignedInt(bank[1]), 0, 0), bank, images);
    }
    private static int rowX(List<CdObjectPlacements.Placement> rows, int at) {
        assertTrue(at >= 0 && at <= rows.size(), "Native scan must stay in the complete authenticated common list");
        return at == rows.size() ? 0xFFFF : rows.get(at).centreX();
    }
}
