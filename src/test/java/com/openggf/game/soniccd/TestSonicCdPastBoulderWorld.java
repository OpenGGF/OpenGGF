package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdBoulder;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine original-USA World/Frame publication from the qualified healthy7851 cut.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Boulder/Main.asm:7-51;
 * Object Solid.asm:344-430 carries object XV<<8 into the actual player's LONG X.
 * The two fixed object-only ingress scenes put a Boulder top two pixels above
 * the current real feet, with dirty XV0 or0040. No actor, camera, terrain, source
 * queue, spawner, timing or private authority is seeded. Preconditions fail hard;
 * there is no coordinate search, terrain probing, Body replay or fallback scene.
 * NativeStopped Solid cannot be manufactured by poisoning Frame links/terrain;
 * component tests own that reach until an actual public World route establishes it.
 * The final public selector fault checks genuine retained Init/failure closure.
 */
@Isolated
class TestSonicCdPastBoulderWorld {
    private static final int SLOT = 127;
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void fixedActualPlayerSupportAndCarryPublishAndReplayBeforeAGenuineReachedInitFailure() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame(); var pool = p.placement().pool();
        var world = h.mode().getWorldSession(); long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var body = assertInstanceOf(CdBoulder.class, actualField(p.visit(), "boulder"));
        var source = frame.stageSource();
        for (int call = 7775; call <= 7851; call++) { step(h, route(call)); healthy(p, body, world, generation, source); }
        var registry = h.mode().getRewindRegistry(); var pristine = registry.capture(); var nativePristine = captureNative(h);
        var initial = frame.state(); var m = initial.motion(); var env = frame.environment();
        assertEquals(2, m.routine()); assertEquals(0, m.flags() & (4 | 0x20), "Fixed fixture needs a nonrolling, unpushed real player");
        assertEquals(0, m.angle(), "The fixed support scene requires the actual flat native player angle");
        assertTrue(m.yVelocity() >= 0 && m.yVelocity() <= 0x600, "Actual player must not rise or outrun the fixed native vertical interval");
        assertTrue(Math.abs(m.xVelocity()) <= 0x600, "Actual ordinary horizontal speed stays inside the fixed width interval");
        assertEquals(0, m.playerControl() & 0x41); assertEquals(0, env.globals().debugMode());
        assertEquals(0, env.movement().gates().miniSonic()); assertEquals(0, env.movement().gates().timeZone());
        assertEquals(0, env.movement().gates().timeWarpDirection()); requireSameChunk(p);
        assertArrayEquals(new byte[64], pool.state(SLOT).copyBytes(), "The one fixed physical ingress must be genuinely clear");

        var neutral = LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral());
        // A separate real ordinary row supplies upstream comparison values only.
        // They never hydrate the scene; placement uses ONLY the retained7851 actor.
        step(h, neutral); healthy(p, body, world, generation, source);
        var control = frame.state(); var controlEnvironment = frame.environment();
        var controlSounds = h.module().pastLoadState().sounds();
        registry.restore(pristine); complete(nativePristine, captureNative(h), "pristine.before-scenes");
        healthy(p, body, world, generation, source);

        for (int velocity : new int[]{0, 0x40}) {
            registry.restore(pristine); complete(nativePristine, captureNative(h), "pristine.before-XV" + velocity);
            healthy(p, body, world, generation, source); requireSameChunk(p);
            var entry = pool.state(SLOT).moveByte(ID, 0x1B).moveByte(SPRITE_FLAGS, 0x84)
                    .moveWord(X, m.centreX16_16() >>> 16)
                    .moveWord(Y, ((m.centreY16_16() >>> 16) + m.yRadius() + 16 - 2) & 0xFFFF)
                    .moveWord(X_VELOCITY, velocity);
            seed(p, entry);
            replayEdge(p, neutral, body, world, generation, source, "fixed-XV" + velocity);
            var actual = frame.state(); var object = pool.state(SLOT);
            assertEquals(SLOT, actual.objectLinks().standObject(), "The actual completed Solid must acquire this Boulder");
            assertEquals(8, actual.motion().flags() & 8); assertEquals(8, object.flags() & 8);
            assertEquals(control.motion().centreX16_16() + (velocity << 8), actual.motion().centreX16_16(),
                    "Dirty XV must actually carry the real player's complete LONG, including its fraction");
            assertEquals(control.motion().yRadius(), m.yRadius(), "Fixed input must retain the native size");
            int y = (entry.centreY() - 16 - control.motion().yRadius()) & 0xFFFF;
            assertEquals((y << 16) | (control.motion().centreY16_16() & 0xFFFF), actual.motion().centreY16_16());
            assertEquals(0, actual.motion().yVelocity()); assertEquals(0, actual.motion().angle());
            assertEquals(0, actual.motion().playerJump()); assertEquals(control.motion().xVelocity(), actual.motion().groundVelocity());
            complete(control.animation(), actual.animation(), "unchanged.animation");
            complete(control.header(), actual.header(), "unchanged.header");
            complete(control.timers(), actual.timers(), "unchanged.timers");
            complete(control.history(), actual.history(), "unchanged.preobject-history");
            assertEquals(control.rotationAngle(), actual.rotationAngle()); assertEquals(control.rotationDistance(), actual.rotationDistance());
            complete(controlEnvironment.controls(), frame.environment().controls(), "unchanged.controls");
            complete(controlEnvironment.movement().physics(), frame.environment().movement().physics(), "unchanged.physics");
            complete(controlEnvironment.movement().gates(), frame.environment().movement().gates(), "unchanged.gates");
            assertEquals(controlSounds, h.module().pastLoadState().sounds(), "Landing contributes no fabricated Boulder sound family");
            assertEquals(1, h.module().pastLoadState().calls().stream().filter(c -> c == SonicCdGameModule.PastLoadCall.BOULDER_BODY).count());
            assertEquals(2, completedDraw(p).provenance().stream().filter(piece -> piece.slotRef().wholeIndex() == SLOT).count());
            // Every Boulder byte follows Init + declared Solid flag writes + actual visibility.
            byte[] expected = entry.copyBytes(); var bytes = ByteBuffer.wrap(expected);
            expected[0x24] = 2; expected[1] = (byte) object.spriteFlags(); expected[0x18] = 4;
            bytes.putInt(4, 0x2094AC); expected[0x19] = 16; expected[0x16] = 16; expected[0x1A] = 0;
            bytes.putShort(2, (short) 0x346); expected[0x22] = 8;
            assertEquals(0x84, object.spriteFlags()); assertArrayEquals(expected, object.copyBytes());
        }

        registry.restore(pristine); complete(nativePristine, captureNative(h), "pristine.before-selector-stop");
        healthy(p, body, world, generation, source); requireSameChunk(p);
        var bad = pool.state(SLOT).moveByte(ID, 0x1B).moveByte(SPRITE_FLAGS, 0x84).moveByte(SUBTYPE2, 1)
                .moveWord(X, m.centreX16_16() >>> 16).moveWord(Y, m.centreY16_16() >>> 16).moveWord(TILE, 0xBEEF);
        seed(p, bad);
        var failure = assertThrows(CdBoulder.UnsupportedBoulder.class, () -> step(h, neutral));
        assertEquals(CdBoulder.UnsupportedBoundary.TILE_ID_SUBTYPE2, failure.boundary()); assertEquals(SLOT, failure.wholeIndex());
        var prefix = bad.moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, bad.spriteFlags() | 4).moveByte(PRIORITY, 4)
                .moveLong(MAPPINGS, 0x2094AC).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 16).moveByte(MAP_FRAME, 0);
        assertEquals(prefix, pool.state(SLOT), "Actual Init prefix remains live before the selected tile read");
        assertTrue(frame.closed()); assertRetired(p);
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.TAMABBOH_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.BOULDER_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.DRAW_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED,
                assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue")).phase());
    }

    private static void replayEdge(Prepared p, LogicalInputSnapshot input, CdBoulder body, Object world,
                                   long generation, Object source, String name) throws Exception {
        var registry = p.harness().mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        var before = captureNative(p.harness()); step(p.harness(), input); healthy(p, body, world, generation, source);
        var after = captureNative(p.harness());
        registry.restore(full); complete(before, captureNative(p.harness()), name + ".full.before");
        healthy(p, body, world, generation, source); step(p.harness(), input); healthy(p, body, world, generation, source);
        complete(after, captureNative(p.harness()), name + ".full.after");
        registry.restoreCourse(course); complete(before, captureNative(p.harness()), name + ".course.before");
        healthy(p, body, world, generation, source); step(p.harness(), input); healthy(p, body, world, generation, source);
        complete(after, captureNative(p.harness()), name + ".course.after");
    }
    private static void healthy(Prepared p, CdBoulder body, Object world, long generation, Object source) throws Exception {
        assertSame(world, p.harness().mode().getWorldSession()); assertSame(source, p.frame().stageSource());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertSame(body, actualField(p.visit(), "boulder"));
        assertFalse(p.frame().closed()); assertFalse(p.frame().paused());
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertRetired(p);
    }
    private static void assertRetired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeBoulder")); assertFalse((boolean) actualField(p.visit(), "boulderPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingBoulder"));
    }
    private static void requireSameChunk(Prepared p) throws Exception {
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(p.harness().module(), "playerPresentation"));
        assertEquals(presentation.pastForegroundImage().cameraX() & 0xFF80, p.placement().status().previousChunkWord(),
                "The fixed scene may not cross into new prespawn work");
    }
    private static void seed(Prepared p, CdObjectState image) throws Exception {
        var actor = p.frame().captureActor(); var shared = p.frame().captureShared(); var before = p.placement().capture();
        var values = actualField(p.visit(), "values"); p.placement().pool().write(image);
        assertEquals(actor, p.frame().captureActor()); assertEquals(shared, p.frame().captureShared());
        assertSame(values, actualField(p.visit(), "values")); assertEquals(before.status(), p.placement().status());
        assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var old : before.pool().slots()) if (old.identity().wholeIndex() != image.identity().wholeIndex())
            assertEquals(old, p.placement().pool().state(old.identity().wholeIndex()));
    }
    private static CdObjectDrawQueue.CpuSat completedDraw(Prepared p) throws Exception {
        return (CdObjectDrawQueue.CpuSat) ((Optional<?>) actualField(actualField(actualField(p.visit(), "values"), "loop"), "completedDraw")).orElseThrow();
    }
    private static void complete(Object expected, Object actual, String name) throws Exception {
        if (expected == actual) return;
        assertNotNull(expected, name); assertNotNull(actual, name); assertEquals(expected.getClass(), actual.getClass(), name);
        if (expected instanceof CdAssetBytes b) assertArrayEquals(b.copy(), ((CdAssetBytes) actual).copy(), name);
        else if (expected instanceof CdObjectDrawQueue.CpuSat b) {
            var a = (CdObjectDrawQueue.CpuSat) actual; assertArrayEquals(b.bytes(), a.bytes(), name);
            assertEquals(b.spriteCount(), a.spriteCount(), name); assertEquals(b.provenance(), a.provenance(), name);
        } else if (expected instanceof CdCheckpointLiveAux.State b) {
            var a = (CdCheckpointLiveAux.State) actual; assertEquals(b.knownMask(), a.knownMask(), name);
            for (int address : new int[]{0xFF1514,0xFF1515,0xFF1516,0xFF1517,0xFFF648,0xFFF649,0xFFF64D,0xFFF64E})
                assertEquals(b.knownByte(address), a.knownByte(address), name + ".known" + address);
        } else if (expected instanceof CdCheckpointSaveData.State b) {
            assertArrayEquals(b.knownMask().copy(), ((CdCheckpointSaveData.State) actual).knownMask().copy(), name); assertEquals(b, actual, name);
        } else if (expected instanceof Optional<?> b) {
            var a = (Optional<?>) actual; assertEquals(b.isPresent(), a.isPresent(), name);
            if (b.isPresent()) complete(b.orElseThrow(), a.orElseThrow(), name + ".value");
        } else if (expected instanceof List<?> b) {
            var a = (List<?>) actual; assertEquals(b.size(), a.size(), name);
            for (int n = 0; n < b.size(); n++) complete(b.get(n), a.get(n), name + '[' + n + ']');
        } else if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), name);
            for (int n = 0; n < Array.getLength(expected); n++) complete(Array.get(expected,n), Array.get(actual,n), name + '[' + n + ']');
        } else if (expected.getClass().isRecord()) {
            for (var field : expected.getClass().getRecordComponents()) {
                var read = field.getAccessor(); read.setAccessible(true); complete(read.invoke(expected), read.invoke(actual), name + '.' + field.getName());
            }
        } else if (Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())) {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true); complete(field.get(expected), field.get(actual), name + '.' + field.getName());
            }
        } else assertEquals(expected, actual, name);
    }
}
