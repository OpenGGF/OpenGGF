package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SozDoorObjectInstance;
import com.openggf.game.sonic3k.objects.SozPushableRockObjectInstance;
import com.openggf.game.sonic3k.objects.SozPushSwitchObjectInstance;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Connected positioned route: real cork mutation, rock-held switch and lower door passage. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozLowerRockPuzzleCapture {
    @Test void corkSuppliesTheFloorForTheRockHeldDoorAndReplaysTheWholeWorld() throws Exception {
        var movie=new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/soz2-lower-rock-sonic-320.bk2"));
        assertEquals(2512,movie.getFrameCount());
        var spots=Set.of(95,110,150,235,610,750,1075,1110,1200,1950,1980,2120,2280,2390,2450);
        var checked=new HashSet<Integer>();
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,
                0x4940,0x430,null,false,false,null,null,false,37,false);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,1,settings);
            int oldFloor=GameServices.level().getCurrentLevel().getMap().getValue(0,0x8F,0xB);
            for(int frame=0;frame<movie.getFrameCount();frame++) {
                session.step(movie.getFrame(frame));session.render();
                assertFalse(session.player().getDead(),"death at "+frame);
                var manager=GameServices.level().getObjectManager();
                if(frame==240) assertNotEquals(oldFloor,
                        GameServices.level().getCurrentLevel().getMap().getValue(0,0x8F,0xB),
                        "the actual cork event replaces the rock corridor's floor");
                if(frame==2400) {
                    var rock=manager.activeObjectsOfType(SozPushableRockObjectInstance.class).stream()
                            .filter(o->o.getSpawn().subtype()==0x87).findFirst().orElseThrow();
                    var button=manager.activeObjectsOfType(SozPushSwitchObjectInstance.class).stream()
                            .filter(o->o.getSpawn().subtype()==0x9B).findFirst().orElseThrow();
                    var door=manager.activeObjectsOfType(SozDoorObjectInstance.class).stream()
                            .filter(o->o.getSpawn().subtype()==0xB).findFirst().orElseThrow();
                    assertEquals(0x4834,rock.getX()); assertEquals(0x5B4,rock.getY());
                    assertEquals(0x4850,button.getX()); assertEquals(0x80,SozZoneRuntimeState.trigger(0xB));
                    assertEquals(0x500,door.getY());
                    assertTrue(session.player().getCentreX()>button.getX()+80,
                            "rock keeps the switch charged after the player leaves it");
                }
                if(!spots.contains(frame)) continue;
                checked.add(frame);
                var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved=registry.capture();
                for(int n=1;n<=45;n++) {session.step(movie.getFrame(frame+n));session.render();}
                var forward=registry.capture();
                registry.restore(saved);session.restoreInputHistory(movie.getFrame(frame));
                for(int n=1;n<=45;n++) {session.step(movie.getFrame(frame+n));session.render();}
                var replay=registry.capture();
                assertEquals(forward.entries().keySet(),replay.entries().keySet());
                for(String key:forward.entries().keySet()) assertTrue(
                        RewindSnapshotDiff.diffKey(key,forward.get(key),replay.get(key)).isEmpty(),
                        "input "+frame+" "+key+RewindSnapshotDiff.diffKey(key,forward.get(key),replay.get(key)));
                registry.restore(saved);session.restoreInputHistory(movie.getFrame(frame));
            }
            assertEquals(spots,checked);
            assertTrue(session.player().getCentreX()>0x4A40,"player crossed the lower door");
            assertEquals(8,GameServices.level().getCurrentZone()); assertEquals(1,GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled());
        }
    }
}
