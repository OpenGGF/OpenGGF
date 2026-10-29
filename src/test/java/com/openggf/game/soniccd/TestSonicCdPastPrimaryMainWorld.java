package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTimeWarp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent bounded World composition witnesses, authored against immutable
 * J2ded6ad264eddba565df7ccb1a09313e728cd5d4 and native
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, FixBugs=0.
 * Sonic/Main.asm:231-351,373-456,574-669,695-707;
 * Object Functions.asm:137-196,251-365,382-559.
 * The existing first-return oracle establishes the genuine healthy7775 cut.
 * Each forward call below is exactly World7776 with the unchanged controller;
 * no test assumes7777 returns. Two conditional scenes change one SharedState,
 * and one Display scene changes one ActorState, each once at that closed cut.
 * Expectations never invoke Body, MODE, Frame execution, scroll or animation.
 * Terrain-selected positions/angles/animation remain causal checks, not guessed
 * literals: this class does not certify those component algorithms or pixels.
 */
@Isolated
class TestSonicCdPastPrimaryMainWorld {
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void genuine7776OwnsMainAndNativeSkippedDisplayUnderTheUnchangedController() throws Exception {
        var p = readyMain(); var initial = p.frame().captureActor();
        var source = p.frame().stageSource(); var world = p.harness().mode().getWorldSession();
        long generation = p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration();
        assertDoesNotThrow(() -> step(p.harness(), route(7776)));
        assertOrdinaryReturn(p, initial, 119);
        assertFalse(p.frame().display().orElseThrow().queued(),
                "Old hurt$78 has bit2 clear: native Display decrements but does not call DrawObject");
        assertEquals(0, p.frame().state().motion().spriteFlags() & 0x80,
                "A skipped DrawObject neither clears nor sets the initially clear own bit7");
        assertTrue(completedDraw(p).provenance().stream().noneMatch(x -> x.slotRef().wholeIndex() == 0));
        assertSame(source, p.frame().stageSource()); assertSame(world, p.harness().mode().getWorldSession());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertTrue(p.frame().stageSource().ownsPast(p.assets()));
    }

    @Test void fullHealthy7775RestoreThenForward7776ReproducesTheCompleteNativeCut() throws Exception {
        var p = readyMain(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var before = captureNative(h); var actor = p.frame().captureActor();
        assertDoesNotThrow(() -> step(h, route(7776))); assertOrdinaryReturn(p, actor, 119);
        var after = captureNative(h);
        registry.restore(full); assertComplete(before, captureNative(h), "full7775"); assertIngress(p);
        assertDoesNotThrow(() -> step(h, route(7776))); assertOrdinaryReturn(p, actor, 119);
        assertComplete(after, captureNative(h), "full7776");
    }

    @Test void courseHealthy7775RestoreThenForward7776ReproducesTheCompleteNativeCut() throws Exception {
        var p = readyMain(); var h = p.harness(); var registry = h.mode().getRewindRegistry();
        var course = registry.captureCourse(); var before = captureNative(h); var actor = p.frame().captureActor();
        assertDoesNotThrow(() -> step(h, route(7776))); assertOrdinaryReturn(p, actor, 119);
        var after = captureNative(h);
        registry.restoreCourse(course); assertComplete(before, captureNative(h), "course7775"); assertIngress(p);
        assertDoesNotThrow(() -> step(h, route(7776))); assertOrdinaryReturn(p, actor, 119);
        assertComplete(after, captureNative(h), "course7776");
    }

    @Test void oneClosedHurt124SceneQueuesWholeZeroAndDrawsItsActualPostBodyBMapping() throws Exception {
        var p = readyMain(); var f = p.frame(); var cut = f.captureActor(); var s = cut.state();
        var timers = s.timers();
        var staged = new CdPlayerBody.State(s.motion(), s.animation(), s.header(),
                new CdPlayerBody.Timers(124, timers.invincibility(), timers.speedShoes(), timers.reset()),
                s.history(), s.rotationDistance(), s.objectLinks(), s.rotationAngle());
        // Exactly one public same-owner staging; no camera, layout, pool or input alteration.
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(cut.owner(), staged, cut.display()));
        var initial = f.captureActor();
        assertDoesNotThrow(() -> step(p.harness(), route(7776)));
        assertOrdinaryReturn(p, initial, 123);
        var display = f.display().orElseThrow(); var state = f.state(); var m = state.motion();
        assertTrue(display.queued(), "Old hurt$7C bit2 admits the real native Display call");
        assertEquals(0x80, m.spriteFlags() & 0x80, "Actual loaded whole0 DrawDone sets bit7");
        assertEquals(m.spriteFlags(), display.spriteFlags()); assertEquals(state.animation().mapFrame(), display.mapFrame());
        assertEquals(m.centreX16_16(), display.centreX16_16()); assertEquals(m.centreY16_16(), display.centreY16_16());
        assertPrimarySatFromRawBMapping(p);
    }

    @Test void oneClosedDirectionSceneReachesTheRealMissingReservedStarOwner() throws Exception {
        var p = readyMain(); stageWarpOnce(p, 0);
        var failure = assertMissingWarp(p, CdPlayerTimeWarp.UnsupportedBoundary.RESERVED_TIME_STAR_READ, 0);
        assertEquals(List.of(new CdPlayerTimeWarp.GlobalWrite(CdPlayerTimeWarp.GlobalField.TIMER_WORD, 0, 1)),
                failure.prefix().stores(), "Only the helper's staged timer-start precedes the missing read");
        assertEquals(1, failure.prefix().state().timer());
    }

    @Test void oneClosedTimer209SceneServicesTo210AndReachesTheRealMissingSaveOwner() throws Exception {
        var p = readyMain();
        assertEquals(0, p.harness().module().pastLoadState().level().spawnMode(),
                "The genuine loaded level selects save, not native spawnMode2's early return");
        stageWarpOnce(p, 209);
        var failure = assertMissingWarp(p, CdPlayerTimeWarp.UnsupportedBoundary.SCROLL_LOCK_WRITE, 210);
        assertEquals(210, failure.prefix().state().timer(), "The single actual VInt owns209 to210");
        assertTrue(failure.prefix().stores().isEmpty(), "No TimeWarp store precedes this actual save-owner read");
    }

    private static Prepared readyMain() throws Exception {
        var p = ready();
        // Existing literal first-pass scanner/body/camera/SAT oracle, never an extra lifecycle owner.
        assertTerminal(p, before(p), 45L, 8, 0, "PAST_PRIMARY_MAIN_INGRESS");
        assertIngress(p); return p;
    }

    private static void assertIngress(Prepared p) {
        var f = p.frame(); var s = f.state();
        assertFalse(f.closed()); assertFalse(f.paused()); assertEquals(45L, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(new CdPlayerMotionState(430178304, 46989312, 1536, 0, 1536,
                2, 0, 0, 4, 9, 19, 0, 0, 0, 0, 0, 0, 0, 0, 0), s.motion());
        assertEquals(new CdPlayerBody.AnimationCursor(0, 0, 0), s.animation());
        assertEquals(new CdPlayerBody.Header(1, 0x230F60, 0x0780, 2, 24), s.header());
        assertEquals(new CdPlayerBody.Timers(120, 0, 0, 0), s.timers()); assertEquals(0, s.history().index());
        assertEquals(0, s.rotationDistance()); assertEquals(0, s.rotationAngle());
        assertEquals(new CdPlayerBody.ObjectLinks(0, 0), s.objectLinks()); assertTrue(f.display().isEmpty());
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(
                new CdPlayerMovement.Physics(1536, 12, 128), new CdPlayerMovement.Bounds(0, 10391, 784, 0),
                new CdPlayerMovement.Gates(0, 0, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0, 96, 0, 0, 0, 0, 1, 225, 192, 252)),
                new CdPlayerBody.Controls(8, 0, 0, 0, 0), new CdPlayerBody.Globals(1, 160, 0, 0, 0, 0, 0, 0),
                CdPlayerBody.DynamicSlots.UNIMPLEMENTED), f.environment());
    }

    /** Exact source nonwrites and branch consequences, without inventing terrain-selected outputs. */
    private static void assertOrdinaryReturn(Prepared p, CdPlayerFrameRuntime.ActorState initial, int hurt) throws Exception {
        var f = p.frame(); var s = f.state(); var e = f.environment();
        assertFalse(f.closed()); assertFalse(f.paused()); assertEquals(46L, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(new CdPlayerBody.Controls(8, 0, 8, 0, 0), e.controls());
        assertEquals(new CdPlayerBody.Globals(2, 158, 0, 0, 0, 0, 0, 0), e.globals(),
                "One Main+1 and ExtCamera's positive$600 pan160 to158, with unrelated globals retained");
        assertEquals(CdPlayerBody.DynamicSlots.UNIMPLEMENTED, e.dynamicSlots(),
                "An actual borrowed pool must not rewrite the development fallback marker");
        assertEquals(new CdPlayerMovement.Physics(1536, 12, 128), e.movement().physics());
        assertEquals(new CdPlayerMovement.Gates(0, 0, 0, 0, 0, 0, 0), e.movement().gates());
        assertEquals(initial.state().header(), s.header()); assertEquals(new CdPlayerBody.Timers(hurt, 0, 0, 0), s.timers());
        assertEquals(initial.state().objectLinks(), s.objectLinks());
        assertEquals(initial.state().rotationDistance(), s.rotationDistance()); assertEquals(initial.state().rotationAngle(), s.rotationAngle());
        assertEquals(4, s.history().index(), "One RecordPos at original word index0, low-byte+4");
        assertArrayEquals(Arrays.copyOfRange(initial.state().history().bytes().copy(), 4, 256),
                Arrays.copyOfRange(s.history().bytes().copy(), 4, 256), "Every history byte outside the single XY store");
        assertEquals(0, s.motion().charge()); assertEquals(0, s.motion().playerControl());
        assertEquals(9, s.motion().xRadius()); assertEquals(19, s.motion().yRadius());
        assertSame(initial.owner(), f.captureActor().owner());
        var display = f.display().orElseThrow();
        assertEquals(0x230F60, display.mappingAddress()); assertEquals(0x0780, display.tile());
        assertEquals(2, display.priority()); assertEquals(24, display.width());
        var names = p.harness().module().pastLoadState().calls().stream().map(Enum::name).toList();
        assertEquals(1L, names.stream().filter("PRIMARY_MAIN"::equals).count());
        assertFalse(names.contains("PRIMARY_INIT"), "Init retains its separate first-pass meaning");
        ordered(names, "ORDINARY_SERVICE", "LEVEL_FRAME_INCREMENT", "SPAWN_OBJECTS", "PRIMARY_MAIN",
                "RESERVED_OBJECTS", "DYNAMIC_OBJECTS", "DRAW_OBJECTS", "ORDINARY_MAIN_RETURN");
        assertNull(actualField(p.visit(), "activePrimary")); assertNull(actualField(p.visit(), "activeDrain"));
        assertNull(actualField(p.visit(), "drawQueue")); assertFalse((boolean) actualField(p.visit(), "runningObjects"));
        assertFalse((boolean) actualField(f, "bodyEntitled")); assertNull(actualField(f, "pending"));
        var cpu = completedDraw(p); assertEquals(640, cpu.bytes().length); assertEquals(cpu.spriteCount(), cpu.provenance().size());
        var values = actualField(p.visit(), "values");
        assertArrayEquals(cpu.bytes(), assertInstanceOf(CdAssetBytes.class, actualField(values, "cpuSat")).copy());
        assertTrue(p.harness().mode().getSpriteManager().getAllSprites().isEmpty());
        assertEquals(0, p.harness().mode().getLevelManager().getFrameCounter());
    }

    /** DrawSprite arithmetic, using actual final source inputs and raw authenticated B map bytes only. */
    private static void assertPrimarySatFromRawBMapping(Prepared p) throws Exception {
        var view = p.assets().view(CdR11bAssets.Asset.MAP_SPR_SONIC);
        assertEquals(0x230F60, view.range().nativeAddress()); var raw = view.bytes();
        assertEquals(3004, raw.length()); assertArrayEquals(raw.copy(), p.assets().sonic().mappings().copy());
        assertEquals(198, p.assets().sonic().mappingFrames().size());
        for (int i = 0; i < 198; i++) {
            int at = (short) raw.unsignedWord(i * 2); int pieces = raw.unsignedByte(at);
            var decoded = p.assets().sonic().mappingFrames().get(i);
            assertEquals(at, decoded.relativeOffset()); assertEquals(pieces, decoded.pieceCount());
            assertArrayEquals(raw.slice(at, 1 + pieces * 5).copy(), decoded.bytes().copy());
        }
        var s = p.frame().state(); var m = s.motion();
        assertEquals(4, m.spriteFlags() & 12, "The genuine primary uses the foreground camera");
        var scroll = (SonicCdPlayerPresentation.ScrollCapture)
                p.harness().module().getScrollHandlerProvider().getHandler(0).captureRewindState();
        int x = m.centreX16_16() >>> 16, y = m.centreY16_16() >>> 16, cy = scroll.state().cameraYWord();
        if (cy < 0x100 && y >= 0x800) y = u16(y - 0x800);
        else if (cy >= 0x700 && y < 0x100) y = u16(y + 0x800);
        x = u16(x - scroll.state().cameraX() + 128); y = u16(y - cy + 128);
        int start = (short) raw.unsignedWord(s.animation().mapFrame() * 2), pieces = raw.unsignedByte(start);
        assertTrue(pieces > 0 && pieces <= 128, "A real nonempty Sonic mapping is required for the slot0 witness");
        var cpu = completedDraw(p); byte[] sat = cpu.bytes(); int matched = 0;
        for (int index = 0; index < cpu.provenance().size(); index++) {
            var proof = cpu.provenance().get(index); if (proof.slotRef().wholeIndex() != 0) continue;
            assertEquals(2, proof.queueBucket()); assertEquals(0, proof.queueOrdinal(), "Primary enqueues before reserved and dynamic bodies");
            assertEquals(matched, proof.mappingPieceOrdinal());
            int at = start + 1 + matched * 5, ry = raw.signedByte(at), size = raw.unsignedByte(at + 1), rx = raw.signedByte(at + 4);
            if ((m.spriteFlags() & 1) != 0) rx = -rx - (((size >>> 2) & 3) + 1) * 8;
            if ((m.spriteFlags() & 2) != 0) ry = -ry - ((size & 3) + 1) * 8;
            int out = index * 8, sx = (x + rx) & 0x1FF;
            assertEquals(u16(y + ry), word(sat, out)); assertEquals(size, Byte.toUnsignedInt(sat[out + 2]));
            assertEquals(index == 79 ? 0 : index + 1, Byte.toUnsignedInt(sat[out + 3]));
            assertEquals(u16(raw.unsignedWord(at + 2) + s.header().tile()) ^ ((m.spriteFlags() & 3) << 11), word(sat, out + 4));
            assertEquals(sx == 0 ? 1 : sx, word(sat, out + 6)); matched++;
        }
        assertEquals(pieces, matched, "Every piece of one actual whole0 occurrence is retained in CPU/provenance");
    }

    private static CdObjectDrawQueue.CpuSat completedDraw(Prepared p) throws Exception {
        var values = actualField(p.visit(), "values"); var loop = actualField(values, "loop");
        return assertInstanceOf(CdObjectDrawQueue.CpuSat.class,
                assertInstanceOf(Optional.class, actualField(loop, "completedDraw")).orElseThrow());
    }

    private static void stageWarpOnce(Prepared p, int timer) {
        var f = p.frame(); var shared = f.captureShared(); var e = shared.environment(); var m = e.movement(); var g = m.gates(); var b = e.globals();
        var stagedMovement = new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(), g.windTunnel(), g.speedShoes(), 1), m.globals());
        var stagedEnvironment = new CdPlayerBody.Environment(stagedMovement, e.controls(),
                new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(), b.debugCheat(), timer, b.invincible(), b.bossMusic()), e.dynamicSlots());
        f.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), stagedEnvironment,
                shared.artState(), shared.paused(), shared.vIntClock()));
        // No subsequent scene reseed, controller change, Frame service or helper execution.
    }

    private static CdPlayerTimeWarp.UnsupportedTimeWarp assertMissingWarp(Prepared p,
            CdPlayerTimeWarp.UnsupportedBoundary boundary, int servicedTimer) throws Exception {
        var f = p.frame(); var actor = f.captureActor(); var e = f.environment(); var art = f.artState();
        var pool = p.placement().capture(); var hud = reserved(p.visit()).copy();
        var values = actualField(p.visit(), "values"); var globals = actualField(values, "globals");
        var contact = assertInstanceOf(CdPlayerBody.ContactGlobals.class, actualField(globals, "contact"));
        var level = actualField(values, "level"); var oldCpu = completedDraw(p);
        var failure = assertThrowsExactly(CdPlayerTimeWarp.UnsupportedTimeWarp.class, () -> step(p.harness(), route(7776)));
        assertEquals(boundary, failure.boundary()); assertEquals(1, failure.prefix().state().direction());
        assertEquals(contact.timeWarp(), failure.prefix().state().timeWarp());
        assertTrue(failure.prefix().reservedPatches().isEmpty()); assertTrue(failure.prefix().saveResult().isEmpty());
        assertTrue(failure.prefix().restartResult().isEmpty());
        // Actual closed failure: use retained raw getters/images only. Never capture/restore/retry it.
        assertTrue(f.closed()); assertFalse(f.paused()); assertEquals(46L, f.vIntClock().absoluteValue().orElseThrow());
        assertEquals(actor.state(), f.state()); assertEquals(actor.display(), f.display());
        var b = e.globals();
        assertEquals(new CdPlayerBody.Environment(e.movement(), new CdPlayerBody.Controls(8, 0, 0, 0, 0),
                new CdPlayerBody.Globals(2, b.cameraXCentre(), b.timeAttackMode(), b.debugMode(), b.debugCheat(), servicedTimer, b.invincible(), b.bossMusic()),
                e.dynamicSlots()), f.environment(), "Only genuine prior VInt/Main writes survive; staged Body has no published Result");
        assertComplete(art, f.artState(), "failed.art");
        assertEquals(pool.status(), p.placement().status());
        for (int i = 0; i < 96; i++) assertEquals(pool.pool().slots().get(i), p.placement().pool().state(32 + i), "failed.pool" + i);
        assertArrayEquals(pool.savedFlags().copy(), assertInstanceOf(byte[].class, actualField(p.placement(), "savedFlags")));
        assertArrayEquals(hud, reserved(p.visit()).copy());
        var afterValues = actualField(p.visit(), "values");
        assertEquals(contact, actualField(actualField(afterValues, "globals"), "contact")); assertEquals(level, actualField(afterValues, "level"));
        assertSame(oldCpu, assertInstanceOf(Optional.class, actualField(actualField(afterValues, "loop"), "completedDraw")).orElseThrow());
        assertTrue(assertInstanceOf(List.class, actualField(p.visit(), "sounds")).isEmpty());
        var calls = assertInstanceOf(List.class, actualField(afterValues, "calls"));
        assertTrue(calls.contains(SonicCdGameModule.PastLoadCall.SPAWN_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.RESERVED_OBJECTS));
        assertTrue(calls.stream().noneMatch(x -> ((Enum<?>) x).name().equals("PRIMARY_MAIN")));
        assertNull(actualField(p.visit(), "activePrimary")); assertFalse((boolean) actualField(p.visit(), "runningObjects"));
        assertFalse((boolean) actualField(f, "bodyEntitled")); assertNull(actualField(f, "pending"));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED,
                assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue")).phase());
        return failure;
    }

    /** Full data comparison, including CpuSat's intentionally identity-only Java class. */
    private static void assertComplete(Object expected, Object actual, String name) throws Exception {
        if (expected == actual) return;
        assertNotNull(expected, name); assertNotNull(actual, name); assertEquals(expected.getClass(), actual.getClass(), name);
        if (expected instanceof CdAssetBytes b) {
            assertArrayEquals(b.copy(), ((CdAssetBytes) actual).copy(), name);
        } else if (expected instanceof CdObjectDrawQueue.CpuSat b) {
            var a = (CdObjectDrawQueue.CpuSat) actual;
            assertArrayEquals(b.bytes(), a.bytes(), name); assertEquals(b.spriteCount(), a.spriteCount(), name); assertEquals(b.provenance(), a.provenance(), name);
        } else if (expected instanceof CdCheckpointLiveAux.State b) {
            var a = (CdCheckpointLiveAux.State) actual; assertEquals(b.knownMask(), a.knownMask(), name);
            for (int address : new int[]{0xFF1514, 0xFF1515, 0xFF1516, 0xFF1517, 0xFFF648, 0xFFF649, 0xFFF64D, 0xFFF64E})
                assertEquals(b.knownByte(address), a.knownByte(address), name + ".known" + address);
        } else if (expected instanceof CdCheckpointSaveData.State b) {
            assertArrayEquals(b.knownMask().copy(), ((CdCheckpointSaveData.State) actual).knownMask().copy(), name);
            assertEquals(b, actual, name); // Native saved equality covers all bytes and all per-byte knowledge.
        } else if (expected instanceof Optional<?> b) {
            var a = (Optional<?>) actual; assertEquals(b.isPresent(), a.isPresent(), name);
            if (b.isPresent()) assertComplete(b.orElseThrow(), a.orElseThrow(), name + ".value");
        } else if (expected instanceof List<?> b) {
            var a = (List<?>) actual; assertEquals(b.size(), a.size(), name);
            for (int i = 0; i < b.size(); i++) assertComplete(b.get(i), a.get(i), name + '[' + i + ']');
        } else if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), name);
            for (int i = 0; i < Array.getLength(expected); i++) assertComplete(Array.get(expected, i), Array.get(actual, i), name + '[' + i + ']');
        } else if (expected.getClass().isRecord()) {
            for (var component : expected.getClass().getRecordComponents()) {
                var read = component.getAccessor(); read.setAccessible(true);
                assertComplete(read.invoke(expected), read.invoke(actual), name + '.' + component.getName());
            }
        } else if (Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())) {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true); assertComplete(field.get(expected), field.get(actual), name + '.' + field.getName());
            }
        } else assertEquals(expected, actual, name); // Semantic values or exact opaque ownership identity.
    }

    private static void ordered(List<String> calls, String... names) {
        int previous = -1;
        for (String name : names) { int at = calls.indexOf(name); assertTrue(at > previous, "Native order: " + name + " in " + calls); previous = at; }
    }
    private static int u16(int value) { return value & 65535; }
    private static int word(byte[] bytes, int at) { return (Byte.toUnsignedInt(bytes[at]) << 8) | Byte.toUnsignedInt(bytes[at + 1]); }
}
