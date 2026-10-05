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
}
