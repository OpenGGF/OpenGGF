package com.openggf.game.patch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** The base game and team requested at a gameplay launch choke point. */
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
}
