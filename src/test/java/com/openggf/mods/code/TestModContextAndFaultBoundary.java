package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.game.CharacterKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TestModContextAndFaultBoundary {
    @Test
    void transactionNamespacesContentRejectsDuplicatesAndFreezesAtomically() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        context.registerObject("buzzer", (spawn, registry) -> null);
        context.registerObjectArt("buzzer", new BakedSheetRef("art/buzzer.ggfsheet"));
        context.registerGamePatch(new Patch("extra", "s2"));

        ModRegistrationPlan plan = context.freeze();
        assertEquals(Set.of("owner:buzzer"), plan.objectFactories().keySet());
        assertEquals(Set.of("owner:buzzer"), plan.objectArt().keySet());
        assertEquals(1, plan.explicitPatches().size());
        assertEquals("owner:extra", plan.explicitPatches().get(0).id());
        assertThrows(ModRegistrationException.class,
                () -> context.registerObject("later", (spawn, registry) -> null));

        ModContext badBase = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        assertThrows(ModRegistrationException.class,
                () -> badBase.registerGamePatch(new Patch("extra", "s1")));
        ModContext duplicate = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        duplicate.registerObject("same", (spawn, registry) -> null);
        assertThrows(ModRegistrationException.class,
                () -> duplicate.registerObject("same", (spawn, registry) -> null));
        assertThrows(ModRegistrationException.class,
                () -> duplicate.registerGamePatch(new Patch("other:foreign", "s2")));
    }

    @Test
    void firstRejectedMutationPermanentlyPoisonsTransactionAndPlanKeepsInsertionOrder() {
        ModContext poisoned = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        poisoned.registerObject("first", (spawn, registry) -> null);
        poisoned.registerObject("second", (spawn, registry) -> null);
        ModRegistrationException firstFailure = assertThrows(ModRegistrationException.class,
                () -> poisoned.registerObject("first", (spawn, registry) -> null));
        assertSame(firstFailure, assertThrows(ModRegistrationException.class,
                () -> poisoned.registerObjectArt("art", new BakedSheetRef("art/a.bin"))));
        assertSame(firstFailure, assertThrows(ModRegistrationException.class, poisoned::freeze));

        ModContext ordered = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        ordered.registerObject("z-last", (spawn, registry) -> null);
        ordered.registerObject("a-first", (spawn, registry) -> null);
        ordered.registerObjectArt("z-last", new BakedSheetRef("art/z.bin"));
        ordered.registerObjectArt("a-first", new BakedSheetRef("art/a.bin"));
        ModRegistrationPlan plan = ordered.freeze();
        assertEquals(List.of("owner:z-last", "owner:a-first"),
                new ArrayList<>(plan.objectFactories().keySet()));
        assertEquals(List.of("owner:z-last", "owner:a-first"),
                new ArrayList<>(plan.objectArt().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.objectFactories().clear());
    }

    @Test
    void characterCollisionPoisonsTransactionAndCannotPublishPartialRegistry() {
        ModContext context = new ModContext("owner", "s2", ModAssetRoot.forTests("owner"));
        var key = CharacterKey.mod("owner", "runner");
        var definition = new com.openggf.game.CharacterDefinition(key, "Runner",
                (code, x, y) -> null, null, com.openggf.game.PlayerCharacter.SONIC_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE, false, code -> null);
        context.registerCharacter("runner", definition);
        ModRegistrationException collision = assertThrows(ModRegistrationException.class,
                () -> context.registerCharacter("runner", definition));
        assertSame(collision, assertThrows(ModRegistrationException.class, context::freeze));
        GameModule base = org.mockito.Mockito.mock(GameModule.class);
        org.mockito.Mockito.when(base.getPlayableCharacterRegistry())
                .thenReturn(com.openggf.game.PlayableCharacterRegistry.empty());
        assertTrue(base.getPlayableCharacterRegistry().definitions().isEmpty(),
                "A poisoned transaction never reaches patch publication");
    }

    @Test
    void callbackFailureDisablesClosurePublishesFindingAndThrowsTypedAbort() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> persisted = new ArrayList<>();
        List<Set<String>> processDisabled = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(
                Map.of("dependent", Set.of("owner"), "transitive", Set.of("dependent")),
                findings, owners -> { persisted.add(owners); return new ModStateSaveResult.Saved(); },
                processDisabled::add);

        IllegalArgumentException cause = new IllegalArgumentException("boom");
        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.run("owner", () -> { throw cause; }));
        assertSame(cause, aborted.getCause());
        assertEquals(Set.of("owner", "dependent", "transitive"), aborted.disabledOwners());
        assertEquals(List.of(aborted.disabledOwners()), persisted);
        assertEquals(persisted, processDisabled);
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").get(0).code());
    }

    @Test
    void ownerDerivedCharacterAndStandaloneBoundariesPreserveDirectAndAbortSignals() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> saved = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(
                Map.of("dependent", Set.of("owner")), findings,
                owners -> { saved.add(owners); return new ModStateSaveResult.Saved(); }, owners -> {});
        assertEquals("builtin", boundary.callCharacter(CharacterKey.SONIC, () -> "builtin"));
        var characterAbort = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.callCharacter(CharacterKey.mod("owner", "runner"),
                        () -> { throw new IllegalStateException("character"); }));
        assertEquals(Set.of("owner", "dependent"), characterAbort.disabledOwners());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
        var standaloneAbort = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.callStandalone("standalone", () -> { throw new IllegalStateException("game"); }));
        assertEquals(Set.of("standalone"), standaloneAbort.disabledOwners());
        assertEquals(List.of(Set.of("owner", "dependent"), Set.of("standalone")), saved);
    }

    @Test
    void saveFailureIsSuppressedAndVmFatalFailuresEscapeWithoutSideEffects() {
        AtomicBoolean disabled = new AtomicBoolean();
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Failed("disk full"), owners -> disabled.set(true));
        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.run("owner", () -> { throw new IllegalStateException("callback"); }));
        assertTrue(disabled.get());
        assertEquals(1, aborted.getCause().getSuppressed().length);
        assertEquals(List.of("MOD_CALLBACK_FAILED", "MOD_DISABLE_SAVE_FAILED"),
                findings.findingsFor("owner").stream().map(com.openggf.mods.ModFinding::code).toList());

        disabled.set(false);
        assertThrows(OutOfMemoryError.class,
                () -> boundary.run("owner", () -> { throw new OutOfMemoryError("fatal"); }));
        assertFalse(disabled.get());
    }

    @Test
    void runtimeFindingUpsertPreservesUnrelatedOwnerBadges() {
        ModRuntimeFindingStore store = new ModRuntimeFindingStore();
        store.replaceOwner("owner", List.of(new com.openggf.mods.ModFinding(
                com.openggf.mods.ModFindingSeverity.WARNING, "EXISTING", "existing warning", null)));
        store.upsertOwnerFinding("owner", new com.openggf.mods.ModFinding(
                com.openggf.mods.ModFindingSeverity.WARNING, "S2_MOD_ZONE_MISSING", "missing", null));
        store.upsertOwnerFinding("owner", new com.openggf.mods.ModFinding(
                com.openggf.mods.ModFindingSeverity.WARNING, "S2_MOD_ZONE_MISSING", "updated", null));

        assertEquals(List.of("EXISTING", "S2_MOD_ZONE_MISSING"),
                store.findingsFor("owner").stream().map(com.openggf.mods.ModFinding::code).toList());
        assertEquals("updated", store.findingsFor("owner").getLast().message());
    }

    private record Patch(String id, String baseGameId) implements GamePatch {
        @Override public String displayName() { return id; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
    }
}
