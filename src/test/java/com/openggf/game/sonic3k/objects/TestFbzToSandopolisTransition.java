package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.Test;
import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzToSandopolisTransition {
    @Test void startNewLevel0800DecodesToSandopolisActOne() {
        assertEquals(Sonic3kZoneIds.ZONE_SOZ, FbzEndBossInstance.exitZone());
        assertEquals(0, FbzEndBossInstance.exitAct());
        assertEquals(0x720, FbzEndBossInstance.exitCameraThreshold());
    }

    @Test void forcedInputMatchesLoc86334IncludingOnObjectLatch() {
        assertEquals(FbzEndBossInstance.ForcedExitInput.RIGHT,
                FbzEndBossInstance.forcedExitInput(false, 0));
        assertEquals(FbzEndBossInstance.ForcedExitInput.A_RIGHT_HELD_RIGHT,
                FbzEndBossInstance.forcedExitInput(false, 1));
        assertEquals(FbzEndBossInstance.ForcedExitInput.A_RIGHT,
                FbzEndBossInstance.forcedExitInput(true, 0));
    }

    @Test void exitReadyWritesFollowerHistoryAndRequestsSstTransitionAtUnsigned720() {
        AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        Camera camera = mock(Camera.class);
        ObjectServices services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, List::of));
        when(services.camera()).thenReturn(camera);
        when(camera.getY()).thenReturn((short) 0x071F, (short) 0x0720);
        FbzEndBossInstance boss = new FbzEndBossInstance(
                new ObjectSpawn(0x307C, 0x648, FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0));
        boss.setServices(services);
        boss.clearExitInputTimerForTest();

        boss.updateExitReadyForTest();
        verify(main).setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        verify(main).writeLogicalInputAndCurrentFollowerHistory(AbstractPlayableSprite.INPUT_RIGHT, false);
        verify(services, never()).requestZoneAndAct(anyInt(), anyInt(), anyBoolean());

        boss.updateExitReadyForTest();
        verify(services).requestZoneAndAct(Sonic3kZoneIds.ZONE_SOZ, 0, true);
        assertTrue(boss.isDestroyed());
    }

    @Test void exactThresholdTransitionsBeforeAnyInputOrFollowerHistoryWrite() {
        AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        Camera camera = mock(Camera.class);
        ObjectServices services = mock(ObjectServices.class);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, List::of));
        when(services.camera()).thenReturn(camera);
        when(camera.getY()).thenReturn((short) 0x0720);
        FbzEndBossInstance boss = new FbzEndBossInstance(
                new ObjectSpawn(0, 0, FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0));
        boss.setServices(services);

        boss.updateExitReadyForTest();

        verifyNoInteractions(main);
        verify(services).requestZoneAndAct(Sonic3kZoneIds.ZONE_SOZ, 0, true);
    }

    @Test void busyExitArtQueueDoesNotDelayTheNativeCameraThresholdTransition() {
        Camera camera = mock(Camera.class);
        ObjectServices services = mock(ObjectServices.class);
        KosinskiModuleQueue queue = new KosinskiModuleQueue();
        queue.restore(new KosinskiModuleQueue.Snapshot(List.of(
                new KosinskiModuleQueue.ArchiveState(0x165BCA, 0x165BCC, 0x7CA0,
                        0x200, 1, 1, 0x100, -1, true)),
                KosinskiModuleQueue.Phase.READY_TO_START, null, List.of(), List.of()));
        when(services.camera()).thenReturn(camera);
        when(services.kosinskiModuleQueue()).thenReturn(queue);
        when(camera.getY()).thenReturn((short) 0x0720);
        FbzEndBossInstance boss = new FbzEndBossInstance(
                new ObjectSpawn(0, 0, FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0));
        boss.setServices(services);

        boss.updateExitReadyForTest();

        verify(services).requestZoneAndAct(Sonic3kZoneIds.ZONE_SOZ, 0, true);
        assertTrue(boss.isDestroyed());
    }

    @Test void pushingPublishesANewPressThenTheTimerPublishesAHeldOnly() {
        AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        ObjectServices services = mock(ObjectServices.class);
        Camera camera = mock(Camera.class);
        when(camera.getY()).thenReturn((short) 0x0600);
        when(services.camera()).thenReturn(camera);
        when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> main, List::of));
        when(main.getPushing()).thenReturn(true, false);
        FbzEndBossInstance boss = new FbzEndBossInstance(
                new ObjectSpawn(0, 0, FbzEndBossInstance.OBJECT_ID, 0, 0, false, 0));
        boss.setServices(services);
        boss.clearExitInputTimerForTest();
        int mask = AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP;

        boss.updateExitReadyForTest();
        verify(main).writeLogicalInputAndCurrentFollowerHistory(mask, true);
        boss.updateExitReadyForTest();
        verify(main).writeLogicalInputAndCurrentFollowerHistory(mask, false);
    }
}
