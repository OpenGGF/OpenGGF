package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdExplosion.NativeCall.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Original B source binding with explicit live-pool component ingress. Expected raw bytes follow
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff _Objects/Explosion/Main.asm:14-106 and Object Animate.asm:14-95.
 * The draw callback is a declared synchronous stand-in. No World, natural route, native execution,
 * current art, FM driver or visual-parity claim is supplied by these checks.
 */
class TestCdExplosionPastFactory {
    private static final int SLOT = 40, MAP = 0x21D714;
    private static CdR11bAssets actual;

    @Test void namedBFactoryKeepsTheExistingPublicConstructorAndAssetsShape() throws Exception {
        var h = new H();
        assertEquals(1, CdExplosion.class.getConstructors().length);
        assertEquals(3, CdExplosion.class.getConstructors()[0].getParameterCount());
        assertEquals(2, CdExplosion.Assets.class.getRecordComponents().length);
        assertNotNull(CdExplosion.class.getMethod("fromPastAssets", CdObjectPlacementRuntime.class,
                CdR11bAssets.class, CdExplosion.DrawObjectCall.class));
        assertEquals(MAP, h.body.assets().mappingsAddress());
        assertArrayEquals(retail().view(CdR11bAssets.Asset.MAP_SPR_EXPLOSION).bytes().copy(),
                h.body.assets().explosion().mappings().copy());
        assertArrayEquals(retail().view(CdR11bAssets.Asset.ANI_EXPLOSION).bytes().copy(),
                h.body.assets().explosion().animationTable().copy());
        assertEquals(0x8680, CdExplosion.NATIVE_TILE_WORD);
        var before = h.runtime.capture();
        assertThrows(NullPointerException.class, () -> CdExplosion.Assets.fromPastAssets(null));
        assertThrows(NullPointerException.class, () -> CdExplosion.fromPastAssets(h.runtime, null, (p, i) -> { }));
        assertThrows(NullPointerException.class, () -> CdExplosion.fromPastAssets(null, retail(), (p, i) -> { }));
        assertThrows(NullPointerException.class, () -> CdExplosion.fromPastAssets(h.runtime, retail(), null));
        same(before, h.runtime);
    }

    @Test void nonbadnikDirtyInitRetainsEveryUnwrittenByteAndMakesNoPointsAcrossRawLayerAndSubtypeBytes() throws Exception {
        for (int subtype : new int[]{0, 1, 255}) for (int layer : new int[]{0, 1, 255}) for (int flip = 0; flip < 4; flip++) {
            var h = new H();
            h.put(dirty(h.slot()).moveByte(ID, 0x18).moveByte(ROUTINE, 0).moveByte(ROUTINE_2, 1)
                    .moveByte(SUBTYPE, subtype).moveByte(LAYER, layer).moveByte(SPRITE_FLAGS, 0xA0 | flip)
                    .moveByte(FLAGS, 0x50 | flip).moveWord(X, 0x2368).moveWord(Y, 0x009E)
                    .moveWord(X_FRACTION, 0x1234).moveWord(Y_FRACTION, 0x5678));
            var before = h.runtime.capture(); byte[] expected = initExpected(h.slot(), subtype, layer);
            var result = h.run();
            assertEquals(List.of(call(INIT), call(MAIN), call(DRAW_OBJECT)), result.effects());
            assertArrayEquals(expected, h.draws.getFirst().copyBytes()); expected[1] &= 0x7F;
            assertArrayEquals(expected, result.state().copyBytes());
            assertEquals(1, result.state().unsignedByte(ROUTINE_2));
            assertFalse(result.deleted()); otherImages(before, h.runtime, SLOT);
        }
    }

    @Test void bothActualScriptsHoldEachFrameFourVisitsThenFcAndFullDeleteWithoutSavedAccess() throws Exception {
        for (int subtype : new int[]{0, 255}) {
            var h = new H(); h.put(dirty(h.slot()).moveByte(ID, 0x18).moveByte(ROUTINE, 0)
                    .moveByte(ROUTINE_2, 1).moveByte(SUBTYPE, subtype).moveByte(SAVED_FLAGS_ID, 219));
            var before = h.runtime.capture(); int[] frames = subtype == 0 ? new int[]{0, 5, 6, 3, 4} : new int[]{0, 1, 2, 3, 4};
            for (int visit = 1; visit <= 20; visit++) {
                var result = h.run(); var state = result.state();
                assertEquals(2, state.routine()); assertEquals(frames[(visit - 1) / 4], state.unsignedByte(MAP_FRAME));
                assertEquals(3 - (visit - 1) % 4, state.unsignedByte(ANIMATION_TIME));
                assertEquals((visit - 1) / 4 + 1, state.unsignedByte(ANIMATION_FRAME));
                assertTrue(result.effects().stream().noneMatch(CdExplosion.PointsCreated.class::isInstance));
            }
            var advanced = h.run(); assertEquals(4, advanced.state().routine());
            assertEquals(4, advanced.state().unsignedByte(MAP_FRAME)); assertEquals(5, advanced.state().unsignedByte(ANIMATION_FRAME));
            assertEquals(3, advanced.state().unsignedByte(ANIMATION_TIME)); assertEquals(21, h.draws.size());
            var done = h.run(); assertEquals(List.of(new CdExplosion.Deleted(), call(DONE)), done.effects());
            assertTrue(done.deleted()); assertArrayEquals(new byte[64], done.state().copyBytes());
            assertEquals(21, h.draws.size()); otherImages(before, h.runtime, SLOT);
            assertThrows(IllegalArgumentException.class, h::run);
        }
    }

    @Test void nonzeroRoutine2SkipsTheEntireFindObjSlotEvenWhenEveryDynamicIdIsOccupied() throws Exception {
        var h = new H();
        for (int i = 32; i < 128; i++) h.pool().write(h.pool().state(i).moveByte(ID, 0x10));
        h.put(h.slot().moveByte(ID, 0x18).moveByte(ROUTINE, 0).moveByte(ROUTINE_2, 255));
        var before = h.runtime.capture(); var result = h.run();
        assertEquals(List.of(call(INIT), call(MAIN), call(DRAW_OBJECT)), result.effects());
        assertEquals(255, h.slot().unsignedByte(ROUTINE_2)); otherImages(before, h.runtime, SLOT);
    }

    @Test void badnikBindingStillCreatesOnlyTheNativeFirstFreePointsPrefixAndFlowerConversion() throws Exception {
        var h = new H(); var free = dirty(h.pool().state(32)).moveByte(ID, 0);
        h.pool().write(free); h.put(h.slot().moveByte(ROUTINE_2, 0).moveByte(SUBTYPE, 1)
                .moveWord(EXPLOSION_POINTS_FRAME, 0xFF0A).moveWord(X, 0x2368).moveWord(Y, 0x009E));
        var result = h.run(); byte[] expected = free.copyBytes(); expected[0] = 0x1C;
        word(expected, 8, 0x2368); word(expected, 12, 0x009E); expected[0x28] = (byte) 0x85;
        assertArrayEquals(expected, h.pool().state(32).copyBytes());
        assertEquals(new CdExplosion.PointsCreated(32, 0x85), result.effects().getFirst());
        h.put(h.slot().moveByte(ROUTINE, 4)); byte[] parent = h.slot().copyBytes(); parent[0] = 0x1F; parent[0x24] = 0;
        var flower = h.run(); assertEquals(List.of(new CdExplosion.BecameFlower(), call(DONE)), flower.effects());
        assertArrayEquals(parent, flower.state().copyBytes()); assertEquals(1, h.draws.size());
    }

    @Test void rawInvalidAnimationIdIsUnreadWhileHeldAndStopsAtTheReachedDirectoryWithItsSubqPrefix() throws Exception {
        var h = new H(); h.put(h.slot().moveByte(ROUTINE, 2).moveByte(ANIMATION, 255).moveByte(PREVIOUS_ANIMATION, 255)
                .moveByte(ANIMATION_TIME, 1).moveByte(ANIMATION_FRAME, 0xA5).moveByte(MAP_FRAME, 0xFE)
                .moveLong(MAPPINGS, 0xDEADBEEF));
        var held = h.slot(); var result = h.run(); byte[] expected = held.copyBytes(); expected[0x1E] = 0; expected[1] &= 0x7F;
        assertArrayEquals(expected, result.state().copyBytes());
        var before = h.slot(); var failure = assertThrows(CdExplosion.UnsupportedExplosion.class, h::run);
        assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_DIRECTORY_READ, failure.boundary());
        assertNotNull(failure.getCause()); assertEquals(before.addByte(ANIMATION_TIME, -1), h.slot());
        assertEquals(1, h.draws.size());
    }

    @Test void changedInvalidIdCommitsOnlyNativeIdResetAndSubqBeforeTheDirectoryReadStops() throws Exception {
        var h = new H(); h.put(h.slot().moveByte(ROUTINE, 2).moveByte(ANIMATION, 2).moveByte(PREVIOUS_ANIMATION, 0)
                .moveByte(ANIMATION_FRAME, 0xA7).moveByte(ANIMATION_TIME, 0x55));
        var before = h.slot(); var fault = assertThrows(CdExplosion.UnsupportedExplosion.class, h::run);
        assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_DIRECTORY_READ, fault.boundary());
        assertEquals(before.moveByte(PREVIOUS_ANIMATION, 2).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 255), h.slot());
        assertTrue(h.draws.isEmpty());
    }

    @Test void rawScriptIndexStopsAfterTheActualDelayStoreWithoutClampingTheIndex() throws Exception {
        for (int animation : new int[]{0, 1}) {
            var h = new H(); h.put(h.slot().moveByte(ROUTINE, 2).moveByte(ANIMATION, animation)
                    .moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 255).moveByte(ANIMATION_TIME, 0));
            var before = h.slot(); var fault = assertThrows(CdExplosion.UnsupportedExplosion.class, h::run);
            assertEquals(CdExplosion.UnsupportedBoundary.ANIMATION_FRAME_READ, fault.boundary());
            assertEquals(before.moveByte(ANIMATION_TIME, 3), h.slot()); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void drawFailureRetainsItsCommittedInitMainAndCallbackWritesAndTheSameInstanceCanResume() throws Exception {
        var h = new H(); h.put(h.slot().moveByte(ROUTINE_2, 1)); var expected = initExpected(h.slot(), 0, 0);
        var original = new IllegalStateException("draw owner fault");
        h.onDraw = state -> { h.put(state.moveByte(SPRITE_FLAGS, state.spriteFlags() | 0x80)); throw original; };
        assertSame(original, assertThrows(IllegalStateException.class, h::run)); expected[1] |= (byte) 0x80;
        assertArrayEquals(expected, h.slot().copyBytes()); assertEquals(1, h.draws.size());
        h.onDraw = state -> { }; h.run(); assertEquals(2, h.slot().unsignedByte(ANIMATION_TIME));
        assertEquals(2, h.draws.size());
    }

    @Test void samePastInstanceRejectsDrawReentryBeforeAnySecondDispatch() throws Exception {
        var h = new H(); h.onDraw = state -> assertThrows(IllegalStateException.class, h::run);
        h.run(); assertEquals(1, h.draws.size()); assertEquals(3, h.slot().unsignedByte(ANIMATION_TIME));
    }

    @Test void unsupportedRoutineAndForeignIdLeaveTheWholePlacementSnapshotUntouched() throws Exception {
        var h = new H();
        for (int routine : new int[]{1, 3, 6, 254, 255}) {
            h.put(h.slot().moveByte(ROUTINE, routine)); var before = h.runtime.capture();
            var fault = assertThrows(CdExplosion.UnsupportedExplosion.class, h::run);
            assertEquals(CdExplosion.UnsupportedBoundary.ROUTINE_DISPATCH, fault.boundary()); same(before, h.runtime);
        }
        h.put(h.slot().moveByte(ID, 0x1C)); var before = h.runtime.capture();
        assertThrows(IllegalArgumentException.class, h::run); same(before, h.runtime); assertTrue(h.draws.isEmpty());
    }

    private static final class H {
        final CdObjectPlacementRuntime runtime; final CdExplosion body; final List<CdObjectState> draws = new ArrayList<>();
        Consumer<CdObjectState> onDraw = state -> { };
        H() throws Exception {
            byte[] saved = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]; Arrays.fill(saved, (byte) 0xA5);
            runtime = CdObjectPlacementRuntime.fromAssets(retail(), CdAssetBytes.copyOf(saved));
            body = CdExplosion.fromPastAssets(runtime, retail(), (pool, id) -> {
                var state = pool.state(id.wholeIndex()); draws.add(state); onDraw.accept(state);
                var live = pool.state(id.wholeIndex()); pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
            });
            put(slot().moveByte(ID, 0x18).moveByte(ROUTINE, 0).moveByte(ROUTINE_2, 1));
        }
        CdObjectPool pool() { return runtime.pool(); } CdObjectState slot() { return pool().state(SLOT); }
        void put(CdObjectState state) { pool().write(state); } CdExplosion.Result run() { return body.run(SLOT); }
    }
    private static CdExplosion.CallReturned call(CdExplosion.NativeCall call) { return new CdExplosion.CallReturned(call); }
    private static byte[] initExpected(CdObjectState state, int subtype, int layer) {
        byte[] e = state.copyBytes(); e[0x24] = 2; e[1] = (byte) ((state.spriteFlags() & 0xFC) | 4 | (state.flags() & 3));
        e[0x18] = 1; word(e, 2, layer == 0 ? 0x8680 : 0x0680); putLong(e, 4, MAP);
        e[0x20] = 0; e[0x1B] = 1; e[0x1E] = 3; e[0x1A] = 0; e[0x1C] = e[0x1D] = (byte) (subtype == 0 ? 0 : 1);
        return e;
    }
    private static CdObjectState dirty(CdObjectState s) {
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0x53 + 7 * f.byteOffset());
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xA153 + 0x103 * f.byteOffset());
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xA1536729 + 0x01030507 * f.byteOffset());
        return s;
    }
    private static void word(byte[] b, int at, int n) { b[at] = (byte) (n >>> 8); b[at + 1] = (byte) n; }
    private static void putLong(byte[] b, int at, int n) { word(b, at, n >>> 16); word(b, at + 2, n); }
    private static void otherImages(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime runtime, int except) {
        var after = runtime.capture(); assertEquals(before.status(), after.status());
        assertArrayEquals(before.savedFlags().copy(), after.savedFlags().copy());
        for (int i = 0; i < 96; i++) if (i != except - 32) assertEquals(before.pool().slots().get(i), after.pool().slots().get(i));
    }
    private static void same(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime runtime) { otherImages(before, runtime, -1); }
    private static CdR11bAssets retail() throws Exception {
        if (actual != null) return actual;
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            actual = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));
        }
        return actual;
    }
}
