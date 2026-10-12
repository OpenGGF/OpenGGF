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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CharacterKey;
import java.util.stream.Stream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Connected positioned route: real cork mutation, rock-held switch and lower door passage. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozLowerRockPuzzleCapture {
    static Stream<Arguments> routes() {
        return Stream.of(WidescreenAspect.values()).flatMap(aspect -> Stream.of(
                Arguments.of("sonic", aspect.pixelWidth(), aspect.pixelWidth()==528 ? 2773 : aspect.pixelWidth()==800 ? 2926 : 2512),
                Arguments.of("knuckles", aspect.pixelWidth(), aspect.pixelWidth()==528 ? 2919 : 2839)));
    }

    @ParameterizedTest @MethodSource("routes")
    void corkSuppliesTheFloorForTheRockHeldDoorAndReplaysTheWholeWorld(String main, int width, int count) throws Exception {
        int routeWidth=(width==528 || (width==800 && main.equals("sonic"))) ? width : 320;
        var movie=new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/soz2-lower-rock-"+main+"-"+routeWidth+".bk2"));
        assertEquals(count,movie.getFrameCount());
        var spots=routeWidth!=320
                ? Set.of(95,110,150,235,610,750,820,900,1000,1100,1700,2000,count-232,count-160,count-120,count-60)
                : main.equals("sonic")
                ? Set.of(95,110,150,235,610,750,1075,1110,1200,1950,1980,2120,2280,2390,2450)
                : Set.of(95,110,150,235,610,816,850,950,1014,1100,1950,2050,2610,2650,2720,2790);
        var checked=new HashSet<Integer>();
        var settings=new GameplayCaptureSession.Settings(width,main,"","off",null,
                0x4940,0x430,"0000000",false,false,null,null,false,37,false);
        var drawing = new RouteFrameDrawing("openggf.soz.drawEveryFrame");
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,1,settings);
            assertEquals(width, GameServices.camera().getWidth());
            assertEquals(main.equals("knuckles") ? CharacterKey.KNUCKLES : CharacterKey.SONIC,
                    session.player().characterKey());
            assertTrue(GameServices.sprites().getSidekicks().isEmpty());
            assertNotNull(session.player().getSpriteRenderer());
            int oldFloor=GameServices.level().getCurrentLevel().getMap().getValue(0,0x8F,0xB);
            for(int frame=0;frame<movie.getFrameCount();frame++) {
                session.step(movie.getFrame(frame));drawing.afterStep(session);
                assertFalse(session.player().getDead(),"death at "+frame);
                var manager=GameServices.level().getObjectManager();
                if(frame==240) assertNotEquals(oldFloor,
                        GameServices.level().getCurrentLevel().getMap().getValue(0,0x8F,0xB),
                        "the actual cork event replaces the rock corridor's floor");
                if(frame==count-112) {
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
                drawing.checkpoint(session);
                var saved=registry.capture();
                for(int n=1;n<=45;n++) {session.step(movie.getFrame(frame+n));drawing.draw(session);}
                var forward=registry.capture();
                registry.restore(saved);session.restoreInputHistory(movie.getFrame(frame));
                for(int n=1;n<=45;n++) {session.step(movie.getFrame(frame+n));drawing.draw(session);}
                var replay=registry.capture();
                assertEquals(forward.entries().keySet(),replay.entries().keySet());
                for(String key:forward.entries().keySet()) {
                    var differences=RewindSnapshotDiff.diffKey(key,forward.get(key),replay.get(key));
                    assertTrue(differences.isEmpty(), "input "+frame+" "+key+differences);
                }
                registry.restore(saved);session.restoreInputHistory(movie.getFrame(frame));
            }
            drawing.checkpoint(session);
            assertEquals(spots,checked);
            assertTrue(session.player().getCentreX()>0x4A40,"player crossed the lower door");
            assertEquals(8,GameServices.level().getCurrentZone()); assertEquals(1,GameServices.level().getCurrentAct());
            assertFalse(session.player().isObjectControlled());
            if(main.equals("knuckles")) assertEquals(width==528 ? 3 : 37,session.player().getRingCount(),
                    "the selected Knuckles route retains its independently captured ring count");
        }
    }
}
