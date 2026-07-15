package com.openggf.game.patch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.save.SelectedTeam;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** The base game and team requested at a gameplay launch choke point. */
@com.openggf.game.ModApi
public record GameplayLaunchRequest(String gameId, String mainCharacter, List<String> sidekicks) {

    public GameplayLaunchRequest {
        Objects.requireNonNull(gameId, "gameId");
        mainCharacter = mainCharacter == null || mainCharacter.isBlank()
                ? "sonic"
                : mainCharacter.trim().toLowerCase(Locale.ROOT);
        sidekicks = sidekicks == null ? List.of() : List.copyOf(sidekicks);
    }

    /** Builds a request from the live, post-launch-profile configuration. */
    public static GameplayLaunchRequest fromConfig(
            SonicConfigurationService configService, String gameId) {
        Objects.requireNonNull(configService, "configService");
        String main = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        String sidekickCsv = configService.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        List<String> requestedSidekicks = sidekickCsv == null
                || sidekickCsv.isBlank()
                || "none".equalsIgnoreCase(sidekickCsv.trim())
                ? List.of()
                : Arrays.stream(sidekickCsv.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        return new GameplayLaunchRequest(gameId, main, requestedSidekicks);
    }

    /** Builds a patch-resolution request from a data-select/save team snapshot. */
    public static GameplayLaunchRequest fromSelectedTeam(String gameId, SelectedTeam team) {
        Objects.requireNonNull(team, "team");
        return new GameplayLaunchRequest(gameId, team.mainCharacter(), team.sidekicks());
    }

    /** Returns the request as exact character keys for resolved-registry validation. */
    public GameplayLaunchTeam team() {
        return new GameplayLaunchTeam(parseKey(mainCharacter),
                sidekicks.stream().map(GameplayLaunchRequest::parseKey).toList());
    }

    private static CharacterKey parseKey(String persisted) {
        String canonical = persisted.equalsIgnoreCase("sonic")
                || persisted.equalsIgnoreCase("tails")
                || persisted.equalsIgnoreCase("knuckles")
                ? persisted.toLowerCase(Locale.ROOT) : persisted;
        return CharacterKey.parsePersisted(canonical);
    }
}
