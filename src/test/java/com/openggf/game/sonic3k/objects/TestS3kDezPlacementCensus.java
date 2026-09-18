package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sonic 3 &amp; Knuckles Death Egg ({@code $B00}, {@code $B01}, {@code $1700}) placement census.
 * This is <b>not</b> Sonic 2's Death Egg.
 *
 * <p>Two different kinds of expectation live here and must not be confused.
 *
 * <ul>
 *   <li><b>ROM facts.</b> The record counts, the terminator shape and the live spawn totals come
 *       from the ROM's own placement tables ({@code DEZ1_Sprites} {@code $1F98F4},
 *       {@code DEZ2_Sprites} {@code $1FA188}, {@code DEZ3_Sprites} {@code $1FCEE8}) as decoded in
 *       the <a href="../../../../../../../docs/architecture/research/s3k-zones/dez-object-inventory.md">placement
 *       inventory</a>. These never change while the ROM does not.</li>
 *   <li><b>A recorded engine baseline.</b> The concrete/placeholder split is a census of
 *       {@code Sonic3kObjectRegistry} as it stands at the start of the DEZ bring-up. It is a
 *       ratchet, not a correctness oracle: every DEZ slice moves placements from placeholder to
 *       concrete and lowers {@link #PLACEHOLDER_ACT_1}/{@link #PLACEHOLDER_ACT_2} in step, until
 *       slice 4 drives both to zero. It exists so that a slice cannot silently leave a family
 *       behind, and so that an unrelated change to a shared factory shows up here.</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezPlacementCensus {
    /** {@code DEZ1_Sprites}: 366 six-byte records, the last of which is the terminator. */
    private static final int DEZ1_OBJECTS_ADDR = 0x1F98F4;
    private static final int DEZ1_RECORDS = 366;
    private static final int DEZ1_LIVE = 365;

    /** {@code DEZ2_Sprites}: 495 records including the terminator. */
    private static final int DEZ2_OBJECTS_ADDR = 0x1FA188;
    private static final int DEZ2_RECORDS = 495;
    private static final int DEZ2_LIVE = 494;

    /**
     * {@code DEZ3_Sprites}: the terminator alone. Everything in the {@code $1700} arena is
     * spawned by {@code DEZ3_ScreenInit} and the boss, never placed.
     */
    private static final int DEZ3_OBJECTS_ADDR = 0x1FCEE8;
    private static final int DEZ3_RECORDS = 1;

    /**
     * The object IDs whose SKL factory is already a real implementation: those shared with
     * other zones, plus whatever the DEZ slices have landed. {@code $5B}
     * ({@code Obj_DEZGravitySwap}, 11 act 2 placements) is slice 3's first. Every other placed
     * DEZ ID resolves to {@link PlaceholderObjectInstance} today, either because its factory is
     * bound to the S3KL pointer set (a different object under the same number) or because no
     * factory exists at all ({@code $5D}-{@code $61}).
     */
    private static final Set<Integer> CONCRETE_DEZ_IDS = Set.of(
            0x01, 0x02, 0x07, 0x08, 0x28, 0x2F, 0x34, 0x3C, 0x5B, 0x6A, 0x6B, 0x78);

    /** Recorded baseline: placements that still resolve to a placeholder. Slices 3-6 drive these to 0. */
    private static final int PLACEHOLDER_ACT_1 = 225;
    private static final int PLACEHOLDER_ACT_2 = 326;
    private static final int CONCRETE_ACT_1 = 140;
    private static final int CONCRETE_ACT_2 = 168;

    @Test
    void romPlacementTablesDecodeToTheInventoriedSpawnCounts() throws IOException {
        assertTerminatedTable(DEZ1_OBJECTS_ADDR, DEZ1_RECORDS, DEZ1_LIVE, "DEZ1_Sprites");
        assertTerminatedTable(DEZ2_OBJECTS_ADDR, DEZ2_RECORDS, DEZ2_LIVE, "DEZ2_Sprites");
        assertTerminatedTable(DEZ3_OBJECTS_ADDR, DEZ3_RECORDS, 0, "DEZ3_Sprites");
    }

    @Test
    void deathEggPlacementsSplitIntoTheRecordedConcreteAndPlaceholderBaseline() throws IOException {
        Sonic3kObjectRegistry registry = new DezTestRegistry();

        assertSplit(registry, act(DEZ1_OBJECTS_ADDR, DEZ1_RECORDS), CONCRETE_ACT_1, PLACEHOLDER_ACT_1, "act 1");
        assertSplit(registry, act(DEZ2_OBJECTS_ADDR, DEZ2_RECORDS), CONCRETE_ACT_2, PLACEHOLDER_ACT_2, "act 2");
    }

    @Test
    void exactlyTheRecordedIdsResolveToARealFactoryUnderTheSklPointerSet() throws IOException {
        Sonic3kObjectRegistry registry = new DezTestRegistry();
        List<ObjectSpawn> placements = new ArrayList<>();
        placements.addAll(act(DEZ1_OBJECTS_ADDR, DEZ1_RECORDS));
        placements.addAll(act(DEZ2_OBJECTS_ADDR, DEZ2_RECORDS));

        Set<Integer> concrete = new TreeSet<>();
        Set<Integer> placed = new LinkedHashSet<>();
        for (ObjectSpawn spawn : placements) {
            placed.add(spawn.objectId());
            if (!(registry.create(spawn) instanceof PlaceholderObjectInstance)) {
                concrete.add(spawn.objectId());
            }
        }

        assertEquals(new TreeSet<>(CONCRETE_DEZ_IDS), concrete,
                "IDs resolving to a real factory under the SKL pointer set");
        assertTrue(placed.containsAll(CONCRETE_DEZ_IDS),
                "every recorded concrete ID must actually be placed in Death Egg");
    }

    /**
     * The wiring that nothing else can catch: {@code Obj_DEZGravitySwap} selects its crossing
     * direction from {@code btst #0,render_flags(a0)} (sonic3k.asm:95512, :95537), and the
     * placement record carries that bit in the <em>y word's</em> top nibble, not in a subtype.
     * {@code CommonPlacementParser} reads it as {@code (yWord >> 13) & 3}, so the eleven act 2
     * records — six with top nibble {@code 0} and five with top nibble {@code 2} — must come
     * out as six with {@code renderFlags} bit 0 clear and five with it set.
     *
     * <p>Get the shift wrong and every one of the eleven reads as unflipped: the census still
     * passes, the object still resolves, the focused behaviour tests still pass on synthetic
     * spawns, and half of Death Egg act 2's gravity triggers silently do the wrong thing.
     */
    @Test
    void theElevenGravitySwapPlacementsSplitSixUnflippedToFiveXFlipped() throws IOException {
        int unflipped = 0;
        int xFlipped = 0;
        for (ObjectSpawn spawn : act(DEZ2_OBJECTS_ADDR, DEZ2_RECORDS)) {
            if (spawn.objectId() != 0x5B) {
                continue;
            }
            assertEquals(0, spawn.subtype(),
                    "every $5B placement is subtype $00: the flip bit is the only variation");
            if ((spawn.renderFlags() & 1) != 0) {
                xFlipped++;
            } else {
                unflipped++;
            }
        }
        assertEquals(6, unflipped, "$5B placements whose left-to-right crossing sets gravity");
        assertEquals(5, xFlipped, "$5B placements whose right-to-left crossing sets it");
    }

    /**
     * {@code Sonic3kObjectRegistry.registerDefaultFactories} puts {@code $78}
     * ({@code Obj_FBZDEZPlayerLauncher}, 10 act 1 placements) into the factory map twice; the
     * later call wins. Pinning the winner here means the duplicate cannot silently change which
     * of the two implementations Death Egg runs. Recorded as a gap in
     * {@code docs/status/s3k-known-bugs.md}.
     */
    @Test
    void duplicateRegistrationOf78ResolvesToTheLaterFactory() {
        Sonic3kObjectRegistry registry = new DezTestRegistry();
        assertInstanceOf(FbzDezPlayerLauncherObjectInstance.class,
                registry.create(new ObjectSpawn(0, 0, 0x78, 0, 0, false, 0)));
    }

    private static void assertSplit(Sonic3kObjectRegistry registry, List<ObjectSpawn> placements,
                                    int expectedConcrete, int expectedPlaceholder, String label) {
        int concrete = 0;
        int placeholder = 0;
        for (ObjectSpawn spawn : placements) {
            ObjectInstance instance = registry.create(spawn);
            if (instance instanceof PlaceholderObjectInstance) {
                placeholder++;
            } else {
                concrete++;
            }
        }
        assertEquals(expectedConcrete, concrete, label + " concrete placements");
        assertEquals(expectedPlaceholder, placeholder, label + " placeholder placements");
        assertEquals(placements.size(), concrete + placeholder, label + " census covers every placement");
    }

    private static void assertTerminatedTable(int address, int records, int liveSpawns, String label)
            throws IOException {
        byte[] bytes = readPlacementBytes(address, records);
        assertEquals(records * 6, bytes.length, label + " raw six-byte record count");
        assertEquals(List.of(0xFF, 0xFF, 0, 0, 0, 0),
                java.util.stream.IntStream.range(bytes.length - 6, bytes.length)
                        .map(index -> bytes[index] & 0xFF).boxed().toList(),
                label + " full six-byte terminator");
        assertEquals(liveSpawns,
                CommonPlacementParser.parseObjectRecords(new RomByteReader(bytes), 0).size(),
                label + " live spawn count");
    }

    private static List<ObjectSpawn> act(int address, int records) throws IOException {
        return CommonPlacementParser.parseObjectRecords(
                new RomByteReader(readPlacementBytes(address, records)), 0);
    }

    private static byte[] readPlacementBytes(int address, int records) throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        if (romFile == null) {
            throw new IOException("Configured S3K ROM is unavailable");
        }
        try (Rom rom = new Rom()) {
            if (!rom.open(romFile.getAbsolutePath())) {
                throw new IOException("Failed to open configured S3K ROM");
            }
            return rom.readBytes(address, records * 6);
        }
    }

    /** Death Egg is zone {@code $0B}, so {@code S3kZoneSet.forZone} gives the SKL pointer set. */
    private static final class DezTestRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_DEZ;
        }
    }
}
