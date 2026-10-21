package com.openggf.stageplay.runtime;

import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayLiveWorldRom {
    @org.junit.jupiter.api.BeforeEach void logicGraphics() { com.openggf.game.GameServices.graphics().initHeadless(); }
    @Test void twoPerformersSharePlacedPlatformAndWholeWorldReplay() throws Exception {
        var bootstrap = com.openggf.game.GameModuleRegistry.getBootstrapDefault();
        try (var library = SceneRomLibrary.open(Map.of("s3k", Path.of(System.getProperty("s3k.rom.path"))));
             var world = StageplayLiveWorld.adoptOffline(library, SessionManager.getCurrentGameplayMode())) {
            var doc = platform();
            var at35 = world.resolve(doc, StageplayTimeline.sample(doc, 35));
            assertSame(bootstrap, com.openggf.game.GameModuleRegistry.getBootstrapDefault(),
                    "An offline native world owns its module through WorldSession, not bootstrap state");
            assertSame(SessionManager.requireCurrentGameModule(), com.openggf.game.GameModuleRegistry.getCurrent());
            assertEquals(2, at35.world().actors().size());
            assertTrue(at35.world().actors().stream().allMatch(a -> a.onObject()), at35.toString());
            assertTrue(at35.world().actors().stream().allMatch(a -> a.ridingLayoutIndex() == 179));
            var platform35 = at35.world().objects().stream().filter(o -> o.layoutIndex() == 179).findFirst().orElseThrow();
            var at100 = world.resolve(doc, StageplayTimeline.sample(doc, 100));
            var platform100 = at100.world().objects().stream().filter(o -> o.layoutIndex() == 179).findFirst().orElseThrow();
            assertNotEquals(platform35.x(), platform100.x());
            assertEquals(platform100.x() - platform35.x(), at100.actors().getFirst().x() - at35.actors().getFirst().x());
            assertSame(at100, world.resolve(doc, StageplayTimeline.sample(doc, 100)), "sampling twice cannot tick");
            world.resolve(doc, StageplayTimeline.sample(doc, 10));
            assertEquals(at100, world.resolve(doc, StageplayTimeline.sample(doc, 100)), "native world + director must replay identically");
            assertEquals(65, at100.world().levelFrame() - at35.world().levelFrame());
        }
        assertSame(bootstrap, com.openggf.game.GameModuleRegistry.getBootstrapDefault(),
                "Closing an offline world must preserve the pre-session bootstrap module");
    }

    @Test void independentControllersArePublishedBeforeNativeObjectAndZonePhasesWithoutTicking() throws Exception {
        try(var library=SceneRomLibrary.open(Map.of("s3k",Path.of(System.getProperty("s3k.rom.path"))));
            var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var scene=platform(); world.resolve(scene,StageplayTimeline.sample(scene,0));
            var sprites=com.openggf.game.GameServices.sprites(); var main=sprites.getMainPlayable(); var second=sprites.getRegisteredSidekicks().getFirst();
            int clock=com.openggf.game.GameServices.level().getObjectManager().getVblaCounter(), x=main.getCentreX();
            var p1=com.openggf.control.PlayerInputState.of(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_UP,0,
                    com.openggf.control.InputActionMasks.ACTION_C,com.openggf.control.InputActionMasks.ACTION_C,false,false);
            var p2=com.openggf.control.PlayerInputState.of(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT,0,0,0,false,false);
            var inputs=Map.of(main.getCode(),p1,second.getCode(),p2);
            sprites.withScriptedInputs(inputs,handler -> {
                assertTrue(sprites.getJoypadPressSnapshot().player1AnyActionPressed());
                assertEquals(0,sprites.getJoypadPressSnapshot().player2ActionPressed());
                assertTrue(main.isUpPressed()); assertTrue(main.isJumpPressed()); assertFalse(main.isLeftPressed());
                assertTrue(second.isLeftPressed()); assertFalse(second.isUpPressed()); assertFalse(second.isJumpPressed());
                assertThrows(IllegalStateException.class,()->sprites.withScriptedInputs(inputs,ignored->{ }));
            });
            assertEquals(clock,com.openggf.game.GameServices.level().getObjectManager().getVblaCounter()); assertEquals(x,main.getCentreX());
            assertDoesNotThrow(()->sprites.withScriptedInputs(inputs,ignored->{ }));
        }
    }

    @Test void boundedWaitGatesLaterInputAndReportsTimeoutThroughReplay() throws Exception {
        var scene=platform().withPuppets(List.of(new PuppetTrack("lead",false,0,List.of(
                new PuppetCommand("brake",0,PuppetCommandKind.STOP,0,0,0,0),
                new PuppetCommand("wrong-platform",40,PuppetCommandKind.WAIT,0,0,0,0,
                        new ObjectTarget(166,74,4,0,0),WaitCondition.ON_OBJECT,5),
                new PuppetCommand("leave-after-wait",41,PuppetCommandKind.JUMP,0,0,0,8))),
                new PuppetTrack("partner",false,0,List.of())));
        try(var library=SceneRomLibrary.open(Map.of("s3k",Path.of(System.getProperty("s3k.rom.path"))));
            var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var waiting=world.resolve(scene,StageplayTimeline.sample(scene,44));
            assertEquals(StageplayTimeline.PuppetStatus.WAITING,waiting.puppets().getFirst().status());
            assertTrue(waiting.world().actors().getFirst().onObject());
            var timedOut=world.resolve(scene,StageplayTimeline.sample(scene,45));
            assertTrue(timedOut.world().events().stream().anyMatch(e->e.kind().equals("WAIT_TIMED_OUT")));
            assertTrue(timedOut.puppets().getFirst().airborne());
            assertTrue(timedOut.puppets().getFirst().velocityY()<0);
            world.resolve(scene,StageplayTimeline.sample(scene,30));
            assertEquals(timedOut,world.resolve(scene,StageplayTimeline.sample(scene,45)));
        }
    }

    @Test void despawnedStableTargetIsReportedWithoutMovingTheActorToItsOldPosition() throws Exception {
        var scene=platform().withPuppets(List.of(new PuppetTrack("lead",false,0,List.of(
                new PuppetCommand("follow",0,PuppetCommandKind.MOVE_TO,0,0,.4,0,
                        new ObjectTarget(179,74,1,0,-35),WaitCondition.NONE,200))),
                new PuppetTrack("partner",false,0,List.of())));
        try(var library=SceneRomLibrary.open(Map.of("s3k",Path.of(System.getProperty("s3k.rom.path"))));
            var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var before=world.resolve(scene,StageplayTimeline.sample(scene,35));
            assertEquals("ACTIVE",before.world().actors().getFirst().targetStatus());
            var platform=com.openggf.game.GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o->o.getSpawn()!=null && o.getSpawn().layoutIndex()==179).findFirst().orElseThrow();
            ((com.openggf.level.objects.AbstractObjectInstance)platform).setDestroyed(true);
            var after=world.resolve(scene,StageplayTimeline.sample(scene,36));
            assertEquals("DESPAWNED",after.world().actors().getFirst().targetStatus());
            assertEquals(StageplayTimeline.PuppetStatus.TARGET_UNAVAILABLE,after.puppets().getFirst().status());
            assertTrue(Math.abs(after.actors().getFirst().x()-before.actors().getFirst().x())<8);
        }
    }

    @Test void directorDeadZoneHandoffAndShakeUseNativePositionsAndReplay() throws Exception {
        var scene=platform().withCamera(List.of(
                new CameraKey(0,5280,1888,1,Interpolation.HOLD,new CameraFollow(List.of("lead","partner"),200,200,0,.2,0,0)),
                new CameraKey(120,5280,1888,1,Interpolation.HOLD,new CameraFollow(List.of("partner"),0,0,0,1,16,-8))))
                .withCameraEffects(List.of(new CameraEffect("shake",0,230,3,4,8,7)));
        try(var library=SceneRomLibrary.open(Map.of("s3k",Path.of(System.getProperty("s3k.rom.path"))));
            var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var held=world.resolve(scene,StageplayTimeline.sample(scene,100));
            assertEquals(5280,held.baseCamera().x()); assertEquals(1888,held.baseCamera().y());
            var authored=StageplayTimeline.sample(scene,100);
            assertEquals(authored.camera().x()-authored.baseCamera().x(),held.camera().x()-held.baseCamera().x(),1e-9);
            var handed=world.resolve(scene,StageplayTimeline.sample(scene,180));
            var partner=handed.actors().get(1);
            assertEquals(Math.floor(partner.x())-160+16,handed.baseCamera().x());
            assertEquals(Math.floor(partner.y())-112-8,handed.baseCamera().y()); assertEquals(1,handed.camera().zoom());
            world.resolve(scene,StageplayTimeline.sample(scene,30));
            assertEquals(handed,world.resolve(scene,StageplayTimeline.sample(scene,180)));
        }
    }

    @Test void seekingAcrossShotBoundaryStartsTheSameNativeClocksAndPopulation() throws Exception {
        var first=platform();
        var actors=first.actors().stream().map(a -> {
            var key=a.keys().getFirst();
            return new Actor(a.id(),a.name(),a.gameId(),a.character(),List.of(key,
                    new ActorKey(120,key.x(),key.y(),0,-1,false,true,1,Interpolation.HOLD)));
        }).toList();
        var scene=new StageplayDocument(StageplayDocument.VERSION,"Boundary replay",240,320,224,first.set(),actors,
                List.of(first.camera().getFirst(),new CameraKey(120,5280,1888,1,Interpolation.HOLD)),List.of(),List.of(),List.of(),
                List.of(new SceneChange("second",120,first.set(),Transition.CUT,0)),List.of(),List.of(),List.of(),first.puppets());
        try(var library=SceneRomLibrary.open(Map.of("s3k",Path.of(System.getProperty("s3k.rom.path"))));
            var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            world.resolve(scene,StageplayTimeline.sample(scene,80));
            var second=world.resolve(scene,StageplayTimeline.sample(scene,180));
            world.resolve(scene,StageplayTimeline.sample(scene,20));
            assertEquals(second,world.resolve(scene,StageplayTimeline.sample(scene,180)));
        }
    }

    @Test void liveWorldCannotReplaceAnInteractiveSession() throws Exception {
        var current = SessionManager.getCurrentGameplayMode();
        try (var library = SceneRomLibrary.open(Map.of("s3k", Path.of(System.getProperty("s3k.rom.path"))))) {
            assertThrows(IllegalStateException.class, () -> StageplayLiveWorld.openOffline(library));
            assertSame(current, SessionManager.getCurrentGameplayMode());
        }
    }

    static StageplayDocument platform() {
        var actors = List.of(new Actor("lead", "Sonic", "s3k", "sonic", List.of(key(0x1534, 0x790))),
                new Actor("partner", "Tails", "s3k", "tails", List.of(key(0x154C, 0x790))));
        return new StageplayDocument(StageplayDocument.VERSION, "Shared platform", 240, 320, 224,
                new SetSource("s3k", 11, 1, "", "", WorldPolicy.LIVE), actors,
                List.of(new CameraKey(0, 0x14A0, 0x760, 1, Interpolation.HOLD)), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(new PuppetTrack("lead", false, 0, List.of()),
                new PuppetTrack("partner", false, 0, List.of())));
    }
    private static ActorKey key(int x, int y) { return new ActorKey(0, x, y, 0, -1, false, true, 1, Interpolation.HOLD); }
}
