package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdSpikes;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
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
 * Fixed actual7851 original-USA object-only scenes. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * common Spikes/Main.asm:19-83; Object Solid.asm:344-430; PP Player Object Collision.asm:229-298.
 * Source geometry places the spike top two pixels above the retained real feet. No actor/global,
 * camera/terrain/spawner/ring/source readiness is written and no coordinate search is performed.
 * Full/course complete native comparisons precede each replay, retaining actual same-World owners.
 * This fixed cut retains real hurt protection, so upright direct Hurt correctly bypasses.
 * Live direct Hurt and later LostRing dispatch remain explicit World coverage gaps. NativeStopped Solid reach belongs to component tests until
 * an actual public World route supplies it. No link/terrain corruption manufactures that reach.
 * Paired owning Frame preparation source was read at verified SHAa5825d2080e47af8647391136395b4de5682d7c1a333275e0f85598fb2869f46.
 * Execution qualification and remaining limits are recorded in the existing level matrix.
 */
@Isolated
class TestSonicCdPastSpikesWorld {
    private static final int SLOT = 127;
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void cachedFamilyAndCallAppendPreserveTheExistingProducerSurface() throws Exception {
        var field = SonicCdPastLoadingVisit.class.getDeclaredField("spikes");
        assertEquals(CdSpikes.class, field.getType()); assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(1, Arrays.stream(SonicCdPastLoadingVisit.class.getDeclaredFields()).filter(f -> f.getType() == CdSpikes.class).count());
        var active = SonicCdPastLoadingVisit.class.getDeclaredField("activeSpikes"); assertTrue(Modifier.isTransient(active.getModifiers()));
        var calls = SonicCdGameModule.PastLoadCall.values();
        assertEquals("BOULDER_BODY", calls[73].name()); assertTrue(SonicCdGameModule.PastLoadCall.SPIKES_BODY.ordinal() >= 74);
        assertEquals(1, Arrays.stream(calls).filter(c -> c == SonicCdGameModule.PastLoadCall.SPIKES_BODY).count());
    }

    @Test void fixedActualVflipSolidAndProtectedUprightReplayBeforeAReachedSelectorFailure() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame(); var pool = p.placement().pool();
        var world = h.mode().getWorldSession(); long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var body = assertInstanceOf(CdSpikes.class, actualField(p.visit(), "spikes")); var source = frame.stageSource();
        for (int call = 7775; call <= 7851; call++) { step(h, route(call)); healthy(p, body, world, generation, source); }
        var registry = h.mode().getRewindRegistry(); var pristine = registry.capture(); var nativePristine = captureNative(h);
        var m = frame.state().motion(); var env = frame.environment();
        assertEquals(2, m.routine()); assertEquals(0, m.flags() & (4 | 0x20 | 0x40)); assertEquals(0, m.angle());
        assertTrue(m.yVelocity() >= 0 && m.yVelocity() <= 0x600); assertTrue(Math.abs(m.xVelocity()) <= 0x600);
        assertEquals(0, m.playerControl() & 0x41); assertEquals(0, env.globals().debugMode());
        assertEquals(0, env.globals().debugCheat()); assertEquals(0, env.movement().gates().miniSonic());
        assertEquals(0, env.movement().gates().timeZone()); assertEquals(0, env.movement().gates().timeWarpDirection());
        requireSameChunk(p); assertArrayEquals(new byte[64], pool.state(SLOT).copyBytes());
        var neutral = LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral());
        // Genuine separate upstream comparison only; none of its values hydrates the scene.
        step(h, neutral); healthy(p, body, world, generation, source);
        var control = frame.state(); var controlEnvironment = frame.environment(); var controlPool = p.placement().capture();
        var controlRing = h.module().pastLoadState().ring(); var controlSounds = h.module().pastLoadState().sounds();
        registry.restore(pristine); complete(nativePristine, captureNative(h), "pristine.before-scenes");

        CdPlayerBody.State supportedState = null;
        CdPlayerBody.Environment supportedEnvironment = null;
        List<CdObjectState> supportedSlots = List.of();
        for (int flip : new int[]{2, 3}) for (int velocity : new int[]{0, 0x40}) {
            registry.restore(pristine); complete(nativePristine, captureNative(h), "vflip.before");
            healthy(p, body, world, generation, source); requireSameChunk(p);
            var entry = spike(pool.state(SLOT), m, flip).moveWord(X_VELOCITY, velocity);
            seed(p, entry); replayEdge(p, neutral, body, world, generation, source, "vflip" + flip + ".XV" + velocity);
            var actual = frame.state(); var object = pool.state(SLOT);
            assertEquals(SLOT, actual.objectLinks().standObject()); assertEquals(8, actual.motion().flags() & 8);
            assertEquals(8, object.flags() & 8); assertEquals(0x83, object.unsignedByte(COLLISION_TYPE));
            assertEquals(0x12, object.unsignedByte(WIDTH)); assertEquals(8, object.unsignedByte(Y_RADIUS));
            assertEquals(control.motion().centreX16_16() + (velocity << 8), actual.motion().centreX16_16());
            int y = (entry.centreY() - 8 - control.motion().yRadius()) & 0xFFFF;
            assertEquals((y << 16) | (control.motion().centreY16_16() & 0xFFFF), actual.motion().centreY16_16());
            assertEquals(0, actual.motion().yVelocity()); assertEquals(0, actual.motion().angle());
            assertEquals(0, actual.motion().playerJump()); assertEquals(control.motion().xVelocity(), actual.motion().groundVelocity());
            complete(control.animation(), actual.animation(), "vflip.animation"); complete(control.header(), actual.header(), "vflip.header");
            complete(control.timers(), actual.timers(), "vflip.no-Hurt-timers"); complete(control.history(), actual.history(), "vflip.history");
            assertEquals(control.rotationAngle(), actual.rotationAngle()); assertEquals(control.rotationDistance(), actual.rotationDistance());
            // Real Solid owns collision-angle scratches; the scene does not fit them to an upstream replay.
            complete(controlEnvironment.controls(), frame.environment().controls(), "vflip.controls");
            complete(controlEnvironment.globals(), frame.environment().globals(), "vflip.body-globals");
            complete(controlEnvironment.movement().physics(), frame.environment().movement().physics(), "vflip.physics");
            complete(controlEnvironment.movement().bounds(), frame.environment().movement().bounds(), "vflip.bounds");
            complete(controlEnvironment.movement().gates(), frame.environment().movement().gates(), "vflip.gates");
            var cg = controlEnvironment.movement().globals(); var ag = frame.environment().movement().globals();
            assertEquals(cg.lookMode(), ag.lookMode()); assertEquals(cg.cameraYCentre(), ag.cameraYCentre());
            assertEquals(cg.boredTimer(), ag.boredTimer()); assertEquals(cg.scoreChain(), ag.scoreChain());
            assertEquals(cg.debugAngle(), ag.debugAngle()); assertEquals(cg.debugAngleShift(), ag.debugAngleShift());
            assertEquals(cg.debugQuadrant(), ag.debugQuadrant()); assertEquals(cg.debugFloorDistance(), ag.debugFloorDistance());
            assertEquals(controlSounds, h.module().pastLoadState().sounds()); assertEquals(controlRing, h.module().pastLoadState().ring());
            assertSpikeImage(entry, object, true);
            assertEquals(4, completedDraw(p).provenance().stream().filter(piece -> piece.slotRef().wholeIndex() == SLOT).count());
            assertEquals(1, h.module().pastLoadState().calls().stream().filter(c -> c == SonicCdGameModule.PastLoadCall.SPIKES_BODY).count());
            if (flip == 2 && velocity == 0) {
                supportedState = frame.state(); supportedEnvironment = frame.environment();
                supportedSlots = pool.capture().slots();
            }
        }

        registry.restore(pristine); complete(nativePristine, captureNative(h), "upright.before");
        healthy(p, body, world, generation, source); requireSameChunk(p);
        // This genuine cut has protection, so native .ChkStand must bypass Hurt.
        // A separate attempted exact-timer neutral wait expired it while ascending;
        // do not fabricate a downward contact, clear the timer or search another coordinate.
        assertTrue(frame.state().timers().hurt() > 0);
        assertEquals(0, frame.environment().globals().invincible());
        var upright = spike(pool.state(SLOT), m, 0); seed(p, upright);
        replayEdge(p, neutral, body, world, generation, source, "upright.native-protection-bypass");
        complete(supportedState, frame.state(), "upright.same-genuine-Solid.no-Hurt.state");
        complete(supportedEnvironment, frame.environment(), "upright.same-genuine-Solid.no-Hurt.environment");
        assertTrue(frame.state().timers().hurt() > 0);
        assertEquals(2, frame.state().motion().routine());
        assertEquals(controlRing, h.module().pastLoadState().ring());
        assertEquals(controlSounds, h.module().pastLoadState().sounds());
        assertSpikeImage(upright, pool.state(SLOT), true);
        for (var expected : supportedSlots) if (expected.identity().wholeIndex() != SLOT)
            assertEquals(expected, pool.state(expected.identity().wholeIndex()), "No Hurt child or unrelated pool write");
        // Changed live Hurt publication is covered by component/Frame cases, not this scene.

        registry.restore(pristine); complete(nativePristine, captureNative(h), "selector.before");
        healthy(p, body, world, generation, source); requireSameChunk(p);
        var bad = spike(pool.state(SLOT), m, 2).moveByte(SUBTYPE2, 1); seed(p, bad);
        var failure = assertThrows(CdSpikes.UnsupportedSpikes.class, () -> step(h, neutral));
        assertEquals(CdSpikes.UnsupportedBoundary.TILE_ID_SUBTYPE2, failure.boundary()); assertEquals(SLOT, failure.wholeIndex());
        var actual = pool.state(SLOT); byte[] expected = bad.copyBytes(); expected[0x24] = 2; expected[1] |= 4;
        expected[0x18] = 3; ByteBuffer.wrap(expected).putInt(4, 0x20C048);
        assertArrayEquals(expected, actual.copyBytes(), "Reached Init prefix survives without tile/Solid/Draw");
        assertRetired(p);
        var failedCalls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertFalse(failedCalls.contains(SonicCdGameModule.PastLoadCall.SPIKES_BODY));
        assertFalse(failedCalls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue")).phase());
    }

    private static CdObjectState spike(CdObjectState clear, com.openggf.game.soniccd.player.CdPlayerMotionState actor, int flip) {
        // Explicit dirty prior-visibility ingress: plain Solid returns GetOff when bit7 is clear.
        return clear.moveByte(ID, 0x26).moveByte(SPRITE_FLAGS, flip | 0x80).moveByte(FLAGS, flip)
                .moveWord(X, actor.centreX16_16() >>> 16)
                .moveWord(Y, ((actor.centreY16_16() >>> 16) + actor.yRadius() + 8 - 2) & 0xFFFF);
    }
    private static void assertSpikeImage(CdObjectState before, CdObjectState after, boolean landed) {
        byte[] expected = before.copyBytes(); var bytes = ByteBuffer.wrap(expected);
        expected[0x24] = 2; expected[1] = (byte) after.spriteFlags(); expected[0x18] = 3;
        bytes.putInt(4, 0x20C048); bytes.putShort(2, (short) 0x320); expected[0x16] = 8;
        expected[0x19] = (byte) ((before.spriteFlags() & 2) == 0 ? 0x10 : 0x12);
        if ((before.spriteFlags() & 2) != 0) expected[0x20] = (byte) 0x83;
        if (landed) expected[0x22] |= 8;
        assertArrayEquals(expected, after.copyBytes()); assertTrue((after.spriteFlags() & 0x80) != 0);
    }

    private static void replayEdge(Prepared p, LogicalInputSnapshot input, CdSpikes body, Object world,
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
    private static void healthy(Prepared p, CdSpikes body, Object world, long generation, Object source) throws Exception {
        assertSame(world, p.harness().mode().getWorldSession()); assertSame(source, p.frame().stageSource());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertSame(body, actualField(p.visit(), "spikes"));
        assertFalse(p.frame().closed()); assertFalse(p.frame().paused());
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertRetired(p);
    }
    private static void assertRetired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeSpikes")); assertFalse((boolean) actualField(p.visit(), "spikesPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingSpikes"));
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
