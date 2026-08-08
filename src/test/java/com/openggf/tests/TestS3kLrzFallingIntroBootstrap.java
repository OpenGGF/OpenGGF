package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bootstrap coverage for the S&K {@code SpawnLevelMainSprites} loc_68A6 gate.
 *
 * <p>The ROM applies the simple falling state to LRZ1 ({@code $0900}) only for
 * non-Knuckles characters. LRZ2 and Knuckles LRZ1 must retain their ordinary
 * grounded bootstrap state. SSZ ({@code $0A00/$0A01}) is covered as a negative
 * gate because the S&K routine has no SSZ comparison; its falling claim in the
 * current-status documents is stale relative to the owning disassembly.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzFallingIntroBootstrap {

    private static final int ACT_1 = 0;
    private static final int ACT_2 = 1;

    @AfterEach
    void resetEnvironment() {
        TestEnvironment.resetAll();
    }

    @Test
    void lrz1SonicAndTailsStartInNativeFallingState() {
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, ACT_1, "sonic", "tails");
        Sonic3kLevelEventManager manager = manager();
        manager.applyZonePlayerState();

        AbstractPlayableSprite player = fixture.sprite();
        assertTrue(player.getAir(), "LRZ1 non-Knuckles player must start airborne");
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getForcedAnimationId(),
                "LRZ1 non-Knuckles player must use the ROM $1B falling animation");
        assertFalse(player.isJumping(), "loc_68A6 does not set jumping on the main player");

        assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size(),
                "Sonic+Tails LRZ1 must retain Player_2");
        AbstractPlayableSprite sidekick = GameServices.sprites().getRegisteredSidekicks().get(0);
        assertTrue(sidekick.getAir(), "LRZ1 Player_2 must start airborne");
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), sidekick.getForcedAnimationId(),
                "LRZ1 Player_2 must use the ROM $1B falling animation");
        assertFalse(sidekick.isJumping(), "loc_68A6 does not set Player_2 jumping");
    }

    @Test
    void lrz1TailsAloneUsesTheSameNonKnucklesGate() {
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, ACT_1, "tails", "");
        manager().applyZonePlayerState();

        AbstractPlayableSprite player = fixture.sprite();
        assertTrue(player.getAir(), "LRZ1 Tails-alone must start airborne");
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getForcedAnimationId(),
                "LRZ1 Tails-alone must use the ROM $1B falling animation");
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty(),
                "Tails-alone must not create a Player_2 sidekick");
    }

    @Test
    void lrz1KnucklesBypassesLoc68A6() {
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, ACT_1, "knuckles", "");
        AbstractPlayableSprite player = fixture.sprite();
        player.setAir(false);
        player.setForcedAnimationId(-1);

        manager().applyZonePlayerState();

        assertFalse(player.getAir(), "LRZ1 Knuckles must bypass the loc_68A6 falling gate");
        assertEquals(-1, player.getForcedAnimationId(),
                "LRZ1 Knuckles must not receive the simple falling animation");
    }

    @Test
    void lrz2DoesNotReuseTheLrz1Gate() {
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_LRZ, ACT_2, "sonic", "tails");
        AbstractPlayableSprite player = fixture.sprite();
        player.setAir(false);
        player.setForcedAnimationId(-1);

        manager().applyZonePlayerState();

        assertFalse(player.getAir(), "LRZ2 must not inherit LRZ1's falling bootstrap");
        assertEquals(-1, player.getForcedAnimationId(),
                "LRZ2 must not receive the loc_68A6 animation");
    }

    @Test
    void sszActsHaveNoLoc68A6GateInTheOwningRomRoutine() {
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_SSZ, ACT_1, "sonic", "tails");
        AbstractPlayableSprite player = fixture.sprite();
        player.setAir(false);
        player.setForcedAnimationId(-1);

        manager().applyZonePlayerState();

        assertFalse(player.getAir(),
                "SSZ1 is $0A00, while the ROM loc_68A6 comparison is LRZ boss $1600");
        assertEquals(-1, player.getForcedAnimationId(),
                "SSZ1 must not receive a state absent from SpawnLevelMainSprites");
    }

    @Test
    void ssz2AlsoHasNoLoc68A6GateInTheOwningRomRoutine() {
        HeadlessTestFixture fixture = load(Sonic3kZoneIds.ZONE_SSZ, ACT_2, "knuckles", "");
        AbstractPlayableSprite player = fixture.sprite();
        player.setAir(false);
        player.setForcedAnimationId(-1);

        manager().applyZonePlayerState();

        assertFalse(player.getAir(),
                "SSZ2 is $0A01 and must not inherit the LRZ loc_68A6 gate");
        assertEquals(-1, player.getForcedAnimationId(),
                "SSZ2 must not receive a state absent from SpawnLevelMainSprites");
    }

    private static HeadlessTestFixture load(int zone, int act,
                                            String mainCharacter, String sidekickCharacter) {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, mainCharacter);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekickCharacter);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(zone, act)
                .build();
        assertNotNull(GameServices.module().getLevelEventProvider(),
                "S3K level load must install a level-event manager");
        return fixture;
    }

    private static Sonic3kLevelEventManager manager() {
        assertTrue(GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager,
                "S3K level load must install Sonic3kLevelEventManager");
        return (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
    }
}
