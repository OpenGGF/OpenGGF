package com.openggf.game.sonic2.kis2;

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
 * the S2-layout Knuckles palette on line 0 (`docs/kis2/BRANCH_DIFFS.md`).
 */
final class Kis2Game extends Sonic2 {

    private final RomByteReader sk;
    private Kis2PlayerArt playerArt;

    Kis2Game(Rom rom, RomByteReader sk) {
        super(rom);
        this.sk = Objects.requireNonNull(sk, "sk");
    }

    @Override
    public String getIdentifier() {
        return "Sonic2";
    }

    @Override
    protected Sonic2ObjectPlacement createObjectPlacement(RomByteReader romReader) {
        return new Kis2ObjectPlacement(sk, romReader, LockOnAddressSpace.tierOne(sk, romReader));
    }

    @Override
    protected Palette characterPaletteOverride() {
        return playerArt().loadKnucklesPalette();
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
            return playerArt().loadKnucklesPalette();
        }
        return super.loadCharacterPalette(characterCode);
    }

    Kis2PlayerArt playerArt() {
        if (playerArt == null) {
            playerArt = new Kis2PlayerArt(sk);
        }
        return playerArt;
    }

    private static boolean isKnuckles(String characterCode) {
        return characterCode != null && "knuckles".equals(characterCode.trim().toLowerCase(Locale.ROOT));
    }
}
