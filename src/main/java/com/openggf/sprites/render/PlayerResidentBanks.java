package com.openggf.sprites.render;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;

/**
 * Engine-internal access for owners that model a native player VRAM bank and its
 * DPLC command path themselves (for example a dedicated bonus-stage sprite whose
 * ROM routine selects another character's DPLC table). Not part of the Mod API.
 */
public final class PlayerResidentBanks {
    private PlayerResidentBanks() { }

    /** Exact resident tiles and the renderer's own last-loaded frame. */
    public record Snapshot(Pattern[] tiles, int lastFrame) {
        public Snapshot {
            tiles = copy(tiles);
        }
        @Override public Pattern[] tiles() { return copy(tiles); }
    }

    /** Draws stop issuing implicit DPLC; the caller owns every bank write. */
    public static void claim(PlayerSpriteRenderer renderer) {
        renderer.setResidentBankExternal(true);
    }

    /** Submits one frame's cues from {@code source} into the resident bank. */
    public static void load(PlayerSpriteRenderer renderer, SpriteDplcFrame cues, Pattern[] source) {
        if (cues != null && !cues.requests().isEmpty()) renderer.residentBank().applyRequests(cues.requests(), source);
    }

    public static Snapshot capture(PlayerSpriteRenderer renderer) {
        return new Snapshot(renderer.residentBank().copyResident(), renderer.residentLastFrame());
    }

    /** Restores CPU and GPU tiles plus the last-loaded frame, without issuing cues. */
    public static void restore(PlayerSpriteRenderer renderer, Snapshot snapshot) {
        renderer.residentBank().restoreResident(snapshot.tiles);
        renderer.restoreResidentLastFrame(snapshot.lastFrame());
    }

    /** Re-uploads a bank whose atlas indices another renderer used meanwhile. */
    public static void refreshTextures(PlayerSpriteRenderer renderer) {
        renderer.residentBank().refreshTextures();
    }

    private static Pattern[] copy(Pattern[] tiles) {
        Pattern[] copy = new Pattern[tiles.length];
        for (int i = 0; i < tiles.length; i++) {
            copy[i] = new Pattern();
            copy[i].copyFrom(tiles[i]);
        }
        return copy;
    }
}
