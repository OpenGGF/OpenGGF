package com.openggf.game.arena;

import com.openggf.control.PlayerInputState;
import com.openggf.mods.run.TrackGeometry;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-owned native arena, with no cartridge-save authority. */
public interface HostedArenaSession extends AutoCloseable {
    ArenaStep step(PlayerInputState input);
    TrackGeometry geometry();
    AbstractPlayableSprite localPlayer();
    boolean resultsRequested();
    boolean trackFinished();
    default RaceSwapApplier raceSwaps() { return null; }
    void draw();
    GhostArtSource ghostArtSource();
    @Override void close();
}
