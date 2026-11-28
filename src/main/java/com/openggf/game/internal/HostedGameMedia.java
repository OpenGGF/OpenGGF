package com.openggf.game.internal;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A stock game hosted from user media other than a cartridge ROM (Sonic CD's disc image). The
 * module registers it with {@link GameModule#getGameService(Class)}; {@link
 * com.openggf.game.GameModuleRegistry#mediaKind} names which stock games have one. The launcher
 * lists such a game on the master title from {@link #probe} and boots it from {@link
 * #openConfiguredMedia}; offline tools that pick a game by its id (trace capture from a trace's
 * {@code game}, gameplay capture from {@code --game}) open the media and name its levels through it.
 * A module without it is booted from its cartridge ROM as before.
 */
public interface HostedGameMedia {
    /** One engine level: the zone index and act index {@code LevelManager.loadZoneAndAct} takes. */
    record Level(int zone, int act) { }

    /** Whether the configured media is present and looks right, and what to tell the player. */
    record Availability(boolean available, String detail) { }

    /**
     * What a user-chosen file is, judged as cheaply as {@link #probe}: its layout and header, never a
     * whole-media hash. {@code READY} media is identified, not verified; verification happens when the
     * game opens it.
     */
    enum MediaKind {
        /** The supported release, identified by its header and layout. */
        READY,
        /** This game, but a release the engine does not play. */
        OTHER_RELEASE,
        /** This game's media with parts missing (for a disc, tracks). */
        INCOMPLETE,
        /** An image of this kind that could not be read or is in an unsupported container. */
        UNREADABLE,
        /** Not this game's media. */
        NOT_THIS_GAME
    }

    /** One inspected file: its kind and the exact English diagnostic, for logs and the details reader. */
    record Inspection(MediaKind kind, String detail) {
        public Inspection {
            java.util.Objects.requireNonNull(kind, "kind");
            detail = detail == null ? "" : detail;
        }
    }

    /** Whether {@code file} is a candidate this game's {@link #inspect} should look at (by name only). */
    default boolean candidate(Path file) { return false; }

    /** Inspects one candidate file; never throws for a missing, foreign or damaged image. */
    default Inspection inspect(Path file) { return new Inspection(MediaKind.NOT_THIS_GAME, ""); }

    /** The game's full name, for the master title's header. */
    String displayName();

    /** The game's short name, for its card. */
    String menuLabel();

    /** The one-based act shown to players; runtime indices may also encode a time period. */
    default int displayAct(int zone, int act) { return act + 1; }

    /** A cheap look at the configured media (no whole-media hashing), for listing the game. */
    Availability probe(SonicConfigurationService config);

    /**
     * Opens and verifies the media the configuration names. The caller owns and closes the source.
     *
     * @throws IOException saying what is wrong when the media is absent, unreadable or not the
     *         release the game is built for
     */
    GameDataSource openConfiguredMedia(SonicConfigurationService config) throws IOException;

    /** Exact configured input path for isolated authoring workers. */
    default Path configuredPath(SonicConfigurationService config) throws IOException {
        throw new IOException(displayName() + " has no authoring source locator");
    }

    /** Opens and verifies an explicitly supplied offline source. The caller owns it. */
    default GameDataSource openMedia(Path path) throws IOException {
        throw new IOException(displayName() + " does not support explicit offline media");
    }

    /** {@link #openConfiguredMedia(SonicConfigurationService)} with the process configuration. */
    default GameDataSource openConfiguredMedia() throws IOException {
        return openConfiguredMedia(com.openggf.game.GameServices.configuration());
    }

    /** Optional native menu theme and its owned media lifetime. */
    default java.util.Optional<HostedMenuMusic> menuMusic(SonicConfigurationService config) throws IOException {
        return java.util.Optional.empty();
    }

    /** Detached title pixels; all media reads finish before returning. */
    default java.util.Optional<com.openggf.game.preview.TitlePreviewProvider.Sequence> titlePreview(
            SonicConfigurationService config) throws IOException {
        return java.util.Optional.empty();
    }

    /** The level a recorded trace's directory starts in, read from its {@code metadata.json}. */
    Level levelOfTrace(Path traceDir) throws IOException;

    /**
     * The level a capture command names: a zone by name or number with a one-based act, or a
     * stage name that carries its own act (the act is then ignored).
     */
    Level levelNamed(String zone, int oneBasedAct);

    /**
     * A clip that runs past a trace's last row hands the game to its production loop, so the hosted
     * game's own flow (a warp, a stage change) decides what follows. A game whose stage ends only
     * through its program flow declares here how the recorded run entered the stage, so that flow
     * knows a stage is running. Called once, before the first row past the trace.
     *
     * @throws IllegalStateException when the loaded stage has no such declaration
     */
    default void declareRunPastTrace(GameModule module) {
        throw new IllegalStateException(displayName() + " has no declaration for a clip past the trace's last row");
    }

    /** Whether a trace's loaded state is its first row (the load runs the main loop's first iteration). */
    static boolean loadIsFirstTraceRow(GameModule module) {
        return LoadedLevelFrameCounter.forHost(module) > 0;
    }

    static HostedGameMedia of(GameModule module) {
        return module == null ? null : module.getGameService(HostedGameMedia.class);
    }
}
