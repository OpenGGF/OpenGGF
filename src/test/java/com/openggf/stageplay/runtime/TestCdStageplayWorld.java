package com.openggf.stageplay.runtime;

import com.openggf.game.soniccd.*;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.*;
import com.openggf.stageplay.StageplayDocument.*;
import com.openggf.tools.HeadlessGameBoot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCdStageplayWorld {
    @Test void nativePerformerUsesSelectedPastProgramAwayFromStart() throws Exception {
        var scene = StageplayDocument.empty("scd", 0, 1, 640, 300, 320, 224)
                .withActors(List.of(new Actor("hero", "Sonic", "scd", "sonic", List.of(
                        new ActorKey(0, 800, 380, 0, -1, Facing.LEFT, true, 1, Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero", false, 0, List.of())));
        try (var library = SceneRomLibrary.open(Map.of("scd", CdTestMedia.usaCue()));
             var boot = new HeadlessGameBoot(320, 224)) {
            boot.setBootToTitle(true);
            boot.boot(library.source("scd"), library.module("scd"), 0, 1);
            try (var world = StageplayLiveWorld.adoptOffline(library, com.openggf.game.session.SessionManager.getCurrentGameplayMode())) {
            var frame = world.resolve(scene, StageplayTimeline.sample(scene, 0));
            assertEquals("scd", frame.set().gameId());
            assertEquals(1, frame.set().act());
            assertEquals(800, com.openggf.game.GameServices.sprites().getMainPlayable().getCentreX());
            assertEquals(CdStagePayload.R11B, assertInstanceOf(com.openggf.game.soniccd.level.CdLevel.class,
                    com.openggf.game.GameServices.level().getCurrentLevel()).payload());
            }
        }
    }
    @Test void nativeControllerWalksBothDirections() throws Exception {
        var scene = StageplayDocument.empty("scd", 0, 1, 640, 400, 320, 224).withDurationFrames(240)
                .withActors(List.of(new Actor("hero", "Sonic", "scd", "sonic", List.of(
                        new ActorKey(0, 800, 549, 0, -1, Facing.LEFT, true, 1, Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero", false, 0, List.of(
                        new PuppetCommand("face-right",24,PuppetCommandKind.FACE_RIGHT,0,0,0,0),
                        new PuppetCommand("right",32,PuppetCommandKind.MOVE_TO,880,549,1,0),
                        new PuppetCommand("stop",80,PuppetCommandKind.STOP,0,0,0,0),
                        new PuppetCommand("face-left",144,PuppetCommandKind.FACE_LEFT,0,0,0,0),
                        new PuppetCommand("left",152,PuppetCommandKind.MOVE_TO,760,549,1,0)))))
                .withWorldCommands(List.of(new WorldCommand("clear",0,WorldCommandKind.CLEAR_OBJECTS,"",ObjectTarget.none())));
        try (var library = SceneRomLibrary.open(Map.of("scd", CdTestMedia.usaCue()));
             var boot = new HeadlessGameBoot(320,224)) {
            boot.setBootToTitle(true); boot.boot(library.source("scd"),library.module("scd"),0,1);
            try (var world = StageplayLiveWorld.adoptOffline(library,com.openggf.game.session.SessionManager.getCurrentGameplayMode())) {
                world.resolve(scene,StageplayTimeline.sample(scene,25));
                var player = com.openggf.game.GameServices.sprites().getMainPlayable();
                assertEquals(com.openggf.physics.Direction.RIGHT,player.getDirection(),"Logical face right");
                world.resolve(scene,StageplayTimeline.sample(scene,80));
                assertTrue(player.getCentreX()>820,"CD scripted native input must move right: "+player.getCentreX()+" y="+player.getCentreY()+" held="+player.getLogicalInputState()+" locked="+player.isControlLocked()+" object="+player.isObjectControlled()+" air="+player.getAir()+" speed="+player.getXSpeed());
                assertEquals(com.openggf.physics.Direction.RIGHT,player.getDirection());
                world.resolve(scene,StageplayTimeline.sample(scene,145));
                assertEquals(com.openggf.physics.Direction.LEFT,player.getDirection(),"Logical face left");
                world.resolve(scene,StageplayTimeline.sample(scene,230));
                assertTrue(player.getCentreX()<820,"CD scripted native input must move left: "+player.getCentreX());
                assertEquals(com.openggf.physics.Direction.LEFT,player.getDirection());
            }
        }
    }
    @Test void selectedPastProgramAdmitsPlayerGroundSurvey() throws Exception {
        try (var library = SceneRomLibrary.open(Map.of("scd",CdTestMedia.usaCue()));
             var resources = new StageplayResources(library,false)) {
            var report = resources.ground(new SetSource("scd",0,1,"",""),
                    new com.openggf.stageplay.StageplayGround.Batch(List.of(
                        new com.openggf.stageplay.StageplayGround.Query("floor",800,540,580,
                            new com.openggf.stageplay.StageplayGround.Player("scd","sonic",0))),20000),()->false);
            assertEquals(com.openggf.stageplay.StageplayGround.Outcome.FOUND,report.answer("floor").outcome(),report.toString());
        }
    }
}
