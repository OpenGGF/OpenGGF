package com.openggf.game.sonic1.events;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.Sonic1ZoneFeatureProvider;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestSonic1LzMultiPlayerTransport {

    private Sonic1LZWaterEvents events;

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        events = new Sonic1LZWaterEvents();
        events.init(Sonic1Constants.ZONE_LZ, 0);
    }

    @Test
    void windTunnelMovesMainThenThreeSidekicksIndependentlyAndUsesActiveAnimationIds() {
        List<TestPlayableSprite> players = fourPlayersInFirstLz1Tunnel();

        for (int i = 0; i < players.size(); i++) {
            events.updateWindTunnels(players.get(i), i == 0);
        }

        for (TestPlayableSprite player : players) {
            assertEquals(0x0A94, player.getCentreX());
            assertEquals(0x0400, player.getXSpeed());
            assertEquals(0, player.getYSpeed());
            assertTrue(player.getAir());
            assertEquals(player.resolveAnimationId(CanonicalAnimation.FLOAT2), player.getAnimationId());
            assertEquals(player.resolveAnimationId(CanonicalAnimation.FLOAT2), player.getForcedAnimationId());
        }

        TestPlayableSprite thirdSidekick = players.get(3);
        thirdSidekick.setCentreX((short) 0x0900);
        events.updateWindTunnels(thirdSidekick, false);
        assertEquals(-1, thirdSidekick.getForcedAnimationId());
        assertEquals(thirdSidekick.resolveAnimationId(CanonicalAnimation.WALK), thirdSidekick.getAnimationId());
        assertEquals(0x0400, players.get(0).getXSpeed(), "sidekick exit must not release the main player");
    }

    @Test
    void waterSlideEntryAndExitAreOwnedPerPlayerForMainPlusThreeSidekicks() {
        List<TestPlayableSprite> players = fourGroundedPlayers();

        for (int i = 0; i < players.size(); i++) {
            events.checkWaterSlide(players.get(i), 2, i == 0);
        }

        for (TestPlayableSprite player : players) {
            assertTrue(player.isSliding());
            assertEquals(10 * 256, player.getGSpeed());
            assertEquals(player.resolveAnimationId(CanonicalAnimation.WATER_SLIDE), player.getAnimationId());
        }

        TestPlayableSprite thirdSidekick = players.get(3);
        events.checkWaterSlide(thirdSidekick, 0, false);

        assertFalse(thirdSidekick.isSliding());
        assertEquals(5, thirdSidekick.getMoveLockTimer());
        assertTrue(players.get(0).isSliding());
        assertTrue(players.get(1).isSliding());
        assertTrue(players.get(2).isSliding());
    }

    @Test
    void zoneFeaturePrePhysicsUsesMainFirstRuntimeRosterThroughThirdSidekick() throws Exception {
        List<TestPlayableSprite> players = fourPlayersInFirstLz1Tunnel();
        players.get(0).setCode("MAIN");
        for (int i = 1; i < players.size(); i++) {
            players.get(i).setCode("SIDEKICK-" + i);
        }
        GameServices.sprites().addSprite(players.get(0));
        GameServices.camera().setFocusedSprite(players.get(0));
        for (int i = 1; i < players.size(); i++) {
            GameServices.sprites().addSprite(players.get(i), "sonic");
        }

        Sonic1ZoneFeatureProvider provider = new Sonic1ZoneFeatureProvider();
        setField(provider, "waterEvents", events);
        setField(provider, "currentZone", Sonic1Constants.ZONE_LZ);
        setField(provider, "currentAct", 0);
        setField(provider, "isSBZ3", false);

        provider.updatePrePhysics(players.get(0), 0, Sonic1Constants.ZONE_LZ);

        for (TestPlayableSprite player : players) {
            assertEquals(0x0A94, player.getCentreX(),
                    "real zone-feature path must transport every runtime participant exactly once");
        }
    }

    private static List<TestPlayableSprite> fourPlayersInFirstLz1Tunnel() {
        List<TestPlayableSprite> players = fourGroundedPlayers();
        for (TestPlayableSprite player : players) {
            player.setCentreX((short) 0x0A90);
            player.setCentreY((short) 0x0320);
        }
        return players;
    }

    private static List<TestPlayableSprite> fourGroundedPlayers() {
        TestPlayableSprite main = new TestPlayableSprite();
        TestPlayableSprite first = new TestPlayableSprite();
        TestPlayableSprite second = new TestPlayableSprite();
        TestPlayableSprite third = new TestPlayableSprite();
        first.setCpuControlled(true);
        second.setCpuControlled(true);
        third.setCpuControlled(true);
        return List.of(main, first, second, third);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
