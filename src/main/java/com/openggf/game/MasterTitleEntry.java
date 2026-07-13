package com.openggf.game;

import java.util.Objects;

/** Immutable master-title selection model for stock and standalone games. */
@ModApi
public sealed interface MasterTitleEntry permits MasterTitleEntry.Stock, MasterTitleEntry.Standalone {
    String displayName();
    String menuLabel();
    String gameId();

    default boolean standalone() { return this instanceof Standalone; }

    @ModApi
    record Stock(MasterTitleScreen.GameEntry game) implements MasterTitleEntry {
        public Stock { Objects.requireNonNull(game, "game"); }
        @Override public String displayName() { return game.displayName; }
        @Override public String menuLabel() { return game.menuLabel; }
        @Override public String gameId() { return game.gameId; }
    }

    @ModApi
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

    @ModApi
    enum Action { NEW_GAME, CONTINUE }

    @ModApi
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
