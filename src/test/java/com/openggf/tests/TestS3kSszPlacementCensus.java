package com.openggf.tests;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sky Sanctuary placement census: the ROM object and ring lists for {@code $A00} and {@code $A01}
 * as the engine decodes them, pinned against the
 * <a href="../../../../../../../docs/architecture/research/s3k-zones/ssz-object-inventory.md">SSZ
 * object inventory</a>.
 *
 * <p>Sources: {@code SpriteLocPtrs} / {@code RingLocPtrs} (pointer index {@code zone * 2 + act},
 * zone {@code $0A}) resolve to {@code SSZ1_Sprites $1F90EE}, {@code SSZ2_Sprites $1F95F2},
 * {@code SSZ1_Rings $1F9616} and {@code SSZ2_Rings $1F98E8} in the locked-on ROM
 * (SHA-1 {@code CFBF98C3…}). Record format is {@code sub_1BA0C}/{@code loc_1BA4A}: X word, Y word
 * ({@code & $FFF} position, bit 13 X-flip, bit 14 Y-flip, bit 15 "load regardless of the Y window"),
 * ID byte, subtype byte. Rings are raw four-byte records walked by {@code Load_Rings}
 * ({@code loc_E8BE}).
 *
 * <p>This is a decode census, not route or behaviour certification. Slice 3 of the
 * <a href="../../../../../../../docs/architecture/plans/2026-09-17-ssz-bring-up.md">SSZ bring-up
 * plan</a> extends it to assert concrete classes once the placeholder families are implemented.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszPlacementCensus {

    private static final int SSZ = Sonic3kZoneIds.ZONE_SSZ;

    /** Act 1 (ID, subtype) -> count, decoded from {@code SSZ1_Sprites}. 213 live records. */
    private static final Map<String, Integer> ACT1_ROWS = actOneRows();

    /** Act 2 (ID, subtype) -> count, decoded from {@code SSZ2_Sprites}. 5 live records. */
    private static final Map<String, Integer> ACT2_ROWS = Map.of(
            "$00:$00", 3,
            "$79:$00", 1,
            "$B2:$00", 1);

    private static Map<String, Integer> actOneRows() {
        Map<String, Integer> rows = new LinkedHashMap<>();
        rows.put("$01:$01", 3);
        rows.put("$01:$03", 8);
        rows.put("$01:$05", 1);
        rows.put("$01:$06", 1);
        rows.put("$01:$07", 1);
        rows.put("$01:$08", 2);
        rows.put("$02:$45", 1);
        rows.put("$07:$01", 4);
        rows.put("$07:$03", 1);
        rows.put("$07:$10", 1);
        rows.put("$07:$12", 3);
        rows.put("$08:$00", 7);
        rows.put("$08:$10", 3);
        rows.put("$08:$11", 1);
        rows.put("$14:$01", 4);
        rows.put("$28:$11", 1);
        rows.put("$28:$30", 1);
        rows.put("$28:$81", 1);
        rows.put("$34:$02", 1);
        rows.put("$34:$03", 1);
        rows.put("$34:$04", 1);
        rows.put("$74:$00", 5);
        rows.put("$75:$00", 5);
        rows.put("$75:$80", 1);
        rows.put("$75:$82", 2);
        rows.put("$76:$00", 3);
        rows.put("$76:$01", 4);
        rows.put("$77:$00", 1);
        rows.put("$79:$00", 5);
        rows.put("$79:$15", 1);
        rows.put("$79:$1E", 1);
        rows.put("$79:$32", 1);
        rows.put("$79:$AA", 1);
        rows.put("$79:$F6", 1);
        rows.put("$7A:$00", 5);
        rows.put("$7B:$00", 31);
        rows.put("$7B:$80", 4);
        rows.put("$7C:$00", 7);
        rows.put("$7C:$80", 1);
        rows.put("$7D:$00", 27);
        rows.put("$7E:$00", 25);
        rows.put("$7F:$00", 8);
        // Obj_EggRobo: 24 subtype rows. off_9186E dispatches on the low nibble:
        // 0 = the scaled fly-by, 2 = the fighter, 4 = the animal releaser
        // (loc_918FC -> loc_915F6 -> loc_917C0), which turns into a fighter after
        // five releases. The high nibble is the _unkFA82 pairing bit index that
        // loc_91570 sets and sub_91914 reads.
        rows.put("$A0:$00", 1);
        rows.put("$A0:$02", 1);
        rows.put("$A0:$04", 3);
        rows.put("$A0:$10", 1);
        rows.put("$A0:$12", 1);
        rows.put("$A0:$20", 1);
        rows.put("$A0:$22", 1);
        rows.put("$A0:$30", 1);
        rows.put("$A0:$32", 1);
        rows.put("$A0:$40", 1);
        rows.put("$A0:$42", 2);
        rows.put("$A0:$50", 1);
        rows.put("$A0:$52", 1);
        rows.put("$A0:$60", 1);
        rows.put("$A0:$62", 1);
        rows.put("$A0:$70", 1);
        rows.put("$A0:$72", 1);
        rows.put("$A0:$80", 1);
        rows.put("$A0:$82", 1);
        rows.put("$A0:$90", 1);
        rows.put("$A0:$92", 1);
        rows.put("$A0:$A0", 1);
        rows.put("$A0:$A2", 1);
        rows.put("$AF:$00", 1);
        return Map.copyOf(rows);
    }

    @Test
    void pointerTableResolvesTheSszLists() throws IOException {
        RomByteReader rom = rom();
        assertEquals(0x1F90EE, rom.readU32BE(Sonic3kConstants.SPRITE_LOC_PTRS_ADDR + (SSZ * 2) * 4),
                "SSZ1_Sprites");
        assertEquals(0x1F95F2, rom.readU32BE(Sonic3kConstants.SPRITE_LOC_PTRS_ADDR + (SSZ * 2 + 1) * 4),
                "SSZ2_Sprites");
        assertEquals(0x1F9616, rom.readU32BE(Sonic3kConstants.RING_LOC_PTRS_ADDR + (SSZ * 2) * 4),
                "SSZ1_Rings");
        assertEquals(0x1F98E8, rom.readU32BE(Sonic3kConstants.RING_LOC_PTRS_ADDR + (SSZ * 2 + 1) * 4),
                "SSZ2_Rings");
    }

    @Test
    void actOneObjectCensusMatchesTheRom() throws IOException {
        List<ObjectSpawn> spawns = new Sonic3kObjectPlacement(rom()).load(SSZ, 0);
        assertEquals(213, spawns.size(), "SSZ1 live object records");
        assertEquals(new TreeMap<>(ACT1_ROWS), new TreeMap<>(census(spawns)), "SSZ1 (ID, subtype) census");
    }

    @Test
    void actTwoObjectCensusMatchesTheRom() throws IOException {
        List<ObjectSpawn> spawns = new Sonic3kObjectPlacement(rom()).load(SSZ, 1);
        assertEquals(5, spawns.size(), "SSZ2 live object records");
        assertEquals(new TreeMap<>(ACT2_ROWS), new TreeMap<>(census(spawns)), "SSZ2 (ID, subtype) census");
    }

    /**
     * No SSZ record sets Y-word bit 15. The bit makes {@code loc_1BA4A} skip the Y-window range
     * test ("load regardless of Y"); the engine decodes it into {@code respawnTracked}, whose
     * Javadoc names it a respawn bit. Nothing in SSZ depends on the difference, so this pins the
     * SSZ side only.
     */
    @Test
    void noSszRecordSetsTheIgnoreYWindowBit() throws IOException {
        for (int act = 0; act <= 1; act++) {
            for (ObjectSpawn spawn : new Sonic3kObjectPlacement(rom()).load(SSZ, act)) {
                assertFalse(spawn.respawnTracked(),
                        "act " + act + " record " + spawn.layoutIndex() + " sets Y bit 15");
            }
        }
    }

    /**
     * Two {@code $7D} cloud records store Y words above {@code $FFF} ({@code $103C}, {@code $104C}).
     * The loader's {@code & $FFF} mask drops bit 12, so they sit at {@code $03C}/{@code $04C}, just
     * past the {@code $1000} vertical wrap seam at the top of the tower.
     */
    @Test
    void theTwoWrapSeamCloudRecordsMaskAcrossTheSeam() throws IOException {
        List<ObjectSpawn> seam = new Sonic3kObjectPlacement(rom()).load(SSZ, 0).stream()
                .filter(spawn -> (spawn.rawYWord() & 0x1000) != 0)
                .toList();
        assertEquals(2, seam.size(), "records with Y-word bit 12 set");
        assertEquals(List.of(0x0C70, 0x0C94), seam.stream().map(ObjectSpawn::x).toList());
        assertEquals(List.of(0x103C, 0x104C), seam.stream().map(ObjectSpawn::rawYWord).toList());
        assertEquals(List.of(0x03C, 0x04C), seam.stream().map(ObjectSpawn::y).toList());
        assertEquals(List.of(0x7D, 0x7D), seam.stream().map(ObjectSpawn::objectId).toList());
    }

    /**
     * Ring lists. The ROM's {@code SSZ1_Rings} holds 180 four-byte records, the first of which is
     * {@code (0,0)}, and {@code SSZ2_Rings} holds only that record. {@code loc_E8BE} starts its
     * scan at {@code max(Camera_X - 8, 1)} and advances while the cursor X is below it, so the
     * {@code (0,0)} record is always stepped over: the ROM never makes it collectible. The
     * collectible totals are therefore 179 and 0.
     *
     * <p>The expectations below are decoded from the ROM bytes here, independently of
     * {@code Sonic3kRingPlacement}, and the loader is then compared against them. The loader is
     * allowed to still carry the sentinel while the shared fix is in flight: the LRZ campaign's
     * {@code 3418eba6e} drops it from {@code Sonic3kRingPlacement} for every S3K act and reaches
     * this branch at merge time. Until then the extra record is asserted as exactly that — a
     * sentinel the ROM skips — rather than as a ring, so the numbers here stay correct on both
     * sides of the merge. See the plan's cross-campaign section.
     */
    @Test
    void ringRecordsMatchTheRomIncludingTheLeadingZeroRecord() throws IOException {
        List<int[]> act1Records = ringRecords(0x1F9616);
        List<int[]> act2Records = ringRecords(0x1F98E8);
        assertEquals(180, act1Records.size(), "SSZ1_Rings records in the ROM");
        assertEquals(0, act1Records.get(0)[0]);
        assertEquals(0, act1Records.get(0)[1]);
        assertEquals(1, act2Records.size(), "SSZ2_Rings records in the ROM");
        assertEquals(0, act2Records.get(0)[0]);
        assertEquals(0, act2Records.get(0)[1]);

        long act1Collectible = act1Records.stream().filter(r -> r[0] != 0 || r[1] != 0).count();
        long act2Collectible = act2Records.stream().filter(r -> r[0] != 0 || r[1] != 0).count();
        assertEquals(179, act1Collectible, "rings loc_E8BE can reach in SSZ1");
        assertEquals(0, act2Collectible, "rings loc_E8BE can reach in SSZ2");

        Sonic3kRingPlacement placement = new Sonic3kRingPlacement(rom());
        assertRingsMatchRom(placement.load(SSZ, 0), act1Collectible, "SSZ1");
        assertRingsMatchRom(placement.load(SSZ, 1), act2Collectible, "SSZ2");
    }

    /**
     * The loader either already matches the ROM's collectible total, or still carries the single
     * leading {@code (0,0)} sentinel that LRZ {@code 3418eba6e} removes. Anything else is wrong.
     */
    private static void assertRingsMatchRom(List<RingSpawn> loaded, long romCollectible, String act) {
        long positioned = loaded.stream().filter(ring -> ring.x() != 0 || ring.y() != 0).count();
        assertEquals(romCollectible, positioned, act + " positioned rings");
        if (loaded.size() == romCollectible) {
            return;
        }
        assertEquals(romCollectible + 1, loaded.size(),
                act + " ring spawns: expected the ROM's " + romCollectible
                        + ", or that plus the leading (0,0) sentinel pending LRZ 3418eba6e");
        assertEquals(0, loaded.get(0).x(), act + " sentinel X");
        assertEquals(0, loaded.get(0).y(), act + " sentinel Y");
    }

    /** Four-byte {@code (X, Y)} records up to the {@code $FFFF} terminator. */
    private static List<int[]> ringRecords(int address) throws IOException {
        RomByteReader reader = rom();
        List<int[]> records = new java.util.ArrayList<>();
        for (int offset = 0; ; offset += 4) {
            int x = reader.readU16BE(address + offset);
            if (x == 0xFFFF) {
                return records;
            }
            records.add(new int[] {x, reader.readU16BE(address + offset + 2)});
        }
    }

    /**
     * Every placed SSZ ID resolves through the SKL (SK Set 2) pointer table, not the S3KL names the
     * same numeric IDs carry for zones 0-6. Placeholder families still resolve to a name; the
     * classification of which are placeholders is the slice-0 baseline recorded in the inventory.
     */
    @Test
    void everyPlacedIdResolvesToItsSklName() throws IOException {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(0x00, "Ring");
        expected.put(0x01, "Monitor");
        expected.put(0x02, "PathSwap");
        expected.put(0x07, "Spring");
        expected.put(0x08, "Spikes");
        expected.put(0x14, "Updraft");
        expected.put(0x28, "InvisibleBlock");
        expected.put(0x34, "StarPost");
        expected.put(0x74, "SSZRetractingSpring");
        expected.put(0x75, "SSZSwingingCarrier");
        expected.put(0x76, "SSZRotatingPlatform");
        expected.put(0x77, "SSZCutsceneBridge");
        expected.put(0x79, "SSZHPZTeleporter");
        expected.put(0x7A, "SSZElevatorBar");
        expected.put(0x7B, "SSZCollapsingBridgeDiagonal");
        expected.put(0x7C, "SSZCollapsingBridge");
        expected.put(0x7D, "SSZBouncyCloud");
        expected.put(0x7E, "SSZCollapsingColumn");
        expected.put(0x7F, "SSZFloatingPlatform");
        expected.put(0xA0, "EggRobo");
        expected.put(0xAF, "SSZCutsceneButton");
        expected.put(0xB2, "KnuxFinalBossCrane");

        Map<Integer, String> actual = new LinkedHashMap<>();
        for (int id : expected.keySet()) {
            actual.put(id, registry.getPrimaryName(id, S3kZoneSet.SKL));
        }
        assertEquals(expected, actual, "SKL names for placed SSZ IDs");

        for (int act = 0; act <= 1; act++) {
            for (ObjectSpawn spawn : new Sonic3kObjectPlacement(rom()).load(SSZ, act)) {
                assertTrue(expected.containsKey(spawn.objectId()),
                        () -> String.format("placed ID $%02X is not in the census", spawn.objectId()));
            }
        }
    }

    private static Map<String, Integer> census(List<ObjectSpawn> spawns) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        for (ObjectSpawn spawn : spawns) {
            rows.merge(String.format("$%02X:$%02X", spawn.objectId(), spawn.subtype()), 1, Integer::sum);
        }
        return rows;
    }

    private static RomByteReader rom() throws IOException {
        return RomByteReader.fromRom(RomManager.getInstance().getRom());
    }
}
