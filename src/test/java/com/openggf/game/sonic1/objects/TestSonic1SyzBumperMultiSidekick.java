package com.openggf.game.sonic1.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class TestSonic1SyzBumperMultiSidekick {
    private static final TouchResponseResult BUMPER_TOUCH =
            new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL);

    @Test
    void mainAndThreeSidekicksTouchingInOnePassAllBounceOnNextUpdate() {
        Sonic1BumperObjectInstance bumper = new Sonic1BumperObjectInstance(
                new ObjectSpawn(100, 100, 0x47, 0, 0, false, 0));
        bumper.setServices(new TestObjectServices() {
            private final GameStateManager gameState = mock(GameStateManager.class);
            @Override public GameStateManager gameState() { return gameState; }
        });
        List<TestPlayableSprite> players = List.of(
                playerAt(84, 100, false),
                playerAt(116, 100, true),
                playerAt(100, 84, true),
                playerAt(100, 116, true));

        for (PlayableEntity player : players) {
            bumper.onTouchResponse(player, BUMPER_TOUCH, 1);
        }
        bumper.update(2, players.getFirst());

        for (TestPlayableSprite player : players) {
            assertNotEquals(0, Math.abs(player.getXSpeed()) + Math.abs(player.getYSpeed()),
                    "every participant must consume its own queued bumper contact");
        }
    }

    @Test
    void queuedThirdSidekickContactRestoresToReplacementPlayerRef() {
        TestPlayableSprite capturedMain = playerAt(40, 40, false);
        TestPlayableSprite capturedThird = playerAt(116, 100, true);
        Sonic1BumperObjectInstance bumper = new Sonic1BumperObjectInstance(
                new ObjectSpawn(100, 100, 0x47, 0, 0, false, 0));
        bumper.setServices(services(capturedMain));
        RewindIdentityTable capturedIds = new RewindIdentityTable();
        capturedIds.registerPlayer(capturedMain, PlayerRefId.mainPlayer());
        capturedIds.registerPlayer(capturedThird, PlayerRefId.sidekick(2));

        bumper.onTouchResponse(capturedThird, BUMPER_TOUCH, 1);
        var snapshot = bumper.captureRewindState(RewindCaptureContext.withIdentityTable(capturedIds));

        TestPlayableSprite replacementMain = playerAt(40, 40, false);
        TestPlayableSprite replacementThird = playerAt(116, 100, true);
        RewindIdentityTable replacementIds = new RewindIdentityTable();
        replacementIds.registerPlayer(replacementMain, PlayerRefId.mainPlayer());
        replacementIds.registerPlayer(replacementThird, PlayerRefId.sidekick(2));
        bumper.setServices(services(replacementMain));
        bumper.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(replacementIds));
        bumper.update(2, replacementMain);

        assertNotEquals(0, Math.abs(replacementThird.getXSpeed()) + Math.abs(replacementThird.getYSpeed()));
        assertEquals(0, Math.abs(capturedThird.getXSpeed()) + Math.abs(capturedThird.getYSpeed()),
                "rewind must not deliver a queued contact to the captured actor instance");
    }

    private static TestObjectServices services(TestPlayableSprite main) {
        return new TestObjectServices() {
            private final GameStateManager gameState = mock(GameStateManager.class);
            @Override public GameStateManager gameState() { return gameState; }
            @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
                return new com.openggf.level.objects.ObjectPlayerQuery(() -> main, List::of);
            }
        };
    }

    private static TestPlayableSprite playerAt(int x, int y, boolean cpu) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setCpuControlled(cpu);
        return player;
    }
}
