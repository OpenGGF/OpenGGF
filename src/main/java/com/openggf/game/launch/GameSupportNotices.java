package com.openggf.game.launch;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Which stock games still carry a support notice, and which notices the player has hidden.
 * Starting a listed game from the hub first shows {@link com.openggf.game.GameSupportNoticeDialog};
 * Back returns to the hub, Continue starts the game. To gate a game, add its game ID to
 * {@link #DEFAULT_STAGES}; to release it, remove the entry.
 *
 * <p>A dismissal is stored per game and stage, so moving a game from alpha to beta shows the
 * new notice once even to players who hid the alpha one.
 */
public final class GameSupportNotices {
    private static final Logger LOGGER = Logger.getLogger(GameSupportNotices.class.getName());

    /** Support stage named by the notice; the code selects the catalog wording. */
    public enum Stage {
        ALPHA("alpha"),
        BETA("beta");

        private final String code;

        Stage(String code) { this.code = code; }

        public String code() { return code; }
    }

    /** Stock games that are playable but not yet ready, keyed by game ID. */
    public static final Map<String, Stage> DEFAULT_STAGES = Map.of("s3k", Stage.BETA);

    private final SonicConfigurationService config;
    private final Map<String, Stage> stages;

    public GameSupportNotices(SonicConfigurationService config) { this(config, DEFAULT_STAGES); }

    public GameSupportNotices(SonicConfigurationService config, Map<String, Stage> stages) {
        this.config = Objects.requireNonNull(config, "config");
        this.stages = Map.copyOf(stages);
    }

    /** The stage of a gated game, or empty when it starts without a notice. */
    public Optional<Stage> stageOf(String gameId) {
        return gameId == null ? Optional.empty() : Optional.ofNullable(stages.get(gameId.toLowerCase(Locale.ROOT)));
    }

    /** The notice to show before starting {@code gameId}, or empty when it is ungated or hidden. */
    public Optional<Stage> pendingNotice(String gameId) {
        return stageOf(gameId).filter(stage -> !dismissed().contains(token(gameId, stage)));
    }

    /** Remembers Don't show again for this game and stage. A failed save only logs: the game still starts. */
    public void dismiss(String gameId, Stage stage) {
        Set<String> next = dismissed();
        if (!next.add(token(gameId, stage))) return;
        try {
            ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.HUB_DISMISSED_NOTICES, String.join(",", next)));
        } catch (IOException failure) {
            LOGGER.warning("Could not save dismissed support notice: " + failure.getMessage());
        }
    }

    private Set<String> dismissed() {
        String stored = config.getString(SonicConfiguration.HUB_DISMISSED_NOTICES);
        Set<String> tokens = new LinkedHashSet<>();
        if (stored == null) return tokens;
        Arrays.stream(stored.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).forEach(tokens::add);
        return tokens;
    }

    static String token(String gameId, Stage stage) {
        return gameId.toLowerCase(Locale.ROOT) + ":" + stage.code();
    }
}
