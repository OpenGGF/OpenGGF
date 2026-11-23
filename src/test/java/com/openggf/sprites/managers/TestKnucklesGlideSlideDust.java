package com.openggf.sprites.managers;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;
import com.openggf.level.objects.ObjectDrawInternalAccess;
import com.openggf.level.objects.SkidDustObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Isolated
class TestKnucklesGlideSlideDust {
    private static final GameplayCaptureSession.Settings SETTINGS =
            new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void kis2GlideLandingAllocatesFirstFreeSlotEvenUnderwater() throws Exception {
        File s3k = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s3k != null, "Sonic 3 & Knuckles ROM required for the S&K lock-on window");
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.SONIC_3K_ROM, s3k.getAbsolutePath());
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, SETTINGS);
            assertEquals(Kis2GamePatch.ID, assertInstanceOf(DelegatingGameModule.class, GameServices.module()).patchId());
            var player = assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
            player.setInWater(true);
            player.getDrowningController().setRemainingAirFromFixedCountdown(11);
            var movement = landFromGlide(player);
            movement.advanceFixedSkidDustWhileStopAnimPersists();
            assertFalse(player.isFixedSkidDustActive(), "air_left below 12 cannot arm Sonic_Dust");
            assertTrue(puffs().isEmpty());

            player.getDrowningController().setRemainingAirFromFixedCountdown(12);
            landFromGlide(player);
            var objects = GameServices.level().getObjectManager();
            int freeSlot = objects.firstFreeDynamicSlot();
            movement.advanceFixedSkidDustWhileStopAnimPersists();
            assertEquals(1, puffs().size(), "KiS2 Obj08 allocates even while underwater");
            var puff = puffs().getFirst();
            assertEquals(freeSlot, puff.getSlotIndex());
            assertEquals(player.getCentreX(), puff.getSpawn().x());
            assertEquals(player.getCentreY() + 6, puff.getSpawn().y());
            assertEquals(0, puff.getSpawn().renderFlags(), "Obj08 clears the puff's flip status");
            assertEquals(3, player.getSkidDustTimer());

            for (int pass = 1; pass <= 17; pass++) {
                puff.update(pass, player);
                assertFalse(puff.isDestroyed(), "dynamic pass " + pass);
            }
            puff.update(18, player);
            assertTrue(puff.isDestroyed(), "routine 4 deletes the puff on the eighteenth pass");

            for (int tick = 0; tick < 4; tick++) {
                movement.advanceFixedSkidDustWhileStopAnimPersists();
            }
            var nextPuff = puffs().getFirst();
            assertNotSame(puff, nextPuff, "the emitter produces another puff four ticks later");
            player.getDrowningController().setRemainingAirFromFixedCountdown(11);
            nextPuff.update(1, player);
            player.getDrowningController().setRemainingAirFromFixedCountdown(30);
            for (int pass = 2; pass <= 20; pass++) {
                nextPuff.update(pass, player);
            }
            assertFalse(nextPuff.isDestroyed(), "the blank looping animation retains the slot");

            assertDamageClearsSlide(player, movement);
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void kis2OrdinaryGlideSlideUsesTheMainDustArtBank() throws Exception {
        File s3k = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(s3k != null, "Sonic 3 & Knuckles ROM required for the S&K lock-on window");
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.SONIC_3K_ROM, s3k.getAbsolutePath());
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, SETTINGS);
            assertEquals(Kis2GamePatch.ID, assertInstanceOf(DelegatingGameModule.class, GameServices.module()).patchId());
            var player = assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());

            for (int tick = 0; tick < 107; tick++) {
                int direction = tick >= 71 ? AbstractPlayableSprite.INPUT_RIGHT : 0;
                int buttons = tick == 40 ? InputActionMasks.ACTION_A
                        : tick >= 71 ? InputActionMasks.ACTION_B : 0;
                session.step(new Bk2FrameInput(tick, direction, buttons, false, "jump and glide on flat EHZ ground"));
                if (player.getDoubleJumpFlag() != 3) {
                    continue;
                }
                assertEquals(1, puffs().size(), "the production fixed slot allocates on the landing pass");
                var puff = puffs().getFirst();
                var dust = player.getSpindashDustController();
                assertNotNull(dust, "Obj08_Init binds Knuckles to the Sonic_Dust art bank");
                assertNotNull(dust.getRenderer());
                assertEquals(-1, ObjectDrawInternalAccess.skidDustDisplayPose(puff).frame());
                session.step(new Bk2FrameInput(tick + 1, direction, buttons, false, "hold the glide"));
                assertEquals(0x11, ObjectDrawInternalAccess.skidDustDisplayPose(puff).frame(),
                        "the first dynamic pass selects the first visible dust frame");
                return;
            }
            fail("the jump and glide did not reach a ground slide");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @RequiresRom(SonicGame.SONIC_3K)
    void s3kDustRoutineReadsSlideFlagEvenDuringHurtOrDeath(boolean death) throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, SETTINGS);
            var player = assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
            var movement = landFromGlide(player);
            assertTrue(player.isFixedSkidDustActive());
            player.setInWater(true);
            movement.advanceFixedSkidDustWhileStopAnimPersists();
            assertTrue(puffs().isEmpty(), "S3K skips the underwater allocation");
            assertEquals(3, player.getSkidDustTimer(), "the timer reloads underwater");
            player.setInWater(false);
            assertTrue(player.applyHurtOrDeath(player.getCentreX() + 16, true, !death));
            assertEquals(!death, player.isHurt());
            assertEquals(death, player.getDead());

            // Independently exercise loc_18CB6 with an armed emitter and a live slide byte.
            player.setDoubleJumpFlag(3);
            for (int tick = 0; tick < 4; tick++) {
                movement.advanceFixedSkidDustWhileStopAnimPersists();
            }
            assertEquals(1, puffs().size(), "loc_18CB6 keys on the slide byte");
            player.setDoubleJumpFlag(0);
            movement.advanceFixedSkidDustWhileStopAnimPersists();
            assertFalse(player.isFixedSkidDustActive());
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void s3kHurtAndKillClearSlideViaKnuxTouchFloor() throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, SETTINGS);
            var player = assertInstanceOf(Knuckles.class, GameServices.sprites().getMainPlayable());
            var movement = landFromGlide(player);
            assertDamageClearsSlide(player, movement);
        }
    }

    private static void assertDamageClearsSlide(AbstractPlayableSprite player, PlayableSpriteMovement movement)
            throws Exception {
        List<SkidDustObjectInstance> existing = puffs();
        assertTrue(player.applyHurtOrDeath(player.getCentreX() + 16, true, true));
        assertEquals(0, player.getDoubleJumpFlag(), "the hurt handler calls the floor-reset tail");
        movement.advanceFixedSkidDustWhileStopAnimPersists();
        assertFalse(player.isFixedSkidDustActive());
        assertEquals(existing, puffs());

        player.setHurt(false);
        player.setInvulnerableFrames(0);
        landFromGlide(player);
        assertTrue(player.applyHurtOrDeath(player.getCentreX() + 16, true, false));
        assertEquals(0, player.getDoubleJumpFlag(), "the kill handler calls the floor-reset tail");
        movement.advanceFixedSkidDustWhileStopAnimPersists();
        assertFalse(player.isFixedSkidDustActive());
        assertEquals(existing, puffs());
    }

    private static PlayableSpriteMovement landFromGlide(AbstractPlayableSprite player) throws Exception {
        player.setAngle((byte) 0);
        player.setAir(true);
        player.setDoubleJumpFlag(1);
        player.setXSpeed((short) -0x400);
        var movement = assertInstanceOf(PlayableSpriteMovement.class, player.getMovementManager());
        // Isolate the flat-floor contact boundary from terrain and route setup.
        var landing = PlayableSpriteMovement.class.getDeclaredMethod("glideHitFloor");
        landing.setAccessible(true);
        landing.invoke(movement);
        assertEquals(3, player.getDoubleJumpFlag());
        return movement;
    }

    private static List<SkidDustObjectInstance> puffs() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(SkidDustObjectInstance.class::isInstance)
                .map(SkidDustObjectInstance.class::cast)
                .filter(puff -> !puff.isDestroyed())
                .toList();
    }
}
