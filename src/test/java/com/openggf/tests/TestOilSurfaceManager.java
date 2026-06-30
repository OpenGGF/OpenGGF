package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.game.sonic2.OilSurfaceManager;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOilSurfaceManager {

    private static final int OIL_SURFACE_Y = Sonic2Constants.OIL_SURFACE_Y - Sonic2Constants.OIL_SUBMERSION_MAX;

    private OilSurfaceManager manager;
    private TestOilSprite sprite;

    @BeforeEach
    public void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        manager = new OilSurfaceManager();
        sprite = new TestOilSprite("test", (short) 0, (short) 0);
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    @Test
    public void keepsOilSupportWhenAirFlagTemporarilySet() {
        landOnOilSurface();
        assertTrue(manager.isStandingOnOil());

        // Simulate movement step clearing support before oil update.
        sprite.setAir(true);
        sprite.setOnObject(false);
        sprite.setYSpeed((short) 0x20);
        int before = manager.getSubmersion();

        manager.update(sprite);

        assertTrue(manager.isStandingOnOil());
        assertFalse(sprite.getAir());
        assertTrue(sprite.isOnObject());
        assertEquals(before - 1, manager.getSubmersion());
    }

    @Test
    public void jumpReleaseClearsOilSupport() {
        landOnOilSurface();
        assertTrue(manager.isStandingOnOil());

        sprite.setAir(true);
        sprite.setJumping(true);
        sprite.setYSpeed((short) -0x200);

        manager.update(sprite);

        assertFalse(manager.isStandingOnOil());
        assertFalse(sprite.isOnObject());
    }

    @Test
    public void suffocatesAfterSubmersionCountdownExpires() {
        landOnOilSurface();
        assertTrue(manager.isStandingOnOil());

        for (int i = 0; i < Sonic2Constants.OIL_SUBMERSION_MAX; i++) {
            manager.update(sprite);
            assertFalse(sprite.getDead(), "Should not be dead while submersion is decrementing");
        }

        assertEquals(0, manager.getSubmersion());
        manager.update(sprite);

        assertTrue(sprite.getDead(), "Should die when submersion reaches zero on standing frame");
        assertFalse(manager.isStandingOnOil());
        assertTrue(sprite.isOnObject(),
                "OOZ suffocation jumps to KillCharacter, which preserves Status_OnObj while setting Status_InAir");
    }

    @Test
    public void frictionSlideUsesLogicalHeldInputFromRomPreObjectSlot() throws Exception {
        sprite.setGSpeed((short) 0x0524);
        sprite.setDirectionalInputPressed(false, false, false, false);
        sprite.setLogicalInputState(false, false, false, true, false);

        invokeFrictionSlide();

        assertEquals(0x0528, sprite.getGSpeed() & 0xFFFF,
                "OilSlides reads Ctrl_1_Held_Logical before the player slot refreshes current raw input");
        assertTrue(sprite.isSliding());
    }

    @Test
    public void airborneSlideExitRefreshesMoveLockToRomDuration() {
        sprite.setAir(true);
        sprite.setSliding(true);
        sprite.setMoveLockTimer(3);

        manager.updateSlides(sprite);

        assertFalse(sprite.isSliding());
        assertEquals(5, sprite.getMoveLockTimer(),
                "OilSlides writes #5 to move_lock every time sliding exits, even over a shorter active lock");
    }

    @Test
    public void oilLandingPublishesSyntheticObj07InteractLatch() {
        landOnOilSurface();

        assertEquals(Sonic2ObjectIds.OIL, sprite.getLatchedSolidObjectId(),
                "Obj07 PlatformObject landing should publish the oil object id for TailsCPU_CheckDespawn");
        assertEquals(AbstractPlayableSprite.SYNTHETIC_INTERACT_SLOT, sprite.getInteractSlotIndex(),
                "Obj07 is manager-hosted, so its ROM interact target is represented by a synthetic slot");
    }

    private void landOnOilSurface() {
        int centreY = OIL_SURFACE_Y + 1 - sprite.getYRadius();
        sprite.setCentreY((short) centreY);
        sprite.setAir(true);
        sprite.setOnObject(false);
        sprite.setJumping(false);
        sprite.setYSpeed((short) 0x200);
        manager.update(sprite);
    }

    private void invokeFrictionSlide() throws Exception {
        Method method = OilSurfaceManager.class.getDeclaredMethod("applyFrictionSlide", AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(manager, sprite);
    }

    private static class TestOilSprite extends AbstractPlayableSprite {
        TestOilSprite(String code, short x, short y) {
            super(code, x, y);
        }

        @Override
        public void draw() {
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 12;
            runDecel = 128;
            friction = 12;
            max = 1536;
            jump = 1664;
        }

        @Override
        protected void createSensorLines() {
        }
    }
}
