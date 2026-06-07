package com.openggf.graphics;

/**
 * Central registry of virtual pattern ID ranges used above the Mega Drive
 * VDP's native 11-bit tile index space.
 */
public enum PatternAtlasRange {
    LEVEL_TILES(0x00000, 0x02000, "Level tiles"),
    OBJECTS(0x20000, 0x08000, "Objects"),
    HUD(0x28000, 0x08000, "HUD"),
    WATER_SURFACE(0x30000, 0x08000, "Water surface"),
    SIDEKICK_BANKS(0x38000, 0x08000, "Sidekick banks"),
    TITLE_CARDS(0x40000, 0x08000, "Title cards"),
    TRANSIENT_EFFECTS(0x48000, 0x08000, "Transient effects"),
    MENU_AND_DATA_SELECT(0x50000, 0x08000, "Menu and data select"),
    RESULTS_SCREENS(0x60000, 0x08000, "Results screens"),
    SPECIAL_STAGE_RESULTS(0x70000, 0x08000, "Special stage results"),
    S3K_TITLE_SCREEN_ANIMATION(0xE0000, 0x08000, "S3K title screen animation"),
    S3K_TITLE_SCREEN_SPRITES(0xE8000, 0x08000, "S3K title screen sprites");

    private final int base;
    private final int size;
    private final String category;

    PatternAtlasRange(int base, int size, String category) {
        this.base = base;
        this.size = size;
        this.category = category;
    }

    public int base() {
        return base;
    }

    public int size() {
        return size;
    }

    public int endExclusive() {
        return base + size;
    }

    public String category() {
        return category;
    }
}
