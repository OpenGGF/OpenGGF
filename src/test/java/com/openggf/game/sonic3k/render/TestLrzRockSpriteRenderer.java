package com.openggf.game.sonic3k.render;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Draw_LRZ_Special_Rock_Sprites} (sonic3k.asm:39556-39647) and {@code sub_1CB68}
 * (39656-39694).
 *
 * <p>Expectations come from the ROM placement lists and {@code LRZ_Rock_SpriteData}, read here
 * directly, and from the window constants in the two routines: front key {@code Camera_X_pos - 8}
 * forced to 1 at or below 8, back key front {@code + $150}, and the unsigned vertical test
 * {@code 0 <= y - Camera_Y_pos + 8 < 240}.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzRockSpriteRenderer {

    private static final int LRZ1_ROCK_PLACEMENT_ADDR = 0x0CAD00;
    private static final int LRZ2_ROCK_PLACEMENT_ADDR = 0x0CB81A;
    private static final int LRZ_ROCK_SPRITE_DATA_ADDR = 0x01CBBE;

    /**
     * Both lists open with the {@code dc.w 0,0,0} record the assembly writes before the binary
     * include, are X-ascending, and end in {@code $FFFF}. Act 1 holds 472 placements and act 2 ten.
     */
    @Test
    void placementListsMatchTheRomRecords() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        var rom = GameServices.rom().getRom();

        LrzRockSpriteRenderer act1 = LrzRockSpriteRenderer.load(rom, 0);
        LrzRockSpriteRenderer act2 = LrzRockSpriteRenderer.load(rom, 1);

        assertEquals(473, act1.placementCount(), "leading (0,0,0) record plus 472 placements");
        assertEquals(11, act2.placementCount(), "leading (0,0,0) record plus 10 placements");

        assertEquals(0, act1.placementX(0));
        assertEquals(0, act1.placementSpriteIndex(0));
        assertEquals(0x38, act1.placementX(1));
        assertEquals(0x7C8, act1.placementY(1));
        assertEquals(0, act1.placementSpriteIndex(1));
        assertEquals(0x2DC8, act1.placementX(472));

        for (LrzRockSpriteRenderer renderer : List.of(act1, act2)) {
            for (int record = 1; record < renderer.placementCount(); record++) {
                assertTrue(renderer.placementX(record - 1) <= renderer.placementX(record),
                        "placement list must be X-ascending at record " + record);
            }
        }

        // Every record's raw bytes, re-read from the ROM region rather than through the renderer.
        byte[] raw = rom.readBytes(LRZ1_ROCK_PLACEMENT_ADDR, act1.placementCount() * 6 + 4);
        for (int record = 0; record < act1.placementCount(); record++) {
            assertEquals(word(raw, record * 6), act1.placementSpriteIndex(record), "record " + record);
            assertEquals(word(raw, record * 6 + 2), act1.placementX(record), "record " + record);
            assertEquals(word(raw, record * 6 + 4), act1.placementY(record), "record " + record);
        }
        assertEquals(0xFFFF, word(raw, act1.placementCount() * 6), "terminator X");
        assertEquals(0x38, word(rom.readBytes(LRZ2_ROCK_PLACEMENT_ADDR + 6, 6), 2));
    }

    /** {@code lsl.w #3,d6}: eight-byte {@code (y offset, size, art tile, x offset)} entries. */
    @Test
    void spriteAttributeEntriesDecodeTheRomTable() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        var rom = GameServices.rom().getRom();
        LrzRockSpriteRenderer renderer = LrzRockSpriteRenderer.load(rom, 0);
        byte[] table = rom.readBytes(LRZ_ROCK_SPRITE_DATA_ADDR, 30 * 8);

        for (int entry = 0; entry < 30; entry++) {
            assertEquals((short) word(table, entry * 8), renderer.entryYOffset(entry), "entry " + entry);
            assertEquals(word(table, entry * 8 + 2) & 0xFF, renderer.entrySize(entry), "entry " + entry);
            assertEquals(word(table, entry * 8 + 4), renderer.entryArtTile(entry), "entry " + entry);
            assertEquals((short) word(table, entry * 8 + 6), renderer.entryXOffset(entry), "entry " + entry);
        }

        // Entry 0 is the plain 2x2 rock: priority set, palette line 2, tile $088, no flips.
        assertEquals(0xC088, renderer.entryArtTile(0));
        assertEquals(0x05, renderer.entrySize(0));
        assertEquals(-8, renderer.entryXOffset(0));
        assertEquals(-8, renderer.entryYOffset(0));
    }

    /**
     * {@code subq.w #8,d4 / bhi / moveq #1,d4}: the branch tests the subtraction's result, so the
     * key is 1 for every camera X at or below 8, and the window is $150 wide from there.
     */
    @Test
    void routineZeroScanMatchesTheWindowKeys() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LrzRockSpriteRenderer renderer = LrzRockSpriteRenderer.load(GameServices.rom().getRom(), 0);

        for (int cameraX : new int[] {0, 1, 8, 9, 0x40, 0x100, 0x800, 0x1B40, 0x2DC0, 0x2FFF}) {
            int frontKey = cameraX > 8 ? cameraX - 8 : 1;
            int[] window = renderer.fullScanWindow(cameraX);
            assertEquals(expectedLowerBound(renderer, frontKey), window[0], "front at " + cameraX);
            assertEquals(expectedLowerBound(renderer, frontKey + 0x150), window[1], "back at " + cameraX);
        }
    }

    /**
     * {@code loc_1CB20} only nudges the pointers, so a frame-by-frame walk in either direction has
     * to agree with {@code loc_1CADE}'s scan from the list head at every step.
     */
    @Test
    void incrementalWalkAgreesWithAFullScanInBothDirections() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LrzRockSpriteRenderer renderer = LrzRockSpriteRenderer.load(GameServices.rom().getRom(), 0);
        LrzZoneRuntimeState state = new LrzZoneRuntimeState(
                Sonic3kZoneIds.ZONE_LRZ, 0, PlayerCharacter.SONIC_AND_TAILS);

        for (int cameraX = 0; cameraX <= 0x2F00; cameraX += 7) {
            renderer.advanceWindow(state, cameraX);
            assertWindowMatchesFullScan(renderer, state, cameraX);
        }
        for (int cameraX = 0x2F00; cameraX >= 0; cameraX -= 11) {
            renderer.advanceWindow(state, cameraX);
            assertWindowMatchesFullScan(renderer, state, cameraX);
        }
        assertEquals(2, state.rocksRoutine(), "addq.b #2,(LRZ_rocks_routine).w on the first pass");
    }

    /** The captured window survives a rewind round trip, or the walk resumes from the wrong record. */
    @Test
    void windowPointersRideTheRuntimeStateCapture() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LrzRockSpriteRenderer renderer = LrzRockSpriteRenderer.load(GameServices.rom().getRom(), 0);
        LrzZoneRuntimeState state = new LrzZoneRuntimeState(
                Sonic3kZoneIds.ZONE_LRZ, 0, PlayerCharacter.SONIC_AND_TAILS);

        renderer.advanceWindow(state, 0x1B40);
        byte[] captured = state.captureBytes();
        int front = state.rocksFrontIndex();
        int back = state.rocksBackIndex();
        assertTrue(back > front, "the $150 window over act 1 is never empty mid-level");

        renderer.advanceWindow(state, 0x0040);
        assertTrue(state.rocksFrontIndex() != front || state.rocksBackIndex() != back);

        state.restoreBytes(captured);
        assertEquals(front, state.rocksFrontIndex());
        assertEquals(back, state.rocksBackIndex());
        assertEquals(2, state.rocksRoutine());
    }

    /**
     * {@code sub_1CB68} draws every record between the pointers whose
     * {@code y - Camera_Y_pos + 8} is below 240 unsigned; a taller viewport widens only that test,
     * and a wider one only the back key, so 320x224 is the ROM's exact set.
     */
    @Test
    void visibleSetIsTheRomWindowAtNativeSizeAndGrowsOnlyWhenTheViewportDoes() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LrzRockSpriteRenderer renderer = LrzRockSpriteRenderer.load(GameServices.rom().getRom(), 0);
        LrzZoneRuntimeState state = new LrzZoneRuntimeState(
                Sonic3kZoneIds.ZONE_LRZ, 0, PlayerCharacter.SONIC_AND_TAILS);

        // A camera over the act-1 opening ledges: 12 records inside the $150 window, six of them
        // inside the 240-line vertical test, so both halves of the selection actually bite.
        int cameraX = 0x00C0;
        int cameraY = 0x04D0;
        renderer.advanceWindow(state, cameraX);

        List<Integer> expected = new ArrayList<>();
        for (int record = state.rocksFrontIndex(); record < state.rocksBackIndex(); record++) {
            if (((renderer.placementY(record) - cameraY + 8) & 0xFFFF) < 240) {
                expected.add(record);
            }
        }
        assertEquals(expected, collect(renderer, state, cameraX, cameraY, 320, 224));
        assertTrue(!expected.isEmpty(), "chosen camera must actually show rocks");

        List<Integer> wide = collect(renderer, state, cameraX, cameraY, 424, 224);
        assertTrue(wide.size() >= expected.size(), "a wider viewport never drops a rock");
        assertEquals(expected, wide.subList(0, expected.size()),
                "the ROM set stays first and in list order");
        int wideBackKey = cameraX - 8 + 0x150 + (424 - 320);
        for (int record : wide) {
            assertTrue(renderer.placementX(record) < wideBackKey,
                    "record " + record + " is outside the widened back key");
        }
    }

    /** The provider a real Lava Reef load resolves is the sprite-table splice point. */
    @Test
    void productionLoadResolvesTheRendererThroughTheZoneFeatureProvider() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        var provider = GameServices.level().getZoneFeatureProvider();

        assertTrue(provider instanceof com.openggf.level.render.PriorityBucketSpriteSource,
                "Render_Sprites_NextLevel needs a bucket splice point");
        assertNotNull(((com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider) provider)
                .lrzRockSpriteRenderer(), "zone 9 loads a rock list");

        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, 1).build();
        var hpzProvider = GameServices.level().getZoneFeatureProvider();
        org.junit.jupiter.api.Assertions.assertNull(
                ((com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider) hpzProvider)
                        .lrzRockSpriteRenderer(),
                "cmpi.b #9,(Current_zone).w: Hidden Palace has no rocks");
    }

    private static List<Integer> collect(LrzRockSpriteRenderer renderer, LrzZoneRuntimeState state,
                                         int cameraX, int cameraY, int width, int height) {
        List<Integer> records = new ArrayList<>();
        renderer.forEachVisibleRock(state, cameraX, cameraY, width, height, records::add);
        return records;
    }

    private static void assertWindowMatchesFullScan(LrzRockSpriteRenderer renderer,
                                                    LrzZoneRuntimeState state, int cameraX) {
        int[] expected = renderer.fullScanWindow(cameraX);
        assertEquals(expected[0], state.rocksFrontIndex(), "front at camera " + cameraX);
        assertEquals(expected[1], state.rocksBackIndex(), "back at camera " + cameraX);
    }

    /** First record whose X is not below {@code key}, computed without the renderer's walk. */
    private static int expectedLowerBound(LrzRockSpriteRenderer renderer, int key) {
        for (int record = 0; record < renderer.placementCount(); record++) {
            if (renderer.placementX(record) >= key) {
                return record;
            }
        }
        return renderer.placementCount();
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
