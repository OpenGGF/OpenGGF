package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.GameplayModeContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import com.openggf.game.GameModule;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kSaveSnapshotProvider {

    @Test
    void restoreProgressRejectsFractionalEmeraldStates() {
        com.openggf.game.GameStateManager state = new com.openggf.game.GameStateManager();

        boolean restored = new S3kSaveSnapshotProvider().restoreProgress(
                state, 3, 0, Map.of("emeraldStates", List.of(0, 1, 2, 3, 0, 1, 2.5)));

        assertFalse(restored);
        assertEquals(List.of(0, 0, 0, 0, 0, 0, 0), state.getS3kEmeraldStates());
    }

    @Test
    void capture_includesTeamAndStartLocation() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.NEW_SLOT_START,
                com.openggf.game.save.RuntimeSaveCapture.capture(null, ctx));
        assertEquals("sonic", payload.get("mainCharacter"));
        assertEquals(0, payload.get("zone"));
        assertEquals(0, payload.get("act"));
    }

    @Test
    void capture_includesSidekicks() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.NEW_SLOT_START,
                com.openggf.game.save.RuntimeSaveCapture.capture(null, ctx));
        assertEquals(List.of("tails"), payload.get("sidekicks"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void capture_nullRuntime_defaultsLivesAndEmeralds() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 2,
                new SelectedTeam("knuckles", List.of()), 3, 1);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.NEW_SLOT_START,
                com.openggf.game.save.RuntimeSaveCapture.capture(null, ctx));
        assertEquals("knuckles", payload.get("mainCharacter"));
        assertEquals(3, payload.get("zone"));
        assertEquals(1, payload.get("act"));
        assertEquals(3, payload.get("lives"));
        assertEquals(List.of(), payload.get("chaosEmeralds"));
        assertEquals(List.of(), payload.get("superEmeralds"));
        assertEquals(false, payload.get("emeraldsConverted"));
        assertEquals(false, payload.get("clear"));
    }

    @Test
    void capture_runtimeUsesLiveZoneActAndGameState() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        GameplayModeContext gameplayMode = mock(GameplayModeContext.class);
        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.game.GameStateManager gameState = mock(com.openggf.game.GameStateManager.class);
        when(gameplayMode.getLevelManager()).thenReturn(levelManager);
        when(gameplayMode.getGameStateManager()).thenReturn(gameState);
        stubStockZones(levelManager);
        when(levelManager.getCurrentZone()).thenReturn(4);
        when(levelManager.getCurrentAct()).thenReturn(1);
        when(gameState.getLives()).thenReturn(7);
        when(gameState.getContinues()).thenReturn(5);
        when(gameState.getCollectedChaosEmeraldIndices()).thenReturn(List.of(0, 2, 4, 6));
        when(gameState.getCollectedSuperEmeraldIndices()).thenReturn(List.of(2));
        when(gameState.isEmeraldsConverted()).thenReturn(true);
        when(gameState.getS3kEmeraldStates()).thenReturn(List.of(0, 2, 1, 0, 1, 0, 3));

        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.PROGRESSION_SAVE,
                com.openggf.game.save.RuntimeSaveCapture.capture(gameplayMode, ctx));

        assertEquals(4, payload.get("zone"));
        assertEquals(1, payload.get("act"));
        assertEquals(7, payload.get("lives"));
        assertEquals(5, payload.get("continues"));
        assertEquals(List.of(0, 2, 4, 6), payload.get("chaosEmeralds"));
        assertEquals(List.of(2), payload.get("superEmeralds"));
        assertEquals(true, payload.get("emeraldsConverted"));
        assertEquals(List.of(0, 2, 1, 0, 1, 0, 3), payload.get("emeraldStates"));
    }

    @Test
    void capture_runtimeModZoneUsesTaggedIdentityWithoutSyntheticZone() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("tails", List.of()), 0, 0);
        GameplayModeContext gameplayMode = mock(GameplayModeContext.class);
        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.game.GameStateManager gameState = mock(com.openggf.game.GameStateManager.class);
        GameModule module = mock(GameModule.class);
        ZoneRegistry zones = mock(ZoneRegistry.class);
        when(gameplayMode.getLevelManager()).thenReturn(levelManager);
        when(gameplayMode.getGameStateManager()).thenReturn(gameState);
        when(levelManager.getCurrentZone()).thenReturn(22);
        when(levelManager.getCurrentAct()).thenReturn(0);
        when(levelManager.getGameModule()).thenReturn(module);
        when(module.getZoneRegistry()).thenReturn(zones);
        when(zones.zoneKey(22)).thenReturn(ZoneKey.mod("flappy", "sky"));
        when(gameState.getLives()).thenReturn(3);
        when(gameState.getCollectedChaosEmeraldIndices()).thenReturn(List.of());
        when(gameState.getCollectedSuperEmeraldIndices()).thenReturn(List.of());

        Map<String, Object> payload = new S3kSaveSnapshotProvider().capture(
                SaveReason.PROGRESSION_SAVE,
                com.openggf.game.save.RuntimeSaveCapture.capture(gameplayMode, ctx));

        assertFalse(payload.containsKey("zone"));
        assertEquals(ZoneKey.mod("flappy", "sky"), S3kSavedZone.read(payload).zoneKey());
        assertEquals(1, payload.get("progressCode"));
    }

    @Test
    void capture_clearSaveIncludesProgressCodeAndClearState() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        ctx.markClear();
        GameplayModeContext gameplayMode = mock(GameplayModeContext.class);
        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.game.GameStateManager gameState = mock(com.openggf.game.GameStateManager.class);
        when(gameplayMode.getLevelManager()).thenReturn(levelManager);
        when(gameplayMode.getGameStateManager()).thenReturn(gameState);
        stubStockZones(levelManager);
        when(levelManager.getCurrentZone()).thenReturn(0x0C);
        when(levelManager.getCurrentAct()).thenReturn(0);
        when(gameState.getLives()).thenReturn(7);
        when(gameState.getCollectedChaosEmeraldIndices()).thenReturn(List.of(0, 1, 2, 3, 4, 5, 6));
        when(gameState.getCollectedSuperEmeraldIndices()).thenReturn(List.of(0, 1, 2, 3, 4, 5, 6));

        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.PROGRESSION_SAVE,
                com.openggf.game.save.RuntimeSaveCapture.capture(gameplayMode, ctx));

        assertEquals(true, payload.get("clear"));
        assertEquals(14, payload.get("progressCode"));
        assertEquals(2, payload.get("clearState"));
    }

    @Test
    void capture_existingSlotLoadWithoutRuntime_rejected() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.capture(SaveReason.EXISTING_SLOT_LOAD, com.openggf.game.save.RuntimeSaveCapture.capture(null, ctx)));

        assertTrue(ex.getMessage().contains("runtime"));
    }

    @Test
    void capture_specialStageSave_requiresRuntimeEmeraldState() {
        SaveSessionContext ctx = SaveSessionContext.forSlot("s3k", 1,
                new SelectedTeam("sonic", List.of("tails")), 0, 0);
        GameplayModeContext gameplayMode = mock(GameplayModeContext.class);
        com.openggf.level.LevelManager levelManager = mock(com.openggf.level.LevelManager.class);
        com.openggf.game.GameStateManager gameState = mock(com.openggf.game.GameStateManager.class);
        when(gameplayMode.getLevelManager()).thenReturn(levelManager);
        when(gameplayMode.getGameStateManager()).thenReturn(gameState);
        stubStockZones(levelManager);
        when(levelManager.getCurrentZone()).thenReturn(7);
        when(levelManager.getCurrentAct()).thenReturn(0);
        when(gameState.getLives()).thenReturn(5);
        when(gameState.getContinues()).thenReturn(2);
        when(gameState.getCollectedChaosEmeraldIndices()).thenReturn(List.of(0, 1, 2, 3, 4, 5, 6));
        when(gameState.getCollectedSuperEmeraldIndices()).thenReturn(List.of(1, 4));

        S3kSaveSnapshotProvider provider = new S3kSaveSnapshotProvider();
        Map<String, Object> payload = provider.capture(
                SaveReason.SPECIAL_STAGE_SAVE,
                com.openggf.game.save.RuntimeSaveCapture.capture(gameplayMode, ctx));

        assertEquals(7, payload.get("zone"));
        assertEquals(0, payload.get("act"));
        assertEquals(5, payload.get("lives"));
        assertEquals(2, payload.get("continues"));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), payload.get("chaosEmeralds"));
        assertEquals(List.of(1, 4), payload.get("superEmeralds"));
    }

    @Test void sszClearTracksCapturedEndingStateWithoutLatchingAcrossRewind() {
        var save = SaveSessionContext.forSlot("s3k", 1, new SelectedTeam("knuckles", List.of()), 10, 1);
        var mode = mock(GameplayModeContext.class);
        var level = mock(com.openggf.level.LevelManager.class);
        var game = new com.openggf.game.GameStateManager();
        var registry = mock(com.openggf.game.zone.ZoneRuntimeRegistry.class);
        var state = new com.openggf.game.sonic3k.runtime.SszZoneRuntimeState(1, com.openggf.game.PlayerCharacter.KNUCKLES);
        when(mode.getLevelManager()).thenReturn(level);
        when(mode.getGameStateManager()).thenReturn(game);
        when(mode.getZoneRuntimeRegistry()).thenReturn(registry);
        when(registry.currentAs(com.openggf.game.sonic3k.runtime.SszZoneRuntimeState.class))
                .thenReturn(java.util.Optional.of(state));
        when(registry.current()).thenReturn(state);
        when(level.getCurrentZone()).thenReturn(10); when(level.getCurrentAct()).thenReturn(1);
        stubStockZones(level);
        var provider = new S3kSaveSnapshotProvider();
        var context = com.openggf.game.save.RuntimeSaveCapture.capture(mode, save);
        var before = state.captureBytes();
        assertEquals(false, provider.capture(SaveReason.PROGRESSION_SAVE, context).get("clear"));
        state.setAct2EndingActive(true);
        assertEquals(false, provider.capture(SaveReason.PROGRESSION_SAVE, context).get("clear"),
                "Earlier capture must stay immutable after the ending state changes");
        assertEquals(true, provider.capture(SaveReason.PROGRESSION_SAVE,
                com.openggf.game.save.RuntimeSaveCapture.capture(mode, save)).get("clear"));
        assertFalse(save.isClear(), "live clear must not latch outside captured state");
        state.restoreBytes(before);
        assertEquals(false, provider.capture(SaveReason.PROGRESSION_SAVE,
                com.openggf.game.save.RuntimeSaveCapture.capture(mode, save)).get("clear"));
    }

    /**
     * SaveGame stores {@code SaveGame_NextLevel[Current_zone_and_act]} (sonic3k.asm:15824-15872);
     * data select loads it through {@code sub_DA4E}/{@code LevelList_DA6E} (:17480-17513).
     * Rows are the native SaveGame callers' zone states.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "SaveGame at {0}/{1} as {2} resumes {3}/{4}")
    @org.junit.jupiter.params.provider.CsvSource({
            "0,1,sonic,1,0",        // AIZ2 results → HCZ1
            "3,1,sonic,5,0",        // CNZ2 results → ICZ1
            "7,1,sonic,4,0",        // MHZ2 results → FBZ1
            "4,1,sonic,8,0",        // FBZ2 results → SOZ1
            "9,1,knuckles,22,1",    // LRZ2 Obj_StartNewLevel → HPZ $1601
            "22,0,sonic,22,1",      // LRZ3 results → HPZ $1601
            "22,1,sonic,10,0",      // HPZ loc_45D8A → SSZ1
            "22,1,tails,10,0",
            "22,1,knuckles,10,1",   // HPZ loc_45B94 → $A01 (sub_DA4E Player_mode 3)
            "10,0,sonic,11,0",      // SSZ1 results → DEZ1
            "10,1,knuckles,11,0",   // SSZ2 loc_7BCB0 → code $C (complete file)
            "23,0,sonic,12,0"})     // DEZ boss arena → DDZ
    void fullSaveGameStoresTheNativeNextLevel(int zone, int act, String main, int nextZone, int nextAct) {
        var team = new SelectedTeam(main, List.of());
        var payload = fullSaveGamePayload(team, zone, act);
        assertEquals(nextZone, payload.get("zone"));
        assertEquals(nextAct, payload.get("act"));
        assertEquals(new com.openggf.game.dataselect.DataSelectDestination(nextZone, nextAct),
                new S3kDataSelectProfile().resolveLoadDestination(payload));
    }

    @Test
    void fullSaveGameWithoutANextLevelCodeKeepsTheCurrentLocation() {
        // DDZ's code $E has no LevelList_DA6E entry; SaveGame always writes it as a completed file.
        var payload = fullSaveGamePayload(new SelectedTeam("sonic", List.of()), 12, 0);
        assertEquals(12, payload.get("zone"));
        assertEquals(0, payload.get("act"));
    }

    @Test
    void persistenceOnlyProgressionRequestsKeepTheCurrentLocation() {
        var save = SaveSessionContext.forSlot("s3k", 1, new SelectedTeam("knuckles", List.of()), 22, 1);
        var payload = new S3kSaveSnapshotProvider().capture(SaveReason.PROGRESSION_SAVE,
                com.openggf.game.save.RuntimeSaveCapture.capture(liveMode(22, 1), save));
        assertEquals(22, payload.get("zone"));
        assertEquals(1, payload.get("act"));
    }

    private static Map<String, Object> fullSaveGamePayload(SelectedTeam team, int zone, int act) {
        var save = SaveSessionContext.forSlot("s3k", 1, team, zone, act);
        var mode = liveMode(zone, act);
        var captured = new java.util.ArrayList<Map<String, Object>>();
        var services = new com.openggf.level.objects.TestObjectServices() {
            @Override public void requestSessionSave(SaveReason reason) {
                captured.add(new S3kSaveSnapshotProvider().capture(reason,
                        com.openggf.game.save.RuntimeSaveCapture.capture(mode, save)));
            }
        }.withGameState(new com.openggf.game.GameStateManager());
        com.openggf.game.sonic3k.S3kFullSaveGame.complete(services);
        assertEquals(1, captured.size());
        return captured.getFirst();
    }

    private static GameplayModeContext liveMode(int zone, int act) {
        var mode = mock(GameplayModeContext.class);
        var level = mock(com.openggf.level.LevelManager.class);
        when(mode.getLevelManager()).thenReturn(level);
        when(mode.getGameStateManager()).thenReturn(new com.openggf.game.GameStateManager());
        when(level.getCurrentZone()).thenReturn(zone);
        when(level.getCurrentAct()).thenReturn(act);
        stubStockZones(level);
        return mode;
    }

    private static void stubStockZones(com.openggf.level.LevelManager levelManager) {
        GameModule module = mock(GameModule.class);
        when(levelManager.getGameModule()).thenReturn(module);
        when(module.getZoneRegistry()).thenReturn(new com.openggf.game.sonic3k.Sonic3kZoneRegistry());
        when(module.getSaveSnapshotProvider()).thenReturn(new S3kSaveSnapshotProvider());
    }
}
