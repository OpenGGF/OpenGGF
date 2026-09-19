package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.SszKnuxFinalBossCraneObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMasterEmeraldObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cold production ownership for SSZ2's placed crane and its boss allocation boundary. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszAct2FinaleHeadless {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void placedCraneReachesTheActTwoBossThroughItsNativeWait() {
        HeadlessTestFixture fixture = boot();
        SszKnuxFinalBossCraneObjectInstance crane = active(SszKnuxFinalBossCraneObjectInstance.class);
        assertNotNull(crane, "$B2 at ($180,$430) resolves through the production placement");

        // INIT owns move.w Camera_min_X,Camera_max_X.
        fixture.stepIdleFrames(1);
        assertEquals(GameServices.camera().getMinX() & 0xFFFF,
                GameServices.camera().getMaxX() & 0xFFFF);

        for (int frame = 0; frame < 0x240 && active(SszMechaSonicObjectInstance.class) == null; frame++) {
            var player = fixture.sprite();
            player.setDead(false);
            player.setHurt(false);
            player.setCentreX((short) 0x120);
            player.setCentreYPreserveSubpixel((short) 0x430);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setAir(true);
            fixture.stepIdleFrames(1);
        }

        SszMechaSonicObjectInstance boss = active(SszMechaSonicObjectInstance.class);
        assertNotNull(boss, "loc_7CB64's signed wait allocated Obj_SSZEndBoss");
        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) fixture.stepIdleFrames(1);
        assertTrue(boss.actTwoForTest(), "Current_act selects the act-2 init branch");
        assertTrue(S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().cutsceneFlag(4), "crane helper raised _unkFAB8 bit 4");
        assertEquals(0x1F, S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry())
                        .orElseThrow().cutsceneFlags() & 0x1F,
                "the hook and camera helper raised native phase bits 0..4");
        assertEquals(GameServices.camera().getMinX() & 0xFFFF,
                GameServices.camera().getMaxX() & 0xFFFF,
                "the final allocation boundary preserves the crane-owned camera lock");
    }

    @Test
    void cranePickupSurvivesCaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = boot();
        var state = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        for (int frame = 0; frame < 0x200 && !state.cutsceneFlag(2); frame++) {
            var player = fixture.sprite();
            player.setCentreX((short) 0x120);
            player.setCentreYPreserveSubpixel((short) 0x430);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setAir(true);
            fixture.stepIdleFrames(1);
        }
        assertTrue(state.cutsceneFlag(2), "loc_7CDD2 captured Knuckles");

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();
        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore during crane pickup");
        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay during crane pickup");
    }

    @Test
    void defeatHandshakeAdvancesThroughTheArenaFloorStage() {
        HeadlessTestFixture fixture = boot();
        var state = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        state.setForegroundRoutine(4);
        state.setEventsFg4Low(0xFF);

        fixture.stepIdleFrames(1);

        assertEquals(8, state.foregroundRoutine(), "loc_58AE0 addq.w #4,Events_routine_fg");
        assertEquals(0, state.eventsFg4(), "loc_58AE0 clr.w Events_fg_4");
    }

    @Test
    void firstHealthBarRunsTheForcedBridgeIntoTheDistinctSuperDispatcher() {
        HeadlessTestFixture fixture = boot();
        SszMechaSonicObjectInstance boss = runToBoss(fixture);
        for (int hit = 0; hit < 8; hit++) landOneHit(fixture, boss);

        assertTrue(boss.superPhaseForTest(), "loc_7B8E6 selected the act-2 transformation");
        assertEquals(0, boss.actTwoRoutePhaseForTest(), "the $BF fade wait begins first");
        assertEquals(8, boss.getCollisionProperty(), "the Super form receives a fresh eight hits");

        boolean enteredGraph = false;
        for (int frame = 0; frame < 0x900 && !enteredGraph; frame++) {
            var player = fixture.sprite();
            player.setDead(false);
            player.setHurt(false);
            player.setCentreYPreserveSubpixel((short) 0x430);
            player.setAir(false);
            fixture.stepIdleFrames(1);
            enteredGraph = boss.actTwoRoutePhaseForTest() < 0;
        }
        assertTrue(enteredGraph, () -> "loc_7BBE0 installed Obj_SSZ2_Boss; bridge phase="
                + boss.actTwoRoutePhaseForTest() + " routine=$"
                + Integer.toHexString(boss.routineForTest()));
        assertTrue((boss.routineForTest() & 1) == 0 && boss.routineForTest() <= 0x46,
                "SSZ2_Boss_Index owns one of its 36 even routine bytes");
        SszMasterEmeraldObjectInstance emerald = active(SszMasterEmeraldObjectInstance.class);
        assertNotNull(emerald, "loc_7BA38 allocated the arena Master Emerald");
        assertTrue(emerald.getX() != 0 && emerald.getY() != 0,
                "loc_7C818 positioned it from Camera_max_X and Camera_Y");
    }

    private static HeadlessTestFixture boot() {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 1)
                .withFreshLevelStartLifecycle()
                .startPosition((short) 0x120, (short) 0x430)
                .startPositionIsCentre()
                .build();
    }

    private static SszMechaSonicObjectInstance runToBoss(HeadlessTestFixture fixture) {
        for (int frame = 0; frame < 0x300; frame++) {
            var player = fixture.sprite();
            player.setCentreX((short) 0x120);
            player.setCentreYPreserveSubpixel((short) 0x430);
            player.setAir(true);
            fixture.stepIdleFrames(1);
            SszMechaSonicObjectInstance boss = active(SszMechaSonicObjectInstance.class);
            if (boss != null && boss.initExecutedForTest() && boss.routineForTest() != 0) return boss;
        }
        throw new AssertionError("the crane never allocated and released Mecha Sonic");
    }

    private static void landOneHit(HeadlessTestFixture fixture,
                                   SszMechaSonicObjectInstance boss) {
        int before = boss.getCollisionProperty();
        for (int frame = 0; frame < 0x500; frame++) {
            var player = fixture.sprite();
            player.setDead(false);
            player.setHurt(false);
            player.setInvulnerableFrames(0);
            player.setInvincibleFrames(2);
            player.setCentreX((short) boss.getX());
            player.setCentreYPreserveSubpixel((short) boss.getY());
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setAir(true);
            player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
            fixture.stepIdleFrames(1);
            if (boss.getCollisionProperty() == before - 1 || boss.superPhaseForTest()) return;
        }
        throw new AssertionError("a hit never landed through the production touch pass");
    }

    private static <T> T active(Class<T> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) return null;
        for (var object : manager.getActiveObjects()) {
            if (type.isInstance(object) && !object.isDestroyed()) return type.cast(object);
        }
        return null;
    }

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }
}
