package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.objects.CdMosqui;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine USA World ingress at Root's measured returned8084 / unbound3D8085.
 * Mosqui/Main.asm:26-151 and Collision Check.asm:157-179 at N1ecc5f3a.
 * Only native object fields are seeded in fixed scenes; Frame, camera, spawner, terrain,
 * art, service order and private authority stay actual. No Body/helper/effect replay.
 * The sole fixed Dive advances at most12 actual neutral rows from one fixed ingress, with no search or retry.
 * These source-authored cases are not a measured continuation or a current-pixel/parity claim.
 */
@Isolated
class TestSonicCdPastMosquiWorld {
    private static final int FIXED = 127;
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actual8085InitAndBoundedOrdinaryRowsRetainOwnersAndReplayBothCompleteCuts() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame();
        var body = assertInstanceOf(CdMosqui.class, actualField(p.visit(), "mosqui"));
        var world = h.mode().getWorldSession(); var source = frame.stageSource();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        for (int call = 7775; call <= 8084; call++) { step(h, route(call)); healthy(p, body, world, generation, source); }
        assertEquals(0, calls(p)); assertArrayEquals(new byte[64], p.placement().pool().state(51).copyBytes());
        assertEquals(7685, frame.state().motion().centreX16_16() >>> 16);
        assertEquals(818, frame.state().motion().centreY16_16() >>> 16);
        assertEquals(7424, p.placement().status().previousChunkWord());
        assertEquals(397, p.placement().status().rightRecordIndex());
        assertEquals(363, p.placement().status().leftRecordIndex());
        var row = p.assets().placements().records().stream()
                .filter(r -> r.appliesTo(CdPeriod.PAST) && r.objectId() == 0x3D && r.centreX() == 8115 && r.centreY() == 856).toList();
        assertEquals(1, row.size(), "The fixed measured source row must be unique in actual B");
        replay(p, route(8085), body, world, generation, source);
        var object = p.placement().pool().state(51); var placement = row.getFirst();
        boolean damaged = placement.subtype() != 0;
        assertEquals(0x3D, object.id()); assertEquals(183, object.savedFlagsId());
        assertEquals(placement.subtype(), object.subtype()); assertEquals(placement.subtype2(), object.subtype2());
        assertEquals(0, placement.subtype2(), "Natural first Mosqui selects B72 selector0/secondary0");
        assertEquals(2, object.routine()); assertEquals(0x2396, object.word(TILE));
        assertEquals(damaged ? 0x20B57A : 0x20B53E, object.longBits(MAPPINGS));
        assertEquals(damaged ? 0x20B530 : 0x20B522, object.longBits(MOSQUI_ANIMATION_SCRIPT));
        assertEquals((8115 << 16) + (damaged ? -0x8000 : -0x10000), object.longBits(X_POSITION));
        assertEquals(856, object.centreY()); assertEquals(8115, object.word(MOSQUI_ANCHOR_X));
        assertEquals(1, calls(p)); assertEquals(0, sounds(p));
        assertEquals(7552, p.placement().status().previousChunkWord());
        // Four ordinary input rows, no automatic frontier probe or swallowed native stop.
        for (int call = 8086; call <= 8089; call++) {
            step(h, route(call)); healthy(p, body, world, generation, source);
            assertTrue(calls(p) >= 1); assertEquals(0, sounds(p));
        }
    }

    @Test void fixedNormalAndDamagedAllFiveBRootsUseActualQueueAndMalformedAnimationKeepsItsPrefix() throws Exception {
        var p = healthy7851(); var h = p.harness(); var pool = p.placement().pool();
        var body = assertInstanceOf(CdMosqui.class, actualField(p.visit(), "mosqui"));
        var world = h.mode().getWorldSession(); var source = p.frame().stageSource();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var registry = h.mode().getRewindRegistry(); var pristine = registry.capture(); var before = captureNative(h);
        var neutral = neutral();
        for (int damaged = 0; damaged < 2; damaged++) for (int mapped = 0; mapped < 5; mapped++) {
            registry.restore(pristine); assertNativeCapture(before, h); requireSameChunk(p);
            var entry = fixed(p, damaged != 0).moveByte(ROUTINE, 4).moveByte(MAP_FRAME, mapped)
                    .moveByte(ANIMATION_TIME, 1).moveByte(SPRITE_FLAGS, 4 | (mapped & 3)).moveByte(FLAGS, mapped & 3);
            seed(p, entry);
            if (mapped == 4) replay(p, neutral, body, world, generation, source);
            else { step(h, neutral); healthy(p, body, world, generation, source); }
            var actual = pool.state(FIXED);
            assertEquals(entry.moveByte(ANIMATION_TIME, 0)
                    .moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
            assertEquals(entry.spriteFlags(), actual.spriteFlags() & 0x7F);
            int expected = damaged == 0 ? new int[]{3,2,2,1,1}[mapped] : new int[]{3,2,2,1,2}[mapped];
            assertEquals(expected, cpu(p).provenance().stream().filter(piece -> piece.slotRef().wholeIndex() == FIXED).count(),
                    "Genuine five-frame B mappings are consumed by actual DrawObjects");
            assertEquals(1, calls(p)); assertEquals(0, sounds(p));
        }
        registry.restore(pristine); assertNativeCapture(before, h); requireSameChunk(p);
        var held = fixed(p, false).moveByte(ROUTINE, 4).moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255)
                .moveByte(ANIMATION_TIME, 1).moveLong(MOSQUI_ANIMATION_SCRIPT, 0xDEADBEEF);
        seed(p, held); step(h, neutral); healthy(p, body, world, generation, source);
        assertEquals(0, pool.state(FIXED).unsignedByte(ANIMATION_TIME));
        assertEquals(0xDEADBEEF, pool.state(FIXED).longBits(MOSQUI_ANIMATION_SCRIPT));
        var prior = pool.state(FIXED);
        var failure = assertThrows(CdMosqui.UnsupportedMosqui.class, () -> step(h, neutral));
        assertEquals(CdMosqui.UnsupportedBoundary.ANIMATION_SCRIPT_POINTER, failure.boundary());
        assertEquals(prior.moveByte(ANIMATION_TIME, 255), pool.state(FIXED)); closed(p);
    }

    @Test void fixedNativeDiveProgressionPublishesOnlyScratchBeforeTheActualA7Cut() throws Exception {
        var p = healthy7851(); var h = p.harness(); var pool = p.placement().pool(); var frame = p.frame();
        var body = assertInstanceOf(CdMosqui.class, actualField(p.visit(), "mosqui"));
        var world = h.mode().getWorldSession(); var source = frame.stageSource();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var registry = h.mode().getRewindRegistry();
        var entry = fixed(p, false).moveByte(ROUTINE, 6).moveByte(SPRITE_FLAGS, 0x84)
                .moveWord(Y, ((frame.state().motion().centreY16_16() >>> 16) + frame.state().motion().yRadius() - 14) & 0xFFFF);
        seed(p, entry); boolean landed = false;
        for (int row = 1; row <= 12; row++) {
            healthy(p, body, world, generation, source);
            var old = pool.state(FIXED); var motion = frame.state().motion();
            var presentation = (SonicCdPlayerPresentation) actualField(h.module(), "playerPresentation");
            if ((presentation.pastForegroundImage().cameraX() & 0xFF80) != p.placement().status().previousChunkWord())
                throw new FixedDivePrerequisite("Fixed Dive row" + row + " crosses a prespawn chunk: camera="
                        + presentation.pastForegroundImage().cameraX() + ", previous=" + p.placement().status().previousChunkWord());
            int dx = (short) (old.centreX() - (motion.centreX16_16() >>> 16));
            if (old.id() != 0x3D || old.routine() != 6 || old.signedByte(SPRITE_FLAGS) >= 0
                    || dx <= motion.xRadius() + 12 + 6 || Math.abs(motion.xVelocity()) > 0x600
                    || frame.environment().globals().debugMode() != 0
                    || frame.environment().movement().gates().timeZone() != 0
                    || frame.environment().movement().gates().timeWarpDirection() != 0)
                throw new FixedDivePrerequisite("Fixed Dive row" + row + " loses actual source/visibility/no-contact gates: dx="
                        + dx + ", routine=" + old.routine() + ", flags=" + old.spriteFlags());
            var full = registry.capture(); var before = captureNative(h);
            // A genuine public ordinary negative-control row, then exact restore of this same
            // closed scene. It supplies comparison-only actor values, never gameplay hydration.
            pool.deleteObject(FIXED); step(h, neutral()); healthy(p, body, world, generation, source);
            var controlActor = frame.captureActor(); var controlShared = frame.captureShared();
            registry.restore(full); assertNativeCapture(before, h);
            replay(p, neutral(), body, world, generation, source);
            assertEquals(controlActor, frame.captureActor(), "Native Mosqui writes no actor field");
            assertEnvironmentExceptPrimary(controlShared.environment(), frame.environment());
            var actualShared = frame.captureShared();
            // CdAssetBytes has identity equality; compare complete art values, not wrapper identities.
            assertArrayEquals(controlShared.artState().cpuBuffer().copy(), actualShared.artState().cpuBuffer().copy());
            assertArrayEquals(controlShared.artState().residentBank().copy(), actualShared.artState().residentBank().copy());
            assertEquals(controlShared.artState().lastFrame(), actualShared.artState().lastFrame());
            assertEquals(controlShared.artState().dirty(), actualShared.artState().dirty());
            assertEquals(controlShared.vIntClock(), actualShared.vIntClock()); assertEquals(controlShared.paused(), actualShared.paused());
            assertTrue(frame.environment().movement().globals().primaryAngle() >= 0
                    && frame.environment().movement().globals().primaryAngle() <= 255, "Observe the actual scalar; no terrain oracle is replayed");
            var actual = pool.state(FIXED); var expected = old.addWord(Y, 6);
            if (actual.routine() == 8) {
                assertTrue((short) (actual.centreY() - expected.centreY()) <= 0, "Native <=-8 branch corrects Y before A7");
                expected = expected.moveWord(Y, actual.centreY()).addByte(ROUTINE, 2);
                assertEquals(1, sounds(p)); landed = true;
            } else {
                assertEquals(6, actual.routine()); assertEquals(0, sounds(p));
            }
            assertEquals(expected.moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
            assertEquals(1, calls(p));
            for (var sound : h.module().pastLoadState().sounds()) if (sound instanceof SonicCdGameModule.PastMosquiFm fm)
                assertEquals(CdMosqui.Sound.FM_A7, fm.sound());
            if (landed) break;
        }
        if (!landed) throw new FixedDivePrerequisite("One fixed Dive did not reach actual <=-8/A7 within12 native neutral rows; Y="
                + pool.state(FIXED).centreY() + ". No alternate coordinate or helper-derived floor was tried.");
        // Exact detached floor-distance/corrected-Y expectations remain component coverage.
    }

    @Test void waitDrawsItsClearedIdZeroAndAReachedBadSelectorKeepsTheExactInitPrefix() throws Exception {
        var p = healthy7851(); var h = p.harness(); var pool = p.placement().pool();
        var body = assertInstanceOf(CdMosqui.class, actualField(p.visit(), "mosqui"));
        var world = h.mode().getWorldSession(); var source = p.frame().stageSource();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var registry = h.mode().getRewindRegistry(); var pristine = registry.capture(); var before = captureNative(h);
        registry.restore(pristine); assertNativeCapture(before, h); requireSameChunk(p);
        var waiting = fixed(p, false).moveByte(ROUTINE, 8).moveByte(SPRITE_FLAGS, 4);
        seed(p, waiting); replay(p, neutral(), body, world, generation, source);
        assertArrayEquals(new byte[64], pool.state(FIXED).copyBytes()); assertEquals(1, calls(p)); assertEquals(0, sounds(p));
        assertTrue(cpu(p).provenance().stream().noneMatch(piece -> piece.slotRef().wholeIndex() == FIXED),
                "Actual cleared Wait source is still passed to DrawObject; drain resolves no fabricated Map0");
        registry.restore(pristine); assertNativeCapture(before, h); requireSameChunk(p);
        var bad = fixed(p, false).moveByte(ROUTINE, 0).moveByte(SUBTYPE2, 18).moveWord(TILE, 0xBEEF);
        seed(p, bad); var failure = assertThrows(CdMosqui.UnsupportedMosqui.class, () -> step(h, neutral()));
        assertEquals(CdMosqui.UnsupportedBoundary.TILE_ADDRESS, failure.boundary()); assertEquals(FIXED, failure.wholeIndex());
        var prefix = bad.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, bad.spriteFlags() | 4).moveByte(PRIORITY, 3)
                .moveByte(X_RADIUS, 16).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 16).moveByte(COLLISION_TYPE, 0x2B)
                .moveWord(MOSQUI_ANCHOR_X, bad.centreX());
        assertEquals(prefix, pool.state(FIXED)); closed(p);
    }

    private static final class FixedDivePrerequisite extends AssertionError {
        private FixedDivePrerequisite(String message) { super(message); }
    }
    private static Prepared healthy7851() throws Exception {
        var p = ready(); for (int call = 7775; call <= 7851; call++) step(p.harness(), route(call));
        assertFalse(p.frame().closed()); requireSameChunk(p); return p;
    }
    private static CdObjectState fixed(Prepared p, boolean damaged) {
        var pool = p.placement().pool(); assertArrayEquals(new byte[64], pool.state(FIXED).copyBytes());
        var motion = p.frame().state().motion(); int x = ((motion.centreX16_16() >>> 16) + 96) & 0xFFFF;
        return pool.state(FIXED).moveByte(ID, 0x3D).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 3)
                .moveWord(X, x).moveWord(Y, motion.centreY16_16() >>> 16).moveWord(TILE, 0x2396)
                .moveByte(SUBTYPE, damaged ? 255 : 0).moveByte(X_RADIUS, 16).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 16)
                .moveByte(COLLISION_TYPE, 0x2B).moveWord(MOSQUI_ANCHOR_X, x)
                .moveLong(MAPPINGS, damaged ? 0x20B57A : 0x20B53E).moveLong(MOSQUI_ANIMATION_SCRIPT, damaged ? 0x20B530 : 0x20B522)
                .moveLong(MOSQUI_X_DELTA, damaged ? -0x8000 : -0x10000);
    }
    private static void replay(Prepared p, LogicalInputSnapshot input, CdMosqui body, Object world,
                               long generation, Object source) throws Exception {
        var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, input); healthy(p, body, world, generation, source); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); healthy(p, body, world, generation, source);
        step(h, input); healthy(p, body, world, generation, source); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); healthy(p, body, world, generation, source);
        step(h, input); healthy(p, body, world, generation, source); assertNativeCapture(after, h);
    }
    private static void healthy(Prepared p, CdMosqui body, Object world, long generation, Object source) throws Exception {
        var h = p.harness(); assertSame(world, h.mode().getWorldSession()); assertSame(source, p.frame().stageSource());
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(p.frame(), h.module().requirePlayerFrame()); assertSame(body, actualField(p.visit(), "mosqui"));
        assertFalse(p.frame().closed()); assertTrue(h.module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        retired(p);
    }
    private static void retired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeMosqui")); assertFalse((boolean) actualField(p.visit(), "mosquiPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingMosqui"));
    }
    private static void closed(Prepared p) throws Exception {
        assertTrue(p.frame().closed()); retired(p);
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, ((CdObjectDrawQueue) actualField(p.visit(), "drawQueue")).phase());
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.MOSQUI_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.DRAW_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }
    private static long calls(Prepared p) { return p.harness().module().pastLoadState().calls().stream().filter(c -> c == SonicCdGameModule.PastLoadCall.MOSQUI_BODY).count(); }
    private static long sounds(Prepared p) { return p.harness().module().pastLoadState().sounds().stream().filter(SonicCdGameModule.PastMosquiFm.class::isInstance).count(); }
    private static void requireSameChunk(Prepared p) throws Exception {
        var presentation = (SonicCdPlayerPresentation) actualField(p.harness().module(), "playerPresentation");
        assertEquals(presentation.pastForegroundImage().cameraX() & 0xFF80, p.placement().status().previousChunkWord(), "Fixed source requires no new prespawn interval");
    }
    private static void seed(Prepared p, CdObjectState state) throws Exception {
        var actor = p.frame().captureActor(); var shared = p.frame().captureShared(); var before = p.placement().capture();
        var values = actualField(p.visit(), "values"); p.placement().pool().write(state);
        assertEquals(actor, p.frame().captureActor()); assertEquals(shared, p.frame().captureShared()); assertSame(values, actualField(p.visit(), "values"));
        assertEquals(before.status(), p.placement().status()); assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var old : before.pool().slots()) if (old.identity().wholeIndex() != state.identity().wholeIndex()) assertEquals(old, p.placement().pool().state(old.identity().wholeIndex()));
    }
    private static CdObjectDrawQueue.CpuSat cpu(Prepared p) throws Exception {
        return (CdObjectDrawQueue.CpuSat) ((Optional<?>) actualField(actualField(actualField(p.visit(), "values"), "loop"), "completedDraw")).orElseThrow();
    }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
    private static void assertEnvironmentExceptPrimary(com.openggf.game.soniccd.player.CdPlayerBody.Environment expected,
                                                        com.openggf.game.soniccd.player.CdPlayerBody.Environment actual) {
        assertEquals(expected.controls(), actual.controls()); assertEquals(expected.globals(), actual.globals()); assertEquals(expected.dynamicSlots(), actual.dynamicSlots());
        var e = expected.movement(); var a = actual.movement(); assertEquals(e.physics(), a.physics()); assertEquals(e.bounds(), a.bounds()); assertEquals(e.gates(), a.gates());
        var x = e.globals(); var y = a.globals(); assertEquals(x.lookMode(), y.lookMode()); assertEquals(x.cameraYCentre(), y.cameraYCentre());
        assertEquals(x.secondaryAngle(), y.secondaryAngle()); assertEquals(x.boredTimer(), y.boredTimer()); assertEquals(x.scoreChain(), y.scoreChain());
        assertEquals(x.debugAngle(), y.debugAngle()); assertEquals(x.debugAngleShift(), y.debugAngleShift()); assertEquals(x.debugQuadrant(), y.debugQuadrant()); assertEquals(x.debugFloorDistance(), y.debugFloorDistance());
    }
}
