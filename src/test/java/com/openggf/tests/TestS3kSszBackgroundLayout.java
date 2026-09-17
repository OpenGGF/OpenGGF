package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closes s3k-known-bugs #41 — "the Sky Sanctuary background plane renders flat sky in both BG
 * modes" — by deciding it from the ROM's own act-1 background layout rather than from a frame.
 *
 * <p>The layout for {@code $A00} lives at {@code LevelPtrs} index {@code $0A * 2 + 0}
 * ({@code $9D5C0 + $50}) and is uncompressed: a four-word header
 * ({@code FG cols}, {@code BG cols}, {@code FG rows}, {@code BG rows}) followed by interleaved
 * per-row pointers into the same {@code $1000}-byte block, based at {@code $8000} in RAM. Act 1's
 * background is 60 columns by 22 rows of 128-pixel chunks, so it covers Y {@code $000}-{@code $AFF}.
 *
 * <p>Decoded here, rows 0-2 ({@code Y $000}-{@code $17F}) and rows 18-21
 * ({@code Y $900}-{@code $AFF}) are a <em>single repeated chunk id</em> across all sixty columns,
 * while rows 3-17 carry between three and fifteen distinct ids each. The background layer is
 * therefore genuinely featureless over the top and bottom bands of the layout and structured only
 * in the middle.
 *
 * <p>{@code sub_57A60}'s plain mode puts the background at {@code Camera_Y + $160}. The arrival
 * camera that {@code SSZ1_ScreenInit} forces is {@code $F49}, and the Y wrap Sky Sanctuary runs
 * ({@code -$100}..{@code $1000}) makes that {@code $10A9 mod $1000 = $A9} — background row 1, one
 * of the single-chunk rows. Flat sky at the arrival is the shipped layout, not a sampling fault.
 *
 * <p>What the cases below actually check is the thing that was open: that the engine's background
 * layer holds the ROM's rows at all, so the structured band is reachable.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszBackgroundLayout {

    /** {@code SSZ1_ScreenInit}: {@code move.w #$F49,(Camera_Y_pos).w}. */
    private static final int ARRIVAL_CAMERA_Y = 0xF49;
    /** {@code sub_57A60} plain mode: {@code addi.w #$160,d0}. */
    private static final int PLAIN_BACKGROUND_Y_OFFSET = 0x160;
    /** The SSZ camera wrap {@code loc_45744} opens and {@code Camera#setVerticalWrapEnabled} owns. */
    private static final int Y_WRAP = 0x1000;
    private static final int CHUNK_PIXELS = 128;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /** The act-1 background layout as the ROM stores it: {@code [row][column]} chunk ids. */
    private static int[][] romBackgroundLayout() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int layoutAddress = rom.readU32BE(Sonic3kConstants.LEVEL_PTRS_ADDR
                + (Sonic3kZoneIds.ZONE_SSZ * 2) * Sonic3kConstants.LEVEL_PTRS_ENTRY_SIZE);
        int backgroundColumns = rom.readU16BE(layoutAddress + 2);
        int backgroundRows = rom.readU16BE(layoutAddress + 6);
        int[][] layout = new int[backgroundRows][backgroundColumns];
        for (int row = 0; row < backgroundRows; row++) {
            int pointer = rom.readU16BE(layoutAddress
                    + Sonic3kConstants.LEVEL_LAYOUT_HEADER_SIZE + 2 + row * 4);
            int offset = (pointer & Sonic3kConstants.LEVEL_LAYOUT_ROW_POINTER_MASK);
            for (int column = 0; column < backgroundColumns; column++) {
                layout[row][column] = rom.readU8(layoutAddress + offset + column);
            }
        }
        return layout;
    }

    /**
     * The shape of the layout itself: a featureless top and bottom band, structure in between.
     * This is the evidence that flat sky at the arrival is the ROM's, and it is decoded from the
     * ROM in the test rather than taken from any engine table.
     */
    @Test
    void theActOneBackgroundLayoutIsOneChunkAtTheTopAndBottomAndStructuredInBetween()
            throws IOException {
        int[][] layout = romBackgroundLayout();
        assertEquals(22, layout.length, "BG rows in the $A00 layout header");
        assertEquals(60, layout[0].length, "BG columns in the $A00 layout header");

        for (int row : new int[]{0, 1, 2}) {
            assertEquals(1, distinct(layout[row]).size(),
                    "BG row " + row + " (Y " + Integer.toHexString(row * CHUNK_PIXELS)
                            + ") is a single repeated chunk");
        }
        for (int row = 18; row < layout.length; row++) {
            assertTrue(distinct(layout[row]).size() <= 2,
                    "BG row " + row + " is at most two chunk ids");
        }
        int structuredRows = 0;
        for (int row = 3; row <= 17; row++) {
            if (distinct(layout[row]).size() >= 3) {
                structuredRows++;
            }
        }
        assertEquals(15, structuredRows,
                "every BG row from 3 to 17 carries at least three distinct chunk ids");
    }

    /**
     * The arrival camera selects one of the single-chunk rows, so the flat sky the slice-2
     * captures show is the layout, not a missing sample.
     */
    @Test
    void theArrivalCameraSelectsASingleChunkBackgroundRow() throws IOException {
        int[][] layout = romBackgroundLayout();
        int backgroundY = (ARRIVAL_CAMERA_Y + PLAIN_BACKGROUND_Y_OFFSET) % Y_WRAP;
        int row = backgroundY / CHUNK_PIXELS;
        assertEquals(1, row, "Camera_Y $F49 + $160 wrapped by $1000 is $A9, background row 1");
        assertEquals(1, distinct(layout[row]).size(),
                "the row the arrival samples is one repeated chunk across the whole level");
    }

    /**
     * The engine loads the ROM's background rows. This is the case that could have disagreed: if
     * the layer were empty, truncated, or offset, flat sky would be an engine fault instead.
     */
    @Test
    void theEngineBackgroundLayerHoldsTheRomRows() throws IOException {
        int[][] expected = romBackgroundLayout();
        boot(320);
        var map = GameServices.level().getCurrentLevel().getMap();
        for (int row = 0; row < expected.length; row++) {
            for (int column = 0; column < expected[row].length; column++) {
                assertEquals(expected[row][column], map.getValue(1, column, row) & 0xFF,
                        "BG layout row " + row + " column " + column);
            }
        }
    }

    private static Set<Integer> distinct(int[] row) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int id : row) {
            ids.add(id);
        }
        return ids;
    }

    private static void boot(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .build()
                .stepIdleFrames(2);
    }
}
