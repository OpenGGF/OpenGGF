package com.openggf.mods.scene;

/**
 * Decodes art from the running game's ROM into RGBA images. Because each sprite is
 * rasterised with its own palette, sprites from different zones, characters and bosses can
 * share the screen without the palette-line juggling the hardware needs.
 */
@com.openggf.game.ModApi
public interface SceneRomArt {
    /** "s1", "s2" or "s3k". */
    String gameId();

    /** Raw ROM bytes, for reading tables a mod understands itself. */
    byte[] read(int address, int length);

    /**
     * Mega Drive colours (9-bit, two bytes each) as {@code 0xFFRRGGBB}. Pass 16 for one
     * palette line or 64 for all four.
     */
    int[] palette(int address, int colors);

    /**
     * A sprite from {@code request}, coloured with {@code palette} (64 colours: four lines of
     * 16; colour 0 of each line is transparent).
     */
    SceneSpriteSet sprites(RomSpriteRequest request, int[] palette);

    /**
     * A playable character's frames ("sonic", "tails", "knuckles") in their own palette,
     * with the ROM's animation scripts.
     */
    SceneSpriteSet character(String characterCode);

    /**
     * A character's separately drawn part, in the character's palette: Tails' two tails in
     * Sonic 2 and Sonic 3 &amp; Knuckles (drawn at Tails' own position; in S3K the idle swish is
     * frames 0x22-0x26 at 8 ticks each). Null when the character has none.
     */
    SceneSpriteSet characterAccessory(String characterCode);

    /** The palette the character's sprites use (16 colours). */
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
}
