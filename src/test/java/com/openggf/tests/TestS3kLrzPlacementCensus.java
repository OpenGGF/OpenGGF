package com.openggf.tests;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lava Reef placement census: the slice-0 baseline of the
 * <a href="../../../../../../docs/architecture/plans/2026-09-17-lrz-bring-up.md">LRZ bring-up plan</a>.
 *
 * <p>Every expectation below is decoded from the locked-on ROM's own placement lists
 * ({@code Sprite_ListingK} / {@code RingLocPtrs}, {@code Sonic3kConstants.SPRITE_LOC_PTRS_ADDR}
 * {@code $1E3D98} and {@code RING_LOC_PTRS_ADDR} {@code $1E3E58}) and transcribed from the
 * byte-matched
 * <a href="../../../../../../docs/architecture/research/s3k-zones/lrz-object-inventory.md">placement
 * inventory</a>. Nothing here is read back from the engine.
 *
 * <p>The census builds every placed record through the production
 * {@link Sonic3kObjectRegistry} and pins, per {@code (id, subtype)}, how many placements still
 * resolve to a {@link PlaceholderObjectInstance}. The baseline only ratchets down: implementing a
 * subtype turns this red until {@link #PLACEHOLDER_BASELINE} is reduced, and forgetting a subtype of
 * an otherwise implemented id leaves its row standing.
 *
 * <p>Ring counts come from {@code loc_EB52}/{@code loc_E8BE}: every S3K ring list starts with a
 * {@code (0,0)} record that the ROM never makes live. {@code loc_EB52} counts from
 * {@code Ring_start_addr_ROM + 4}, and {@code loc_E8BE} starts the visible window at the first record
 * whose X is at least {@code max(Camera_X - 8, 1)}, so a record at X {@code 0} is always stepped over.
 */
@com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
class TestS3kLrzPlacementCensus {

    /** {@code LRZ1_Sprites} / {@code LRZ2_Sprites} / {@code LRZ3_Sprites} live record counts. */
    private static final int ACT1_PLACEMENTS = 609;
    private static final int ACT2_PLACEMENTS = 455;
    private static final int BOSS_PLACEMENTS = 35;

    /** {@code LRZ1_Rings} / {@code LRZ2_Rings} / {@code LRZ3_Rings} minus the leading sentinel. */
    private static final int ACT1_RINGS = 331;
    private static final int ACT2_RINGS = 281;
    private static final int BOSS_RINGS = 52;

    /**
     * Placements that still build a {@link PlaceholderObjectInstance}, per act, as
     * {@code id:subtype=count[,subtype=count...];...} with hexadecimal ids and subtypes.
     * Transcribed from the inventory's "placeholder (SKL branch)" and "unregistered" rows, then
     * ratcheted down as slices land. Slice 1 removed {@code $6E} {@code Obj_InvisibleLavaBlock}
     * (34 / 4 / 6), taking the totals from 239 / 281 / 14 to 205 / 277 / 8; slice 3a removed
     * {@code $1E} {@code Obj_LRZDashElevator} (6 / 0 / 0), taking act 1 to 199; slice 3b removed
     * {@code $19} {@code Obj_LRZDoor} (15 / 11), {@code $1A} {@code Obj_LRZBigDoor} (1 / 0),
     * {@code $1C} {@code Obj_LRZButtonHorizontal} (10 / 11) and {@code $1D}
     * {@code Obj_LRZShootingTrigger} (2 / 0), taking the totals to 171 / 255 / 8; slice 3a's
     * {@code $15} {@code Obj_LRZCorkscrew} (1 / 0) took act 1 to 170; slice 3c's {@code $17}
     * {@code Obj_LRZSinkingRock} (11 / 0) took act 1 to 159, and 3a's remaining {@code $16}
     * {@code Obj_LRZWallRide} (1 / 1) took the totals to 158 / 254, and 3c's {@code $18}
     * {@code Obj_LRZFallingSpike} (15 / 0) took act 1 to 143 and {@code $1B}
     * {@code Obj_LRZFireballLauncher} (27 / 0) to 116 and {@code $1F}
     * {@code Obj_LRZLavaFall} (7 / 0) to 109, and {@code $20}
     * {@code Obj_LRZSwingingSpikeBall} (11 / 14) took the totals to 98 / 240, and 3c's
     * {@code $21} {@code Obj_LRZSmashingSpikePlatform} (15 / 0) took act 1 to 83, and {@code $22}
     * {@code Obj_LRZSpikeBall} (6 / 0) to 77, and 3d's {@code $9C}
     * {@code Obj_LRZRockCrusher} (2 / 0) to 75, and slice 4's {@code $9A}
     * {@code Obj_Iwamodoki} (32 / 34) took the totals to 43 / 206, and {@code $9B}
     * {@code Obj_Toxomister} (22 / 9) to 21 / 197, and {@code $99} {@code Obj_Fireworm}
     * (20 / 9) to 1 / 188, and slice 6's {@code $9D} {@code Obj_LRZMiniboss} (1 / 0) took act 1
     * to 0. Slice 7's {@code $2B} {@code Obj_LRZOrbitingSpikeBallHorizontal} (0 / 12) and
     * {@code $2C} {@code Obj_LRZOrbitingSpikeBallVertical} (0 / 40) took act 2 from 188 to
     * 136, {@code $29} {@code Obj_LRZFlameThrower} (0 / 52) to 84 and {@code $2D}
     * {@code Obj_LRZSolidMovingPlatforms} (0 / 52) to 32 and {@code $37}
     * {@code Obj_LRZSpikeBallLauncher} (0 / 9) to 23 and {@code $B3}
     * {@code Obj_StartNewLevel} (0 / 1) to 22 and {@code $25}
     * {@code Obj_LRZChainedPlatforms} (0 / 3) to 19 and {@code $32}
     * {@code Obj_LRZTurbineSprites} (0 / 18) to 1 and {@code $AE}
     * {@code Obj_LRZ2CutsceneKnuckles} (0 / 1) to <b>0</b>; the sentence below is kept as it
     * was written for act 1
     * to <b>0</b>: every act 1 placement now builds a concrete class.
     */
    private static final Map<String, String> PLACEHOLDER_BASELINE = Map.of(
            "LRZ1", "",
            "LRZ2", "",
            "LRZ3", "");

    private static RomByteReader rom;

    @BeforeAll
    static void openRom() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        try (Rom opened = new Rom()) {
            assertTrue(opened.open(romFile.getAbsolutePath()), "locked-on S3K ROM must open");
            rom = RomByteReader.fromRom(opened);
        }
    }

    @Test
    void romPlacementListsHoldTheInventoryRecordCounts() {
        assertEquals(ACT1_PLACEMENTS, placements(Sonic3kZoneIds.ZONE_LRZ, 0).size(), "LRZ1_Sprites");
        assertEquals(ACT2_PLACEMENTS, placements(Sonic3kZoneIds.ZONE_LRZ, 1).size(), "LRZ2_Sprites");
        assertEquals(BOSS_PLACEMENTS, placements(Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, 0).size(),
                "LRZ3_Sprites");
    }

    @Test
    void act1PlaceholderBaselineIsExact() {
        assertPlaceholderBaseline("LRZ1", Sonic3kZoneIds.ZONE_LRZ, 0, 0);
    }

    @Test
    void act2PlaceholderBaselineIsExact() {
        assertPlaceholderBaseline("LRZ2", Sonic3kZoneIds.ZONE_LRZ, 1, 0);
    }

    @Test
    void bossActPlaceholderBaselineIsExact() {
        assertPlaceholderBaseline("LRZ3", Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, 0, 0);
    }

    /**
     * {@code loc_EB52} counts {@code Perfect_rings_left} from the second record, and
     * {@code loc_E8BE} clamps the window search key to at least 1, so the leading {@code (0,0)}
     * record of every LRZ ring list is never a live ring.
     */
    @Test
    void ringListsDropTheLeadingZeroSentinel() {
        assertRings(Sonic3kZoneIds.ZONE_LRZ, 0, ACT1_RINGS, "LRZ1_Rings");
        assertRings(Sonic3kZoneIds.ZONE_LRZ, 1, ACT2_RINGS, "LRZ2_Rings");
        assertRings(Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, 0, BOSS_RINGS, "LRZ3_Rings");
    }

    private void assertRings(int zone, int act, int expected, String label) {
        List<RingSpawn> rings = new Sonic3kRingPlacement(rom).load(zone, act);
        assertEquals(expected, rings.size(), label + " live ring count");
        assertTrue(rings.stream().noneMatch(ring -> ring.x() == 0 && ring.y() == 0),
                label + " must not spawn the leading (0,0) sentinel as a ring");
    }

    private void assertPlaceholderBaseline(String label, int zone, int act, int expectedTotal) {
        Sonic3kObjectRegistry registry = new LrzTestRegistry(zone);
        Map<String, Integer> observed = new TreeMap<>();
        int total = 0;
        for (ObjectSpawn spawn : placements(zone, act)) {
            ObjectInstance instance = registry.create(spawn);
            if (!(instance instanceof PlaceholderObjectInstance)) {
                continue;
            }
            observed.merge(key(spawn.objectId(), spawn.subtype()), 1, Integer::sum);
            total++;
        }
        assertEquals(parseBaseline(PLACEHOLDER_BASELINE.get(label)), observed,
                label + " placeholder placements by (id, subtype)");
        assertEquals(expectedTotal, total, label + " placeholder placement total");
    }

    private List<ObjectSpawn> placements(int zone, int act) {
        return new Sonic3kObjectPlacement(rom).load(zone, act);
    }

    private static Map<String, Integer> parseBaseline(String spec) {
        Map<String, Integer> parsed = new TreeMap<>();
        if (spec.isEmpty()) {
            // An act with nothing left on the placeholder baseline, as LRZ1 has been since the
            // miniboss landed. The ratchet only goes down, so an empty spec is a real state.
            return parsed;
        }
        for (String idGroup : spec.split(";")) {
            String[] idAndSubtypes = idGroup.split(":", 2);
            int id = Integer.parseInt(idAndSubtypes[0], 16);
            for (String entry : idAndSubtypes[1].split(",")) {
                String[] subtypeAndCount = entry.split("=", 2);
                parsed.put(key(id, Integer.parseInt(subtypeAndCount[0], 16)),
                        Integer.parseInt(subtypeAndCount[1]));
            }
        }
        return parsed;
    }

    /** Sorts and prints as the inventory writes a row: {@code $6E/$31}. */
    private static String key(int objectId, int subtype) {
        return String.format("$%02X/$%02X", objectId & 0xFF, subtype & 0xFF);
    }

    /** The registry resolves its pointer set and zone branches from the live ROM zone id. */
    private static final class LrzTestRegistry extends Sonic3kObjectRegistry {
        private final int zone;

        private LrzTestRegistry(int zone) {
            this.zone = zone;
        }

        @Override
        protected int currentRomZoneId() {
            return zone;
        }
    }
}
