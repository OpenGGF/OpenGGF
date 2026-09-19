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
import com.openggf.game.sonic3k.objects.SszEndingIslandMaskObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMasterEmeraldObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszSuperMechaProjectileChild;
import com.openggf.game.sonic3k.objects.bosses.SszSuperMechaMissilePodChild;
import com.openggf.game.sonic3k.objects.bosses.SszSuperMechaLaserChild;
import com.openggf.level.objects.ObjectSpawn;
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
    void seededEndingTransitionFillsMaskTilesAndCompletesDelayedDraw() {
        HeadlessTestFixture fixture = boot();
        var state = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        state.setForegroundRoutine(8);
        state.setEventsFg4(0x00FF);
        GameServices.camera().setY((short) 0x649);
        GameServices.camera().setYCopy((short) 0x649);

        fixture.stepIdleFrames(1);

        assertEquals(0x0C, state.foregroundRoutine(), "loc_58B20 advances to delayed draw stage");
        assertEquals(0x0F, state.endingDrawRows(), "Draw_delayed_rowcount starts at $F");
        assertNotNull(active(SszEndingIslandMaskObjectInstance.class),
                "loc_58B4C allocates the ending-island sprite mask");
        for (int tile = 0x7F0; tile <= 0x7FF; tile++) {
            var pattern = GameServices.level().getCurrentLevel().getPattern(tile);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                assertEquals(6, pattern.getPixel(x, y), "solid mask tile $" + Integer.toHexString(tile));
            }
        }

        fixture.stepIdleFrames(17);
        assertEquals(0x10, state.foregroundRoutine(), "loc_58B7C advances after all 16 rows");
        assertEquals(0, GameServices.camera().getY(), "loc_58B7C clears Camera_Y_pos");
        assertEquals(0, GameServices.camera().getYCopy(), "loc_58B7C clears Camera_Y_pos_copy");
    }

    @Test
    void superBurstUsesTheRomsEightIndexedVelocityRows() {
        int[][] expected = {
                {0, 0x400}, {0x2D4, 0x2D4}, {0x400, 0}, {0x2D4, -0x2D4},
                {0, -0x400}, {-0x2D4, -0x2D4}, {-0x400, 0}, {-0x2D4, 0x2D4}
        };
        for (int subtype = 0; subtype < expected.length; subtype++) {
            var shot = new SszSuperMechaProjectileChild(
                    new ObjectSpawn(0x200, 0x400, 0, subtype, 0, false, 0));
            assertEquals(expected[subtype][0], shot.xVelForTest(), "word_7D172 x row " + subtype);
            assertEquals(expected[subtype][1], shot.yVelForTest(), "word_7D172 y row " + subtype);
            assertEquals(0x87, shot.getCollisionFlags(), "ObjDat3_7D444 collision");
        }
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

    @Test
    void superDispatcherSurvivesCaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = boot();
        SszMechaSonicObjectInstance boss = runToBoss(fixture);
        for (int hit = 0; hit < 8; hit++) landOneHit(fixture, boss);
        runIntoSuperGraph(fixture, boss);

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();
        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore in Obj_SSZ2_Boss");
        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay in Obj_SSZ2_Boss");
    }

    @Test
    void superDashCarriesTheNativeMissilePodChild() {
        HeadlessTestFixture fixture = boot();
        SszMechaSonicObjectInstance boss = runToBoss(fixture);
        for (int hit = 0; hit < 8; hit++) landOneHit(fixture, boss);
        runIntoSuperGraph(fixture, boss);

        SszSuperMechaMissilePodChild pod = null;
        for (int frame = 0; frame < 0x100 && pod == null; frame++) {
            fixture.stepIdleFrames(1);
            pod = active(SszSuperMechaMissilePodChild.class);
        }
        assertNotNull(pod, "loc_7BEB0 allocated ChildObjDat_7D49A for the Super dash");
        fixture.stepIdleFrames(1);
        int expectedDx = boss.renderFlippedForTest() ? -0x14 : 0x14;
        assertEquals((boss.getX() + expectedDx) & 0xFFFF, pod.getX(),
                "loc_7C79C mirrors child_dx $14 with the parent");
        assertEquals((boss.getY() - 4) & 0xFFFF, pod.getY(),
                "ChildObjDat_7D49A owns child_dy -4");

        CompositeSnapshot before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.gameplayMode().getRewindRegistry().restore(before);
        sameSnapshot(before, fixture.gameplayMode().getRewindRegistry().capture(),
                "restore with attached missile pod");
        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, fixture.gameplayMode().getRewindRegistry().capture(),
                "forward replay with attached missile pod");
    }

    @Test
    void superLaserDetachesAndAimsAtKnuckles() {
        HeadlessTestFixture fixture = boot();
        SszMechaSonicObjectInstance boss = runToBoss(fixture);
        for (int hit = 0; hit < 8; hit++) landOneHit(fixture, boss);
        runIntoSuperGraph(fixture, boss);

        SszSuperMechaLaserChild laser = new SszSuperMechaLaserChild(
                new ObjectSpawn(boss.getX(), boss.getY(), 0, 0, 0, false, 0), boss);
        GameServices.level().getObjectManager().addDynamicObject(laser);
        fixture.sprite().setCentreX((short) (boss.getX() + 0x80));
        fixture.sprite().setCentreYPreserveSubpixel((short) (boss.getY() + 0x20));
        laser.update(0, fixture.sprite());
        int expectedDx = boss.renderFlippedForTest() ? 7 : -7;
        assertEquals((boss.getX() + expectedDx) & 0xFFFF, laser.getX(),
                "ChildObjDat_7D4AE keeps the armed laser at child_dx -7");
        assertEquals((boss.getY() - 8) & 0xFFFF, laser.getY(),
                "ChildObjDat_7D4AE keeps the armed laser at child_dy -8");

        for (int frame = 0; frame < 0x100 && !laser.launchedForTest(); frame++) {
            laser.update(frame + 1, fixture.sprite());
        }
        assertTrue(laser.launchedForTest(), () -> "byte_7D68C's $F4 callback entered loc_7C764; "
                + "anim_frame=" + laser.animationFrameForTest() + " mapping_frame="
                + laser.mappingFrameForTest() + " destroyed=" + laser.isDestroyed());
        assertEquals(0x400, Math.max(Math.abs(laser.xVelForTest()),
                Math.abs(laser.yVelForTest())), "sub_861D0 d5=2 fixes the dominant speed at $400");
        assertTrue(laser.xVelForTest() > 0, "the launched beam aims toward Knuckles");
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

    private static void runIntoSuperGraph(HeadlessTestFixture fixture,
                                          SszMechaSonicObjectInstance boss) {
        for (int frame = 0; frame < 0x900; frame++) {
            var player = fixture.sprite();
            player.setDead(false);
            player.setHurt(false);
            player.setCentreYPreserveSubpixel((short) 0x430);
            player.setAir(false);
            fixture.stepIdleFrames(1);
            if (boss.actTwoRoutePhaseForTest() < 0) return;
        }
        throw new AssertionError("the forced bridge never installed Obj_SSZ2_Boss");
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
