package com.openggf.game.sonic3k.ending;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeState;
import java.util.function.IntBinaryOperator;

/**
 * What the ending scenes show beside the cartridge's 320 pixels, and which of them do.
 *
 * <p>The ending draws its sprites at native screen positions, centred in a wider picture by
 * {@link EndingScreenProjection}. Its two tile planes are scrolled by native words that know
 * nothing of the wider picture, so the tile pass puts their native left edge at the
 * picture's left edge, one margin left of the sprites. Each plane image is therefore handed
 * to the tile pass moved right by that margin (every supported width has a margin that is a
 * whole number of 8-pixel cells), and the cells beside the native window are chosen here.
 *
 * <p>At 320 pixels the margin is zero and every method returns the native answer.
 */
public final class EndingWidePlanes {
    private static final int NATIVE_WIDTH = 320;

    private EndingWidePlanes() { }

    /**
     * Whether this scene fills a wider picture itself, so that the native-window mask must
     * stay off: every ending scene until its closing art on black, which is 320 pixels of
     * art with nothing beside it and stays between the bars.
     */
    public static boolean fillsPicture(ZoneRuntimeState state) {
        if (state instanceof EndingZoneRuntimeState rescue) return !rescue.ending().flag(3);
        // Knuckles' ending starts under the white screen of Mecha Sonic's final defeat.
        return state instanceof SszZoneRuntimeState ssz && ssz.actIndex() == 1
                && ssz.act2EndingActive() && !ssz.ending().flag(3);
    }

    /**
     * loc_5AB36 parks Plane A at Camera_X_pos_copy = $140 without redrawing it, and the island
     * pan then runs 144 passes of 2 pixels, to $260. DrawTilesAsYouMove keeps the columns from
     * the rounded position to $160 past it drawn, so the pan draws layout columns $290-$3BF.
     */
    private static final int PAN_FIRST_COLUMN = 0x140 + 0x150, PAN_END_COLUMN = (0x260 & ~15) + 0x160;
    private static final int DRAWN_AHEAD = 0x160, PLANE_WIDTH = 512;

    /**
     * $0D01 Plane A at a foreground tilemap position.
     *
     * <p>Inside the native window this is the retained name table, stale cells included.
     * Beside it:
     * <ul>
     *   <li>Angel Island's own level layout (the Super Emerald return) simply continues.</li>
     *   <li>The rescue's layout is a scratch sheet that keeps other shots' art just outside
     *       what each shot draws. During the island pan the columns the pan itself draws are
     *       shown, ahead of its leading edge as well as behind it, so the island comes in
     *       from the real edge of the picture.</li>
     *   <li>Anywhere else the name table's own cell is shown while it still holds what was
     *       last drawn at that position, as a wider VDP would show it, and nothing once the
     *       leading edge has reused the cell.</li>
     * </ul>
     */
    public static int foreground(EndingZoneRuntimeState zone, Camera camera, IntBinaryOperator layout,
                                 int sourceX, int sourceY) {
        var plane = zone.ending().scene().foreground();
        int margin = EndingWideStage.margin(camera);
        if (margin == 0) return plane.descriptor(sourceX, sourceY);
        int x = sourceX - margin, left = camera.getXCopy();
        if (x + 8 > left && x < left + NATIVE_WIDTH) return plane.descriptor(x, sourceY);
        if (levelLayout(zone)) return layout.applyAsInt(x, sourceY);
        if (islandPan(zone) && x >= PAN_FIRST_COLUMN && x < PAN_END_COLUMN) return layout.applyAsInt(x, sourceY);
        int drawn = (left & ~15) + DRAWN_AHEAD;
        return x >= drawn - PLANE_WIDTH && x < drawn ? plane.descriptor(x, sourceY) : 0;
    }

    private static boolean levelLayout(EndingZoneRuntimeState zone) {
        return zone.ending().scene().activeScene() == S3kEndingSceneState.AIZ;
    }

    /** Ending_ScreenEvent routines 4-12: from the parked plane to the fade that ends the rescue. */
    private static boolean islandPan(EndingZoneRuntimeState zone) {
        int routine = zone.screen().foregroundRoutine;
        return routine >= 4 && routine <= 12;
    }

    /** Changes with the name table, the shot, and the scroll position that selects the cells beside it. */
    public static long foregroundRevision(EndingZoneRuntimeState zone, Camera camera) {
        var plane = zone.ending().scene().foreground();
        int margin = EndingWideStage.margin(camera);
        return margin == 0 ? plane.revision() : (long) plane.revision() << 32
                | (levelLayout(zone) ? 1L << 30 : 0) | (islandPan(zone) ? 1L << 29 : 0)
                | (long) (camera.getXCopy() >> 3 & 0xFFFF) << 12 | margin;
    }

    /**
     * $0D01 Plane B at a background tilemap position. Its rows are drawn 512 pixels wide and
     * scrolled line by line, so the name table is one image that repeats, as on the VDP.
     */
    public static int background(S3kEndingSceneState.Plane plane, Camera camera, int sourceX, int sourceY) {
        return plane.descriptor(sourceX - EndingWideStage.margin(camera), sourceY);
    }

    public static long backgroundRevision(S3kEndingSceneState.Plane plane, Camera camera) {
        int margin = EndingWideStage.margin(camera);
        return margin == 0 ? plane.revision() : (long) plane.revision() << 12 | margin;
    }

    /**
     * Sky Sanctuary act 2's island Plane B: the layout column shown at a background tilemap
     * column. The plane shows 320 pixels from {@code Camera_X_pos_BG_copy}; the layout beside
     * them holds unrelated cloud strips, so each margin repeats the nearest native column.
     */
    public static int islandColumn(int margin, int backgroundCameraX, int sourceX) {
        if (margin == 0) return sourceX;
        return Math.clamp(sourceX - margin, backgroundCameraX & ~7, (backgroundCameraX + NATIVE_WIDTH - 1) & ~7);
    }
}
