package com.openggf.mods.scene;

/**
 * Decodes art from one supplied game's ROM into RGBA images. Because each sprite is
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

    /** SHA-1 of the exact supplied ROM bytes, lowercase; empty when identity is unavailable. */
    default String romSha1() { return ""; }

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
     * Raw 8x8 tiles as one image, for ROM art that has no sprite mappings (bonus-stage reel
     * faces, HUD digits, menu and title art): {@code widthTiles} x {@code heightTiles} tiles
     * starting at tile {@code firstTile} of the art at {@code address}, coloured with the first
     * 16 colours of {@code palette} (one line; colour 0 is transparent). Tiles fill the image a
     * row at a time, or a column at a time when {@code columnMajor} (as sprite pieces and the
     * Slot Machine's reel faces store them). Uncompressed art reads just the tiles it needs;
     * compressed art is decompressed whole. Not cached.
     *
     * <pre>{@code
     * // ArtUnc_SlotOptions: eight 4x4-tile reel faces stored column by column, $200 bytes each.
     * SceneImage face = rom.tiles(0x158CAE, RomSpriteRequest.Compression.UNCOMPRESSED, face * 16, 4, 4,
     *         true, rom.palette(0xA9C7C, 16));                        // Pal_Slot_Special line 0
     * }</pre>
     *
     * @param firstTile   the first tile used, counted from the start of the art (0 or more)
     * @param widthTiles  1 to 512 tiles across
     * @param heightTiles 1 to 512 tiles down
     * @param palette     at least 16 {@code 0xAARRGGBB} colours
     * @throws IllegalArgumentException for sizes out of range, a short palette, art that cannot
     *                                  be decoded, or art with fewer tiles than asked for
     */
    SceneImage tiles(int address, RomSpriteRequest.Compression compression, int firstTile, int widthTiles,
            int heightTiles, boolean columnMajor, int[] palette);

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
     * Whether this act has zone pictures: {@link #zoneBackdrop}, {@link #levelOverview},
     * {@link #levelStages} and {@link #levelForeground} return null (or no stages) exactly when
     * this is false. Zone ids follow the game's public zone registry, and acts are zero-based.
     * Sonic 1 supports Green Hill act 1 ({@code 0, 0}); Sonic 2 supports Chemical Plant act 1
     * ({@code 1, 0}, whose stock level index 2 resolves ROM zone $0D). These two providers
     * picture the decoded load state with a stationary backdrop band; they do not animate
     * tiles, cycle palettes or reproduce gameplay parallax.
     *
     * <p>Sonic 3 &amp; Knuckles zone ids: 0 Angel Island, 1 Hydrocity, 6 Launch Base, 10 Sky
     * Sanctuary (act 0 is act 1, act 1 is act 2). Five acts are supported, each pictured in one
     * representative state chosen for presentation, from the act's own level data:
     * <ul>
     *   <li>Angel Island act 1 ({@code 0, 0}): the main level after the intro, with the art and
     *       palette its skip-intro load uses; never the intro beach.</li>
     *   <li>Angel Island act 2 ({@code 0, 1}): the burnt jungle a fresh act 2 load shows (not
     *       act 1's fire transition palette).</li>
     *   <li>Hydrocity act 1 ({@code 1, 0}): seen from below the waterline: the background art
     *       strips the act uploads once the camera is well under the water.</li>
     *   <li>Launch Base act 1 ({@code 6, 0}): as it loads.</li>
     *   <li>Sky Sanctuary act 1 ({@code 10, 0}): the backdrop is the cloud sea the act draws
     *       while the camera is among the clouds; the overview and foreground are as it loads.</li>
     * </ul>
     * Animated tiles show their first frame. Further acts and states are extension points.
     */
    boolean hasZonePictures(int zone, int act);

    /**
     * A zone's background (level art, layout, palette and animated tiles in the state
     * {@link #hasZonePictures} describes) with its parallax bands; see {@link SceneBackdrop} and
     * {@link SceneCanvas#drawBackdrop} for drawing it. Built from the ROM on the first request
     * for a zone and act (tens of milliseconds) and cached; it never touches a running level.
     *
     * @param zone the game's zone id
     * @param act  0 for act 1, 1 for act 2
     * @return the backdrop, or null when {@link #hasZonePictures} is false
     */
    SceneBackdrop zoneBackdrop(int zone, int act);

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
     * <p>Built on the first request (up to about a second for a long act) and cached per zone,
     * act and {@code maxHeight}; it never touches a running level.
     *
     * @param zone      the game's zone id
     * @param act       0 for act 1, 1 for act 2
     * @param maxHeight the tallest result wanted, in pixels (1 or more; 4096 at most is used)
     * @return the picture, or null when {@link #hasZonePictures} is false
     * @throws IllegalArgumentException when {@code maxHeight} is less than 1
     */
    SceneImage levelOverview(int zone, int act, int maxHeight);

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
     * <p>Found on the first request for a zone, act and sizes and cached; it never touches a
     * running level.
     *
     * @param zone     the game's zone id
     * @param act      0 for act 1, 1 for act 2
     * @param width    the narrowest stage wanted, in pixels (1 or more)
     * @param headroom the clear height wanted above the floor, in pixels (1 or more)
     * @param maxRise  how far the floor may rise or fall along a stage, in pixels (0 or more)
     * @return the stages, possibly none; always none when {@link #hasZonePictures} is false
     * @throws IllegalArgumentException when {@code width} or {@code headroom} is less than 1 or
     *                                  {@code maxRise} is negative
     */
    java.util.List<SceneLevelStage> levelStages(int zone, int act, int width, int headroom, int maxRise);

    /**
     * An act's foreground plane for a rectangle of level pixels, unscaled: the level art the
     * act loads with (animated tiles at their first frame, no objects, rings or water tint),
     * transparent wherever the plane would show the background and outside the playable
     * layout, so a {@link #zoneBackdrop} drawn first shows through. Both priorities are drawn.
     * Not cached: build it once per placement and keep it.
     *
     * @param zone   the game's zone id
     * @param act    0 for act 1, 1 for act 2
     * @param x      the rectangle's left edge in level pixels (may be negative)
     * @param y      its top edge (may be negative)
     * @param width  1 to 4096 pixels
     * @param height 1 to 4096 pixels
     * @return the picture, or null when {@link #hasZonePictures} is false
     * @throws IllegalArgumentException when a side is outside 1-4096
     */
    SceneImage levelForeground(int zone, int act, int x, int y, int width, int height);

    /**
     * Whether {@link #titleCard} has this act's card. Sonic 3 &amp; Knuckles has one for zones
     * 0-12 (acts 0 and 1), zone 22 (Lava Reef's boss act 0 and Hidden Palace act 1) and zone 23
     * act 0 (the Death Egg boss act). Sonic 1 and Sonic 2 have none yet.
     */
    boolean hasTitleCard(int zone, int act);

    /**
     * The act's title card as four sprites: the four objects the ROM's title card slides on
     * and off screen ({@code ObjArray_TtlCard}), from the card's own art and mappings, coloured
     * with Sonic's palette line ({@code Pal_SonicTails}: the card draws in line 0, the player's
     * line, so this is how it looks when Sonic or Tails plays). Each frame's origin is its
     * object's position. Cached per zone and act.
     *
     * <ol start="0">
     *   <li>the red banner, with "SONIC 3 &amp; KNUCKLES" at its foot;</li>
     *   <li>the zone's name;</li>
     *   <li>"ZONE";</li>
     *   <li>"ACT" and the act number. The ROM leaves this one out for Sky Sanctuary (10),
     *       Doomsday (12) and Hidden Palace (22, act 1).</li>
     * </ol>
     *
     * <p>To animate it as the ROM does, on its 320-pixel-wide screen (on a wider screen add
     * {@code (width - 320) / 2} to every x to centre it):
     * <table>
     *   <caption>Title card elements</caption>
     *   <tr><th>Frame</th><th>Starts at</th><th>Slides to</th><th>Moves</th><th>Exit tick</th></tr>
     *   <tr><td>0 banner</td><td>(96, -112)</td><td>(96, 64)</td><td>vertically</td><td>1</td></tr>
     *   <tr><td>1 zone name</td><td>(480, 96)</td><td>(160, 96)</td><td>horizontally</td><td>3</td></tr>
     *   <tr><td>2 "ZONE"</td><td>(636, 128)</td><td>(252, 128)</td><td>horizontally</td><td>5</td></tr>
     *   <tr><td>3 act</td><td>(708, 160)</td><td>(260, 160)</td><td>horizontally</td><td>7</td></tr>
     * </table>
     * All of them slide in together at 16 pixels per frame and stop on their targets. Once every
     * one has arrived, the card holds for 90 frames. Then an exit counter counts 1, 2, 3, ...
     * once per frame, and each element starts leaving at 32 pixels per frame (the banner
     * upwards, the others to the right) when the counter reaches its exit tick, until it is off
     * screen.
     *
     * @param zone the game's zone id
     * @param act  0 for act 1, 1 for act 2
     * @return the four frames, or null when {@link #hasTitleCard} is false
     */
    SceneSpriteSet titleCard(int zone, int act);
}
