package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterBeamInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless Task 8 coverage for CNZ's Knuckles teleporter route and bounded
 * end-boss defeat handoff.
 *
 * <p>These tests deliberately stop at the Task 8 scope the plan and ROM notes
 * agreed on:
 * <ul>
 *   <li>{@code Obj_CNZTeleporter} must clamp and lock the player, queue the
 *   teleporter handoff, and spawn the shared {@code Obj_TeleporterBeam}</li>
 *   <li>The teleporter route must <strong>not</strong> spawn the egg capsule</li>
 *   <li>{@code Obj_CNZEndBoss} only owns startup/defeat handoff for now:
 *   clear the boss flag, widen camera bounds, spawn the CNZ capsule wrapper,
 *   and restore player control</li>
 * </ul>
 *
 * <p>Full CNZ end-boss attack-state parity is intentionally deferred.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzTeleporterRouteHeadless {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        com.openggf.game.session.SessionManager.clear();
    }

    /**
     * ROM anchors:
     * {@code Obj_CNZTeleporter} arms when Knuckles reaches the late Act 2 route,
     * clamps airborne overshoot back to {@code x=$4A40}, zeroes the movement
     * speeds, locks control, queues {@code ArtKosM_CNZTeleport}, then hands off
     * to {@code Obj_CNZTeleporterMain}. That main phase waits for the art load
     * and for Knuckles to be grounded before spawning the shared
     * {@code Obj_TeleporterBeam}.
     *
     * <p>Task 8 must preserve one critical boundary from the verified ROM notes:
     * the teleporter route does <strong>not</strong> spawn the egg capsule. That
     * handoff belongs only to the later {@code Obj_CNZEndBoss} defeat path.
     */
    @Test
    void knucklesTeleporterRequiresPublishedRouteBeforeLockingControl() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();

        CnzTeleporterInstance teleporter = new CnzTeleporterInstance(
                new ObjectSpawn(0x4A40, 0x0A38, 0, 0, 0, false, 0));
        teleporter.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(teleporter);

        fixture.sprite().setCentreX((short) 0x4A50);
        fixture.sprite().setCentreY((short) 0x0A30);
        fixture.sprite().setAir(true);
        fixture.sprite().setJumping(true);
        fixture.sprite().setXSpeed((short) 0x180);
        fixture.sprite().setGSpeed((short) 0x200);

        fixture.stepIdleFrames(1);
        assertFalse(fixture.sprite().isControlLocked(),
                "Obj_CNZTeleporter should stay dormant until the CNZ route seam is published externally");
        assertFalse(getCnzEvents().isTeleporterBeamSpawned(),
                "The shared beam handoff must remain inactive before the route is published");

        Sonic3kCNZEvents events = getCnzEvents();
        events.beginKnucklesTeleporterRoute();

        fixture.sprite().setCentreX((short) 0x4A50);
        fixture.sprite().setCentreY((short) 0x0A30);
        fixture.sprite().setAir(true);
        fixture.sprite().setJumping(true);
        fixture.sprite().setXSpeed((short) 0x180);
        fixture.sprite().setGSpeed((short) 0x200);

        fixture.stepIdleFrames(1);
        assertTrue(fixture.sprite().isControlLocked(),
                "Obj_CNZTeleporter should mirror Ctrl_1_locked by immediately removing player control");
        assertEquals(0x4A40, fixture.sprite().getCentreX(),
                "Airborne overshoot past $4A40 should clamp back to the teleporter beam X");
        assertEquals(0, fixture.sprite().getXSpeed(),
                "The teleporter arming frame should clear x_vel before the beam sequence begins");
        assertEquals(0, fixture.sprite().getGSpeed(),
                "The teleporter arming frame should also clear ground_vel");
        assertEquals(0x4750, fixture.camera().getMinX(),
                "The Knuckles-only route should publish the late Act 2 left camera clamp");
        assertEquals(0x48E0, fixture.camera().getMaxX(),
                "The Knuckles-only route should publish the late Act 2 right camera clamp");

        fixture.sprite().setAir(false);
        fixture.sprite().setJumping(false);
        fixture.stepIdleFrames(1);

        assertTrue(isObjectPresent(CnzTeleporterBeamInstance.class),
                "Obj_CNZTeleporterMain should spawn the shared beam once the player is grounded");
        assertTrue(events.isTeleporterBeamSpawned(),
                "The CNZ event bridge should record the explicit beam-handoff seam");
        assertFalse(isObjectPresent(CnzEggCapsuleInstance.class),
                "The teleporter route must not spawn the capsule; that belongs to Obj_CNZEndBoss");

        fixture.stepIdleFrames(CnzTeleporterBeamInstance.PLAYER_CAPTURE_COUNTER);
        assertTrue(fixture.sprite().isObjectControlled(),
                "At beam counter 8, Obj_CNZTeleporterMain should take full player object control");
        assertFalse(fixture.sprite().isObjectControlAllowsCpu(),
                "Full bit-7 teleporter control should not leave CPU movement enabled");
        assertTrue(fixture.sprite().isObjectControlSuppressesMovement(),
                "Full bit-7 teleporter control should suppress player movement");
        assertTrue(fixture.sprite().isTouchResponseSuppressedByObjectControl(),
                "Full bit-7 teleporter control should suppress normal touch responses");
    }

    /**
     * ROM anchors:
     * Task 8 only claims the defeat handoff from {@code Obj_CNZEndBoss}. When
     * the boss sequence is finished, it must clear {@code Boss_flag}, widen the
     * boss camera clamp, spawn the egg capsule, and restore level music/control.
     *
     * <p>This test intentionally does not claim attack-state parity, hit timing,
     * or the full cutscene choreography.
     */
    @Test
    void cnzEndBossSlotStaysPassiveUntilExternalBossStateExists() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();

        CnzEndBossInstance boss = spawnCnzEndBossForTest();
        fixture.stepIdleFrames(1);

        assertFalse(getCnzEvents().isBossFlag(),
                "The promoted CNZ end-boss slot must not claim Boss_flag on its own");
        assertEquals(0, GameServices.gameState().getCurrentBossId(),
                "Task 8 keeps boss-mode ownership on the external CNZ event seam until later attack work exists");
        assertFalse(isObjectPresent(CnzEggCapsuleInstance.class),
                "Without an external boss-mode seam and defeat signal, the bounded wrapper must stay inert");

        boss.setDestroyed(true);
    }

    /**
     * ROM anchors:
     * Task 8 only claims the defeat handoff from {@code Obj_CNZEndBoss}. When
     * the boss sequence is finished, it must clear {@code Boss_flag}, widen the
     * boss camera clamp, spawn the egg capsule, and restore level music/control.
     *
     * <p>This test intentionally does not claim attack-state parity, hit timing,
     * or the full cutscene choreography.
     */
    @Test
    void cnzEndBossDefeatHandoffClearsBossFlagWidensCameraAndSpawnsCapsule() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();

        Sonic3kCNZEvents events = getCnzEvents();
        events.setBossFlag(true);
        GameServices.gameState().setCurrentBossId(Sonic3kObjectIds.CNZ_END_BOSS);

        fixture.camera().setMaxX((short) 0x48E0);
        fixture.sprite().setControlLocked(true);
        fixture.sprite().setObjectControlled(true);
        fixture.sprite().setHidden(true);

        CnzEndBossInstance boss = spawnCnzEndBossForTest();
        defeatCnzEndBossWithPlayerAttacks(boss, fixture.sprite());

        fixture.stepIdleFrames(1);

        CnzEggCapsuleInstance capsule = findObject(CnzEggCapsuleInstance.class);
        assertTrue(capsule != null,
                "The bounded Task 8 defeat handoff should spawn the CNZ-local egg capsule wrapper");

        capsule.forceResultsCompleteForTest();
        fixture.sprite().setCentreX((short) 0x4A20);
        boss.update(1, fixture.sprite());
        assertEquals(1, boss.getPostCapsuleReleaseCountForTest(),
                "CNZ post-capsule control/music restore should fire once when results complete");

        for (int i = 0; i < 4; i++) {
            boss.update(2 + i, fixture.sprite());
        }
        assertEquals(1, boss.getPostCapsuleReleaseCountForTest(),
                "Waiting left of the launcher trigger must not replay CNZ2 music/control restore every frame");

        assertFalse(fixture.sprite().isControlLocked(),
                "Capsule release should return player control once the results screen has finished");
        assertFalse(fixture.sprite().isObjectControlled(),
                "Capsule release should clear object control instead of leaving the player frozen");
        assertFalse(fixture.sprite().isHidden(),
                "If the teleporter route hid the player earlier, capsule release must reveal them again");
        assertFalse(events.isBossFlag(),
                "Task 8 owns clearing Boss_flag so later CNZ event logic can leave boss mode");
        assertEquals(0, GameServices.gameState().getCurrentBossId(),
                "Defeat handoff should clear Current_Boss_ID alongside Boss_flag");
        assertTrue(fixture.camera().getMaxX() > 0x48E0,
                "Task 8 should widen the CNZ boss camera clamp during the capsule handoff");
    }

    @Test
    void cnzPostCapsuleRouteSpawnsCannonAndRequestsIczAfterLaunchThreshold() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();

        Sonic3kCNZEvents events = getCnzEvents();
        events.setBossFlag(true);
        GameServices.gameState().setCurrentBossId(Sonic3kObjectIds.CNZ_END_BOSS);

        CnzEndBossInstance boss = spawnCnzEndBossForTest();
        defeatCnzEndBossWithPlayerAttacks(boss, fixture.sprite());
        fixture.stepIdleFrames(1);

        CnzEggCapsuleInstance capsule = findObject(CnzEggCapsuleInstance.class);
        assertTrue(capsule != null,
                "Obj_CNZEndBoss loc_6E6E4 should create the egg capsule before the launcher route");

        capsule.forceResultsCompleteForTest();
        fixture.sprite().setCentreX((short) 0x4A30);
        boss.update(1, fixture.sprite());

        CnzCannonInstance cannon = findObject(CnzCannonInstance.class);
        assertTrue(cannon != null,
                "Obj_CNZEndBoss loc_6E778 should spawn Obj_CNZCannon at the launcher handoff");

        fixture.sprite().setCentreX((short) 0x4B20);
        fixture.sprite().setCentreY((short) 0x0280);
        fixture.sprite().setAir(false);
        fixture.sprite().setJumping(false);
        cannon.onSolidContact(fixture.sprite(), new SolidContact(true, false, false, true, false), 0);
        invokeCannonLaunchReadyHook(cannon);
        assertTrue(cannon.isEndSequenceLaunchReady(),
                "The end-sequence cannon should capture Sonic before the boss forces the jump input");

        for (int frame = 0; frame < 210 && fixture.sprite().isObjectControlled(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertFalse(fixture.sprite().isObjectControlled(),
                "Obj_CNZEndBoss loc_6E7E4 should force the cannon launch instead of waiting for manual input");

        fixture.sprite().setCentreY((short) (fixture.camera().getY() + 0x10));
        boss.update(2, fixture.sprite());

        assertTrue(GameServices.level().consumeZoneActRequest(),
                "Obj_CNZEndBoss loc_6E80C should request StartNewLevel once the launcher carries Sonic offscreen");
        int requestedZone = GameServices.level().getRequestedZone();
        int requestedAct = GameServices.level().getRequestedAct();
        assertEquals(Sonic3kZoneIds.ZONE_ICZ, requestedZone);
        assertEquals(0, requestedAct);
        assertTrue(GameServices.level().isLevelInactiveForTransition(),
                "The ICZ request should freeze level updates while the fade transition owns the load");
        assertEquals(0, fixture.sprite().getXSpeed(),
                "The frozen fade window must not leave Sonic carrying the CNZ cannon launch x velocity into ICZ");
        assertEquals(0, fixture.sprite().getYSpeed(),
                "The frozen fade window must not leave Sonic visibly flying upward from the CNZ cannon");
        assertFalse(fixture.sprite().getAir(),
                "The ICZ transition request should neutralize airborne launcher state before the level freezes");
        assertTrue(fixture.sprite().isControlLocked(),
                "The neutral transition pose should keep control locked until the ICZ load reinitializes the player");
        assertTrue(fixture.sprite().isHidden(),
                "Sonic should be hidden during the frozen fade instead of remaining visible off screen");

        GameServices.level().loadZoneAndAct(requestedZone, requestedAct);

        assertFalse(fixture.sprite().isHidden(),
                "ICZ load should clear the neutral fade pose and own the new player state");
        assertEquals(0x0800, fixture.sprite().getXSpeed(),
                "ICZ load must replace the neutral fade pose with Obj_LevelIntroICZ1's snowboard startup x velocity");
        assertEquals(0x0280, fixture.sprite().getYSpeed(),
                "ICZ load must replace the neutral fade pose with Obj_LevelIntroICZ1's snowboard startup y velocity");
        assertTrue(fixture.sprite().getAir(),
                "ICZ load should hand off to the airborne snowboard intro state, not carry CNZ launcher state");
    }

    /**
     * Task 7 still left the CNZ end-boss slot placeholder-backed. Task 8 should
     * promote that explicit registry slot to the bounded CNZ end-boss wrapper so
     * the route and defeat handoff can be exercised without touching unrelated
     * zones that also reuse object ID {@code $A7}.
     */
    @Test
    void registryPromotesCnzEndBossSlotForTask8() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance endBoss = registry.create(
                new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));

        assertInstanceOf(CnzEndBossInstance.class, endBoss);
    }

    private Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return manager.getCnzEvents();
    }

    private CnzEndBossInstance spawnCnzEndBossForTest() {
        CnzEndBossInstance boss = new CnzEndBossInstance(
                new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(boss);
        return boss;
    }

    private void defeatCnzEndBossWithPlayerAttacks(CnzEndBossInstance boss, AbstractPlayableSprite player) {
        TouchResponseResult hit = new TouchResponseResult(0x06, 0, 0, TouchCategory.ENEMY);
        for (int i = 0; i < 8; i++) {
            boss.onPlayerAttack(player, hit);
            for (int cooldown = 0; cooldown < 0x20; cooldown++) {
                boss.update(cooldown, player);
            }
        }
    }

    private boolean isObjectPresent(Class<?> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(type::isInstance);
    }

    private <T> T findObject(Class<T> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    private static void invokeCannonLaunchReadyHook(CnzCannonInstance cannon) {
        try {
            java.lang.reflect.Method method =
                    CnzCannonInstance.class.getDeclaredMethod("setLaunchDelayFramesForTest", int.class);
            method.setAccessible(true);
            method.invoke(cannon, 0);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to ready CNZ cannon for route test", e);
        }
    }
}
