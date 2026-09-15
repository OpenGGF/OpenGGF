package com.openggf.game.solid;

import com.openggf.game.rewind.snapshot.SolidExecutionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSolidExecutionRewindSnapshot {

    @Test
    void keyIsSolidExecution() {
        assertEquals("solid-execution", new DefaultSolidExecutionRegistry().key());
    }

    @Test
    void captureReturnsEmptyRecord() {
        DefaultSolidExecutionRegistry reg = new DefaultSolidExecutionRegistry();
        SolidExecutionSnapshot snap = reg.capture();
        assertNotNull(snap);
        // A fresh registry has no previous-frame standing state, so the snapshot
        // must be the empty/sentinel record (no entries to serialise).
        assertNotNull(snap.previousStanding(), "previousStanding list must not be null");
        assertTrue(snap.previousStanding().isEmpty(),
                "fresh registry must capture an empty previousStanding list, got "
                        + snap.previousStanding());
        assertEquals(new SolidExecutionSnapshot(List.of()), snap,
                "fresh capture must equal the empty snapshot record");
    }

    @Test
    void emptyRestoreNeedsNoGameplaySession() {
        // An empty history is valid outside an active gameplay session.
        DefaultSolidExecutionRegistry reg = new DefaultSolidExecutionRegistry();
        SolidExecutionSnapshot snap = reg.capture();
        assertDoesNotThrow(() -> reg.restore(snap));
    }
    @Test
    void sharedSpawnChildrenAndDuplicatePlayersRestoreByStableIdentity() {
        var manager=org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        var level=org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(level.getObjectManager()).thenReturn(manager);
        var before=new com.openggf.game.rewind.identity.RewindIdentityTable();
        var restored=new com.openggf.game.rewind.identity.RewindIdentityTable();
        var spawn=new com.openggf.level.objects.ObjectSpawn(100,100,15,0,0,false,0);
        var oldObjects=new com.openggf.level.objects.ObjectInstance[2];
        var newObjects=new com.openggf.level.objects.ObjectInstance[2];
        var oldPlayers=new com.openggf.game.PlayableEntity[2];
        var newPlayers=new com.openggf.game.PlayableEntity[2];
        for(int i=0;i<2;i++) {
            oldObjects[i]=org.mockito.Mockito.mock(com.openggf.level.objects.ObjectInstance.class);
            newObjects[i]=org.mockito.Mockito.mock(com.openggf.level.objects.ObjectInstance.class);
            org.mockito.Mockito.when(oldObjects[i].getSpawn()).thenReturn(spawn);
            org.mockito.Mockito.when(newObjects[i].getSpawn()).thenReturn(spawn);
            oldPlayers[i]=new com.openggf.tests.TestablePlayableSprite("sonic",(short)0,(short)0);
            newPlayers[i]=new com.openggf.tests.TestablePlayableSprite("sonic",(short)0,(short)0);
            var oid=com.openggf.game.rewind.identity.ObjectRefId.child(i,0,7,i);
            var pid=new com.openggf.game.rewind.identity.PlayerRefId(i+1);
            before.registerObject(oldObjects[i],oid);restored.registerObject(newObjects[i],oid);
            before.registerPlayer(oldPlayers[i],pid);restored.registerPlayer(newPlayers[i],pid);
        }
        var reg=new DefaultSolidExecutionRegistry();
        for(int i=0;i<2;i++) {
            var result=new PlayerSolidContactResult(ContactKind.TOP,true,false,false,false,null,null,0);
            reg.publishCheckpoint(new SolidCheckpointBatch(oldObjects[i],java.util.Map.of(oldPlayers[i],result)));
        }
        reg.finishFrame();
        org.mockito.Mockito.when(manager.captureIdentityContext()).thenReturn(
                com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(before),
                com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(restored));
        try(var globals=org.mockito.Mockito.mockStatic(com.openggf.game.GameServices.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            globals.when(com.openggf.game.GameServices::levelOrNull).thenReturn(level);
            var snapshot=reg.capture();assertEquals(2,snapshot.previousStanding().size());
            reg.restore(snapshot);
            for(int i=0;i<2;i++) {
                assertTrue(reg.previousStanding(newObjects[i],newPlayers[i]).standing());
                assertFalse(reg.previousStanding(newObjects[i],newPlayers[1-i]).standing());
                assertFalse(reg.previousStanding(oldObjects[i],oldPlayers[i]).standing());
            }
            assertEquals(snapshot,reg.capture());
        }
    }

}
