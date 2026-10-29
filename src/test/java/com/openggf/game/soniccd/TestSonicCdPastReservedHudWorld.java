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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original-USA reserved HUD dispatch after Main402/primary Init.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: HUD and Points/Main.asm:67-140;
 * Monitor and Time Post/Main.asm:7-71,269-280; Object Functions.asm:11-33,137-196.
 * Expected images, native scan and arithmetic are detached test values only.
 * The retained queue is a terminal partial pass: no DrawObjects/CPU SAT/framebuffer
 * or completed7775 is claimed. No production helper/effect replay supplies an oracle.
 */
@Isolated
class TestSonicCdPastReservedHudWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualReservedBodiesInitializeAllNativeBytesAndQueueOnlyThreeHudRefsBeforeDynamics() throws Exception {
        var p = ready();
        var before = before(p);
        assertTerminal(p, before, 45L, 8, 0);
    }

    @Test void fullHealthyCutRestoreUndoesBankAndPoolContrastBeforeTheActualReservedWalk() throws Exception {
        var p = ready(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var nativeBefore = captureNative(h); var before = before(p);
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 45L, 8, 0);
    }

    @Test void pauseAndCourseRestorePreserveUnrunReservedSlotsThenUnpauseQueuesTheirRealRefsOnce() throws Exception {
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
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, before, 47L, 0x80, 0x80);
    }

    private record Prepared(Harness harness, Object visit, CdPlayerFrameRuntime frame,
                            CdObjectPlacementRuntime placement, CdR11bAssets assets,
                            CdAssetBytes carried, int returned) { }
    private record ExpectedScan(CdObjectPlacementRuntime.Status status, byte[] bank, byte[][] images) { }
    private record Before(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                          SonicCdGameModule.PastLoadState observed, Object values,
                          SonicCdPlayerPresentation.ScrollCapture scroll, Object presentation,
                          Object world, long generation, boolean paused, TestSonicCdPastRingWorld.RingPrefix prefix) { }

    private static Prepared ready() throws Exception {
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
        return p;
    }
    private static Before before(Prepared p) throws Exception {
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
        return new Before(frame.captureActor(), frame.captureShared(), h.module().pastLoadState(),
                actualField(p.visit(), "values"), scroll, actualField(h.module(), "playerPresentation"),
                h.mode().getWorldSession(), h.mode().getLevelManager().getCompletedProductionLoadGeneration(),
                frame.paused(), expectedPastRingPrefix(h, p.visit(), frame));
    }
    private static void assertNoReservedPass(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "drawQueue")); assertNull(actualField(p.visit(), "activeReserved"));
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.PRIMARY_INIT));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
    }
    private static CdAssetBytes reserved(Object visit) throws Exception {
        return assertInstanceOf(CdAssetBytes.class, actualField(actualField(visit, "values"), "hudObjects"));
    }
    private static void assertUnscanned(Prepared p) {
        var s = p.placement().capture(); byte[] bank = p.carried().copy();
        assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0,
                Byte.toUnsignedInt(bank[0]), Byte.toUnsignedInt(bank[1]), -1, -1), s.status());
        assertArrayEquals(bank, s.savedFlags().copy());
        for (int i = 0; i < 96; i++) {
            assertEquals(32 + i, s.pool().slots().get(i).identity().wholeIndex());
            assertArrayEquals(new byte[64], s.pool().slots().get(i).copyBytes());
        }
    }
    private static void contrast(CdObjectPlacementRuntime placement) {
        var before = placement.capture();
        placement.pool().write(placement.pool().state(127).moveByte(CdObjectState.ByteField.ID, 0x3F));
        if ((placement.savedFlag(1, 0) & 0x40) == 0) placement.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_7);
        else placement.bclrSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_7);
        assertFalse(Arrays.equals(before.savedFlags().copy(), placement.capture().savedFlags().copy()));
        assertEquals(0x3F, placement.pool().state(127).id());
        // Constructed restore contrast only: always removed before a real forward row.
    }
    private static LogicalInputSnapshot start(boolean pressed) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, pressed, pressed), PlayerInputState.neutral());
    }

    private static void assertTerminal(Prepared p, Before before, long clock, int held, int tap) throws Exception {
        var h = p.harness(); var frame = p.frame();
        var placed = p.placement(); var pool = placed.pool(); var artOwner = actualField(frame, "art");
        var cpuBefore = assertInstanceOf(CdAssetBytes.class, actualField(before.values(), "cpuSat"));
        assertArrayEquals(new byte[640], cpuBefore.copy(), "The source-established first pass has not built a CPU SAT");
        var failure = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class,
                () -> step(h, held == 8 ? route(p.returned() + 1) : start(true)));
        assertEquals(before.prefix().boundary(), failure.boundary());
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
        assertTrue(frame.display().isEmpty()); assertEquals(before.shared().artState(), actualField(artOwner, "state"));
        assertSame(frame, actualField(h.module(), "playerFrame")); assertNull(actualField(h.module(), "playerActor"));
        assertSame(p.assets(), actualField(p.visit(), "assets")); assertSame(p.placement(), actualField(p.visit(), "placement"));
        assertEquals(before.prefix().status(), placed.status()); assertArrayEquals(before.prefix().bank(), assertInstanceOf(byte[].class, actualField(placed, "savedFlags")));
        int occupied = 0;
        for (int i = 0; i < 96; i++) {
            var image = pool.state(32 + i); assertEquals(32 + i, image.identity().wholeIndex());
            assertArrayEquals(before.prefix().images()[i], image.copyBytes(), "Native spawn image before body" + (32 + i));
            if (image.id() != 0) occupied++;
        }
        assertEquals(java.util.Arrays.stream(before.prefix().images()).filter(image -> image[0] != 0).count(), occupied,
                "Native Ring expansion/deletion determines the post-prefix occupancy");
        assertEquals(Byte.toUnsignedInt(before.prefix().images()[0][0]), pool.state(32).id(),
                "Dynamic32 retains its native completed Ring image or legitimate deletion");
        assertArrayEquals(expectedReserved(), reserved(p.visit()).copy(), "All31 complete images after only2/3/5/31 execute");
        assertNull(actualField(p.visit(), "activeReserved"), "The real invocation scope retires before the next-body fault");
        var queue = assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue"));
        assertQueue(queue, before.prefix(), cpuBefore.copy());
        assertNull(actualField(p.visit(), "activeRing"), "The actual Ring scope retires before the next native owner");
        assertNull(actualField(p.visit(), "activeSpring"), "The actual Spring scope retires before the next native owner");
        assertOrdinaryNonwrites(p, before, clock == 47L);
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        for (var call : List.of(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS,
                SonicCdGameModule.PastLoadCall.PRIMARY_INIT, SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS))
            assertEquals(1, Collections.frequency(calls, call), "One genuine completed" + call);
        assertEquals(before.prefix().completedRingWholes().size(), Collections.frequency(calls, SonicCdGameModule.PastLoadCall.RING_BODY));
        assertEquals(before.prefix().completedSpringWholes().size(), Collections.frequency(calls, SonicCdGameModule.PastLoadCall.SPRING_BODY));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.UPDATE_HUD) < calls.indexOf(SonicCdGameModule.PastLoadCall.ORDINARY_SERVICE));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.ORDINARY_SERVICE) < calls.indexOf(SonicCdGameModule.PastLoadCall.LEVEL_FRAME_INCREMENT));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.LEVEL_FRAME_INCREMENT) < calls.indexOf(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS) < calls.indexOf(SonicCdGameModule.PastLoadCall.PRIMARY_INIT));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.PRIMARY_INIT) < calls.indexOf(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
        assertSame(before.world(), h.mode().getWorldSession());
        assertEquals(before.generation(), h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty()); assertNull(h.mode().getLevelManager().getLevelGamestate());
        assertEquals(0, h.mode().getLevelManager().getFrameCounter());
        System.out.println("PAST_RESERVED_HUD boundary=" + before.prefix().boundary() + " recipeIndex=" + (p.returned() + 1)
                + " clock=" + clock + " levelFrames=" + frame.environment().globals().levelFrames()
                + " occupiedAfterRings=" + occupied + " hudRefs=2/3/5 iconFrame=19 cpuSatCount=0");
    }
    private static void assertQueue(CdObjectDrawQueue queue, TestSonicCdPastRingWorld.RingPrefix expectedPrefix, byte[] cpuBefore) throws Exception {
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, queue.phase(), "Fault closes admission without draining the partial pass");
        var counts = assertInstanceOf(int[].class, actualField(queue, "pointerByteCounts"));
        assertArrayEquals(expectedPrefix.pointerByteCounts(), counts, "Native pending pointer WORD bytes, not SAT pieces");
        var expectedPointers = expectedPrefix.pointerWholes();
        var pointers = assertInstanceOf(CdObjectDrawQueue.CdSlotRef[][].class, actualField(queue, "pointers"));
        assertEquals(8, pointers.length);
        for (int bucket = 0; bucket < 8; bucket++) {
            assertEquals(63, pointers[bucket].length);
            for (int ordinal = 0; ordinal < 63; ordinal++) {
                int whole = expectedPointers[bucket][ordinal];
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
        var section = assertInstanceOf(CdR11bSectionArt.State.class, actualField(after, "section")); var b = old.section();
        assertEquals(b.sectionWord(), section.sectionWord()); assertEquals(b.queue(), section.queue());
        assertArrayEquals(b.codeTable().copy(), section.codeTable().copy()); assertEquals(b.decoder(), section.decoder());
        assertEquals(b.savedDecoder(), section.savedDecoder()); assertEquals(b.remainingTilesWord(), section.remainingTilesWord());
        assertEquals(b.processBudgetWord(), section.processBudgetWord()); assertEquals(b.hintWord(), section.hintWord());
        assertEquals(b.padWord(), section.padWord()); assertEquals(b.stopped(), section.stopped());
        for (var region : CdR11bCameraArtResidency.Region.values())
            if (region != CdR11bCameraArtResidency.Region.AT_B060 && region != CdR11bCameraArtResidency.Region.AT_B4A0)
                assertEquals(b.residency().region(region), section.residency().region(region), "Unwritten current" + region);
        assertEquals(unpause ? List.of(new SonicCdGameModule.PastSubCpuSound(SonicCdGameModule.PastMusicCommand.SCMD_UNPAUSEPCM))
                : List.of(), actualField(p.visit(), "sounds"), "Reserved bodies emit no new sound request");
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

    /** Native first Init/Main scan for this actual signed-forward camera half only. */
    private static ExpectedScan firstScan(List<CdObjectPlacements.Placement> rows, int camera, byte[] carried) {
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
        return new ExpectedScan(new CdObjectPlacementRuntime.Status(2, chunk, right, left,
                Byte.toUnsignedInt(bank[0]), Byte.toUnsignedInt(bank[1]), 0, 0), bank, images);
    }
    private static int rowX(List<CdObjectPlacements.Placement> rows, int at) {
        assertTrue(at >= 0 && at <= rows.size(), "Native scan must stay in the complete authenticated common list");
        return at == rows.size() ? 0xFFFF : rows.get(at).centreX();
    }
}
