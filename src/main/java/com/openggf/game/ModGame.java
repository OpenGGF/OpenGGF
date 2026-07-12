package com.openggf.game;

import com.openggf.audio.GameSound;
import com.openggf.data.Game;
import com.openggf.data.Rom;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Minimal no-ROM game base; creators provide level and music loading only. */
@ModApi
public abstract class ModGame extends Game {
    private final String identifier;
    private final GameDataSource source;

    protected ModGame(String identifier, GameDataSource source) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.source = Objects.requireNonNull(source, "source");
    }

    protected final GameDataSource dataSource() { return source; }
    @Override public final Rom getRom() { return null; }
    @Override public boolean isCompatible() { return true; }
    @Override public String getIdentifier() { return identifier; }
    @Override public List<String> getTitleCards() { return List.of(); }
    @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
    @Override public boolean canRelocateLevels() { return false; }
    @Override public boolean canSave() { return false; }
    @Override public boolean relocateLevels(boolean unsafe) { return false; }
    @Override public boolean save(int levelIdx, com.openggf.level.Level level) { return false; }
    @Override public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) {
        return new int[]{cameraX, cameraY};
    }
}
