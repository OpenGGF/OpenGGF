package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.objects.CdAnton;
import com.openggf.game.soniccd.objects.CdCheckpointLiveAux;
import com.openggf.game.soniccd.objects.CdCheckpointSaveData;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual World/Frame callback and capture proof, not a natural Anton route.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Anton/Main.asm:7-103;
 * Collision Check.asm:157-179 pre-zeroes the shared byte before the first terrain read.
 * Factory c0cd3a635319c3f2dbc6245ede7f0470bf39b0684cd8ddc0f57c2d5838292900.
 * Two fixed object-only scenes start64px behind the actual healthy7851 actor,
 * with the object's feet at that actor's real feet. Source grounding/current-art
 * and twelve-row Init/Place progression are fixed; World patrol is certified only if source-reached; no angle/floor helper is called
 * to find or predict a location. No player, camera, terrain, spawner, art or timing
 * edit, private authority, Body/animation replay, pixel or parity oracle is used.
 */
@Isolated
class TestSonicCdPastAntonWorld {
    private static final int SLOT = 127, ROWS = 12;
    private record Control(CdPlayerFrameRuntime.ActorState actor, CdPlayerBody.Environment environment,
                           List<SonicCdGameModule.PastSound> sounds) { }
    private final TestSonicCdPastLoadingWorld fixture = new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup() { fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void fixedNormalAndDamagedWorldPlacePublishesRealAnglesAndReplaysBeforeTheRawTileStop() throws Exception {
        var p = ready(); var h = p.harness(); var frame = p.frame(); var pool = p.placement().pool();
        var world = h.mode().getWorldSession(); long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var source = frame.stageSource(); var body = assertInstanceOf(CdAnton.class, actualField(p.visit(), "anton"));
        for (int call = 7775; call <= 7851; call++) { step(h, route(call)); healthy(p, body, world, generation, source); }
        var registry = h.mode().getRewindRegistry(); var pristine = registry.capture(); var nativePristine = captureNative(h);
        var player = frame.state().motion(); var environment = frame.environment();
        assertEquals(2, player.routine()); assertEquals(0, player.flags() & 0x0E,
                "Fixed grounding requires actual nonrolling terrain support, rather than an object or falling actor");
        assertEquals(0, player.angle()); assertEquals(0, player.yVelocity());
        assertTrue(player.xVelocity() >= 0 && player.xVelocity() <= 0x600);
        assertEquals(0, environment.globals().debugMode()); assertEquals(0, environment.movement().gates().miniSonic());
        assertEquals(0, environment.movement().gates().timeZone()); assertEquals(0, environment.movement().gates().timeWarpDirection());
        var retained = actualField(actualField(actualField(p.visit(), "values"), "globals"), "retainedDev");
        assertEquals(0, (int) retained.getClass().getMethod("goodFuture").invoke(retained));
        assertArrayEquals(new byte[64], pool.state(SLOT).copyBytes()); requireSameChunk(p);
        // Native collision type29 uses pair40: half-width8, safely behind this fixed actor.
        var dimensions = p.assets().view(CdR11bAssets.Asset.TOUCH_DIMENSIONS).bytes();
        assertEquals(8, dimensions.unsignedByte(80)); assertEquals(16, dimensions.unsignedByte(81));
        var current = h.module().pastLoadState().section().residency();
        var region = current.region(CdR11bCameraArtResidency.Region.AT_6B40);
        for (int offset = 0; offset < 22 * 32; offset++)
            assertTrue(region.knownByte(0x7DA0 - 0x6B40 + offset).isPresent(),
                    "All22 current physical patterns must already be known; no canonical-art preload is supplied");

        var neutral = LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral());
        var controls = new ArrayList<Control>();
        for (int row = 0; row < ROWS; row++) {
            requireSameChunk(p); step(h, neutral); healthy(p, body, world, generation, source);
            controls.add(new Control(frame.captureActor(), frame.environment(), h.module().pastLoadState().sounds()));
        }
        registry.restore(pristine); complete(nativePristine, captureNative(h), "pristine.before-scenes");

        for (int subtype : new int[]{0, 0xFF}) {
            registry.restore(pristine); complete(nativePristine, captureNative(h), "pristine.before-subtype" + subtype);
            healthy(p, body, world, generation, source); requireSameChunk(p);
            var entry = pool.state(SLOT).moveByte(ID, 0x3F).moveByte(SPRITE_FLAGS, 4).moveByte(SUBTYPE, subtype)
                    .moveLong(X_POSITION, ((((player.centreX16_16() >>> 16) - 64) & 0xFFFF) << 16) | 0x1234)
                    .moveLong(Y_POSITION, ((((player.centreY16_16() >>> 16) + player.yRadius() - 19) & 0xFFFF) << 16) | 0x5678);
            seed(p, entry); Set<Integer> frames = new HashSet<>(); boolean moved = false, reachedMain = false;
            for (int row = 0; row < ROWS; row++) {
                requireSameChunk(p); var before = pool.state(SLOT);
                replayEdge(p, neutral, body, world, generation, source, "subtype" + subtype + ".row" + row);
                var object = pool.state(SLOT); var control = controls.get(row);
                complete(control.actor(), frame.captureActor(), "angle-only.actor.row" + row);
                compare(control.environment(), frame.environment(), "angle-only.environment.row" + row, true);
                assertEquals(control.sounds(), h.module().pastLoadState().sounds(), "Anton creates no sound owner/request");
                assertEquals(1, h.module().pastLoadState().calls().stream().filter(c -> c == SonicCdGameModule.PastLoadCall.ANTON_BODY).count());
                assertEquals(0x3F, object.id()); assertEquals(0x20B202, object.longBits(MAPPINGS));
                assertEquals(0x23ED, object.word(TILE)); assertEquals(4, object.unsignedByte(PRIORITY));
                assertEquals(19, object.unsignedByte(Y_RADIUS)); assertEquals(24, object.unsignedByte(X_RADIUS));
                if (row == 0) {
                    byte[] expected = initHeader(entry); var bytes = ByteBuffer.wrap(expected);
                    bytes.putShort(2, (short) 0x23ED); bytes.putInt(0x2A, subtype == 0 ? -0x10000 : -0x8000);
                    expected[0x1C] = (byte) (subtype == 0 ? 0 : 1);
                    bytes.putInt(12, entry.longBits(Y_POSITION) + 0x10000);
                    expected[0x24] = (byte) object.routine(); expected[1] = (byte) object.spriteFlags();
                    assertArrayEquals(expected, object.copyBytes());
                    assertTrue(object.routine() == 0 || object.routine() == 2, "Actual Place advances only on its negative floor result");
                    assertEquals(0, object.unsignedByte(MAP_FRAME), "Init must not pre-run the damaged animation");
                } else if (before.routine() <= 2) {
                    assertEquals(before.longBits(Y_POSITION) + 0x10000, object.longBits(Y_POSITION), "Native Place always moves exactly one LONG pixel");
                    assertTrue(object.routine() == before.routine() || object.routine() == before.routine() + 2,
                            "Only a genuine negative floor may advance Place; no grounded result is assumed");
                } else {
                    assertEquals(4, before.routine()); assertEquals(4, object.routine()); reachedMain = true;
                    frames.add(object.unsignedByte(MAP_FRAME)); moved |= object.longBits(X_POSITION) != before.longBits(X_POSITION);
                }
            }
            if (reachedMain) {
                assertTrue(moved, "Source-reached Main patrol must actually move X");
                assertTrue((subtype == 0 ? Set.of(0, 1) : Set.of(2, 3)).containsAll(frames));
            } else {
                assertTrue(frames.isEmpty()); assertTrue(pool.state(SLOT).routine() <= 2);
                assertEquals(entry.longBits(Y_POSITION) + ROWS * 0x10000, pool.state(SLOT).longBits(Y_POSITION));
                System.out.println("ANTON_WORLD fixed terrain did not reach Main; Init/Place/full-course qualified, World patrol remains a gap subtype=" + subtype);
            }
        }

        registry.restore(pristine); complete(nativePristine, captureNative(h), "pristine.before-tile-stop");
        healthy(p, body, world, generation, source); requireSameChunk(p);
        var bad = pool.state(SLOT).moveByte(ID, 0x3F).moveByte(SUBTYPE2, 2).moveWord(TILE, 0xBEEF)
                .moveWord(X, ((player.centreX16_16() >>> 16) - 64) & 0xFFFF).moveWord(Y, player.centreY16_16() >>> 16);
        seed(p, bad);
        var failure = assertThrows(CdAnton.UnsupportedAnton.class, () -> step(h, neutral));
        assertEquals(CdAnton.UnsupportedBoundary.TILE_ENTRY, failure.boundary()); assertEquals(SLOT, failure.wholeIndex()); assertEquals(2, failure.value());
        assertArrayEquals(initHeader(bad), pool.state(SLOT).copyBytes(), "Only the real pre-tile Init stores survive");
        assertEquals(controls.getFirst().environment().movement().globals().primaryAngle(), frame.environment().movement().globals().primaryAngle(),
                "No Anton floor pre-zero is reached before the selected tile fault");
        assertTrue(frame.closed()); assertRetired(p);
        var calls = assertInstanceOf(List.class, actualField(actualField(p.visit(), "values"), "calls"));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ANTON_BODY));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.DRAW_OBJECTS));
        assertFalse(calls.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        assertEquals(CdObjectDrawQueue.Phase.CLOSED, assertInstanceOf(CdObjectDrawQueue.class, actualField(p.visit(), "drawQueue")).phase());
    }

    private static byte[] initHeader(CdObjectState before) {
        byte[] b = before.copyBytes(); var words = ByteBuffer.wrap(b); b[1] |= 4; b[0x18] = 4;
        words.putInt(4, 0x20B202); b[0x17] = 24; b[0x19] = 24; b[0x16] = 19; b[0x20] = 0x29;
        words.putShort(0x2E, (short) before.centreX()); return b;
    }
    private static void replayEdge(Prepared p, LogicalInputSnapshot input, CdAnton body, Object world,
                                   long generation, Object source, String name) throws Exception {
        var registry = p.harness().mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        var before = captureNative(p.harness()); step(p.harness(), input); healthy(p, body, world, generation, source); var after = captureNative(p.harness());
        registry.restore(full); complete(before, captureNative(p.harness()), name + ".full.before");
        healthy(p, body, world, generation, source); step(p.harness(), input); healthy(p, body, world, generation, source);
        complete(after, captureNative(p.harness()), name + ".full.after");
        registry.restoreCourse(course); complete(before, captureNative(p.harness()), name + ".course.before");
        healthy(p, body, world, generation, source); step(p.harness(), input); healthy(p, body, world, generation, source);
        complete(after, captureNative(p.harness()), name + ".course.after");
    }
    private static void healthy(Prepared p, CdAnton body, Object world, long generation, Object source) throws Exception {
        assertSame(world, p.harness().mode().getWorldSession()); assertSame(source, p.frame().stageSource());
        assertEquals(generation, p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertSame(p.frame(), p.harness().module().requirePlayerFrame()); assertSame(body, actualField(p.visit(), "anton"));
        assertFalse(p.frame().closed()); assertFalse(p.frame().paused()); assertRetired(p);
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }
    private static void assertRetired(Prepared p) throws Exception {
        assertNull(actualField(p.visit(), "activeAnton")); assertFalse((boolean) actualField(p.visit(), "antonPort"));
        assertFalse((boolean) actualField(p.visit(), "drawingAnton"));
    }
    private static void requireSameChunk(Prepared p) throws Exception {
        var presentation = assertInstanceOf(SonicCdPlayerPresentation.class, actualField(p.harness().module(), "playerPresentation"));
        assertEquals(presentation.pastForegroundImage().cameraX() & 0xFF80, p.placement().status().previousChunkWord(),
                "Fixed neutral scenes must not admit a new prespawn chunk");
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
    private static void complete(Object expected, Object actual, String name) throws Exception { compare(expected, actual, name, false); }
    /** Full value comparison; the environment-only check excludes exactly the one owned scratch byte. */
    private static void compare(Object expected, Object actual, String name, boolean primaryOnly) throws Exception {
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
            if (b.isPresent()) compare(b.orElseThrow(), a.orElseThrow(), name + ".value", primaryOnly);
        } else if (expected instanceof List<?> b) {
            var a = (List<?>) actual; assertEquals(b.size(), a.size(), name);
            for (int n = 0; n < b.size(); n++) compare(b.get(n), a.get(n), name + '[' + n + ']', primaryOnly);
        } else if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual), name);
            for (int n = 0; n < Array.getLength(expected); n++) compare(Array.get(expected,n), Array.get(actual,n), name + '[' + n + ']', primaryOnly);
        } else if (expected.getClass().isRecord()) {
            for (var field : expected.getClass().getRecordComponents()) {
                if (primaryOnly && expected instanceof CdPlayerMovement.Globals && field.getName().equals("primaryAngle")) continue;
                var read = field.getAccessor(); read.setAccessible(true);
                compare(read.invoke(expected), read.invoke(actual), name + '.' + field.getName(), primaryOnly);
            }
        } else if (Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot",
                "com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())) {
            for (var field : expected.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true); compare(field.get(expected), field.get(actual), name + '.' + field.getName(), primaryOnly);
            }
        } else assertEquals(expected, actual, name);
    }
}
