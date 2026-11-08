package com.openggf.game.arena;

import com.openggf.control.PlayerInputState;
import com.openggf.game.arena.TrackGeometry;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-owned native arena, with no cartridge-save authority. */
public interface HostedArenaSession extends AutoCloseable {
    ArenaStep step(PlayerInputState input);
    TrackGeometry geometry();
    AbstractPlayableSprite localPlayer();
    boolean resultsRequested();
    boolean trackFinished();
    /** Native death-to-active transition observed in the most recent step. */
    default boolean respawned() { return false; }
    default RaceSwapApplier raceSwaps() { return null; }
    void draw();
    GhostArtSource ghostArtSource();
    @Override void close();
}
