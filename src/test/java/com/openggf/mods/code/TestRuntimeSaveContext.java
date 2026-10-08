package com.openggf.mods.code;

import com.openggf.game.save.*;

import com.openggf.game.*;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestRuntimeSaveContext {
    public static final class StandaloneController implements com.openggf.game.mode.GameplayFrameController,
            com.openggf.game.rewind.RewindSnapshottable<Integer> {
        int value=7;
        public String key() { return "mode"; }
        public Integer capture() { return value; }
        public void restore(Integer value) { this.value=value; }
        public boolean beforeTick(com.openggf.game.mode.CourseControl course,com.openggf.control.LogicalInputSnapshot input) { return true; }
        public void afterTick(com.openggf.game.mode.CourseControl course,boolean advanced) { }
    }
    @Test void expertStandaloneControllerServiceAndAdapterShareTheActualCoursePartitionIdentity() {
        var raw=new StandaloneController();
        var spec=StandaloneGameSpec.builder("sample").zone("FIRST",new StandaloneGameSpec.Act(0x400,
                mock(com.openggf.level.Level.class),0,0,MusicReference.namespaced("sample","theme"))).build();
        var delegate=new AbstractStandaloneGameModule(spec) {
            @Override public com.openggf.game.mode.GameplayFrameController gameplayFrameController() { return raw; }
            @Override public java.util.List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() { return List.of(raw); }
            @Override public <T> T getGameService(Class<T> type) {
                return type.isInstance(raw) ? type.cast(raw) : super.getGameService(type);
            }
        };
        var boundary=new ModFaultBoundary(Map.of(),new com.openggf.mods.ModRuntimeFindingStore(),
                owners->new com.openggf.mods.ModStateSaveResult.Saved(),owners->{});
        var owned=com.openggf.mods.runtime.OwnerBoundGamePatch.wrapStandaloneRewinds("sample",
                OwnerAwareStandaloneModule.wrap("sample",delegate,boundary,Map.of()),boundary);
        var controller=owned.gameplayFrameController(); var adapter=owned.rewindAdapters().getFirst();
        assertSame(controller,adapter);
        assertSame(controller,owned.getGameService(com.openggf.game.mode.GameplayFrameController.class));
        assertSame(raw,owned.getGameService(StandaloneController.class));
        com.openggf.game.session.EngineServices.configure(com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
        var gameplay=new GameplayModeContext(new com.openggf.game.session.WorldSession(owned));
        var rng=new GameRng(GameRng.Flavour.S1_S2);
        try {
            gameplay.attachGameplayManagers(new com.openggf.camera.Camera(),new com.openggf.timer.TimerManager(),
                    new GameStateManager(),new com.openggf.graphics.FadeManager(),rng,new com.openggf.game.solid.DefaultSolidExecutionRegistry());
            var registry=gameplay.getRewindRegistry(); var full=registry.capture(); var course=registry.captureCourse();
            assertEquals(7,full.get(adapter.key())); assertFalse(course.containsKey(adapter.key()));
            assertTrue(course.containsKey("gamerng"));
            raw.value=99; registry.restoreCourse(course); assertEquals(99,raw.value);
            registry.restore(full); assertEquals(7,raw.value);
        } finally { gameplay.destroy(); }
    }
    @Test void commonProgressAndNestedExtensionInputsAreFrozenAtOneCapture() {
        var mode=mock(GameplayModeContext.class); var level=mock(LevelManager.class);
        var module=mock(GameModule.class); var state=new GameStateManager();
        state.restoreSaveProgress(5,2,List.of(),List.of(),false);
        when(mode.getLevelManager()).thenReturn(level); when(mode.getGameStateManager()).thenReturn(state);
        when(level.getGameModule()).thenReturn(module); when(level.getCurrentZone()).thenReturn(2);
        when(level.getCurrentAct()).thenReturn(1);
        List<Object> inventory=new ArrayList<>(List.of("key",3));
        Map<String,Object> quest=new LinkedHashMap<>(Map.of("items",inventory));
        Map<String,Object> fields=new LinkedHashMap<>(Map.of("quest",quest));
        int[] captures={0};
        when(module.getSaveSnapshotProvider()).thenReturn(new SaveSnapshotProvider() {
            public Map<String,Object> capture(SaveReason reason,RuntimeSaveContext context) { return context.capturedFields(); }
            public Map<String,Object> captureSaveFields(com.openggf.game.zone.ZoneRuntimeState zone) {
                captures[0]++; return fields;
            }
        });
        var save=SaveSessionContext.forSlot("sample",1,new SelectedTeam("sample:hero",List.of()),0,0);
        var captured=RuntimeSaveCapture.capture(mode,save);
        inventory.add("later"); quest.put("changed",true); fields.clear();
        state.restoreSaveProgress(9,2,List.of(),List.of(),false);
        when(level.getCurrentZone()).thenReturn(4); save.markClear();
        assertEquals(1,captures[0]); assertEquals(2,captured.currentZone()); assertEquals(1,captured.currentAct());
        assertEquals(5,captured.lives()); assertEquals(2,captured.continues()); assertFalse(captured.isClear());
        assertEquals(Map.of("quest",Map.of("items",List.of("key",3))),captured.capturedFields());
        assertThrows(UnsupportedOperationException.class,()->captured.capturedFields().clear());
        @SuppressWarnings("unchecked") var nested=(List<Object>)((Map<?,?>)captured.capturedFields().get("quest")).get("items");
        assertThrows(UnsupportedOperationException.class,()->nested.add("bad"));
        var fresh=RuntimeSaveCapture.capture(mode,save);
        assertEquals(4,fresh.currentZone()); assertEquals(9,fresh.lives()); assertTrue(fresh.isClear());
    }

    @Test void freshSaveNeedsNoLiveManagersAndArbitraryProviderPayloadRemainsUnconstrained() {
        var save=SaveSessionContext.forSlot("sample",2,new SelectedTeam("sample:hero",List.of("sample:friend")),3,1);
        var context=RuntimeSaveContext.forNewGame(save);
        assertFalse(context.hasLiveGameplayState()); assertEquals(3,context.currentZone());
        assertEquals(1,context.currentAct()); assertEquals(3,context.lives()); assertEquals(2,context.activeSlot().orElseThrow());
        SaveSnapshotProvider provider=(reason,input)->Map.of("inventory",List.of("key"),"questFlags",Map.of("door",true));
        assertEquals(Map.of("inventory",List.of("key"),"questFlags",Map.of("door",true)),provider.capture(SaveReason.NEW_SLOT_START,context));
    }

    @Test void creditsSentinelPreservesCompletionProgressAndStandaloneDefaultSaveFormat() {
        var spec=StandaloneGameSpec.builder("sample").zone("FIRST",new StandaloneGameSpec.Act(0x400,
                mock(com.openggf.level.Level.class),0,0,MusicReference.stock(-1))).build();
        var zoneQueries=new java.util.concurrent.atomic.AtomicInteger();
        var registry=new DelegatingZoneRegistry(spec.zoneRegistry()) {
            @Override public ZoneKey zoneKey(int zone) {
                zoneQueries.incrementAndGet();
                return super.zoneKey(zone);
            }
        };
        var delegate=new AbstractStandaloneGameModule(spec) {
            @Override public ZoneRegistry getZoneRegistry() { return registry; }
        };
        var boundary=new ModFaultBoundary(Map.of(),new com.openggf.mods.ModRuntimeFindingStore(),
                owners->new com.openggf.mods.ModStateSaveResult.Saved(),owners->{});
        var module=OwnerAwareStandaloneModule.wrap("sample",delegate,boundary,Map.of());
        var mode=mock(GameplayModeContext.class); var level=mock(LevelManager.class);
        when(mode.getLevelManager()).thenReturn(level); when(mode.getGameStateManager()).thenReturn(new GameStateManager());
        when(level.getGameModule()).thenReturn(module); when(level.getCurrentZone()).thenReturn(1);
        var save=SaveSessionContext.forSlot("sample",1,new SelectedTeam("sample:hero",List.of()),0,0);
        var captured=RuntimeSaveCapture.capture(mode,save);
        assertEquals(1,captured.currentZone()); assertEquals(0,captured.currentAct());
        assertEquals(ZoneKey.stock(1),captured.zoneKey()); assertEquals(0,zoneQueries.get());
        assertEquals(Map.of("zone",0,"act",0,"mainCharacter","sample:hero","sidekicks",List.of(),"clear",false),
                module.getSaveSnapshotProvider().capture(SaveReason.PROGRESSION_SAVE,captured));

        // Other invalid indices still call the owner-bound registry and fail there.
        when(level.getCurrentZone()).thenReturn(2);
        assertEquals("sample",assertThrows(ModFaultBoundary.CallbackAborted.class,
                ()->RuntimeSaveCapture.capture(mode,save)).owner());
        assertEquals(1,zoneQueries.get());
    }

    @Test void providerCallbacksCannotChangeTheAlreadyCapturedCommonProgress() {
        var mode=mock(GameplayModeContext.class); var level=mock(LevelManager.class); var module=mock(GameModule.class);
        var state=new GameStateManager(); state.restoreSaveProgress(5,2,List.of(),List.of(),false);
        var save=SaveSessionContext.noSave("sample",new SelectedTeam("sample:hero",List.of()),0,0);
        when(mode.getLevelManager()).thenReturn(level); when(mode.getGameStateManager()).thenReturn(state);
        when(level.getGameModule()).thenReturn(module); when(level.getCurrentZone()).thenReturn(2);
        when(module.getSaveSnapshotProvider()).thenReturn(new SaveSnapshotProvider() {
            public Map<String,Object> capture(SaveReason reason,RuntimeSaveContext context) { return Map.of(); }
            public Map<String,Object> captureSaveFields(com.openggf.game.zone.ZoneRuntimeState zone) {
                state.restoreSaveProgress(9,2,List.of(),List.of(),false);
                when(level.getCurrentZone()).thenReturn(4); save.markClear();
                return Map.of("questFlag",true);
            }
        });
        var captured=RuntimeSaveCapture.capture(mode,save);
        assertEquals(2,captured.currentZone()); assertEquals(5,captured.lives()); assertFalse(captured.isClear());
        assertEquals(Map.of("questFlag",true),captured.capturedFields());
        var fresh=RuntimeSaveCapture.capture(mode,save);
        assertEquals(4,fresh.currentZone()); assertEquals(9,fresh.lives()); assertTrue(fresh.isClear());
    }

    @Test void mutableNonJsonExtensionValuesAreRejectedBeforePublication() {
        var mode=mock(GameplayModeContext.class); var level=mock(LevelManager.class); var module=mock(GameModule.class);
        when(mode.getLevelManager()).thenReturn(level); when(mode.getGameStateManager()).thenReturn(new GameStateManager());
        when(level.getGameModule()).thenReturn(module);
        when(module.getSaveSnapshotProvider()).thenReturn(new SaveSnapshotProvider() {
            public Map<String,Object> capture(SaveReason reason,RuntimeSaveContext context) { return Map.of(); }
            public Map<String,Object> captureSaveFields(com.openggf.game.zone.ZoneRuntimeState zone) { return Map.of("mutable",new int[]{1}); }
        });
        var save=SaveSessionContext.noSave("sample",new SelectedTeam("sample:hero",List.of()),0,0);
        assertThrows(IllegalArgumentException.class,()->RuntimeSaveCapture.capture(mode,save));
    }

    @Test void runtimeFieldCallbackRetainsTheProvidersRegisteredOwnerBoundary() {
        var findings=new com.openggf.mods.ModRuntimeFindingStore();
        var disabled=new java.util.concurrent.atomic.AtomicReference<Set<String>>();
        var boundary=new com.openggf.mods.code.ModFaultBoundary(Map.of(),findings,
                owners->new com.openggf.mods.ModStateSaveResult.Saved(),disabled::set);
        var delegate=mock(GameModule.class);
        when(delegate.getSaveSnapshotProvider()).thenReturn(new SaveSnapshotProvider() {
            public Map<String,Object> capture(SaveReason reason,RuntimeSaveContext context) { return Map.of(); }
            public Map<String,Object> captureSaveFields(com.openggf.game.zone.ZoneRuntimeState zone) {
                throw new IllegalStateException("capture fields");
            }
        });
        var owned=com.openggf.mods.code.OwnerAwareStandaloneModule.wrap("registered-owner",delegate,boundary,Map.of());
        var mode=mock(GameplayModeContext.class); var level=mock(LevelManager.class);
        when(mode.getLevelManager()).thenReturn(level); when(mode.getGameStateManager()).thenReturn(new GameStateManager());
        when(level.getGameModule()).thenReturn(owned);
        var save=SaveSessionContext.noSave("sample",new SelectedTeam("sample:hero",List.of()),0,0);
        var failure=assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,
                ()->RuntimeSaveCapture.capture(mode,save));
        assertEquals("registered-owner",failure.owner());
        assertEquals(Set.of("registered-owner"),disabled.get());
        assertEquals("MOD_CALLBACK_FAILED",findings.findingsFor("registered-owner").getFirst().code());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "standalone,array", "standalone,cycle", "standalone,null",
            "patch,array", "patch,cycle", "patch,null",
            "service,array", "service,cycle", "service,null"})
    void invalidReturnedRuntimeFieldsDisableTheirActualProviderAndRequiredDependents(String route,String kind) {
        Map<String,Object> fields;
        if (kind.equals("array")) fields=Map.of("mutable",new int[]{1});
        else if (kind.equals("cycle")) {
            fields=new LinkedHashMap<>(); fields.put("self",fields);
        } else fields=null;
        var findings=new com.openggf.mods.ModRuntimeFindingStore();
        var disabled=new java.util.concurrent.atomic.AtomicReference<Set<String>>();
        var boundary=new ModFaultBoundary(Map.of("required-dependent",Set.of("registered-owner")),findings,
                owners->new com.openggf.mods.ModStateSaveResult.Saved(),disabled::set);
        var delegate=mock(GameModule.class);
        int[] calls={0};
        SaveSnapshotProvider provider=new SaveSnapshotProvider() {
            public Map<String,Object> capture(SaveReason reason,RuntimeSaveContext context) { return Map.of(); }
            public Map<String,Object> captureSaveFields(com.openggf.game.zone.ZoneRuntimeState zone) {
                calls[0]++; return fields;
            }
        };
        when(delegate.getSaveSnapshotProvider()).thenReturn(provider);
        GameModule owned;
        if (route.equals("standalone")) {
            owned=OwnerAwareStandaloneModule.wrap("registered-owner",delegate,boundary,Map.of());
        } else if (route.equals("patch")) {
            var patch=mock(com.openggf.game.patch.GamePatch.class);
            var base=new com.openggf.game.sonic2.Sonic2GameModule();
            var patched=new com.openggf.game.patch.DelegatingGameModule(base,"registered-owner:save") {
                @Override public SaveSnapshotProvider getSaveSnapshotProvider() { return provider; }
            };
            when(patch.apply(base,null)).thenReturn(patched);
            owned=com.openggf.mods.runtime.OwnerBoundGamePatch.wrap("registered-owner",patch,boundary).apply(base,null);
        } else {
            var callbacks=new com.openggf.mods.runtime.OwnerBoundCallbacks("registered-owner",boundary,32,Map.of());
            when(delegate.getSaveSnapshotProvider()).thenReturn(callbacks.bind(SaveSnapshotProvider.class,provider));
            owned=delegate;
        }
        var mode=mock(GameplayModeContext.class); var level=mock(LevelManager.class);
        when(mode.getLevelManager()).thenReturn(level); when(mode.getGameStateManager()).thenReturn(new GameStateManager());
        when(level.getGameModule()).thenReturn(owned);
        var save=SaveSessionContext.noSave("sample",new SelectedTeam("sample:hero",List.of()),0,0);
        var failure=assertThrows(ModFaultBoundary.CallbackAborted.class,()->RuntimeSaveCapture.capture(mode,save));
        assertEquals(1,calls[0]); assertEquals("registered-owner",failure.owner());
        assertEquals(Set.of("registered-owner","required-dependent"),disabled.get());
        assertEquals("MOD_CALLBACK_FAILED",findings.findingsFor("registered-owner").getFirst().code());
    }
}
