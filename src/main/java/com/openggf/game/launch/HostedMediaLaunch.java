package com.openggf.game.launch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.internal.DurableSaveHost;
import com.openggf.game.internal.HostedGameMedia;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A player's launch of a stock game hosted from media other than a cartridge ROM
 * ({@link GameModuleRegistry.MediaKind#HOSTED_MEDIA}). The engine asks it before its ROM boot: when
 * the selected game is hosted, it opens and verifies the game's media through the module's {@link
 * HostedGameMedia}, attaches the game's durable saves ({@link DurableSaveHost}) to the save root, and
 * keeps the media open until the gameplay runtime is torn down.
 */
public final class HostedMediaLaunch implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(HostedMediaLaunch.class.getName());

    /** The opened game: its stock module and the session's data source. */
    public record Opened(GameModule module, GameDataSource source) { }

    private GameDataSource open;

    /** The hosted game the configured game code names, or empty for a cartridge game or an unknown code. */
    public static Optional<GameId> hostedGame(String configuredGame) {
        if (configuredGame == null || configuredGame.isBlank()) return Optional.empty();
        GameId id;
        try {
            id = GameId.fromCode(configuredGame.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
        return id != GameId.STANDALONE && GameModuleRegistry.mediaKind(id) == GameModuleRegistry.MediaKind.HOSTED_MEDIA
                ? Optional.of(id) : Optional.empty();
    }

    /**
     * Opens {@code game}'s configured media for a player's session; any media this launch still holds
     * is closed first.
     *
     * @throws IOException saying why the media was refused (missing, unreadable, another release)
     */
    public Opened open(GameId game, SonicConfigurationService config, Path saveRoot) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(saveRoot, "saveRoot");
        close();
        GameModule module = GameModuleRegistry.createStockModule(game);
        HostedGameMedia media = HostedGameMedia.of(module);
        if (media == null) throw new IOException(game + " registers no hosted media");
        GameDataSource source = media.openConfiguredMedia(config);
        DurableSaveHost saves = module.getGameService(DurableSaveHost.class);
        if (saves != null) saves.attachDurableSaves(saveRoot);
        open = source;
        LOGGER.info("Opened " + media.displayName() + " from " + source.identity());
        return new Opened(module, source);
    }

    /** Closes the media of the last launch, if any. */
    @Override
    public void close() {
        GameDataSource held = open;
        open = null;
        if (held instanceof java.io.Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException failure) {
                LOGGER.warning("Could not close hosted media: " + failure.getMessage());
            }
        }
    }
}
