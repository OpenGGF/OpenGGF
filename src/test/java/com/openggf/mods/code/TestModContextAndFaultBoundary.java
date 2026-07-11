package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
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

    private record Patch(String id, String baseGameId) implements GamePatch {
        @Override public String displayName() { return id; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
    }
}
