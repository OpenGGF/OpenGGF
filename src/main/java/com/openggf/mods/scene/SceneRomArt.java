package com.openggf.mods.scene;

/**
 * Decodes art from the running game's ROM into RGBA images. Because each sprite is
 * rasterised with its own palette, sprites from different zones, characters and bosses can
 * share the screen without the palette-line juggling the hardware needs.
 *
 * <p>Not thread-safe: call it only from the scene's own {@code enter}, {@code update} and
 * {@code draw} calls (the ROM's reads share one file position), never from a worker thread.
 * Methods that build something expensive say whether they cache it; cache the rest yourself.
 * The engine implements this interface; mods use it and are not meant to implement it.
 */
@com.openggf.game.ModApi
public interface SceneRomArt {
    /** "s1", "s2" or "s3k". */
    String gameId();

    /**
     * Raw ROM bytes, for reading tables a mod understands itself.
     *
     * @param length 0 to 1 MiB
     * @throws IllegalArgumentException when the range is negative, over 1 MiB or past the ROM's end
     */
    byte[] read(int address, int length);

    /**
     * Mega Drive colours (9-bit, two bytes each) as {@code 0xFFRRGGBB}: 16 for one palette line,
     * 64 for all four, or any count from 1 to 64 (48 reads three lines).
     *
     * @throws IllegalArgumentException when {@code colors} is outside 1-64 or the read leaves the ROM
     */
    int[] palette(int address, int colors);

    /**
     * A sprite from {@code request}, coloured with {@code palette} (64 colours: four lines of
     * 16; colour 0 of each line is transparent; a shorter array leaves the rest black). Not
     * cached: each call decompresses the art again and returns a new set, so keep the result.
     * Frames are rasterised when first asked for.
     *
     * @throws IllegalArgumentException when the art cannot be decoded at that address with that
     *                                  compression
     */
    SceneSpriteSet sprites(RomSpriteRequest request, int[] palette);

    /**
     * A playable character's frames in their own palette, with the ROM's animation scripts;
     * cached per character. Sonic 1 has {@code "sonic"}; Sonic 2 {@code "sonic"} and
     * {@code "tails"}; Sonic 3 &amp; Knuckles {@code "sonic"}, {@code "tails"} and
     * {@code "knuckles"}.
     *
     * @throws IllegalArgumentException for a character the game does not have
     * @throws IllegalStateException    when the game's character art cannot be loaded
     */
    SceneSpriteSet character(String characterCode);

    /**
     * A character's separately drawn part, in the character's palette: Tails' two tails in
     * Sonic 2 and Sonic 3 &amp; Knuckles (drawn at Tails' own position; in S3K the idle swish is
     * frames 0x22-0x26 at 8 ticks each). Null when the character has none. Cached.
     */
    SceneSpriteSet characterAccessory(String characterCode);

    /**
     * The palette the character's sprites use (16 colours). Only meaningful for the characters
     * {@link #character} supports; for others it may be all zeros or another character's line.
     */
    int[] characterPalette(String characterCode);

    /**
     * A zone's background as the game loads it (level art, layout, load-time palette and
     * animated tiles at their first frame), with its parallax bands; see
     * {@link SceneBackdrop} for drawing it. Built from the ROM on the first request for a
     * zone and act (tens of milliseconds) and cached; it never touches a running level.
     *
     * <p>Sonic 3 &amp; Knuckles zone ids: 0 Angel Island, 1 Hydrocity, 6 Launch Base,
     * 10 Sky Sanctuary. Supported today: Angel Island acts 1 and 2 (act 1's main level, not
     * the intro beach), Hydrocity act 1 (seen from below the waterline), Launch Base act 1
     * and Sky Sanctuary act 1 (its cloud sea).
     *
     * @param zone the game's zone id
     * @param act  0 for act 1, 1 for act 2
     * @return the backdrop, or null when this game or zone has none
     */
    default SceneBackdrop zoneBackdrop(int zone, int act) {
        return null;
    }

    /**
     * A whole act as one small opaque picture, for maps and level-select screens: the
     * foreground plane over the background plane (the background at the same level
     * coordinates, repeating as its layout does; no objects, rings or water tint), cropped to
     * the area the camera can show, then shrunk by the smallest whole factor that makes it at
     * most {@code maxHeight} pixels tall and 4096 wide. Each output pixel is the average of
     * its square of level pixels, so it reads as a miniature; edge pixels average the part of
     * their square inside the level. Uses the art and palette the act loads with, and animated
     * tiles at their first frame.
     *
     * <pre>{@code
     * SceneImage map = ctx.art().rom().levelOverview(1, 0, 196);   // Hydrocity act 1
     * canvas.drawRegion(map, scrollX, 0, canvas.width(), map.height(), 0, 14, canvas.width(),
     *         map.height(), SceneDraw.plain());
     * }</pre>
     *
     * <p>Same zones and acts as {@link #zoneBackdrop}. Built on the first request (up to about
     * a second for a long act) and cached per zone, act and {@code maxHeight}; it never touches
     * a running level.
     *
     * @param zone      the game's zone id
     * @param act       0 for act 1, 1 for act 2
     * @param maxHeight the tallest result wanted, in pixels (1 or more; 4096 at most is used)
     * @return the picture, or null when this game or zone has none
     * @throws IllegalArgumentException when {@code maxHeight} is less than 1
     */
    default SceneImage levelOverview(int zone, int act, int maxHeight) {
        return null;
    }

    /**
     * Where an act's floor runs with room above it: stages at least {@code width} pixels wide
     * with {@code headroom} pixels clear of solid terrain above every column, whose highest and
     * lowest floor rows are at most {@code maxRise} apart (0 for dead flat; rolling ground needs
     * more: Angel Island act 1 has a handful of 400-pixel stages within 24 rows), inside the
     * area the camera can show, left to right and not overlapping. Floor is read from the act's
     * collision as a floor sensor sees it (the primary collision path's top-solid shapes),
     * followed every four columns and then read in each column; objects (platforms, badniks,
     * monitors) are not part of the layout and are not considered. Use with
     * {@link #levelForeground} to stage a scene on the real level:
     *
     * <pre>{@code
     * List<SceneLevelStage> stages = rom.levelStages(0, 0, canvas.width(), 100, 24);  // AIZ act 1
     * SceneLevelStage stage = stages.get(stages.size() / 2);
     * int groundRow = 134;                         // the screen row the stage's median floor sits on
     * int top = stage.floorY() - groundRow;        // level row at the top of the screen
     * SceneImage front = rom.levelForeground(0, 0, stage.x(), top, canvas.width(), canvas.height());
     * // draw the zone's backdrop, then front at (0, 0); a character at screen x stands on
     * // screen row stage.floorAt(stage.x() + x) - top
     * }</pre>
     *
     * <p>Same zones and acts as {@link #zoneBackdrop}; empty for others. Found on the first
     * request for a zone, act and sizes and cached; it never touches a running level.
     *
     * @param zone     the game's zone id
     * @param act      0 for act 1, 1 for act 2
     * @param width    the narrowest stage wanted, in pixels (1 or more)
     * @param headroom the clear height wanted above the floor, in pixels (1 or more)
     * @param maxRise  how far the floor may rise or fall along a stage, in pixels (0 or more)
     * @return the stages, possibly none
     * @throws IllegalArgumentException when {@code width} or {@code headroom} is less than 1 or
     *                                  {@code maxRise} is negative
     */
    default java.util.List<SceneLevelStage> levelStages(int zone, int act, int width, int headroom, int maxRise) {
        return java.util.List.of();
    }

    /**
     * An act's foreground plane for a rectangle of level pixels, unscaled: the level art the
     * act loads with (animated tiles at their first frame, no objects, rings or water tint),
     * transparent wherever the plane would show the background and outside the playable
     * layout, so a {@link #zoneBackdrop} drawn first shows through. Both priorities are drawn.
     * Not cached: build it once per placement and keep it.
     *
     * <p>Same zones and acts as {@link #zoneBackdrop}.
     *
     * @param zone   the game's zone id
     * @param act    0 for act 1, 1 for act 2
     * @param x      the rectangle's left edge in level pixels (may be negative)
     * @param y      its top edge (may be negative)
     * @param width  1 to 4096 pixels
     * @param height 1 to 4096 pixels
     * @return the picture, or null when this game or zone has none
     * @throws IllegalArgumentException when a side is outside 1-4096
     */
    default SceneImage levelForeground(int zone, int act, int x, int y, int width, int height) {
        return null;
    }
}
