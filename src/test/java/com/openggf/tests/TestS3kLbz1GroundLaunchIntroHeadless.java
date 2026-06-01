package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLbz1GroundLaunchIntroHeadless {
    private Object oldSkipIntros;
    private Object oldMainCharacter;
    private Object oldSidekickCharacters;

    @BeforeEach
    void setUpConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacters = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterEach
    void restoreConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacters != null ? oldSidekickCharacters : "");
    }

    @Test
    void lbz1StartsWithRomGroundLaunchIntro() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();
        int startY = sonic.getCentreY() & 0xFFFF;
        applyTitleCardHandoff();

        assertTrue(hasGroundLaunchIntro(), "LBZ1 should spawn Obj_LevelIntro_PlayerLaunchFromGround.");
        assertTrue(sonic.isControlLocked(), "LBZ1 launch intro should lock input immediately.");
        assertTrue(sonic.isObjectControlled(), "LBZ1 launch intro should hold Sonic under object control initially.");
        assertEquals(0x00B0, sonic.getCentreX() & 0xFFFF,
                "LBZ1 should use the ROM start X before the launch.");
        assertTrue(startY >= 0x064C && startY <= 0x0650,
                "LBZ1 should begin near the buried ROM start Y before the launch.");
        assertEquals(startY, sonic.getCentreY() & 0xFFFF,
                "Arming the launch controller should not move Sonic before the launch timer expires.");

        for (int frame = 0; frame < 29; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertEquals(0, sonic.getYSpeed(), "The ROM waits 30 frames before applying launch velocity.");
        assertTrue(sonic.isControlLocked(), "Input remains locked during the pre-launch delay.");

        fixture.stepFrame(false, false, false, false, false);
        assertEquals((short) -0x0B00, sonic.getYSpeed(),
                "After the 30-frame delay, Sonic should spring upward at ROM speed -$0B00.");
        assertTrue(sonic.getAir(), "The launch marks Sonic airborne.");
        assertEquals(Sonic3kAnimationIds.SPRING.id(), sonic.getAnimationId(),
                "The launch uses the ROM spring animation.");
        assertEquals(firstFrameOfAnimation(sonic, Sonic3kAnimationIds.SPRING.id()), sonic.getMappingFrame(),
                "The visible mapping frame should switch to the spring animation on the launch frame.");

        for (int frame = 0; frame < 90 && sonic.isControlLocked(); frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        assertTrue((sonic.getCentreY() & 0xFFFF) < 0x05C0,
                "The intro should carry Sonic upward out of the terrain before release.");
        assertFalse(sonic.isControlLocked(), "Input should unlock after Sonic rises past y=$05C0.");
        assertFalse(sonic.isObjectControlled(), "Object control should clear after the launch handoff.");
        assertFalse(hasGroundLaunchIntro(), "The launch intro object should delete itself after release.");
    }

    @Test
    void skipIntroBootstrapStillRunsGroundLaunchBecauseRomStartIsBuried() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);

        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0)
                .build();
        applyTitleCardHandoff();

        assertTrue(hasGroundLaunchIntro(),
                "LBZ1's ROM start is inside terrain, so the ground-launch controller must still run.");
        assertTrue(fixture.sprite().isControlLocked(),
                "The launch controller must take control even when generic intro skipping is enabled.");

        for (int frame = 0; frame < 30; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertEquals((short) -0x0B00, fixture.sprite().getYSpeed(),
                "The skip-intro path must still spring Sonic out of the buried ROM start.");

        for (int frame = 0; frame < 90 && fixture.sprite().isControlLocked(); frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertFalse(fixture.sprite().isControlLocked(),
                "Sonic must regain control after the LBZ1 launch even when generic intro skipping is enabled.");
    }

    @Test
    void lbz1GroundLaunchWaitsForTitleCardHandoff() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();
        int startY = sonic.getCentreY() & 0xFFFF;

        for (int frame = 0; frame < 45; frame++) {
            GameServices.level().updateObjectPositions();
            GameServices.camera().updatePosition(true);
        }

        assertEquals(0, sonic.getYSpeed(),
                "LBZ1 launch velocity must not be applied during the blocking title-card phase.");
        assertEquals(startY, sonic.getCentreY() & 0xFFFF,
                "Sonic should remain at the buried ROM start until the level starts.");
        assertTrue(hasGroundLaunchIntro(),
                "The LBZ1 launch controller should be waiting for the title-card handoff.");

        applyTitleCardHandoff();

        for (int frame = 0; frame < 30; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        assertEquals((short) -0x0B00, sonic.getYSpeed(),
                "LBZ1 should launch once after the title-card handoff.");
    }

    @Test
    void lbz1GroundLaunchKeepsSpringPoseWhileSonicEmerges() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();
        applyTitleCardHandoff();

        for (int frame = 0; frame < 30; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        int springFrame = firstFrameOfAnimation(sonic, Sonic3kAnimationIds.SPRING.id());
        boolean sawEmergingFrame = false;
        for (int frame = 0; frame < 90 && sonic.isControlLocked(); frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if ((sonic.getCentreY() & 0xFFFF) <= 0x0620) {
                sawEmergingFrame = true;
                assertEquals(Sonic3kAnimationIds.SPRING.id(), sonic.getAnimationId(),
                        "Sonic should still be using the ROM spring animation while emerging from the ground.");
                assertEquals(springFrame, sonic.getMappingFrame(),
                        "Sonic should still render the ROM spring mapping frame while emerging from the ground.");
            }
        }

        assertTrue(sawEmergingFrame, "The test should observe Sonic rising out of the buried start position.");
        assertEquals(Sonic3kAnimationIds.SPRING.id(), sonic.getAnimationId(),
                "Release should not immediately fall back to the running/walking animation.");
        assertEquals(springFrame, sonic.getMappingFrame(),
                "Release should leave the visible spring pose in place.");
    }

    private boolean hasGroundLaunchIntro() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .map(ObjectInstance::getName)
                .anyMatch("LBZ1GroundLaunchIntro"::equals);
    }

    private void applyTitleCardHandoff() {
        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .applyZonePlayerStateAfterTitleCard();
    }

    private int firstFrameOfAnimation(AbstractPlayableSprite sprite, int animationId) {
        return sprite.getAnimationSet().getScript(animationId).frames().getFirst();
    }
}
