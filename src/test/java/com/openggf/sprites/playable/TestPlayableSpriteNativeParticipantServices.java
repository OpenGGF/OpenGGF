package com.openggf.sprites.playable;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestPlayableSpriteNativeParticipantServices {
    @Test void eachHumanUsesItsOwnActualWorldForCameraGameAndLevelState() {
        var owned = new NativeCompetitionRuntimeFixture();
        var other = new NativeCompetitionRuntimeFixture();
        assertNotSame(owned.world, SessionManager.getCurrentWorldSession());
        assertSame(owned.cameras[0], owned.p1.currentCamera());
        assertSame(owned.cameras[1], owned.p2.currentCamera());
        assertSame(owned.games[0], owned.p1.currentGameState());
        assertSame(owned.games[1], owned.p2.currentGameState());
        assertSame(owned.games[1], owned.p2.currentGameStateOrNull());
        assertSame(owned.banks[0], owned.p1.currentLevelState());
        assertSame(owned.banks[1], owned.p2.currentLevelState());
        assertNotSame(other.p2.currentCamera(), owned.p2.currentCamera());
        owned.p2.setRingCount(12); owned.p1.setRingCount(37);
        owned.p2.addRings(3); owned.p2.currentGameState().addScore(1000);
        owned.p2.currentGameState().loseLife();
        assertEquals(37, owned.banks[0].getRings());
        assertEquals(15, owned.banks[1].getRings());
        assertEquals(0, owned.ordinary.getRings());
        assertEquals(3, owned.games[0].getLives());
        assertEquals(2, owned.games[1].getLives());
        assertEquals(0, owned.games[0].getScore());
        assertEquals(1000, owned.games[1].getScore());
    }

    @Test void ordinaryCpuAndRetiredActorsKeepTheirExistingBoundServices() {
        var world = new NativeCompetitionRuntimeFixture();
        var cpu = world.ordinaryCpu();
        assertSame(world.ordinaryCamera, cpu.currentCamera());
        assertSame(world.ordinaryGame, cpu.currentGameState());
        assertSame(world.ordinaryGame, cpu.currentGameStateOrNull());
        assertSame(world.ordinary, cpu.currentLevelState());
        var old = world.p2;
        world.replacePlayers();
        assertSame(world.ordinary, old.currentLevelState());
        assertSame(world.banks[1], world.p2.currentLevelState());
    }

    @Test void ordinaryUnboundActorRetainsActiveRuntimeFallback() {
        var sonic = new Sonic("ordinary", (short) 0, (short) 0);
        assertSame(GameServices.camera(), sonic.currentCamera());
        assertSame(GameServices.gameState(), sonic.currentGameState());
        assertSame(GameServices.gameStateOrNull(), sonic.currentGameStateOrNull());
        assertSame(GameServices.level().getLevelGamestate(), sonic.currentLevelState());
    }
}
