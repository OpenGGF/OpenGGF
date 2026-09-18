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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sky Sanctuary act-1 background layout, decoded from the ROM, and the two different things it
 * says about the two background modes.
 *
 * <p>The layout for {@code $A00} lives at {@code LevelPtrs} index {@code $0A * 2 + 0}
 * ({@code $9D5C0 + $50}) and is uncompressed: a four-word header
 * ({@code FG cols}, {@code BG cols}, {@code FG rows}, {@code BG rows}) followed by interleaved
 * per-row pointers into the same {@code $1000}-byte block, based at {@code $8000} in RAM. Act 1's
 * background is 60 columns by 22 rows of 128-pixel chunks, so it covers Y {@code $000}-{@code $AFF}.
 *
 * <p><b>Plain mode is correct.</b> {@code sub_57A60}'s plain framing puts the background at
 * {@code Camera_Y + $160}; the arrival camera {@code SSZ1_ScreenInit} forces is {@code $F49}, and
 * the Y wrap Sky Sanctuary runs makes that {@code $10A9 mod $1000 = $A9} — background row 1, one of
 * the rows that is a single repeated chunk across all sixty columns. Flat sky at the arrival is the
 * shipped layout, not a sampling fault.
 *
 * <p><b>Cloud mode is not.</b> {@code loc_5786A}, {@code loc_57946} and {@code loc_5799A} each load
 * a literal {@code move.w #$1C00,d1} before {@code Refresh_PlaneFull} / {@code Draw_TileRow}, and
 * {@code loc_5799A} passes {@code moveq #$20,d6} (32 cells, the full 512-pixel plane width). So
 * cloud mode reads layout columns 56-59 every frame regardless of the camera, and those are exactly
 * the columns that hold the cloud band. The engine derives its columns from the camera and so draws
 * sky. That is s3k-known-bugs #41; the case below pins the window a fix has to target.
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
    /** {@code move.w #$1C00,d1}: {@code $1C00 >> 7}. */
    private static final int CLOUD_WINDOW_FIRST_COLUMN = 56;
    /** {@code moveq #$20,d6}: 32 cells is four 128-pixel chunks. */
    private static final int CLOUD_WINDOW_COLUMNS = 4;
    /** The literal {@code d1} the three cloud-path draw calls load. */
    private static final int CLOUD_WINDOW_LAYOUT_X = 0x1C00;
    /** {@code loc_57960}: wrapped {@code Camera_Y} in {@code [$800,$F00)} is cloud mode. */
    private static final int CLOUD_BAND_LOW = 0x800;
    private static final int CLOUD_BAND_HIGH = 0xF00;

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
     * {@code loc_5799A}'s {@code move.w #$1C00,d1} with {@code moveq #$20,d6} pins the cloud-mode
     * plane to layout columns {@code $1C00 >> 7 = 56} through 59, four chunks tiling the whole
     * 512-pixel plane. Those four columns carry the cloud band in rows 3-7 and nothing anywhere
     * else, which is why sampling them from the camera instead produces flat sky: at those rows,
     * columns 0-22 and 43-55 are entirely the single sky chunk.
     */
    @Test
    void theCloudBandLivesOnlyInTheFourColumnsTheRomPinsTheCloudPlaneTo() throws IOException {
        int[][] layout = romBackgroundLayout();
        Set<Integer> skyAndBlank = new LinkedHashSet<>();
        for (int row = 0; row < layout.length; row++) {
            for (int column = CLOUD_WINDOW_FIRST_COLUMN;
                    column < CLOUD_WINDOW_FIRST_COLUMN + CLOUD_WINDOW_COLUMNS; column++) {
                int id = layout[row][column];
                if (row >= 3 && row <= 7) {
                    assertTrue(id != 0x00 && id != 0x02,
                            "cloud-window row " + row + " column " + column + " carries cloud art");
                } else {
                    skyAndBlank.add(id);
                }
            }
        }
        assertEquals(Set.of(0x00, 0x02), skyAndBlank,
                "outside rows 3-7 the cloud window is only the sky and blank chunks");

        // The camera-derived columns the engine uses instead, at the same rows.
        for (int row = 1; row <= 8; row++) {
            for (int column = 0; column <= 22; column++) {
                assertEquals(0x02, layout[row][column],
                        "row " + row + " column " + column + " is the flat sky chunk");
            }
        }
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
     * s3k-known-bugs #41. Cloud mode must source the background plane from the fixed
     * {@code $1C00} window, exactly as {@code loc_5786A}, {@code loc_57946} and {@code loc_5799A}
     * do with their literal {@code move.w #$1C00,d1} (and {@code loc_5799A}'s
     * {@code moveq #$20,d6}, the full 512-pixel plane width). The arrival rise carries the camera
     * out of the plain framing and into the cloud band, so a cold load reaches the mode on its
     * own; the handler must then report the window origin rather than leaving the tilemap window
     * at the camera-derived default, which lands on columns 0-3 — all sky at these rows.
     *
     * <p>This is the assertion the earlier "flat sky is correct" reading missed: the two modes
     * index the layout by different rules, so the arrival's row selection says nothing about the
     * columns the cloud path reads.
     */
    @Test
    void cloudModeSourcesThePlaneFromTheFixedWindowAndNotTheCamera() {
        HeadlessTestFixture fixture = boot(320);
        var parallax = GameServices.parallax();
        boolean reachedCloudBand = false;
        for (int frame = 0; frame < 400 && !reachedCloudBand; frame++) {
            fixture.stepIdleFrames(1);
            int wrappedCameraY = GameServices.camera().getY() & (Y_WRAP - 1);
            reachedCloudBand = wrappedCameraY >= CLOUD_BAND_LOW && wrappedCameraY < CLOUD_BAND_HIGH;
        }
        assertTrue(reachedCloudBand,
                "the arrival rise carries the camera into the cloud band [$800,$F00)");
        assertEquals(CLOUD_WINDOW_LAYOUT_X, parallax.getBgCameraX(),
                "cloud mode pins the background plane's layout X to $1C00 (loc_5799A)");
    }

    /**
     * The cloud band cannot occlude the HUD, and the reason is in the tile words rather than in
     * the renderer. On the hardware the plane/sprite order is low planes, low sprites, high
     * planes, high sprites — so a Plane B tile with bit 15 set really can cover a low-priority
     * sprite. None of the cloud chunks sets it: every pattern in the cloud window's chunks is
     * low priority, so they sit behind every sprite including the HUD.
     *
     * <p>Filed because the fixed background made the white HUD text sit on white cloud instead of
     * on flat blue, which reads as washed out. That is contrast, not occlusion — the HUD glyph
     * pixels are byte-identical before and after the fix — and this case pins the ROM fact the
     * conclusion rests on, so a later renderer change cannot quietly invalidate it.
     */
    @Test
    void noCloudWindowChunkCarriesAHighPriorityTile() throws IOException {
        int[][] layout = romBackgroundLayout();
        boot(320);
        var level = GameServices.level().getCurrentLevel();
        Set<Integer> checked = new LinkedHashSet<>();
        int patterns = 0;
        for (int row = 3; row <= 7; row++) {
            for (int column = CLOUD_WINDOW_FIRST_COLUMN;
                    column < CLOUD_WINDOW_FIRST_COLUMN + CLOUD_WINDOW_COLUMNS; column++) {
                int chunkId = layout[row][column];
                if (!checked.add(chunkId)) {
                    continue;
                }
                var block = level.getBlock(chunkId);
                assertNotNull(block, "chunk $" + Integer.toHexString(chunkId) + " is decoded");
                int side = block.getGridSide();
                for (int by = 0; by < side; by++) {
                    for (int bx = 0; bx < side; bx++) {
                        var chunk = level.getChunk(block.getChunkDesc(bx, by).getChunkIndex());
                        if (chunk == null) {
                            continue;
                        }
                        for (int ty = 0; ty < 2; ty++) {
                            for (int tx = 0; tx < 2; tx++) {
                                patterns++;
                                assertFalse(chunk.getPatternDesc(tx, ty).getPriority(),
                                        "cloud chunk $" + Integer.toHexString(chunkId)
                                                + " sets a high-priority tile");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked.size() >= 12, "the cloud band uses at least twelve distinct chunks");
        assertTrue(patterns > 0, "the walk actually reached pattern descriptors");
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

    private static HeadlessTestFixture boot(int width) {
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
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .build();
        fixture.stepIdleFrames(2);
        return fixture;
    }
}
