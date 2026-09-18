package com.openggf.game.sonic2.kis2;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2;
import com.openggf.game.sonic2.Sonic2ObjectPlacement;
import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Sonic 2 game data with the lock-on overrides: KiS2 object layouts through
 * the lock-on address space, Knuckles' player art from the S&amp;K half, and
 * the Knuckles palette on line 0 (`docs/kis2/BRANCH_DIFFS.md`). With the
 * lock-on dump (tier two) the chip window joins the address space, so the CNZ
 * pointers resolve and line 0 is the chip's own {@code Pal_BGND}.
 */
final class Kis2Game extends Sonic2 {

    private final RomByteReader sk;
    private final PlayerSpriteArtProvider skKnucklesArt;
    private final RomByteReader kis2Image;
    private final Kis2ChipArt chipArt;
    private Kis2PlayerArt playerArt;

    Kis2Game(Rom rom, RomByteReader sk, PlayerSpriteArtProvider skKnucklesArt) {
        this(rom, sk, skKnucklesArt, null, null);
    }

    /**
     * @param kis2Image the full lock-on image, or {@code null} for tier one
     * @param chipArt   the chip assets over that image, or {@code null} for tier one
     */
    Kis2Game(Rom rom, RomByteReader sk, PlayerSpriteArtProvider skKnucklesArt,
             RomByteReader kis2Image, Kis2ChipArt chipArt) {
        super(rom);
        this.sk = Objects.requireNonNull(sk, "sk");
        this.skKnucklesArt = Objects.requireNonNull(skKnucklesArt, "skKnucklesArt");
        this.kis2Image = kis2Image;
        this.chipArt = chipArt;
    }

    @Override
    public String getIdentifier() {
        return "Sonic2";
    }

    @Override
    protected Sonic2ObjectPlacement createObjectPlacement(RomByteReader romReader) {
        LockOnAddressSpace addressSpace = kis2Image == null
                ? LockOnAddressSpace.tierOne(sk, romReader)
                : LockOnAddressSpace.tierTwo(sk, romReader, kis2Image);
        return new Kis2ObjectPlacement(sk, romReader, addressSpace);
    }

    /**
     * Line 0: the chip's {@code Pal_BGND} when the dump is present, else the
     * S&amp;K {@code Pal_KnuxEndPose} line that byte-matches it.
     */
    @Override
    protected Palette characterPaletteOverride() {
        return knucklesPalette();
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        if (isKnuckles(characterCode)) {
            return playerArt().loadKnuckles();
        }
        return super.loadPlayerSpriteArt(characterCode);
    }

    @Override
    public Palette loadCharacterPalette(String characterCode) {
        if (isKnuckles(characterCode)) {
            return knucklesPalette();
        }
        return super.loadCharacterPalette(characterCode);
    }

    private Palette knucklesPalette() {
        if (chipArt != null) {
            return chipArt.backgroundPaletteLine0();
        }
        return playerArt().loadKnucklesPalette();
    }

    Kis2PlayerArt playerArt() {
        if (playerArt == null) {
            playerArt = new Kis2PlayerArt(sk, skKnucklesArt);
        }
        return playerArt;
    }

    private static boolean isKnuckles(String characterCode) {
        return characterCode != null && "knuckles".equals(characterCode.trim().toLowerCase(Locale.ROOT));
    }
}
