package com.openggf.mods.scene;

/** A song section's selected logical musical channels, starting in the song sample coordinate. */
@com.openggf.game.ModApi
public record SceneMusicPart(long onsetSamples, int fmMask, int psgMask, boolean dacMuted) {
    public SceneMusicPart {
        if (onsetSamples < 0 || (fmMask & ~0x3F) != 0 || (psgMask & ~0x0F) != 0) {
            throw new IllegalArgumentException("invalid musical part selection");
        }
    }
}
