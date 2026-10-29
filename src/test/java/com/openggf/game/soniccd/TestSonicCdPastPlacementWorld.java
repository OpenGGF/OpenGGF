package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual USA five-jump load and first native Main402/primary0 dispatch.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/Main.asm:186-216,333-403;
 * Object Spawner.asm:42-101,107-120,176-319; Sonic/Main.asm:56-106,135-155.
 * The detached scan below writes only expected arrays; it never calls a gameplay
 * owner or uses completed effects to manufacture expected state. Restore contrasts
 * are explicitly test-owned, undone at a healthy cut before actual forwarding.
 * DYNAMIC_OBJECTS remains a failed first iteration, not returned7775 or Past parity.
 */
@Isolated
class TestSonicCdPastPlacementWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualSavedObjectBankSurvivesProgramRetirementFirstLoadingServiceAndCompletedFade() throws Exception {
        var p = enterPast(); var h = p.harness(); var frame = p.frame();
        assertEquals(SonicCdGameModule.PastLoadPhase.FIRST_PLC, h.module().pastLoadState().phase());
        assertEquals(766, p.carried().length());
        assertTrue(nonzeroBody(p.carried().copy()), "The unchanged route must retain a real nonzero saved bank, not a vacuous zero-bank witness");
        assertSame(p.assets().placements(), p.placement().placements());
        assertTrue(p.placement().sourceIdentity().isEmpty(), "A source identity must not be forged for the B owner");
        assertUnscanned(p);
        var clock = frame.vIntClock(); var world = h.mode().getWorldSession();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var nativeBefore = captureNative(h);
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        contrast(p.placement());
        registry.restoreCourse(course); assertNativeCapture(nativeBefore, h); assertUnscanned(p);
        assertEquals(clock, frame.vIntClock(), "Restoring carried flags must not execute the first C service");
        step(h, route(7730));
        assertEquals(SonicCdGameModule.PastLoadPhase.SECTION_PLC, h.module().pastLoadState().phase());
        assertUnscanned(p);
        var ready = finishLoading(p, 7730);
        assertEquals(7774, ready.returned());
        assertEquals(44L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames());
        assertUnscanned(p);
        assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty());
    }

    @Test void actualFirstUnpausedMainSpawnsAllNativeImagesThenInitializesOnlyPrimaryBeforeReservedHud() throws Exception {
        var p = finishLoading(enterPast(), 7729);
        var source = p.frame().state(); var environment = p.frame().environment();
        var expected = firstScan(p.assets().placements().records(), camera(p), p.carried().copy());
        assertTerminal(p, source, environment, expected, 45L, 8, 0);
    }

    @Test void fullRestoreRecoversTheCompleteHealthyPlacementCutBeforeTheRealScanAndPrimaryInit() throws Exception {
        var p = finishLoading(enterPast(), 7729); var h = p.harness();
        var before = captureNative(h); var registry = h.mode().getRewindRegistry(); var full = registry.capture();
        var source = p.frame().state(); var environment = p.frame().environment();
        var expected = firstScan(p.assets().placements().records(), camera(p), p.carried().copy());
        contrast(p.placement());
        registry.restore(full); assertNativeCapture(before, h); assertUnscanned(p);
        assertSame(p.placement(), actualField(p.visit(), "placement"));
        assertEquals(44L, p.frame().vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, source, environment, expected, 45L, 8, 0);
    }

    @Test void pausedMainNeverSpawnsOrInitializesAndCourseRestoreForwardsTheActualUnpauseOnce() throws Exception {
        var p = finishLoading(enterPast(), 7729); var h = p.harness(); var frame = p.frame();
        var actor = frame.captureActor();
        step(h, start(true));
        assertTrue(frame.paused()); assertFalse(frame.closed());
        assertEquals(45L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(0, frame.environment().globals().levelFrames());
        assertEquals(actor, frame.captureActor()); assertUnscanned(p); assertNoObjectCalls(p);
        step(h, start(false));
        assertTrue(frame.paused()); assertFalse(frame.closed());
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(actor, frame.captureActor()); assertUnscanned(p); assertNoObjectCalls(p);
        var source = frame.state(); var environment = frame.environment();
        var registry = h.mode().getRewindRegistry(); var before = captureNative(h); var course = registry.captureCourse();
        var expected = firstScan(p.assets().placements().records(), camera(p), p.carried().copy());
        contrast(p.placement());
        registry.restoreCourse(course); assertNativeCapture(before, h); assertUnscanned(p);
        assertEquals(46L, frame.vIntClock().absoluteValue().orElseThrow());
        assertTerminal(p, source, environment, expected, 47L, 0x80, 0x80);
    }

    private record Prepared(Harness harness, Object visit, CdPlayerFrameRuntime frame,
                            CdObjectPlacementRuntime placement, CdR11bAssets assets,
                            CdAssetBytes carried, int returned) { }
    private record ExpectedScan(CdObjectPlacementRuntime.Status status, byte[] bank, byte[][] images) { }

    private static Prepared enterPast() throws Exception {
        var h = opening(); var m = h.module();
        var old = assertInstanceOf(CdOpeningObjectRuntime.class, m.getGameService(NativeDynamicObjectPass.class));
        for (int call = 1; call <= 7728; call++) step(h, route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING, m.levelProgramState().phase());
        var bank = old.placement().capture().savedFlags(); // Before the actual old-owner retirement.
        step(h, route(7729));
        var frame = m.requirePlayerFrame(); var visit = actualField(m, "pastLoading");
        var placement = assertInstanceOf(CdObjectPlacementRuntime.class, actualField(visit, "placement"));
        return new Prepared(h, visit, frame, placement, m.pastLoadState().assets(), bank, 7729);
    }
    private static Prepared finishLoading(Prepared p, int completed) throws Exception {
        int returned = completed;
        for (int call = completed + 1; call <= 7800; call++) {
            if (p.harness().module().pastLoadState().phase() == SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL)
                return new Prepared(p.harness(), p.visit(), p.frame(), p.placement(), p.assets(), p.carried(), returned);
            step(p.harness(), route(call)); returned = call;
        }
        fail("The unchanged bounded route must reach the actual pre-ordinary Past cut");
        throw new AssertionError();
    }
    private static int camera(Prepared p) {
        var scroll = (SonicCdPlayerPresentation.ScrollCapture) p.harness().module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        return scroll.state().cameraX();
    }
    private static void assertUnscanned(Prepared p) {
        var snapshot = p.placement().capture(); byte[] bank = p.carried().copy();
        assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0,
                Byte.toUnsignedInt(bank[0]), Byte.toUnsignedInt(bank[1]), -1, -1), snapshot.status());
        assertArrayEquals(bank, snapshot.savedFlags().copy(), "All766 bytes survive; Init has not rewritten even the header");
        for (int i = 0; i < 96; i++) {
            var image = snapshot.pool().slots().get(i);
            assertEquals(32 + i, image.identity().wholeIndex());
            assertArrayEquals(new byte[64], image.copyBytes(), "Native-cleared dynamic slot" + (32 + i));
        }
    }
    private static void assertNoObjectCalls(Prepared p) throws Exception {
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.PRIMARY_INIT));
    }
    private static void contrast(CdObjectPlacementRuntime placement) {
        var old = placement.capture();
        placement.pool().write(placement.pool().state(127).moveByte(CdObjectState.ByteField.ID, 0x3F));
        if ((placement.savedFlag(1, 0) & 0x40) == 0)
            placement.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_7);
        else placement.bclrSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_7);
        assertFalse(Arrays.equals(old.savedFlags().copy(), placement.capture().savedFlags().copy()));
        assertEquals(0x3F, placement.pool().state(127).id());
        // This is a restore-only contrast at a healthy cut, never a forward ingress.
    }
    private static LogicalInputSnapshot start(boolean pressed) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, pressed, pressed), PlayerInputState.neutral());
    }
    private static boolean nonzeroBody(byte[] bank) {
        for (int i = 2; i < bank.length; i++) if (bank[i] != 0) return true;
        return false;
    }

    private static void assertTerminal(Prepared p, CdPlayerBody.State source, CdPlayerBody.Environment before,
                                       ExpectedScan expected, long clock, int held, int tap) throws Exception {
        var h = p.harness(); var frame = p.frame(); var art = frame.artState();
        var world = h.mode().getWorldSession(); long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var values = actualField(p.visit(), "values");
        var cpuSat = assertInstanceOf(CdAssetBytes.class, actualField(values, "cpuSat"));
        var reserved = assertInstanceOf(CdAssetBytes.class, actualField(values, "hudObjects"));
        assertEquals(0, source.motion().routine()); assertEquals(1, source.header().slotId());
        assertEquals(0, before.movement().gates().miniSonic(), "The real saved mini byte must admit normal Init; do not stage a replacement");
        assertEquals(0, before.globals().debugMode()); assertEquals(0, before.movement().gates().timeZone());
        assertEquals(0, before.globals().levelFrames()); assertEquals(44L + (clock == 47L ? 2 : 0), frame.vIntClock().absoluteValue().orElseThrow());
        var ringPrefix = expectedPastRingPrefix(h, p.visit(), frame);
        assertEquals(expected.status(), ringPrefix.status(), "Ring owns no placement cursor/header rewrite");
        expected = new ExpectedScan(ringPrefix.status(), ringPrefix.bank(), ringPrefix.images());
        var input = held == 8 ? route(p.returned() + 1) : start(true);
        var failure = assertThrows(SonicCdGameModule.UnsupportedPastLoading.class, () -> step(h, input));
        assertEquals(ringPrefix.boundary(), failure.boundary());
        // Retained references/read-only values only. No closed-Frame capture, registry
        // restore, module require accessor, source helper replay or another service.
        assertTrue(frame.closed()); assertFalse(frame.paused());
        assertEquals(clock, frame.vIntClock().absoluteValue().orElseThrow());
        assertEquals(expectedInit(source), frame.state());
        assertEquals(expectedInitEnvironment(before, held, tap), frame.environment());
        assertTrue(frame.display().isEmpty(), "ObjSonic_Init does not fall through to Main/Animate/DrawObject");
        assertEquals(art, frame.artState(), "Init cannot prime a DPLC or mutate resident pixels");
        var actual = p.placement().capture();
        assertEquals(expected.status(), actual.status()); assertArrayEquals(expected.bank(), actual.savedFlags().copy());
        int spawned = 0;
        for (int i = 0; i < 96; i++) {
            var image = actual.pool().slots().get(i);
            assertEquals(32 + i, image.identity().wholeIndex());
            assertArrayEquals(expected.images()[i], image.copyBytes(), "Complete native slot" + (32 + i));
            if (image.id() != 0) spawned++;
        }
        assertEquals(java.util.Arrays.stream(expected.images()).filter(image -> image[0] != 0).count(), spawned,
                "Native Ring expansion/deletion determines the post-prefix occupancy");
        assertSame(p.placement(), actualField(p.visit(), "placement"));
        var afterValues = actualField(p.visit(), "values");
        assertArrayEquals(cpuSat.copy(), assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "cpuSat")).copy());
        var afterReserved = assertInstanceOf(CdAssetBytes.class, actualField(afterValues, "hudObjects"));
        assertArrayEquals(expectedPastReservedHud(), afterReserved.copy(),
                "Reserved2/3/5/31 are retained across the following native Ring prefix");
        assertReservedImages(afterReserved.copy());
        var calls = assertInstanceOf(List.class, actualField(afterValues, "calls"));
        assertEquals(1, Collections.frequency(calls, SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertEquals(1, Collections.frequency(calls, SonicCdGameModule.PastLoadCall.PRIMARY_INIT));
        assertEquals(1, Collections.frequency(calls, SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
        assertEquals(ringPrefix.completedRingWholes().size(), Collections.frequency(calls, SonicCdGameModule.PastLoadCall.RING_BODY));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.UPDATE_HUD) < calls.indexOf(SonicCdGameModule.PastLoadCall.ORDINARY_SERVICE));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.ORDINARY_SERVICE) < calls.indexOf(SonicCdGameModule.PastLoadCall.LEVEL_FRAME_INCREMENT));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.LEVEL_FRAME_INCREMENT) < calls.indexOf(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS) < calls.indexOf(SonicCdGameModule.PastLoadCall.PRIMARY_INIT));
        assertTrue(calls.indexOf(SonicCdGameModule.PastLoadCall.PRIMARY_INIT) < calls.indexOf(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
        assertSame(world, h.mode().getWorldSession());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty()); assertNull(h.mode().getLevelManager().getLevelGamestate());
        assertEquals(0, h.mode().getLevelManager().getFrameCounter());
        System.out.println("PAST_PLACEMENT boundary=" + ringPrefix.boundary() + " recipeIndex=" + (p.returned() + 1)
                + " clock=" + clock + " levelFrames=" + frame.environment().globals().levelFrames()
                + " occupiedAfterRings=" + spawned + " right=" + actual.status().rightRecordIndex()
                + " left=" + actual.status().leftRecordIndex() + " firstID=" + actual.pool().slots().getFirst().id());
    }
    private static void assertReservedImages(byte[] actual) {
        assertArrayEquals(expectedPastReservedHud(), actual);
    }
    private static CdPlayerBody.State expectedInit(CdPlayerBody.State source) {
        var m = source.motion();
        int charge = m.charge() == 0 ? 0 : Math.min((m.charge() + 1) & 0xFF, (m.flags() & 4) == 0 ? 30 : 45);
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                2, m.angle(), m.flags(), 4, 9, 19, m.animation(), m.previousAnimation(), m.playerJump(), charge,
                m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        return new CdPlayerBody.State(motion, source.animation(), new CdPlayerBody.Header(source.header().slotId(),
                0x230F60, 0x780, 2, 0x18), source.timers(), source.history(), source.rotationDistance(), source.objectLinks(), source.rotationAngle());
    }
    private static CdPlayerBody.Environment expectedInitEnvironment(CdPlayerBody.Environment before, int held, int tap) {
        var e = before.movement(); var g = before.globals(); var c = before.controls();
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 12, 0x80),
                e.bounds(), e.gates(), e.globals()), new CdPlayerBody.Controls(held, tap, c.logicalHeld(), c.logicalTap(), c.controlLocked()),
                new CdPlayerBody.Globals(1, g.cameraXCentre(), g.timeAttackMode(), g.debugMode(), g.debugCheat(),
                        g.timeWarpTimer(), g.invincible(), g.bossMusic()), before.dynamicSlots());
    }

    /** Source-local first Init/Main scan only: detached arrays, no producer calls.
     * This actual route is restricted to the signed-forward camera half, rather
     * than claiming the whole arbitrary-WORD spawner domain is independently covered.
     */
    private static ExpectedScan firstScan(List<CdObjectPlacements.Placement> rows, int camera, byte[] carried) {
        assertEquals(766, carried.length); assertTrue(camera >= 0 && camera < 0x8000, "Actual fixture requires the native signed-forward first scan");
        assertEquals(497, rows.size()); byte[] bank = carried.clone(); bank[0] = bank[1] = 1;
        int right = 0, left = 0; int bound = camera < 0x80 ? 0 : (camera - 0x80) & 0xFFFF; bound &= 0xFF80;
        while (bound > rowX(rows, right)) { if (rows.get(right).hasSavedFlag()) bank[0]++; right++; }
        boolean borrow = bound < 0x80; bound = (bound - 0x80) & 0xFFFF;
        if (!borrow) while (bound > rowX(rows, left)) { if (rows.get(left).hasSavedFlag()) bank[1]++; left++; }
        byte[][] images = new byte[96][64];
        int chunk = camera & 0xFF80; bound = (chunk + 0x280) & 0xFFFF; int savedId = 0;
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
            if (!allocated) {
                if (row.hasSavedFlag()) { bank[0]--; bank[2 + 3 * savedId] &= 0x7F; }
                break;
            }
            right++;
        }
        borrow = bound < 0x300; bound = (bound - 0x300) & 0xFFFF;
        if (!borrow) while (bound > rowX(rows, left)) { if (rows.get(left).hasSavedFlag()) bank[1]++; left++; }
        return new ExpectedScan(new CdObjectPlacementRuntime.Status(2, chunk, right, left,
                Byte.toUnsignedInt(bank[0]), Byte.toUnsignedInt(bank[1]), 0, 0), bank, images);
    }
    private static int rowX(List<CdObjectPlacements.Placement> rows, int at) {
        assertTrue(at >= 0 && at <= rows.size(), "Literal scan exceeded the authenticated common list");
        return at == rows.size() ? 0xFFFF : rows.get(at).centreX();
    }
}
