package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual cold-program five-jump route and fixed object-only World scenes. Native N1ecc5f3a:
 * Robot Generator/Main.asm:25-196,206-229; Explosion/Main.asm:14-106; Object Spawner.asm:305-340.
 * Constructed collision-status1 exercises the native hit branch; it is not natural scanner contact.
 * No actor, controls, camera, terrain, spawner, current-art knowledge or private authority is supplied.
 * Genuine stopped-Solid World reach and naturally produced Robot hit remain component/route gaps.
 */
@Isolated
class TestSonicCdPastRobotGeneratorWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actual8277InitAnd8278MainUseNativeFullImagesAndReplayBothCompleteCuts() throws Exception {
        var p = ordinary(8276); var pool = p.placement().pool(); var before = pool.state(32);
        assertEquals(0, before.id()); assertEquals(0, before.routine());
        var row = p.assets().placements().records().stream().filter(r -> r.appliesTo(CdPeriod.PAST)
                && r.objectId() == 0x2D && r.centreX() == 9064 && r.centreY() == 158).toList();
        assertEquals(1, row.size()); var sourceRow = row.getFirst();
        assertTrue(Math.abs((short) (9064 - (p.frame().state().motion().centreX16_16() >>> 16))) > 0x100,
                "Fixed natural Init must retain the actual no-contact Solid ingress");
        replay(p, route(8277));
        var actual = pool.state(32); int flip = ((sourceRow.packedY() << 2) | (sourceRow.packedY() >>> 14)) & 3;
        var expected = before.moveWord(X, 9064).moveWord(Y, 158).moveByte(SPRITE_FLAGS, flip).moveByte(FLAGS, flip)
                .moveByte(SAVED_FLAGS_ID, 0xDB).moveByte(ID, 0x2D).moveByte(SUBTYPE, sourceRow.subtype()).moveByte(SUBTYPE2, sourceRow.subtype2())
                .addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, flip | 4).moveByte(PRIORITY, 4)
                .moveByte(X_RADIUS, 0x22).moveByte(WIDTH, 0x22).moveByte(Y_RADIUS, 0x20)
                .moveWord(TILE, 0x043F).moveLong(MAPPINGS, 0x20E816).moveLong(ROBOT_GENERATOR_SCHEDULE, 0x20EA54)
                .moveWord(ROBOT_GENERATOR_BASE_Y, 158).moveWord(ROBOT_GENERATOR_TIMER, 5).moveWord(ROBOT_GENERATOR_FLOAT_STEP, 1)
                .moveByte(MAP_FRAME, 3).moveByte(COLLISION_TYPE, 0xFA).addWord(Y, -16)
                .moveByte(ANIMATION_FRAME, 1).moveByte(ANIMATION_TIME, 3);
        assertEquals(flip | 4, actual.spriteFlags() & 0x7F);
        assertEquals(expected.moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        assertEquals(1, calls(p, SonicCdGameModule.PastLoadCall.ROBOT_GENERATOR_BODY));
        assertEquals(0, robotSounds(p));
        replay(p, route(8278)); var main = pool.state(32);
        assertEquals(actual.addWord(ROBOT_GENERATOR_TIMER, 1).addByte(ANIMATION_TIME, -1)
                .moveByte(SPRITE_FLAGS, main.spriteFlags()), main);
        assertEquals(actual.spriteFlags() & 0x7F, main.spriteFlags() & 0x7F);
        assertEquals(1, calls(p, SonicCdGameModule.PastLoadCall.ROBOT_GENERATOR_BODY));
    }

    @Test void fixedHitPublishesGoodFutureAndSharedPointsBeforeRealSolidAndCapturesTheNewValues() throws Exception {
        var p = ordinary(8278); var pool = p.placement().pool(); sameChunk(p);
        assertEquals(0, dev(p).goodFuture()); var ring = p.harness().module().pastLoadState().ring(); var oldScore = score(p);
        assertTrue(Integer.compareUnsigned(oldScore.score(), 999849) < 0, "This fixed ownership case uses an uncapped score");
        assertTrue(Integer.compareUnsigned(oldScore.score() + 150, oldScore.nextLifeScore()) < 0,
                "This fixed score case has no extra-life edge; the selected AddPoints kernel owns edge vectors");
        var entry = fixed(p, 127).moveByte(COLLISION_STATUS, 1); seed(p, entry);
        replay(p, neutral()); var actual = pool.state(127);
        assertEquals(entry.moveByte(COLLISION_TYPE, 0).moveByte(COLLISION_STATUS, 0)
                .moveWord(ROBOT_GENERATOR_TIMER, 0).moveByte(MAP_FRAME, 7).moveByte(ROUTINE, 4)
                .moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
        assertEquals(4, actual.spriteFlags() & 0x7F); assertEquals(1, dev(p).goodFuture());
        assertEquals(oldScore.score() + 150, score(p).score()); assertEquals(oldScore.nextLifeScore(), score(p).nextLifeScore());
        assertEquals(1, score(p).updateHudScore()); assertEquals(ring.rings(), p.harness().module().pastLoadState().ring().rings());
        assertEquals(ring.lives(), p.harness().module().pastLoadState().ring().lives());
        assertEquals(ring.updateHudLives(), p.harness().module().pastLoadState().ring().updateHudLives());
        assertEquals(0, robotSounds(p));
        assertTrue(p.harness().module().pastLoadState().sounds().stream().noneMatch(s -> s instanceof SonicCdGameModule.PastFrameSound f
                && f.sound() instanceof com.openggf.game.soniccd.player.CdPlayerFrameRuntime.ContactSubCpuCommand),
                "No YESSFX is manufactured on the selected below-threshold AddPoints path");
    }

    @Test void fixedSchedulePreservesDirtyFirstFreeChildrenAndActualEarlierLaterPhysicalOrder() throws Exception {
        var p = ordinary(8278); var pool = p.placement().pool(); var registry = p.harness().mode().getRewindRegistry();
        sameChunk(p); var pristine = registry.capture(); var nativePristine = captureNative(p.harness());
        // Explosion art must already be genuine current data; nothing installs or marks it known here.
        for (int tile = 0; tile < 70; tile++) CdR11bCameraArtResidency.readTile(p.harness().module().pastLoadState().section().residency(), 0x680 + tile);
        for (boolean earlier : new boolean[]{false, true}) {
            registry.restore(pristine); assertNativeCapture(nativePristine, p.harness()); sameChunk(p);
            int first = pool.findObjSlot().orElseThrow(); int parent = earlier ? 127 : first;
            assertEquals(0, pool.state(parent).id()); assertEquals(0, pool.state(parent).routine());
            var parentImage = fixed(p, parent).moveByte(ROUTINE, 4).moveByte(MAP_FRAME, 7).moveWord(ROBOT_GENERATOR_TIMER, 0);
            seed(p, parentImage);
            int child = pool.findObjSlot().orElseThrow(); assertNotEquals(parent, child);
            assertEquals(earlier, child < parent, "The actual first-free pool, not a filler allocation, selects this physical order");
            var dirty = pool.state(child).moveWord(X_FRACTION, 0x1357).moveWord(Y_FRACTION, 0x2468).moveByte(FLAGS, 0x40);
            assertEquals(0, dirty.routine()); assertEquals(0, dirty.subtype()); seed(p, dirty);
            replay(p, neutral()); var actual = pool.state(child);
            var expected = dirty.moveByte(ID, 0x18).moveByte(ROUTINE_2, 1).moveWord(X, parentImage.centreX()).moveWord(Y, parentImage.centreY());
            if (!earlier) expected = expected.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 1)
                    .moveWord(TILE, 0x8680).moveLong(MAPPINGS, 0x21D714).moveByte(COLLISION_TYPE, 0)
                    .moveByte(ANIMATION_FRAME, 1).moveByte(ANIMATION_TIME, 3).moveWord(ANIMATION_WORD, 0).moveByte(MAP_FRAME, 0);
            assertEquals(expected.moveByte(SPRITE_FLAGS, actual.spriteFlags()), actual);
            assertEquals(earlier ? dirty.spriteFlags() : 4, actual.spriteFlags() & 0x7F);
            assertEquals(earlier ? 0 : 2, actual.routine());
            assertEquals(1, robotSounds(p));
            var request = p.harness().module().pastLoadState().sounds().stream().filter(SonicCdGameModule.PastRobotGeneratorFm.class::isInstance).toList();
            assertEquals(0x9E, ((SonicCdGameModule.PastRobotGeneratorFm) request.getFirst()).nativeId());
            var parentAfter = pool.state(parent);
            assertEquals(parentImage.moveWord(ROBOT_GENERATOR_TIMER, 0x0100).moveLong(ROBOT_GENERATOR_SCHEDULE, 0x20EA57)
                    .moveByte(SPRITE_FLAGS, parentAfter.spriteFlags()), parentAfter);
            if (earlier) {
                replay(p, neutral()); assertEquals(2, pool.state(child).routine()); assertEquals(0x21D714, pool.state(child).longBits(MAPPINGS));
                assertEquals(1, pool.state(child).unsignedByte(ROUTINE_2)); assertEquals(0x1357, pool.state(child).word(X_FRACTION));
                assertEquals(0x2468, pool.state(child).word(Y_FRACTION));
            }
        }
    }

    @Test void highTimerByteRebuildAndSelectedScheduleFailureKeepTheirActualCompletedPrefixes() throws Exception {
        var p = ordinary(8278); var pool = p.placement().pool(); sameChunk(p);
        var registry = p.harness().mode().getRewindRegistry(); var pristine = registry.capture(); var nativeBefore = captureNative(p.harness());
        var entry = fixed(p, 127).moveByte(ROUTINE, 6).moveByte(MAP_FRAME, 7).moveWord(ROBOT_GENERATOR_TIMER, 0x02A5)
                .moveWord(ROBOT_GENERATOR_BASE_Y, ((p.frame().state().motion().centreY16_16() >>> 16) + 7) & 0xFFFF);
        seed(p, entry); replay(p, neutral()); var one = pool.state(127);
        assertEquals(entry.moveWord(ROBOT_GENERATOR_TIMER, 0x01A5).moveByte(SPRITE_FLAGS, one.spriteFlags()), one); assertEquals(0, robotSounds(p));
        replay(p, neutral()); var two = pool.state(127);
        assertEquals(one.moveWord(ROBOT_GENERATOR_TIMER, 0x00A5).moveByte(ROUTINE, 0).moveWord(Y, entry.word(ROBOT_GENERATOR_BASE_Y))
                .moveByte(SPRITE_FLAGS, two.spriteFlags()), two); assertEquals(1, robotSounds(p));
        assertEquals(0xD9, p.harness().module().pastLoadState().sounds().stream().filter(SonicCdGameModule.PastRobotGeneratorFm.class::isInstance)
                .map(SonicCdGameModule.PastRobotGeneratorFm.class::cast).findFirst().orElseThrow().nativeId());
        registry.restore(pristine); assertNativeCapture(nativeBefore, p.harness()); sameChunk(p);
        var bad = fixed(p, 127).moveByte(ROUTINE, 4).moveLong(ROBOT_GENERATOR_SCHEDULE, 0xDEADBEEF); seed(p, bad);
        var failure = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class, () -> step(p.harness(), neutral()));
        assertEquals(CdRobotGenerator.UnsupportedBoundary.SCHEDULE_TICK_READ, failure.boundary()); assertEquals(127, failure.wholeIndex());
        assertEquals(bad, pool.state(127)); assertTrue(p.frame().closed()); retired(p);
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, ((CdObjectDrawQueue) actualField(p.visit(), "drawQueue")).phase());
        var calls = (List<?>) actualField(actualField(p.visit(), "values"), "calls");
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }

    private static Prepared ordinary(int through) throws Exception {
        var p = ready();
        for (int call = p.returned() + 1; call <= through; call++) { step(p.harness(), route(call)); healthy(p); }
        return p;
    }
    private static void healthy(Prepared p) throws Exception {
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertFalse(p.frame().closed());
        assertInstanceOf(CdRobotGenerator.class, actualField(p.visit(), "robotGenerator"));
        assertInstanceOf(CdExplosion.class, actualField(p.visit(), "explosion"));
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN)); retired(p);
    }
    private static void retired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeMachine")); assertFalse((boolean) actualField(p.visit(), "machinePort"));
        assertFalse((boolean) actualField(p.visit(), "drawingMachine"));
    }
    private static void replay(Prepared p, LogicalInputSnapshot input) throws Exception {
        var h = p.harness(); var mode = h.mode(); var registry = mode.getRewindRegistry();
        var world = mode.getWorldSession(); long generation = mode.getLevelManager().getCompletedProductionLoadGeneration();
        var source = p.frame().stageSource(); var robot = actualField(p.visit(), "robotGenerator"); var explosion = actualField(p.visit(), "explosion");
        var full = registry.capture(); var course = registry.captureCourse(); var before = captureNative(h);
        step(h, input); healthy(p); var after = captureNative(h);
        registry.restore(full); assertNativeCapture(before, h); healthy(p);
        step(h, input); healthy(p); assertNativeCapture(after, h);
        registry.restoreCourse(course); assertNativeCapture(before, h); healthy(p);
        step(h, input); healthy(p); assertNativeCapture(after, h);
        assertSame(world, mode.getWorldSession()); assertEquals(generation, mode.getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(source, p.frame().stageSource()); assertSame(robot, actualField(p.visit(), "robotGenerator")); assertSame(explosion, actualField(p.visit(), "explosion"));
    }
    private static CdObjectState fixed(Prepared p, int whole) {
        var pool = p.placement().pool(); assertEquals(0, pool.state(whole).id()); assertEquals(0, pool.state(whole).routine());
        var m = p.frame().state().motion(); int x = ((m.centreX16_16() >>> 16) + 96) & 0xFFFF; int y = m.centreY16_16() >>> 16;
        return pool.state(whole).moveByte(ID, 0x2D).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 4)
                .moveByte(X_RADIUS, 0x22).moveByte(WIDTH, 0x22).moveByte(Y_RADIUS, 0x20).moveWord(X, x).moveWord(Y, y)
                .moveWord(TILE, 0x043F).moveLong(MAPPINGS, 0x20E816).moveLong(ROBOT_GENERATOR_SCHEDULE, 0x20EA54)
                .moveWord(ROBOT_GENERATOR_BASE_Y, y).moveWord(ROBOT_GENERATOR_TIMER, 0).moveWord(ROBOT_GENERATOR_FLOAT_STEP, 1)
                .moveByte(MAP_FRAME, 3).moveByte(COLLISION_TYPE, 0xFA);
    }
    private static void seed(Prepared p, CdObjectState state) {
        var actor = p.frame().captureActor(); var shared = p.frame().captureShared(); var before = p.placement().capture();
        p.placement().pool().write(state); assertEquals(actor, p.frame().captureActor()); assertEquals(shared, p.frame().captureShared());
        assertEquals(before.status(), p.placement().status()); assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var old : before.pool().slots()) if (old.identity().wholeIndex() != state.identity().wholeIndex())
            assertEquals(old, p.placement().pool().state(old.identity().wholeIndex()));
    }
    private static void sameChunk(Prepared p) throws Exception {
        var presentation = (SonicCdPlayerPresentation) actualField(p.harness().module(), "playerPresentation");
        assertEquals(presentation.pastForegroundImage().cameraX() & 0xFF80, p.placement().status().previousChunkWord(),
                "The one fixed scene requires no newly crossed prespawn interval; no coordinate search is attempted");
    }
    private static CdOpeningObjectRuntime.DevGlobals dev(Prepared p) throws Exception {
        return (CdOpeningObjectRuntime.DevGlobals) actualField(actualField(actualField(p.visit(), "values"), "globals"), "retainedDev");
    }
    private static CdOpeningObjectRuntime.ScoreGlobals score(Prepared p) throws Exception {
        return (CdOpeningObjectRuntime.ScoreGlobals) actualField(actualField(actualField(p.visit(), "values"), "globals"), "score");
    }
    private static long calls(Prepared p, SonicCdGameModule.PastLoadCall call) { return p.harness().module().pastLoadState().calls().stream().filter(c -> c == call).count(); }
    private static long robotSounds(Prepared p) { return p.harness().module().pastLoadState().sounds().stream().filter(SonicCdGameModule.PastRobotGeneratorFm.class::isInstance).count(); }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
}
