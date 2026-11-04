package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original-USA R12A source relations and isolated existing placement/Ring algorithms.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Object Spawner.asm:42-319;
 * Ring/Main.asm:20-35,150-174,214-232. No Frame/World/TitleCard/pass authority or current-art claim.
 * Authored before the relation additions; all eight Java cases UNRUN by their author.
 */
class TestCdR12aPlacementRing {
    @Test void explicitSavedBankWidthIsCheckedBeforeTheActualTerrainSource() {
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets((CdR12aTerrainAssets) null, null));
        for (int size : new int[]{0, 0x2FD, 0x2FF}) {
            var bank = CdAssetBytes.copyOf(new byte[size]);
            assertThrows(IllegalArgumentException.class, () -> CdObjectPlacementRuntime.fromAssets((CdR12aTerrainAssets) null, bank));
        }
        assertThrows(NullPointerException.class, () -> CdObjectPlacementRuntime.fromAssets((CdR12aTerrainAssets) null, bank()));
    }

    @Test void actualTerrainFactoryRetainsItsOwnLayoutAndEveryCarriedBankByteWithoutRunningInit() throws Exception {
        var a = retail().act2(); var carried = bank(); var p = CdObjectPlacementRuntime.fromAssets(a, carried);
        assertEquals(CdReleaseVariant.USA, a.identity().variant()); assertEquals(CdStageKey.parse("R12A"), a.identity().stage());
        assertEquals(CdNativeFile.R12A, a.identity().file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain());
        assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7", a.identity().mmdSha256());
        assertSame(a.objects().placements(), p.placements()); assertEquals(488, p.placements().records().size());
        assertTrue(p.sourceIdentity().isEmpty(), "The historical accessor still describes A alone");
        assertTrue(p.hasAuthenticatedSource()); assertTrue(p.sameAct2Source(a.objects()));
        assertArrayEquals(carried.copy(), p.capture().savedFlags().copy());
        assertEquals(new CdObjectPlacementRuntime.Status(0, 0, 0, 0, 0xC7, 0x9E, -1, -1), p.status());
        for (var state : p.pool().capture().slots()) assertArrayEquals(new byte[64], state.copyBytes());
        p.bsetSavedFlag(1, 2, CdObjectPlacementRuntime.SavedBit.LOADED);
        assertArrayEquals(bank().copy(), carried.copy(), "The immutable carried value is not the runtime bank");
    }

    @Test void cameraZeroSpawnsTheActualSixPresentRowsWithoutExecutingOrExpandingAnyBody() throws Exception {
        var p = placement(); var before = p.capture(); p.spawnObjects(0, 1);
        // Original R12A common rows0,1,2,3,5,6; row4 is a wrong-period successful skip.
        int[][] literal = {{9, 397, 0x0A, 6}, {36, 395, 0x10, 0x11}, {208, 312, 0x10, 0x12},
                {443, 171, 0x24, 0}, {576, 128, 0x24, 0}, {576, 395, 0x10, 0x52}};
        for (int i = 0; i < 96; i++) {
            var expected = before.pool().slots().get(i);
            if (i < literal.length) {
                var row = literal[i]; expected = expected.moveWord(X, row[0]).moveWord(Y, row[1])
                        .moveByte(ID, row[2]).moveByte(SUBTYPE, row[3]);
                if (i == 1) expected = expected.moveByte(SAVED_FLAGS_ID, 1);
                if (i == 2) expected = expected.moveByte(SAVED_FLAGS_ID, 2);
                if (i == 5) expected = expected.moveByte(SAVED_FLAGS_ID, 3);
            }
            assertEquals(expected, p.pool().state(32 + i), "all64 bytes of whole " + (32 + i));
        }
        assertEquals(new CdObjectPlacementRuntime.Status(2, 0, 7, 0, 4, 1, 0, 0), p.status());
        byte[] expectedBank = bank().copy(); expectedBank[0] = 4; expectedBank[1] = 1;
        for (int id : new int[]{1, 2, 3}) expectedBank[2 + 3 * id + 1] |= (byte) 0x80;
        assertArrayEquals(expectedBank, p.capture().savedFlags().copy());
        assertEquals(6, p.pool().capture().slots().stream().filter(s -> s.id() != 0).count());
        assertThrows(IndexOutOfBoundsException.class, () -> p.pool().state(31));
        assertThrows(IndexOutOfBoundsException.class, () -> p.pool().state(128));
    }

    @Test void actualRingFactoryUsesAct2OperandsAndFiniteMappingsWithoutBorrowingAnArtBank() throws Exception {
        var a = retail().act2(); var p = placement(); var before = p.capture(); var ring = CdRing.fromAssets(p, a.objects());
        assertCut(before, p); assertTrue(ring.sourceIdentity().isEmpty()); assertTrue(ring.artBinding().isEmpty());
        assertEquals(0x209F22, ring.sourceData().mappingAddress()); assertEquals(0xA7AE, ring.sourceData().baseTileWord());
        assertEquals(0x209ABE, a.profile().range(RING_DELTAS).nativeAddress()); assertEquals(0x209F1A, a.profile().range(ANI_RING).nativeAddress());
        assertSame(a.objects().rings(), ring.sourceData().visuals()); assertEquals(TestCdRing.data().deltas(), ring.sourceData().deltas());
        assertEquals("cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a",
                sha256(ring.sourceData().visuals().mappings().copy()));
        assertEquals("4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a",
                sha256(ring.sourceData().visuals().animationTable().copy()));
        assertEquals(9, ring.sourceData().visuals().mappingFrames().size());
        assertTrue(ring.sourceData().visuals().mappingFrames().get(8).pieces().isEmpty());
    }

    @Test void factoriesRejectConstructedAndCrossSourcePlacementsBeforeAnyWrite() throws Exception {
        var f = retail(); var p = placement(); var a = CdObjectPlacementRuntime.fromAssets(f.act1());
        var b = CdObjectPlacementRuntime.fromAssets(f.past(), bank());
        var raw = new CdObjectPlacementRuntime(f.act2().objects().placements());
        for (var other : List.of(a, b, raw)) {
            var before = other.capture(); assertFalse(other.sameAct2Source(f.act2().objects()));
            assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(other, f.act2().objects())); assertCut(before, other);
        }
        var before = p.capture();
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(p, f.act1().openingObjects()));
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(p, f.past()));
        assertThrows(IllegalArgumentException.class, () -> new CdRing(p, TestCdRing.data()));
        assertThrows(NullPointerException.class, () -> CdRing.fromAssets(p, (CdR12aObjectAssets) null));
        assertThrows(NullPointerException.class, () -> CdRing.fromAssets(null, f.act2().objects()));
        assertEquals(f.act2().identity(), f.independentAct2().identity());
        assertNotSame(f.act2().objects().placements(), f.independentAct2().objects().placements());
        assertFalse(p.sameAct2Source(f.independentAct2().objects()), "An equal identity cannot substitute another retained layout view");
        assertThrows(IllegalArgumentException.class, () -> CdRing.fromAssets(p, f.independentAct2().objects()));
        assertCut(before, p);
    }

    @Test void onlyTheBoundAct2SourceAdmitsAct2InputsAndEveryRejectedDomainKeepsTheFullCut() throws Exception {
        var p = placement(); var ring = CdRing.fromAssets(p, retail().act2().objects()); var ports = new TestCdRing.Ports(p);
        var before = p.capture();
        for (var input : List.of(TestCdRing.input(), input(CdReleaseVariant.USA, "R11B", 0, 1))) {
            assertThrows(IllegalArgumentException.class, () -> ring.executeSelected(32, input, ports, ports)); assertCut(before, p);
        }
        for (var input : List.of(input(CdReleaseVariant.JAPAN, "R12A", 0, 1), input(CdReleaseVariant.USA, "R12B", 0, 1),
                input(CdReleaseVariant.USA, "R12A", 6, 1), input(CdReleaseVariant.USA, "R12A", 0, 3))) {
            assertThrows(UnsupportedOperationException.class, () -> ring.executeSelected(32, input, ports, ports)); assertCut(before, p);
        }
        assertTrue(ports.events.isEmpty());
        var raw = new CdObjectPlacementRuntime(retail().act2().objects().placements()); var rawPorts = new TestCdRing.Ports(raw);
        var rawBefore = raw.capture(); var rawRing = new CdRing(raw, TestCdRing.data());
        assertThrows(UnsupportedOperationException.class, () -> rawRing.executeSelected(32, input(), rawPorts, rawPorts));
        assertCut(rawBefore, raw); assertTrue(rawPorts.events.isEmpty());
    }

    @Test void theSameRingAlgorithmExpandsActualParentsAndRunsPhysicalLaterChildrenWithoutTouchingOtherBodies() throws Exception {
        var p = placement(); p.spawnObjects(0, 1); var before = p.capture();
        var ring = CdRing.fromAssets(p, retail().act2().objects()); var ports = new TestCdRing.Ports(p); var globals = ports.value;
        for (int whole = 32; whole < 128; whole++) if (p.pool().state(whole).id() == 0x10) ring.executeSelected(whole, input(), ports, ports);
        assertEquals(List.of(33, 34, 37, 38, 39, 40, 41, 42), ports.drawn);
        int[][] literal = {{33, 36, 395, 36, 1, 1}, {34, 208, 312, 208, 2, 1}, {37, 576, 395, 576, 3, 1},
                {38, 60, 395, 36, 1, 2}, {39, 232, 312, 208, 2, 2}, {40, 256, 312, 208, 2, 3},
                {41, 576, 427, 576, 3, 2}, {42, 576, 459, 576, 3, 3}};
        for (var row : literal) {
            var expected = before.pool().slots().get(row[0] - 32).moveByte(ID, 0x10).moveByte(ROUTINE, 2)
                    .moveWord(X, row[1]).moveWord(Y, row[2]).moveWord(RING_ANCHOR_X, row[3])
                    .moveByte(SAVED_FLAGS_ID, row[4]).moveByte(RING_MEMBER, row[5]).moveLong(MAPPINGS, 0x209F22)
                    .moveWord(TILE, 0xA7AE).moveByte(PRIORITY, 2).moveByte(SPRITE_FLAGS, 4).moveByte(COLLISION_TYPE, 0x47)
                    .moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8).moveByte(MAP_FRAME, 3);
            assertEquals(expected, p.pool().state(row[0]), "all64 bytes of Ring whole " + row[0]);
        }
        for (int whole : new int[]{32, 35, 36}) assertEquals(before.pool().slots().get(whole - 32), p.pool().state(whole));
        for (int whole = 43; whole < 128; whole++) assertEquals(before.pool().slots().get(whole - 32), p.pool().state(whole));
        byte[] expectedBank = before.savedFlags().copy(); for (int id : new int[]{1, 2, 3}) expectedBank[2 + 3 * id + 1] &= 0x7F;
        assertArrayEquals(expectedBank, p.capture().savedFlags().copy()); assertEquals(globals, ports.value); assertTrue(ports.sounds.isEmpty());
    }

    @Test void healthyRestoreRetainsActualAct2SourceAndCannotAdoptAnotherRuntimeCapture() throws Exception {
        var p = placement(); var empty = p.capture(); p.spawnObjects(0, 1); var spawned = p.capture();
        p.restore(empty); assertCut(empty, p); p.spawnObjects(0, 1); assertCut(spawned, p);
        var other = placement(); var cut = p.capture();
        assertThrows(IllegalArgumentException.class, () -> p.restore(other.capture())); assertCut(cut, p);
        assertTrue(p.sameAct2Source(retail().act2().objects()));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static CdRing.Inputs input() { return input(CdReleaseVariant.USA, "R12A", 0, 1); }
    private static CdRing.Inputs input(CdReleaseVariant variant, String stage, int zone, int period) {
        return new CdRing.Inputs(variant, CdStageKey.parse(stage), zone, 0, period, 0);
    }
    private static CdAssetBytes bank() {
        byte[] bytes = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]; bytes[0] = (byte) 0xC7; bytes[1] = (byte) 0x9E;
        bytes[2 + 3 * 1] = 0x40; bytes[2 + 3 * 1 + 2] = 0x21; bytes[765] = 0x56;
        return CdAssetBytes.copyOf(bytes);
    }
    private static CdObjectPlacementRuntime placement() throws Exception { return CdObjectPlacementRuntime.fromAssets(retail().act2(), bank()); }
    private static void assertCut(CdObjectPlacementRuntime.Snapshot before, CdObjectPlacementRuntime current) {
        assertEquals(before.status(), current.status()); assertEquals(before.pool().slots(), current.pool().capture().slots());
        assertArrayEquals(before.savedFlags().copy(), current.capture().savedFlags().copy());
    }
    private record Retail(CdR12aTerrainAssets act2, CdR11aAssets act1, CdR11bAssets past, CdR12aTerrainAssets independentAct2) { }
    private static String retainedRoot;
    private static Retail retained;
    private static synchronized Retail retail() throws Exception {
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure the original USA CD media root"); assertFalse(root.isBlank());
        if (retained != null && root.equals(retainedRoot)) return retained;
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Retail(CdR12aTerrainAssets.fromPrograms(source),
                    CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A")),
                    CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source)), CdR12aTerrainAssets.fromPrograms(source));
            retainedRoot = root; return retained;
        }
    }
}
