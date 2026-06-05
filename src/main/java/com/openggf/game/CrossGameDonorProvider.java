package com.openggf.game;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.RomByteReader;
import com.openggf.data.SpindashDustArtProvider;
import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;

import java.io.IOException;

/**
 * Module-owned provider for assets and behavior a game can donate to a
 * cross-game host.
 */
public interface CrossGameDonorProvider {
    DonorCapabilities getDonorCapabilities();

    PlayerSpriteArtProvider createPlayerArtProvider(RomByteReader reader);

    default SpindashDustArtProvider createSpindashDustArtProvider(RomByteReader reader) {
        return null;
    }

    GameAudioProfile getAudioProfile();

    default Palette loadCharacterPalette(RomByteReader reader, String characterCode) {
        return null;
    }

    default Palette loadHostCompatiblePalette(RomByteReader reader, String characterCode) {
        return null;
    }

    default SuperStateController createSuperStateController(AbstractPlayableSprite player) {
        return null;
    }

    default boolean hasSeparateTailsTailArt() {
        return false;
    }

    default SpriteArtSet loadTailsTailArt(RomByteReader reader) throws IOException {
        return SpriteArtSet.EMPTY;
    }

    default SpriteArtSet loadInstaShieldArt(RomByteReader reader) throws IOException {
        return SpriteArtSet.EMPTY;
    }
}
