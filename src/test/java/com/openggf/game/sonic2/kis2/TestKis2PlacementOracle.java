package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.sonic2.Sonic2ObjectPlacement;
import com.openggf.game.sonic2.ZoneAct;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Walks {@code Off_Objects_KiS2} ({@code 0xDF370}) on the local S&amp;K half and
 * asserts each resolvable act against the record counts recorded in
 * docs/kis2/BRANCH_DIFFS.md §Object placements. Skips without both ROMs.
 */
class TestKis2PlacementOracle {

    /** Catalogue counts: entry index → (KiS2 records, stock records). */
    private static final Map<Integer, int[]> CATALOGUE = Map.ofEntries(
            Map.entry(0, new int[]{157, 135}),   // EHZ1
            Map.entry(1, new int[]{175, 158}),   // EHZ2
            Map.entry(8, new int[]{193, 193}),   // MTZ1 (same count, content differs)
            Map.entry(9, new int[]{224, 220}),   // MTZ2
            Map.entry(10, new int[]{273, 270}),  // MTZ3
            Map.entry(11, new int[]{273, 270}),  // MTZ3 (zone 5 act 1 duplicate)
            Map.entry(12, new int[]{170, 157}),  // WFZ1
            Map.entry(13, new int[]{0, 0}),      // WFZ2 stub
            Map.entry(14, new int[]{148, 144}),  // HTZ1
            Map.entry(15, new int[]{285, 259}),  // HTZ2
            Map.entry(16, new int[]{45, 45}),    // HPZ1 (S2 cart)
            Map.entry(17, new int[]{0, 0}),      // HPZ2 (S2 cart)
            Map.entry(20, new int[]{204, 189}),  // OOZ1
            Map.entry(21, new int[]{202, 190}),  // OOZ2
            Map.entry(22, new int[]{131, 130}),  // MCZ1
            Map.entry(23, new int[]{152, 148}),  // MCZ2
            Map.entry(26, new int[]{189, 153}),  // CPZ1
            Map.entry(27, new int[]{249, 202}),  // CPZ2
            Map.entry(28, new int[]{5, 5}),      // DEZ1 (S2 cart)
            Map.entry(29, new int[]{0, 0}),      // DEZ2 (S2 cart)
            Map.entry(30, new int[]{200, 182}),  // ARZ1
            Map.entry(31, new int[]{303, 222}),  // ARZ2
            Map.entry(32, new int[]{60, 60}),    // SCZ1 (S2 cart)
            Map.entry(33, new int[]{0, 0}));     // SCZ2 (S2 cart)

    private static final int[] CHIP_ENTRIES = {24, 25}; // CNZ1, CNZ2
    private static final int[] NULL_ENTRIES = {2, 3, 4, 5, 6, 7, 18, 19};

    private static RomByteReader sk;
    private static RomByteReader s2;
    private static Kis2ObjectPlacement placement;
    private static Sonic2ObjectPlacement stock;

    @BeforeAll
    static void loadRoms() throws IOException {
        File s2File = RomTestUtils.ensureSonic2RomAvailable();
        File s3kFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s2File != null && s3kFile != null, "KiS2 placement oracle needs the S2 and S3K ROMs");
        s2 = RomByteReader.fromBytes(Files.readAllBytes(s2File.toPath()));
        sk = LogicalRomResolver.windowSkFromCombined(Files.readAllBytes(s3kFile.toPath()));
        placement = new Kis2ObjectPlacement(sk, s2, LockOnAddressSpace.tierOne(sk, s2));
        stock = new Sonic2ObjectPlacement(s2);
    }

    @Test
    void tableEntriesResolveToTheWindowsTheCatalogueRecords() {
        LockOnAddressSpace space = LockOnAddressSpace.tierOne(sk, s2);
        for (int entry = 0; entry < Kis2Constants.OFF_OBJECTS_KIS2_ENTRIES; entry++) {
            long pointer = Integer.toUnsignedLong(sk.readU32BE(Kis2Constants.OFF_OBJECTS_KIS2 + entry * 4));
            Optional<LockOnAddressSpace.Read> read = space.resolve(pointer);
            if (isChip(entry)) {
                assertEquals(LockOnAddressSpace.Window.CHIP, LockOnAddressSpace.windowOf(pointer), "entry " + entry);
                assertTrue(read.isEmpty(), "chip entry " + entry + " must be unresolvable in tier one");
            } else {
                assertTrue(read.isPresent(), "entry " + entry + " at 0x" + Long.toHexString(pointer));
            }
        }
        // Boundary word after the table, as S2K_Sprite_Lists declares (dc.w $FFFF, 0, 0).
        assertEquals(0xFFFF, sk.readU16BE(Kis2Constants.OFF_OBJECTS_KIS2
                + Kis2Constants.OFF_OBJECTS_KIS2_ENTRIES * 4));
    }

    @Test
    void resolvableActsMatchTheCatalogueCountsAndDifferFromStockWhereRecorded() {
        LockOnAddressSpace space = LockOnAddressSpace.tierOne(sk, s2);
        for (Map.Entry<Integer, int[]> expected : CATALOGUE.entrySet()) {
            int entry = expected.getKey();
            // Raw table entries: the engine's load() additionally clamps
            // single-act zones (WFZ, DEZ, SCZ) to act 0 like stock Sonic 2.
            long pointer = Integer.toUnsignedLong(sk.readU32BE(Kis2Constants.OFF_OBJECTS_KIS2 + entry * 4));
            LockOnAddressSpace.Read read = space.resolve(pointer).orElseThrow();
            List<ObjectSpawn> kis2 = CommonPlacementParser.parseObjectRecords(read.reader(), read.localAddress());
            List<ObjectSpawn> stockSpawns = CommonPlacementParser.parseObjectRecords(
                    s2, s2.readPointer16(Sonic2ObjectPlacement.OFF_OBJECTS_REV01, entry));
            assertEquals(expected.getValue()[0], kis2.size(), "KiS2 count for entry " + entry);
            assertEquals(expected.getValue()[1], stockSpawns.size(), "stock count for entry " + entry);
            if (expected.getValue()[0] != expected.getValue()[1]) {
                assertNotEquals(stockSpawns, kis2, "entry " + entry + " must differ from stock");
            }
        }
    }

    @Test
    void loadedActsUseTheKis2CountsThroughTheStockZoneActIndexing() {
        assertEquals(157, placement.load(new ZoneAct(0, 0)).size());
        assertEquals(175, placement.load(new ZoneAct(0, 1)).size());
        assertEquals(273, placement.load(new ZoneAct(5, 0)).size());
        assertEquals(170, placement.load(new ZoneAct(6, 0)).size());
        assertEquals(303, placement.load(new ZoneAct(15, 1)).size());
    }

    @Test
    void metropolisAct1KeepsTheStockCountButNotTheStockContent() {
        List<ObjectSpawn> kis2 = placement.load(new ZoneAct(4, 0));
        List<ObjectSpawn> stockSpawns = stock.load(new ZoneAct(4, 0));
        assertEquals(stockSpawns.size(), kis2.size());
        assertNotEquals(stockSpawns, kis2, "MTZ1 was rewritten in place (BRANCH_DIFFS.md)");
    }

    @Test
    void sonic2CartPointersReproduceTheStockLayoutsExactly() {
        for (int entry : new int[]{16, 17, 28, 29, 32, 33}) {
            ZoneAct zoneAct = new ZoneAct(entry / 2, entry % 2);
            assertEquals(stock.load(zoneAct), placement.load(zoneAct), "S2-cart entry " + entry);
        }
    }

    @Test
    void chipPointersFallBackToStockCasinoNightLayouts() {
        for (int entry : CHIP_ENTRIES) {
            ZoneAct zoneAct = new ZoneAct(entry / 2, entry % 2);
            assertFalse(placement.isResolvable(zoneAct), "CNZ entry " + entry + " lives on the chip");
            assertEquals(stock.load(zoneAct), placement.load(zoneAct), "CNZ entry " + entry);
        }
    }

    /**
     * Tier two: with the user-supplied lock-on dump the chip pointers resolve
     * and CNZ uses the KiS2 layouts (BRANCH_DIFFS.md: 292 and 257 records).
     */
    @Test
    void withTheLockOnDumpCasinoNightResolvesFromTheChipWithTheCatalogueCounts() {
        RomByteReader dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "no user-supplied S&K + Sonic 2 lock-on dump; skipping tier two");
        Kis2ObjectPlacement tierTwo = new Kis2ObjectPlacement(sk, s2, LockOnAddressSpace.tierTwo(sk, s2, dump));
        int[][] expected = {{24, 292, 286}, {25, 257, 254}};
        for (int[] row : expected) {
            ZoneAct zoneAct = new ZoneAct(row[0] / 2, row[0] % 2);
            assertTrue(tierTwo.isResolvable(zoneAct), "CNZ entry " + row[0] + " resolves through the chip");
            List<ObjectSpawn> kis2 = tierTwo.load(zoneAct);
            List<ObjectSpawn> stockSpawns = stock.load(zoneAct);
            assertEquals(row[1], kis2.size(), "KiS2 count for entry " + row[0]);
            assertEquals(row[2], stockSpawns.size(), "stock count for entry " + row[0]);
            assertNotEquals(stockSpawns, kis2, "CNZ entry " + row[0] + " must differ from stock");
        }
        assertEquals(Kis2Constants.OBJECTS_CNZ_1, tierTwo.pointerFor(new ZoneAct(12, 0)));
        assertEquals(Kis2Constants.OBJECTS_CNZ_2, tierTwo.pointerFor(new ZoneAct(12, 1)));
    }

    @Test
    void theDumpsSkAndS2WindowsReproduceEveryTierOneLayout() {
        RomByteReader dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "no user-supplied S&K + Sonic 2 lock-on dump; skipping tier two");
        RomByteReader dumpSk = dump.window(Kis2Constants.SK_WINDOW_START, Kis2Constants.SK_WINDOW_END);
        RomByteReader dumpS2 = dump.window(Kis2Constants.S2_WINDOW_START,
                Kis2Constants.S2_WINDOW_END - Kis2Constants.S2_WINDOW_START);
        Kis2ObjectPlacement fromDump = new Kis2ObjectPlacement(dumpSk, dumpS2,
                LockOnAddressSpace.tierTwo(dumpSk, dumpS2, dump));
        for (int entry : CATALOGUE.keySet()) {
            ZoneAct zoneAct = new ZoneAct(entry / 2, entry % 2);
            assertEquals(placement.load(zoneAct), fromDump.load(zoneAct), "entry " + entry);
        }
    }

    @Test
    void unusedZoneSlotsAreEmpty() {
        for (int entry : NULL_ENTRIES) {
            assertEquals(0xE40AE, placement.pointerFor(new ZoneAct(entry / 2, entry % 2)), "entry " + entry);
        }
        assertTrue(placement.load(new ZoneAct(1, 0)).isEmpty());
    }

    @Test
    void singleActZonesClampToActZeroLikeStock() {
        assertEquals(placement.load(new ZoneAct(6, 0)), placement.load(new ZoneAct(6, 1)));
        assertEquals(placement.load(new ZoneAct(16, 0)), placement.load(new ZoneAct(16, 1)));
    }

    private static boolean isChip(int entry) {
        for (int chip : CHIP_ENTRIES) {
            if (chip == entry) {
                return true;
            }
        }
        return false;
    }
}
