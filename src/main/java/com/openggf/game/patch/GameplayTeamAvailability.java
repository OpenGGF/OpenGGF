package com.openggf.game.patch;

import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SaveSessionLaunchTeamAccess;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.session.GameplayTeamBootstrap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/** Sanitizes only the in-memory launch context; the save slot is never rewritten. */
public final class GameplayTeamAvailability {
    private static final Logger LOGGER = Logger.getLogger(GameplayTeamAvailability.class.getName());

    private GameplayTeamAvailability() {
    }

    public static SaveSessionContext sanitizeForLaunch(SaveSessionContext context,
            String gameId, List<String> availableCharacters) {
        if (context == null) {
            return null;
        }
        Objects.requireNonNull(gameId, "gameId");
        Set<String> available = new LinkedHashSet<>();
        for (String character : Objects.requireNonNull(availableCharacters, "availableCharacters")) {
            available.add(character.toLowerCase(Locale.ROOT));
        }
        SelectedTeam team = context.selectedTeam();
        String requestedMain = team.mainCharacter().toLowerCase(Locale.ROOT);
        String main = available.contains(requestedMain) ? requestedMain : "sonic";
        List<String> sidekicks = team.sidekicks().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(available::contains)
                .toList();
        if (main.equals(team.mainCharacter()) && sidekicks.equals(team.sidekicks())) {
            return context;
        }
        LOGGER.warning("Launch team for " + gameId + " requested unavailable characters; "
                + "using session-only team main=" + main + ", sidekicks=" + sidekicks);
        return context.withSelectedTeam(new SelectedTeam(main, sidekicks));
    }

    /** Validates and installs one required team into a copied launch context without fallback. */
    public static SaveSessionContext requireForLaunch(SaveSessionContext context,
            GameplayLaunchTeam requestedTeam, PlayableCharacterRegistry resolvedRegistry) {
        Objects.requireNonNull(context, "context");
        GameplayTeamBootstrap.requireExactTeam(resolvedRegistry, requestedTeam);
        return SaveSessionLaunchTeamAccess.withLaunchTeam(context, requestedTeam);
    }
}
