package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE ONLY: authored before the production mirror; compilation and Java/JUnit are UNRUN here.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Robot Generator/Main.asm:25-229, Robot Generator/Tile IDs.asm:7-10,
 * Object Functions.asm:137-196,228-237 and Object Spawner.asm:385-446 own the literal expectations.
 * Retained authenticated USA source is loaded only by the existing CdProgramAssets test ingress.
 * Explicit dirty pool images, callback stand-ins and receipt probes establish component contracts,
 * not a Frame/World owner, natural visit, current art, media parity, native run or pixel proof.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdRobotGeneratorAct2Factory {
    private static final int SLOT = 40, MAP_ADDRESS = 0x20E4F6, SCHEDULE_ADDRESS = 0x20E734;
    private static final byte[] BASE = HexFormat.of().parseHex("043f0409000000000454040000000000000000000000");
    private static Media retained;

    @Test void legacyPublicSurfaceRemainsConstructor8Assets5Globals5AndRun3() throws Exception {
        assertEquals(1, CdRobotGenerator.class.getConstructors().length);
        assertEquals(8, CdRobotGenerator.class.getConstructors()[0].getParameterCount());
        assertEquals(5, CdRobotGenerator.Assets.class.getRecordComponents().length);
        assertEquals(5, CdRobotGenerator.Globals.class.getRecordComponents().length);
        assertNotNull(CdRobotGenerator.class.getMethod("run", int.class, CdRobotGenerator.Globals.class, CdRobotGenerator.Inputs.class));
        assertNotNull(CdRobotGenerator.Assets.class.getMethod("fromPastAssets", CdR11bAssets.class));
        assertNotNull(CdRobotGenerator.class.getMethod("fromAct2Assets", CdObjectPlacementRuntime.class,
                CdR12aObjectAssets.class, CdSolidObject.class, CdRobotGenerator.SolidCommit.class,
                CdRobotGenerator.GoodFutureStore.class, CdRobotGenerator.AddPointsCall.class,
                CdRobotGenerator.FmCall.class, CdRobotGenerator.DrawObjectCall.class,
                CdRobotGenerator.DispatchObserver.class, CdRobotGenerator.ReceiptGuard.class));
    }

    @Test void actualSourceBorrowsExactFiniteViewsCachedVisualAndDoesNotDecodeArt() throws Exception {
        var owner = media().terrain().objects(); var decodedBefore = new java.util.HashMap<>((java.util.Map<?, ?>) field(owner, "decoded"));
        var a = CdRobotGenerator.Assets.fromAct2Assets(owner);
        assertSame(owner.view(CdR12aProfile.Asset.ROBOT_GENERATOR_BASE_TILES), a.baseTiles());
        assertSame(owner.view(CdR12aProfile.Asset.ROBOT_GENERATOR_EXPLOSION_SCHEDULE), a.explosionSchedule());
        assertSame(owner.robotGenerator(), a.visual()); assertSame(a.visual(), CdRobotGenerator.Assets.fromAct2Assets(owner).visual());
        assertArrayEquals(BASE, a.baseTiles().copy()); assertEquals(22, a.baseTiles().length());
        assertEquals(8, a.visual().mappingFrames().size()); assertEquals(1, a.visual().animations().size());
        assertEquals(MAP_ADDRESS, a.mappingsAddress()); assertEquals(SCHEDULE_ADDRESS, a.explosionScheduleAddress());
        assertEquals(574, a.visual().mappings().length()); assertEquals(8, a.visual().animationTable().length());
        assertEquals(64, a.explosionSchedule().length());
        assertEquals("69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59", sha(a.visual().mappings().copy()));
        assertEquals("1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123", sha(a.explosionSchedule().copy()));
        assertEquals(decodedBefore, field(owner, "decoded"));
        assertThrows(NullPointerException.class, () -> CdRobotGenerator.Assets.fromAct2Assets(null));
    }

    @Test void factoryRequiresTheActualPairedPlacementBeforeAnyDispatch() throws Exception {
        var h = new H(); var before = h.placement.capture(); var owner = media().terrain().objects();
        var unpaired = new CdObjectPlacementRuntime(owner.placements());
        assertThrows(IllegalArgumentException.class, () -> factory(unpaired, owner, h));
        assertThrows(NullPointerException.class, () -> factory(null, owner, h));
        assertThrows(NullPointerException.class, () -> factory(h.placement, null, h));
        assertSame(owner.robotGenerator(), h.body.assets().visual()); assertPlacement(before, h.placement);
    }

    @Test void actualInitPublishesHeaderThenOperandsThenFrameBeforeDrawAndPreservesAll96() throws Exception {
        for (int good : new int[]{0, 1, 255}) {
            var h = new H(); h.good = good; h.put(dirty(h.state()).moveByte(ID, 0x2D).moveByte(ROUTINE, 0)
                    .moveByte(SPRITE_FLAGS, 0xE1).moveByte(SAVED_FLAGS_ID, 0).moveWord(X, 0x1000).moveWord(Y, 0x160));
            var before = h.placement.capture(); byte[] expected = initExpected(h.state(), good, 1, 0x400);
            var result = h.run(); expected[1] &= 0x7F;
            assertArrayEquals(expected, result.state().copyBytes()); assertOthers(before, h.placement, SLOT);
            assertEquals(List.of("before:SOURCE_STORE", "prepared:1", "after:SOURCE_STORE", "completed:1",
                    "before:SOURCE_STORE", "prepared:2", "after:SOURCE_STORE", "completed:2",
                    "before:SOURCE_STORE", "prepared:3", "after:SOURCE_STORE", "completed:3",
                    "before:DrawCall", "prepared:4", "draw", "after:DrawCall", "completed:4"), h.order);
            assertEquals(3, h.publications.size()); assertEquals(4, h.completed.size());
            var header = h.publications.getFirst().output(); assertEquals(2, header.routine());
            assertEquals(before.pool().slots().get(SLOT - 32).longBits(MAPPINGS), header.longBits(MAPPINGS));
            assertEquals(MAP_ADDRESS, h.publications.get(1).output().longBits(MAPPINGS));
            assertEquals(good == 0 ? 2 : 0, h.publications.get(2).output().unsignedByte(MAP_FRAME));
            assertNull(h.pending); assertFalse(h.body.isCurrentCall(h.publications.getFirst()));
            assertThrows(IllegalStateException.class, () -> h.body.currentCallOrdinal(h.publications.getFirst()));
        }
    }

    @Test void rawPresentMainReadsNoSolidInputsOrMalformedTableBytes() throws Exception {
        var placement = new CdObjectPlacementRuntime(layout()); var state = placement.pool().state(SLOT)
                .moveByte(ID, 0x2D).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84).moveWord(X, 0x1000);
        placement.pool().write(state);
        var body = legacy(placement, new CdRobotGenerator.Assets(bytes(new byte[0]), visual(), MAP_ADDRESS, bytes(new byte[0]), SCHEDULE_ADDRESS),
                call -> placement.pool().write(call.expectedSource().moveByte(SPRITE_FLAGS, call.expectedSource().spriteFlags() & 0x7F)));
        var result = body.run(SLOT, new CdRobotGenerator.Globals(0, 1, 255, 0, 0x1000), null);
        assertEquals(state.moveByte(SPRITE_FLAGS, 4), result.state());
    }

    @Test void rawActAndPeriodByteBoundariesRetainHeaderBeforeUnsupportedTableRead() throws Exception {
        for (int[] row : new int[][]{{0, 1, 0x409}, {1, 1, 0x400}, {2, 2, 0}, {0, 10, 0}}) {
            var p = new CdObjectPlacementRuntime(layout()); var old = p.pool().state(SLOT).moveByte(ID, 0x2D).moveByte(ROUTINE, 0)
                    .moveByte(SPRITE_FLAGS, 0x80).moveWord(X, 0x1000).moveWord(Y, 0x160); p.pool().write(old);
            var body = legacy(p, constructed(), call -> p.pool().write(call.expectedSource().moveByte(SPRITE_FLAGS, call.expectedSource().spriteFlags() & 0x7F)));
            var result = body.run(SLOT, new CdRobotGenerator.Globals(1, row[1], row[0], 0, 0x1000), null);
            byte[] expected = initExpected(old, 1, row[1], row[2]); expected[1] &= 0x7F; assertArrayEquals(expected, result.state().copyBytes());
        }
        for (int[] row : new int[][]{{0, 11, 22}, {64, 255, 0x3FE}, {255, 255, 0x7F6}, {1, 0x80, 0x108}}) {
            var p = new CdObjectPlacementRuntime(layout()); var old = p.pool().state(SLOT).moveByte(ID, 0x2D); p.pool().write(old);
            var body = legacy(p, constructed(), call -> fail("unreached Draw"));
            var failure = assertThrows(CdRobotGenerator.UnsupportedRobotGenerator.class,
                    () -> body.run(SLOT, new CdRobotGenerator.Globals(1, row[1], row[0], 0, 0), null));
            assertEquals(CdRobotGenerator.UnsupportedBoundary.BASE_TILE_READ, failure.boundary()); assertEquals(row[2], failure.value());
            assertEquals(old.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 4)
                    .moveByte(X_RADIUS, 0x22).moveByte(WIDTH, 0x22).moveByte(Y_RADIUS, 0x20), p.pool().state(SLOT));
        }
    }

    @Test void actualDespawnClearsOnlyLoadedSavedByteThenDeletesFullDirty64() throws Exception {
        for (int time : new int[]{1, 0x81}) {
            var h = new H(); h.time = time; h.put(dirty(h.state()).moveByte(ID, 0x2D).moveByte(ROUTINE, 2)
                    .moveByte(SAVED_FLAGS_ID, 185).moveByte(SPRITE_FLAGS, 0xFF).moveWord(X, time == 1 ? 0x1800 : 0x1000));
            var before = h.placement.capture(); var result = h.run(); assertTrue(result.deleted());
            assertArrayEquals(new byte[64], result.state().copyBytes()); assertEquals(before.status(), h.placement.status());
            var saved = before.savedFlags().copy(); saved[558] &= 0x7F; assertArrayEquals(saved, h.placement.capture().savedFlags().copy());
            assertEquals(List.of(CdRobotGenerator.StoreKind.SAVED_FLAG_CLEAR, CdRobotGenerator.StoreKind.DELETE),
                    h.publications.stream().map(CdRobotGenerator.Publication::kind).toList());
            var clear = h.publications.getFirst(); assertEquals(558, clear.savedByteIndex()); assertEquals(0xE5, clear.previousValue());
            assertEquals(0x65, clear.value()); assertNull(clear.before()); assertNull(clear.output());
            assertEquals(SLOT, h.publications.get(1).before().identity().wholeIndex());
            assertEquals(3, h.completed.size()); assertOtherSlots(before, h.placement, SLOT);
        }
    }

    @Test void originalReceiptIdentityRejectsEqualCopiesAndInactiveOrDuplicateCompletion() throws Exception {
        var h = new H(); h.beforePublication = call -> {
            var clone = new CdRobotGenerator.Publication(call.ordinal(), call.kind(), call.actualCut(), call.expectedSource(),
                    call.before(), call.output(), call.savedByteIndex(), call.previousValue(), call.value());
            assertEquals(call, clone); assertNotSame(call, clone); assertFalse(h.body.isCurrentCall(clone));
            assertThrows(IllegalStateException.class, () -> h.body.currentCallOrdinal(clone));
        };
        h.beforeCall = call -> {
            var draw = assertInstanceOf(CdRobotGenerator.DrawCall.class, call);
            var clone = new CdRobotGenerator.DrawCall(draw.actualCut(), draw.expectedSource());
            assertEquals(draw, clone); assertFalse(h.body.isCurrentCall(clone));
            assertThrows(IllegalStateException.class, () -> h.prepared(clone));
        };
        h.run(); Object done = h.completed.getFirst(); assertFalse(h.body.isCurrentCall(done));
        assertThrows(IllegalStateException.class, () -> h.completed(done)); assertThrows(IllegalStateException.class, () -> h.prepared(done));
    }

    @Test void throwingBeforeAndAfterPublicationRetainOnlyRealNativePrefixesAndReleaseReentryGuard() throws Exception {
        for (boolean after : new boolean[]{false, true}) {
            var h = new H(); h.put(h.state().moveByte(ROUTINE, 0)); var old = h.state(); var original = new IllegalStateException("observer fault");
            Consumer<CdRobotGenerator.Publication> fail = call -> { throw original; };
            if (after) h.afterPublication = fail; else h.beforePublication = fail;
            assertSame(original, assertThrows(IllegalStateException.class, h::run));
            assertEquals(after ? old.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, old.spriteFlags() | 4).moveByte(PRIORITY, 4)
                    .moveByte(X_RADIUS, 0x22).moveByte(WIDTH, 0x22).moveByte(Y_RADIUS, 0x20) : old, h.state());
            assertTrue(h.completed.isEmpty()); h.beforePublication = call -> { }; h.afterPublication = call -> { }; h.pending = null;
            assertDoesNotThrow(h::run);
        }
    }

    @Test void drawThrowKeepsOriginalExceptionAndSuppressesForbiddenMutationVerification() throws Exception {
        var h = new H(); var original = new IllegalStateException("real Draw fault");
        h.onDraw = call -> { h.placement.pool().write(h.placement.pool().state(41).moveByte(SUBTYPE, 9)); throw original; };
        assertSame(original, assertThrows(IllegalStateException.class, h::run)); assertEquals(1, original.getSuppressed().length);
        assertEquals(9, h.placement.pool().state(41).subtype()); assertTrue(h.completed.isEmpty());
        h.onDraw = call -> { }; h.pending = null; assertDoesNotThrow(h::run);
    }

    @Test void observerAndGuardCannotMutateSavedBankOrOtherSlotsOnReturnOrThrow() throws Exception {
        for (boolean throwing : new boolean[]{false, true}) {
            var h = new H(); var original = new IllegalArgumentException("observer original");
            h.beforeCall = call -> { h.placement.bclrSavedFlag(2, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
                if (throwing) throw original; };
            var failure = assertThrows(RuntimeException.class, h::run);
            if (throwing) { assertSame(original, failure); assertEquals(1, original.getSuppressed().length); }
            else assertInstanceOf(IllegalStateException.class, failure);
            assertTrue(h.completed.isEmpty());
        }
        var h = new H(); h.onPrepared = call -> h.placement.pool().write(h.placement.pool().state(41).moveByte(ID, 3));
        assertThrows(IllegalStateException.class, h::run); assertTrue(h.completed.isEmpty());
    }

    @Test void allReachedObserverAndReceiptPortsRejectSameBodyReentry() throws Exception {
        var h = new H(); h.put(h.state().moveByte(ROUTINE, 0));
        h.beforePublication = call -> assertThrows(IllegalStateException.class, h::run);
        h.afterPublication = h.beforePublication; h.beforeCall = call -> assertThrows(IllegalStateException.class, h::run);
        h.afterCall = h.beforeCall; h.onPrepared = h.beforeCall; h.onCompleted = h.beforeCall;
        h.run(); assertEquals(4, h.completed.size()); assertNull(h.pending);
    }

    @Test void fullKernelExplosionStillPublishesGenuineDirtyChildThenFmAndDraw() throws Exception {
        var h = new H(); var child = dirty(h.placement.pool().state(32)).moveByte(ID, 0); h.placement.pool().write(child);
        h.put(h.state().moveByte(ROUTINE, 4).moveWord(ROBOT_GENERATOR_TIMER, 0x004D).moveLong(ROBOT_GENERATOR_SCHEDULE, SCHEDULE_ADDRESS));
        var result = h.run(); byte[] expected = child.copyBytes(); expected[0] = 0x18; expected[0x25] = 1;
        word(expected, 8, 0x1000); word(expected, 12, 0x160); assertArrayEquals(expected, h.placement.pool().state(32).copyBytes());
        assertEquals(List.of(CdRobotGenerator.StoreKind.SOURCE_STORE, CdRobotGenerator.StoreKind.SOURCE_STORE,
                CdRobotGenerator.StoreKind.CHILD_STORE), h.publications.stream().map(CdRobotGenerator.Publication::kind).toList());
        assertEquals(5, h.completed.size()); assertEquals(1, h.fm.size()); assertEquals(0x9E, h.fm.getFirst().nativeId());
        assertFalse(result.deleted()); assertEquals(3, h.publications.get(2).ordinal());
    }

    @Test void equalSavedByteStillObservesOneOriginalBclrBeforeDeletion() throws Exception {
        var h = new H(); h.placement.bclrSavedFlag(185, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        h.put(h.state().moveByte(SAVED_FLAGS_ID, 185).moveWord(X, 0x1800));
        h.run(); var call = h.publications.getFirst(); assertEquals(CdRobotGenerator.StoreKind.SAVED_FLAG_CLEAR, call.kind());
        assertEquals(0x65, call.previousValue()); assertEquals(0x65, call.value()); assertEquals(1, h.completed.stream().filter(c -> c == call).count());
    }

    @Test void throwingFmKeepsRealChildPrefixOriginalErrorAndNoLaterDrawOrCompletion() throws Exception {
        var h = new H(); var original = new IllegalStateException("FM owner original");
        h.onFm = call -> { h.placement.bclrSavedFlag(2, 1, CdObjectPlacementRuntime.SavedBit.LOADED); throw original; };
        h.put(h.state().moveByte(ROUTINE, 4).moveWord(ROBOT_GENERATOR_TIMER, 0x004D).moveLong(ROBOT_GENERATOR_SCHEDULE, SCHEDULE_ADDRESS));
        assertSame(original, assertThrows(IllegalStateException.class, h::run)); assertEquals(1, original.getSuppressed().length);
        assertEquals(0x18, h.placement.pool().state(32).id()); assertEquals(3, h.completed.size());
        assertTrue(h.order.stream().noneMatch("draw"::equals)); assertFalse(h.body.isCurrentCall(h.fm.getFirst()));
    }

    private static final class H implements CdRobotGenerator.DispatchObserver, CdRobotGenerator.ReceiptGuard {
        final CdObjectPlacementRuntime placement; final CdRobotGenerator body;
        final List<String> order = new ArrayList<>(); final List<CdRobotGenerator.Publication> publications = new ArrayList<>();
        final List<Object> completed = new ArrayList<>(); final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        final List<CdRobotGenerator.FmRequest> fm = new ArrayList<>(); Object pending; long lastOrdinal;
        int good, time = 1; boolean running;
        Consumer<CdRobotGenerator.Publication> beforePublication = call -> { }, afterPublication = call -> { };
        Consumer<Object> beforeCall = call -> { }, afterCall = call -> { }, onPrepared = call -> { }, onCompleted = call -> { };
        Consumer<CdRobotGenerator.DrawCall> onDraw = call -> { };
        Consumer<CdRobotGenerator.FmRequest> onFm = call -> { };
        H() throws Exception {
            byte[] saved = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]; Arrays.fill(saved, (byte) 0xE5);
            placement = CdObjectPlacementRuntime.fromAssets(media().terrain(), bytes(saved));
            put(state().moveByte(ID, 0x2D).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84).moveWord(X, 0x1000).moveWord(Y, 0x160));
            body = factory(placement, media().terrain().objects(), this);
        }
        CdObjectState state() { return placement.pool().state(SLOT); }
        void put(CdObjectState state) { placement.pool().write(state); }
        CdRobotGenerator.Result run() {
            if (running) return body.run(SLOT, new CdRobotGenerator.Globals(good, time, 1, 0, 0x1000), null);
            running = true; lastOrdinal = 0;
            try { return body.run(SLOT, new CdRobotGenerator.Globals(good, time, 1, 0, 0x1000), null); }
            finally { running = false; }
        }
        public void beforePublication(CdRobotGenerator.Publication call) { order.add("before:" + call.kind()); publications.add(call); beforePublication.accept(call); }
        public void afterPublication(CdRobotGenerator.Publication call) { order.add("after:" + call.kind()); afterPublication.accept(call); }
        public void beforeCall(Object call) { order.add("before:" + call.getClass().getSimpleName()); beforeCall.accept(call); }
        public void afterCall(Object call) { order.add("after:" + call.getClass().getSimpleName()); afterCall.accept(call); }
        public void prepared(Object call) {
            if (!body.isCurrentCall(call) || pending != null || seen.containsKey(call)) throw new IllegalStateException("not an original prepared call");
            long ordinal = body.currentCallOrdinal(call); assertEquals(lastOrdinal + 1, ordinal); lastOrdinal = ordinal;
            pending = call; order.add("prepared:" + ordinal); onPrepared.accept(call);
        }
        public void completed(Object call) {
            if (!body.isCurrentCall(call) || pending != call || seen.containsKey(call)) throw new IllegalStateException("not an original completed call");
            order.add("completed:" + body.currentCallOrdinal(call)); onCompleted.accept(call); pending = null; seen.put(call, true); completed.add(call);
        }
        void playFm(CdRobotGenerator.FmRequest call) { fm.add(call); onFm.accept(call); }
        void draw(CdRobotGenerator.DrawCall call) { order.add("draw"); onDraw.accept(call);
            var live = state(); put(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F)); }
    }

    private static CdRobotGenerator factory(CdObjectPlacementRuntime p, CdR12aObjectAssets owner, H h) throws Exception {
        return CdRobotGenerator.fromAct2Assets(p, owner, solid(), call -> fail("unreached Solid"), call -> fail("unreached goodFuture"),
                call -> fail("unreached AddPoints"), h::playFm, h::draw, h, h);
    }
    private static CdRobotGenerator legacy(CdObjectPlacementRuntime p, CdRobotGenerator.Assets a, CdRobotGenerator.DrawObjectCall draw) throws Exception {
        return new CdRobotGenerator(p, a, solid(), call -> fail("unreached Solid"), call -> fail("unreached goodFuture"),
                call -> fail("unreached AddPoints"), call -> fail("unreached FM"), draw);
    }
    private static CdSolidObject solid() throws Exception {
        return new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2(media().terrain(), media().player())));
    }
    private static CdRobotGenerator.Assets constructed() { return new CdRobotGenerator.Assets(bytes(BASE), visual(), MAP_ADDRESS, bytes(new byte[0]), SCHEDULE_ADDRESS); }
    private static CdObjectVisualAssets visual() {
        byte[] maps = new byte[17]; for (int i = 0; i < 8; i++) word(maps, 2 * i, 16);
        return CdObjectVisualAssets.decode(maps, 8, HexFormat.of().parseHex("00020303040506ff"), 1, 23);
    }
    private static byte[] initExpected(CdObjectState old, int good, int time, int tile) {
        byte[] b = old.copyBytes(); b[0x24] += 2; b[1] |= 4; b[0x18] = 4; b[0x17] = b[0x19] = 0x22; b[0x16] = 0x20;
        word(b, 2, tile); ByteBuffer.wrap(b).putInt(4, MAP_ADDRESS).putInt(0x2C, SCHEDULE_ADDRESS);
        word(b, 0x30, old.centreY()); word(b, 0x2A, 4); word(b, 0x32, 1); b[0x1A] = (byte) ((good == 0 ? 2 : 0) + (time == 0 ? 1 : 0)); return b;
    }
    private static CdObjectPlacements layout() {
        var b = ByteBuffer.allocate(26); b.putShort((short) 12).putShort((short) 20).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); b.putShort((short) 0xFFFF).putInt(0); return CdObjectPlacements.decodeLayout(b.array());
    }
    private static CdObjectState dirty(CdObjectState base) throws Exception {
        byte[] raw = new byte[64]; for (int i = 0; i < 64; i++) raw[i] = (byte) (0x31 + i * 17);
        var constructor = CdObjectState.class.getDeclaredConstructor(Object.class, CdObjectState.Identity.class, byte[].class);
        constructor.setAccessible(true); return constructor.newInstance(field(base, "owner"), base.identity(), raw);
    }
    private static void assertPlacement(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime p) {
        assertEquals(before.pool().slots(), p.capture().pool().slots()); assertOthers(before, p, -1);
    }
    private static void assertOthers(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime p, int except) {
        assertEquals(before.status(), p.status()); assertArrayEquals(before.savedFlags().copy(), p.capture().savedFlags().copy()); assertOtherSlots(before, p, except);
    }
    private static void assertOtherSlots(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime p, int except) {
        assertEquals(96, before.pool().slots().size()); for (var old : before.pool().slots())
            if (old.identity().wholeIndex() != except) assertEquals(old, p.pool().state(old.identity().wholeIndex()));
    }
    private static CdAssetBytes bytes(byte[] bytes) { return CdAssetBytes.copyOf(bytes); }
    private static void word(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static Object field(Object owner, String name) throws Exception { var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
    private record Media(CdR12aTerrainAssets terrain, CdR12aPlayerAssets player) { }
    private static Media media() throws Exception {
        if (retained != null) return retained; String root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Media(CdR12aTerrainAssets.fromPrograms(programs), CdR12aPlayerAssets.fromPrograms(programs));
        }
        return retained;
    }
}
