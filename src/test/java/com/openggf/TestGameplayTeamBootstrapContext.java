package com.openggf;

import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.patch.GameplayTeamAvailability;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.GameplayTeamBootstrap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class TestGameplayTeamBootstrapContext {
    @Test
    void publishesAttachedModeBeforeTeamBootstrapAndLevelLoad() throws Exception {
        List<String> order = new ArrayList<>();
        com.openggf.game.session.GameplayModeContext mode =
                mock(com.openggf.game.session.GameplayModeContext.class);

        String team = GameplayTeamBootstrapContext.publishBootstrapAndLoad(
                mode,
                published -> order.add(published == mode ? "publish" : "wrong-mode"),
                () -> {
                    order.add("availability");
                    assertEquals(List.of("publish", "availability"), order);
                    order.add("character-factory");
                    assertEquals(List.of("publish", "availability", "character-factory"), order);
                    return "team";
                },
                created -> order.add("prepare-" + created),
                () -> {
                    order.add("level-load");
                    assertEquals(List.of("publish", "availability", "character-factory",
                                    "prepare-team", "level-load"),
                            order);
                });

        assertEquals("team", team);
    }

    @Test
    void configuredAvailabilitySupplierReturningNullIsRejected() {
        GameplayTeamBootstrapContext context = new GameplayTeamBootstrapContext(() -> null);

        assertThrows(NullPointerException.class,
                () -> context.resolveAvailability(PlayableCharacterRegistry.empty()));
    }

    @Test
    void registryOnlyModeDerivesAvailabilityFromRegistry() {
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty();
        GameplayTeamBootstrapContext context = GameplayTeamBootstrapContext.registryOnly();

        var actual = context.resolveAvailability(registry);

        assertFalse(actual.isKnownOwner("missing"));
        assertFalse(actual.isEnabledOwner("missing"));
    }

    @Test
    void requiredLaunchTeamUsesResolvedRegistryAndCopiesOnlyLaunchContext() {
        SaveSessionContext durable = SaveSessionContext.noSave("s3k",
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        GameplayLaunchTeam required = new GameplayLaunchTeam(CharacterKey.TAILS, List.of());
        PlayableCharacterRegistry registry = new Sonic2GameModule().getPlayableCharacterRegistry();

        SaveSessionContext launch = GameplayTeamAvailability.requireForLaunch(
                durable, required, registry);

        assertNotSame(durable, launch);
        assertEquals(new SelectedTeam("tails", List.of()), launch.selectedTeam());
        assertEquals(new SelectedTeam("sonic", List.of("tails")), durable.selectedTeam());
        assertThrows(GameplayTeamBootstrap.UnavailableRequiredCharacter.class,
                () -> GameplayTeamAvailability.requireForLaunch(durable,
                        new GameplayLaunchTeam(CharacterKey.mod("missing", "hero"), List.of()),
                        registry));
        assertThrows(GameplayTeamBootstrap.UnavailableRequiredCharacter.class,
                () -> GameplayTeamAvailability.requireForLaunch(durable,
                        new GameplayLaunchTeam(CharacterKey.TAILS,
                                List.of(CharacterKey.mod("missing", "sidekick"))), registry));
    }
}
