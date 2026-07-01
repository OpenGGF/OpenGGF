package com.openggf.tests.game;

import com.openggf.game.CollisionModel;
import com.openggf.game.GameModule;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.rules.CameraRules;
import com.openggf.game.rules.CollisionRules;
import com.openggf.game.rules.DrowningBubbleRules;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.ObjectInteractionRules;
import com.openggf.game.rules.PlayerAnimationRules;
import com.openggf.game.rules.PlayerCapabilityRules;
import com.openggf.game.rules.PlayerMovementRules;
import com.openggf.game.rules.PowerUpRules;
import com.openggf.game.rules.RingRules;
import com.openggf.game.rules.SidekickCpuRules;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGameRulesFromPhysicsFeatureSet {

    private static final String PLAYER_MOVEMENT = PlayerMovementRules.class.getSimpleName();
    private static final String PLAYER_CAPABILITY = PlayerCapabilityRules.class.getSimpleName();
    private static final String COLLISION = CollisionRules.class.getSimpleName();
    private static final String PLAYER_ANIMATION = PlayerAnimationRules.class.getSimpleName();
    private static final String CAMERA = CameraRules.class.getSimpleName();
    private static final String RING = RingRules.class.getSimpleName();
    private static final String OBJECT_INTERACTION = ObjectInteractionRules.class.getSimpleName();
    private static final String SIDEKICK_CPU = SidekickCpuRules.class.getSimpleName();
    private static final String POWER_UP = PowerUpRules.class.getSimpleName();
    private static final String DROWNING_BUBBLE = DrowningBubbleRules.class.getSimpleName();

    private static final Map<String, String> OWNERSHIP = Map.ofEntries(
            entry("spindashEnabled", PLAYER_CAPABILITY),
            entry("spindashSpeedTable", PLAYER_CAPABILITY),
            entry("collisionModel", COLLISION),
            entry("fixedAnglePosThreshold", PLAYER_MOVEMENT),
            entry("lookScrollDelay", CAMERA),
            entry("waterShimmerEnabled", CAMERA),
            entry("inputAlwaysCapsGroundSpeed", PLAYER_MOVEMENT),
            entry("elementalShieldsEnabled", PLAYER_CAPABILITY),
            entry("instaShieldEnabled", PLAYER_CAPABILITY),
            entry("jumpRepressClearsRollJumpBeforeAbility", PLAYER_CAPABILITY),
            entry("angleDiffCardinalSnap", PLAYER_MOVEMENT),
            entry("extendedEdgeBalance", PLAYER_ANIMATION),
            entry("singleFacingBalanceAnimationSet", PLAYER_ANIMATION),
            entry("ringFloorCheckMask", RING),
            entry("ringFloorProbeRequiresRenderFlag", RING),
            entry("ringCollisionWidth", RING),
            entry("ringCollisionHeight", RING),
            entry("lightningShieldEnabled", PLAYER_CAPABILITY),
            entry("superSpindashSpeedTable", PLAYER_CAPABILITY),
            entry("movingCrouchThreshold", PLAYER_MOVEMENT),
            entry("groundWallCollisionEnabled", COLLISION),
            entry("groundWallPushRequiresFacingIntoWall", COLLISION),
            entry("repeatedObjectRideGroundWallResponseDeferred", COLLISION),
            entry("animationChangeClearsPush", PLAYER_ANIMATION),
            entry("airSuperspeedPreserved", PLAYER_MOVEMENT),
            entry("slopeResistStartsFromRest", PLAYER_MOVEMENT),
            entry("slopeRepelChecksOnObject", PLAYER_MOVEMENT),
            entry("slopeRepelUsesS3kSlipKick", PLAYER_MOVEMENT),
            entry("pinballLandingPreservesRoll", PLAYER_MOVEMENT),
            entry("pinballLandingPreservesPinballMode", PLAYER_MOVEMENT),
            entry("rollingJumpPinballGateRequiresSpindashFlag", PLAYER_MOVEMENT),
            entry("topSolidLandingAllowsZeroDist", COLLISION),
            entry("airBottomSolidHitClearsGroundSpeed", COLLISION),
            entry("airRightWallHitContinuesIntoCeilingSeparation", COLLISION),
            entry("airLeftWallHitContinuesIntoCeilingSeparation", COLLISION),
            entry("fullSolidBottomOverlapUsesCurrentYRadiusOnly", COLLISION),
            entry("fastScrollCap", CAMERA),
            entry("uncappedLeftwardHorizontalScroll", CAMERA),
            entry("bossHitNegatesGroundSpeed", OBJECT_INTERACTION),
            entry("bossHitHalvesBounceVelocity", OBJECT_INTERACTION),
            entry("stageRingsUseObjectTouchCollection", RING),
            entry("stageRingSweepUsesRawCameraWindow", RING),
            entry("sidekickFollowSnapThreshold", SIDEKICK_CPU),
            entry("sidekickDespawnX", SIDEKICK_CPU),
            entry("sidekickFollowLeadOffset", SIDEKICK_CPU),
            entry("sidekickFollowNudgeBlockedByObjectControlBit0", SIDEKICK_CPU),
            entry("sidekickDelayedJumpPressUsesHistoryEdge", SIDEKICK_CPU),
            entry("sidekickPanicTreatsPinballModeAsSpindashFlag", SIDEKICK_CPU),
            entry("sidekickSpawningRequiresGroundedLeader", SIDEKICK_CPU),
            entry("useScreenYWrapValueForVisibility", CAMERA),
            entry("playerControlAppliesVerticalWrapMask", CAMERA),
            entry("sidekickDespawnUsesObjectIdMismatch", OBJECT_INTERACTION),
            entry("sidekickNormalDespawnDelaysFreshRenderEntry", OBJECT_INTERACTION),
            entry("sidekickFlyLandStatusBlockerMask", SIDEKICK_CPU),
            entry("sidekickFlyLandRequiresLeaderAlive", SIDEKICK_CPU),
            entry("sidekickCatchUpYOffset", SIDEKICK_CPU),
            entry("sidekickFlightAutoLandFrames", SIDEKICK_CPU),
            entry("sidekickFlightMaxXStep", SIDEKICK_CPU),
            entry("sidekickFlightYStep", SIDEKICK_CPU),
            entry("sidekickFlightLeadXOffset", SIDEKICK_CPU),
            entry("sidekickFlightLeadSuppressGSpeed", SIDEKICK_CPU),
            entry("solidObjectOffscreenGate", COLLISION),
            entry("solidObjectRequiresSidekickOnScreen", COLLISION),
            entry("sidekickDespawnUsesRidingInstanceLoss", OBJECT_INTERACTION),
            entry("sidekickRespawnEntersCatchUpFlight", SIDEKICK_CPU),
            entry("sidekickPushBypassUsesGraceStatus", COLLISION),
            entry("sidekickSuppressesFastLeaderTinyFollowNudge", COLLISION),
            entry("sidekickClearsStalePushVelocityBeforeGroundMove", COLLISION),
            entry("sidekickCpuUsesLevelFrameCounter", SIDEKICK_CPU),
            entry("landingRollClearUsesCurrentYRadiusDelta", PLAYER_MOVEMENT),
            entry("rollStopsBelowMinimumSpeed", PLAYER_MOVEMENT),
            entry("rollControlledDecelUsesEffectiveDecelQuarter", PLAYER_MOVEMENT),
            entry("levelBoundaryRightStrict", PLAYER_MOVEMENT),
            entry("levelBoundaryUsesCentreY", PLAYER_MOVEMENT),
            entry("solidObjectTopBranchAlwaysLiftsOnUpwardVelocity", COLLISION),
            entry("sidekickNormalCpuSkipsHurtRoutine", OBJECT_INTERACTION),
            entry("controlLockLatchesLogicalInput", PLAYER_MOVEMENT),
            entry("hurtRoutineLatchesLogicalInput", PLAYER_MOVEMENT),
            entry("waterExitBoostSkipsFastUpwardVelocity", PLAYER_MOVEMENT),
            entry("slopeResistAppliesAtZeroInertia", PLAYER_MOVEMENT),
            entry("permanentRespawnTableLatch", OBJECT_INTERACTION),
            entry("objectsExecuteAfterPlayerPhysics", OBJECT_INTERACTION),
            entry("speedShoesTimerPrePhysicsExtraTicks", POWER_UP),
            entry("shieldObjectFixedSlotIndex", POWER_UP),
            entry("invincibilityStarsFixedSlotIndex", POWER_UP),
            entry("touchResponseUsesRenderFlagYGate", OBJECT_INTERACTION),
            entry("touchResponseUsesPreviousCollisionResponseList", OBJECT_INTERACTION),
            entry("sidekickDeathUsesDeferredDespawn", SIDEKICK_CPU),
            entry("rightWallDeepProbePreservesPenetration", COLLISION),
            entry("solidObjectBarelyPokingResolvesAsSide", COLLISION),
            entry("speedShoesTimerDecimation", POWER_UP),
            entry("initialDrowningCountdownFrameTimer", DROWNING_BUBBLE),
            entry("mouthBubbleTimerBias", DROWNING_BUBBLE),
            entry("breathingBubbleDefersFirstObjectPass", DROWNING_BUBBLE),
            entry("mouthBubbleRiseVelocity", DROWNING_BUBBLE),
            entry("solidObjectKeepsOnObjWhenJumpedOffSameFrame", COLLISION),
            entry("levelBoundaryLockUsesScreenLockFlag", PLAYER_MOVEMENT),
            entry("advanceWaterLevelBeforePlayerPhysics", COLLISION),
            entry("animalObjectPreservesObjectMoveXSubpixel", OBJECT_INTERACTION),
            entry("animalObjectUsesRenderFlagDeleteBounds", OBJECT_INTERACTION),
            entry("fixedSkidDustAllocatesAfterDynamicObjectPass", POWER_UP)
    );

    private static final Map<String, String> DERIVED_OWNERSHIP = Map.ofEntries(
            entry("waterSplashUsesFixedDustObject", POWER_UP),
            entry("primaryFixedDustSlotIndex", POWER_UP),
            entry("secondaryFixedDustSlotIndex", POWER_UP)
    );

    private static final Map<String, Class<? extends Record>> RULE_TYPES = Map.ofEntries(
            entry(PLAYER_MOVEMENT, PlayerMovementRules.class),
            entry(PLAYER_CAPABILITY, PlayerCapabilityRules.class),
            entry(COLLISION, CollisionRules.class),
            entry(PLAYER_ANIMATION, PlayerAnimationRules.class),
            entry(CAMERA, CameraRules.class),
            entry(RING, RingRules.class),
            entry(OBJECT_INTERACTION, ObjectInteractionRules.class),
            entry(SIDEKICK_CPU, SidekickCpuRules.class),
            entry(POWER_UP, PowerUpRules.class),
            entry(DROWNING_BUBBLE, DrowningBubbleRules.class)
    );

    @Test
    void mapsS1CoreRulesFromLegacyFeatureSet() {
        GameRules rules = GameRules.fromLegacy(PhysicsFeatureSet.SONIC_1);

        assertFalse(rules.playerCapability().spindashEnabled());
        assertEquals(CollisionModel.UNIFIED, rules.collision().collisionModel());
        assertEquals(PhysicsFeatureSet.LOOK_SCROLL_DELAY_NONE, rules.camera().lookScrollDelay());
        assertTrue(rules.camera().waterShimmerEnabled());
        assertTrue(rules.ring().stageRingsUseObjectTouchCollection());
        assertFalse(rules.powerUp().waterSplashUsesFixedDustObject());
    }

    @Test
    void mapsS2CoreRulesFromLegacyFeatureSet() {
        GameRules rules = GameRules.fromLegacy(PhysicsFeatureSet.SONIC_2);

        assertTrue(rules.playerCapability().spindashEnabled());
        assertEquals(CollisionModel.DUAL_PATH, rules.collision().collisionModel());
        assertEquals(PhysicsFeatureSet.LOOK_SCROLL_DELAY_S2, rules.camera().lookScrollDelay());
        assertEquals(PhysicsFeatureSet.SIDEKICK_FOLLOW_SNAP_S2,
                rules.sidekickCpu().sidekickFollowSnapThreshold());
        assertEquals(134, rules.powerUp().shieldObjectFixedSlotIndex());
        assertEquals(0, rules.drowningBubble().initialDrowningCountdownFrameTimer());
        assertTrue(rules.powerUp().waterSplashUsesFixedDustObject());
    }

    @Test
    void mapsS3kCoreRulesFromLegacyFeatureSet() {
        GameRules rules = GameRules.fromLegacy(PhysicsFeatureSet.SONIC_3K);

        assertTrue(rules.playerCapability().elementalShieldsEnabled());
        assertTrue(rules.playerCapability().instaShieldEnabled());
        assertTrue(rules.playerAnimation().singleFacingBalanceAnimationSet());
        assertEquals(PhysicsFeatureSet.SONIC_3K.fastScrollCap(), rules.camera().fastScrollCap());
        assertTrue(rules.ring().stageRingSweepUsesRawCameraWindow());
        assertTrue(rules.objectInteraction().bossHitNegatesGroundSpeed());
        assertEquals(-0x100, rules.drowningBubble().mouthBubbleRiseVelocity());
    }

    @Test
    void sonic1ModuleExposesRulesFromPhysicsProviderFeatureSet() {
        assertModuleRulesMatchProviderFeatureSet(new Sonic1GameModule());
    }

    @Test
    void sonic2ModuleExposesRulesFromPhysicsProviderFeatureSet() {
        assertModuleRulesMatchProviderFeatureSet(new Sonic2GameModule());
    }

    @Test
    void sonic3kModuleExposesRulesFromPhysicsProviderFeatureSet() {
        assertModuleRulesMatchProviderFeatureSet(new Sonic3kGameModule());
    }

    @Test
    void testHelperPhysicsFeatureSetKeepsGameRulesSynchronized() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);

        sprite.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

        assertEquals(GameRules.fromLegacy(PhysicsFeatureSet.SONIC_3K), sprite.getGameRules());

        sprite.setPhysicsFeatureSetForTest(null);

        assertNull(sprite.getGameRules());
    }

    @Test
    void rejectsNullLegacyFeatureSet() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> GameRules.fromLegacy(null));

        assertEquals("PhysicsFeatureSet is required", exception.getMessage());
    }

    @Test
    void defensivelyCopiesCapabilitySpeedTables() {
        short[] spindashTable = new short[]{1, 2, 3};
        short[] superTable = new short[]{4, 5, 6};

        PlayerCapabilityRules rules = new PlayerCapabilityRules(true, spindashTable, true, true,
                true, true, superTable);

        spindashTable[0] = 99;
        superTable[0] = 99;

        assertArrayEquals(new short[]{1, 2, 3}, rules.spindashSpeedTable());
        assertArrayEquals(new short[]{4, 5, 6}, rules.superSpindashSpeedTable());

        short[] accessorResult = rules.spindashSpeedTable();
        assertNotSame(accessorResult, rules.spindashSpeedTable());
        accessorResult[0] = 42;

        assertArrayEquals(new short[]{1, 2, 3}, rules.spindashSpeedTable());
    }

    @Test
    void capabilityRulesUseValueSemanticsForS2SpeedTable() {
        PlayerCapabilityRules first = PlayerCapabilityRules.fromLegacy(PhysicsFeatureSet.SONIC_2);
        PlayerCapabilityRules second = PlayerCapabilityRules.fromLegacy(PhysicsFeatureSet.SONIC_2);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void capabilityRulesUseValueSemanticsForS3kSuperSpeedTable() {
        PlayerCapabilityRules first = PlayerCapabilityRules.fromLegacy(PhysicsFeatureSet.SONIC_3K);
        PlayerCapabilityRules second = new PlayerCapabilityRules(
                first.spindashEnabled(),
                first.spindashSpeedTable(),
                first.elementalShieldsEnabled(),
                first.instaShieldEnabled(),
                first.jumpRepressClearsRollJumpBeforeAbility(),
                first.lightningShieldEnabled(),
                first.superSpindashSpeedTable());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertTrue(first.toString().contains("superSpindashSpeedTable=[2816, 2944, 3072"));
    }

    @Test
    void exhaustivelyAssignsEveryPhysicsFeatureSetComponentExactlyOnce() {
        Set<String> components = Stream.of(PhysicsFeatureSet.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertEquals(components, OWNERSHIP.keySet());
        assertEquals("CameraRules", OWNERSHIP.get("lookScrollDelay"));
        assertEquals("CameraRules", OWNERSHIP.get("waterShimmerEnabled"));
        assertEquals("CollisionRules", OWNERSHIP.get("advanceWaterLevelBeforePlayerPhysics"));
        assertEquals("CollisionRules", OWNERSHIP.get("collisionModel"));
        assertEquals("PlayerAnimationRules", OWNERSHIP.get("singleFacingBalanceAnimationSet"));
        assertEquals("PowerUpRules", OWNERSHIP.get("shieldObjectFixedSlotIndex"));
        assertEquals("PowerUpRules", OWNERSHIP.get("invincibilityStarsFixedSlotIndex"));
        assertEquals("PowerUpRules", OWNERSHIP.get("fixedSkidDustAllocatesAfterDynamicObjectPass"));
        assertEquals("PowerUpRules", DERIVED_OWNERSHIP.get("waterSplashUsesFixedDustObject"));
    }

    @Test
    void fromLegacyMapsEveryDirectOwnedComponentValue() throws Exception {
        assertDirectMappingsMatch(PhysicsFeatureSet.SONIC_1);
        assertDirectMappingsMatch(PhysicsFeatureSet.SONIC_2);
        assertDirectMappingsMatch(PhysicsFeatureSet.SONIC_3K);
    }

    @Test
    void everyRuleOwnedComponentHasMatchingRecordAccessor() {
        Map<String, String> destinations = new LinkedHashMap<>(OWNERSHIP);
        destinations.putAll(DERIVED_OWNERSHIP);
        Map<String, Set<String>> accessorsByRule = new HashMap<>();
        for (Map.Entry<String, Class<? extends Record>> type : RULE_TYPES.entrySet()) {
            accessorsByRule.put(type.getKey(), Stream.of(type.getValue().getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(Collectors.toSet()));
        }

        destinations.forEach((component, destination) -> {
            if (destination.startsWith("provider-owned:")) {
                return;
            }
            assertTrue(RULE_TYPES.containsKey(destination), () -> "Unknown destination for " + component);
            assertTrue(accessorsByRule.get(destination).contains(component),
                    () -> destination + " must expose accessor " + component + "()");
        });
    }

    private static void assertDirectMappingsMatch(PhysicsFeatureSet featureSet) throws Exception {
        GameRules rules = GameRules.fromLegacy(featureSet);
        Map<String, Object> ruleInstances = ruleInstancesByRuleName(rules);

        for (RecordComponent component : PhysicsFeatureSet.class.getRecordComponents()) {
            String componentName = component.getName();
            String destination = OWNERSHIP.get(componentName);
            if (destination.startsWith("provider-owned:")) {
                continue;
            }
            Object expected = component.getAccessor().invoke(featureSet);
            Method ruleAccessor = RULE_TYPES.get(destination).getMethod(componentName);
            Object actual = ruleAccessor.invoke(ruleInstances.get(destination));

            if (expected instanceof short[] expectedArray) {
                assertArrayEquals(expectedArray, (short[]) actual, componentName);
            } else {
                assertEquals(expected, actual, componentName);
            }
        }
    }

    private static void assertModuleRulesMatchProviderFeatureSet(GameModule module) {
        PhysicsFeatureSet featureSet = module.getPhysicsProvider().getFeatureSet();

        assertEquals(GameRules.fromLegacy(featureSet), module.getPhysicsProvider().getRules());
        assertEquals(GameRules.fromLegacy(featureSet), module.getRules());
    }

    private static Map<String, Object> ruleInstancesByRuleName(GameRules rules) throws Exception {
        Map<String, Object> result = new HashMap<>();
        for (RecordComponent component : GameRules.class.getRecordComponents()) {
            Object rule = component.getAccessor().invoke(rules);
            result.put(rule.getClass().getSimpleName(), rule);
        }
        return result;
    }
}
