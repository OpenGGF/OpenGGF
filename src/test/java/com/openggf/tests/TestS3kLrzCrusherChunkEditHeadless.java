package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kLRZEvents;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@code LRZ1_ScreenEvent}'s chunk edits (sonic3k.asm:115199-115224), the consumer side of the
 * rock crusher's {@code Events_bg+$0C} request.
 *
 * <p>{@code a3} is {@code Level_layout_main}, whose entries are longs
 * ({@code Layout_row_index_mask = $7C}, :102207), so {@code movea.w $38(a3)} / {@code $3C(a3)} /
 * {@code $40(a3)} are the FOREGROUND row pointers of layout rows 14, 15 and 16, and
 * {@code lea $1D(a1)} indexes column 29. The rock crusher's own bridge coordinates confirm the
 * reading independently: subtype 0 drops slabs at {@code ($F00,$760)} and {@code ($F80,$760)} --
 * columns 30 and 31 of row 14 -- and the other subtype at {@code ($540,$860)}, column 10 of row 16.
 *
 * <p>The engine calls the ROM's {@code $80 x $80} unit a block, so each ROM byte write is one
 * {@code setBlockInMap(0, column, row, id)}.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzCrusherChunkEditHeadless {

    private static final int ROW_A = 14;
    private static final int ROW_B = 15;
    private static final int ROW_C = 16;
    private static final int FIRST_COLUMN = 29;
    private static final int POSITIVE_COLUMN = 10;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    /** {@code loc_56B2C} (:115210-115219): {@code $44,$00,$4A} then {@code $3E,$00,$4B}. */
    @Test
    void aNegativeRequestWritesTheTwoRowOpening() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        Map map = map();
        int[] beforeA = {block(map, FIRST_COLUMN, ROW_A), block(map, FIRST_COLUMN + 1, ROW_A),
                block(map, FIRST_COLUMN + 2, ROW_A)};
        int[] beforeB = {block(map, FIRST_COLUMN, ROW_B), block(map, FIRST_COLUMN + 1, ROW_B),
                block(map, FIRST_COLUMN + 2, ROW_B)};
        int[] expectedA = {0x44, 0x00, 0x4A};
        int[] expectedB = {0x3E, 0x00, 0x4B};
        assertNotEquals(expectedA[0], beforeA[0],
                "the edit must be observable: the placed layout already holds a different id here");

        state().setChunkEditRequest(-1);
        events().update(0, 1);

        for (int i = 0; i < 3; i++) {
            assertEquals(expectedA[i], block(map, FIRST_COLUMN + i, ROW_A),
                    "row 14 column " + (FIRST_COLUMN + i));
            assertEquals(expectedB[i], block(map, FIRST_COLUMN + i, ROW_B),
                    "row 15 column " + (FIRST_COLUMN + i));
        }
        assertEquals(0, state().chunkEditRequest(), "clr.w (Events_bg+$0C).w at loc_56B54");
        assertEquals(block(map, POSITIVE_COLUMN, ROW_C), block(map, POSITIVE_COLUMN, ROW_C),
                "the positive branch's cell is untouched by this request");
    }

    /** {@code move.b #$9C,$A(a1)} on the positive branch (:115205-115207). */
    @Test
    void aPositiveRequestWritesTheSingleBlock() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        Map map = map();
        int beforeA = block(map, FIRST_COLUMN, ROW_A);

        state().setChunkEditRequest(0x00FF);
        events().update(0, 1);

        assertEquals(0x9C, block(map, POSITIVE_COLUMN, ROW_C), "row 16 column 10");
        assertEquals(beforeA, block(map, FIRST_COLUMN, ROW_A),
                "the two-row opening belongs to the negative branch only");
        assertEquals(0, state().chunkEditRequest());
    }

    /** {@code tst.w (Events_bg+$0C).w / beq.s loc_56B5E} (:115203-115204). */
    @Test
    void noRequestChangesNothing() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        Map map = map();
        int beforeA = block(map, FIRST_COLUMN, ROW_A);
        int beforeC = block(map, POSITIVE_COLUMN, ROW_C);

        events().update(0, 1);

        assertEquals(beforeA, block(map, FIRST_COLUMN, ROW_A));
        assertEquals(beforeC, block(map, POSITIVE_COLUMN, ROW_C));
    }

    /** Act 2 shares the runtime state but {@code LRZ2_ScreenEvent} has no chunk-edit branch. */
    @Test
    void actTwoIgnoresTheRequest() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 1).build();
        Map map = map();
        int beforeA = block(map, FIRST_COLUMN, ROW_A);

        state().setChunkEditRequest(-1);
        events().update(1, 1);

        assertEquals(beforeA, block(map, FIRST_COLUMN, ROW_A));
        assertEquals(-1, (short) state().chunkEditRequest(), "the request is not consumed either");
    }

    private static int block(Map map, int column, int row) {
        return map.getValue(0, column, row) & 0xFF;
    }

    private static Map map() {
        LevelManager manager = GameServices.levelOrNull();
        Level level = manager != null ? manager.getCurrentLevel() : null;
        return level != null ? level.getMap() : null;
    }

    private static LrzZoneRuntimeState state() {
        return S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static Sonic3kLRZEvents events() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return manager.getLrzEventsForTest();
    }
}
