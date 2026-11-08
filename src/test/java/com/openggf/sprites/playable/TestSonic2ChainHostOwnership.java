package com.openggf.sprites.playable;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Game-wide chain ownership follows the world, independently of donor movement rules. */
class TestSonic2ChainHostOwnership {
    @BeforeEach void setup() {
        TestEnvironment.resetAll();
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void foreignActorInS2WorldUsesAndResetsThatWorldsWord() {
        var host = new Sonic2GameModule();
        var donor = new Sonic1GameModule();
        var context = boundContext(donor, host);
        var actor = context.construct(() -> new Sonic("s1-donor", (short)128, (short)256));
        var word = host.getGameService(BadnikChainPolicy.class);
        assertSame(donor, actor.currentGameModule(), "movement remains donor-owned");
        assertEquals(100, word.incrementAndAward());
        assertEquals(200, actor.incrementBadnikChain(), "the host already contributed the first kill");
        assertEquals(4, word.word());
        PlayableNativeFloorReset.resetChain(actor);
        assertEquals(0, word.word());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void s2ActorsInOtherWorldsRetainIndependentLocalChains(boolean s3kHost) {
        GameModule host = s3kHost ? new Sonic3kGameModule() : new Sonic1GameModule();
        var donor = new Sonic2GameModule();
        var context = boundContext(donor, host);
        var first = context.construct(() -> new Sonic("first-s2-donor", (short)128, (short)256));
        var second = context.construct(() -> new Sonic("second-s2-donor", (short)160, (short)256));
        assertEquals(100, first.incrementBadnikChain());
        assertEquals(100, second.incrementBadnikChain(), "a non-S2 host keeps actor-local chains");
        assertNull(PlayableNativeFloorReset.chain(first));
        var unusedDonorWord = donor.getGameService(BadnikChainPolicy.class);
        assertEquals(0, unusedDonorWord.word());
        unusedDonorWord.restoreWord(8);
        PlayableNativeFloorReset.resetChain(first);
        first.setAir(true); first.setAir(false);
        assertEquals(100, first.incrementBadnikChain());
        assertEquals(8, unusedDonorWord.word(), "host landing does not mutate an unrelated donor word");
    }

    private static PlayableRuntimeContext boundContext(GameModule actorModule, GameModule host) {
        var world = mock(WorldSession.class);
        when(world.getGameModule()).thenReturn(host);
        var level = mock(LevelManager.class);
        when(level.getWorldSession()).thenReturn(world);
        when(level.getLevelGamestate()).thenReturn(new LevelGamestate());
        return new PlayableRuntimeContext(actorModule, new Camera(), level, null, null,
                new SpriteManager(GameServices.configuration()), new GameStateManager(), null, GameServices.audio());
    }
}
