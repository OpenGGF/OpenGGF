package com.openggf.game;

import java.util.List;
import java.util.Objects;

/** Immutable team selected for one gameplay launch without changing durable selection state. */
@ModApi
public record GameplayLaunchTeam(CharacterKey main, List<CharacterKey> sidekicks) {
    public GameplayLaunchTeam {
        Objects.requireNonNull(main, "main");
        sidekicks = List.copyOf(Objects.requireNonNull(sidekicks, "sidekicks"));
    }
}
