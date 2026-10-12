package com.openggf.tests;

import com.openggf.game.*;
import com.openggf.game.mutators.*;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.MutatorPlacementClassifier;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Three real ROM assembly/respawn paths; source lists remain comparison identities. */
class TestMutatorNativeLevelAssembly {
    @AfterEach void cleanup() { SessionManager.clear(); }
    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1() throws Exception { check(); }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2() throws Exception { check(); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3k() throws Exception { check(); }

    private void check() throws Exception {
        var nativeModule = GameModuleRegistry.getCurrent();
        var rom = TestEnvironment.currentRom();
        var policies = new LevelMutatorTestWorld(nativeModule);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(policies.module());
        SessionManager.openGameplaySession(nativeModule, policies.module(), null);
        TestEnvironment.activeGameplayMode();
        GameServices.rom().setRom(rom);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        var level = GameServices.level();
        var sourceObjects = List.copyOf(level.getCurrentLevel().getObjects());
        var sourceRings = List.copyOf(level.getCurrentLevel().getRings());
        var classifier = (MutatorPlacementClassifier) nativeModule.createObjectRegistry();
        var monitor = sourceObjects.stream().filter(s -> classifier.monitorContent(s) != null).findFirst().orElseThrow();
        var checkpoint = level.getCheckpointState();
        assertInstanceOf(CheckpointState.class, checkpoint);
        ((CheckpointState) checkpoint).restoreFromSaved(monitor.x(), monitor.y(), monitor.x(), monitor.y(), 1);
        ((CheckpointState) checkpoint).saveRingState(77, 2);
        var denied = new LevelMutatorPolicy(EnumSet.allOf(MonitorContent.class), true, true, true, true);
        policies.request(denied);
        assertEquals(LevelMutatorPolicy.STOCK, policies.policy(), "pending LOAD edit does not touch the running level");
        level.respawnPlayer();
        assertEquals(denied, policies.policy());
        assertFalse(level.getCheckpointState().isActive(), "death load drops the bank after LOAD admission");
        assertEquals(0, fixture.sprite().getRingCount());
        assertEquals(sourceObjects, level.getCurrentLevel().getObjects());
        assertEquals(sourceRings, level.getCurrentLevel().getRings());
        GameServices.camera().setX((short) monitor.x());
        GameServices.camera().setY((short) monitor.y());
        var objects = level.getObjectManager();
        objects.reset(monitor.x());
        assertTrue(objects.getActiveObjects().stream().noneMatch(o -> classifier.monitorContent(o.getSpawn()) != null));
        assertTrue(level.getRingManager().getActiveSpawns().isEmpty());
        var transitions = level.getTransitions();
        transitions.requestSpecialStageEntry();
        assertFalse(transitions.isSpecialStageRequested());
        policies.request(LevelMutatorPolicy.STOCK);
        level.restartCurrentLevelFromConfiguration();
        assertEquals(LevelMutatorPolicy.STOCK, policies.policy());
        GameServices.camera().setX((short) monitor.x());
        GameServices.camera().setY((short) monitor.y());
        level.getObjectManager().reset(monitor.x());
        assertTrue(level.getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> o.getSpawn().equals(monitor)), "disabled filters recreate the original native placement");
    }
}
