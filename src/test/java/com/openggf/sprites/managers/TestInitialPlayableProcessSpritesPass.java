package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra;
import com.openggf.level.objects.PerObjectRewindSnapshot.SidekickCpuRewindExtra;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceCharacterState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestInitialPlayableProcessSpritesPass {

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void nativeNeutralInputContainsNoRawOrLogicalControllerState() {
        InitialPlayableInput input = InitialPlayableInput.nativeNeutral();

        assertEquals(0, input.p1Held());
        assertEquals(0, input.p1Pressed());
        assertEquals(0, input.p2Held());
        assertEquals(0, input.p2Pressed());
        assertTrue(input.consumeQueuedObjectControlState(),
                "queued runtime object-control state is not user input");
    }

    @Test
    void nativeNeutralInputAndEpochProduceTheCapturedAiz1PlayerState() throws Exception {
        SharedLevel level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            SpriteManager manager = GameServices.sprites();
            AbstractPlayableSprite p1 = manager.getMainPlayable();
            AbstractPlayableSprite p2 = manager.getSidekicks().getFirst();
            int spriteFrame = manager.getFrameCounter();
            int levelFrame = GameServices.level().getFrameCounter();
            int objectFrame = GameServices.level().getObjectManager().getFrameCounter();
            int vblank = GameServices.level().getObjectManager().getVblaCounter();

            manager.processInitialPlayableSlots(
                    new ProcessSpritesEpoch(0, 1, false),
                    InitialPlayableInput.nativeNeutral());

            // Locked-on ROM oracle, pre-call $647E -> return $6484:
            // docs/skdisasm/sonic3k.asm:7848-7856,21931-21941,26101-26156.
            assertEquals(0x0040, p1.getCentreX());
            assertEquals(0x0420, p1.getCentreY());
            assertEquals(0x0020, p2.getCentreX());
            assertEquals(0x0424, p2.getCentreY());
            assertEquals(0, p1.getXSubpixelRaw());
            assertEquals(0, p1.getYSubpixelRaw());
            assertEquals(0, p2.getXSubpixelRaw());
            assertEquals(0, p2.getYSubpixelRaw());
            assertEquals(0, p1.getXSpeed());
            assertEquals(0, p1.getYSpeed());
            assertEquals(0, p1.getGSpeed());
            assertEquals(0, p2.getXSpeed());
            assertEquals(0, p2.getYSpeed());
            assertEquals(0, p2.getGSpeed());
            assertEquals(2, TraceCharacterState.routineFromSprite(p1));
            assertEquals(2, TraceCharacterState.routineFromSprite(p2));
            assertEquals(0x53, nativeAizSetupObjectControl(p1),
                    "Obj_AIZPlaneIntro publishes Player_1 object_control=$53");
            assertEquals(0, nativeAizSetupObjectControl(p2));
            assertEquals(0, TraceCharacterState.statusByteFromSprite(p1));
            assertEquals(0, TraceCharacterState.statusByteFromSprite(p2));
            assertEquals(0, p1.getLogicalInputState());
            assertEquals(0, p2.getLogicalInputState());
            assertEquals(30, p1.getDrowningController().getRemainingAir());
            assertEquals(30, p2.getDrowningController().getRemainingAir());
            assertEquals(4, p1.getFlipSpeed());
            assertEquals(4, p2.getFlipSpeed());
            assertEquals(0, p1.getDoubleJumpFlag());
            assertEquals(0, p2.getDoubleJumpFlag());
            assertEquals(0, p1.getFlipsRemaining());
            assertEquals(0, p2.getFlipsRemaining());
            assertEquals(0, p1.getMoveLockTimer());
            assertEquals(0, p2.getMoveLockTimer());
            assertEquals(0, p1.getAnimationId());
            assertEquals(0, p1.getAnimationFrameIndex());
            assertEquals(0, p1.getAnimationTick());
            assertEquals(0, p2.getAnimationId());
            assertEquals(0, p2.getAnimationFrameIndex());
            assertEquals(0, p2.getAnimationTick());
            assertEquals(0, p1.getAnimationManager().captureRewindState().lastAnimationId());
            assertEquals(0, p2.getAnimationManager().captureRewindState().lastAnimationId());
            assertFalse(p1.getAir());
            assertFalse(p2.getAir());

            PlayerRewindExtra p1State = p1.captureRewindState(false).playerExtra();
            PlayerRewindExtra p2State = p2.captureRewindState(false).playerExtra();
            assertEquals(0, nativeStatusSecondary(p1));
            assertEquals(0, nativeStatusSecondary(p2));
            assertSecondaryStatusAndPowerTimersZero(p1, p1State);
            assertSecondaryStatusAndPowerTimersZero(p2, p2State);
            assertFalse(p1 instanceof TouchResponseProvider,
                    "Player collision_flags/property remain native zero; no touch-list publisher owns them");
            assertFalse(p2 instanceof TouchResponseProvider,
                    "Player collision_flags/property remain native zero; no touch-list publisher owns them");
            assertEquals(0, p1.captureRewindState(false).preUpdateCollisionFlags());
            assertEquals(0, p2.captureRewindState(false).preUpdateCollisionFlags());
            assertTrue(GameServices.water().hasWater(0, 0),
                    "AIZ1 Water_flag remains 1 across the setup pass");

            // The setup call is outside LevelLoop. It receives the immutable
            // (level=0, object ordinal=1) epoch without publishing gameplay/VInt
            // counters or fabricating a temporary SpriteManager counter.
            assertEquals(spriteFrame, manager.getFrameCounter());
            assertEquals(levelFrame, GameServices.level().getFrameCounter());
            assertEquals(objectFrame, GameServices.level().getObjectManager().getFrameCounter());
            assertEquals(vblank, GameServices.level().getObjectManager().getVblaCounter());
        } finally {
            level.dispose();
        }
    }

    @Test
    void p1InitializesTheTemporaryOffsetHistoryBeforeP2WithoutAdvancingItsCursor() throws Exception {
        SharedLevel level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            SpriteManager manager = GameServices.sprites();
            AbstractPlayableSprite p1 = manager.getMainPlayable();
            AbstractPlayableSprite p2 = manager.getSidekicks().getFirst();
            SidekickCpuController cpu = p2.getCpuController();

            assertEquals(List.of(p1, p2),
                    SpriteManager.buildPlayableUpdateOrder(
                            manager.getAllSprites(), manager.getSidekicks(), false),
                    "Process_Sprites visits P1 slot 0 before P2 slot 1");

            manager.processInitialPlayableSlots(
                    new ProcessSpritesEpoch(0, 1, false),
                    InitialPlayableInput.nativeNeutral());

            // Sonic_Init temporarily shifts P1 to $20,$424 and calls
            // Reset_Player_Position_Array. Tails_Init runs later and does not
            // perform an ordinary delayed-follow read (sonic3k.asm:21931-21941,
            // 22166-22193,26101-26156).
            assertEquals(0, p1.historyPos());
            assertTrue(allEqual(p1.copyXHistory(), 0x0020));
            assertTrue(allEqual(p1.copyYHistory(), 0x0424));
            assertEquals(0, cpu.getDiagnosticRomCpuRoutine());
            assertEquals(0, cpu.targetX());
            assertEquals(0, cpu.targetY());
            assertEquals(0, cpu.getDiagnosticGeneratedHeldInput());
            assertEquals(0, cpu.getDiagnosticGeneratedPressedInput());
            assertEquals(0, cpu.getDiagnosticJumpingFlag());
            SidekickCpuRewindExtra cpuState = cpu.captureRewindState();
            assertEquals(0, cpuState.frameCounter());
            assertEquals(0, cpuState.controlCounter());
            assertEquals(0, cpuState.flightTimer());
            assertEquals(0, cpuState.catchUpTargetX());
            assertEquals(0, cpuState.catchUpTargetY());
        } finally {
            level.dispose();
        }
    }

    private static int nativeAizSetupObjectControl(AbstractPlayableSprite player) {
        int value = 0;
        if (player.isObjectControlled()) value |= 0x01;
        if (player.isObjectMappingFrameControl()) value |= 0x02;
        if (player.isControlLocked()) value |= 0x10;
        if (player.isHidden()) value |= 0x40;
        return value;
    }

    private static void assertSecondaryStatusAndPowerTimersZero(
            AbstractPlayableSprite player, PlayerRewindExtra state) {
        // status_secondary bits are Shield, Invincible, SpeedShoes, and the
        // three elemental-shield bits (sonic3k.constants.asm:183-191).
        assertFalse(player.hasShield());
        assertFalse(player.hasSpeedShoes());
        assertEquals(0, player.getInvulnerableFrames());
        assertEquals(0, player.getInvincibleFrames());
        assertEquals(0, state.speedShoesRemainingTicks());
        assertEquals(0, state.doubleJumpFlag());
        assertEquals(0, state.moveLockTimer());
        assertEquals(0, state.flipsRemaining() & 0xFF);
    }

    private static int nativeStatusSecondary(AbstractPlayableSprite player) {
        int value = 0;
        if (player.hasShield()) {
            value |= 0x01;
            value |= switch (player.getShieldType()) {
                case FIRE -> 0x10;
                case LIGHTNING -> 0x20;
                case BUBBLE -> 0x40;
                case BASIC -> 0;
            };
        }
        if (player.getInvincibleFrames() > 0) value |= 0x02;
        if (player.hasSpeedShoes()) value |= 0x04;
        return value;
    }

    private static boolean allEqual(short[] values, int expected) {
        for (short value : values) {
            if ((value & 0xFFFF) != expected) {
                return false;
            }
        }
        return true;
    }
}
