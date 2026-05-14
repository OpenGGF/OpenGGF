package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kIcz1SnowboardIntroHeadless {
    private static final int ZONE_ICZ = 0x05;
    private static final int ACT_1 = 0;

    private Object oldSkipIntros;
    private Object oldMainCharacter;
    private Object oldSidekickCharacters;

    @BeforeEach
    public void setUpConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacters = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterEach
    public void restoreConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacters != null ? oldSidekickCharacters : "");
    }

    @Test
    public void sonicAloneRunsSnowboardIntroAndReleasesControlAfterCrash() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_ICZ, ACT_1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        assertTrue(hasSnowboardIntroObject(), "ICZ1 should spawn the Sonic snowboard intro object");
        assertTrue(sonic.isControlLocked(), "Intro should lock Sonic input");
        assertTrue(sonic.isObjectControlled(), "Intro should hold Sonic under object control initially");
        assertTrue(sonic.getAir(), "Intro should start Sonic airborne");
        assertTrue(sonic.getRolling(), "Intro should force Sonic into rolling/snowboard posture");
        renderSnowboardIntroObjects();

        int startX = sonic.getCentreX();
        for (int frame = 0; frame < 45; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertTrue(sonic.getCentreX() > startX + 0x10,
                "Sonic should start rolling right after the ROM startup lock expires");
        assertFalse(sonic.isObjectControlled(),
                "Initial object-control hold should be released after the ROM startup lock");

        boolean sawSlopeRegion = false;
        boolean sawCrashHandoff = false;

        for (int frame = 0; frame < 1800; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (sonic.getCentreX() >= 0x184 && hasSnowboardIntroObject()) {
                sawSlopeRegion = true;
                renderSnowboardIntroObjects();
            }
            if (sonic.getCentreX() >= 0x3880
                    && !sonic.isObjectControlled()
                    && !hasSnowboardIntroObject()) {
                sawCrashHandoff = true;
                break;
            }
        }

        assertTrue(sonic.getCentreX() > startX + 0x1000,
                "Snowboard intro should carry Sonic far down ICZ1");
        assertTrue(sawSlopeRegion, "Sonic should reach the snowboard slope handoff region");
        assertTrue(sawCrashHandoff, "Sonic should crash off the snowboard and hand control to the ICZ1 event");
        assertFalse(sonic.isObjectControlled(), "The snowboard intro should stop object-controlling Sonic after the crash");
    }

    @Test
    public void introRecoversIfSonicLandsBeforeReachingSnowboard() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_ICZ, ACT_1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        for (int frame = 0; frame < 35; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        sonic.setAir(false);
        sonic.setXSpeed((short) 0);
        sonic.setYSpeed((short) 0);
        sonic.setGSpeed((short) 0);

        for (int frame = 0; frame < 90; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        assertTrue(sonic.getCentreX() >= 0x00C0,
                "ICZ intro should keep Sonic moving into the snowboard even if he lands early");
    }

    @Test
    public void snowboardIntroStillStartsAfterBlockingTitleCard() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_ICZ, ACT_1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        assertTrue(hasSnowboardIntroObject(), "ICZ1 should spawn the Sonic snowboard intro object");

        for (int frame = 0; frame < 45; frame++) {
            GameServices.level().updateObjectPositions();
            GameServices.camera().updatePosition(true);
        }

        ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .applyZonePlayerState();

        int startX = sonic.getCentreX();
        for (int frame = 0; frame < 60; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        assertTrue(sonic.getCentreX() > startX + 0x10,
                "ICZ intro should resume player movement after the blocking title card exits");
        assertFalse(sonic.isObjectControlSuppressesMovement(),
                "The title-card exit reapply must not leave object control suppressing movement");
    }

    @Test
    public void bigSnowPileFallsAfterCrashAndRequiresJumpEscape() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_ICZ, ACT_1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        boolean sawCrashRelease = false;
        for (int frame = 0; frame < 1900; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (sonic.getCentreX() >= 0x3880
                    && !sonic.isObjectControlled()
                    && !hasSnowboardIntroObject()) {
                sawCrashRelease = true;
                break;
            }
        }
        assertTrue(sawCrashRelease, "Sonic should reach the ICZ1 wall crash handoff");

        boolean sawBigSnowPile = false;
        boolean sawLockedOnPile = false;
        for (int frame = 0; frame < 360; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            sawBigSnowPile |= hasObjectSimpleName("IczBigSnowPileInstance");
            if (sawBigSnowPile && sonic.isControlLocked() && !sonic.getAir()) {
                sawLockedOnPile = true;
                break;
            }
        }

        assertTrue(sawBigSnowPile, "ICZ1 background event should spawn Obj_ICZ1BigSnowPile");
        assertTrue(sawLockedOnPile, "Sonic should remain locked while standing under the fallen pile");

        fixture.stepFrame(false, false, false, false, true);

        assertFalse(sonic.isControlLocked(), "Jumping out of the pile should unlock Sonic");
        assertTrue(sonic.getAir(), "Jumping out of the pile should force Sonic airborne");
        assertTrue(sonic.getYSpeed() < 0, "Jumping out of the pile should apply upward velocity");

        for (int frame = 0; frame < 12; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertFalse(sonic.isControlLocked(), "The screen event should not relock Sonic after the pile jump release");

        for (int frame = 0; frame < 180 && sonic.getAir(); frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertFalse(sonic.getAir(), "Sonic should land after jumping out of the pile");
        assertFalse(sonic.isControlLocked(), "Sonic should stay unlocked after landing from the pile jump");
        int landedX = sonic.getCentreX();
        for (int frame = 0; frame < 24; frame++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        assertTrue(sonic.getCentreX() > landedX,
                "Sonic should accept right input on the ground after escaping the pile");
    }

    @Test
    public void bigSnowPileRendersLockOnBackgroundSnowTiles() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_ICZ, ACT_1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        for (int frame = 0; frame < 2260; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (hasObjectSimpleName("IczBigSnowPileInstance")
                    && sonic.isControlLocked()
                    && !sonic.getAir()) {
                break;
            }
        }

        ObjectInstance pile = findObjectSimpleName("IczBigSnowPileInstance");
        assertNotNull(pile, "ICZ1 should spawn Obj_ICZ1BigSnowPile");
        List<GLCommand> commands = new ArrayList<>();
        pile.appendRenderCommands(commands);
        Object objectPassTileCount = pile.getClass()
                .getDeclaredMethod("getLastRenderedTileCountForTests")
                .invoke(pile);
        assertEquals(0, ((Number) objectPassTileCount).intValue(),
                "Obj_ICZ1BigSnowPile visual snow should not render in the object/sprite pass");

        GameServices.specialRenderEffectRegistry().dispatch(
                SpecialRenderEffectStage.AFTER_BACKGROUND,
                new SpecialRenderEffectContext(
                        GameServices.camera(),
                        0,
                        GameServices.level(),
                        GameServices.graphics()));
        Object backgroundPassTileCount = pile.getClass()
                .getDeclaredMethod("getLastRenderedTileCountForTests")
                .invoke(pile);
        assertEquals(0, ((Number) backgroundPassTileCount).intValue(),
                "Obj_ICZ1BigSnowPile visual snow should render in front of low-priority foreground tiles");

        GameServices.specialRenderEffectRegistry().dispatch(
                SpecialRenderEffectStage.SPRITE_PRIORITY_MASK,
                new SpecialRenderEffectContext(
                        GameServices.camera(),
                        0,
                        GameServices.level(),
                        GameServices.graphics()));
        Object priorityMaskTileCount = pile.getClass()
                .getDeclaredMethod("getLastRenderedTileCountForTests")
                .invoke(pile);
        assertTrue(((Number) priorityMaskTileCount).intValue() > 0,
                "Obj_ICZ1BigSnowPile should contribute to the sprite priority mask so Sonic appears behind it");

        GameServices.specialRenderEffectRegistry().dispatch(
                SpecialRenderEffectStage.AFTER_FOREGROUND,
                new SpecialRenderEffectContext(
                        GameServices.camera(),
                        0,
                        GameServices.level(),
                        GameServices.graphics()));
        Object tileCount = pile.getClass()
                .getDeclaredMethod("getLastRenderedTileCountForTests")
                .invoke(pile);
        assertTrue(((Number) tileCount).intValue() > 0,
                "Obj_ICZ1BigSnowPile should render non-empty lock-on ICZ background snow tiles");
    }

    private boolean hasSnowboardIntroObject() {
        return hasObjectSimpleName("IczSnowboardIntroInstance");
    }

    private boolean hasObjectSimpleName(String simpleName) {
        return findObjectSimpleName(simpleName) != null;
    }

    private ObjectInstance findObjectSimpleName(String simpleName) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> simpleName.equals(object.getClass().getSimpleName()))
                .findFirst()
                .orElse(null);
    }

    private void renderSnowboardIntroObjects() {
        List<GLCommand> commands = new ArrayList<>();
        for (ObjectInstance object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object.getClass().getSimpleName().contains("Snowboard")) {
                object.appendRenderCommands(commands);
            }
        }
    }
}
