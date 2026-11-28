package com.openggf.game;

import java.util.Objects;

/** Immutable master-title selection model for stock, hosted-media and standalone games, and a suspended one. */
public sealed interface MasterTitleEntry
        permits MasterTitleEntry.Stock, MasterTitleEntry.Hosted, MasterTitleEntry.Standalone,
        MasterTitleEntry.Suspended, MasterTitleEntry.ModGame, MasterTitleEntry.Library {
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

    /**
     * A stock game hosted from media other than a cartridge ROM ({@link GameModuleRegistry.MediaKind#HOSTED_MEDIA}):
     * its module finds and opens the media ({@link com.openggf.game.internal.HostedGameMedia}). It has no
     * cartridge preview or ROM locator; its media service supplies preview/music and its Setup profile
     * supplies the launch team and donor.
     */
    record Hosted(GameId game, String displayName, String menuLabel) implements MasterTitleEntry {
        public Hosted {
            Objects.requireNonNull(game, "game");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(menuLabel, "menuLabel");
        }

        /** The entry for a hosted stock game, named by its media service. */
        public static Hosted of(GameId game) {
            var media = GameModuleRegistry.hostedMedia(game);
            return new Hosted(game, media.displayName(), media.menuLabel());
        }

        @Override public String gameId() { return game.code(); }
    }

    record ModGame(String owner, String displayName, String baseGame) implements MasterTitleEntry {
        public ModGame {
            owner = ModKeySyntax.requireManifestId(owner);
            Objects.requireNonNull(displayName, "displayName");
            if (!"any".equals(baseGame)) MasterTitleScreen.GameEntry.fromGameId(baseGame);
        }
        @Override public String menuLabel() { return displayName; }
        @Override public String gameId() { return owner; }
    }

    record Library(int count) implements MasterTitleEntry {
        @Override public String displayName() {
            return com.openggf.i18n.UiMessages.text("hub.library.count", java.util.Map.of("count", count));
        }
        @Override public String menuLabel() { return displayName(); }
        @Override public String gameId() { return ""; }
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
     * A cartridge or hosted game left with a held Escape and kept in memory. Its card sits first on the shelf
     * and resumes the game; it is never launched, so it has no {@link Launch}. {@code inLevel}
     * says it was paused in a level, where a new aspect can take effect on resume.
     */
    record Suspended(MasterTitleEntry game, String location, boolean inLevel) implements MasterTitleEntry {
        public Suspended {
            Objects.requireNonNull(game, "game");
            if (!(game instanceof Stock) && !(game instanceof Hosted)) {
                throw new IllegalArgumentException("Only cartridge or hosted games can be suspended");
            }
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
            if (entry instanceof Standalone standalone
                    && action == Action.CONTINUE && !standalone.continueAvailable()) {
                throw new IllegalArgumentException("Continue is unavailable for this standalone entry");
            }
        }
    }
}
