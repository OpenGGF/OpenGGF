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
}
