package com.openggf;

import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModRuntimeFindingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestModCharacterFallbackFindings {
    @Test
    void runtimeSinkUsesStableOwnerFindingWithoutClobberingCallbackFailure() {
        ModRuntimeFindingStore store = new ModRuntimeFindingStore();
        store.replaceOwner("owner", List.of(new ModFinding(ModFindingSeverity.ERROR,
                "MOD_CALLBACK_FAILED", "callback", null)));

        ModCharacterFallbackFindings.sink(store).record(new GameplayTeamBootstrap.CharacterFinding(
                "owner:runner", "sonic", PlayableCharacterRegistry.FallbackReason.DISABLED_OWNER));

        assertEquals(List.of("MOD_CALLBACK_FAILED", "MOD_CHARACTER_DISABLED_FALLBACK"),
                store.findingsFor("owner").stream().map(ModFinding::code).toList());
        assertEquals("owner:runner", store.findingsFor("owner").getLast().assetPath());
    }
}
