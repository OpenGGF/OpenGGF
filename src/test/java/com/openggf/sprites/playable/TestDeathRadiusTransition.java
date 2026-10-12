package com.openggf.sprites.playable;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** KillSonic/KillCharacter/Player_TouchFloor native position writes before death velocity. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestDeathRadiusTransition {
    @AfterEach
    void clearSession() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    static Stream<Arguments> rollingDeaths() {
        return Stream.of(
                Arguments.of("S1 pit", 1, "sonic", false, false, 0, "pit", -5),
                Arguments.of("S1 wall crush", 1, "sonic", false, false, 0x40, "crush", -5),
                Arguments.of("S2 Sonic oil", 2, "sonic", false, false, 0, "oil", -5),
                Arguments.of("S2 Tails main crush", 2, "tails", false, false, 0, "crush", -1),
                Arguments.of("S2 Tails CPU pit", 2, "tails", true, false, 0, "pit", -1),
                Arguments.of("S3K Sonic pit", 3, "sonic", false, false, 0, "pit", -5),
                Arguments.of("S3K Tails CPU crush", 3, "tails", true, false, 0, "crush", -1),
                Arguments.of("S3K Knuckles crush", 3, "knuckles", false, false, 0, "crush", -5),
                Arguments.of("S3K inverted gravity", 3, "sonic", false, true, 0, "pit", 5),
                Arguments.of("S3K ceiling angle", 3, "sonic", false, false, 0x80, "crush", 5));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rollingDeaths")
    void killRestoresNativeRadiusPositionAndReplaysAfterRewind(String name, int game,
            String character, boolean cpu, boolean reverse, int angle, String reason, int delta) {
        AbstractPlayableSprite player = player(game, character);
        player.setCpuControlled(cpu);
        GameServices.gameState().setReverseGravityActive(reverse);
        player.setGroundMode(angle == 0x40 ? GroundMode.RIGHTWALL : GroundMode.GROUND);
        player.setAngle((byte) angle);
        player.setRolling(true);
        player.setAir(true);
        player.setCentreXPreserveSubpixel((short) 0x08DA);
        player.setCentreYPreserveSubpixel((short) 0x03D7);
        player.setSubpixelRaw(0xEC00, 0xA600);
        var before = player.captureRewindState();

        assertTrue(kill(player, reason));
        assertNativeDeath(player, delta);
        assertFalse(kill(player, reason), "routine 6 cannot reset the floor again");
        assertNativeDeath(player, delta);

        player.restoreRewindState(before);
        assertTrue(player.getRolling());
        assertFalse(player.getDead());
        assertEquals(0x03D7, player.getCentreY());
        assertEquals((byte) angle, player.getAngle(), "restored native angle");
        assertEquals(14, player.getYRadius(), "restored rolling radius");
        assertEquals(reverse, GameServices.gameState().isReverseGravityActive(), "live gravity owner");
        assertEquals(game == 3, player.getGameRules().playerMovement().landing()
                .landingRollClearUsesCurrentYRadiusDelta(), "restored radius policy");
        assertTrue(kill(player, reason));
        assertNativeDeath(player, delta);
    }

    static Stream<Arguments> standingDeaths() {
        return Stream.of(Arguments.of(1, "sonic", 10), Arguments.of(2, "tails", 10),
                Arguments.of(3, "sonic", 19), Arguments.of(3, "tails", 15));
    }

    @ParameterizedTest
    @MethodSource("standingDeaths")
    void nonRollingKillPreservesWordPositionAndNativeSplitRadiusPolicy(int game,
            String character, int expectedRadius) {
        AbstractPlayableSprite player = player(game, character);
        player.applyCustomRadii(8, 10);
        player.setCentreXPreserveSubpixel((short) 0x100);
        player.setCentreYPreserveSubpixel((short) 0x200);
        player.setSubpixelRaw(0x1200, 0x3400);
        assertTrue(player.applyPitDeath());
        assertEquals(0x100, player.getCentreX());
        assertEquals(0x200, player.getCentreY());
        assertEquals(expectedRadius, player.getYRadius());
        assertEquals(0x3400, player.getYSubpixelRaw());
    }

    private AbstractPlayableSprite player(int game, String character) {
        GameModule module = switch (game) {
            case 1 -> new Sonic1GameModule();
            case 2 -> new Sonic2GameModule();
            default -> new Sonic3kGameModule();
        };
        TestEnvironment.configureGameModuleFixture(module);
        return switch (character) {
            case "tails" -> new Tails(character, (short) 0, (short) 0);
            case "knuckles" -> new Knuckles(character, (short) 0, (short) 0);
            default -> new Sonic(character, (short) 0, (short) 0);
        };
    }

    private boolean kill(AbstractPlayableSprite player, String reason) {
        return switch (reason) {
            case "crush" -> player.applyCrushDeath();
            case "oil" -> player.applyOilSuffocateDeath();
            default -> player.applyPitDeath();
        };
    }

    private void assertNativeDeath(AbstractPlayableSprite player, int delta) {
        assertEquals(0x08DA, player.getCentreX());
        assertEquals(0x03D7 + delta, player.getCentreY());
        assertEquals(0xEC00, player.getXSubpixelRaw());
        assertEquals(0xA600, player.getYSubpixelRaw());
        assertFalse(player.getRolling());
        assertTrue(player.getAir());
        assertTrue(player.getDead());
        assertEquals(-0x700, player.getYSpeed());
        assertEquals(0, player.getXSpeed());
    }
}
