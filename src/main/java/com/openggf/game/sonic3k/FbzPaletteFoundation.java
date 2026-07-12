package com.openggf.game.sonic3k;

/** ROM palette ownership boundaries shared by the later FBZ event and boss tasks. */
public final class FbzPaletteFoundation {
    public static final String OWNER = S3kPaletteOwners.FBZ_EVENT_PALETTE;
    public static final int PALETTE_LINE_INDEX = 3;
    public static final int BACKGROUND_FIRST_COLOR = 2;
    public static final int BACKGROUND_COLOR_COUNT = 8;
    public static final int BOSS_COLOR_INDEX = 1;

    private FbzPaletteFoundation() {}
}
