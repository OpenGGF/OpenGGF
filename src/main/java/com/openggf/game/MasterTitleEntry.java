package com.openggf.game;

import java.util.Objects;

/** Immutable master-title selection model for stock and standalone games, and a suspended one. */
public sealed interface MasterTitleEntry
        permits MasterTitleEntry.Stock, MasterTitleEntry.Standalone, MasterTitleEntry.Suspended {
    String displayName();
    String menuLabel();
    String gameId();

    default boolean standalone() { return this instanceof Standalone; }

    record Stock(MasterTitleScreen.GameEntry game) implements MasterTitleEntry {
        public Stock { Objects.requireNonNull(game, "game"); }
        @Override public String displayName() { return game.displayName; }
        @Override public String menuLabel() { return game.menuLabel; }
        @Override public String gameId() { return game.gameId; }
    }

    record Standalone(String owner, String displayName, boolean continueAvailable)
            implements MasterTitleEntry {
        public Standalone {
            owner = ModKeySyntax.requireManifestId(owner);
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("Standalone display name is required");
            }
        }
        @Override public String menuLabel() { return displayName; }
        @Override public String gameId() { return owner; }
    }

    /**
     * A stock game left with a held Escape and kept in memory. Its card sits first on the shelf
     * and resumes the game; it is never launched, so it has no {@link Launch}. {@code inLevel}
     * says it was paused in a level, where a new aspect can take effect on resume.
     */
    record Suspended(Stock game, String location, boolean inLevel) implements MasterTitleEntry {
        public Suspended {
            Objects.requireNonNull(game, "game");
            location = location == null ? "" : location;
        }
        @Override public String displayName() { return game.displayName(); }
        @Override public String menuLabel() { return game.menuLabel(); }
        @Override public String gameId() { return game.gameId(); }
    }

    enum Action { NEW_GAME, CONTINUE }

    record Launch(MasterTitleEntry entry, Action action) {
        public Launch {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(action, "action");
            if (entry instanceof Stock && action != Action.NEW_GAME) {
                throw new IllegalArgumentException("Stock master-title entries use the normal launch path");
            }
            if (entry instanceof Standalone standalone
                    && action == Action.CONTINUE && !standalone.continueAvailable()) {
                throw new IllegalArgumentException("Continue is unavailable for this standalone entry");
            }
        }
    }
}
