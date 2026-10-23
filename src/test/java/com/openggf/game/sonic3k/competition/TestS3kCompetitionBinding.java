package com.openggf.game.sonic3k.competition;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@Isolated("Native sprite bridge uses the existing bootstrap owner")
class TestS3kCompetitionBinding {
    @BeforeEach void configure() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void clear() { SessionManager.clear(); GameModuleRegistry.reset(); }

    @Test void restoredSpriteRosterReplacesBothRetiredNativeModelsBeforeObjectReplay() {
        var world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        var retiredP1 = world.session.player(0);
        var retiredP2 = world.session.player(1);
        world.session.signalGo(); world.cross(0, true); world.cross(1, true); world.step(7);
        var before = world.session.capture();
        world.session.applyImmediateItem(0, 0); world.players.get(0).setCentre(0x777, 0x888);
        world.session.restore(before); // Policy restore intentionally precedes sprite recreation.
        var restoredP1 = S3kCompetitionPlayer.recreate(before.players().get(0).body());
        var restoredP2 = S3kCompetitionPlayer.recreate(before.players().get(1).body());
        var sprites = new SpriteManager();
        sprites.addSprite(restoredP2.sprite()); sprites.addSprite(restoredP1.sprite());
        world.session.bindRoster(sprites);
        assertSame(restoredP1, world.session.player(0)); assertSame(restoredP2, world.session.player(1));
        assertSame(restoredP1, world.players.getFirst(), "host-owned terrain and view list was rebound");
        assertNotSame(retiredP1, world.session.player(0)); assertNotSame(retiredP2, world.session.player(1));
        var retiredBefore = retiredP1.capture();
        world.session.applyImmediateItem(0, 0);
        assertEquals(0xC00, restoredP1.speeds().maxSpeed());
        assertEquals(retiredBefore, retiredP1.capture(), "objects must never mutate the retired actor");
        world.session.restore(before); world.step(17); var forward = world.session.capture();
        world.session.restore(before); world.step(17); assertEquals(forward, world.session.capture());
    }

    @Test void singleHumanRebindRequiresOnlyP1AndRejectsMissingOrDuplicateNativeSlots() {
        var world = new CompetitionTestWorld(S3kCompetitionSession.Mode.TIME_ATTACK);
        var sprites = new SpriteManager();
        assertThrows(IllegalStateException.class, () -> world.session.bindRoster(sprites));
        var fresh = S3kCompetitionPlayer.recreate(world.session.player(0).capture());
        sprites.addSprite(fresh.sprite()); world.session.bindRoster(sprites);
        assertSame(fresh, world.session.player(0)); assertEquals(1, world.session.players().size());
        var unexpected = new S3kCompetitionPlayer(1, S3kCompetitionPlayer.Character.TAILS, 0x440, 0x180);
        sprites.addSprite(unexpected.sprite());
        assertThrows(IllegalStateException.class, () -> world.session.bindRoster(sprites));
    }

    @Test void deathResetUsesP1CameraForP2AndDefersLapRollbackToNextGoalCall() {
        var world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.session.signalGo(); world.cross(1, true); world.session.giveRing(1);
        var p2 = world.session.player(1); p2.kill(); p2.setFractions(0x1234, 0x5678);
        p2.setCentreNative(0x600, 0x290); p2.setHighPriority(true);
        assertFalse(world.session.resolveDeadPlayer(1, 0x200), "threshold is exclusive");
        p2.setCentreNative(0x600, 0x291);
        assertTrue(world.session.resolveDeadPlayer(1, 0x200));
        assertEquals(980, p2.centreX()); assertEquals(200, p2.centreY());
        assertEquals(0x1234, p2.xFraction()); assertEquals(0x5678, p2.yFraction());
        assertFalse(p2.highPriority()); assertEquals(0, world.session.rings(1));
        assertEquals(1, world.session.lap(1), "rollback belongs to sub_36DFC, after participant execution");
        world.session.observeGoal(1000, 200, 8); assertEquals(0, world.session.lap(1));
    }
}
