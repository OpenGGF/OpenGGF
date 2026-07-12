package com.openggf.game.session;

import com.openggf.game.GameModule;
import com.openggf.game.GameDataSource;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.level.Level;

import java.util.Objects;

@com.openggf.game.ModApi
public final class WorldSession {
    private final GameModule rootGameModule;
    private final GameModule gameModule;
    private final SaveSessionContext saveSessionContext;
    private final GameDataSource dataSource;
    private final PlayableCharacterRegistry playableCharacterRegistry;

    // Loaded-level metadata. Owned by WorldSession because the loaded zone/act
    // identity must survive editor mode swaps (per
    // docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md).
    // LevelManager mirrors these via write-through during level load; the
    // mirror exists for fast internal reads without touching session API.
    private int currentZone = 0;
    private int currentAct = 0;
    private int apparentAct = 0;
    // The currently-loaded Level (full record: tilemap, blocks, palettes,
    // mutable layout). Lives on WorldSession because the loaded world data
    // must survive editor mode swaps. LevelManager mirrors this via
    // write-through during level load; new LevelManagers inherit it on
    // construction.
    private Level currentLevel;
    PatternWindowState patternWindowState = PatternWindowState.EMPTY;

    public WorldSession(GameModule gameModule) {
        this(gameModule, null);
    }

    public WorldSession(GameModule gameModule, SaveSessionContext saveSessionContext) {
        this(gameModule, gameModule, saveSessionContext);
    }

    public WorldSession(GameModule rootGameModule, GameModule resolvedGameModule,
            SaveSessionContext saveSessionContext) {
        this(rootGameModule, resolvedGameModule,
                new MissingDataSource("legacy:" + rootGameModule.getIdentifier()),
                saveSessionContext);
    }

    public WorldSession(GameModule rootGameModule, GameModule resolvedGameModule,
            GameDataSource dataSource, SaveSessionContext saveSessionContext) {
        this.rootGameModule = Objects.requireNonNull(rootGameModule, "rootGameModule");
        this.gameModule = Objects.requireNonNull(resolvedGameModule, "resolvedGameModule");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        PlayableCharacterRegistry registry = resolvedGameModule.getPlayableCharacterRegistry();
        this.playableCharacterRegistry = registry == null
                ? PlayableCharacterRegistry.empty()
                : registry;
        this.saveSessionContext = saveSessionContext;
    }

    public GameModule rootGameModule() {
        return rootGameModule;
    }

    public GameModule resolvedGameModule() {
        return gameModule;
    }

    /**
     * Returns the active gameplay module owned by this world session.
     */
    public GameModule getGameModule() {
        return gameModule;
    }

    public GameDataSource getDataSource() { return dataSource; }

    /** Returns the one immutable registry view pinned when this session opened. */
    public PlayableCharacterRegistry getPlayableCharacterRegistry() {
        return playableCharacterRegistry;
    }

    /**
     * Returns the save session context associated with this world session,
     * or null if no save context was provided (e.g., non-save gameplay).
     */
    public SaveSessionContext getSaveSessionContext() {
        return saveSessionContext;
    }

    public int getCurrentZone() {
        return currentZone;
    }

    public int getCurrentAct() {
        return currentAct;
    }

    public int getApparentAct() {
        return apparentAct;
    }

    public void setCurrentZone(int currentZone) {
        this.currentZone = currentZone;
    }

    public void setCurrentAct(int currentAct) {
        this.currentAct = currentAct;
    }

    public void setApparentAct(int apparentAct) {
        this.apparentAct = apparentAct;
    }

    /**
     * Returns the currently-loaded {@link Level}, or {@code null} if no level
     * has been loaded into this session yet.
     */
    public Level getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(Level currentLevel) {
        this.currentLevel = currentLevel;
    }

    /** Internal placeholder for legacy callers that have not reached the B1 boot seam yet. */
    private record MissingDataSource(String identity) implements GameDataSource {
        private MissingDataSource {
            Objects.requireNonNull(identity, "identity");
            if (identity.isBlank()) throw new IllegalArgumentException("identity must not be blank");
        }

        @Override public java.util.Optional<com.openggf.data.Rom> rom() {
            return java.util.Optional.empty();
        }

        @Override public java.io.InputStream openAsset(String normalizedPath) throws java.io.IOException {
            Objects.requireNonNull(normalizedPath, "normalizedPath");
            throw new java.io.IOException("Missing legacy data source does not expose named assets");
        }
    }

}
