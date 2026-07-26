package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
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
                    GameServices.level(),
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
            assertEquals(0, p1.getLogicalInputState());
            assertEquals(0, p2.getLogicalInputState());
            assertEquals(30, p1.getDrowningController().getRemainingAir());
            assertEquals(30, p2.getDrowningController().getRemainingAir());
            assertEquals(4, p1.getFlipSpeed());
            assertEquals(4, p2.getFlipSpeed());
            assertEquals(0, p1.getAnimationId());
            assertEquals(0, p1.getAnimationFrameIndex());
            assertEquals(0, p1.getAnimationTick());
            assertEquals(0, p2.getAnimationId());
            assertEquals(0, p2.getAnimationFrameIndex());
            assertEquals(0, p2.getAnimationTick());
            assertFalse(p1.getAir());
            assertFalse(p2.getAir());

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
                    GameServices.level(),
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
        } finally {
            level.dispose();
        }
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
