package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original-USA B factory and the unchanged native Ring algorithm on explicit test-owned cuts.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Ring/Main.asm:7-312.
 * No World/phase/dispatch entitlement, actual placement route, queue/SAT, current art, native run,
 * VInt, completed Past frame or regional parity is supplied by these isolated body calls. UNRUN.
 */
class TestCdPastRing {
    private static final int B_MAPPING = 0x209F52;
    private static final byte[] DELTAS = hex("100018002000001000180020101018182020f010e818e02010081810f008e810");
    private static final byte[] MAPPINGS = hex("00120018001e0024002a00300036003c0042"
            + "01f8050000f8" + "01f8050004f8" + "01f8010008fc" + "01f8050804f8"
            + "01f805000af8" + "01f805180af8" + "01f805100af8" + "01f805080af8" + "00");
    private static final byte[] ANIMATION = hex("00020504050607fc");

    // Catches borrowing A views/art or decoding a wider/different source instead of the finite B views.
    @Test void actualBFactoryBindsItsOwnFiniteViewsWithoutMutatingTheKnownCarriedBank() throws Exception {
        var f = retail(); var runtime = placement(f.past()); var before = runtime.capture();
        var ring = CdRing.fromAssets(runtime, f.past());
        assertCut(before, runtime);
        assertTrue(runtime.sourceIdentity().isEmpty()); assertTrue(ring.sourceIdentity().isEmpty());
        assertTrue(ring.artBinding().isEmpty(), "A ArtBinding cannot describe B current art");
        assertEquals(B_MAPPING, ring.sourceData().mappingAddress()); assertEquals(0xA7AE, ring.sourceData().baseTileWord());
        assertArrayEquals(DELTAS, f.past().view(CdR11bAssets.Asset.RING_DELTAS).bytes().copy());
        for (int i = 0; i < 16; i++) {
            assertEquals((int) DELTAS[i * 2], ring.sourceData().deltas().get(i).x());
            assertEquals((int) DELTAS[i * 2 + 1], ring.sourceData().deltas().get(i).y());
        }
        var visual = ring.sourceData().visuals();
        assertArrayEquals(MAPPINGS, f.past().view(CdR11bAssets.Asset.MAP_SPR_RING).bytes().copy());
        assertArrayEquals(ANIMATION, f.past().view(CdR11bAssets.Asset.ANI_RING).bytes().copy());
        assertArrayEquals(MAPPINGS, visual.mappings().copy()); assertArrayEquals(ANIMATION, visual.animationTable().copy());
        assertEquals(9, visual.mappingFrames().size()); assertEquals(1, visual.animations().size());
        int[] roots = {18, 24, 30, 36, 42, 48, 54, 60, 66};
        for (int frame = 0; frame < roots.length; frame++) {
            assertEquals(roots[frame], visual.mappingFrames().get(frame).relativeOffset());
            assertEquals(frame == 8 ? 0 : 1, visual.mappingFrames().get(frame).pieces().size());
        }
        assertEquals(List.of(4, 5, 6, 7), visual.animations().getFirst().frameBytes());
        assertEquals(0x0804, visual.mappingFrames().get(3).pieces().getFirst().rawTileWord());
        assertEquals(0x180A, visual.mappingFrames().get(5).pieces().getFirst().rawTileWord());
        assertThrows(UnsupportedOperationException.class, () -> ring.sourceData().deltas().clear());
    }

    // Catches treating the empty A-only sourceIdentity accessor as proof that authenticated B is pure.
    @Test void pureConstructorRejectsAuthenticatedBAndAWithoutAnyPoolOrBankWrite() throws Exception {
        var f = retail();
        for (var runtime : List.of(placement(f.past()), CdObjectPlacementRuntime.fromAssets(f.present()))) {
            var before = runtime.capture();
            assertThrows(IllegalArgumentException.class, () -> new CdRing(runtime, TestCdRing.data()));
            assertCut(before, runtime);
        }
        var pure = new CdObjectPlacementRuntime(f.past().placements());
        assertDoesNotThrow(() -> new CdRing(pure, TestCdRing.data()), "Pure format use remains available");
    }

    // Catches source lending across A/B or from actual assets to an unauthenticated constructed placement.
    @Test void factoryRejectsConstructedAndMixedPlacementSourcesBeforeMutation() throws Exception {
        var f = retail(); var past = placement(f.past()); var present = CdObjectPlacementRuntime.fromAssets(f.present());
        var constructed = new CdObjectPlacementRuntime(f.past().placements());
        var pastCut = past.capture(); var presentCut = present.capture(); var constructedCut = constructed.capture();
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(past, f.present().openingObjects()));
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(present, f.past()));
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(constructed, f.past()));
        assertThrows(NullPointerException.class, () -> CdRing.fromAssets(past, (CdR11bAssets) null));
        assertThrows(NullPointerException.class, () -> CdRing.fromAssets(null, f.past()));
        assertCut(pastCut, past); assertCut(presentCut, present); assertCut(constructedCut, constructed);
    }

    // Reflection constructs only a rejection control; no reflected value ever enters an accepted factory.
    @Test void foreignPrivateBPlacementIdentityCannotBorrowTheActualDiscSource() throws Exception {
        var a = retail().past(); var id = a.identity();
        var foreign = new CdR11bAssets.Identity(id.variant(), id.stage(), id.file(), id.domain(),
                id.mmdSha256(), "foreign-disc-rejection-control");
        var constructor = CdObjectPlacementRuntime.class.getDeclaredConstructor(CdObjectPlacements.class,
                Optional.class, Optional.class);
        constructor.setAccessible(true);
        var runtime = constructor.newInstance(a.placements(), Optional.empty(), Optional.of(foreign));
        var before = runtime.capture();
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(runtime, a));
        assertThrows(IllegalArgumentException.class, () -> new CdRing(runtime, TestCdRing.data()));
        assertCut(before, runtime);
    }

    // Catches allowing a supported mathematical B period to relabel an authenticated A body, or vice versa.
    @Test void retainedActualSourceMustMatchInputsBeforeAnyRoutineOrGlobalsCall() throws Exception {
        var f = retail(); var b = placement(f.past()); seed(b, 32, 0xF0, 0x720, 0x21, 3);
        var a = CdObjectPlacementRuntime.fromAssets(f.present()); seed(a, 32, 0xF0, 0x720, 0x21, 3);
        var bRing = CdRing.fromAssets(b, f.past()); var aRing = CdRing.fromAssets(a, f.present().openingObjects());
        var bPorts = new Ports(b); var aPorts = new Ports(a); var bCut = b.capture(); var aCut = a.capture();
        assertThrows(IllegalArgumentException.class, () -> bRing.executeSelected(32, TestCdRing.input(), bPorts, bPorts));
        assertThrows(IllegalArgumentException.class, () -> aRing.executeSelected(32, input(0, 0, 0), aPorts, aPorts));
        for (var bad : List.of(new CdRing.Inputs(CdReleaseVariant.JAPAN, CdStageKey.parse("R11B"), 0, 0, 0, 0),
                new CdRing.Inputs(CdReleaseVariant.EUROPE, CdStageKey.parse("R11B"), 0, 0, 0, 0),
                new CdRing.Inputs(CdReleaseVariant.USA, CdStageKey.parse("R12B"), 0, 0, 0, 0),
                new CdRing.Inputs(CdReleaseVariant.USA, CdStageKey.parse("R11B"), 6, 0, 0, 0), input(0, 3, 0))) {
            assertThrows(UnsupportedOperationException.class, () -> bRing.executeSelected(32, bad, bPorts, bPorts));
            assertCut(bCut, b);
        }
        assertCut(bCut, b); assertCut(aCut, a); assertTrue(bPorts.events.isEmpty()); assertTrue(aPorts.events.isEmpty());
    }

    // Literal source stores over all64 bytes catch implicit clearing, wrong B map, member renumbering and early child execution.
    @Test void periodZeroInitRetainsDirtyImagesSkipsCollectedMiddleAndRunsLaterMembersOnlyWhenSelected() throws Exception {
        var runtime = placement(retail().past()); var ring = CdRing.fromAssets(runtime, retail().past());
        runtime.pool().write(dirty(runtime.pool().state(32)).moveByte(ID, 0x10).moveByte(ROUTINE, 0)
                .moveWord(X, 0xF0).moveWord(Y, 0x720).moveByte(SUBTYPE, 0x23).moveByte(SAVED_FLAGS_ID, 3));
        runtime.pool().write(dirty(runtime.pool().state(33)).moveByte(ID, 0));
        runtime.pool().write(dirty(runtime.pool().state(34)).moveByte(ID, 0));
        runtime.bsetSavedFlag(3, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_2);
        var before = runtime.capture(); var ports = new Ports(runtime);
        ring.executeSelected(32, input(0, 0, 0), ports, ports);
        var expected = images(before);
        expected.set(0, initialized(expected.get(0), 0xF0, 0x720, 0xF0, 3, 1, 3));
        expected.set(1, initialized(expected.get(1), 0x130, 0x720, 0xF0, 3, 3, -1));
        expected.set(2, initialized(expected.get(2), 0x150, 0x720, 0xF0, 3, 4, -1));
        assertImages(expected, runtime); assertEquals(List.of("draw:32"), ports.events);
        byte[] bank = before.savedFlags().copy(); bank[2 + 3 * 3] = 2;
        assertArrayEquals(bank, runtime.capture().savedFlags().copy());
        ring.executeSelected(33, input(0, 0, 0), ports, ports);
        ring.executeSelected(34, input(0, 0, 0), ports, ports);
        expected.get(1)[0x1A] = expected.get(2)[0x1A] = 3;
        assertImages(expected, runtime); assertEquals(List.of("draw:32", "draw:33", "draw:34"), ports.events);
        assertEquals(new CdRingGlobals(0, 3, 0, 0, 0, 0, 7, 3), ports.value);
        assertArrayEquals(bank, runtime.capture().savedFlags().copy());
    }

    // Catches deleting the group, or testing/deleting its parent before children are created.
    @Test void collectedFirstMemberCreatesItsLaterMembersThenDeletesOnlyTheWholeParentImage() throws Exception {
        var runtime = placement(retail().past()); seed(runtime, 32, 0xF0, 0x720, 0x22, 3);
        runtime.bsetSavedFlag(3, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        var before = runtime.capture(); var ports = new Ports(runtime);
        CdRing.fromAssets(runtime, retail().past()).executeSelected(32, input(0, 0, 0), ports, ports);
        var expected = images(before); expected.set(0, new byte[64]);
        expected.set(1, initialized(expected.get(1), 0x110, 0x720, 0xF0, 3, 2, -1));
        expected.set(2, initialized(expected.get(2), 0x130, 0x720, 0xF0, 3, 3, -1));
        assertImages(expected, runtime); assertTrue(ports.events.isEmpty());
        byte[] bank = before.savedFlags().copy(); bank[2 + 3 * 3] = 1;
        assertArrayEquals(bank, runtime.capture().savedFlags().copy());
    }

    // Catches wrapping/reusing earlier slots, clearing failed allocations or rolling back a completed native prefix.
    @Test void lateAllocationFailurePreservesTheCreatedMemberAndClearsLoadedBeforeTheFailedSearch() throws Exception {
        var runtime = placement(retail().past());
        for (int whole = 32; whole < 128; whole++) runtime.pool().write(dirty(runtime.pool().state(whole)).moveByte(ID, 1));
        seed(runtime, 126, 0xF0, 0x720, 0x26, 3);
        runtime.pool().write(dirty(runtime.pool().state(127)).moveByte(ID, 0));
        var before = runtime.capture(); var ports = new Ports(runtime);
        CdRing.fromAssets(runtime, retail().past()).executeSelected(126, input(0, 0, 0), ports, ports);
        var expected = images(before);
        expected.set(94, initialized(expected.get(94), 0xF0, 0x720, 0xF0, 3, 1, 3));
        expected.set(95, initialized(expected.get(95), 0x110, 0x720, 0xF0, 3, 2, -1));
        assertImages(expected, runtime); assertEquals(List.of("draw:126"), ports.events);
        byte[] bank = before.savedFlags().copy(); bank[2 + 3 * 3] = 0;
        assertArrayEquals(bank, runtime.capture().savedFlags().copy());
    }

    // Catches signed/range/off-by-one changes, using X instead of anchor, and ignoring prior onscreen or timeStop.
    @Test void periodZeroMainRetainsNativeRangeAndPriorOnscreenAndTimeStopSemantics() throws Exception {
        int[][] cases = {{0x280, 0x80, 4, 0, 1}, {0x300, 0x80, 4, 0, 0},
                {0, 0, 4, 0, 1}, {0x1000, 0x80, 0x84, 0, 1}, {0x280, 0x80, 4, 1, 1}};
        for (int[] c : cases) {
            var runtime = placement(retail().past()); seed(runtime, 32, 0x7777, 0x720, 0x21, 3);
            runtime.pool().write(runtime.pool().state(32).moveByte(ROUTINE, 2).moveWord(RING_ANCHOR_X, c[0])
                    .moveByte(SPRITE_FLAGS, c[2]).moveByte(MAP_FRAME, 0xE4));
            var before = runtime.capture(); var ports = new Ports(runtime);
            ports.value = new CdRingGlobals(0, 3, 0, 0, 0, c[3], 7, 3); var globals = ports.value;
            CdRing.fromAssets(runtime, retail().past()).executeSelected(32, input(c[1], 0, 0), ports, ports);
            var expected = images(before);
            if (c[4] == 0) expected.set(0, new byte[64]);
            else if (c[3] == 0) expected.getFirst()[0x1A] = 3;
            assertImages(expected, runtime); assertEquals(c[4], ports.events.size());
            assertArrayEquals(before.savedFlags().copy(), runtime.capture().savedFlags().copy()); assertEquals(globals, ports.value);
        }
    }

    // Catches stale publication after DrawObject, swallowing failure or rolling back the earlier member/bank prefix.
    @Test void throwingDrawRetainsAllCompletedInitWritesAndItsLivePointerMutation() throws Exception {
        var runtime = placement(retail().past()); seed(runtime, 32, 0xF0, 0x720, 0x21, 3);
        var before = runtime.capture(); var ports = new Ports(runtime); var failure = new IllegalStateException("draw boundary");
        ports.onDraw = () -> {
            runtime.pool().write(runtime.pool().state(32).moveByte(MAP_FRAME, 0x6D)); throw failure;
        };
        var ring = CdRing.fromAssets(runtime, retail().past());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> ring.executeSelected(32, input(0, 0, 0), ports, ports)));
        var expected = images(before);
        expected.set(0, initialized(expected.get(0), 0xF0, 0x720, 0xF0, 3, 1, 0x6D));
        expected.set(1, initialized(expected.get(1), 0x110, 0x720, 0xF0, 3, 2, -1));
        assertImages(expected, runtime); assertEquals(List.of("draw:32"), ports.events);
        byte[] bank = before.savedFlags().copy(); bank[2 + 3 * 3] = 0;
        assertArrayEquals(bank, runtime.capture().savedFlags().copy());
    }

    // Catches moving collection/bank/animation across the native synchronous sound call.
    @Test void periodZeroSoundFailurePublishesOnlyTheNativeRewardPrefixBeforeBankAndSparkle() throws Exception {
        var runtime = placement(retail().past()); seed(runtime, 32, 0xF0, 0x720, 0x20, 3);
        var ports = new Ports(runtime); var ring = CdRing.fromAssets(runtime, retail().past());
        ring.executeSelected(32, input(0, 0, 0), ports, ports); ports.events.clear();
        runtime.pool().write(runtime.pool().state(32).moveByte(ROUTINE, 4)); var before = runtime.capture();
        ports.value = new CdRingGlobals(99, 3, 0, 0, 0, 0, 7, 3);
        var failure = new IllegalStateException("sound boundary"); ports.onSound = () -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> ring.executeSelected(32, input(0, 0, 0), ports, ports)));
        var expected = images(before); expected.getFirst()[0x24] = 6; expected.getFirst()[0x20] = 0; expected.getFirst()[0x18] = 1;
        assertImages(expected, runtime); assertEquals(new CdRingGlobals(100, 4, 2, 1, 1, 0, 7, 3), ports.value);
        assertEquals(List.of("globals", "SCMD_YESSFX"), ports.events);
        assertArrayEquals(before.savedFlags().copy(), runtime.capture().savedFlags().copy());
    }

    // Catches accidental A factory/getter/art/data changes while introducing the B overload.
    @Test void existingActualAFactoryAndNativeInitStillUseTheirOwnIdentityAndOriginalMap() throws Exception {
        var a = retail().present(); var runtime = CdObjectPlacementRuntime.fromAssets(a); var objects = a.openingObjects();
        var ring = CdRing.fromAssets(runtime, objects); seed(runtime, 32, 0xF0, 0x720, 0x20, 3);
        var before = runtime.capture(); var ports = new Ports(runtime);
        assertEquals(Optional.of(a.identity()), ring.sourceIdentity()); assertSame(objects.rings(), ring.sourceData().visuals());
        assertEquals(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS), ring.artBinding().orElseThrow());
        ring.executeSelected(32, TestCdRing.input(), ports, ports);
        byte[] expected = initialized(before.pool().slots().getFirst().copyBytes(), 0xF0, 0x720, 0xF0, 3, 1, 3);
        putLong(expected, 4, 0x20A0FA); assertArrayEquals(expected, runtime.pool().state(32).copyBytes());
        assertEquals(List.of("draw:32"), ports.events);
    }

    private static CdRing.Inputs input(int camera, int period, int direction) {
        return new CdRing.Inputs(CdReleaseVariant.USA, CdStageKey.parse("R11B"), 0, camera, period, direction);
    }
    private static CdObjectPlacementRuntime placement(CdR11bAssets a) {
        return CdObjectPlacementRuntime.fromAssets(a, CdAssetBytes.copyOf(knownBank()));
    }
    private static byte[] knownBank() {
        byte[] bank = new byte[766];
        for (int i = 0; i < bank.length; i++) bank[i] = (byte) ((0x21 + i * 13) & 0x7F);
        bank[0] = (byte) 0xC7; bank[1] = (byte) 0x9E;
        bank[2 + 3 * 3] = (byte) 0x80; bank[2 + 3 * 3 + 1] = 0; bank[2 + 3 * 3 + 2] = 0x55;
        return bank;
    }
    private static void seed(CdObjectPlacementRuntime r, int whole, int x, int y, int subtype, int saved) {
        r.pool().write(r.pool().state(whole).moveByte(ID, 0x10).moveByte(ROUTINE, 0).moveWord(X, x).moveWord(Y, y)
                .moveByte(SUBTYPE, subtype).moveByte(SAVED_FLAGS_ID, saved));
        r.bsetSavedFlag(saved, 0, CdObjectPlacementRuntime.SavedBit.LOADED);
    }
    private static CdObjectState dirty(CdObjectState s) {
        for (var field : CdObjectState.ByteField.values()) s = s.moveByte(field, 0x31 + field.byteOffset() * 7);
        return s.moveWord(TILE, 0x7312).moveLong(CdObjectState.LongField.MAPPINGS, 0x20CAFE).moveLong(X_POSITION, 0x1111ABCD)
                .moveLong(Y_POSITION, 0x2222FEDC).moveWord(X_VELOCITY, 0x3579).moveWord(Y_VELOCITY, 0xACE0);
    }
    private static byte[] initialized(byte[] old, int x, int y, int anchor, int saved, int member, int mapFrame) {
        byte[] b = old.clone(); b[0] = 0x10; b[0x24] = 2; putWord(b, 8, x); putWord(b, 0x32, anchor); putWord(b, 12, y);
        putLong(b, 4, B_MAPPING); putWord(b, 2, 0xA7AE); b[0x18] = 2; b[1] = 4; b[0x20] = 0x47;
        b[0x19] = 8; b[0x16] = 8; b[0x23] = (byte) saved; b[0x34] = (byte) member;
        if (mapFrame >= 0) b[0x1A] = (byte) mapFrame;
        return b;
    }
    private static List<byte[]> images(CdObjectPlacementRuntime.Snapshot cut) {
        var images = new ArrayList<byte[]>(); for (var image : cut.pool().slots()) images.add(image.copyBytes()); return images;
    }
    private static void assertImages(List<byte[]> expected, CdObjectPlacementRuntime actual) {
        assertEquals(96, expected.size());
        for (int index = 0; index < 96; index++) assertArrayEquals(expected.get(index), actual.pool().state(32 + index).copyBytes(), "whole " + (32 + index));
    }
    private static void assertCut(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime actual) {
        assertEquals(before.status(), actual.status()); assertEquals(before.pool().slots(), actual.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(), actual.capture().savedFlags().copy());
    }
    private static void putWord(byte[] b, int at, int word) { b[at] = (byte) (word >>> 8); b[at + 1] = (byte) word; }
    private static void putLong(byte[] b, int at, int bits) { putWord(b, at, bits >>> 16); putWord(b, at + 2, bits); }
    private static byte[] hex(String s) { return HexFormat.of().parseHex(s); }

    /** Test-owned synchronous boundary observations; this is not DrawObject or the shared World queue. */
    private static final class Ports implements CdRing.GlobalsPort, CdRing.SourceCalls {
        private final CdObjectPlacementRuntime runtime;
        private CdRingGlobals value = new CdRingGlobals(0, 3, 0, 0, 0, 0, 7, 3);
        private final List<String> events = new ArrayList<>();
        private Runnable onDraw = () -> { }; private Runnable onSound = () -> { };
        private Ports(CdObjectPlacementRuntime runtime) { this.runtime = runtime; }
        @Override public CdRingGlobals read() { return value; }
        @Override public void write(CdRingGlobals next) { value = next; events.add("globals"); }
        @Override public void playFmSound(CdRing.FmSound sound) { events.add(sound.name()); onSound.run(); }
        @Override public void subCpuCommand(CdRing.SubCpuCommand command) { events.add(command.name()); onSound.run(); }
        @Override public void drawObject(CdObjectPool pool, int whole) {
            assertSame(runtime.pool(), pool); events.add("draw:" + whole); onDraw.run();
        }
    }

    private record Fixture(CdR11aAssets present, CdR11bAssets past) { }
    private static String retainedRoot;
    private static Fixture retained;
    private static synchronized Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "Explicit B factory/body checks require original configured USA media");
        assertFalse(root.isBlank(), "Original USA media root must not be blank");
        if (retained != null && root.equals(retainedRoot)) return retained;
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured original USA source needs exactly one existing CUE"); cue = cues.getFirst();
        }
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            var present = CdR11aAssetLoader.load(owner, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
            var past = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(owner));
            assertEquals(CdNativeFile.R11B, past.identity().file());
            assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, past.identity().domain());
            assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", past.identity().mmdSha256());
            retained = new Fixture(present, past); retainedRoot = root; return retained;
        }
    }
}
