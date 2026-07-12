package com.openggf;

import com.openggf.game.PlayableCharacterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
