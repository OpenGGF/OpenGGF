package com.openggf.game.sonic2.debug;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the native S2 debug-placement catalog and the engine's current readiness boundary.
 *
 * <p>This deliberately decodes the user-supplied ROM in test code. It is evidence for a future
 * production capability, not a dormant production catalog or a gameplay data source.</p>
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2DebugPlacementRomContract {
    private static final String REV01_SHA1 = "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9";
    private static final int DEBUG_OBJECT_LISTS = 0x41D0C;

    private static final int[] EXPECTED_LIST_OFFSETS = {
            0x0034, 0x0022, 0x0022, 0x0022, 0x00CE, 0x00CE,
            0x01E0, 0x02E2, 0x03DC, 0x0022, 0x03DC, 0x04E6,
            0x05A8, 0x066A, 0x0022, 0x072C, 0x0816
    };

    private static final int[] EXPECTED_ZONE_ROW_COUNTS = {
            19, 2, 2, 2, 34, 34, 32, 31, 33, 2, 33, 24, 24, 24, 2, 29, 13
    };

    @Test
    void rev01ZoneTableAndListCardinalityMatchTheShippedCatalog() throws Exception {
        Contract contract = loadContract();

        assertEquals(REV01_SHA1, contract.sha1(),
                "Native placement evidence is pinned to Sonic 2 World REV01");
        assertArrayEquals(EXPECTED_LIST_OFFSETS, contract.zoneListOffsets());
        assertArrayEquals(EXPECTED_ZONE_ROW_COUNTS, contract.zoneRowCounts());
        assertEquals(17, contract.zoneListOffsets().length);
        assertEquals(340, Arrays.stream(contract.zoneRowCounts()).sum(),
                "Count rows after expanding aliases through all 17 zone-table slots");

        Set<Integer> distinctOffsets = new LinkedHashSet<>();
        Arrays.stream(contract.zoneListOffsets()).forEach(distinctOffsets::add);
        assertEquals(11, distinctOffsets.size());
        assertEquals(265, distinctOffsets.stream()
                        .mapToInt(offset -> contract.rowsByOffset(offset).size())
                        .sum(),
                "Count each shared list definition once");
        assertEquals(contract.zoneListOffsets()[8], contract.zoneListOffsets()[10],
                "HPZ and OOZ share one list pointer in the shipped table");
    }

    @Test
    void rev01RowsRetainShippedFixBugsZeroVariantsAndValidMappings() throws Exception {
        Contract contract = loadContract();

        List<DebugRow> ehz = contract.rowsByOffset(0x0034);
        DebugRow waterfallSubtypeZero = ehz.stream()
                .filter(row -> row.objectId() == 0x49 && row.subtype() == 0)
                .findFirst()
                .orElseThrow();
        assertEquals(0, waterfallSubtypeZero.mappingFrame(),
                "fixBugs=0 keeps the blank EHZ waterfall preview frame");

        List<DebugRow> cnz = contract.rowsByOffset(0x05A8);
        DebugRow bombPrize = cnz.stream()
                .filter(row -> row.objectId() == 0xD3)
                .findFirst()
                .orElseThrow();
        assertEquals(0x2B8D4, bombPrize.mappingsAddress(),
                "fixBugs=0 retains the parent-dependent CNZ Bomb Prize row");
        assertEquals(0, bombPrize.subtype());
        assertEquals(0, bombPrize.mappingFrame());

        for (int offset : new LinkedHashSet<>(Arrays.stream(contract.zoneListOffsets())
                .boxed().toList())) {
            for (DebugRow row : contract.rowsByOffset(offset)) {
                assertTrue(row.mappingsAddress() > 0
                                && row.mappingsAddress() < contract.romSize(),
                        () -> String.format("Obj%02X mapping pointer $%06X must be in the REV01 ROM",
                                row.objectId(), row.mappingsAddress()));
            }
        }
    }

    @Test
    void registryGapIsExactAndTheModuleKeepsPlacementUnavailable() throws Exception {
        Contract contract = loadContract();
        Set<Integer> uniqueIds = new TreeSet<>();
        Set<Integer> distinctOffsets = new LinkedHashSet<>();
        Arrays.stream(contract.zoneListOffsets()).forEach(distinctOffsets::add);
        distinctOffsets.forEach(offset -> contract.rowsByOffset(offset).stream()
                .map(DebugRow::objectId)
                .forEach(uniqueIds::add));

        assertEquals(117, uniqueIds.size());

        Sonic2ObjectRegistry registry = new Sonic2ObjectRegistry();
        Set<Integer> missingFactories = new TreeSet<>();
        uniqueIds.stream()
                .filter(id -> !registry.hasRegisteredFactory(id))
                .forEach(missingFactories::add);

        assertEquals(Set.of(0x25, 0x46, 0x73, 0xD3), missingFactories,
                "Ring uses RingManager; OOZ Ball, Rotating Rings, and Bomb Prize lack placement-safe factories");
        assertEquals(113, uniqueIds.size() - missingFactories.size());

        assertFalse(new Sonic2GameModule().getDebugModeProvider().hasLevelDebug(),
                "Native level placement stays unavailable until preview art, dynamic ring/object lifecycle, "
                        + "global gates, rewind, and dedicated REV01 trace evidence are complete");
    }

    private static Contract loadContract() throws Exception {
        byte[] bytes = GameServices.rom().getRom().readAllBytes();
        String sha1 = HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        RomByteReader reader = new RomByteReader(bytes);

        int[] offsets = new int[EXPECTED_LIST_OFFSETS.length];
        int[] counts = new int[EXPECTED_LIST_OFFSETS.length];
        List<DecodedList> distinctLists = new ArrayList<>();
        Set<Integer> seenOffsets = new LinkedHashSet<>();

        for (int zone = 0; zone < offsets.length; zone++) {
            int offset = reader.readU16BE(DEBUG_OBJECT_LISTS + zone * 2);
            offsets[zone] = offset;
            int listAddress = DEBUG_OBJECT_LISTS + offset;
            int count = reader.readU16BE(listAddress);
            counts[zone] = count;
            if (seenOffsets.add(offset)) {
                int listEnd = listAddress + 2 + count * 8;
                assertTrue(listEnd <= reader.size(),
                        () -> String.format("Debug list at relative offset $%04X exceeds the ROM", offset));

                List<DebugRow> rows = new ArrayList<>(count);
                for (int rowIndex = 0; rowIndex < count; rowIndex++) {
                    int rowAddress = listAddress + 2 + rowIndex * 8;
                    int packed = reader.readU32BE(rowAddress);
                    rows.add(new DebugRow(
                            packed >>> 24,
                            packed & 0x00FF_FFFF,
                            reader.readU8(rowAddress + 4),
                            reader.readU8(rowAddress + 5),
                            reader.readU16BE(rowAddress + 6)));
                }
                distinctLists.add(new DecodedList(offset, List.copyOf(rows)));
            }
        }

        return new Contract(sha1, reader.size(), offsets, counts, List.copyOf(distinctLists));
    }

    private record DebugRow(
            int objectId,
            int mappingsAddress,
            int subtype,
            int mappingFrame,
            int artTile) {
    }

    private record DecodedList(int offset, List<DebugRow> rows) {
    }

    private record Contract(
            String sha1,
            int romSize,
            int[] zoneListOffsets,
            int[] zoneRowCounts,
            List<DecodedList> distinctLists) {

        private List<DebugRow> rowsByOffset(int offset) {
            return distinctLists.stream()
                    .filter(list -> list.offset() == offset)
                    .findFirst()
                    .orElseThrow()
                    .rows();
        }
    }
}
