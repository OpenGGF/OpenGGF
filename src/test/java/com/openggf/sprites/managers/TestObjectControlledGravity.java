package com.openggf.sprites.managers;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.game.GameServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestObjectControlledGravity {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3, 4, 5})
    void knucklesObjectControlClearsGlideBeforeHangingAnimationAndRelease(int glideState) throws Exception {
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(
                320, "knuckles", "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 1, settings);
            GameServices.level().consumePendingInitialProcessSpritesPass();
            var player = session.player();
            assertEquals(com.openggf.game.CharacterKey.KNUCKLES, player.characterKey());
            player.setCentreX((short) 320);
            player.setCentreY((short) 850);
            GameServices.camera().setLevelStarted(true);
            player.setAir(true);
            player.applyCustomRadii(10, 10);
            player.setDoubleJumpFlag(glideState);
            player.setDoubleJumpProperty((byte) 0x80);
            player.setForcedAnimationId(0x20);
            player.setAbilityMappingFrameControl(true);
            player.setMappingFrame(0xC0);
            // The switch writes anim=$14 and object_control=1 after the player slot.
            player.setAnimationId(0x14);
            com.openggf.sprites.playable.ObjectControlState
                    .nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
            session.step(new com.openggf.debug.playback.Bk2FrameInput(0, AbstractPlayableSprite.INPUT_JUMP, 1, false, ""));
            assertEquals(0, player.getDoubleJumpFlag(), "Knuckles_Control loc_165AE clears the native state");
            assertEquals((byte) 0x80, player.getDoubleJumpProperty(), "the ROM clears only double_jump_flag");
            assertEquals(10, player.getXRadius());
            assertEquals(10, player.getYRadius());
            assertEquals(0x14, player.getAnimationId(), "the object's hanging animation survives");
            assertEquals(0x91, player.getMappingFrame(), "ROM hanging pose replaces the glide mapping");
            // sub_40F52 release: the following player pass must use ordinary jump physics.
            com.openggf.sprites.playable.ObjectControlState.none().applyTo(player);
            player.setRolling(true);
            player.setJumping(true);
            player.applyCustomRadii(7, 14);
            player.setAnimationId(2);
            player.setXSpeed((short) -0x200);
            player.setYSpeed((short) -0x380);
            session.step(new com.openggf.debug.playback.Bk2FrameInput(1, AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_JUMP, 1, false, ""));
            assertTrue(player.getXSpeed() <= -0x200, "release momentum must not become glide acceleration: " + player.getXSpeed());
            assertEquals(-0x380 + player.getGravity(), player.getYSpeed());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"sonic,1", "tails,1", "knuckles,0", "knuckles,1"})
    void objectControlPreservesOtherCharacterStateAndExplicitObjectPose(String character, int abilityState)
            throws Exception {
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(
                320, character, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 1, settings);
            GameServices.level().consumePendingInitialProcessSpritesPass();
            var player = session.player();
            player.setAir(true);
            player.setDoubleJumpFlag(abilityState);
            player.setAnimationId(0x14);
            player.setForcedAnimationId(0x14);
            player.setObjectMappingFrameControl(true);
            player.setMappingFrame(0x22);
            com.openggf.sprites.playable.ObjectControlState
                    .nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
            session.step(new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
            // Tails already follows its own loc_1384A clear; Sonic keeps its ability state.
            assertEquals(character.equals("sonic") ? abilityState : 0, player.getDoubleJumpFlag());
            assertEquals(0x14, player.getForcedAnimationId(), "a separate object's pose is not a glide override");
            assertTrue(player.isObjectMappingFrameControl());
            assertEquals(0x22, player.getMappingFrame());
        }
    }

    @Test
    void gravityIsSkippedWhenObjectControlled() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(3, 0)  // CNZ1 avoids AIZ1 intro object-control scripting.
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        GameServices.camera().setLevelStarted(true);
        sonic.setCentreY((short) 0x0200);
        sonic.setOnObject(false);
        sonic.setAir(true);
        sonic.setObjectControlled(true);
        sonic.setYSpeed((short) 0);
        short before = sonic.getYSpeed();

        // 5 booleans: up, down, left, right, jump — all false = idle frame
        fixture.stepFrame(false, false, false, false, false);

        short after = sonic.getYSpeed();
        assertEquals(before, after,
                "y_speed must not change when object-controlled (gravity gated)");
    }

    @Test
    void gravityStillAppliedWhenNotObjectControlled() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(3, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        GameServices.camera().setLevelStarted(true);
        sonic.setCentreY((short) 0x0200);
        sonic.setOnObject(false);
        sonic.setAir(true);
        sonic.setObjectControlled(false);
        sonic.setYSpeed((short) 0);
        short before = sonic.getYSpeed();

        fixture.stepFrame(false, false, false, false, false);

        short after = sonic.getYSpeed();
        assertEquals((short) (before + (short) sonic.getGravity()), after,
                "y_speed must accumulate gravity when not object-controlled");
    }

    @Test
    void gravityStillAppliesWhenObjectControlDoesNotSuppressMovement() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(3, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        GameServices.camera().setLevelStarted(true);
        sonic.setCentreY((short) 0x0200);
        sonic.setOnObject(false);
        sonic.setAir(true);
        sonic.setObjectControlled(true);
        sonic.setObjectControlAllowsCpu(true);
        sonic.setObjectControlSuppressesMovement(false);
        sonic.setYSpeed((short) 0);
        short before = sonic.getYSpeed();

        fixture.stepFrame(false, false, false, false, false);

        short after = sonic.getYSpeed();
        assertEquals((short) (before + (short) sonic.getGravity()), after,
                "ROM object_control bits without bit 0 should not skip movement gravity");
    }
}
