package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic2.objects.LauncherBallObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Seeded legal entry states; the boundary and launcher owners execute the attempted tricks. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2CurrentLimitations {
    @BeforeEach
    void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        LauncherBallObjectInstance.clearActiveCaptures();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void underflowClampsToUnsignedNativeFarRight(boolean lockOn) throws Exception {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(10)) {
            var player = player(lockOn);
            GameServices.camera().setMinX((short) 0);
            GameServices.camera().setMaxX((short) 0x2000);
            player.setCentreX((short) 24);
            player.setXSpeed((short) -0x3000);
            player.setGSpeed((short) -0x3000);
            assertEquals(0xFFE8, (24 - 0x30) & 0xFFFF);
            var boundary = PlayableSpriteMovement.class.getDeclaredMethod("doLevelBoundary");
            boundary.setAccessible(true);
            boundary.invoke(new PlayableSpriteMovement(player));
            assertEquals(0, player.getXSpeed(), "must reach a boundary clamp");
            assertEquals(0, player.getGSpeed());
            // Sonic_LevelBound (s2.asm:37233-37254) uses unsigned word comparisons.
            assertEquals(0x2168, player.getCentreX() & 0xFFFF,
                    "Native word comparison clamps underflow to the right edge");
            player.setCentreX((short) 24);
            player.setXSpeed((short) -0x100);
            boundary.invoke(new PlayableSpriteMovement(player));
            assertEquals(24, player.getCentreX());
            assertEquals(-0x100, player.getXSpeed(), "ordinary in-bounds motion is the control");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hurtLauncherAdmitsNativeRoutineFourEntry(boolean lockOn) {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(10)) {
            var player = player(lockOn);
            player.setCentreX((short) 0x1000);
            player.setCentreY((short) 0x1000);
            assertTrue(player.applyHurtOrDeath(0x1001, true, true));
            assertTrue(player.isHurt());
            assertFalse(player.getDead());
            var launcher = new LauncherBallObjectInstance(
                    new ObjectSpawn(0x1000, 0x1000, 0x48, 0, 0, false, 0), "LauncherBall");
            launcher.setServices(new TestObjectServices().withCamera(GameServices.camera()));
            launcher.update(1, player);
            com.openggf.tests.reel.S2ComponentTestReel.frame(player, java.util.List.of(launcher), 0, 0, "hurt launcher");
            // loc_252F0 (s2.asm:51309-51387) rejects routine >=6, accepts hurt routine 4.
            assertTrue(player.isObjectControlled(), "native routine 4 enters capture");
            assertTrue(player.isHurt(), "FixBugs=0 does not write player routine 2 at admission");
            assertEquals(0x1000, player.getGSpeed());
            assertEquals(0, player.getXSpeed());
            assertEquals(0, player.getYSpeed());
            launcher.onUnload();
            player.setHurt(false);
            launcher.update(2, player);
            com.openggf.tests.reel.S2ComponentTestReel.frame(player, java.util.List.of(launcher), 0, 0, "normal launcher control");
            assertTrue(player.isObjectControlled(), "same location and launcher must capture the normal control");
            assertEquals(0x1000, player.getGSpeed());
            assertEquals(0, player.getXSpeed());
            assertEquals(0, player.getYSpeed());
        }
    }

    private TestablePlayableSprite player(boolean lockOn) {
        var player = new TestablePlayableSprite(lockOn ? "knuckles" : "sonic", (short) 0, (short) 0);
        player.setGameRulesForTest(lockOn ? Kis2Rules.RULES : GameRules.SONIC_2);
        return player;
    }
}
