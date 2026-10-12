package com.openggf.mods.code;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelEventRewindResolver;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestModZoneEventLifecycle {
    static final class Events implements RewindableZoneEvents<Integer> {
        int value;
        int reconciles;
        String fail;
        void check(String name) { if (name.equals(fail)) throw new IllegalStateException(name); }
        public void initLevel(int zone, int act) { value = act + 1; }
        public void update() { value++; }
        public Integer capture() { check("capture"); return value; }
        public void restore(Integer snapshot) { check("restore"); value = snapshot; }
        public void resetForMissingSnapshot() { check("reset"); value = 0; }
        public void reconcileAfterRewindRestore() { check("reconcile"); reconciles++; }
    }
    record Fixture(ModZoneEventProvider provider, RewindRegistry registry,
                   List<Events> created, ModRuntimeFindingStore findings) { }
    Fixture fixture() {
        var created = new ArrayList<Events>();
        var findings = new ModRuntimeFindingStore();
        var boundary = new ModFaultBoundary(Map.of("dependent", Set.of("owner")), findings,
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        PreparedModZone zone = new PreparedModZone("owner", "zone", null, null,
                () -> { Events events = new Events(); created.add(events); return events; },
                "ZONE", 0x400, 0x40, 0, 0);
        var provider = new ModZoneEventProvider(null, 3, List.of(zone), boundary);
        var registry = new RewindRegistry();
        List<RewindSnapshottable<?>> adapters = LevelEventRewindResolver.adapters(provider, 3);
        assertEquals(1, adapters.size(), "Contributed event adapter exists before level-event init");
        adapters.forEach(registry::register);
        registry.registerPostRestoreCallback("events", () -> LevelEventRewindResolver.reconcile(provider, 3));
        return new Fixture(provider, registry, created, findings);
    }
    @Test void eventDecoratorForwardsEveryLevelEventCallbackIncludingFutureDefaults() {
        for (var method : LevelEventProvider.class.getMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
            assertDoesNotThrow(() -> ModZoneEventProvider.class.getDeclaredMethod(method.getName(), method.getParameterTypes()),
                    "Contributed event decorator must forward " + method);
        }
    }

    @Test void stateRoundTripsAndMissingSnapshotsResetBeforeReconcile() {
        Fixture f = fixture();
        f.provider.initLevel(3, 0);
        f.provider.update();
        var captured = f.registry.capture();
        Events events = f.created.getFirst();
        f.provider.update(); f.provider.update();
        f.registry.restore(captured);
        assertEquals(2, events.value);
        assertEquals(1, events.reconciles);
        f.provider.update();
        assertEquals(3, events.value, "Forward replay starts from the restored state");
        f.registry.restore(new CompositeSnapshot(Map.of()));
        assertEquals(0, events.value);
        assertEquals(2, events.reconciles);
    }
    @Test void loadAndRespawnRecreateHandlersButKeepTheEngineOwnedAdapter() {
        Fixture f = fixture();
        Object identity = LevelEventRewindResolver.adapters(f.provider, 3).getFirst();
        f.provider.initLevel(3, 0); f.provider.update();
        f.provider.initLevel(3, 0);
        assertEquals(2, f.created.size());
        assertNotSame(f.created.getFirst(), f.created.getLast());
        assertEquals(1, f.created.getLast().value);
        assertSame(identity, LevelEventRewindResolver.adapters(f.provider, 3).getFirst());
        assertTrue(f.registry.capture().entries().keySet().stream().allMatch(key -> key.startsWith("mod:owner:")));
    }
    @Test void everyStateCallbackRetainsOwnerAndDependentDisable() {
        for (String callback : List.of("capture", "restore", "reset", "reconcile")) {
            Fixture f = fixture();
            f.provider.initLevel(3, 0);
            CompositeSnapshot captured = f.registry.capture();
            f.created.getFirst().fail = callback;
            Runnable action = callback.equals("capture") ? f.registry::capture
                    : callback.equals("reset") ? () -> f.registry.restore(new CompositeSnapshot(Map.of()))
                    : () -> f.registry.restore(captured);
            var failure = assertThrows(ModFaultBoundary.CallbackAborted.class, action::run);
            assertEquals("owner", failure.owner());
            assertEquals(Set.of("owner", "dependent"), failure.disabledOwners());
            assertEquals("MOD_CALLBACK_FAILED", f.findings.findingsFor("owner").getFirst().code());
        }
    }
    @Test void actLoadsCreateIndependentHandlersUnderOneStableZoneEventIdentity() {
        var declared = ModZoneContribution.multiAct("campaign", List.of(
                new BakedLevelRef("one/level.json"), new BakedLevelRef("two/level.json")),
                null, Events::new, false);
        var first = PreparedModZone.prepared("owner", declared, TestModZoneLoader.minimalDefinition(), 0);
        var second = PreparedModZone.prepared("owner", declared, TestModZoneLoader.minimalDefinition(), 1);
        var findings = new ModRuntimeFindingStore();
        var boundary = new ModFaultBoundary(Map.of(), findings,
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        var provider = new ModZoneEventProvider(null, 3, List.of(first, second), boundary);
        var registry = new RewindRegistry();
        var adapter = LevelEventRewindResolver.adapters(provider, 3).getFirst();
        registry.register(adapter);
        provider.defersInitialObjectPlacementUntilAfterLevelEvents(3, 1);
        provider.initLevel(3, 1);
        var snapshot = registry.capture();
        provider.update();
        registry.restore(snapshot);
        assertSame(adapter, LevelEventRewindResolver.adapters(provider, 3).getFirst());
        provider.initLevel(3, 0);
        var mismatch = assertThrows(ModFaultBoundary.CallbackAborted.class, () -> registry.restore(snapshot));
        assertEquals("owner", mismatch.owner(), "Cross-act snapshot hydration fails through its actual owner");
    }

    @Test void gameplayRegistersBeforeInitAndReconcilesAfterWorldRestoration() {
        Fixture f = fixture();
        com.openggf.game.session.EngineServices.configure(
                com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
        var nativeModule = new com.openggf.game.sonic2.Sonic2GameModule();
        var module = new com.openggf.game.patch.DelegatingGameModule(nativeModule, "owner:events") {
            @Override public LevelEventProvider getLevelEventProvider() { return f.provider; }
        };
        var gameplay = new com.openggf.game.session.GameplayModeContext(new com.openggf.game.session.WorldSession(module));
        gameplay.attachGameplayManagers(new com.openggf.camera.Camera(), new com.openggf.timer.TimerManager(),
                new com.openggf.game.GameStateManager(), new com.openggf.graphics.FadeManager(),
                new com.openggf.game.GameRng(com.openggf.game.GameRng.Flavour.S1_S2),
                new com.openggf.game.solid.DefaultSolidExecutionRegistry());
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(level.getGameModule()).thenReturn(module);
        org.mockito.Mockito.when(level.getCurrentZone()).thenReturn(3);
        org.mockito.Mockito.when(level.getTilemapManager()).thenReturn(org.mockito.Mockito.mock(com.openggf.level.LevelTilemapManager.class));
        var levelAdapter = new RewindSnapshottable<com.openggf.game.rewind.snapshot.LevelSnapshot>() {
            public String key() { return "level"; }
            public com.openggf.game.rewind.snapshot.LevelSnapshot capture() {
                return org.mockito.Mockito.mock(com.openggf.game.rewind.snapshot.LevelSnapshot.class);
            }
            public void restore(com.openggf.game.rewind.snapshot.LevelSnapshot ignored) { }
        };
        var tilemapAdapter = new RewindSnapshottable<com.openggf.game.rewind.snapshot.LevelTilemapSnapshot>() {
            public String key() { return "level-tilemap"; }
            public com.openggf.game.rewind.snapshot.LevelTilemapSnapshot capture() {
                return org.mockito.Mockito.mock(com.openggf.game.rewind.snapshot.LevelTilemapSnapshot.class);
            }
            public void restore(com.openggf.game.rewind.snapshot.LevelTilemapSnapshot ignored) { }
        };
        org.mockito.Mockito.when(level.levelRewindSnapshottable()).thenReturn(levelAdapter);
        org.mockito.Mockito.when(level.levelTilemapRewindSnapshottable()).thenReturn(tilemapAdapter);
        gameplay.registerLevelAdapters(level);
        f.provider.initLevel(3, 0); f.provider.update();
        CompositeSnapshot captured = gameplay.getRewindRegistry().capture();
        f.provider.update();
        gameplay.getRewindRegistry().restore(captured);
        assertEquals(2, f.created.getFirst().value);
        assertEquals(1, f.created.getFirst().reconciles);
        gameplay.registerLevelAdapters(level);
        f.provider.initLevel(3, 0);
        assertEquals(2, f.created.size());
        assertEquals(1, f.created.getLast().value);
    }

    @Test void statelessLegacyHandlersRemainUsableAcrossRestoreAndReset() {
        int[] inits = {0};
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        var zone = new PreparedModZone("owner", "legacy", null, null, () -> new LevelEventProvider() {
            public void initLevel(int zone, int act) { inits[0]++; }
            public void update() { }
        }, "LEGACY", 0x400, 0x40, 0, 0);
        var provider = new ModZoneEventProvider(null, 3, List.of(zone), boundary);
        var registry = new RewindRegistry();
        LevelEventRewindResolver.adapters(provider, 3).forEach(registry::register);
        provider.initLevel(3, 0);
        registry.restore(registry.capture());
        registry.restore(new CompositeSnapshot(Map.of()));
        assertEquals(2, inits[0]);
    }
}
