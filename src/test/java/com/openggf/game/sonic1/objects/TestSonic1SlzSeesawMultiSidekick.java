package com.openggf.game.sonic1.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSonic1SlzSeesawMultiSidekick {
    @Test
    void thirdSidekickStandingWithoutMainIsTheLaunchOwner() {
        TestPlayableSprite main = player(false);
        TestPlayableSprite first = player(true);
        TestPlayableSprite second = player(true);
        TestPlayableSprite third = player(true);
        Sonic1SeesawObjectInstance seesaw = new Sonic1SeesawObjectInstance(
                new ObjectSpawn(100, 100, 0x5E, 0, 0, false, 0));
        SpriteManager spriteManager = mock(SpriteManager.class);
        when(spriteManager.getAllSprites()).thenReturn(List.of(main, first, second, third));
        seesaw.setServices(new TestObjectServices() {
            private final ObjectPlayerQuery players = new ObjectPlayerQuery(
                    () -> main, () -> List.of(first, second, third));
            @Override public ObjectPlayerQuery playerQuery() { return players; }
            @Override public SpriteManager spriteManager() { return spriteManager; }
        });

        seesaw.onSolidContact(third, standingContact(), 1);

        assertSame(third, seesaw.getStandingPlayer(),
                "the seesaw must not redirect a sidekick contact to the main player");
    }

    @Test
    void standingThirdSidekickRestoresToReplacementPlayerRef() {
        TestPlayableSprite capturedMain = player(false);
        TestPlayableSprite capturedThird = player(true);
        Sonic1SeesawObjectInstance seesaw = new Sonic1SeesawObjectInstance(
                new ObjectSpawn(100, 100, 0x5E, 0xFF, 0, false, 0));
        seesaw.setServices(services(capturedMain, capturedThird));
        RewindIdentityTable capturedIds = new RewindIdentityTable();
        capturedIds.registerPlayer(capturedMain, PlayerRefId.mainPlayer());
        capturedIds.registerPlayer(capturedThird, PlayerRefId.sidekick(2));
        seesaw.onSolidContact(capturedThird, standingContact(), 1);
        var snapshot = seesaw.captureRewindState(RewindCaptureContext.withIdentityTable(capturedIds));

        TestPlayableSprite replacementMain = player(false);
        TestPlayableSprite replacementThird = player(true);
        RewindIdentityTable replacementIds = new RewindIdentityTable();
        replacementIds.registerPlayer(replacementMain, PlayerRefId.mainPlayer());
        replacementIds.registerPlayer(replacementThird, PlayerRefId.sidekick(2));
        seesaw.setServices(services(replacementMain, replacementThird));
        seesaw.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(replacementIds));

        assertSame(replacementThird, seesaw.getStandingPlayer());
    }

    @Test
    void mainAndThreeSidekicksRemainDistinctStandingParticipantsInNativeOrder() {
        TestPlayableSprite main = player(false);
        TestPlayableSprite first = player(true);
        TestPlayableSprite second = player(true);
        TestPlayableSprite third = player(true);
        Sonic1SeesawObjectInstance seesaw = new Sonic1SeesawObjectInstance(
                new ObjectSpawn(100, 100, 0x5E, 0xFF, 0, false, 0));
        seesaw.setServices(new TestObjectServices() {
            private final ObjectPlayerQuery players = new ObjectPlayerQuery(
                    () -> main, () -> List.of(first, second, third));
            @Override public ObjectPlayerQuery playerQuery() { return players; }
        });

        seesaw.onSolidContact(third, standingContact(), 1);
        seesaw.onSolidContact(first, standingContact(), 1);
        seesaw.onSolidContact(main, standingContact(), 1);
        seesaw.onSolidContact(second, standingContact(), 1);

        assertEquals(List.of(main, first, second, third), seesaw.getStandingPlayers());
    }

    private static TestObjectServices services(TestPlayableSprite main, TestPlayableSprite third) {
        return new TestObjectServices() {
            private final ObjectPlayerQuery players = new ObjectPlayerQuery(
                    () -> main, () -> List.of(third));
            @Override public ObjectPlayerQuery playerQuery() { return players; }
        };
    }

    private static TestPlayableSprite player(boolean cpu) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 100);
        player.setCentreY((short) 80);
        player.setCpuControlled(cpu);
        return player;
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }
}
