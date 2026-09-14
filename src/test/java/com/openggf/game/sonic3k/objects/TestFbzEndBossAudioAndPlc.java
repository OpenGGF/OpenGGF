package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzEndBossAudioAndPlc {
    @Test void exitOwnsBothPhysicalArchivesUntilReadyAndRestoresTheirPendingClaims() throws Exception {
        var queue = mock(com.openggf.game.sonic3k.resources.S3kKosModuleQueue.class);
        var coordinator = mock(com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.class);
        var timing = mock(com.openggf.game.timing.HardwareTimingService.class);
        var journal = mock(com.openggf.level.resources.KosinskiModuleQueue.class);
        var rom = mock(com.openggf.data.Rom.class);
        var kind = com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE;
        var door = new com.openggf.game.timing.HardwareWorkHandle(kind, 7, "door-fingerprint");
        var hall = new com.openggf.game.timing.HardwareWorkHandle(kind, 8, "hall-fingerprint");
        when(coordinator.moduleQueue()).thenReturn(queue);
        when(rom.readBytes(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(32)))
                .thenReturn(new byte[32]);
        var entries = com.openggf.game.sonic3k.Sonic3kPlcLoader.fbzEndBossExitKosmEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var handle = i == 0 ? door : hall;
            when(journal.enqueue(rom, entry.sourceAddress(), entry.destinationVramBytes())).thenReturn(true);
            when(queue.queue(rom, entry.sourceAddress(), entry.destinationVramBytes() / 32)).thenReturn(handle);
            when(timing.pendingHandle(kind, handle.ordinal())).thenReturn(java.util.Optional.of(handle));
        }
        var boss = new FbzEndBossInstance(new com.openggf.level.objects.ObjectSpawn(
                0x3000, 0x600, com.openggf.game.sonic3k.constants.Sonic3kObjectIds.FBZ_END_BOSS,
                0, 0, false, 0));
        boss.setServices(new com.openggf.level.objects.TestObjectServices() {
            @Override public com.openggf.data.Rom rom() { return rom; }
            @Override public com.openggf.level.resources.KosinskiModuleQueue kosinskiModuleQueue() { return journal; }
            @Override public com.openggf.game.RuntimeArtCoordinator runtimeArtCoordinator() { return coordinator; }
            @Override public com.openggf.game.timing.HardwareTimingService hardwareTiming() { return timing; }
        });
        var started = FbzEndBossInstance.class.getDeclaredField("nativeStarted");
        started.setAccessible(true);
        started.setBoolean(boss, true);
        var enqueue = FbzEndBossInstance.class.getDeclaredMethod("queueExitArt");
        enqueue.setAccessible(true);
        enqueue.invoke(boss);
        var ordered = org.mockito.Mockito.inOrder(queue);
        for (var entry : entries) ordered.verify(queue).queue(rom, entry.sourceAddress(), entry.destinationVramBytes() / 32);
        var pending = boss.captureRewindState();
        boss.updateBossLogic(1, null);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).claim(org.mockito.ArgumentMatchers.any());
        when(queue.isReady(door)).thenReturn(true);
        boss.updateBossLogic(2, null);
        org.mockito.Mockito.verify(queue).claim(door);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).claim(hall);
        when(queue.isReady(hall)).thenReturn(true);
        boss.updateBossLogic(3, null);
        boss.updateBossLogic(4, null);
        org.mockito.Mockito.verify(queue).claim(hall);
        boss.restoreRewindState(pending); // the timing owner restores the same pending handles independently
        boss.updateBossLogic(5, null);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.times(2)).claim(door);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.times(2)).claim(hall);
        for (var entry : entries) org.mockito.Mockito.verify(queue).queue(rom, entry.sourceAddress(), entry.destinationVramBytes() / 32);
    }

    @Test
    void entryAndExitPlcsUseLockedOnIdsAndKosmOrder() {
        assertEquals(0x6F, Sonic3kPlcLoader.fbzEndBossPlcId());
        assertEquals(java.util.List.of(
                new Sonic3kPlcLoader.KosmQueueEntry(
                        Sonic3kConstants.ART_KOSM_FBZ_EXIT_DOOR_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR * 32),
                new Sonic3kPlcLoader.KosmQueueEntry(
                        Sonic3kConstants.ART_KOSM_FBZ_EXIT_HALL_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_EXIT_HALL * 32)),
                Sonic3kPlcLoader.fbzEndBossExitKosmEntries());
    }

    @Test
    void combatAudioIdsAndPalettePatchMatchTheDisassembly() {
        assertEquals(0x19, FbzEndBossInstance.BOSS_MUSIC_ID);
        assertEquals(0x6E, FbzEndBossInstance.BOSS_HIT_SFX_ID);
        assertEquals(0xC9, FbzEndBossInstance.ARM_ROTATE_SFX_ID);
        assertEquals(0x4F, FbzEndBossFlameChild.CONTINUOUS_SFX_ID);
        assertEquals(0x70F94, Sonic3kConstants.PAL_FBZ_END_BOSS_ADDR);
    }

    @Test
    void exitPublicationUsesTheCanonicalRewindCapturedUnkFaa8Word() {
        var gameState = new com.openggf.game.GameStateManager();
        gameState.setEndOfLevelActive(true);
        var snapshot = gameState.capture();
        gameState.setEndOfLevelActive(false);
        gameState.restore(snapshot);
        assertTrue(gameState.isEndOfLevelActive());
        assertFalse(gameState.isEndOfLevelFlag(), "End_of_level_flag is a different native word");
    }
}
