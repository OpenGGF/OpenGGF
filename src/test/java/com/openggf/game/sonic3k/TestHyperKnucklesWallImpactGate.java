package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestHyperKnucklesWallImpactGate {
    private Sonic3kSuperStateController controller;

    @BeforeEach
    void setUp() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.level().resetLevelGamestate(
                GameModuleRegistry.getCurrent().createLevelState());
        GameServices.gameState().restoreS3kEmeraldProgress(
                java.util.List.of(3, 3, 3, 3, 3, 3, 3), true);
        Knuckles knuckles = new Knuckles("knuckles", (short) 0, (short) 0);
        knuckles.setRingCount(50);
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(knuckles, "knuckles");
        controller = new Sonic3kSuperStateController(knuckles);
        knuckles.setSuperStateController(controller);
        assertTrue(controller.activateFromAirAbility());
        controller.update();
        assertTrue(controller.isHyperFormActive());
    }

    @Test
    void gateUsesUnsigned16BitPreZeroGroundSpeedThreshold() {
        assertFalse(controller.triggerPoweredWallImpact(0x47F));
        assertTrue(controller.triggerPoweredWallImpact(0x480));
        assertTrue(controller.triggerPoweredWallImpact(-0x600),
                "$FA00 is above $0480 under the ROM's unsigned cmp/blo");
    }
}
